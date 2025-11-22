package com.cw2.nekoama.presentation.actions

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.cw2.nekoama.presentation.ui.AnalysisConfigDialog
import com.cw2.nekoama.presentation.ui.AnalysisProgressDialog
import com.cw2.nekoama.presentation.ui.ReportViewer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * 代码依赖分析Action
 *
 * 功能：
 * - 提供基于PSI的深度代码依赖关系分析
 * - 支持项目级、包级、类级的分析范围选择
 * - 集成分析配置对话框和进度反馈
 * - 生成HTML、JSON、Markdown格式的分析报告
 */
internal class AnalyzeCodeDepsAction : BaseAction() {

    override fun perform(project: Project, editor: Editor?, e: AnActionEvent): Int {
        try {
            // 显示分析配置对话框
            val configDialog = AnalysisConfigDialog(project)
            if (!configDialog.showAndGet()) {
                return 0 // 用户取消配置
            }

            val analysisConfig = configDialog.getConfig()

            val title = NekoamaBundle.message("action.analyzeCodeDeps.progress.title")
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.fraction = 0.0

                    try {
                        // 阶段1: 初始化分析器
                        indicator.text = NekoamaBundle.message("progress.analyze.initializing")
                        indicator.fraction = 0.1

                        // 阶段2: 扫描分析范围
                        indicator.text = NekoamaBundle.message("progress.analyze.scanning")
                        indicator.fraction = 0.2

                        // 阶段3: 执行依赖分析
                        indicator.text = NekoamaBundle.message("progress.analyze.dependencies")
                        indicator.fraction = 0.3

                        // 模拟分析过程
                        Thread.sleep(1000) // 模拟分析耗时

                        if (indicator.isCanceled) {
                            return
                        }

                        // 阶段4: 生成分析报告
                        indicator.text = NekoamaBundle.message("progress.analyze.generating")
                        indicator.fraction = 0.8

                        Thread.sleep(500) // 模拟报告生成

                        if (!indicator.isCanceled) {
                            indicator.text = NekoamaBundle.message("progress.analyze.completed")
                            indicator.fraction = 1.0

                            ApplicationManager.getApplication().invokeLater {
                                try {
                                    AnalysisProgressDialog.showAndHandleResult(project, analysisConfig)
                                    ReportViewer.showReports(project, "Analysis completed successfully")
                                } catch (t: Throwable) {
                                    NekoamaLogger.logError(
                                        "AnalyzeCodeDepsAction",
                                        com.cw2.nekoama.core.exception.NekoamaError.UIError.DialogError(NekoamaBundle.message("action.analyzeCodeDeps.error.reportFailed", t.message ?: "")),
                                        mapOf("exception" to (t.message ?: "unknown"))
                                    )
                                }
                            }

                            NekoamaNotifier.info(NekoamaBundle.message("action.analyzeCodeDeps.success"))
                        }

                    } catch (t: Throwable) {
                        if (!indicator.isCanceled) {
                            NekoamaLogger.logError(
                                "AnalyzeCodeDepsAction",
                                com.cw2.nekoama.core.exception.NekoamaError.AnalysisError.DependencyAnalysisError(NekoamaBundle.message("action.analyzeCodeDeps.dependencyAnalysisFailed", t.message ?: "")),
                                mapOf("exception" to (t.message ?: "unknown"))
                            )
                            NekoamaNotifier.error(NekoamaBundle.message("action.analyzeCodeDeps.error.failed", t.message ?: ""))
                        }
                    }
                }

                override fun onCancel() {
                    NekoamaNotifier.info(NekoamaBundle.message("action.analyzeCodeDeps.cancelled"))
                }
            })

        } catch (t: Throwable) {
            NekoamaLogger.logError(
                "AnalyzeCodeDepsAction",
                com.cw2.nekoama.core.exception.NekoamaError.AnalysisError.DependencyAnalysisError(NekoamaBundle.message("action.analyzeCodeDeps.analysisStartFailed", t.message ?: "")),
                mapOf("exception" to (t.message ?: "unknown"))
            )
            NekoamaNotifier.error(NekoamaBundle.message("action.analyzeCodeDeps.error.startFailed", t.message ?: ""))
        }

        return 0 // TODO: 计算实际的Token使用量
    }

    override fun getActionType(): ActionType = ActionType.ANALYZE_CODE_DEPS

    override fun requiresEditor(): Boolean = false // 可以在项目级别使用，不需要编辑器
}