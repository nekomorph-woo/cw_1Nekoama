package com.cw2.nekoama.domain.code_analysis.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*
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

/**
 * Java代码分析器
 *
 * 专门处理Java代码元素的分析，包括方法、类、变量等。
 *
 * 为了遵循PSI线程约束和确保IDE稳定性，所有PSI访问都在ReadAction中进行。
 */
class JavaCodeAnalyzer(private val project: Project) {

    fun analyzeJavaMethod(method: PsiMethod): Result<MethodContext> {
        return try {
            ReadAction.compute<Result<MethodContext>, Throwable> {
                val parameters = method.parameterList.parameters.map { param ->
                    ParameterMetadata(
                        name = param.name ?: "",
                        type = TypeMetadata(
                            typeName = param.type.presentableText,
                            fullQualifiedName = param.type.canonicalText,
                            isPrimitive = param.type is PsiPrimitiveType
                        ),
                        annotations = param.annotations.map {
                            AnnotationMetadata(it.qualifiedName ?: "", it.qualifiedName)
                        }
                    )
                }

                val returnType = TypeMetadata(
                    typeName = method.returnType?.presentableText ?: "void",
                    fullQualifiedName = method.returnType?.canonicalText ?: "void",
                    isPrimitive = method.returnType is PsiPrimitiveType
                )

                val modifiers = method.modifierList?.let { modifierList ->
                    mutableListOf<String>().apply {
                        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) add("public")
                        if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) add("private")
                        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) add("protected")
                        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) add("static")
                        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) add("final")
                        if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) add("abstract")
                    }
                } ?: emptyList()

                val methodContext = MethodContext(
                    language = ProgrammingLanguage.JAVA,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        precedingCode = emptyList(),
                        followingCode = emptyList(),
                        imports = emptyList(),
                        fileComments = emptyList(),
                        siblingElements = emptyList()
                    ),
                    methodName = method.name,
                    parameters = parameters,
                    returnType = returnType,
                    modifiers = modifiers,
                    annotations = method.annotations.map {
                        AnnotationMetadata(it.qualifiedName ?: "", it.qualifiedName)
                    },
                    exceptions = method.throwsList.referencedTypes.map {
                        TypeMetadata(it.presentableText, it.canonicalText)
                    },
                    methodBody = method.body?.text,
                    isConstructor = method.isConstructor,
                    isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT)
                )

                Result.success(methodContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Java方法分析失败: ${e.message}"))
        }
    }
    
    fun analyzeJavaClass(clazz: PsiClass): Result<ClassContext> {
        return try {
            ReadAction.compute<Result<ClassContext>, Throwable> {
                val superClass = clazz.superClass?.let { superCls ->
                    TypeMetadata(
                        typeName = superCls.name ?: "",
                        fullQualifiedName = superCls.qualifiedName
                    )
                }

                val interfaces = clazz.implementsList?.referencedTypes?.map { interfaceType ->
                    TypeMetadata(
                        typeName = interfaceType.presentableText,
                        fullQualifiedName = interfaceType.canonicalText
                    )
                } ?: emptyList()

                val modifiers = clazz.modifierList?.let { modifierList ->
                    mutableListOf<String>().apply {
                        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) add("public")
                        if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) add("private")
                        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) add("protected")
                        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) add("static")
                        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) add("final")
                        if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) add("abstract")
                    }
                } ?: emptyList()

                val fields = clazz.fields.map { field ->
                    FieldMetadata(
                        name = field.name,
                        type = TypeMetadata(
                            typeName = field.type.presentableText,
                            fullQualifiedName = field.type.canonicalText,
                            isPrimitive = field.type is PsiPrimitiveType
                        ),
                        modifiers = field.modifierList?.let { modList ->
                            mutableListOf<String>().apply {
                                if (modList.hasModifierProperty(PsiModifier.PUBLIC)) add("public")
                                if (modList.hasModifierProperty(PsiModifier.PRIVATE)) add("private")
                                if (modList.hasModifierProperty(PsiModifier.PROTECTED)) add("protected")
                                if (modList.hasModifierProperty(PsiModifier.STATIC)) add("static")
                                if (modList.hasModifierProperty(PsiModifier.FINAL)) add("final")
                            }
                        } ?: emptyList()
                    )
                }

                val methods = clazz.methods.map { method ->
                    MethodMetadata(
                        name = method.name,
                        returnType = TypeMetadata(
                            typeName = method.returnType?.presentableText ?: "void",
                            fullQualifiedName = method.returnType?.canonicalText ?: "void"
                        ),
                        parameters = method.parameterList.parameters.map { param ->
                            ParameterMetadata(
                                name = param.name ?: "",
                                type = TypeMetadata(
                                    typeName = param.type.presentableText,
                                    fullQualifiedName = param.type.canonicalText
                                )
                            )
                        }
                    )
                }

                val classContext = ClassContext(
                    language = ProgrammingLanguage.JAVA,
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
                    annotations = clazz.annotations.map {
                        AnnotationMetadata(it.qualifiedName ?: "", it.qualifiedName)
                    },
                    fields = fields,
                    methods = methods,
                    innerClasses = clazz.innerClasses.map { innerClass ->
                        ClassMetadata(
                            name = innerClass.name ?: "",
                            fullQualifiedName = innerClass.qualifiedName,
                            isInterface = innerClass.isInterface,
                            isAbstract = innerClass.hasModifierProperty(PsiModifier.ABSTRACT)
                        )
                    },
                    isInterface = clazz.isInterface,
                    isAbstract = clazz.hasModifierProperty(PsiModifier.ABSTRACT),
                    isEnum = clazz.isEnum,
                    packageName = (clazz.containingFile as? PsiJavaFile)?.packageName ?: ""
                )

                Result.success(classContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Java类分析失败: ${e.message}"))
        }
    }
    
    fun analyzeJavaVariable(variable: PsiVariable): Result<VariableContext> {
        return try {
            ReadAction.compute<Result<VariableContext>, Throwable> {
                val variableType = TypeMetadata(
                    typeName = variable.type.presentableText,
                    fullQualifiedName = variable.type.canonicalText,
                    isPrimitive = variable.type is PsiPrimitiveType
                )

                val modifiers = variable.modifierList?.let { modifierList ->
                    mutableListOf<String>().apply {
                        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) add("final")
                        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) add("static")
                        if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) add("volatile")
                        if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) add("transient")
                    }
                } ?: emptyList()

                val variableContext = VariableContext(
                    language = ProgrammingLanguage.JAVA,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        precedingCode = emptyList(),
                        followingCode = emptyList(),
                        imports = emptyList(),
                        fileComments = emptyList(),
                        siblingElements = emptyList()
                    ),
                    variableName = variable.name,
                    variableType = variableType,
                    modifiers = modifiers,
                    annotations = variable.annotations.map {
                        AnnotationMetadata(it.qualifiedName ?: "", it.qualifiedName)
                    },
                    initializer = variable.initializer?.text,
                    scope = when (variable) {
                        is PsiField -> VariableScope.FIELD
                        is PsiLocalVariable -> VariableScope.LOCAL
                        is PsiParameter -> VariableScope.PARAMETER
                        else -> VariableScope.LOCAL
                    },
                    isConstant = variable.modifierList?.hasModifierProperty(PsiModifier.FINAL) == true,
                    isStatic = variable.modifierList?.hasModifierProperty(PsiModifier.STATIC) == true
                )

                Result.success(variableContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Java变量分析失败: ${e.message}"))
        }
    }
    
    fun analyzeJavaField(field: PsiField): Result<VariableContext> {
        return try {
            ReadAction.compute<Result<VariableContext>, Throwable> {
                val fieldType = TypeMetadata(
                    typeName = field.type.presentableText,
                    fullQualifiedName = field.type.canonicalText,
                    isPrimitive = field.type is PsiPrimitiveType
                )

                val modifiers = field.modifierList?.let { modifierList ->
                    mutableListOf<String>().apply {
                        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) add("public")
                        if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) add("private")
                        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) add("protected")
                        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) add("static")
                        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) add("final")
                        if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) add("volatile")
                        if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) add("transient")
                    }
                } ?: emptyList()

                val variableContext = VariableContext(
                    language = ProgrammingLanguage.JAVA,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        precedingCode = emptyList(),
                        followingCode = emptyList(),
                        imports = emptyList(),
                        fileComments = emptyList(),
                        siblingElements = emptyList()
                    ),
                    variableName = field.name,
                    variableType = fieldType,
                    modifiers = modifiers,
                    annotations = field.annotations.map {
                        AnnotationMetadata(it.qualifiedName ?: "", it.qualifiedName)
                    },
                    initializer = field.initializer?.text,
                    scope = if (field.hasModifierProperty(PsiModifier.STATIC)) VariableScope.STATIC_FIELD else VariableScope.FIELD,
                    isConstant = field.hasModifierProperty(PsiModifier.FINAL),
                    isStatic = field.hasModifierProperty(PsiModifier.STATIC),
                    containingClass = field.containingClass?.let { containingClass ->
                        ClassMetadata(
                            name = containingClass.name ?: "",
                            fullQualifiedName = containingClass.qualifiedName,
                            isInterface = containingClass.isInterface,
                            isAbstract = containingClass.hasModifierProperty(PsiModifier.ABSTRACT)
                        )
                    }
                )

                Result.success(variableContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Java字段分析失败: ${e.message}"))
        }
    }
}
