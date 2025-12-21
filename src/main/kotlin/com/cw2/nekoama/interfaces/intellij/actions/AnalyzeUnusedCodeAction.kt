package com.cw2.nekoama.interfaces.intellij.actions

import com.cw2.nekoama.domain.metrics.model.ActionType
import com.cw2.nekoama.domain.code_analysis.service.UnusedCodeAnalyzer
import com.cw2.nekoama.domain.code_analysis.model.UnusedCodeAnalysisResult
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.shared.util.NekoamaNotifier
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 未使用代码分析Action
 *
 * 功能：
 * - 扫描项目中未使用的类、方法、属性
 * - 生成HTML报告
 * - 在ToolsMenu中提供入口
 */
internal class AnalyzeUnusedCodeAction : BaseAction() {

    override fun getActionType(): ActionType = ActionType.ANALYZE_UNUSED_CODE

    override fun requiresEditor(): Boolean = false

    override fun perform(project: Project, editor: Editor?, e: AnActionEvent): Int {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "未使用代码分析", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "正在分析未使用代码..."
                    indicator.fraction = 0.0

                    val analyzer = UnusedCodeAnalyzer(project)
                    val result = analyzer.analyzeUnusedCode(indicator)

                    indicator.text = "正在生成报告..."
                    indicator.fraction = 0.8

                    // 生成HTML报告
                    val reportPath = generateUnusedCodeReport(project, result)

                    indicator.text = "分析完成"
                    indicator.fraction = 1.0

                    // 询问是否打开报告
                    invokeLater {
                        val message = NekoamaBundle.message(
                            "unusedCode.analysis.complete",
                            result.unusedClasses.size, result.unusedMethods.size, result.unusedFields.size
                        )
                        val openReport = Messages.showYesNoDialog(
                            project,
                            "$message\n\n报告已保存至：$reportPath\n\n是否立即查看报告？",
                            "未使用代码分析完成",
                            "查看报告", "稍后查看",
                            Messages.getInformationIcon()
                        ) == Messages.YES

                        if (openReport) {
                            BrowserUtil.browse(reportPath.toFile())
                        }
                    }

                } catch (ex: Exception) {
                    NekoamaNotifier.error("未使用代码分析失败: ${ex.message}")
                }
            }
        })

        return 0 // 未使用AI服务，不消耗Token
    }

    /**
     * 生成未使用代码分析报告
     */
    private fun generateUnusedCodeReport(
        project: Project,
        result: UnusedCodeAnalysisResult
    ): Path {
        val timestamp =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val reportFileName = "unused-code-analysis-${timestamp}.html"
        val projectBasePath = Paths.get(project.basePath!!)
        val reportsDir = projectBasePath.resolve("nekoama-reports")
        Files.createDirectories(reportsDir)

        val reportPath = reportsDir.resolve(reportFileName)

        val htmlContent = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"zh-CN\">")
            appendLine("<head>")
            appendLine("    <meta charset=\"UTF-8\">")
            appendLine("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("    <title>${project.name} - 未使用代码分析报告</title>")
            appendLine("    <style>")
            appendLine(getReportStyles())
            appendLine("    </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("    <div class=\"container\">")
            appendLine("        <header class=\"header\">")
            appendLine("            <h1>${project.name} - 未使用代码分析报告</h1>")
            appendLine(
                "            <p>生成时间: ${
                    LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }</p>"
            )
            appendLine("        </header>")

            appendLine("        <section class=\"summary\">")
            appendLine("            <h2>分析概览</h2>")
            appendLine("            <div class=\"stats-grid\">")
            appendLine("                <div class=\"stat-card\">")
            appendLine("                    <div class=\"stat-number\">${result.unusedClasses.size}</div>")
            appendLine("                    <div class=\"stat-label\">未使用类</div>")
            appendLine("                </div>")
            appendLine("                <div class=\"stat-card\">")
            appendLine("                    <div class=\"stat-number\">${result.unusedMethods.size}</div>")
            appendLine("                    <div class=\"stat-label\">未使用方法</div>")
            appendLine("                </div>")
            appendLine("                <div class=\"stat-card\">")
            appendLine("                    <div class=\"stat-number\">${result.unusedFields.size}</div>")
            appendLine("                    <div class=\"stat-label\">未使用属性</div>")
            appendLine("                </div>")
            appendLine("            </div>")
            appendLine("        </section>")

            if (result.unusedClasses.isNotEmpty()) {
                appendLine("        <section class=\"details\">")
                appendLine("            <h2>未使用类 (${result.unusedClasses.size})</h2>")
                appendLine("            <div class=\"items-list\">")
                result.unusedClasses.forEach { unusedClass ->
                    appendLine("                <div class=\"item\">")
                    appendLine("                    <div class=\"item-name\">${unusedClass.className}</div>")
                    appendLine("                    <div class=\"item-location\">${unusedClass.location}</div>")
                    appendLine("                </div>")
                }
                appendLine("            </div>")
                appendLine("        </section>")
            }

            if (result.unusedMethods.isNotEmpty()) {
                appendLine("        <section class=\"details\">")
                appendLine("            <h2>未使用方法 (${result.unusedMethods.size})</h2>")
                appendLine("            <div class=\"items-list\">")
                result.unusedMethods.forEach { unusedMethod ->
                    appendLine("                <div class=\"item\">")
                    appendLine("                    <div class=\"item-name\">${unusedMethod.className}.${unusedMethod.methodName}</div>")
                    appendLine("                    <div class=\"item-location\">${unusedMethod.location}</div>")
                    appendLine("                </div>")
                }
                appendLine("            </div>")
                appendLine("        </section>")
            }

            if (result.unusedFields.isNotEmpty()) {
                appendLine("        <section class=\"details\">")
                appendLine("            <h2>未使用属性 (${result.unusedFields.size})</h2>")
                appendLine("            <div class=\"items-list\">")
                result.unusedFields.forEach { unusedField ->
                    appendLine("                <div class=\"item\">")
                    appendLine("                    <div class=\"item-name\">${unusedField.className}.${unusedField.fieldName}</div>")
                    appendLine("                    <div class=\"item-location\">${unusedField.location}</div>")
                    appendLine("                </div>")
                }
                appendLine("            </div>")
                appendLine("        </section>")
            }

            appendLine("    </div>")
            appendLine("</body>")
            appendLine("</html>")
        }

        Files.writeString(reportPath, htmlContent, StandardCharsets.UTF_8)
        return reportPath
    }

    /**
     * 获取报告样式
     */
    private fun getReportStyles(): String {
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
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
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
            .items-list {
                margin-top: 20px;
            }
            .item {
                background: #f8f9fa;
                border-left: 4px solid #dc3545;
                padding: 15px;
                margin-bottom: 10px;
                border-radius: 0 4px 4px 0;
            }
            .item-name {
                font-weight: bold;
                color: #2c3e50;
                margin-bottom: 5px;
            }
            .item-location {
                color: #6c757d;
                font-size: 0.9em;
            }
        """.trimIndent()
    }
}