package com.cw2.nekoama.infrastructure.code_suggestion_gen.code_analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*
import com.cw2.nekoama.shared.model.Result
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.domain.code_suggestion_gen.model.AnnotationMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.ClassContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.ClassMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.MethodContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.ParameterMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.ProgrammingLanguage
import com.cw2.nekoama.domain.code_suggestion_gen.model.ProjectMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.SurroundingContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.TypeMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.VariableContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.VariableScope

/**
 * Java 代码分析器
 *
 * 专门处理 Java 语言元素的分析工作，包括分析类、方法等。
 *
 * 为了遵循 PSI 线程安全规则确保 IDE 稳定性，所有 PSI 访问都在 ReadAction 中进行。
 */
class JavaCodeAnalyzer(private val project: Project) {

    /**
     * 分析 Java 方法并提取其上下文信息
     *
     * @param method 要分析的 PSI 方法对象
     * @return 包含方法上下文信息的 Result 对象，成功时返回 MethodContext，失败时返回错误信息
     */
    fun analyzeJavaMethod(method: PsiMethod): Result<MethodContext> {
        return try {
            // 在 ReadAction 中执行 PSI 访问，确保线程安全
            ReadAction.compute<Result<MethodContext>, Throwable> {
                // 提取方法参数信息：遍历参数列表，构建参数元数据
                val parameters = method.parameterList.parameters.map { param ->
                    ParameterMetadata(
                        name = param.name,
                        type = TypeMetadata(
                            typeName = param.type.presentableText
                        ),
                        annotations = param.annotations.map {
                            AnnotationMetadata(it.qualifiedName ?: "")
                        }
                    )
                }

                // 提取返回类型信息，若无返回类型则默认为 "void"
                val returnType = TypeMetadata(
                    typeName = method.returnType?.presentableText ?: "void"
                )

                // 提取方法修饰符：检查并收集所有适用的修饰符
                val modifiers = method.modifierList.let { modifierList ->
                    mutableListOf<String>().apply {
                        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) add("public")
                        if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) add("private")
                        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) add("protected")
                        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) add("static")
                        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) add("final")
                        if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) add("abstract")
                    }
                }

                // 构建完整的方法上下文对象
                val methodContext = MethodContext(
                    language = ProgrammingLanguage.JAVA,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        namingPatterns = null
                    ),
                    methodName = method.name,
                    parameters = parameters,
                    returnType = returnType,
                    modifiers = modifiers,
                    // 提取方法上的注解
                    annotations = method.annotations.map {
                        AnnotationMetadata(it.qualifiedName ?: "")
                    },
                    // 提取方法声明的异常类型
                    exceptions = method.throwsList.referencedTypes.map {
                        TypeMetadata(it.presentableText)
                    },
                    methodBody = method.body?.text,
                    isConstructor = method.isConstructor,
                    isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT)
                )

                Result.success(methodContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Java 方法分析失败: ${e.message}"))
        }
    }

    /**
     * 分析 Java 类并提取其上下文信息
     *
     * @param clazz 要分析的 PSI 类对象
     * @return 包含类上下文信息的 Result 对象，成功时返回 ClassContext，失败时返回错误信息
     */
    fun analyzeJavaClass(clazz: PsiClass): Result<ClassContext> {
        return try {
            // 在 ReadAction 中执行 PSI 访问，确保线程安全
            ReadAction.compute<Result<ClassContext>, Throwable> {
                // 提取父类信息（如果存在）
                val superClass = clazz.superClass?.let { superCls ->
                    TypeMetadata(
                        typeName = superCls.name ?: ""
                    )
                }

                // 构建完整的类上下文对象
                val classContext = ClassContext(
                    language = ProgrammingLanguage.JAVA,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        namingPatterns = null
                    ),
                    className = clazz.name,
                    superClass = superClass,
                    isInterface = clazz.isInterface,
                    isAbstract = clazz.hasModifierProperty(PsiModifier.ABSTRACT),
                    isEnum = clazz.isEnum,
                    // 从包含文件中提取包名，若非 Java 文件则返回空字符串
                    packageName = (clazz.containingFile as? PsiJavaFile)?.packageName ?: ""
                )

                Result.success(classContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Java 类分析失败: ${e.message}"))
        }
    }

    /**
     * 分析 Java 变量并提取其上下文信息
     *
     * @param variable 要分析的 PSI 变量对象（可能是字段、局部变量或参数）
     * @return 包含变量上下文信息的 Result 对象，成功时返回 VariableContext，失败时返回错误信息
     */
    fun analyzeJavaVariable(variable: PsiVariable): Result<VariableContext> {
        return try {
            // 在 ReadAction 中执行 PSI 访问，确保线程安全
            ReadAction.compute<Result<VariableContext>, Throwable> {
                // 提取变量类型信息
                val variableType = TypeMetadata(
                    typeName = variable.type.presentableText
                )

                // 提取变量修饰符：检查并收集所有适用的修饰符
                val modifiers = variable.modifierList?.let { modifierList ->
                    mutableListOf<String>().apply {
                        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) add("final")
                        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) add("static")
                        if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) add("volatile")
                        if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) add("transient")
                    }
                } ?: emptyList()

                // 根据变量的具体类型确定其作用域
                // PsiVariable 是一个接口，其实现包括 PsiField（字段）、PsiLocalVariable（局部变量）和 PsiParameter（参数）
                val variableScope = when (variable) {
                    is PsiField -> VariableScope.FIELD
                    is PsiLocalVariable -> VariableScope.LOCAL
                    is PsiParameter -> VariableScope.PARAMETER
                    else -> VariableScope.LOCAL
                }

                // 构建完整的变量上下文对象
                val variableContext = VariableContext(
                    language = ProgrammingLanguage.JAVA,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        namingPatterns = null
                    ),
                    variableName = variable.name,
                    variableType = variableType,
                    modifiers = modifiers,
                    annotations = variable.annotations.map {
                        AnnotationMetadata(it.qualifiedName ?: "")
                    },
                    initializer = variable.initializer?.text,
                    scope = variableScope,
                    isConstant = variable.modifierList?.hasModifierProperty(PsiModifier.FINAL) == true,
                    isStatic = variable.modifierList?.hasModifierProperty(PsiModifier.STATIC) == true
                )

                Result.success(variableContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Java 变量分析失败: ${e.message}"))
        }
    }
}
