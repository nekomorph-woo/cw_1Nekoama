package com.cw2.nekoama.interfaces.intellij.actions

import com.cw2.nekoama.application.usecase.CustomGenerateUseCase
import com.cw2.nekoama.application.usecase.GeneratorFactory
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.domain.statistics.model.ActionType
import com.cw2.nekoama.domain.statistics.service.StatisticsService
import com.cw2.nekoama.domain.statistics.service.TokenUsageData
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.shared.util.NekoamaNotifier
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.shared.exception.NekoamaError
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 自定义生成动作
 *
 * 职责：
 * - 处理 UI 交互（Editor、AnActionEvent）
 * - 提取选中的文本
 * - 调用 UseCase 执行业务逻辑
 * - 将生成的内容插入到编辑器
 *
 * 业务逻辑已移至 CustomGenerateUseCase
 */
internal class CustomGenerateAction : BaseAction() {

    override fun getMenuTextKey(): String = "generate"

    override fun getCustomText(settings: NekoamaSettings): String =
        settings.customGenerateMenuText

    override fun perform(project: Project, editor: Editor?, e: AnActionEvent) {
        // 创建 UseCase 实例
        val useCase = CustomGenerateUseCase(
            generatorFactory = GeneratorFactory()
        )
        val selection = editor!!.selectionModel.selectedText ?: run {
            NekoamaNotifier.warn(NekoamaBundle.message("action.custom.selectText"))
            return
        }

        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, NekoamaBundle.message("action.customGenerate.text"), true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = NekoamaBundle.message("progress.parsingCustomPrompt")

                    try {
                        if (indicator.isCanceled) return

                        indicator.text = NekoamaBundle.message("progress.analyzingContext")

                        if (indicator.isCanceled) return

                        indicator.text = NekoamaBundle.message("progress.generatingCustom")

                        // 调用 UseCase 进行自定义生成
                        val result = runBlocking {
                            useCase.generateCustom(selection)
                        }

                        if (indicator.isCanceled) return

                        // 处理结果：将AI返回内容以行注释的方式插入到选中内容的上方
                        if (result.isSuccess) {
                            val suggestion = result.getOrNull()
                                ?: com.cw2.nekoama.domain.code_suggestion_gen.model.CustomSuggestion(
                                    content = NekoamaBundle.message("action.custom.emptyResult")
                                )
                            val generatedContent = suggestion.content
                            val lineComment = generatedContent
                                .lines()
                                .joinToString(separator = "\n// ") { it.trimEnd() }
                                .let { "// $it" }
                            WriteCommandAction.runWriteCommandAction(
                                project,
                                NekoamaBundle.message("action.customGenerate.text"),
                                null,
                                {
                                    val document = editor.document
                                    val startOffset = editor.selectionModel.selectionStart
                                    val lineNumber = document.getLineNumber(startOffset)
                                    val insertionOffset = document.getLineStartOffset(lineNumber)
                                    document.insertString(insertionOffset, "$lineComment\n\n")
                                })
                            NekoamaNotifier.info(NekoamaBundle.message("action.comment.generatedOk"))

                            // 记录使用统计
                            project.service<StatisticsService>()?.let { service ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    // 记录功能使用次数
                                    service.recordUsage(ActionType.CUSTOM_GENERATE)

                                    // 记录 Token 使用量
                                    service.recordTokenUsage(
                                        TokenUsageData(
                                            promptTokens = suggestion.metadata.promptTokens,
                                            completionTokens = suggestion.metadata.completionTokens,
                                            totalTokens = suggestion.metadata.totalTokens
                                        )
                                    )
                                }
                            }
                        } else {
                            val error = result.errorOrNull()
                            val errMsg = error?.message ?: NekoamaBundle.message("common.unknownError")
                            NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                        }

                    } catch (t: Throwable) {
                        NekoamaLogger.logError(
                            "CustomGenerateAction",
                            NekoamaError.APIError.ServerError("自定义生成异常: ${t.message}"),
                            mapOf("exception" to (t.message ?: "unknown"))
                        )
                        val errMsg = t.message ?: NekoamaBundle.message("common.unknownError")
                        NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                    }
                }
            })
    }

    override fun requiresEditor(): Boolean = true
}
