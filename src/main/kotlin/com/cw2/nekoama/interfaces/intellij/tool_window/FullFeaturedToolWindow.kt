package com.cw2.nekoama.interfaces.intellij.tool_window

import com.cw2.nekoama.application.metrics.service.MetricsCollector
import com.cw2.nekoama.application.metrics.service.MetricsUpdateListener
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.domain.metrics.model.ActionRecord
import com.cw2.nekoama.domain.metrics.model.ActionType
import com.cw2.nekoama.domain.metrics.model.EnhancedMetricsSnapshot
import com.cw2.nekoama.domain.metrics.model.ErrorType
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
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
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.LocalDate
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
        tabbedPane.addTab(NekoamaBundle.message("fullfeatured.tab.overview"), overviewPanel)
        tabbedPane.addTab(NekoamaBundle.message("fullfeatured.tab.details"), detailsPanel)
        tabbedPane.addTab(NekoamaBundle.message("fullfeatured.tab.history"), historyPanel)

        // 布局
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = EmptyBorder(0, 0, 10, 0)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val refreshButton = JButton(NekoamaBundle.message("fullfeatured.button.refresh.all"))
        refreshButton.addActionListener { refreshAllData() }
        headerPanel.add(refreshButton, BorderLayout.EAST)

        mainPanel.add(headerPanel, BorderLayout.NORTH)
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
    }

    private fun createOverviewPanel(): JPanel {
        return NekoamaToolWindow().getComponent() as JPanel
    }

    private fun createDetailsPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // 创建详细统计信息
        val detailsContent = createDetailedStatsContent()

        // 控制按钮
        val controlPanel = JPanel()
        val refreshButton = JButton(NekoamaBundle.message("fullfeatured.button.refresh.details"))
        val exportButton = JButton(NekoamaBundle.message("fullfeatured.button.export.report"))

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
        infoPanel.add(JBLabel(NekoamaBundle.message("fullfeatured.info.history.description")))

        panel.add(infoPanel, BorderLayout.NORTH)
        panel.add(historyViewer.getComponent(), BorderLayout.CENTER)

        return panel
    }

    private fun createDetailedStatsContent(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // 获取详细的增强快照
        val snapshot = runBlocking { MetricsCollector.getEnhancedSnapshot() }

        // 性能指标卡片
        panel.add(createMetricCard(NekoamaBundle.message("fullfeatured.card.performance"), createPerformanceMetrics(snapshot)))

        // 使用模式分析
        panel.add(createMetricCard(NekoamaBundle.message("fullfeatured.card.usage.pattern"), createUsagePatternAnalysis(snapshot)))

        // 错误分析
        panel.add(createMetricCard(NekoamaBundle.message("fullfeatured.card.error.analysis"), createErrorAnalysis(snapshot)))

        // 趋势数据
        panel.add(createMetricCard(NekoamaBundle.message("fullfeatured.card.usage.trend"), createTrendAnalysis(snapshot)))

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

    private fun createPerformanceMetrics(snapshot: EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.avg.latency"), "${snapshot.averageLatencyMs}ms"))
        panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.success.rate"), String.format("%.1f%%", snapshot.successRate * 100)))
        panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.avg.usage"), String.format("%.1f", snapshot.avgRequestsPerDay)))
        panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.peak.hour"), "${snapshot.peakUsageHour}:00"))

        return panel
    }

    private fun createUsagePatternAnalysis(snapshot: EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.most.used"), formatActionType(snapshot.mostUsedAction)))

        // 今日分类统计
        val todayStats = snapshot.todayByType.entries.joinToString(", ") {
            "${formatActionType(it.key)}: ${it.value}"
        }
        panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.today.classification"), if (todayStats.isEmpty()) NekoamaBundle.message("fullfeatured.metric.no.data") else todayStats))

        // 本周分类统计
        val weekStats = snapshot.weeklyByType.entries.joinToString(", ") {
            "${formatActionType(it.key)}: ${it.value}"
        }
        panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.week.classification"), if (weekStats.isEmpty()) NekoamaBundle.message("fullfeatured.metric.no.data") else weekStats))

        return panel
    }

    private fun createErrorAnalysis(snapshot: EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val errorStats = snapshot.errorsToday.entries.joinToString(", ") {
            "${formatErrorType(it.key)}: ${it.value}"
        }

        if (errorStats.isEmpty()) {
            panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.today.errors"), NekoamaBundle.message("fullfeatured.metric.no.errors")))
        } else {
            panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.today.errors"), errorStats))
        }

        val weekErrorStats = snapshot.errorsWeek.entries.joinToString(", ") {
            "${formatErrorType(it.key)}: ${it.value}"
        }

        if (weekErrorStats.isEmpty()) {
            panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.week.errors"), NekoamaBundle.message("fullfeatured.metric.no.errors")))
        } else {
            panel.add(createInfoRow(NekoamaBundle.message("fullfeatured.metric.week.errors"), weekErrorStats))
        }

        return panel
    }

    private fun createTrendAnalysis(snapshot: EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // 显示最近7天的趋势
        panel.add(JBLabel(NekoamaBundle.message("fullfeatured.trend.title")))

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
                val endDate = LocalDate.now()
                val startDate = endDate.minusMonths(1) // 导出最近一个月的数据

                val data = MetricsCollector.exportData(startDate, endDate)
                if (data != null) {
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        NekoamaBundle.message("fullfeatured.export.success.clipboard"),
                        NekoamaBundle.message("fullfeatured.export.success.clipboard.title"),
                        JOptionPane.INFORMATION_MESSAGE
                    )

                    // 复制到剪贴板
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    val stringSelection = StringSelection(data)
                    clipboard.setContents(stringSelection, null)
                } else {
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        NekoamaBundle.message("fullfeatured.export.no.data.clipboard"),
                        NekoamaBundle.message("fullfeatured.export.failed.clipboard.title"),
                        JOptionPane.WARNING_MESSAGE
                    )
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    mainPanel,
                    NekoamaBundle.message("fullfeatured.export.failed.clipboard", e.message ?: ""),
                    NekoamaBundle.message("fullfeatured.export.failed.clipboard.title"),
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
        overviewPanel.add(NekoamaToolWindow().getComponent(), BorderLayout.CENTER)
        overviewPanel.revalidate()
        overviewPanel.repaint()
    }

    private fun registerMetricsListener() {
        MetricsCollector.addListener(this)
    }

    override fun onMetricsUpdated(record: ActionRecord) {
        // 当指标更新时，延迟一点时间再刷新，避免频繁更新
        scope.launch {
            delay(1000) // 延迟1秒
            SwingUtilities.invokeLater {
                refreshAllData()
            }
        }
    }

    private fun formatActionType(actionType: ActionType): String {
        return when (actionType) {
            ActionType.GENERATE_NAMING -> NekoamaBundle.message("action.type.naming")
            ActionType.GENERATE_COMMENT -> NekoamaBundle.message("action.type.comment")
            ActionType.CUSTOM_GENERATE -> NekoamaBundle.message("action.type.custom")
            ActionType.ANALYZE_UNUSED_CODE -> NekoamaBundle.message("action.type.analyze")
            ActionType.ANALYZE_CODE_DEPS -> NekoamaBundle.message("action.type.analyze")
        }
    }

    private fun formatErrorType(errorType: ErrorType): String {
        return when (errorType) {
            ErrorType.NETWORK_ERROR -> NekoamaBundle.message("error.type.network")
            ErrorType.API_ERROR -> "API Error"
            ErrorType.TIMEOUT_ERROR -> NekoamaBundle.message("error.type.timeout")
            ErrorType.PARSING_ERROR -> NekoamaBundle.message("error.type.parsing")
            ErrorType.UNKNOWN_ERROR -> NekoamaBundle.message("error.type.unknown")
        }
    }

    fun getComponent(): JComponent = mainPanel

    fun dispose() {
        scope.cancel()
        MetricsCollector.removeListener(this)
    }
}