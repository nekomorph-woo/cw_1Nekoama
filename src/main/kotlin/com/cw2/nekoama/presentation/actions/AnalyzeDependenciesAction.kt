package com.cw2.nekoama.presentation.actions

import com.cw2.nekoama.core.analysis.SimpleAnalysisConfig
import com.cw2.nekoama.core.analysis.SimpleAnalysisExecutor
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.core.reporting.ReportGenerationResult
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 代码依赖分析Action
 *
 * 功能：
 * - 分析项目代码依赖关系
 * - 生成包含可视化的HTML报告
 * - 在ToolsMenu中提供入口
 */
internal class AnalyzeDependenciesAction : BaseAction() {

    override fun getActionType(): ActionType = ActionType.ANALYZE_CODE_DEPS

    override fun requiresEditor(): Boolean = false

    override fun perform(project: Project, editor: Editor?, e: AnActionEvent): Int {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "代码依赖分析", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "正在分析代码依赖关系..."
                    indicator.fraction = 0.0

                    val executor = SimpleAnalysisExecutor(project)
                    val config = SimpleAnalysisConfig(
                        includeTestCode = false,
                        enableComplexityAnalysis = true,
                        enableCodeSmellDetection = true,
                        enableDependencyAnalysis = true
                    )

                    val analysisResult = kotlinx.coroutines.runBlocking {
                        executor.executeAnalysis(config, indicator)
                    }

                    indicator.text = "正在生成依赖分析报告..."
                    indicator.fraction = 0.8

                    // 生成HTML报告
                    val reportPath = generateDependencyReport(project, analysisResult, executor)

                    indicator.text = "分析完成"
                    indicator.fraction = 1.0

                    // 询问是否打开报告
                    com.intellij.openapi.application.invokeLater {
                        val message = NekoamaBundle.message("dependency.analysis.complete",
                            analysisResult.stats.totalClasses, analysisResult.stats.totalMethods,
                            analysisResult.stats.entryPointsCount, analysisResult.stats.codeSmellStats.totalSmells)
                        val openReport = com.intellij.openapi.ui.Messages.showYesNoDialog(
                            project,
                            "$message\n\n报告已保存至：$reportPath\n\n是否立即查看报告？",
                            "代码依赖分析完成",
                            "查看报告", "稍后查看",
                            com.intellij.openapi.ui.Messages.getInformationIcon()
                        ) == com.intellij.openapi.ui.Messages.YES

                        if (openReport) {
                            com.intellij.ide.BrowserUtil.browse(reportPath.toFile())
                        }
                    }

                } catch (ex: Exception) {
                    NekoamaNotifier.error("代码依赖分析失败: ${ex.message}")
                }
            }
        })

        return 0 // 未使用AI服务，不消耗Token
    }

    /**
     * 生成依赖分析报告
     */
    private fun generateDependencyReport(
        project: Project,
        analysisResult: com.cw2.nekoama.core.analysis.AnalysisResult,
        executor: SimpleAnalysisExecutor
    ): java.nio.file.Path {
        val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val reportFileName = "dependency-analysis-${timestamp}.html"
        val projectBasePath = Paths.get(project.basePath!!)
        val reportsDir = projectBasePath.resolve("nekoama-reports")
        Files.createDirectories(reportsDir)

        val reportPath = reportsDir.resolve(reportFileName)

        try {
            val reportResult: ReportGenerationResult = kotlinx.coroutines.runBlocking {
                executor.generateHtmlReport(analysisResult, reportPath)
            }

            if (!reportResult.success) {
                throw RuntimeException("报告生成失败: ${reportResult.message}")
            }

            return reportResult.outputPath
        } catch (ex: Exception) {
            // 如果使用新的报告生成器失败，则创建一个简单的备用报告
            NekoamaNotifier.warn("使用高级可视化报告生成失败，创建简化版报告")
            return createSimpleFallbackReport(project, analysisResult, reportPath)
        }
    }

    /**
     * 创建简化的备用报告
     */
    private fun createSimpleFallbackReport(
        project: Project,
        analysisResult: com.cw2.nekoama.core.analysis.AnalysisResult,
        reportPath: java.nio.file.Path
    ): java.nio.file.Path {
        val htmlContent = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"zh-CN\">")
            appendLine("<head>")
            appendLine("    <meta charset=\"UTF-8\">")
            appendLine("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("    <title>${project.name} - 代码依赖分析报告</title>")
            appendLine("    <style>")
            appendLine(getSimpleReportStyles())
            appendLine("    </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("    <div class=\"container\">")
            appendLine("        <header class=\"header\">")
            appendLine("            <h1>${project.name} - 代码依赖分析报告</h1>")
            appendLine("            <p>生成时间: ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}</p>")
            appendLine("            <p><strong>注意：</strong> 由于系统限制，当前显示的是简化版报告。完整可视化报告功能正在维护中。</p>")
            appendLine("        </header>")

            appendLine("        <section class=\"summary\">")
            appendLine("            <h2>分析概览</h2>")
            appendLine("            <div class=\"stats-grid\">")
            appendLine("                <div class=\"stat-card\">")
            appendLine("                    <div class=\"stat-number\">${analysisResult.stats.totalClasses}</div>")
            appendLine("                    <div class=\"stat-label\">总类数</div>")
            appendLine("                </div>")
            appendLine("                <div class=\"stat-card\">")
            appendLine("                    <div class=\"stat-number\">${analysisResult.stats.totalMethods}</div>")
            appendLine("                    <div class=\"stat-label\">总方法数</div>")
            appendLine("                </div>")
            appendLine("                <div class=\"stat-card\">")
            appendLine("                    <div class=\"stat-number\">${analysisResult.stats.entryPointsCount}</div>")
            appendLine("                    <div class=\"stat-label\">入口点数</div>")
            appendLine("                </div>")
            appendLine("                <div class=\"stat-card\">")
            appendLine("                    <div class=\"stat-number\">${analysisResult.stats.complexityStats.totalComplexity}</div>")
            appendLine("                    <div class=\"stat-label\">总复杂度</div>")
            appendLine("                </div>")
            appendLine("            </div>")
            appendLine("        </section>")

            appendLine("        <section class=\"details\">")
            appendLine("            <h2>复杂度分析</h2>")
            appendLine("            <div class=\"complexity-info\">")
            appendLine("                <p><strong>平均复杂度:</strong> ${"%.2f".format(analysisResult.stats.complexityStats.averageComplexity)}</p>")
            appendLine("                <p><strong>高复杂度方法 (>30):</strong> ${analysisResult.stats.complexityStats.highComplexityMethods}</p>")
            appendLine("            </div>")
            appendLine("        </section>")

            appendLine("        <section class=\"details\">")
            appendLine("            <h2>代码质量问题</h2>")
            appendLine("            <div class=\"issues-summary\">")
            appendLine("                <p><strong>总问题数:</strong> ${analysisResult.stats.codeSmellStats.totalSmells}</p>")
            appendLine("                <p><strong>严重问题:</strong> ${analysisResult.stats.codeSmellStats.criticalSmells}</p>")
            appendLine("                <p><strong>高优先级问题:</strong> ${analysisResult.stats.codeSmellStats.highSmells}</p>")
            appendLine("            </div>")
            appendLine("        </section>")

            appendLine("        <section class=\"details\">")
            appendLine("            <h2>入口点分析</h2>")
            appendLine("            <div class=\"entry-points\">")
            analysisResult.entryPoints.take(10).forEach { entryPoint ->
                appendLine("                <div class=\"entry-point\">")
                appendLine("                    <div class=\"entry-name\">${entryPoint.className}.${entryPoint.methodName}</div>")
                appendLine("                    <div class=\"entry-type\">${entryPoint.entryType}</div>")
                appendLine("                    <div class=\"entry-scenario\">${entryPoint.businessScenario}</div>")
                appendLine("                </div>")
            }
            if (analysisResult.entryPoints.size > 10) {
                appendLine("                <p><em>... 还有 ${analysisResult.entryPoints.size - 10} 个入口点未显示</em></p>")
            }
            appendLine("            </div>")
            appendLine("        </section>")

            appendLine("    </div>")
            appendLine("</body>")
            appendLine("</html>")
        }

        Files.writeString(reportPath, htmlContent, java.nio.charset.StandardCharsets.UTF_8)
        return reportPath
    }

    /**
     * 获取简化报告样式
     */
    private fun getSimpleReportStyles(): String {
        return """
            body {
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                line-height: 1.6;
                margin: 0;
                padding: 20px;
                background-color: #f5f5f5;
                color: #333;
            }
            .container {
                max-width: 1200px;
                margin: 0 auto;
                background: white;
                padding: 30px;
                border-radius: 8px;
                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            }
            .header {
                text-align: center;
                margin-bottom: 40px;
                padding-bottom: 20px;
                border-bottom: 2px solid #e9ecef;
            }
            .header h1 {
                color: #2c3e50;
                margin-bottom: 10px;
            }
            .summary {
                margin-bottom: 40px;
            }
            .stats-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 20px;
                margin-top: 20px;
            }
            .stat-card {
                background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
                color: white;
                padding: 20px;
                border-radius: 8px;
                text-align: center;
            }
            .stat-number {
                font-size: 2.5em;
                font-weight: bold;
                margin-bottom: 5px;
            }
            .stat-label {
                font-size: 1.1em;
                opacity: 0.9;
            }
            .details {
                margin-bottom: 30px;
            }
            .details h2 {
                color: #2c3e50;
                border-bottom: 2px solid #e9ecef;
                padding-bottom: 10px;
            }
            .complexity-info, .issues-summary {
                background: #f8f9fa;
                padding: 20px;
                border-radius: 8px;
                margin-top: 20px;
            }
            .entry-points {
                margin-top: 20px;
            }
            .entry-point {
                background: #e8f4fd;
                border-left: 4px solid #2196F3;
                padding: 15px;
                margin-bottom: 10px;
                border-radius: 0 4px 4px 0;
            }
            .entry-name {
                font-weight: bold;
                color: #2c3e50;
                margin-bottom: 5px;
            }
            .entry-type {
                color: #2196F3;
                font-size: 0.9em;
                margin-bottom: 3px;
            }
            .entry-scenario {
                color: #6c757d;
                font-size: 0.9em;
            }
        """.trimIndent()
    }
}