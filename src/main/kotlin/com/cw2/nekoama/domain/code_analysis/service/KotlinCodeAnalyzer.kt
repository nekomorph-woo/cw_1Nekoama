package com.cw2.nekoama.domain.code_analysis.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*
import com.cw2.nekoama.shared.model.Result
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.domain.code_suggestion_gen.model.AnnotationMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.ClassContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.ClassMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.FieldMetadata
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
 * Kotlin代码分析器
 * 
 * 专门处理Kotlin代码元素的分析，包括函数、类、属性等。
 */
class KotlinCodeAnalyzer(private val project: Project) {
    
    // 说明：为了兼容 Kotlin K2 模式与遵循 PSI 线程约束，所有 PSI 读取均放入 ReadAction 中执行
    
    fun analyzeKotlinFunction(function: KtFunction): Result<MethodContext> {
        return try {
            ReadAction.compute<Result<MethodContext>, Throwable> {
                val parameters = function.valueParameters.map { param ->
                    ParameterMetadata(
                        name = param.name ?: "",
                        type = TypeMetadata(
                            typeName = param.typeReference?.text ?: "Any",
                            fullQualifiedName = param.typeReference?.text ?: "Any"
                        ),
                        annotations = param.annotationEntries.map {
                            AnnotationMetadata(it.shortName?.asString() ?: "", it.typeReference?.text)
                        },
                        hasDefaultValue = param.hasDefaultValue(),
                        defaultValue = param.defaultValue?.text
                    )
                }

                val returnType = TypeMetadata(
                    typeName = function.typeReference?.text ?: "Unit",
                    fullQualifiedName = function.typeReference?.text ?: "Unit"
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
                        imports = emptyList(),
                        fileComments = emptyList(),
                        siblingElements = emptyList()
                    ),
                    methodName = function.name,
                    parameters = parameters,
                    returnType = returnType,
                    modifiers = modifiers,
                    annotations = function.annotationEntries.map {
                        AnnotationMetadata(it.shortName?.asString() ?: "", it.typeReference?.text)
                    },
                    exceptions = emptyList(), // Kotlin doesn't have checked exceptions
                    methodBody = function.bodyExpression?.text ?: function.bodyBlockExpression?.text,
                    isConstructor = function is KtConstructor<*>,
                    isAbstract = function.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                )

                Result.success(methodContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Kotlin函数分析失败: ${e.message}"))
        }
    }
    
    fun analyzeKotlinClass(clazz: KtClass): Result<ClassContext> {
        return try {
            ReadAction.compute<Result<ClassContext>, Throwable> {
                val superClass = clazz.superTypeListEntries
                    .filterIsInstance<KtSuperTypeCallEntry>()
                    .firstOrNull()?.let { superEntry ->
                        TypeMetadata(
                            typeName = superEntry.typeReference?.text ?: "",
                            fullQualifiedName = superEntry.typeReference?.text ?: ""
                        )
                    }

                val interfaces = clazz.superTypeListEntries
                    .filterIsInstance<KtSuperTypeEntry>()
                    .map { interfaceEntry ->
                        TypeMetadata(
                            typeName = interfaceEntry.typeReference?.text ?: "",
                            fullQualifiedName = interfaceEntry.typeReference?.text ?: ""
                        )
                    }

                val modifiers = mutableListOf<String>().apply {
                    if (clazz.hasModifier(KtTokens.PUBLIC_KEYWORD)) add("public")
                    if (clazz.hasModifier(KtTokens.PRIVATE_KEYWORD)) add("private")
                    if (clazz.hasModifier(KtTokens.PROTECTED_KEYWORD)) add("protected")
                    if (clazz.hasModifier(KtTokens.INTERNAL_KEYWORD)) add("internal")
                    if (clazz.hasModifier(KtTokens.ABSTRACT_KEYWORD)) add("abstract")
                    if (clazz.hasModifier(KtTokens.FINAL_KEYWORD)) add("final")
                    if (clazz.hasModifier(KtTokens.OPEN_KEYWORD)) add("open")
                    if (clazz.hasModifier(KtTokens.DATA_KEYWORD)) add("data")
                    if (clazz.hasModifier(KtTokens.SEALED_KEYWORD)) add("sealed")
                    if (clazz.hasModifier(KtTokens.INLINE_KEYWORD)) add("inline")
                }

                val properties = clazz.getProperties().map { property ->
                    FieldMetadata(
                        name = property.name ?: "",
                        type = TypeMetadata(
                            typeName = property.typeReference?.text ?: "Any",
                            fullQualifiedName = property.typeReference?.text ?: "Any"
                        ),
                        modifiers = mutableListOf<String>().apply {
                            if (property.hasModifier(KtTokens.PUBLIC_KEYWORD)) add("public")
                            if (property.hasModifier(KtTokens.PRIVATE_KEYWORD)) add("private")
                            if (property.hasModifier(KtTokens.PROTECTED_KEYWORD)) add("protected")
                            if (property.hasModifier(KtTokens.INTERNAL_KEYWORD)) add("internal")
                            if (property.isVar()) add("var") else add("val")
                        },
                        annotations = property.annotationEntries.map {
                            AnnotationMetadata(it.shortName?.asString() ?: "", it.typeReference?.text)
                        }
                    )
                }

                val methods = clazz.declarations.filterIsInstance<KtFunction>().map { function ->
                    MethodMetadata(
                        name = function.name ?: "",
                        returnType = TypeMetadata(
                            typeName = function.typeReference?.text ?: "Unit",
                            fullQualifiedName = function.typeReference?.text ?: "Unit"
                        ),
                        parameters = function.valueParameters.map { param ->
                            ParameterMetadata(
                                name = param.name ?: "",
                                type = TypeMetadata(
                                    typeName = param.typeReference?.text ?: "Any",
                                    fullQualifiedName = param.typeReference?.text ?: "Any"
                                )
                            )
                        },
                        modifiers = mutableListOf<String>().apply {
                            if (function.hasModifier(KtTokens.PUBLIC_KEYWORD)) add("public")
                            if (function.hasModifier(KtTokens.PRIVATE_KEYWORD)) add("private")
                            if (function.hasModifier(KtTokens.PROTECTED_KEYWORD)) add("protected")
                            if (function.hasModifier(KtTokens.INTERNAL_KEYWORD)) add("internal")
                            if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) add("override")
                            if (function.hasModifier(KtTokens.SUSPEND_KEYWORD)) add("suspend")
                        },
                        annotations = function.annotationEntries.map {
                            AnnotationMetadata(it.shortName?.asString() ?: "", it.typeReference?.text)
                        }
                    )
                }

                val classContext = ClassContext(
                    language = ProgrammingLanguage.KOTLIN,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        precedingCode = emptyList(),
                        followingCode = emptyList(),
                        imports = emptyList(),
                        fileComments = emptyList(),
                        siblingElements = emptyList()
                    ),
                    className = clazz.name,
                    superClass = superClass,
                    interfaces = interfaces,
                    modifiers = modifiers,
                    annotations = clazz.annotationEntries.map {
                        AnnotationMetadata(it.shortName?.asString() ?: "", it.typeReference?.text)
                    },
                    fields = properties,
                    methods = methods,
                    innerClasses = clazz.declarations.filterIsInstance<KtClass>().map { innerClass ->
                        ClassMetadata(
                            name = innerClass.name ?: "",
                            fullQualifiedName = innerClass.containingKtFile.packageDirective?.fqName?.let { packageName ->
                                if (packageName.isRoot) innerClass.name else "${packageName.asString()}.${innerClass.name}"
                            } ?: innerClass.name,
                            isInterface = innerClass.isInterface(),
                            isAbstract = innerClass.hasModifier(KtTokens.ABSTRACT_KEYWORD),
                            isEnum = innerClass.isEnum()
                        )
                    },
                    isInterface = clazz.isInterface(),
                    isAbstract = clazz.hasModifier(KtTokens.ABSTRACT_KEYWORD),
                    isEnum = clazz.isEnum(),
                    packageName = clazz.containingKtFile.packageDirective?.fqName?.asString() ?: ""
                )

                Result.success(classContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Kotlin类分析失败: ${e.message}"))
        }
    }
    
    fun analyzeKotlinProperty(property: KtProperty): Result<VariableContext> {
        return try {
            ReadAction.compute<Result<VariableContext>, Throwable> {
                val propertyType = TypeMetadata(
                    typeName = property.typeReference?.text ?: "Any",
                    fullQualifiedName = property.typeReference?.text ?: "Any",
                    isNullable = property.typeReference?.text?.endsWith("?") == true
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
                        imports = emptyList(),
                        fileComments = emptyList(),
                        siblingElements = emptyList()
                    ),
                    variableName = property.name,
                    variableType = propertyType,
                    modifiers = modifiers,
                    annotations = property.annotationEntries.map {
                        AnnotationMetadata(it.shortName?.asString() ?: "", it.typeReference?.text)
                    },
                    scope = if (property.isTopLevel) VariableScope.GLOBAL else VariableScope.FIELD,
                    isConstant = property.hasModifier(KtTokens.CONST_KEYWORD) || !property.isVar(),
                    isStatic = property.isTopLevel,
                    containingClass = containingClass?.let { cls ->
                        ClassMetadata(
                            name = cls.name ?: "",
                            fullQualifiedName = cls.containingKtFile.packageDirective?.fqName?.let { packageName ->
                                if (packageName.isRoot) cls.name else "${packageName.asString()}.${cls.name}"
                            } ?: cls.name,
                            isInterface = cls.isInterface(),
                            isAbstract = cls.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                        )
                    }
                )

                Result.success(variableContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Kotlin属性分析失败: ${e.message}"))
        }
    }
    
    fun analyzeKotlinParameter(parameter: KtParameter): Result<VariableContext> {
        return try {
            ReadAction.compute<Result<VariableContext>, Throwable> {
                val parameterType = TypeMetadata(
                    typeName = parameter.typeReference?.text ?: "Any",
                    fullQualifiedName = parameter.typeReference?.text ?: "Any",
                    isNullable = parameter.typeReference?.text?.endsWith("?") == true
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
                        imports = emptyList(),
                        fileComments = emptyList(),
                        siblingElements = emptyList()
                    ),
                    variableName = parameter.name,
                    variableType = parameterType,
                    modifiers = modifiers,
                    annotations = parameter.annotationEntries.map {
                        AnnotationMetadata(it.shortName?.asString() ?: "", it.typeReference?.text)
                    },
                    initializer = parameter.defaultValue?.text,
                    scope = VariableScope.PARAMETER,
                    isConstant = !parameter.isMutable,
                    isStatic = false,
                    containingMethod = PsiTreeUtil.getParentOfType(parameter, KtFunction::class.java)?.let { function ->
                        MethodMetadata(
                            name = function.name ?: "",
                            returnType = TypeMetadata(
                                typeName = function.typeReference?.text ?: "Unit",
                                fullQualifiedName = function.typeReference?.text ?: "Unit"
                            )
                        )
                    }
                )

                Result.success(variableContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Kotlin参数分析失败: ${e.message}"))
        }
    }
}
