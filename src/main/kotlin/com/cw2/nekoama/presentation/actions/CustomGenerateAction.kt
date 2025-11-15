package com.cw2.nekoama.presentation.actions

import com.cw2.nekoama.ai.model.CodeContext
import com.cw2.nekoama.ai.model.MethodContext
import com.cw2.nekoama.ai.model.ProgrammingLanguage
import com.cw2.nekoama.ai.model.SurroundingContext
import com.cw2.nekoama.ai.model.TypeInfo
import com.cw2.nekoama.ai.provider.custom.CustomAPIProvider
import com.cw2.nekoama.ai.provider.custom.CustomAPIConfig
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.data.settings.NekoamaSettings
import com.cw2.nekoama.data.settings.NekoamaSecureStorage
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.integrations.psi.UniversalCodeAnalyzer
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking

/**
 * 自定义生成动作
 *
 * 实现策略：
 * - 解析选中文本，提取用户的自定义提示信息
 * - 获取当前代码上下文作为AI生成的参考
 * - 调用配置的 AI Provider 根据自定义提示生成内容
 * - 将生成结果显示给用户或插入到适当位置
 */
internal class CustomGenerateAction : BaseAction() {

    override fun perform(project: Project, editor: Editor, e: AnActionEvent) {
        val selection = editor.selectionModel.selectedText ?: run {
            NekoamaNotifier.warn(NekoamaBundle.message("action.custom.selectText"))
            return
        }

        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, NekoamaBundle.message("action.customGenerate.text"), true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = NekoamaBundle.message("progress.parsingCustomPrompt")

                try {
                    // 创建AI Provider实例
                    val provider = createAIProvider()
                    if (provider == null) {
                        NekoamaNotifier.warn(NekoamaBundle.message("settings.api.notConfigured"))
                        return
                    }

                    // 提取自定义提示内容
                    val customPrompt = extractCustomPrompt(selection)
                    if (customPrompt.isBlank()) {
                        NekoamaNotifier.warn(NekoamaBundle.message("action.custom.invalidPrompt"))
                        return
                    }

                    if (indicator.isCanceled) return

                    indicator.text = NekoamaBundle.message("progress.analyzingContext")

                    // 构建代码上下文（可选）
//                    val codeContext = psiFile?.let { buildCodeContext(project, editor, it, indicator) }

                    if (indicator.isCanceled) return

                    indicator.text = NekoamaBundle.message("progress.generatingCustom")

                    // 调用AI进行自定义生成
                    val result = runBlocking {
                        provider.generateCustom(customPrompt, null)
                    }

                    if (indicator.isCanceled) return

                    // 处理结果：将AI返回内容以行注释的方式插入到选中内容的上方
                    if (result.isSuccess) {
                        val generatedContent = result.getOrNull() ?: NekoamaBundle.message("action.custom.emptyResult")
                        val lineComment = generatedContent
                            .lines()
                            .joinToString(separator = "\n// ") { it.trimEnd() }
                            .let { "// $it" }
                        WriteCommandAction.runWriteCommandAction(project, NekoamaBundle.message("action.customGenerate.text"), null, Runnable {
                            val document = editor.document
                            val startOffset = editor.selectionModel.selectionStart
                            val lineNumber = document.getLineNumber(startOffset)
                            val insertionOffset = document.getLineStartOffset(lineNumber)
                            document.insertString(insertionOffset, "$lineComment\n\n")
                        })
                        NekoamaNotifier.info(NekoamaBundle.message("action.comment.generatedOk"))
                    } else {
                        val error = result.errorOrNull()
                        val errMsg = error?.message ?: NekoamaBundle.message("common.unknownError")
                        NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                    }

                } catch (t: Throwable) {
                    NekoamaLogger.logError("CustomGenerateAction",
                        com.cw2.nekoama.core.exception.NekoamaError.APIError.ServerError("自定义生成异常: ${t.message}"),
                        mapOf("exception" to (t.message ?: "unknown")))
                    run {
                        val errMsg = t.message ?: NekoamaBundle.message("common.unknownError")
                        NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                    }
                }
            }
        })
    }

    /**
     * 创建AI Provider实例（固定使用Custom API）
     */
    private fun createAIProvider(): com.cw2.nekoama.ai.provider.AIProvider? {
        val settings = NekoamaSettings.getInstance()
        val secureKey = NekoamaSecureStorage.getApiKeySync()
        val resolvedKey = if (secureKey.isNotBlank()) secureKey else settings.apiKey.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }

        if (resolvedKey.isBlank() || settings.apiEndpoint.isBlank()) return null

        return CustomAPIProvider(
            CustomAPIConfig(
                providerName = "Custom API",
                apiUrl = settings.apiEndpoint,
                apiKey = resolvedKey,
                model = settings.model,
                temperature = settings.modelTemperature / 100.0,
                timeoutMs = settings.requestTimeoutMs.toLong(),
                maxTokens = 2000
            )
        )
    }

    /**
     * 提取自定义提示内容
     * 说明：若文本中包含以 [$ 开始、以 ] 结束的段落，将提取其中 $ 后的内容；
     * 否则直接使用选中文本作为提示。
     */
    private fun extractCustomPrompt(selection: String): String {
        val idx = selection.indexOf("[$")
        if (idx >= 0) {
            val end = selection.indexOf(']', idx + 2)
            if (end > idx + 2) {
                return selection.substring(idx + 2, end).trim()
            }
        }
        return selection.trim()
    }

    /**
     * 构建代码上下文（轻量级版本）
     */
    private fun buildCodeContext(project: Project, editor: Editor, psiFile: PsiFile, indicator: ProgressIndicator): CodeContext? {
        return try {
            ReadAction.compute<CodeContext?, Throwable> {
                val analyzer = UniversalCodeAnalyzer(project)
                val offset = editor.caretModel.offset
                val element = psiFile.findElementAt(offset)
                
                if (element == null) {
                    // 创建基础上下文
                    MethodContext(
                        language = ProgrammingLanguage.OTHER,
                        projectInfo = analyzer.getProjectInfo(),
                        surroundingContext = SurroundingContext(
                            precedingCode = emptyList(),
                            followingCode = emptyList(),
                            imports = emptyList(),
                            packageDeclaration = null,
                            fileComments = emptyList(),
                            siblingElements = emptyList(),
                            namingPatterns = null,
                            codeStyleAnalysis = null
                        ),
                        methodName = null,
                        parameters = emptyList(),
                        returnType = TypeInfo("Any"),
                        modifiers = emptyList(),
                        annotations = emptyList(),
                        exceptions = emptyList(),
                        methodBody = null,
                        isConstructor = false,
                        isAbstract = false,
                        containingClass = null
                    )
                } else {
                    val language = analyzer.detectLanguage(element)
                    val projectInfo = analyzer.getProjectInfo()
                    val surroundingContext = analyzer.extractSurroundingContext(element).getOrNull() ?: 
                        SurroundingContext(
                            precedingCode = emptyList(),
                            followingCode = emptyList(),
                            imports = emptyList(),
                            packageDeclaration = null,
                            fileComments = emptyList(),
                            siblingElements = emptyList(),
                            namingPatterns = null,
                            codeStyleAnalysis = null
                        )

                    // 创建通用上下文
                    MethodContext(
                        language = language,
                        projectInfo = projectInfo,
                        surroundingContext = surroundingContext,
                        methodName = null,
                        parameters = emptyList(),
                        returnType = TypeInfo("Any"),
                        modifiers = emptyList(),
                        annotations = emptyList(),
                        exceptions = emptyList(),
                        methodBody = editor.selectionModel.selectedText,
                        isConstructor = false,
                        isAbstract = false,
                        containingClass = null
                    )
                }
            }
        } catch (t: Throwable) {
            NekoamaLogger.logError("buildCodeContext",
                com.cw2.nekoama.core.exception.NekoamaError.ParseError.InvalidConfiguration("构建代码上下文失败: ${t.message}"),
                mapOf("exception" to (t.message ?: "unknown")))
            null
        }
    }

    override fun getActionType(): ActionType = ActionType.CUSTOM_GENERATE
}
