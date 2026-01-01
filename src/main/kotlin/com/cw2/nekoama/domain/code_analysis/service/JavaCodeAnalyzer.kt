package com.cw2.nekoama.domain.code_analysis.service

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
 * Java���������
 *
 * ר�Ŵ���Java����Ԫ�صķ����������������ࡢ�����ȡ�
 *
 * Ϊ����ѭPSI�߳�Լ����ȷ��IDE�ȶ��ԣ�����PSI���ʶ���ReadAction�н��С�
 */
class JavaCodeAnalyzer(private val project: Project) {

    fun analyzeJavaMethod(method: PsiMethod): Result<MethodContext> {
        return try {
            ReadAction.compute<Result<MethodContext>, Throwable> {
                val parameters = method.parameterList.parameters.map { param ->
                    ParameterMetadata(
                        name = param.name ?: "",
                        type = TypeMetadata(
                            typeName = param.type.presentableText
                        ),
                        annotations = param.annotations.map {
                            AnnotationMetadata(it.qualifiedName ?: "")
                        }
                    )
                }

                val returnType = TypeMetadata(
                    typeName = method.returnType?.presentableText ?: "void"
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
                        imports = emptyList()
                    ),
                    methodName = method.name,
                    parameters = parameters,
                    returnType = returnType,
                    modifiers = modifiers,
                    annotations = method.annotations.map {
                        AnnotationMetadata(it.qualifiedName ?: "")
                    },
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
            Result.error(NekoamaError.Unknown("Java��������ʧ��: ${e.message}"))
        }
    }
    
    fun analyzeJavaClass(clazz: PsiClass): Result<ClassContext> {
        return try {
            ReadAction.compute<Result<ClassContext>, Throwable> {
                val superClass = clazz.superClass?.let { superCls ->
                    TypeMetadata(
                        typeName = superCls.name ?: ""
                    )
                }

                val classContext = ClassContext(
                    language = ProgrammingLanguage.JAVA,
                    projectMeta = ProjectMetadata(project.name),
                    surroundingContext = SurroundingContext(
                        precedingCode = emptyList(),
                        followingCode = emptyList(),
                        imports = emptyList()
                    ),
                    className = clazz.name,
                    superClass = superClass,
                    isInterface = clazz.isInterface,
                    isAbstract = clazz.hasModifierProperty(PsiModifier.ABSTRACT),
                    isEnum = clazz.isEnum,
                    packageName = (clazz.containingFile as? PsiJavaFile)?.packageName ?: ""
                )

                Result.success(classContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Java�����ʧ��: ${e.message}"))
        }
    }
    
    fun analyzeJavaVariable(variable: PsiVariable): Result<VariableContext> {
        return try {
            ReadAction.compute<Result<VariableContext>, Throwable> {
                val variableType = TypeMetadata(
                    typeName = variable.type.presentableText
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
                        imports = emptyList()
                    ),
                    variableName = variable.name,
                    variableType = variableType,
                    modifiers = modifiers,
                    annotations = variable.annotations.map {
                        AnnotationMetadata(it.qualifiedName ?: "")
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
            Result.error(NekoamaError.Unknown("Java��������ʧ��: ${e.message}"))
        }
    }
    
    fun analyzeJavaField(field: PsiField): Result<VariableContext> {
        return try {
            ReadAction.compute<Result<VariableContext>, Throwable> {
                val fieldType = TypeMetadata(
                    typeName = field.type.presentableText
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
                        imports = emptyList()
                    ),
                    variableName = field.name,
                    variableType = fieldType,
                    modifiers = modifiers,
                    annotations = field.annotations.map {
                        AnnotationMetadata(it.qualifiedName ?: "")
                    },
                    initializer = field.initializer?.text,
                    scope = if (field.hasModifierProperty(PsiModifier.STATIC)) VariableScope.STATIC_FIELD else VariableScope.FIELD,
                    isConstant = field.hasModifierProperty(PsiModifier.FINAL),
                    isStatic = field.hasModifierProperty(PsiModifier.STATIC),
                    containingClass = field.containingClass?.let { containingClass ->
                        ClassMetadata(
                            name = containingClass.name ?: ""
                        )
                    }
                )

                Result.success(variableContext)
            }
        } catch (e: Exception) {
            Result.error(NekoamaError.Unknown("Java�ֶη���ʧ��: ${e.message}"))
        }
    }
}
