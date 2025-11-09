package com.cw2.nekoama.presentation.toolwindow.tab

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.core.metrics.ErrorType
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.ui.components.JBLabel
import com.intellij.ui.Gray
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Token统计Tab
 *
 * 整合现有的Token统计功能到新的Tab架构中，支持状态保持和独立刷新。
 */
class TokenStatsTab : BaseNekoamaTab() {

    override val tabId = "token_stats"
    override val displayName = NekoamaBundle.message("tab.token_stats.title")
    override val tooltip = NekoamaBundle.message("tab.token_stats.tooltip")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainPanel = JPanel(BorderLayout())
    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout)

    // 视图组件
    private val basicStatsPanel = createBasicStatsPanel()
    private val detailedStatsPanel = createDetailedStatsPanel()

    // Tab状态
    private var currentView = "basic" // basic | detailed
    private var lastRefreshTime = 0L
    private var autoRefreshEnabled = true

    init {
        setupUI()
        setupAutoRefresh()
        NekoamaLogger.debug("TokenStatsTab", "initialized")
    }

    /**
     * 设置自动刷新机制
     */
    private fun setupAutoRefresh() {
        scope.launch {
            while (autoRefreshEnabled) {
                delay(30000) // 每30秒自动刷新一次
                if (isActive) {
                    refreshTabContent()
                }
            }
        }
    }

    /**
     * 设置UI布局
     */
    private fun setupUI() {
        // 添加视图卡片
        contentPanel.add(basicStatsPanel, "basic")
        contentPanel.add(detailedStatsPanel, "detailed")

        // 创建切换按钮
        val toggleButton = JButton(NekoamaBundle.message("tokenstats.button.detailed"))
        toggleButton.addActionListener {
            toggleView()
            updateToggleButton(toggleButton)
        }

        // 创建刷新按钮
        val refreshButton = JButton(NekoamaBundle.message("tokenstats.button.refresh"))
        refreshButton.toolTipText = NekoamaBundle.message("tokenstats.button.refresh.tooltip")
        refreshButton.addActionListener {
            refreshTabContent()
        }

        // 创建导出按钮
        val exportButton = JButton(NekoamaBundle.message("tokenstats.button.export"))
        exportButton.toolTipText = NekoamaBundle.message("tokenstats.button.export.tooltip")
        exportButton.addActionListener {
            exportTokenData()
        }

        // 创建重置按钮
        val resetButton = JButton(NekoamaBundle.message("tokenstats.button.reset"))
        resetButton.toolTipText = NekoamaBundle.message("tokenstats.button.reset.tooltip")
        resetButton.addActionListener {
            resetTokenStats()
        }

        // 按钮面板
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.add(toggleButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(refreshButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(exportButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(resetButton)

        // 主布局
        mainPanel.add(buttonPanel, BorderLayout.NORTH)
        mainPanel.add(contentPanel, BorderLayout.CENTER)
    }

    /**
     * 创建基础统计面板
     */
    private fun createBasicStatsPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // 获取基础统计数据
        val snapshot = runBlocking { EnhancedMetricsCollector.getEnhancedSnapshot() }

        // Token使用统计
        panel.add(createMetricCard(NekoamaBundle.message("tokenstats.card.token.usage"), createTokenUsageStats(snapshot)))

        // 基础性能指标
        panel.add(createMetricCard(NekoamaBundle.message("tokenstats.card.performance"), createBasicPerformanceStats(snapshot)))

        return panel
    }

    /**
     * 创建详细统计面板
     */
    private fun createDetailedStatsPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // 获取详细统计数据
        val snapshot = runBlocking { EnhancedMetricsCollector.getEnhancedSnapshot() }

        // 详细性能指标
        panel.add(createMetricCard(NekoamaBundle.message("tokenstats.card.performance"), createDetailedPerformanceStats(snapshot)))

        // 使用模式分析
        panel.add(createMetricCard(NekoamaBundle.message("tokenstats.card.usage.pattern"), createUsagePatternAnalysis(snapshot)))

        // 错误分析
        panel.add(createMetricCard(NekoamaBundle.message("tokenstats.card.error.analysis"), createErrorAnalysis(snapshot)))

        // 使用趋势
        panel.add(createMetricCard(NekoamaBundle.message("tokenstats.card.usage.trend"), createTrendAnalysis(snapshot)))

        return panel
    }

    /**
     * 创建Token使用统计组件
     */
    private fun createTokenUsageStats(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // Token使用统计
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.today.tokens"), formatNumber(snapshot.tokensToday)))
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.week.tokens"), formatNumber(snapshot.tokensWeek)))
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.month.tokens"), formatNumber(snapshot.tokensMonth)))
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.total.tokens"), formatNumber(snapshot.tokensTotal)))

        // 添加分隔线
        panel.add(Box.createVerticalStrut(10))
        val separator1 = JSeparator()
        separator1.foreground = Gray._240
        panel.add(separator1)
        panel.add(Box.createVerticalStrut(10))

        // 请求统计
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.today.requests"), "${snapshot.today} " + NekoamaBundle.message("tokenstats.times")))
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.success.rate"), String.format("%.1f%%", snapshot.successRate * 100)))

        return panel
    }

    /**
     * 创建基础性能统计组件
     */
    private fun createBasicPerformanceStats(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.avg.latency"), "${snapshot.averageLatencyMs}ms"))
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.success.rate"), String.format("%.1f%%", snapshot.successRate * 100)))

        return panel
    }

    /**
     * 创建详细性能统计组件
     */
    private fun createDetailedPerformanceStats(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // 基础性能指标
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.avg.latency"), "${snapshot.averageLatencyMs}ms"))
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.success.rate"), String.format("%.1f%%", snapshot.successRate * 100)))
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.avg.usage"), String.format("%.1f " + NekoamaBundle.message("tokenstats.times"), snapshot.avgRequestsPerDay)))
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.peak.hour"), "${snapshot.peakUsageHour}:00"))

        // 添加分隔线
        panel.add(Box.createVerticalStrut(10))
        val separator = JSeparator()
        separator.foreground = Gray._240
        panel.add(separator)
        panel.add(Box.createVerticalStrut(10))

        // 效率指标
        val tokensPerRequest = if (snapshot.today > 0) snapshot.tokensToday.toDouble() / snapshot.today else 0.0
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.avg.tokens.per.request"), String.format("%.1f", tokensPerRequest)))

        
        return panel
    }

    /**
     * 创建使用模式分析组件
     */
    private fun createUsagePatternAnalysis(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.most.used"), formatActionType(snapshot.mostUsedAction)))

        // 今日分类统计
        val todayStats = snapshot.todayByType.entries.joinToString(", ") {
            "${formatActionType(it.key)}: ${it.value}"
        }
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.today.classification"), if (todayStats.isEmpty()) NekoamaBundle.message("tokenstats.no.data") else todayStats))

        // 本周分类统计
        val weekStats = snapshot.weeklyByType.entries.joinToString(", ") {
            "${formatActionType(it.key)}: ${it.value}"
        }
        panel.add(createInfoRow(NekoamaBundle.message("tokenstats.weekly.classification"), if (weekStats.isEmpty()) NekoamaBundle.message("tokenstats.no.data") else weekStats))

        return panel
    }

    /**
     * 创建错误分析组件
     */
    private fun createErrorAnalysis(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val errorStats = snapshot.errorsToday.entries.joinToString(", ") {
            "${formatErrorType(it.key)}: ${it.value}"
        }

        if (errorStats.isEmpty()) {
            panel.add(createInfoRow(NekoamaBundle.message("tokenstats.today.errors"), NekoamaBundle.message("tokenstats.no.errors")))
        } else {
            panel.add(createInfoRow(NekoamaBundle.message("tokenstats.today.errors"), errorStats))
        }

        val weekErrorStats = snapshot.errorsWeek.entries.joinToString(", ") {
            "${formatErrorType(it.key)}: ${it.value}"
        }

        if (weekErrorStats.isEmpty()) {
            panel.add(createInfoRow(NekoamaBundle.message("tokenstats.weekly.errors"), NekoamaBundle.message("tokenstats.no.errors")))
        } else {
            panel.add(createInfoRow(NekoamaBundle.message("tokenstats.weekly.errors"), weekErrorStats))
        }

        return panel
    }

    /**
     * 创建趋势分析组件
     */
    private fun createTrendAnalysis(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = createThemedCard()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // 显示最近7天的趋势
        val titleLabel = JBLabel(NekoamaBundle.message("tokenstats.recent.days.trend"))
        titleLabel.font = titleLabel.font.deriveFont(JBFont.BOLD)
        titleLabel.horizontalAlignment = SwingConstants.LEFT
        panel.add(titleLabel)

        // 使用流式布局避免挤压
        val trendPanel = JPanel()
        trendPanel.layout = FlowLayout(FlowLayout.LEFT, 8, 4)
        trendPanel.background = UIUtil.getPanelBackground()

        val dateFormatter = DateTimeFormatter.ofPattern("MM-dd")

        snapshot.dailyTrend.forEach { trend ->
            try {
                // 解析日期并格式化为更好的显示格式
                val localDate = LocalDate.parse(trend.date)
                val displayDate = localDate.format(dateFormatter)

                // 创建趋势项卡片
                val trendItem = JPanel()
                trendItem.layout = BorderLayout()
                trendItem.border = JBEmptyBorder(JBUI.insets(6, 10, 6, 10))
                trendItem.background = UIUtil.getPanelBackground().brighter()

                // 日期标签
                val dateLabel = JBLabel(displayDate, SwingConstants.CENTER)
                dateLabel.font = dateLabel.font.deriveFont(11f).deriveFont(JBFont.BOLD)

                // 请求数量标签
                val requestsLabel = JBLabel("${trend.requests} " + NekoamaBundle.message("tokenstats.trend.requests.short"), SwingConstants.CENTER)
                requestsLabel.font = requestsLabel.font.deriveFont(10f)
                requestsLabel.foreground = Gray._100

                trendItem.add(dateLabel, BorderLayout.NORTH)
                trendItem.add(requestsLabel, BorderLayout.SOUTH)

                trendPanel.add(trendItem)
            } catch (e: Exception) {
                // 如果日期解析失败，使用原格式
                NekoamaLogger.warn("TokenStatsTab", "Failed to parse date: ${trend.date}", error = e)
                val fallbackLabel = JBLabel("${trend.date.substring(5)}: ${trend.requests}", SwingConstants.CENTER)
                fallbackLabel.border = JBEmptyBorder(JBUI.insets(6, 10, 6, 10))
                trendPanel.add(fallbackLabel)
            }
        }

        panel.add(Box.createVerticalStrut(8))
        panel.add(trendPanel)

        return panel
    }

    /**
     * 创建指标卡片
     */
    private fun createMetricCard(title: String, content: JComponent): JComponent {
        val card = createThemedCard(10, 10, 10, 10)

        val titleLabel = JBLabel(title)
        titleLabel.font = titleLabel.font.deriveFont(14f).deriveFont(JBFont.BOLD)

        val headerPanel = JPanel()
        headerPanel.add(titleLabel)
        headerPanel.border = EmptyBorder(0, 0, 5, 0)

        card.add(headerPanel, BorderLayout.NORTH)
        card.add(content, BorderLayout.CENTER)

        return card
    }

    /**
     * 创建信息行
     */
    private fun createInfoRow(label: String, value: String): JComponent {
        val row = JPanel(BorderLayout())
        row.border = EmptyBorder(2, 0, 2, 0)

        val labelComponent = JBLabel("$label:")
        labelComponent.horizontalAlignment = SwingConstants.LEFT

        val valueComponent = JBLabel(value)
        valueComponent.horizontalAlignment = SwingConstants.RIGHT

        row.add(labelComponent, BorderLayout.WEST)
        row.add(valueComponent, BorderLayout.EAST)

        return row
    }

    /**
     * 切换视图
     */
    private fun toggleView() {
        currentView = if (currentView == "basic") "detailed" else "basic"
        cardLayout.show(contentPanel, currentView)
        NekoamaLogger.debug("TokenStatsTab", "view toggled to: $currentView")
    }

    /**
     * 更新切换按钮文本
     */
    private fun updateToggleButton(button: JButton) {
        button.text = if (currentView == "basic") NekoamaBundle.message("tokenstats.button.detailed") else NekoamaBundle.message("tokenstats.button.basic")
    }

    /**
     * 刷新Tab内容
     */
    private fun refreshTabContent() {
        scope.launch {
            try {
                // 重新创建内容面板
                contentPanel.removeAll()

                val newBasicStatsPanel = createBasicStatsPanel()
                val newDetailedStatsPanel = createDetailedStatsPanel()

                contentPanel.add(newBasicStatsPanel, "basic")
                contentPanel.add(newDetailedStatsPanel, "detailed")

                // 恢复当前视图
                cardLayout.show(contentPanel, currentView)

                contentPanel.revalidate()
                contentPanel.repaint()

                lastRefreshTime = System.currentTimeMillis()
                NekoamaLogger.debug("TokenStatsTab", "content refreshed")
            } catch (e: Exception) {
                NekoamaLogger.error("TokenStatsTab", "Failed to refresh TokenStatsTab content", error = e)
            }
        }
    }

    override fun refresh() {
        refreshTabContent()
    }

    override fun getTabState(): Map<String, Any> {
        return mapOf(
            "currentView" to currentView,
            "lastRefreshTime" to lastRefreshTime,
            "autoRefreshEnabled" to autoRefreshEnabled,
            "timestamp" to System.currentTimeMillis()
        )
    }

    override fun restoreTabState(state: Map<String, Any>) {
        try {
            currentView = state["currentView"] as? String ?: "basic"
            lastRefreshTime = (state["lastRefreshTime"] as? Long) ?: 0L
            autoRefreshEnabled = (state["autoRefreshEnabled"] as? Boolean) ?: true

            // 恢复视图显示
            cardLayout.show(contentPanel, currentView)

            // 如果距离上次刷新超过30秒，立即刷新
            val timeSinceLastRefresh = System.currentTimeMillis() - lastRefreshTime
            if (timeSinceLastRefresh > 30000) {
                refreshTabContent()
            }

            NekoamaLogger.debug("TokenStatsTab", "state restored: view=$currentView, autoRefresh=$autoRefreshEnabled")
        } catch (e: Exception) {
            NekoamaLogger.error("TokenStatsTab", "Failed to restore TokenStatsTab state", error = e)
        }
    }

    override fun dispose() {
        try {
            scope.cancel()
            NekoamaLogger.debug("TokenStatsTab", "disposed")
        } catch (e: Exception) {
            NekoamaLogger.error("TokenStatsTab", "Error disposing TokenStatsTab", error = e)
        }
    }

    override fun getComponent(): JComponent = mainPanel

    private fun formatActionType(actionType: ActionType): String {
        return when (actionType) {
            ActionType.GENERATE_NAMING -> NekoamaBundle.message("tokenstats.action.type.naming")
            ActionType.GENERATE_COMMENT -> NekoamaBundle.message("tokenstats.action.type.comment")
            ActionType.CUSTOM_GENERATE -> NekoamaBundle.message("tokenstats.action.type.custom")
            ActionType.ANALYZE_UNUSED_CODE -> NekoamaBundle.message("tokenstats.action.type.analyze")
        }
    }

    private fun formatErrorType(errorType: ErrorType): String {
        return when (errorType) {
            ErrorType.NETWORK_ERROR -> NekoamaBundle.message("tokenstats.error.type.network")
            ErrorType.API_ERROR -> NekoamaBundle.message("tokenstats.error.type.api")
            ErrorType.TIMEOUT_ERROR -> NekoamaBundle.message("tokenstats.error.type.timeout")
            ErrorType.PARSING_ERROR -> NekoamaBundle.message("tokenstats.error.type.parsing")
            ErrorType.UNKNOWN_ERROR -> NekoamaBundle.message("tokenstats.error.type.unknown")
        }
    }

    /**
     * 格式化数字显示
     */
    private fun formatNumber(number: Int): String {
        return when {
            number >= 1000000 -> String.format("%.1fM", number / 1000000.0)
            number >= 1000 -> String.format("%.1fK", number / 1000.0)
            else -> number.toString()
        }
    }

    /**
     * 导出Token数据
     */
    private fun exportTokenData() {
        scope.launch {
            try {
                val snapshot = runBlocking { EnhancedMetricsCollector.getEnhancedSnapshot() }

                val exportData = buildString {
                    appendLine("=== Nekoama Token Usage Statistics ===")
                    appendLine("Export Time: ${java.time.LocalDateTime.now()}")
                    appendLine()
                    appendLine("=== Token Usage ===")
                    appendLine("Today's Tokens: ${snapshot.tokensToday}")
                    appendLine("This Week's Tokens: ${snapshot.tokensWeek}")
                    appendLine("This Month's Tokens: ${snapshot.tokensMonth}")
                    appendLine("Total Tokens: ${snapshot.tokensTotal}")
                    appendLine()
                    appendLine("=== Request Statistics ===")
                    appendLine("Today's Requests: ${snapshot.today}")
                    appendLine("Success Rate: ${String.format("%.2f%%", snapshot.successRate * 100)}")
                    appendLine("Average Latency: ${snapshot.averageLatencyMs}ms")
                    appendLine()
                    appendLine("=== Usage Pattern Analysis ===")
                    appendLine("Most Used Feature: ${formatActionType(snapshot.mostUsedAction)}")
                    appendLine("Daily Average Usage: ${String.format("%.1f", snapshot.avgRequestsPerDay)}")
                    appendLine("Peak Hour: ${snapshot.peakUsageHour}:00")
                }

                // 复制到剪贴板
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                val stringSelection = java.awt.datatransfer.StringSelection(exportData)
                clipboard.setContents(stringSelection, null)

                JOptionPane.showMessageDialog(
                    mainPanel,
                    NekoamaBundle.message("tokenstats.export.success.clipboard"),
                    NekoamaBundle.message("tokenstats.export.success.title"),
                    JOptionPane.INFORMATION_MESSAGE
                )

                NekoamaLogger.debug("TokenStatsTab", "Token data exported successfully")

            } catch (e: Exception) {
                NekoamaLogger.error("TokenStatsTab", "Failed to export token data", error = e)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    NekoamaBundle.message("tokenstats.export.failed", e.message ?: ""),
                    NekoamaBundle.message("tokenstats.dialog.error"),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    /**
     * 重置Token统计
     */
    private fun resetTokenStats() {
        scope.launch {
            val result = JOptionPane.showConfirmDialog(
                mainPanel,
                NekoamaBundle.message("tokenstats.reset.confirm"),
                NekoamaBundle.message("tokenstats.reset.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )

            if (result == JOptionPane.YES_OPTION) {
                try {
                    // 这里可以调用重置统计的方法
                    // 目前先显示提示信息
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        NekoamaBundle.message("tokenstats.reset.development"),
                        NekoamaBundle.message("tokenstats.dialog.info"),
                        JOptionPane.INFORMATION_MESSAGE
                    )

                    NekoamaLogger.debug("TokenStatsTab", "User requested to reset token stats")

                } catch (e: Exception) {
                    NekoamaLogger.error("TokenStatsTab", "Failed to reset token stats", error = e)
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        NekoamaBundle.message("tokenstats.reset.failed", e.message ?: ""),
                        NekoamaBundle.message("tokenstats.dialog.error"),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
    }
}