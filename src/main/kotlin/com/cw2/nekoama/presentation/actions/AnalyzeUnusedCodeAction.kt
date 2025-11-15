package com.cw2.nekoama.presentation.actions

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.core.result.Result
import com.cw2.nekoama.integrations.psi.UnusedCodeScanner
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

/**
 * 扫描未使用的文件/类/方法/属性，并生成报告。
 *
 * 设计说明（为什么）：
 * - 用户需要快速识别未使用的代码以便清理；该动作在后台完成扫描，避免阻塞 UI。
 * - 报告写入 build/neko-unused-report-{时间戳}.txt，方便查看与版本控制外排除。
 */
internal class AnalyzeUnusedCodeAction : BaseAction() {

    
    override fun perform(project: Project, editor: Editor?, e: AnActionEvent) {
        NekoamaLogger.info("UNUSED_SCAN", "start")
        UnusedCodeScanner.scanInBackground(project) { res ->
            when (res) {
                is Result.Success -> {
                    val report = res.data
                    val out = UnusedCodeScanner.writeReportToFile(project, report)
                    val outPath = out?.absolutePath ?: NekoamaBundle.message("action.analyzeUnused.notWritten")
                    val msg = NekoamaBundle.message(
                        "action.analyzeUnused.success",
                        report.unused.size,
                        report.scannedFiles,
                        report.scannedSymbols,
                        outPath
                    )
                    NekoamaNotifier.info(msg)
                    NekoamaLogger.info("UNUSED_SCAN", "done", mapOf(
                        "unused" to report.unused.size,
                        "files" to report.scannedFiles,
                        "symbols" to report.scannedSymbols,
                        "out" to (out?.absolutePath ?: "")
                    ))
                }
                is Result.Error -> {
                    val errMsg = res.error.message ?: NekoamaBundle.message("common.unknownError")
                    NekoamaNotifier.error(NekoamaBundle.message("action.analyzeUnused.failed", errMsg))
                    NekoamaLogger.logError("UNUSED_SCAN", res.error)
                    // 不抛出异常，让BaseAction处理
                }
            }
        }
    }

    override fun getActionType(): ActionType = ActionType.ANALYZE_UNUSED_CODE

    override fun requiresEditor(): Boolean = false
}
