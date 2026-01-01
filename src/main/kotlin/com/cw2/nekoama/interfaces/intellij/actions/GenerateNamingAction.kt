package com.cw2.nekoama.interfaces.intellij.actions

import com.cw2.nekoama.domain.code_suggestion_gen.model.CodeSuggestionGenerator
import com.cw2.nekoama.infrastructure.code_suggestion_gen.client.openai.OpenAIGenerator
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config.CustomGeneratorConfig
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.domain.settings.service.NekoamaSecureStorage
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis.CodeAnalysisService
import com.cw2.nekoama.infrastructure.code_suggestion_gen.code_analysis.UniversalCodeElementAnalyzer
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.shared.util.NekoamaNotifier
import com.cw2.nekoama.domain.code_suggestion_gen.model.ClassContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.CodeContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.MethodContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.SurroundingContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.TypeMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.VariableContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.VariableScope
import com.cw2.nekoama.shared.exception.NekoamaError
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * 生成命名建议的动作
 *
 * 实现策略：
 * - 在后台线程分析光标处的 PSI 元素
 * - 根据方法/字段分别构建实体的 CodeContext
 * - 调用现有的 AI Provider 生成实际命名建议
 * - 展示AI生成的多个命名建议供用户选择
 */
internal class GenerateNamingAction : BaseAction() {

    override fun perform(project: Project, editor: Editor?, e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: run {
            NekoamaNotifier.warn(NekoamaBundle.message("action.naming.noPsiFile"))
            return
        }
        // 优先获取使用光标位置的 PSI 元素（避免右键点击触发时，菜单中可能包含的 PSI 元素，而是右键位置）
        val element = elementAtCaret(editor!!, psiFile) ?: e.getData(CommonDataKeys.PSI_ELEMENT)
        if (element == null) {
            NekoamaNotifier.warn(NekoamaBundle.message("action.naming.noElement"))
            return
        }

        // 在EDT线程预先获取选中的文本，避免后台线程直接访问 UI
        val selectionText = ReadAction.compute<String?, Throwable> {
            editor!!.selectionModel.selectedText
        }

        val title = NekoamaBundle.message("action.generateNaming.text")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = NekoamaBundle.message("progress.analyzingContext")

                try {
                    // 创建 AI Provider 实例（固定使用 Custom API）
                    val generator = createCodeSuggestionGenerator()
                    if (generator == null) {
                        NekoamaNotifier.warn(NekoamaBundle.message("settings.api.notConfigured"))
                        return
                    }

                    // 构建代码上下文对象
                    val codeContext = buildCodeContext(project, selectionText, element, indicator)
                    if (codeContext == null || indicator.isCanceled) return

                    indicator.text = NekoamaBundle.message("progress.generatingNaming")

                    // 调用 AI 生成命名建议
                    val result = runBlocking {
                        generator.generateNaming(codeContext)
                    }

                    if (indicator.isCanceled) return

                    // 处理结果
                    val message = if (result.isSuccess) {
                        val suggestions = result.getOrNull() ?: emptyList()
                        if (suggestions.isNotEmpty()) {
                            val suggestionList = suggestions.take(5).joinToString(" / ") { it.name }
                            NekoamaBundle.message("action.naming.suggestions", suggestionList)
                        } else {
                            NekoamaBundle.message("action.naming.noSuggestions")
                        }
                    } else {
                        val error = result.getOrNull()
                        val errMsg = error ?: NekoamaBundle.message("common.unknownError")
                        NekoamaBundle.message("action.common.failed", errMsg)
                    }

                    NekoamaNotifier.info(message)

                } catch (t: Throwable) {
                    NekoamaLogger.logError(
                        "GenerateNamingAction",
                        NekoamaError.APIError.ServerError("命名生成异常: ${t.message}"),
                        mapOf("exception" to (t.message ?: "unknown"))
                    )
                    run {
                        val errMsg = t.message ?: NekoamaBundle.message("common.unknownError")
                        NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                    }
                }
            }
        })
    }

    override fun requiresEditor(): Boolean = true

    private fun elementAtCaret(editor: Editor?, psiFile: PsiFile): PsiElement? {
        val offset = editor!!.caretModel.offset
        val element = psiFile.findElementAt(offset)
        if (element != null) {
            // 尝试查找是否是局部变量（优先 Kotlin 和 Java）
            val ktVar = PsiTreeUtil.getParentOfType(element, KtProperty::class.java)
            if (ktVar != null) return ktVar
            val psiVar = PsiTreeUtil.getParentOfType(element, PsiLocalVariable::class.java)
            if (psiVar != null) return psiVar
            val psiParam = PsiTreeUtil.getParentOfType(element, PsiParameter::class.java)
            if (psiParam != null) return psiParam
        }
        return element
    }

    /**
     * 创建 AI Provider 实例（固定使用 Custom API）
     */
    private fun createCodeSuggestionGenerator(): CodeSuggestionGenerator? {
        val settings = NekoamaSettings.getInstance()
        val secureKey = NekoamaSecureStorage.getApiKeySync()
        val resolvedKey =
            if (secureKey.isNotBlank()) secureKey else settings.apiKey.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }

        if (resolvedKey.isBlank() || settings.apiEndpoint.isBlank()) return null

        return OpenAIGenerator(
            CustomGeneratorConfig(
                generatorName = "Custom API",
                apiUrl = settings.apiEndpoint,
                apiKey = resolvedKey,
                model = settings.model,
                temperature = settings.modelTemperature / 100.0,
                timeoutMs = settings.requestTimeoutMs.toLong(),
                maxTokens = 150
            )
        )
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
        project: Project,
        selectionText: String?,
        element: PsiElement,
        indicator: ProgressIndicator
    ): CodeContext? {
        // 检测是否包含自定义上下文模版（#描述#格式）
        val customContext = selectionText?.let { extractCustomContext(it) }

        return try {
            ReadAction.compute<CodeContext?, Throwable> {
                val codeAnalysisService = CodeAnalysisService(UniversalCodeElementAnalyzer(project))
                val language = codeAnalysisService.detectLanguage(element)
                val projectMetadata = codeAnalysisService.getProjectMetadata()
                val surroundingContext = codeAnalysisService.extractSurroundingContext(element).getOrNull() ?: SurroundingContext(
                    namingPatterns = null
                )

                // 优先级1：如果用户提供了自定义上下文模版，直接处理该元素对应的 Context
                if (customContext != null) {
                    return@compute when {
                        // 检测是否是特定的 PSI 元素
                        PsiTreeUtil.getParentOfType(element, KtFunction::class.java) != null -> {
                            val fn = PsiTreeUtil.getParentOfType(element, KtFunction::class.java)!!
                            MethodContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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
                                userIntent = customContext,
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

                        PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) != null -> {
                            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)!!
                            MethodContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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

                        PsiTreeUtil.getParentOfType(element, PsiField::class.java) != null -> {
                            val field = PsiTreeUtil.getParentOfType(element, PsiField::class.java)!!
                            VariableContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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

                        PsiTreeUtil.getParentOfType(element, PsiLocalVariable::class.java) != null -> {
                            val localVar = PsiTreeUtil.getParentOfType(element, PsiLocalVariable::class.java)!!
                            VariableContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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

                        PsiTreeUtil.getParentOfType(element, PsiParameter::class.java) != null -> {
                            val param = PsiTreeUtil.getParentOfType(element, PsiParameter::class.java)!!
                            VariableContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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
                                userIntent = customContext,
                                className = cls.name,
                                superClass = null,
                                packageName = cls.containingKtFile.packageDirective?.fqName?.asString() ?: "",
                                isInterface = cls.isInterface(),
                                isAbstract = cls.hasModifier(KtTokens.ABSTRACT_KEYWORD),
                                isEnum = cls.isEnum()
                            )
                        }

                        PsiTreeUtil.getParentOfType(element, PsiClass::class.java) != null -> {
                            val cls = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)!!
                            ClassContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                className = cls.name,
                                superClass = null,
                                packageName = (cls.containingFile as? PsiJavaFile)?.packageName ?: "",
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
                                userIntent = customContext,
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

                // 如果用户没有自定义上下文模版，使用原有逻辑
                when {
                    PsiTreeUtil.getParentOfType(element, KtFunction::class.java) != null -> {
                        val fn = PsiTreeUtil.getParentOfType(element, KtFunction::class.java)!!
                        val analyzeResult = codeAnalysisService.analyzeMethod(fn)
                        if (analyzeResult.isSuccess) {
                            // 如果存在自定义上下文模版，添加 userIntent 到 MethodContext
                            if (customContext != null) {
                                MethodContext(
                                    language = language,
                                    projectMeta = projectMetadata,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    methodName = fn.name,
                                    parameters = analyzeResult.getOrNull()?.parameters ?: emptyList(),
                                    returnType = analyzeResult.getOrNull()?.returnType ?: TypeMetadata("Unit"),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    exceptions = analyzeResult.getOrNull()?.exceptions ?: emptyList(),
                                    methodBody = fn.bodyExpression?.text,
                                    isConstructor = false,
                                    isAbstract = false,
                                    containingClass = analyzeResult.getOrNull()?.containingClass
                                )
                            } else {
                                analyzeResult.getOrNull() as? MethodContext
                            }
                        } else {
                            // 构造默认的 MethodContext（Kotlin）
                            MethodContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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
                            // 如果存在自定义上下文模版，添加 userIntent 到 VariableContext
                            if (customContext != null) {
                                VariableContext(
                                    language = language,
                                    projectMeta = projectMetadata,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    variableName = prop.name,
                                    variableType = analyzeResult.getOrNull()?.variableType ?: TypeMetadata("Any"),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    initializer = prop.initializer?.text,
                                    scope = analyzeResult.getOrNull()?.scope
                                        ?: VariableScope.LOCAL,
                                    isConstant = prop.isVar.not(),
                                    isStatic = false,
                                    containingClass = analyzeResult.getOrNull()?.containingClass,
                                    containingMethod = analyzeResult.getOrNull()?.containingMethod
                                )
                            } else {
                                analyzeResult.getOrNull() as? VariableContext
                            }
                        } else {
                            // 构造默认的 VariableContext（Kotlin）
                            VariableContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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

                    PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) != null -> {
                        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)!!
                        val analyzeResult = codeAnalysisService.analyzeMethod(method)
                        if (analyzeResult.isSuccess) {
                            // 如果存在自定义上下文模版，添加 userIntent 到 MethodContext
                            if (customContext != null) {
                                MethodContext(
                                    language = language,
                                    projectMeta = projectMetadata,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    methodName = method.name,
                                    parameters = analyzeResult.getOrNull()?.parameters ?: emptyList(),
                                    returnType = analyzeResult.getOrNull()?.returnType ?: TypeMetadata("void"),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    exceptions = analyzeResult.getOrNull()?.exceptions ?: emptyList(),
                                    methodBody = method.body?.text,
                                    isConstructor = method.isConstructor,
                                    isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT),
                                    containingClass = analyzeResult.getOrNull()?.containingClass
                                )
                            } else {
                                analyzeResult.getOrNull() as? MethodContext
                            }
                        } else {
                            // 构造默认的 MethodContext（Java）
                            MethodContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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

                    PsiTreeUtil.getParentOfType(element, PsiField::class.java) != null -> {
                        val field = PsiTreeUtil.getParentOfType(element, PsiField::class.java)!!
                        val analyzeResult = codeAnalysisService.analyzeVariable(field)
                        if (analyzeResult.isSuccess) {
                            // 如果存在自定义上下文模版，添加 userIntent 到 VariableContext
                            if (customContext != null) {
                                VariableContext(
                                    language = language,
                                    projectMeta = projectMetadata,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    variableName = field.name,
                                    variableType = analyzeResult.getOrNull()?.variableType
                                        ?: TypeMetadata(field.type.presentableText),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    initializer = field.initializer?.text,
                                    scope = analyzeResult.getOrNull()?.scope ?: if (field.hasModifierProperty(
                                            PsiModifier.STATIC
                                        )
                                    ) VariableScope.STATIC_FIELD else VariableScope.FIELD,
                                    isConstant = field.hasModifierProperty(PsiModifier.FINAL),
                                    isStatic = field.hasModifierProperty(PsiModifier.STATIC),
                                    containingClass = analyzeResult.getOrNull()?.containingClass,
                                    containingMethod = analyzeResult.getOrNull()?.containingMethod
                                )
                            } else {
                                analyzeResult.getOrNull() as? VariableContext
                            }
                        } else {
                            // 构造默认的 VariableContext（Java 字段）
                            VariableContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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

                    PsiTreeUtil.getParentOfType(element, PsiVariable::class.java) != null -> {
                        val v = PsiTreeUtil.getParentOfType(element, PsiVariable::class.java)!!
                        val analyzeResult = codeAnalysisService.analyzeVariable(v)
                        if (analyzeResult.isSuccess) {
                            // 如果存在自定义上下文模版，添加 userIntent 到 VariableContext
                            if (customContext != null) {
                                VariableContext(
                                    language = language,
                                    projectMeta = projectMetadata,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    variableName = v.name,
                                    variableType = analyzeResult.getOrNull()?.variableType
                                        ?: TypeMetadata(v.type.presentableText),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    initializer = v.initializer?.text,
                                    scope = analyzeResult.getOrNull()?.scope
                                        ?: VariableScope.LOCAL,
                                    isConstant = false,
                                    isStatic = false,
                                    containingClass = analyzeResult.getOrNull()?.containingClass,
                                    containingMethod = analyzeResult.getOrNull()?.containingMethod
                                )
                            } else {
                                analyzeResult.getOrNull() as? VariableContext
                            }
                        } else {
                            // 构造默认的 VariableContext（Java 局部变量/参数）
                            VariableContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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
                    PsiTreeUtil.getParentOfType(element, PsiLocalVariable::class.java) != null -> {
                        val localVar = PsiTreeUtil.getParentOfType(element, PsiLocalVariable::class.java)!!
                        val analyzeResult = codeAnalysisService.analyzeVariable(localVar)
                        if (analyzeResult.isSuccess) {
                            // 如果存在自定义上下文模版，添加 userIntent 到 VariableContext
                            if (customContext != null) {
                                VariableContext(
                                    language = language,
                                    projectMeta = projectMetadata,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    variableName = localVar.name,
                                    variableType = analyzeResult.getOrNull()?.variableType
                                        ?: TypeMetadata(localVar.type.presentableText),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    initializer = localVar.initializer?.text,
                                    scope = analyzeResult.getOrNull()?.scope
                                        ?: VariableScope.LOCAL,
                                    isConstant = localVar.hasModifierProperty(PsiModifier.FINAL),
                                    isStatic = false,
                                    containingClass = analyzeResult.getOrNull()?.containingClass,
                                    containingMethod = analyzeResult.getOrNull()?.containingMethod
                                )
                            } else {
                                analyzeResult.getOrNull() as? VariableContext
                            }
                        } else {
                            VariableContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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

                    PsiTreeUtil.getParentOfType(element, PsiParameter::class.java) != null -> {
                        val param = PsiTreeUtil.getParentOfType(element, PsiParameter::class.java)!!
                        val analyzeResult = codeAnalysisService.analyzeVariable(param)
                        if (analyzeResult.isSuccess) {
                            // 如果存在自定义上下文模版，添加 userIntent 到 VariableContext
                            if (customContext != null) {
                                VariableContext(
                                    language = language,
                                    projectMeta = projectMetadata,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    variableName = param.name,
                                    variableType = analyzeResult.getOrNull()?.variableType
                                        ?: TypeMetadata(param.type.presentableText),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    initializer = null,
                                    scope = VariableScope.PARAMETER,
                                    isConstant = param.hasModifierProperty(PsiModifier.FINAL),
                                    isStatic = false,
                                    containingClass = analyzeResult.getOrNull()?.containingClass,
                                    containingMethod = analyzeResult.getOrNull()?.containingMethod
                                )
                            } else {
                                analyzeResult.getOrNull() as? VariableContext
                            }
                        } else {
                            VariableContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
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
                            // 如果存在自定义上下文模版，添加 userIntent 到 ClassContext
                            if (customContext != null) {
                                ClassContext(
                                    language = language,
                                    projectMeta = projectMetadata,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    className = cls.name,
                                    superClass = analyzeResult.getOrNull()?.superClass,
                                    packageName = cls.containingKtFile.packageDirective?.fqName?.asString() ?: "",
                                    isInterface = cls.isInterface(),
                                    isAbstract = cls.hasModifier(KtTokens.ABSTRACT_KEYWORD),
                                    isEnum = cls.isEnum()
                                )
                            } else {
                                analyzeResult.getOrNull() as? ClassContext
                            }
                        } else {
                            ClassContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                className = cls.name,
                                superClass = null,
                                packageName = cls.containingKtFile.packageDirective?.fqName?.asString() ?: "",
                                isInterface = cls.isInterface(),
                                isAbstract = cls.hasModifier(KtTokens.ABSTRACT_KEYWORD),
                                isEnum = cls.isEnum()
                            )
                        }
                    }

                    PsiTreeUtil.getParentOfType(element, PsiClass::class.java) != null -> {
                        val cls = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)!!
                        val analyzeResult = codeAnalysisService.analyzeClass(cls)
                        if (analyzeResult.isSuccess) {
                            // 如果存在自定义上下文模版，添加 userIntent 到 ClassContext
                            if (customContext != null) {
                                ClassContext(
                                    language = language,
                                    projectMeta = projectMetadata,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    className = cls.name,
                                    superClass = analyzeResult.getOrNull()?.superClass,
                                    packageName = (cls.containingFile as? PsiJavaFile)?.packageName ?: "",
                                    isInterface = cls.isInterface,
                                    isAbstract = cls.hasModifierProperty(PsiModifier.ABSTRACT),
                                    isEnum = cls.isEnum
                                )
                            } else {
                                analyzeResult.getOrNull() as? ClassContext
                            }
                        } else {
                            ClassContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                className = cls.name,
                                superClass = null,
                                packageName = (cls.containingFile as? PsiJavaFile)?.packageName ?: "",
                                isInterface = cls.isInterface,
                                isAbstract = cls.hasModifierProperty(PsiModifier.ABSTRACT),
                                isEnum = cls.isEnum
                            )
                        }
                    }

                    else -> null
                }
            }
        } catch (t: Throwable) {
            NekoamaLogger.logError(
                "buildCodeContext",
                NekoamaError.ParseError.InvalidConfiguration(NekoamaBundle.message("action.build.context.failed", t.message ?: "")),
                mapOf("exception" to (t.message ?: "unknown"))
            )
            null
        }
    }
}
