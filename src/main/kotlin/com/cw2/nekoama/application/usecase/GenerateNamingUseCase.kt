package com.cw2.nekoama.application.usecase

import com.cw2.nekoama.domain.code_suggestion_gen.model.*
import com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis.CodeAnalysisService
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.shared.model.NekoamaResult
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * 生成命名建议的应用服务用例
 *
 * 职责：
 * - 编排命名生成的业务流程
 * - 分析 PSI 元素并构建 CodeContext
 * - 调用 AI 生成命名建议
 * - 返回生成结果供 Action 层展示
 *
 * @param project IntelliJ 项目实例
 * @param codeAnalysisService 代码分析服务
 * @param generatorFactory 生成器工厂
 */
class GenerateNamingUseCase(
    private val project: com.intellij.openapi.project.Project,
    private val codeAnalysisService: CodeAnalysisService,
    private val generatorFactory: GeneratorFactory
) {

    /**
     * 生成命名建议
     *
     * @param element 目标 PSI 元素
     * @param selectionText 用户选中的文本（可能包含自定义上下文）
     * @return 生成结果，成功时返回命名建议列表，失败时返回错误信息
     */
    suspend fun generateNaming(
        element: PsiElement,
        selectionText: String?
    ): NekoamaResult<List<NamingSuggestion>> {
        // 创建生成器
        val generator = generatorFactory.createGenerator(maxTokens = 150)
            ?: return NekoamaResult.error(NekoamaError.AuthenticationError.ApiKeyNotConfigured("API 未配置"))

        // 构建代码上下文
        val codeContext = buildCodeContext(element, selectionText)
            ?: return NekoamaResult.error(NekoamaError.ParseError.InvalidConfiguration("无法构建代码上下文"))

        // 调用 AI 生成命名建议
        return generator.generateNaming(codeContext)
    }

    /**
     * 提取自定义上下文
     * 支持 #描述# 格式，直接提取 # 之间的内容作为 userIntent
     * 如果匹配成功，返回该 userIntent 并跳过自动推断，否则返回 null
     */
    private fun extractCustomContext(selection: String): String? {
        val pattern = """#\s*([^#]+)\s*#""".toRegex()
        val match = pattern.find(selection)
        return match?.groupValues?.get(1)?.trim()
    }

    /**
     * 构建代码上下文对象
     */
    private fun buildCodeContext(
        element: PsiElement,
        selectionText: String?
    ): CodeContext? {
        return ReadAction.compute<CodeContext?, Throwable> {
            try {
                // 检测是否包含自定义上下文模版（#描述#格式）
                val customContext = selectionText?.let { extractCustomContext(it) }

                val language = codeAnalysisService.detectLanguage(element)
                val projectMetadata = codeAnalysisService.getProjectMetadata()
                val surroundingContext = codeAnalysisService.extractSurroundingContext(element).getOrNull()
                    ?: SurroundingContext(namingPatterns = null)

                // 优先级1：如果用户提供了自定义上下文模版，直接处理该元素对应的 Context
                // 优先级2：如果用户没有自定义上下文模版，使用原有逻辑
                if (customContext != null) {
                    buildContextWithCustomIntent(element, language, projectMetadata, surroundingContext, customContext, selectionText)
                } else {
                    buildContextFromAnalysis(element, language, projectMetadata, surroundingContext)
                }
            } catch (t: Throwable) {
                NekoamaLogger.logError(
                    "GenerateNamingUseCase.buildCodeContext",
                    NekoamaError.ParseError.InvalidConfiguration("构建代码上下文失败: ${t.message}"),
                    mapOf("exception" to (t.message ?: "unknown"))
                )
                null
            }
        }
    }

    /**
     * 使用自定义意图构建上下文
     */
    private fun buildContextWithCustomIntent(
        element: PsiElement,
        language: ProgrammingLanguage,
        projectMetadata: ProjectMetadata,
        surroundingContext: SurroundingContext,
        userIntent: String,
        selectionText: String?
    ): CodeContext? {
        return ReadAction.compute<CodeContext?, Throwable> {
            when {
                PsiTreeUtil.getParentOfType(element, KtFunction::class.java) != null -> {
                    val fn = PsiTreeUtil.getParentOfType(element, KtFunction::class.java)!!
                    MethodContext(
                        language = language,
                        projectMeta = projectMetadata,
                        surroundingContext = surroundingContext,
                        userIntent = userIntent,
                        methodName = fn.name,
                        parameters = emptyList(),
                        returnType = TypeMetadata("Unit"),
                        modifiers = emptyList(),
                        annotations = emptyList(),
                        exceptions = emptyList(),
                        methodBody = fn.bodyExpression?.text,
                        isConstructor = false,
                        isAbstract = false,
                        containingClass = null
                    )
                }

                PsiTreeUtil.getParentOfType(element, KtProperty::class.java) != null -> {
                    val prop = PsiTreeUtil.getParentOfType(element, KtProperty::class.java)!!
                    VariableContext(
                        language = language,
                        projectMeta = projectMetadata,
                        surroundingContext = surroundingContext,
                        userIntent = userIntent,
                        variableName = prop.name,
                        variableType = TypeMetadata("Any"),
                        modifiers = emptyList(),
                        annotations = emptyList(),
                        initializer = prop.initializer?.text,
                        scope = VariableScope.LOCAL,
                        isConstant = prop.isVar.not(),
                        isStatic = false,
                        containingClass = null,
                        containingMethod = null
                    )
                }

                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiMethod::class.java) != null -> {
                    val method = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiMethod::class.java)!!
                    MethodContext(
                        language = language,
                        projectMeta = projectMetadata,
                        surroundingContext = surroundingContext,
                        userIntent = userIntent,
                        methodName = method.name,
                        parameters = emptyList(),
                        returnType = TypeMetadata("void"),
                        modifiers = emptyList(),
                        annotations = emptyList(),
                        exceptions = emptyList(),
                        methodBody = method.body?.text,
                        isConstructor = method.isConstructor,
                        isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT),
                        containingClass = null
                    )
                }

                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiField::class.java) != null -> {
                    val field = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiField::class.java)!!
                    VariableContext(
                        language = language,
                        projectMeta = projectMetadata,
                        surroundingContext = surroundingContext,
                        userIntent = userIntent,
                        variableName = field.name,
                        variableType = TypeMetadata(field.type.presentableText),
                        modifiers = emptyList(),
                        annotations = emptyList(),
                        initializer = field.initializer?.text,
                        scope = if (field.hasModifierProperty(PsiModifier.STATIC)) VariableScope.STATIC_FIELD else VariableScope.FIELD,
                        isConstant = field.hasModifierProperty(PsiModifier.FINAL),
                        isStatic = field.hasModifierProperty(PsiModifier.STATIC),
                        containingClass = null,
                        containingMethod = null
                    )
                }

                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiLocalVariable::class.java) != null -> {
                    val localVar = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiLocalVariable::class.java)!!
                    VariableContext(
                        language = language,
                        projectMeta = projectMetadata,
                        surroundingContext = surroundingContext,
                        userIntent = userIntent,
                        variableName = localVar.name,
                        variableType = TypeMetadata(localVar.type.presentableText),
                        modifiers = emptyList(),
                        annotations = emptyList(),
                        initializer = localVar.initializer?.text,
                        scope = VariableScope.LOCAL,
                        isConstant = localVar.hasModifierProperty(PsiModifier.FINAL),
                        isStatic = false,
                        containingClass = null,
                        containingMethod = null
                    )
                }

                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiParameter::class.java) != null -> {
                    val param = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiParameter::class.java)!!
                    VariableContext(
                        language = language,
                        projectMeta = projectMetadata,
                        surroundingContext = surroundingContext,
                        userIntent = userIntent,
                        variableName = param.name,
                        variableType = TypeMetadata(param.type.presentableText),
                        modifiers = emptyList(),
                        annotations = emptyList(),
                        initializer = null,
                        scope = VariableScope.PARAMETER,
                        isConstant = param.hasModifierProperty(PsiModifier.FINAL),
                        isStatic = false,
                        containingClass = null,
                        containingMethod = null
                    )
                }

                PsiTreeUtil.getParentOfType(element, KtClass::class.java) != null -> {
                    val cls = PsiTreeUtil.getParentOfType(element, KtClass::class.java)!!
                    ClassContext(
                        language = language,
                        projectMeta = projectMetadata,
                        surroundingContext = surroundingContext,
                        userIntent = userIntent,
                        className = cls.name,
                        superClass = null,
                        packageName = cls.containingKtFile.packageDirective?.fqName?.asString() ?: "",
                        isInterface = cls.isInterface(),
                        isAbstract = cls.hasModifier(KtTokens.ABSTRACT_KEYWORD),
                        isEnum = cls.isEnum()
                    )
                }

                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiClass::class.java) != null -> {
                    val cls = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiClass::class.java)!!
                    ClassContext(
                        language = language,
                        projectMeta = projectMetadata,
                        surroundingContext = surroundingContext,
                        userIntent = userIntent,
                        className = cls.name,
                        superClass = null,
                        packageName = (cls.containingFile as? com.intellij.psi.PsiJavaFile)?.packageName ?: "",
                        isInterface = cls.isInterface,
                        isAbstract = cls.hasModifierProperty(PsiModifier.ABSTRACT),
                        isEnum = cls.isEnum
                    )
                }

                // 如果没有找到特定元素，则使用通用的命名建议
                else -> {
                    MethodContext(
                        language = language,
                        projectMeta = projectMetadata,
                        surroundingContext = surroundingContext,
                        userIntent = userIntent,
                        methodName = null,
                        parameters = emptyList(),
                        returnType = TypeMetadata("Any"),
                        modifiers = emptyList(),
                        annotations = emptyList(),
                        exceptions = emptyList(),
                        methodBody = selectionText,
                        isConstructor = false,
                        isAbstract = false,
                        containingClass = null
                    )
                }
            }
        }
    }

    /**
     * 通过分析服务构建上下文
     */
    private fun buildContextFromAnalysis(
        element: PsiElement,
        language: ProgrammingLanguage,
        projectMetadata: ProjectMetadata,
        surroundingContext: SurroundingContext
    ): CodeContext? {
        return ReadAction.compute<CodeContext?, Throwable> {
            when {
                PsiTreeUtil.getParentOfType(element, KtFunction::class.java) != null -> {
                    val fn = PsiTreeUtil.getParentOfType(element, KtFunction::class.java)!!
                    val analyzeResult = codeAnalysisService.analyzeMethod(fn)
                    if (analyzeResult.isSuccess) {
                        analyzeResult.getOrNull() as? MethodContext
                    } else {
                        // 构造默认的 MethodContext（Kotlin）
                        MethodContext(
                            language = language,
                            projectMeta = projectMetadata,
                            surroundingContext = surroundingContext,
                            methodName = fn.name,
                            parameters = emptyList(),
                            returnType = TypeMetadata("Unit"),
                            modifiers = emptyList(),
                            annotations = emptyList(),
                            exceptions = emptyList(),
                            methodBody = fn.bodyExpression?.text,
                            isConstructor = false,
                            isAbstract = false,
                            containingClass = null
                        )
                    }
                }

                PsiTreeUtil.getParentOfType(element, KtProperty::class.java) != null -> {
                    val prop = PsiTreeUtil.getParentOfType(element, KtProperty::class.java)!!
                    val analyzeResult = codeAnalysisService.analyzeVariable(prop)
                    if (analyzeResult.isSuccess) {
                        analyzeResult.getOrNull() as? VariableContext
                    } else {
                        // 构造默认的 VariableContext（Kotlin）
                        VariableContext(
                            language = language,
                            projectMeta = projectMetadata,
                            surroundingContext = surroundingContext,
                            variableName = prop.name,
                            variableType = TypeMetadata("Any"),
                            modifiers = emptyList(),
                            annotations = emptyList(),
                            initializer = prop.initializer?.text,
                            scope = VariableScope.LOCAL,
                            isConstant = prop.isVar.not(),
                            isStatic = false,
                            containingClass = null,
                            containingMethod = null
                        )
                    }
                }

                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiMethod::class.java) != null -> {
                    val method = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiMethod::class.java)!!
                    val analyzeResult = codeAnalysisService.analyzeMethod(method)
                    if (analyzeResult.isSuccess) {
                        analyzeResult.getOrNull() as? MethodContext
                    } else {
                        // 构造默认的 MethodContext（Java）
                        MethodContext(
                            language = language,
                            projectMeta = projectMetadata,
                            surroundingContext = surroundingContext,
                            methodName = method.name,
                            parameters = emptyList(),
                            returnType = TypeMetadata(method.returnType?.presentableText ?: "void"),
                            modifiers = emptyList(),
                            annotations = emptyList(),
                            exceptions = emptyList(),
                            methodBody = method.body?.text,
                            isConstructor = method.isConstructor,
                            isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT),
                            containingClass = null
                        )
                    }
                }

                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiField::class.java) != null -> {
                    val field = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiField::class.java)!!
                    val analyzeResult = codeAnalysisService.analyzeVariable(field)
                    if (analyzeResult.isSuccess) {
                        analyzeResult.getOrNull() as? VariableContext
                    } else {
                        // 构造默认的 VariableContext（Java 字段）
                        VariableContext(
                            language = language,
                            projectMeta = projectMetadata,
                            surroundingContext = surroundingContext,
                            variableName = field.name,
                            variableType = TypeMetadata(field.type.presentableText),
                            modifiers = emptyList(),
                            annotations = emptyList(),
                            initializer = field.initializer?.text,
                            scope = if (field.hasModifierProperty(PsiModifier.STATIC)) VariableScope.STATIC_FIELD else VariableScope.FIELD,
                            isConstant = field.hasModifierProperty(PsiModifier.FINAL),
                            isStatic = field.hasModifierProperty(PsiModifier.STATIC),
                            containingClass = null,
                            containingMethod = null
                        )
                    }
                }

                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiVariable::class.java) != null -> {
                    val v = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiVariable::class.java)!!
                    val analyzeResult = codeAnalysisService.analyzeVariable(v)
                    if (analyzeResult.isSuccess) {
                        analyzeResult.getOrNull() as? VariableContext
                    } else {
                        // 构造默认的 VariableContext（Java 局部变量/参数）
                        VariableContext(
                            language = language,
                            projectMeta = projectMetadata,
                            surroundingContext = surroundingContext,
                            variableName = v.name,
                            variableType = TypeMetadata(v.type.presentableText),
                            modifiers = emptyList(),
                            annotations = emptyList(),
                            initializer = v.initializer?.text,
                            scope = VariableScope.LOCAL,
                            isConstant = false,
                            isStatic = false,
                            containingClass = null,
                            containingMethod = null
                        )
                    }
                }

                // 专门处理局部变量和参数
                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiLocalVariable::class.java) != null -> {
                    val localVar = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiLocalVariable::class.java)!!
                    val analyzeResult = codeAnalysisService.analyzeVariable(localVar)
                    if (analyzeResult.isSuccess) {
                        analyzeResult.getOrNull() as? VariableContext
                    } else {
                        VariableContext(
                            language = language,
                            projectMeta = projectMetadata,
                            surroundingContext = surroundingContext,
                            variableName = localVar.name,
                            variableType = TypeMetadata(localVar.type.presentableText),
                            modifiers = emptyList(),
                            annotations = emptyList(),
                            initializer = localVar.initializer?.text,
                            scope = VariableScope.LOCAL,
                            isConstant = localVar.hasModifierProperty(PsiModifier.FINAL),
                            isStatic = false,
                            containingClass = null,
                            containingMethod = null
                        )
                    }
                }

                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiParameter::class.java) != null -> {
                    val param = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiParameter::class.java)!!
                    val analyzeResult = codeAnalysisService.analyzeVariable(param)
                    if (analyzeResult.isSuccess) {
                        analyzeResult.getOrNull() as? VariableContext
                    } else {
                        VariableContext(
                            language = language,
                            projectMeta = projectMetadata,
                            surroundingContext = surroundingContext,
                            variableName = param.name,
                            variableType = TypeMetadata(param.type.presentableText),
                            modifiers = emptyList(),
                            annotations = emptyList(),
                            initializer = null,
                            scope = VariableScope.PARAMETER,
                            isConstant = param.hasModifierProperty(PsiModifier.FINAL),
                            isStatic = false,
                            containingClass = null,
                            containingMethod = null
                        )
                    }
                }

                PsiTreeUtil.getParentOfType(element, KtClass::class.java) != null -> {
                    val cls = PsiTreeUtil.getParentOfType(element, KtClass::class.java)!!
                    val analyzeResult = codeAnalysisService.analyzeClass(cls)
                    if (analyzeResult.isSuccess) {
                        analyzeResult.getOrNull() as? ClassContext
                    } else {
                        ClassContext(
                            language = language,
                            projectMeta = projectMetadata,
                            surroundingContext = surroundingContext,
                            className = cls.name,
                            superClass = null,
                            packageName = cls.containingKtFile.packageDirective?.fqName?.asString() ?: "",
                            isInterface = cls.isInterface(),
                            isAbstract = cls.hasModifier(KtTokens.ABSTRACT_KEYWORD),
                            isEnum = cls.isEnum()
                        )
                    }
                }

                PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiClass::class.java) != null -> {
                    val cls = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiClass::class.java)!!
                    val analyzeResult = codeAnalysisService.analyzeClass(cls)
                    if (analyzeResult.isSuccess) {
                        analyzeResult.getOrNull() as? ClassContext
                    } else {
                        ClassContext(
                            language = language,
                            projectMeta = projectMetadata,
                            surroundingContext = surroundingContext,
                            className = cls.name,
                            superClass = null,
                            packageName = (cls.containingFile as? com.intellij.psi.PsiJavaFile)?.packageName ?: "",
                            isInterface = cls.isInterface,
                            isAbstract = cls.hasModifierProperty(PsiModifier.ABSTRACT),
                            isEnum = cls.isEnum
                        )
                    }
                }

                else -> null
            }
        }
    }
}
