package com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*
import com.cw2.nekoama.shared.model.Result
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.domain.code_suggestion_gen.model.AnnotationMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.ClassContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.ClassMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.MethodContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.MethodMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.ParameterMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.ProgrammingLanguage
import com.cw2.nekoama.domain.code_suggestion_gen.model.ProjectMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.SurroundingContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.TypeMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.VariableContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.VariableScope
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Kotlin 代码分析器
 *
 * 专门处理 Kotlin 语言元素的分析工作，包括分析类、属性、函数等。
 *
 * 为了遵循 PSI 线程安全规则确保 IDE 稳定性，所有 PSI 访问都在 ReadAction 中进行。
 */
class KotlinCodeAnalyzer(private val project: Project) {
    /**
     * 分析 Kotlin 函数并提取其上下文信息
     *
     * @param function 要分析的 Kotlin PSI 函数对象
     * @return 包含函数上下文信息的 Result 对象，成功时返回 MethodContext，失败时返回错误信息
     */
    fun analyzeKotlinFunction(function: KtFunction): Result<MethodContext> {
        return try {
            ReadAction.compute<Result<MethodContext>, Throwable> {
                // 提取函数参数信息：遍历值参数列表，构建参数元数据
                val parameters = function.valueParameters.map { param ->
                    ParameterMetadata(
                        name = param.name ?: "",
                        type = TypeMetadata(
                            typeName = param.typeReference?.text ?: "Any"
                        ),
                        annotations = param.annotationEntries.map {
                            AnnotationMetadata(it.shortName?.asString() ?: "")
                        }
                    )
                }

                // 提取返回类型信息，若无显式声明则默认为 "Unit"
                val returnType = TypeMetadata(
                    typeName = function.typeReference?.text ?: "Unit"
                )

                // 提取函数修饰符：检查并收集所有适用的修饰符
                val modifiers = mutableListOf<String>().apply {
                    if (function.hasModifier(KtTokens.PUBLIC_KEYWORD)) add("public")
                    if (function.hasModifier(KtTokens.PRIVATE_KEYWORD)) add("private")
                    if (function.hasModifier(KtTokens.PROTECTED_KEYWORD)) add("protected")
                    if (function.hasModifier(KtTokens.INTERNAL_KEYWORD)) add("internal")
                    if (function.hasModifier(KtTokens.ABSTRACT_KEYWORD)) add("abstract")
                    if (function.hasModifier(KtTokens.FINAL_KEYWORD)) add("final")
                    if (function.hasModifier(KtTokens.OPEN_KEYWORD)) add("open")
                    if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) add("override")
                    if (function.hasModifier(KtTokens.SUSPEND_KEYWORD)) add("suspend")
                    if (function.hasModifier(KtTokens.INLINE_KEYWORD)) add("inline")
                }

                // 构建完整的函数上下文对象
                val methodContext = MethodContext(
                    language = ProgrammingLanguage.KOTLIN,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        precedingCode = emptyList(),
                        followingCode = emptyList(),
                        imports = emptyList()
                    ),
                    methodName = function.name,
                    parameters = parameters,
                    returnType = returnType,
                    modifiers = modifiers,
                    // 提取函数上的注解
                    annotations = function.annotationEntries.map {
                        AnnotationMetadata(it.shortName?.asString() ?: "")
                    },
                    // Kotlin 没有受检异常，因此返回空列表
                    exceptions = emptyList(),
                    // 提取函数体：优先使用表达式体，若不存在则使用代码块体
                    methodBody = function.bodyExpression?.text ?: function.bodyBlockExpression?.text,
                    isConstructor = function is KtConstructor<*>,
                    isAbstract = function.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                )

                Result.success(methodContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Kotlin 函数分析失败: ${e.message}"))
        }
    }
    
    /**
     * 分析 Kotlin 类并提取其上下文信息
     *
     * @param clazz 要分析的 Kotlin PSI 类对象
     * @return 包含类上下文信息的 Result 对象，成功时返回 ClassContext，失败时返回错误信息
     */
    fun analyzeKotlinClass(clazz: KtClass): Result<ClassContext> {
        return try {
            ReadAction.compute<Result<ClassContext>, Throwable> {
                // 提取父类信息：从超类型列表中过滤出 KtSuperTypeCallEntry 并获取第一个
                // Kotlin 中使用冒号语法声明继承，如 "class Child : Parent()"
                val superClass = clazz.superTypeListEntries
                    .filterIsInstance<KtSuperTypeCallEntry>()
                    .firstOrNull()?.let { superEntry ->
                        TypeMetadata(
                            typeName = superEntry.typeReference?.text ?: ""
                        )
                    }

                // 构建完整的类上下文对象
                val classContext = ClassContext(
                    language = ProgrammingLanguage.KOTLIN,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        precedingCode = emptyList(),
                        followingCode = emptyList(),
                        imports = emptyList()
                    ),
                    className = clazz.name,
                    superClass = superClass,
                    isInterface = clazz.isInterface(),
                    isAbstract = clazz.hasModifier(KtTokens.ABSTRACT_KEYWORD),
                    isEnum = clazz.isEnum(),
                    // 从包含文件的包指令中提取包名
                    packageName = clazz.containingKtFile.packageDirective?.fqName?.asString() ?: ""
                )

                Result.success(classContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Kotlin 类分析失败: ${e.message}"))
        }
    }
    
    /**
     * 分析 Kotlin 属性并提取其上下文信息
     *
     * @param property 要分析的 Kotlin PSI 属性对象
     * @return 包含属性上下文信息的 Result 对象，成功时返回 VariableContext，失败时返回错误信息
     */
    fun analyzeKotlinProperty(property: KtProperty): Result<VariableContext> {
        return try {
            ReadAction.compute<Result<VariableContext>, Throwable> {
                // 提取属性类型信息，若无显式声明则默认为 "Any"
                val propertyType = TypeMetadata(
                    typeName = property.typeReference?.text ?: "Any"
                )

                // 提取属性修饰符：检查并收集所有适用的修饰符
                val modifiers = mutableListOf<String>().apply {
                    if (property.hasModifier(KtTokens.PUBLIC_KEYWORD)) add("public")
                    if (property.hasModifier(KtTokens.PRIVATE_KEYWORD)) add("private")
                    if (property.hasModifier(KtTokens.PROTECTED_KEYWORD)) add("protected")
                    if (property.hasModifier(KtTokens.INTERNAL_KEYWORD)) add("internal")
                    if (property.hasModifier(KtTokens.CONST_KEYWORD)) add("const")
                    if (property.hasModifier(KtTokens.LATEINIT_KEYWORD)) add("lateinit")
                    if (property.hasModifier(KtTokens.OVERRIDE_KEYWORD)) add("override")
                    // Kotlin 属性必须是 val（只读）或 var（可变）
                    if (property.isVar()) add("var") else add("val")
                }

                // 获取包含该属性的类（如果存在）
                // 属性的父元素通常是 KtPropertyAccessor，再上层才是 KtClass
                val containingClass = property.parent?.parent as? KtClass

                // 构建完整的属性上下文对象
                val variableContext = VariableContext(
                    language = ProgrammingLanguage.KOTLIN,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        precedingCode = emptyList(),
                        followingCode = emptyList(),
                        imports = emptyList()
                    ),
                    variableName = property.name,
                    variableType = propertyType,
                    modifiers = modifiers,
                    annotations = property.annotationEntries.map {
                        AnnotationMetadata(it.shortName?.asString() ?: "")
                    },
                    // 顶层属性的作用域为 GLOBAL，类成员属性的作用域为 FIELD
                    scope = if (property.isTopLevel) VariableScope.GLOBAL else VariableScope.FIELD,
                    // const 修饰符或 val（非 var）属性被视为常量
                    isConstant = property.hasModifier(KtTokens.CONST_KEYWORD) || !property.isVar(),
                    // 顶层属性被视为静态的
                    isStatic = property.isTopLevel,
                    containingClass = containingClass?.let { cls ->
                        ClassMetadata(
                            name = cls.name ?: ""
                        )
                    }
                )

                Result.success(variableContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Kotlin 属性分析失败: ${e.message}"))
        }
    }
    
    /**
     * 分析 Kotlin 函数参数并提取其上下文信息
     *
     * @param parameter 要分析的 Kotlin PSI 参数对象
     * @return 包含参数上下文信息的 Result 对象，成功时返回 VariableContext，失败时返回错误信息
     */
    fun analyzeKotlinParameter(parameter: KtParameter): Result<VariableContext> {
        return try {
            ReadAction.compute<Result<VariableContext>, Throwable> {
                // 提取参数类型信息，若无显式声明则默认为 "Any"
                val parameterType = TypeMetadata(
                    typeName = parameter.typeReference?.text ?: "Any"
                )

                // 提取参数修饰符：检查并收集所有适用的修饰符
                val modifiers = mutableListOf<String>().apply {
                    if (parameter.hasModifier(KtTokens.VARARG_KEYWORD)) add("vararg")
                    if (parameter.hasModifier(KtTokens.NOINLINE_KEYWORD)) add("noinline")
                    if (parameter.hasModifier(KtTokens.CROSSINLINE_KEYWORD)) add("crossinline")
                    // Kotlin 参数可以是 val（默认，不可变）或 var（可变，仅用于具名参数解构）
                    if (parameter.isMutable) add("var") else add("val")
                }

                // 构建完整的参数上下文对象
                val variableContext = VariableContext(
                    language = ProgrammingLanguage.KOTLIN,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        precedingCode = emptyList(),
                        followingCode = emptyList(),
                        imports = emptyList()
                    ),
                    variableName = parameter.name,
                    variableType = parameterType,
                    modifiers = modifiers,
                    annotations = parameter.annotationEntries.map {
                        AnnotationMetadata(it.shortName?.asString() ?: "")
                    },
                    // 提取参数的默认值（如果有）
                    initializer = parameter.defaultValue?.text,
                    scope = VariableScope.PARAMETER,
                    // 不可变参数被视为常量
                    isConstant = !parameter.isMutable,
                    // 函数参数不是静态的
                    isStatic = false,
                    // 使用 PsiTreeUtil 向上遍历 PSI 树，查找包含该参数的函数
                    containingMethod = PsiTreeUtil.getParentOfType(parameter, KtFunction::class.java)?.let { function ->
                        MethodMetadata(
                            name = function.name ?: "",
                            returnType = TypeMetadata(
                                typeName = function.typeReference?.text ?: "Unit"
                            )
                        )
                    }
                )

                Result.success(variableContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Kotlin 参数分析失败: ${e.message}"))
        }
    }
}
