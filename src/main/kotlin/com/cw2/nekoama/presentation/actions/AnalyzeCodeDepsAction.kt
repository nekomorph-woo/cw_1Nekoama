package com.cw2.nekoama.presentation.actions

import com.cw2.nekoama.ai.model.dependency.AnalysisConfig as AiAnalysisConfig
import com.cw2.nekoama.ai.model.dependency.DependencyAnalysisResult
import com.cw2.nekoama.ai.model.dependency.ComplexityThresholds
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.integrations.psi.BatchAnalysisProcessor
import com.cw2.nekoama.integrations.psi.BoundaryEntryPointDetector
import com.cw2.nekoama.core.reporting.DependencyReportGenerator
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.cw2.nekoama.presentation.ui.AnalysisConfigDialog
import com.cw2.nekoama.presentation.ui.EntryPointConfirmationDialog
import com.cw2.nekoama.presentation.ui.ReportViewer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Paths
import kotlin.coroutines.CoroutineContext

/**
 * 代码依赖分析Action
 *
 * 功能：
 * - 提供基于PSI的深度代码依赖关系分析
 * - 支持项目级、包级、类级的分析范围选择
 * - 集成分析配置对话框和进度反馈
 * - 生成HTML、JSON、Markdown格式的分析报告
 * - 业务入口点确认和场景化分析
 */
internal class AnalyzeCodeDepsAction : BaseAction() {

    override fun perform(project: Project, editor: Editor?, e: AnActionEvent): Int {
        try {
            // 1. 显示分析配置对话框
            val configDialog = AnalysisConfigDialog(project)
            if (!configDialog.showAndGet()) {
                return 0 // 用户取消配置
            }

            val dialogConfig = configDialog.getConfig()

            // 转换为AI AnalysisConfig
            val analysisConfig = convertToAiAnalysisConfig(dialogConfig)

            // 2. 检测业务入口点
            val entryPointDetector = BoundaryEntryPointDetector(project)
            val allEntryPoints = entryPointDetector.detectBusinessEntryPoints()

            // 3. 显示入口点确认对话框
            val entryPointDialog = EntryPointConfirmationDialog(project, allEntryPoints)
            val confirmedEntryPoints = if (entryPointDialog.showAndGet()) {
                entryPointDialog.getConfirmedEntryPoints()
            } else {
                emptyList() // 用户取消
            }

            if (confirmedEntryPoints.isEmpty() && allEntryPoints.isNotEmpty()) {
                NekoamaNotifier.info(NekoamaBundle.message("action.analyzeCodeDeps.noEntryPointsSelected"))
                return 0
            }

            // 4. 执行依赖分析
            val title = NekoamaBundle.message("action.analyzeCodeDeps.progress.title")
            val analysisResult = runCatching {
                ProgressManager.getInstance().run(object : Task.WithResult<DependencyAnalysisResult, Exception>(project, title, true) {
                    override fun compute(indicator: ProgressIndicator): DependencyAnalysisResult {
                        return executeDependencyAnalysis(project, analysisConfig, confirmedEntryPoints, indicator)
                    }
                })
            }.getOrElse { exception ->
                NekoamaLogger.logError(
                    "AnalyzeCodeDepsAction",
                    com.cw2.nekoama.core.exception.NekoamaError.AnalysisError.DependencyAnalysisError(
                        NekoamaBundle.message("action.analyzeCodeDeps.dependencyAnalysisFailed", exception.message ?: "")
                    ),
                    mapOf("exception" to (exception.message ?: "unknown"))
                )
                NekoamaNotifier.error(NekoamaBundle.message("action.analyzeCodeDeps.error.failed", exception.message ?: ""))
                return 0
            }

            // 5. 生成并显示报告
            ApplicationManager.getApplication().invokeLater {
                try {
                    generateAndShowReports(project, analysisResult)
                    NekoamaNotifier.info(NekoamaBundle.message("action.analyzeCodeDeps.success"))
                } catch (t: Throwable) {
                    NekoamaLogger.logError(
                        "AnalyzeCodeDepsAction",
                        com.cw2.nekoama.core.exception.NekoamaError.UIError.DialogError(
                            NekoamaBundle.message("action.analyzeCodeDeps.error.reportFailed", t.message ?: "")
                        ),
                        mapOf("exception" to (t.message ?: "unknown"))
                    )
                    NekoamaNotifier.error(NekoamaBundle.message("action.analyzeCodeDeps.error.reportFailed", t.message ?: ""))
                }
            }

        } catch (t: Throwable) {
            NekoamaLogger.logError(
                "AnalyzeCodeDepsAction",
                com.cw2.nekoama.core.exception.NekoamaError.AnalysisError.DependencyAnalysisError(
                    NekoamaBundle.message("action.analyzeCodeDeps.analysisStartFailed", t.message ?: "")
                ),
                mapOf("exception" to (t.message ?: "unknown"))
            )
            NekoamaNotifier.error(NekoamaBundle.message("action.analyzeCodeDeps.error.startFailed", t.message ?: ""))
        }

        return 0 // TODO: 计算实际的Token使用量
    }

    /**
     * 执行依赖分析
     */
    private fun executeDependencyAnalysis(
        project: Project,
        config: AiAnalysisConfig,
        confirmedEntryPoints: List<String>,
        indicator: ProgressIndicator
    ): DependencyAnalysisResult {
        indicator.isIndeterminate = false
        indicator.fraction = 0.0

        try {
            // 阶段1: 初始化分析器 (10%)
            indicator.text = NekoamaBundle.message("progress.analyze.initializing")
            indicator.fraction = 0.1
            val batchProcessor = BatchAnalysisProcessor(project)

            // 阶段2: 扫描分析范围 (20%)
            indicator.text = NekoamaBundle.message("progress.analyze.scanning")
            indicator.fraction = 0.2

            // 阶段3: 执行批量依赖分析 (70%)
            indicator.text = NekoamaBundle.message("progress.analyze.dependencies")
            indicator.fraction = 0.3

            val analysisResult = runBlocking {
                batchProcessor.executeBatchAnalysis(config, indicator)
            }

            // 阶段4: 分析完成 (100%)
            if (!indicator.isCanceled) {
                indicator.text = NekoamaBundle.message("progress.analyze.completed")
                indicator.fraction = 1.0
            }

            return analysisResult

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                throw e // 重新抛出取消异常
            }
            throw RuntimeException("依赖分析执行失败: ${e.message}", e)
        }
    }

    /**
     * 生成并显示报告
     */
    private fun generateAndShowReports(project: Project, analysisResult: DependencyAnalysisResult) {
        // 创建报告输出目录
        val reportsDir = Paths.get(project.basePath, "reports", "dependency-analysis")
        val timestamp = System.currentTimeMillis()

        // 生成HTML报告
        val htmlReportPath = reportsDir.resolve("dependency-analysis-$timestamp.html")
        runBlocking {
            val reportGenerator = DependencyReportGenerator()
            reportGenerator.generateReport(analysisResult, htmlReportPath)
        }

        // 显示报告查看器
        ReportViewer.showReports(
            project,
            analysisResult,
            htmlReportPath.toFile()
        )
    }

    /**
     * 转换AnalysisConfigDialog.AnalysisConfig到AI AnalysisConfig
     */
    private fun convertToAiAnalysisConfig(dialogConfig: AnalysisConfigDialog.AnalysisConfig): AiAnalysisConfig {
        val complexityThresholds = ComplexityThresholds(
            cyclomaticComplexity = 10,
            cognitiveComplexity = 15,
            nestingDepth = 5,
            methodLength = 50,
            classLength = 300,
            parameterCount = 7
        )

        return AiAnalysisConfig(
            maxDepth = when (dialogConfig.scopeType) {
                AnalysisConfigDialog.ScopeType.PROJECT -> 10
                AnalysisConfigDialog.ScopeType.MODULE -> 8
                AnalysisConfigDialog.ScopeType.PACKAGE -> 6
                AnalysisConfigDialog.ScopeType.FILE -> 4
            },
            excludePackages = listOf("java", "javax", "org.springframework", "com.fasterxml.jackson"),
            includeTestClasses = dialogConfig.includeTestCode,
            complexityThresholds = complexityThresholds
        )
    }

    override fun getActionType(): ActionType = ActionType.ANALYZE_CODE_DEPS

    override fun requiresEditor(): Boolean = false // 可以在项目级别使用，不需要编辑器
}