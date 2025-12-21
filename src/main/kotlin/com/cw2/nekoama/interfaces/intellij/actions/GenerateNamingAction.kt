package com.cw2.nekoama.interfaces.intellij.actions

import com.cw2.nekoama.domain.ai.service.CustomAPIConfig
import com.cw2.nekoama.domain.ai.service.CustomAIService
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.domain.settings.service.NekoamaSecureStorage
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.domain.metrics.model.ActionType
import com.cw2.nekoama.domain.code_analysis.service.UniversalCodeAnalyzer
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.shared.util.NekoamaNotifier
import com.cw2.nekoama.domain.ai.model.ClassContext
import com.cw2.nekoama.domain.ai.model.CodeContext
import com.cw2.nekoama.domain.ai.model.MethodContext
import com.cw2.nekoama.domain.ai.model.SurroundingContext
import com.cw2.nekoama.domain.ai.model.TypeInfo
import com.cw2.nekoama.domain.ai.model.VariableContext
import com.cw2.nekoama.domain.ai.model.VariableScope
import com.cw2.nekoama.domain.ai.service.AIProvider
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
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty

/**
 * 生成命名建议
 *
 * 实现策略：
 * - 在后台任务中分析当前光标处 PSI 元素
 * - 根据方法/变量分别构建适当的 CodeContext
 * - 调用配置的 AI Provider 生成真实的命名建议
 * - 展示AI生成的多个命名选项供用户选择
 */
internal class GenerateNamingAction : BaseAction() {

    override fun perform(project: Project, editor: Editor?, e: AnActionEvent): Int {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: run {
            NekoamaNotifier.warn(NekoamaBundle.message("action.naming.noPsiFile"))
            return 0
        }
        // 优先使用光标位置的 PSI 元素；若不可用再回退到事件上下文中的 PSI 元素（右键位置）
        val element = elementAtCaret(editor!!, psiFile) ?: e.getData(CommonDataKeys.PSI_ELEMENT)
        if (element == null) {
            NekoamaNotifier.warn(NekoamaBundle.message("action.naming.noElement"))
            return 0
        }

        // 在主线程中预先获取选中文本，避免后台线程直接访问 UI
        val selectionText = ReadAction.compute<String?, Throwable> {
            editor!!.selectionModel.selectedText
        }

        val title = NekoamaBundle.message("action.generateNaming.text")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = NekoamaBundle.message("progress.analyzingContext")

                try {
                    // 创建AI Provider实例
                    val provider = createAIProvider()
                    if (provider == null) {
                        NekoamaNotifier.warn(NekoamaBundle.message("settings.api.notConfigured"))
                        return
                    }

                    // 构建代码上下文
                    val codeContext = buildCodeContext(project, selectionText, element, indicator)
                    if (codeContext == null || indicator.isCanceled) return

                    indicator.text = NekoamaBundle.message("progress.generatingNaming")

                    // 调用AI生成命名建议
                    val result = runBlocking {
                        provider.generateNaming(codeContext)
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
        return 0 // TODO: 需要从AI响应中获取实际Token数量
    }

    private fun elementAtCaret(editor: Editor?, psiFile: PsiFile): PsiElement? {
        val offset = editor!!.caretModel.offset
        val element = psiFile.findElementAt(offset)
        if (element != null) {
            // 检查是否是局部变量（包括 Kotlin 和 Java）
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
     * 创建AI Provider实例（固定使用Custom API）
     */
    private fun createAIProvider(): AIProvider? {
        val settings = NekoamaSettings.getInstance()
        val secureKey = NekoamaSecureStorage.getApiKeySync()
        val resolvedKey =
            if (secureKey.isNotBlank()) secureKey else settings.apiKey.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }

        if (resolvedKey.isBlank() || settings.apiEndpoint.isBlank()) return null

        return CustomAIService(
            CustomAPIConfig(
                providerName = "Custom API",
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
     * 支持 #内容# 格式，直接提取 # 中间的内容作为 userIntent
     * 如果匹配成功，返回包含 userIntent 的上下文；否则返回 null
     */
    private fun extractCustomContext(selection: String): String? {
        val pattern = """#\s*([^#]+)\s*#""".toRegex()
        val match = pattern.find(selection)
        return match?.groupValues?.get(1)?.trim()
    }

    /**
     * 构建代码上下文
     */
    private fun buildCodeContext(
        project: Project,
        selectionText: String?,
        element: PsiElement,
        indicator: ProgressIndicator
    ): CodeContext? {
        // 检查是否有自定义上下文（#内容#格式）
        val customContext = selectionText?.let { extractCustomContext(it) }

        return try {
            ReadAction.compute<CodeContext?, Throwable> {
                val analyzer = UniversalCodeAnalyzer(project)
                val language = analyzer.detectLanguage(element)
                val projectInfo = analyzer.getProjectInfo()
                val surroundingContext = analyzer.extractSurroundingContext(element).getOrNull() ?: SurroundingContext(
                    precedingCode = emptyList(),
                    followingCode = emptyList(),
                    imports = emptyList(),
                    packageDeclaration = null,
                    fileComments = emptyList(),
                    siblingElements = emptyList(),
                    namingPatterns = null,
                    codeStyleAnalysis = null
                )

                // 优先级1：如果有自定义上下文，直接创建相应的 Context
                if (customContext != null) {
                    return@compute when {
                        // 检查是否有特定的 PSI 元素
                        PsiTreeUtil.getParentOfType(element, KtFunction::class.java) != null -> {
                            val fn = PsiTreeUtil.getParentOfType(element, KtFunction::class.java)!!
                            MethodContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                methodName = fn.name,
                                parameters = emptyList(),
                                returnType = TypeInfo("Unit"),
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
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                variableName = prop.name,
                                variableType = TypeInfo("Any"),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = prop.initializer?.text,
                                scope = VariableScope.LOCAL,
                                isConstant = prop.isVar.not(),
                                isStatic = false,
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }

                        PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) != null -> {
                            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)!!
                            MethodContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                methodName = method.name,
                                parameters = emptyList(),
                                returnType = TypeInfo("void"),
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
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                variableName = field.name,
                                variableType = TypeInfo(field.type.presentableText),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = field.initializer?.text,
                                scope = if (field.hasModifierProperty(PsiModifier.STATIC)) VariableScope.STATIC_FIELD else VariableScope.FIELD,
                                isConstant = field.hasModifierProperty(PsiModifier.FINAL),
                                isStatic = field.hasModifierProperty(PsiModifier.STATIC),
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }

                        PsiTreeUtil.getParentOfType(element, PsiLocalVariable::class.java) != null -> {
                            val localVar = PsiTreeUtil.getParentOfType(element, PsiLocalVariable::class.java)!!
                            VariableContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                variableName = localVar.name,
                                variableType = TypeInfo(localVar.type.presentableText),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = localVar.initializer?.text,
                                scope = VariableScope.LOCAL,
                                isConstant = localVar.hasModifierProperty(PsiModifier.FINAL),
                                isStatic = false,
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }

                        PsiTreeUtil.getParentOfType(element, PsiParameter::class.java) != null -> {
                            val param = PsiTreeUtil.getParentOfType(element, PsiParameter::class.java)!!
                            VariableContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                variableName = param.name,
                                variableType = TypeInfo(param.type.presentableText),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = null,
                                scope = VariableScope.PARAMETER,
                                isConstant = param.hasModifierProperty(PsiModifier.FINAL),
                                isStatic = false,
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }

                        PsiTreeUtil.getParentOfType(element, KtClass::class.java) != null -> {
                            val cls = PsiTreeUtil.getParentOfType(element, KtClass::class.java)!!
                            ClassContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                className = cls.name,
                                superClass = null,
                                interfaces = emptyList(),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                fields = emptyList(),
                                methods = emptyList(),
                                innerClasses = emptyList(),
                                isInterface = false,
                                isAbstract = false,
                                isEnum = false,
                                packageName = surroundingContext.packageDeclaration ?: ""
                            )
                        }

                        PsiTreeUtil.getParentOfType(element, PsiClass::class.java) != null -> {
                            val cls = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)!!
                            ClassContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                className = cls.name,
                                superClass = null,
                                interfaces = emptyList(),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                fields = emptyList(),
                                methods = emptyList(),
                                innerClasses = emptyList(),
                                isInterface = cls.isInterface,
                                isAbstract = cls.hasModifierProperty(PsiModifier.ABSTRACT),
                                isEnum = cls.isEnum,
                                packageName = surroundingContext.packageDeclaration ?: ""
                            )
                        }
                        // 如果没有找到特定元素，创建通用的上下文
                        else -> {
                            MethodContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                methodName = null,
                                parameters = emptyList(),
                                returnType = TypeInfo("Any"),
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

                // 如果没有自定义上下文，使用原有逻辑
                when {
                    PsiTreeUtil.getParentOfType(element, KtFunction::class.java) != null -> {
                        val fn = PsiTreeUtil.getParentOfType(element, KtFunction::class.java)!!
                        val analyzeResult = analyzer.analyzeMethod(fn)
                        if (analyzeResult.isSuccess) {
                            // 如果有自定义上下文，创建包含 userIntent 的 MethodContext
                            if (customContext != null) {
                                MethodContext(
                                    language = language,
                                    projectInfo = projectInfo,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    methodName = fn.name,
                                    parameters = analyzeResult.getOrNull()?.parameters ?: emptyList(),
                                    returnType = analyzeResult.getOrNull()?.returnType ?: TypeInfo("Unit"),
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
                            // 创建基础的MethodContext（Kotlin）
                            MethodContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                methodName = fn.name,
                                parameters = emptyList(),
                                returnType = TypeInfo("Unit"),
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
                        val analyzeResult = analyzer.analyzeVariable(prop)
                        if (analyzeResult.isSuccess) {
                            // 如果有自定义上下文，创建包含 userIntent 的 VariableContext
                            if (customContext != null) {
                                VariableContext(
                                    language = language,
                                    projectInfo = projectInfo,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    variableName = prop.name,
                                    variableType = analyzeResult.getOrNull()?.variableType ?: TypeInfo("Any"),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    initializer = prop.initializer?.text,
                                    scope = analyzeResult.getOrNull()?.scope
                                        ?: VariableScope.LOCAL,
                                    isConstant = prop.isVar.not(),
                                    isStatic = false,
                                    usagePattern = analyzeResult.getOrNull()?.usagePattern,
                                    containingClass = analyzeResult.getOrNull()?.containingClass,
                                    containingMethod = analyzeResult.getOrNull()?.containingMethod
                                )
                            } else {
                                analyzeResult.getOrNull() as? VariableContext
                            }
                        } else {
                            // 创建基础的VariableContext（Kotlin）
                            VariableContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                variableName = prop.name,
                                variableType = TypeInfo("Any"),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = prop.initializer?.text,
                                scope = VariableScope.LOCAL,
                                isConstant = prop.isVar.not(),
                                isStatic = false,
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }
                    }

                    PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) != null -> {
                        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)!!
                        val analyzeResult = analyzer.analyzeMethod(method)
                        if (analyzeResult.isSuccess) {
                            // 如果有自定义上下文，创建包含 userIntent 的 MethodContext
                            if (customContext != null) {
                                MethodContext(
                                    language = language,
                                    projectInfo = projectInfo,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    methodName = method.name,
                                    parameters = analyzeResult.getOrNull()?.parameters ?: emptyList(),
                                    returnType = analyzeResult.getOrNull()?.returnType ?: TypeInfo("void"),
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
                            // 创建基础的MethodContext（Java）
                            MethodContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                methodName = method.name,
                                parameters = emptyList(),
                                returnType = TypeInfo(method.returnType?.presentableText ?: "void"),
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
                        val analyzeResult = analyzer.analyzeVariable(field)
                        if (analyzeResult.isSuccess) {
                            // 如果有自定义上下文，创建包含 userIntent 的 VariableContext
                            if (customContext != null) {
                                VariableContext(
                                    language = language,
                                    projectInfo = projectInfo,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    variableName = field.name,
                                    variableType = analyzeResult.getOrNull()?.variableType
                                        ?: TypeInfo(field.type.presentableText),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    initializer = field.initializer?.text,
                                    scope = analyzeResult.getOrNull()?.scope ?: if (field.hasModifierProperty(
                                            PsiModifier.STATIC
                                        )
                                    ) VariableScope.STATIC_FIELD else VariableScope.FIELD,
                                    isConstant = field.hasModifierProperty(PsiModifier.FINAL),
                                    isStatic = field.hasModifierProperty(PsiModifier.STATIC),
                                    usagePattern = analyzeResult.getOrNull()?.usagePattern,
                                    containingClass = analyzeResult.getOrNull()?.containingClass,
                                    containingMethod = analyzeResult.getOrNull()?.containingMethod
                                )
                            } else {
                                analyzeResult.getOrNull() as? VariableContext
                            }
                        } else {
                            // 创建基础的VariableContext（Java 字段）
                            VariableContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                variableName = field.name,
                                variableType = TypeInfo(field.type.presentableText),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = field.initializer?.text,
                                scope = if (field.hasModifierProperty(PsiModifier.STATIC)) VariableScope.STATIC_FIELD else VariableScope.FIELD,
                                isConstant = field.hasModifierProperty(PsiModifier.FINAL),
                                isStatic = field.hasModifierProperty(PsiModifier.STATIC),
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }
                    }

                    PsiTreeUtil.getParentOfType(element, PsiVariable::class.java) != null -> {
                        val v = PsiTreeUtil.getParentOfType(element, PsiVariable::class.java)!!
                        val analyzeResult = analyzer.analyzeVariable(v)
                        if (analyzeResult.isSuccess) {
                            // 如果有自定义上下文，创建包含 userIntent 的 VariableContext
                            if (customContext != null) {
                                VariableContext(
                                    language = language,
                                    projectInfo = projectInfo,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    variableName = v.name,
                                    variableType = analyzeResult.getOrNull()?.variableType
                                        ?: TypeInfo(v.type.presentableText),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    initializer = v.initializer?.text,
                                    scope = analyzeResult.getOrNull()?.scope
                                        ?: VariableScope.LOCAL,
                                    isConstant = false,
                                    isStatic = false,
                                    usagePattern = analyzeResult.getOrNull()?.usagePattern,
                                    containingClass = analyzeResult.getOrNull()?.containingClass,
                                    containingMethod = analyzeResult.getOrNull()?.containingMethod
                                )
                            } else {
                                analyzeResult.getOrNull() as? VariableContext
                            }
                        } else {
                            // 创建基础的VariableContext（Java 局部变量/参数）
                            VariableContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                variableName = v.name,
                                variableType = TypeInfo(v.type.presentableText),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = v.initializer?.text,
                                scope = VariableScope.LOCAL,
                                isConstant = false,
                                isStatic = false,
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }
                    }

                    // 专门处理局部变量和参数
                    PsiTreeUtil.getParentOfType(element, PsiLocalVariable::class.java) != null -> {
                        val localVar = PsiTreeUtil.getParentOfType(element, PsiLocalVariable::class.java)!!
                        val analyzeResult = analyzer.analyzeVariable(localVar)
                        if (analyzeResult.isSuccess) {
                            // 如果有自定义上下文，创建包含 userIntent 的 VariableContext
                            if (customContext != null) {
                                VariableContext(
                                    language = language,
                                    projectInfo = projectInfo,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    variableName = localVar.name,
                                    variableType = analyzeResult.getOrNull()?.variableType
                                        ?: TypeInfo(localVar.type.presentableText),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    initializer = localVar.initializer?.text,
                                    scope = analyzeResult.getOrNull()?.scope
                                        ?: VariableScope.LOCAL,
                                    isConstant = localVar.hasModifierProperty(PsiModifier.FINAL),
                                    isStatic = false,
                                    usagePattern = analyzeResult.getOrNull()?.usagePattern,
                                    containingClass = analyzeResult.getOrNull()?.containingClass,
                                    containingMethod = analyzeResult.getOrNull()?.containingMethod
                                )
                            } else {
                                analyzeResult.getOrNull() as? VariableContext
                            }
                        } else {
                            VariableContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                variableName = localVar.name,
                                variableType = TypeInfo(localVar.type.presentableText),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = localVar.initializer?.text,
                                scope = VariableScope.LOCAL,
                                isConstant = localVar.hasModifierProperty(PsiModifier.FINAL),
                                isStatic = false,
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }
                    }

                    PsiTreeUtil.getParentOfType(element, PsiParameter::class.java) != null -> {
                        val param = PsiTreeUtil.getParentOfType(element, PsiParameter::class.java)!!
                        val analyzeResult = analyzer.analyzeVariable(param)
                        if (analyzeResult.isSuccess) {
                            // 如果有自定义上下文，创建包含 userIntent 的 VariableContext
                            if (customContext != null) {
                                VariableContext(
                                    language = language,
                                    projectInfo = projectInfo,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    variableName = param.name,
                                    variableType = analyzeResult.getOrNull()?.variableType
                                        ?: TypeInfo(param.type.presentableText),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    initializer = null,
                                    scope = VariableScope.PARAMETER,
                                    isConstant = param.hasModifierProperty(PsiModifier.FINAL),
                                    isStatic = false,
                                    usagePattern = analyzeResult.getOrNull()?.usagePattern,
                                    containingClass = analyzeResult.getOrNull()?.containingClass,
                                    containingMethod = analyzeResult.getOrNull()?.containingMethod
                                )
                            } else {
                                analyzeResult.getOrNull() as? VariableContext
                            }
                        } else {
                            VariableContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                variableName = param.name,
                                variableType = TypeInfo(param.type.presentableText),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = null,
                                scope = VariableScope.PARAMETER,
                                isConstant = param.hasModifierProperty(PsiModifier.FINAL),
                                isStatic = false,
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }
                    }

                    PsiTreeUtil.getParentOfType(element, KtClass::class.java) != null -> {
                        val cls = PsiTreeUtil.getParentOfType(element, KtClass::class.java)!!
                        val analyzeResult = analyzer.analyzeClass(cls)
                        if (analyzeResult.isSuccess) {
                            // 如果有自定义上下文，创建包含 userIntent 的 ClassContext
                            if (customContext != null) {
                                ClassContext(
                                    language = language,
                                    projectInfo = projectInfo,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    className = cls.name,
                                    superClass = analyzeResult.getOrNull()?.superClass,
                                    interfaces = analyzeResult.getOrNull()?.interfaces ?: emptyList(),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    fields = analyzeResult.getOrNull()?.fields ?: emptyList(),
                                    methods = analyzeResult.getOrNull()?.methods ?: emptyList(),
                                    innerClasses = analyzeResult.getOrNull()?.innerClasses ?: emptyList(),
                                    isInterface = false,
                                    isAbstract = false,
                                    isEnum = false,
                                    packageName = surroundingContext.packageDeclaration ?: ""
                                )
                            } else {
                                analyzeResult.getOrNull() as? ClassContext
                            }
                        } else {
                            ClassContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                className = cls.name,
                                superClass = null,
                                interfaces = emptyList(),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                fields = emptyList(),
                                methods = emptyList(),
                                innerClasses = emptyList(),
                                isInterface = false,
                                isAbstract = false,
                                isEnum = false,
                                packageName = surroundingContext.packageDeclaration ?: ""
                            )
                        }
                    }

                    PsiTreeUtil.getParentOfType(element, PsiClass::class.java) != null -> {
                        val cls = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)!!
                        val analyzeResult = analyzer.analyzeClass(cls)
                        if (analyzeResult.isSuccess) {
                            // 如果有自定义上下文，创建包含 userIntent 的 ClassContext
                            if (customContext != null) {
                                ClassContext(
                                    language = language,
                                    projectInfo = projectInfo,
                                    surroundingContext = surroundingContext,
                                    userIntent = customContext,
                                    className = cls.name,
                                    superClass = analyzeResult.getOrNull()?.superClass,
                                    interfaces = analyzeResult.getOrNull()?.interfaces ?: emptyList(),
                                    modifiers = analyzeResult.getOrNull()?.modifiers ?: emptyList(),
                                    annotations = analyzeResult.getOrNull()?.annotations ?: emptyList(),
                                    fields = analyzeResult.getOrNull()?.fields ?: emptyList(),
                                    methods = analyzeResult.getOrNull()?.methods ?: emptyList(),
                                    innerClasses = analyzeResult.getOrNull()?.innerClasses ?: emptyList(),
                                    isInterface = cls.isInterface,
                                    isAbstract = cls.hasModifierProperty(PsiModifier.ABSTRACT),
                                    isEnum = cls.isEnum,
                                    packageName = surroundingContext.packageDeclaration ?: ""
                                )
                            } else {
                                analyzeResult.getOrNull() as? ClassContext
                            }
                        } else {
                            ClassContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                userIntent = customContext,
                                className = cls.name,
                                superClass = null,
                                interfaces = emptyList(),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                fields = emptyList(),
                                methods = emptyList(),
                                innerClasses = emptyList(),
                                isInterface = cls.isInterface,
                                isAbstract = cls.hasModifierProperty(PsiModifier.ABSTRACT),
                                isEnum = cls.isEnum,
                                packageName = surroundingContext.packageDeclaration ?: ""
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

    override fun getActionType(): ActionType = ActionType.GENERATE_NAMING

    override fun requiresEditor(): Boolean = true
}
