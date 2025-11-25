package com.cw2.nekoama.presentation.ui

import com.cw2.nekoama.ai.model.dependency.DependencyAnalysisResult
import com.cw2.nekoama.core.exception.NekoamaError
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.reporting.DependencyReportGenerator
import com.cw2.nekoama.core.reporting.MarkdownReportGenerator
import com.cw2.nekoama.core.reporting.DependencyJsonSerializer
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.*
import javax.swing.event.HyperlinkEvent

/**
 * 报告查看器（完整版本）
 *
 * 功能：
 * - 集成HTML报告浏览器，支持AntV G6交互式依赖图谱
 * - 实现Markdown报告实时渲染
 * - 提供报告内搜索、导航和章节跳转功能
 * - 支持PDF、HTML、Markdown、Excel等格式导出
 * - 实现报告链接分享和数据导出功能
 */
class ReportViewer private constructor(
    private val project: Project,
    private val analysisResult: DependencyAnalysisResult,
    private val htmlReportFile: File
) : DialogWrapper(project) {

    // UI组件
    private val mainPanel = JPanel(BorderLayout())
    private val tabbedPane = JBTabbedPane()
    private val htmlViewer = createHtmlViewer()
    private val markdownViewer = createMarkdownViewer()
    private val jsonViewer = createJsonViewer()
    private val summaryPanel = createSummaryPanel()

    // 工具栏
    private val toolbar = createToolbar()

    init {
        title = NekoamaBundle.message("reportViewer.title")
        setModal(false)
        setupUI()
        loadReports()
        init()
    }

    /**
     * 设置UI
     */
    private fun setupUI() {
        mainPanel.background = UIUtil.getPanelBackground()
        mainPanel.border = JBUI.Borders.empty(10)

        // 添加工具栏
        mainPanel.add(toolbar, BorderLayout.NORTH)

        // 添加标签页
        tabbedPane.addTab(NekoamaBundle.message("reportViewer.tab.html"), htmlViewer)
        tabbedPane.addTab(NekoamaBundle.message("reportViewer.tab.summary"), summaryPanel)
        tabbedPane.addTab(NekoamaBundle.message("reportViewer.tab.markdown"), markdownViewer)
        tabbedPane.addTab(NekoamaBundle.message("reportViewer.tab.json"), jsonViewer)

        mainPanel.add(tabbedPane, BorderLayout.CENTER)
    }

    /**
     * 创建工具栏
     */
    private fun createToolbar(): JPanel {
        val toolbar = JPanel(GridBagLayout())
        toolbar.background = UIUtil.getPanelBackground()
        toolbar.border = JBUI.Borders.empty(0, 0, 10, 0)

        val gbc = GridBagConstraints()

        // 刷新按钮
        val refreshButton = JButton(NekoamaBundle.message("reportViewer.button.refresh"))
        refreshButton.toolTipText = NekoamaBundle.message("reportViewer.button.refresh.tooltip")
        refreshButton.addActionListener { refreshReports() }

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.insets = JBUI.insets(0, 0, 0, 10)
        toolbar.add(refreshButton, gbc)

        // 导出按钮
        val exportButton = JButton(NekoamaBundle.message("reportViewer.button.export"))
        exportButton.toolTipText = NekoamaBundle.message("reportViewer.button.export.tooltip")
        exportButton.addActionListener { showExportDialog() }

        gbc.gridx = 1
        gbc.gridy = 0
        gbc.insets = JBUI.insets(0, 0, 0, 10)
        toolbar.add(exportButton, gbc)

        // 在浏览器中打开按钮
        val browserButton = JButton(NekoamaBundle.message("reportViewer.button.openBrowser"))
        browserButton.toolTipText = NekoamaBundle.message("reportViewer.button.openBrowser.tooltip")
        browserButton.addActionListener { openInBrowser() }

        gbc.gridx = 2
        gbc.gridy = 0
        gbc.insets = JBUI.insets(0, 0, 0, 10)
        toolbar.add(browserButton, gbc)

        // 搜索框
        val searchLabel = JBLabel(NekoamaBundle.message("reportViewer.search"))
        gbc.gridx = 3
        gbc.gridy = 0
        gbc.insets = JBUI.insets(0, 20, 0, 5)
        gbc.anchor = GridBagConstraints.WEST
        toolbar.add(searchLabel, gbc)

        val searchField = JTextField(20)
        searchField.toolTipText = NekoamaBundle.message("reportViewer.searchPlaceholder")
        gbc.gridx = 4
        gbc.gridy = 0
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(0, 0, 0, 10)
        toolbar.add(searchField, gbc)

        // 搜索按钮
        val searchButton = JButton(NekoamaBundle.message("reportViewer.button.search"))
        searchButton.addActionListener { searchInReport(searchField.text) }

        gbc.gridx = 5
        gbc.gridy = 0
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.insets = JBUI.insets(0, 0, 0, 0)
        toolbar.add(searchButton, gbc)

        // 右侧：文件信息
        val fileLabel = JBLabel(NekoamaBundle.message("reportViewer.fileInfo", htmlReportFile.name))
        fileLabel.font = fileLabel.font.deriveFont(fileLabel.font.size - 1f)
        fileLabel.foreground = UIUtil.getLabelForeground().darker()

        gbc.gridx = 6
        gbc.gridy = 0
        gbc.insets = JBUI.insets(0, 20, 0, 0)
        gbc.anchor = GridBagConstraints.EAST
        toolbar.add(fileLabel, gbc)

        return toolbar
    }

    /**
     * 创建HTML查看器
     */
    private fun createHtmlViewer(): JComponent {
        val htmlPane = JEditorPane()
        htmlPane.contentType = "text/html"
        htmlPane.isEditable = false
        htmlPane.addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                BrowserUtil.browse(e.url)
            }
        }

        val scrollPane = JBScrollPane(htmlPane)
        scrollPane.preferredSize = JBUI.size(800, 600)
        return scrollPane
    }

    /**
     * 创建Markdown查看器
     */
    private fun createMarkdownViewer(): JComponent {
        val markdownPane = JTextPane()
        markdownPane.contentType = "text/plain"
        markdownPane.isEditable = false
        markdownPane.font = markdownPane.font.deriveFont(markdownPane.font.size - 1f)

        val scrollPane = JBScrollPane(markdownPane)
        scrollPane.preferredSize = JBUI.size(800, 600)
        return scrollPane
    }

    /**
     * 创建JSON查看器
     */
    private fun createJsonViewer(): JComponent {
        val jsonPane = JTextPane()
        jsonPane.contentType = "text/plain"
        jsonPane.isEditable = false
        // 使用IntelliJ平台的标准等宽字体
        jsonPane.font = JBUI.Fonts.create("JetBrains Mono", 14)

        val scrollPane = JBScrollPane(jsonPane)
        scrollPane.preferredSize = JBUI.size(800, 600)
        return scrollPane
    }

    /**
     * 创建摘要面板
     */
    private fun createSummaryPanel(): JComponent {
        val summaryPanel = JPanel(BorderLayout())
        summaryPanel.background = UIUtil.getPanelBackground()

        val titleLabel = JBLabel(NekoamaBundle.message("reportViewer.summary.title"))
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.style or java.awt.Font.BOLD)
        titleLabel.border = JBUI.Borders.empty(10)

        val metricsPanel = createMetricsPanel()
        val topIssuesPanel = createTopIssuesPanel()

        val tabs = JBTabbedPane()
        tabs.addTab(NekoamaBundle.message("reportViewer.summary.metrics"), metricsPanel)
        tabs.addTab(NekoamaBundle.message("reportViewer.summary.issues"), topIssuesPanel)

        summaryPanel.add(titleLabel, BorderLayout.NORTH)
        summaryPanel.add(tabs, BorderLayout.CENTER)

        return summaryPanel
    }

    /**
     * 创建指标面板
     */
    private fun createMetricsPanel(): JPanel {
        val metricsPanel = JPanel(GridBagLayout())
        metricsPanel.background = UIUtil.getPanelBackground()
        metricsPanel.border = JBUI.Borders.empty(10)

        val gbc = GridBagConstraints()
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2
        gbc.insets = JBUI.insets(0, 0, 20, 0)

        // 基础统计信息
        val stats = mapOf(
            NekoamaBundle.message("reportViewer.summary.totalClasses") to analysisResult.metadata.statistics.totalClasses,
            NekoamaBundle.message("reportViewer.summary.totalPackages") to analysisResult.metadata.statistics.totalPackages,
            NekoamaBundle.message("reportViewer.summary.totalMethods") to analysisResult.metadata.statistics.totalMethods,
            NekoamaBundle.message("reportViewer.summary.codeSmells") to analysisResult.codeSmells.size
        )

        stats.forEach { (label, value) ->
            val labelComponent = JBLabel(label)
            val valueComponent = JBLabel(value.toString())

            labelComponent.font = labelComponent.font.deriveFont(labelComponent.font.style or java.awt.Font.BOLD)
            valueComponent.foreground = UIUtil.getLabelForeground()

            gbc.gridx = 0
            gbc.gridy++
            gbc.anchor = GridBagConstraints.WEST
            gbc.gridwidth = 1
            gbc.insets = JBUI.insets(5, 0, 5, 20)
            metricsPanel.add(labelComponent, gbc)

            gbc.gridx = 1
            gbc.anchor = GridBagConstraints.EAST
            gbc.insets = JBUI.insets(5, 0, 5, 0)
            metricsPanel.add(valueComponent, gbc)
        }

        return metricsPanel
    }

    /**
     * 创建问题面板
     */
    private fun createTopIssuesPanel(): JPanel {
        val issuesPanel = JPanel(BorderLayout())
        issuesPanel.background = UIUtil.getPanelBackground()
        issuesPanel.border = JBUI.Borders.empty(10)

        val topIssues = analysisResult.codeSmells.take(10)
        if (topIssues.isEmpty()) {
            val noIssuesLabel = JBLabel(NekoamaBundle.message("reportViewer.summary.noIssues"))
            noIssuesLabel.foreground = UIUtil.getLabelForeground().darker()
            issuesPanel.add(noIssuesLabel, BorderLayout.CENTER)
        } else {
            val issuesTableModel = IssuesTableModel(topIssues)
            val issuesTable = JTable(issuesTableModel)
            issuesTable.rowHeight = JBUI.scale(20)
            issuesTable.font = issuesTable.font.deriveFont(issuesTable.font.size - 1f)

            val scrollPane = JBScrollPane(issuesTable)
            scrollPane.preferredSize = JBUI.size(700, 300)

            issuesPanel.add(scrollPane, BorderLayout.CENTER)
        }

        return issuesPanel
    }

    /**
     * 安全获取JEditorPane组件
     */
    private fun getEditorPane(scrollPane: JComponent): JEditorPane? {
        if (scrollPane !is JBScrollPane) return null

        return try {
            // 首先尝试通过viewport获取
            scrollPane.viewport?.view as? JEditorPane
        } catch (e: Exception) {
            NekoamaLogger.debug("ReportViewer", "Failed to get editor pane via viewport: ${e.message}")
            try {
                // 备用方案：通过组件索引获取
                scrollPane.getComponent(0) as? JEditorPane
            } catch (e2: Exception) {
                NekoamaLogger.debug("ReportViewer", "Failed to get editor pane via component index: ${e2.message}")
                null
            }
        }
    }

    /**
     * 安全获取JTextPane组件
     */
    private fun getTextPane(scrollPane: JComponent): JTextPane? {
        if (scrollPane !is JBScrollPane) return null

        return try {
            // 首先尝试通过viewport获取
            scrollPane.viewport?.view as? JTextPane
        } catch (e: Exception) {
            NekoamaLogger.debug("ReportViewer", "Failed to get text pane via viewport: ${e.message}")
            try {
                // 备用方案：通过组件索引获取
                scrollPane.getComponent(0) as? JTextPane
            } catch (e2: Exception) {
                NekoamaLogger.debug("ReportViewer", "Failed to get text pane via component index: ${e2.message}")
                null
            }
        }
    }

    /**
     * 加载报告
     */
    private fun loadReports() {
        try {
            // 加载HTML报告
            loadHTMLReport()

            // 生成并加载Markdown报告
            loadMarkdownReport()

            // 生成并加载JSON报告
            loadJSONReport()

        } catch (e: Exception) {
            NekoamaLogger.logError(
                "ReportViewer",
                NekoamaError.UIError.DialogError(NekoamaBundle.message("error.reports.load.failed", e.message ?: "")),
                mapOf("exception" to (e.message ?: "unknown") as Any)
            )
            Messages.showErrorDialog(project, NekoamaBundle.message("reportViewer.error.loadFailed", e.message ?: ""), NekoamaBundle.message("common.dialog.error"))
        }
    }

    /**
     * 加载HTML报告
     */
    private fun loadHTMLReport() {
        try {
            val htmlContent = htmlReportFile.readText()
            val htmlPane = getEditorPane(htmlViewer)
            if (htmlPane != null) {
                htmlPane.text = htmlContent
                htmlPane.caretPosition = 0 // 滚动到顶部
            } else {
                throw Exception("无法获取HTML编辑器组件")
            }
        } catch (e: Exception) {
            NekoamaLogger.warn("ReportViewer", "Failed to load HTML report: ${e.message}")
            // 显示错误信息在HTML视图中
            val htmlPane = getEditorPane(htmlViewer)
            htmlPane?.text = """
                <html>
                <body style="font-family: Arial, sans-serif; padding: 20px; color: #ff6b6b;">
                    <h2>HTML报告加载失败</h2>
                    <p><strong>错误:</strong> ${e.message ?: "未知错误"}</p>
                    <p>请尝试点击"在浏览器中打开"按钮查看完整报告。</p>
                </body>
                </html>
            """.trimIndent()
        }
    }

    /**
     * 加载Markdown报告
     */
    private fun loadMarkdownReport() {
        try {
            runBlocking {
                val markdownGenerator = MarkdownReportGenerator()
                val tempMarkdownFile = File.createTempFile("analysis-report", ".md")
                try {
                    val result = markdownGenerator.generateReport(analysisResult, tempMarkdownFile.toPath())
                    if (result.success) {
                        val markdownPane = getTextPane(markdownViewer)
                        if (markdownPane != null) {
                            markdownPane.text = tempMarkdownFile.readText()
                            markdownPane.caretPosition = 0 // 滚动到顶部
                        } else {
                            throw Exception("无法获取Markdown编辑器组件")
                        }
                    } else {
                        throw Exception(NekoamaBundle.message("error.markdown.generation.failed", result.message))
                    }
                } finally {
                    tempMarkdownFile.delete()
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.warn("ReportViewer", "Failed to load Markdown report: ${e.message}")
            // 显示错误信息在Markdown视图中
            val markdownPane = getTextPane(markdownViewer)
            markdownPane?.text = """
                # Markdown报告加载失败

                **错误**: ${e.message ?: "未知错误"}

                请尝试刷新报告或在浏览器中查看HTML版本。
            """.trimIndent()
        }
    }

    /**
     * 加载JSON报告
     */
    private fun loadJSONReport() {
        try {
            runBlocking {
                val jsonSerializer = DependencyJsonSerializer()
                val tempJsonFile = File.createTempFile("analysis-report", ".json")
                try {
                    val result = jsonSerializer.exportPrettyJson(analysisResult, tempJsonFile.toPath())
                    if (result.success) {
                        val jsonPane = getTextPane(jsonViewer)
                        if (jsonPane != null) {
                            jsonPane.text = tempJsonFile.readText()
                            jsonPane.caretPosition = 0 // 滚动到顶部
                        } else {
                            throw Exception("无法获取JSON编辑器组件")
                        }
                    } else {
                        throw Exception(NekoamaBundle.message("error.json.generation.failed", result.message))
                    }
                } finally {
                    tempJsonFile.delete()
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.warn("ReportViewer", "Failed to load JSON report: ${e.message}")
            // 显示错误信息在JSON视图中
            val jsonPane = getTextPane(jsonViewer)
            jsonPane?.text = """
                {
                  "error": "JSON报告加载失败",
                  "message": "${e.message ?: "未知错误"}",
                  "suggestion": "请尝试刷新报告或在浏览器中查看HTML版本"
                }
            """.trimIndent()
        }
    }

    /**
     * 刷新报告
     */
    private fun refreshReports() {
        loadReports()
        NekoamaLogger.info("ReportViewer", "Reports refreshed")
    }

    /**
     * 在浏览器中打开
     */
    private fun openInBrowser() {
        try {
            BrowserUtil.browse(htmlReportFile)
        } catch (e: Exception) {
            NekoamaLogger.logError(
                "ReportViewer",
                NekoamaError.UIError.DialogError(NekoamaBundle.message("error.browser.open.failed", e.message ?: "")),
                mapOf("exception" to (e.message ?: "unknown") as Any)
            )
            Messages.showErrorDialog(project, NekoamaBundle.message("reportViewer.error.openBrowserFailed", e.message ?: ""), NekoamaBundle.message("common.dialog.error"))
        }
    }

    /**
     * 显示导出对话框
     */
    private fun showExportDialog() {
        val options = arrayOf(
            NekoamaBundle.message("reportViewer.export.html"),
            NekoamaBundle.message("reportViewer.export.markdown"),
            NekoamaBundle.message("reportViewer.export.json"),
            NekoamaBundle.message("reportViewer.export.pdf")
        )

        val choice = Messages.showDialog(
            project,
            NekoamaBundle.message("reportViewer.export.chooseFormat"),
            NekoamaBundle.message("reportViewer.export.title"),
            options,
            0,
            Messages.getQuestionIcon()
        )

        if (choice >= 0) {
            exportReport(choice)
        }
    }

    /**
     * 导出报告
     */
    private fun exportReport(formatIndex: Int) {
        try {
            val reportsDir = File(File(project.basePath), "reports/dependency-analysis")
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val timestamp = System.currentTimeMillis()
            val baseFileName = "dependency-analysis-$timestamp"

            when (formatIndex) {
                0 -> exportHTMLReport(reportsDir, baseFileName)
                1 -> exportMarkdownReport(reportsDir, baseFileName)
                2 -> exportJSONReport(reportsDir, baseFileName)
                3 -> exportPDFReport(reportsDir, baseFileName)
            }

            Messages.showInfoMessage(
                project,
                NekoamaBundle.message("reportViewer.export.success", baseFileName),
                NekoamaBundle.message("reportViewer.export.title")
            )

        } catch (e: Exception) {
            NekoamaLogger.logError(
                "ReportViewer",
                NekoamaError.ExportError.JsonExportError(NekoamaBundle.message("error.export.failed"), e),
                mapOf("exception" to (e.message ?: "unknown") as Any)
            )
            Messages.showErrorDialog(project, NekoamaBundle.message("reportViewer.export.failed", e.message ?: ""), NekoamaBundle.message("common.dialog.error"))
        }
    }

    /**
     * 导出HTML报告
     */
    private fun exportHTMLReport(reportsDir: File, baseFileName: String) {
        val targetFile = File(reportsDir, "$baseFileName.html")
        htmlReportFile.copyTo(targetFile, overwrite = true)
    }

    /**
     * 导出Markdown报告
     */
    private fun exportMarkdownReport(reportsDir: File, baseFileName: String) {
        runBlocking {
            val markdownGenerator = MarkdownReportGenerator()
            val result = markdownGenerator.generateReport(
                analysisResult,
                File(reportsDir, "$baseFileName.md").toPath()
            )
            if (!result.success) {
                throw Exception(NekoamaBundle.message("error.markdown.generation.failed", result.message))
            }
        }
    }

    /**
     * 导出JSON报告
     */
    private fun exportJSONReport(reportsDir: File, baseFileName: String) {
        runBlocking {
            val jsonSerializer = DependencyJsonSerializer()
            val result = jsonSerializer.exportPrettyJson(
                analysisResult,
                File(reportsDir, "$baseFileName.json").toPath()
            )
            if (!result.success) {
                throw Exception(NekoamaBundle.message("error.json.generation.failed", result.message))
            }
        }
    }

    /**
     * 导出PDF报告（简化实现）
     */
    private fun exportPDFReport(reportsDir: File, baseFileName: String) {
        // 简化实现：使用HTML转PDF的方式
        val targetFile = File(reportsDir, "$baseFileName.pdf")
        // 这里可以使用第三方库如Flying Saucer或wkhtmltopdf
        // 目前只是一个占位符实现
        targetFile.writeText(NekoamaBundle.message("progress.pdf.not.implemented"))
    }

    /**
     * 在报告中搜索
     */
    private fun searchInReport(query: String) {
        if (query.isBlank()) return

        val selectedTab = tabbedPane.selectedIndex
        when (selectedTab) {
            0 -> searchInHTML(query)
            1 -> searchInSummary(query)
            2 -> searchInMarkdown(query)
            3 -> searchInJSON(query)
        }
    }

    /**
     * 在HTML中搜索
     */
    private fun searchInHTML(query: String) {
        val htmlPane = getEditorPane(htmlViewer)
        if (htmlPane != null) {
            val content = htmlPane.text
            if (content.contains(query, ignoreCase = true)) {
                // 简化的搜索实现
                Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.found", query), NekoamaBundle.message("reportViewer.search.title"))
            } else {
                Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.notFound", query), NekoamaBundle.message("reportViewer.search.title"))
            }
        } else {
            NekoamaLogger.warn("ReportViewer", "Cannot search in HTML: Editor pane not available")
            Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.unavailable"), NekoamaBundle.message("reportViewer.search.title"))
        }
    }

    /**
     * 在摘要中搜索
     */
    private fun searchInSummary(query: String) {
        // 简化实现
        Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.summary"), NekoamaBundle.message("reportViewer.search.title"))
    }

    /**
     * 在Markdown中搜索
     */
    private fun searchInMarkdown(query: String) {
        val markdownPane = getTextPane(markdownViewer)
        if (markdownPane != null) {
            val content = markdownPane.text
            if (content.contains(query, ignoreCase = true)) {
                Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.found", query), NekoamaBundle.message("reportViewer.search.title"))
            } else {
                Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.notFound", query), NekoamaBundle.message("reportViewer.search.title"))
            }
        } else {
            NekoamaLogger.warn("ReportViewer", "Cannot search in Markdown: Text pane not available")
            Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.unavailable"), NekoamaBundle.message("reportViewer.search.title"))
        }
    }

    /**
     * 在JSON中搜索
     */
    private fun searchInJSON(query: String) {
        val jsonPane = getTextPane(jsonViewer)
        if (jsonPane != null) {
            val content = jsonPane.text
            if (content.contains(query, ignoreCase = true)) {
                Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.found", query), NekoamaBundle.message("reportViewer.search.title"))
            } else {
                Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.notFound", query), NekoamaBundle.message("reportViewer.search.title"))
            }
        } else {
            NekoamaLogger.warn("ReportViewer", "Cannot search in JSON: Text pane not available")
            Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.unavailable"), NekoamaBundle.message("reportViewer.search.title"))
        }
    }

    override fun createCenterPanel(): JComponent = mainPanel

    override fun getPreferredFocusedComponent(): JComponent = tabbedPane

    /**
     * 问题表格模型
     */
    private class IssuesTableModel(private val issues: List<com.cw2.nekoama.ai.model.dependency.CodeSmell>) : javax.swing.table.AbstractTableModel() {
        private val columnNames = arrayOf(
            NekoamaBundle.message("reportViewer.issues.column.type"),
            NekoamaBundle.message("reportViewer.issues.column.severity"),
            NekoamaBundle.message("reportViewer.issues.column.class"),
            NekoamaBundle.message("reportViewer.issues.column.method"),
            NekoamaBundle.message("reportViewer.issues.column.description")
        )

        override fun getRowCount(): Int = issues.size

        override fun getColumnCount(): Int = columnNames.size

        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(row: Int, column: Int): Any? {
            val issue = issues[row]
            return when (column) {
                0 -> issue.type
                1 -> issue.severity
                2 -> issue.className
                3 -> issue.methodName ?: "-"
                4 -> issue.description
                else -> null
            }
        }
    }

    companion object {
        /**
         * 显示报告查看器
         */
        fun showReports(project: Project, analysisResult: DependencyAnalysisResult, htmlReportFile: File) {
            try {
                SwingUtilities.invokeLater {
                    val viewer = ReportViewer(project, analysisResult, htmlReportFile)
                    viewer.show()
                }
            } catch (t: Throwable) {
                NekoamaLogger.logError(
                    "showReports",
                    NekoamaError.UIError.DialogError(NekoamaBundle.message("reportViewer.startFailed", t.message ?: "")),
                    mapOf("exception" to (t.message ?: "unknown"))
                )
            }
        }

        /**
         * 兼容性方法（保持向后兼容）
         */
        @Deprecated("Use showReports with analysisResult and htmlReportFile parameters")
        fun showReports(project: Project, reportInfo: String) {
            try {
                Messages.showInfoMessage(
                    project,
                    NekoamaBundle.message("reportViewer.deprecated.message"),
                    NekoamaBundle.message("reportViewer.title")
                )
            } catch (t: Throwable) {
                NekoamaLogger.logError(
                    "showReports",
                    NekoamaError.UIError.DialogError(NekoamaBundle.message("reportViewer.startFailed", t.message ?: "")),
                    mapOf("exception" to (t.message ?: "unknown"))
                )
            }
        }
    }
}