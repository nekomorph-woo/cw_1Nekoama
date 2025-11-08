package com.cw2.nekoama.presentation.toolwindow

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.MetricsUpdateListener
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import com.intellij.util.ui.JBFont
import javax.swing.border.EmptyBorder

/**
 * 功能完整的Nekoama工具窗口
 * 包含实时统计、详细分析、历史数据查看等功能
 */
class FullFeaturedToolWindow : MetricsUpdateListener {

    private val mainPanel = JPanel(BorderLayout())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Tab面板
    private val tabbedPane = JBTabbedPane()

    // 各个功能面板
    private lateinit var overviewPanel: JPanel
    private lateinit var detailsPanel: JPanel
    private lateinit var historyPanel: JPanel

    init {
        setupUI()
        registerMetricsListener()
        refreshAllData()
    }

    private fun setupUI() {
        mainPanel.border = JBEmptyBorder(10)

        // 创建标题
        val titleLabel = JBLabel(NekoamaBundle.message("toolwindow.title"))
        titleLabel.font = titleLabel.font.deriveFont(18f).deriveFont(JBFont.BOLD)

        // 创建各个功能面板
        overviewPanel = createOverviewPanel()
        detailsPanel = createDetailsPanel()
        historyPanel = createHistoryPanel()

        // 添加Tab
        tabbedPane.addTab("概览统计", overviewPanel)
        tabbedPane.addTab("详细分析", detailsPanel)
        tabbedPane.addTab("历史数据", historyPanel)

        // 布局
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = EmptyBorder(0, 0, 10, 0)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val refreshButton = JButton("刷新所有数据")
        refreshButton.addActionListener { refreshAllData() }
        headerPanel.add(refreshButton, BorderLayout.EAST)

        mainPanel.add(headerPanel, BorderLayout.NORTH)
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
    }

    private fun createOverviewPanel(): JPanel {
        return EnhancedNekoamaToolWindow().getComponent() as JPanel
    }

    private fun createDetailsPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // 创建详细统计信息
        val detailsContent = createDetailedStatsContent()

        // 控制按钮
        val controlPanel = JPanel()
        val refreshButton = JButton("刷新详细统计")
        val exportButton = JButton("导出分析报告")

        refreshButton.addActionListener { refreshDetailedStats() }
        exportButton.addActionListener { exportAnalysisReport() }

        controlPanel.add(refreshButton)
        controlPanel.add(exportButton)

        panel.add(controlPanel, BorderLayout.NORTH)
        panel.add(JScrollPane(detailsContent), BorderLayout.CENTER)

        return panel
    }

    private fun createHistoryPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        val historyViewer = HistoryViewer()

        // 添加说明文字
        val infoPanel = JPanel()
        infoPanel.border = EmptyBorder(5, 5, 5, 5)
        infoPanel.add(JBLabel("查看历史使用数据，支持按日期范围、操作类型等条件筛选"))

        panel.add(infoPanel, BorderLayout.NORTH)
        panel.add(historyViewer.getComponent(), BorderLayout.CENTER)

        return panel
    }

    private fun createDetailedStatsContent(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // 获取详细的增强快照
        val snapshot = runBlocking { EnhancedMetricsCollector.getEnhancedSnapshot() }

        // 性能指标卡片
        panel.add(createMetricCard("性能指标", createPerformanceMetrics(snapshot)))

        // 使用模式分析
        panel.add(createMetricCard("使用模式分析", createUsagePatternAnalysis(snapshot)))

        // 错误分析
        panel.add(createMetricCard("错误分析", createErrorAnalysis(snapshot)))

        // 趋势数据
        panel.add(createMetricCard("使用趋势", createTrendAnalysis(snapshot)))

        return panel
    }

    private fun createMetricCard(title: String, content: JComponent): JComponent {
        val card = JPanel(BorderLayout())
        card.border = JBEmptyBorder(JBUI.insets(10, 10, 10, 10))
        card.background = UIUtil.getPanelBackground()

        val titleLabel = JBLabel(title)
        titleLabel.font = titleLabel.font.deriveFont(14f).deriveFont(JBFont.BOLD)

        val headerPanel = JPanel()
        headerPanel.add(titleLabel)
        headerPanel.border = EmptyBorder(0, 0, 5, 0)

        card.add(headerPanel, BorderLayout.NORTH)
        card.add(content, BorderLayout.CENTER)

        return card
    }

    private fun createPerformanceMetrics(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(createInfoRow("平均延迟", "${snapshot.averageLatencyMs}ms"))
        panel.add(createInfoRow("成功率", String.format("%.1f%%", snapshot.successRate * 100)))
        panel.add(createInfoRow("日均使用", String.format("%.1f", snapshot.avgRequestsPerDay)))
        panel.add(createInfoRow("高峰时段", "${snapshot.peakUsageHour}:00"))

        return panel
    }

    private fun createUsagePatternAnalysis(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(createInfoRow("最常用功能", formatActionType(snapshot.mostUsedAction)))

        // 今日分类统计
        val todayStats = snapshot.todayByType.entries.joinToString(", ") {
            "${formatActionType(it.key)}: ${it.value}"
        }
        panel.add(createInfoRow("今日分类", if (todayStats.isEmpty()) "无数据" else todayStats))

        // 本周分类统计
        val weekStats = snapshot.weeklyByType.entries.joinToString(", ") {
            "${formatActionType(it.key)}: ${it.value}"
        }
        panel.add(createInfoRow("本周分类", if (weekStats.isEmpty()) "无数据" else weekStats))

        return panel
    }

    private fun createErrorAnalysis(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val errorStats = snapshot.errorsToday.entries.joinToString(", ") {
            "${formatErrorType(it.key)}: ${it.value}"
        }

        if (errorStats.isEmpty()) {
            panel.add(createInfoRow("今日错误", "无错误"))
        } else {
            panel.add(createInfoRow("今日错误", errorStats))
        }

        val weekErrorStats = snapshot.errorsWeek.entries.joinToString(", ") {
            "${formatErrorType(it.key)}: ${it.value}"
        }

        if (weekErrorStats.isEmpty()) {
            panel.add(createInfoRow("本周错误", "无错误"))
        } else {
            panel.add(createInfoRow("本周错误", weekErrorStats))
        }

        return panel
    }

    private fun createTrendAnalysis(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // 显示最近7天的趋势
        panel.add(JBLabel("最近7天使用趋势:"))

        val trendPanel = JPanel()
        trendPanel.layout = BoxLayout(trendPanel, BoxLayout.X_AXIS)

        snapshot.dailyTrend.forEach { trend ->
            val label = JBLabel("${trend.date.substring(5)}: ${trend.requests}", SwingConstants.CENTER)
            label.border = EmptyBorder(0, 2, 0, 2)
            trendPanel.add(label)
        }

        panel.add(trendPanel)

        return panel
    }

    private fun createInfoRow(label: String, value: String): JComponent {
        val row = JPanel(BorderLayout())
        row.border = EmptyBorder(2, 0, 2, 0)

        val labelComponent = JBLabel("$label:")
        labelComponent.preferredSize = Dimension(100, 20)

        val valueComponent = JBLabel(value)

        row.add(labelComponent, BorderLayout.WEST)
        row.add(valueComponent, BorderLayout.CENTER)

        return row
    }

    private fun refreshDetailedStats() {
        scope.launch {
            // 重新构建详细统计面板
            detailsPanel.removeAll()
            detailsPanel.add(createDetailedStatsContent(), BorderLayout.CENTER)
            detailsPanel.revalidate()
            detailsPanel.repaint()
        }
    }

    private fun exportAnalysisReport() {
        scope.launch {
            try {
                val endDate = java.time.LocalDate.now()
                val startDate = endDate.minusMonths(1) // 导出最近一个月的数据

                val data = EnhancedMetricsCollector.exportData(startDate, endDate)
                if (data != null) {
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        "分析报告已导出到剪贴板",
                        "导出成功",
                        JOptionPane.INFORMATION_MESSAGE
                    )

                    // 复制到剪贴板
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val stringSelection = java.awt.datatransfer.StringSelection(data)
                    clipboard.setContents(stringSelection, null)
                } else {
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        "无数据可导出",
                        "导出失败",
                        JOptionPane.WARNING_MESSAGE
                    )
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "导出失败: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private fun refreshAllData() {
        scope.launch {
            // 刷新所有Tab的数据
            refreshOverviewData()
            refreshDetailedStats()
        }
    }

    private fun refreshOverviewData() {
        // 更新概览Tab
        overviewPanel.removeAll()
        overviewPanel.add(EnhancedNekoamaToolWindow().getComponent(), BorderLayout.CENTER)
        overviewPanel.revalidate()
        overviewPanel.repaint()
    }

    private fun registerMetricsListener() {
        EnhancedMetricsCollector.addListener(this)
    }

    override fun onMetricsUpdated(record: com.cw2.nekoama.core.metrics.ActionRecord) {
        // 当指标更新时，延迟一点时间再刷新，避免频繁更新
        scope.launch {
            delay(1000) // 延迟1秒
            SwingUtilities.invokeLater {
                refreshAllData()
            }
        }
    }

    private fun formatActionType(actionType: com.cw2.nekoama.core.metrics.ActionType): String {
        return when (actionType) {
            com.cw2.nekoama.core.metrics.ActionType.GENERATE_NAMING -> "命名生成"
            com.cw2.nekoama.core.metrics.ActionType.GENERATE_COMMENT -> "注释生成"
            com.cw2.nekoama.core.metrics.ActionType.CUSTOM_GENERATE -> "自定义生成"
            com.cw2.nekoama.core.metrics.ActionType.ANALYZE_UNUSED_CODE -> "代码分析"
        }
    }

    private fun formatErrorType(errorType: com.cw2.nekoama.core.metrics.ErrorType): String {
        return when (errorType) {
            com.cw2.nekoama.core.metrics.ErrorType.NETWORK_ERROR -> "网络错误"
            com.cw2.nekoama.core.metrics.ErrorType.API_ERROR -> "API错误"
            com.cw2.nekoama.core.metrics.ErrorType.TIMEOUT_ERROR -> "超时错误"
            com.cw2.nekoama.core.metrics.ErrorType.PARSING_ERROR -> "解析错误"
            com.cw2.nekoama.core.metrics.ErrorType.UNKNOWN_ERROR -> "未知错误"
        }
    }

    fun getComponent(): JComponent = mainPanel

    fun dispose() {
        scope.cancel()
        EnhancedMetricsCollector.removeListener(this)
    }
}