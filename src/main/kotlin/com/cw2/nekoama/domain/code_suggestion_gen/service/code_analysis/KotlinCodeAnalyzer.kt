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
 * 专门处理 Kotlin 语言元素的分析工作，包括分析类、属性等。
 */
class KotlinCodeAnalyzer(private val project: Project) {
    fun analyzeKotlinFunction(function: KtFunction): Result<MethodContext> {
        return try {
            ReadAction.compute<Result<MethodContext>, Throwable> {
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

                val returnType = TypeMetadata(
                    typeName = function.typeReference?.text ?: "Unit"
                )

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
                    annotations = function.annotationEntries.map {
                        AnnotationMetadata(it.shortName?.asString() ?: "")
                    },
                    exceptions = emptyList(), // Kotlin doesn't have checked exceptions
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
    
    fun analyzeKotlinClass(clazz: KtClass): Result<ClassContext> {
        return try {
            ReadAction.compute<Result<ClassContext>, Throwable> {
                val superClass = clazz.superTypeListEntries
                    .filterIsInstance<KtSuperTypeCallEntry>()
                    .firstOrNull()?.let { superEntry ->
                        TypeMetadata(
                            typeName = superEntry.typeReference?.text ?: ""
                        )
                    }

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
                    packageName = clazz.containingKtFile.packageDirective?.fqName?.asString() ?: ""
                )

                Result.success(classContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Kotlin 类分析失败: ${e.message}"))
        }
    }
    
    fun analyzeKotlinProperty(property: KtProperty): Result<VariableContext> {
        return try {
            ReadAction.compute<Result<VariableContext>, Throwable> {
                val propertyType = TypeMetadata(
                    typeName = property.typeReference?.text ?: "Any"
                )

                val modifiers = mutableListOf<String>().apply {
                    if (property.hasModifier(KtTokens.PUBLIC_KEYWORD)) add("public")
                    if (property.hasModifier(KtTokens.PRIVATE_KEYWORD)) add("private")
                    if (property.hasModifier(KtTokens.PROTECTED_KEYWORD)) add("protected")
                    if (property.hasModifier(KtTokens.INTERNAL_KEYWORD)) add("internal")
                    if (property.hasModifier(KtTokens.CONST_KEYWORD)) add("const")
                    if (property.hasModifier(KtTokens.LATEINIT_KEYWORD)) add("lateinit")
                    if (property.hasModifier(KtTokens.OVERRIDE_KEYWORD)) add("override")
                    if (property.isVar()) add("var") else add("val")
                }

                val containingClass = property.parent?.parent as? KtClass

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
                    scope = if (property.isTopLevel) VariableScope.GLOBAL else VariableScope.FIELD,
                    isConstant = property.hasModifier(KtTokens.CONST_KEYWORD) || !property.isVar(),
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
    
    fun analyzeKotlinParameter(parameter: KtParameter): Result<VariableContext> {
        return try {
            ReadAction.compute<Result<VariableContext>, Throwable> {
                val parameterType = TypeMetadata(
                    typeName = parameter.typeReference?.text ?: "Any"
                )

                val modifiers = mutableListOf<String>().apply {
                    if (parameter.hasModifier(KtTokens.VARARG_KEYWORD)) add("vararg")
                    if (parameter.hasModifier(KtTokens.NOINLINE_KEYWORD)) add("noinline")
                    if (parameter.hasModifier(KtTokens.CROSSINLINE_KEYWORD)) add("crossinline")
                    if (parameter.isMutable) add("var") else add("val")
                }

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
                    initializer = parameter.defaultValue?.text,
                    scope = VariableScope.PARAMETER,
                    isConstant = !parameter.isMutable,
                    isStatic = false,
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
