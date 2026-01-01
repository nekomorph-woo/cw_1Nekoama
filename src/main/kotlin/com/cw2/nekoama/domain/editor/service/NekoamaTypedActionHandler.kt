package com.cw2.nekoama.domain.editor.service

import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.shared.util.NekoamaNotifier
import com.cw2.nekoama.domain.code_suggestion_gen.model.CodeSuggestionGenerator
import com.cw2.nekoama.infrastructure.code_suggestion_gen.client.openai.OpenAIGenerator
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config.CustomGeneratorConfig
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.domain.settings.service.NekoamaSecureStorage
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.shared.util.IntellijTaskManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.runBlocking
import kotlin.math.max

/*
 * 键入处理器（基于 TypedActionHandler）
 *
 * 中文说明：
 * - 通过 StartupActivity 在 IDE 启动后替换原有 TypedActionHandler，从而实现按键拦截。
 * - 新约定：当检测到形如 "[$<用户自定义提示>]" 的模式且键入 ']' 完成闭合时，
 *   触发 AI 调用，使用用户输入的提示作为唯一 prompt，不添加任何硬编码前后缀，
 *   并将整个特殊符号（包括方括号）替换为 AI 返回的内容。
 * - 保持非目标输入走默认逻辑，避免影响 IDE 体验。
 */
internal class NekoamaTypedActionHandler(
    private val delegate: TypedActionHandler?
) : TypedActionHandler {

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        // 仅当设置开启且键入 ']' 时尝试匹配
        if (!NekoamaSettings.Companion.getInstance().autoTrigger || charTyped != ']') {
            delegate?.execute(editor, charTyped, dataContext)
            return
        }

        val project = editor.project
        if (project == null) {
            delegate?.execute(editor, charTyped, dataContext)
            return
        }

        val document = editor.document
        val caretOffset = editor.caretModel.offset

        // 为避免全量扫描，仅在最近窗口中回溯查找 "[$"
        val windowSize = 512
        val windowStart = max(0, caretOffset - windowSize)
        val recent = document.getText(TextRange(windowStart, caretOffset))
        val localIdx = recent.lastIndexOf("[$")
        if (localIdx < 0) {
            delegate?.execute(editor, charTyped, dataContext)
            return
        }

        val startOffset = windowStart + localIdx // 指向 '[' 的位置
        val promptStart = startOffset + 2 // 紧随 "[$" 之后
        val promptEnd = caretOffset - 1 // ']' 之前的最后一个字符
        if (promptEnd < promptStart) {
            delegate?.execute(editor, charTyped, dataContext)
            return
        }

        val prompt = document.getText(TextRange(promptStart, promptEnd)).trim()
        if (prompt.isEmpty()) {
            // 空提示不触发 AI，交给默认处理
            delegate?.execute(editor, charTyped, dataContext)
            return
        }

        val generator = createCodeSuggestionGenerator()
        if (generator == null) {
            NekoamaNotifier.warn(NekoamaBundle.message("typed.handler.notConfigured"))
            return
        }

        // 捕获当前需要替换的范围：[startOffset, caretOffset)
        val replaceStart = startOffset
        val replaceEnd = caretOffset

        IntellijTaskManager.execute(
            project,
            title = NekoamaBundle.message("typed.handler.title"),
            cancellable = true,
            task = {
                runBlocking {
                    generator.generateCustom(prompt, null)
                }
            },
            onSuccess = { result ->
                if (result.isSuccess) {
                    val text = result.getOrNull() ?: ""
                    // 为确保在平台完全加载、索引可用且在 EDT 执行写操作，这里延迟到 EDT 并在 Smart 模式下执行
                    val app = ApplicationManager.getApplication()
                    app.invokeLater({
                        val proj = project
                        if (proj.isDisposed) return@invokeLater
                        DumbService.Companion.getInstance(proj).runWhenSmart {
                            WriteCommandAction.runWriteCommandAction(proj) {
                                try {
                                    document.replaceString(replaceStart, replaceEnd, text)
                                    PsiDocumentManager.getInstance(proj).commitDocument(document)
                                    editor.caretModel.moveToOffset(replaceStart + text.length)
                                } catch (t: Throwable) {
                                    NekoamaLogger.warn("TypedReplace", "Failed to replace text after AI result", error = t)
                                }
                            }
                        }
                    }, ModalityState.defaultModalityState())
                } else {
                    val err = result.errorOrNull()
                    val errMsg = err?.message ?: NekoamaBundle.message("common.unknownError")
                    NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                }
            },
            onError = { t ->
                run {
                    val errMsg = t.message ?: NekoamaBundle.message("common.unknownError")
                    NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                }
            }
        )
    }

    private fun createCodeSuggestionGenerator(): CodeSuggestionGenerator? {
        val settings = NekoamaSettings.Companion.getInstance()
        val secureKey = NekoamaSecureStorage.getApiKeySync()
        val resolvedKey = secureKey.ifBlank { settings.apiKey.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" } }
        if (resolvedKey.isBlank() || settings.apiEndpoint.isBlank()) return null

        return OpenAIGenerator(
            CustomGeneratorConfig(
                generatorName = "Custom API",
                apiUrl = settings.apiEndpoint,
                apiKey = resolvedKey,
                model = settings.model,
                temperature = settings.modelTemperature / 100.0,
                timeoutMs = settings.requestTimeoutMs.toLong(),
                maxTokens = 500
            )
        )
    }
}