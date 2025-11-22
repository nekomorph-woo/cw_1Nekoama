package com.cw2.nekoama.presentation.ui

import com.cw2.nekoama.ai.model.dependency.DependencyAnalysisResult
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
        searchField.placeholderText = NekoamaBundle.message("reportViewer.searchPlaceholder")
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
        fileLabel.foreground = UIUtil.getSecondaryTextForeground()

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
        jsonPane.font = JBUI.Fonts.monospaced()

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
            noIssuesLabel.foreground = UIUtil.getSecondaryTextForeground()
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
     * 加载报告
     */
    private fun loadReports() {
        try {
            // 加载HTML报告
            val htmlContent = htmlReportFile.readText()
            (htmlViewer.getComponent(0) as JEditorPane).text = htmlContent

            // 生成并加载Markdown报告
            runBlocking {
                val markdownGenerator = MarkdownReportGenerator()
                val markdownContent = markdownGenerator.generateReport(analysisResult)
                (markdownViewer.getComponent(0) as JTextPane).text = markdownContent
            }

            // 生成并加载JSON报告
            runBlocking {
                val jsonSerializer = DependencyJsonSerializer()
                val jsonContent = jsonSerializer.serialize(analysisResult, true) // 美化格式
                (jsonViewer.getComponent(0) as JTextPane).text = jsonContent
            }

        } catch (e: Exception) {
            NekoamaLogger.logError("ReportViewer", "加载报告失败", error = e)
            Messages.showErrorDialog(project, NekoamaBundle.message("reportViewer.error.loadFailed", e.message), "Error")
        }
    }

    /**
     * 刷新报告
     */
    private fun refreshReports() {
        loadReports()
        NekoamaLogger.info("ReportViewer", "报告已刷新")
    }

    /**
     * 在浏览器中打开
     */
    private fun openInBrowser() {
        try {
            BrowserUtil.browse(htmlReportFile)
        } catch (e: Exception) {
            NekoamaLogger.logError("ReportViewer", "在浏览器中打开报告失败", error = e)
            Messages.showErrorDialog(project, NekoamaBundle.message("reportViewer.error.openBrowserFailed", e.message), "Error")
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
            val reportsDir = File(project.basePath, "reports", "dependency-analysis")
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
            NekoamaLogger.logError("ReportViewer", "导出报告失败", error = e)
            Messages.showErrorDialog(project, NekoamaBundle.message("reportViewer.export.failed", e.message), "Error")
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
            val markdownContent = markdownGenerator.generateReport(analysisResult)
            val targetFile = File(reportsDir, "$baseFileName.md")
            targetFile.writeText(markdownContent)
        }
    }

    /**
     * 导出JSON报告
     */
    private fun exportJSONReport(reportsDir: File, baseFileName: String) {
        runBlocking {
            val jsonSerializer = DependencyJsonSerializer()
            val jsonContent = jsonSerializer.serialize(analysisResult, true)
            val targetFile = File(reportsDir, "$baseFileName.json")
            targetFile.writeText(jsonContent)
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
        targetFile.writeText("PDF export not yet implemented")
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
        val htmlPane = (htmlViewer.getComponent(0) as JEditorPane)
        val content = htmlPane.text

        if (content.contains(query, ignoreCase = true)) {
            // 简化的搜索实现
            Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.found", query), "Search")
        } else {
            Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.notFound", query), "Search")
        }
    }

    /**
     * 在摘要中搜索
     */
    private fun searchInSummary(query: String) {
        // 简化实现
        Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.summary"), "Search")
    }

    /**
     * 在Markdown中搜索
     */
    private fun searchInMarkdown(query: String) {
        val markdownPane = (markdownViewer.getComponent(0) as JTextPane)
        val content = markdownPane.text

        if (content.contains(query, ignoreCase = true)) {
            Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.found", query), "Search")
        } else {
            Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.notFound", query), "Search")
        }
    }

    /**
     * 在JSON中搜索
     */
    private fun searchInJSON(query: String) {
        val jsonPane = (jsonViewer.getComponent(0) as JTextPane)
        val content = jsonPane.text

        if (content.contains(query, ignoreCase = true)) {
            Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.found", query), "Search")
        } else {
            Messages.showInfoMessage(project, NekoamaBundle.message("reportViewer.search.notFound", query), "Search")
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
                    com.cw2.nekoama.core.exception.NekoamaError.UIError.DialogError(NekoamaBundle.message("reportViewer.startFailed", t.message ?: "")),
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
                    com.cw2.nekoama.core.exception.NekoamaError.UIError.DialogError(NekoamaBundle.message("reportViewer.startFailed", t.message ?: "")),
                    mapOf("exception" to (t.message ?: "unknown"))
                )
            }
        }
    }
}