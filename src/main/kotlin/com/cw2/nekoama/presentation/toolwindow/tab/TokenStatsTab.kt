package com.cw2.nekoama.presentation.toolwindow.tab

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.core.metrics.ErrorType
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.ui.components.JBLabel
import com.intellij.ui.Gray
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
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
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Token统计Tab
 *
 * 整合现有的Token统计功能到新的Tab架构中，支持状态保持和独立刷新。
 */
class TokenStatsTab : BaseNekoamaTab() {

    override val tabId = "token_stats"
    override val displayName = "Token统计"
    override val tooltip = "查看Token使用情况和详细统计"

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

    init {
        setupUI()
        NekoamaLogger.debug("TokenStatsTab", "initialized")
    }

    /**
     * 设置UI布局
     */
    private fun setupUI() {
        // 添加视图卡片
        contentPanel.add(basicStatsPanel, "basic")
        contentPanel.add(detailedStatsPanel, "detailed")

        // 创建切换按钮
        val toggleButton = JButton("详细统计")
        toggleButton.addActionListener {
            toggleView()
            updateToggleButton(toggleButton)
        }

        // 创建刷新按钮
        val refreshButton = JButton("刷新")
        refreshButton.addActionListener {
            refreshTabContent()
        }

        // 按钮面板
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.add(toggleButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(refreshButton)

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
        panel.add(createMetricCard("Token使用统计", createTokenUsageStats(snapshot)))

        // 基础性能指标
        panel.add(createMetricCard("性能指标", createBasicPerformanceStats(snapshot)))

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
        panel.add(createMetricCard("性能指标", createDetailedPerformanceStats(snapshot)))

        // 使用模式分析
        panel.add(createMetricCard("使用模式分析", createUsagePatternAnalysis(snapshot)))

        // 错误分析
        panel.add(createMetricCard("错误分析", createErrorAnalysis(snapshot)))

        // 使用趋势
        panel.add(createMetricCard("使用趋势", createTrendAnalysis(snapshot)))

        return panel
    }

    /**
     * 创建Token使用统计组件
     */
    private fun createTokenUsageStats(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(createInfoRow("今日Token", "${snapshot.tokensToday}"))
        panel.add(createInfoRow("本周Token", "${snapshot.tokensWeek}"))
        panel.add(createInfoRow("本月Token", "${snapshot.tokensMonth}"))
        panel.add(createInfoRow("累计Token", "${snapshot.tokensTotal}"))

        return panel
    }

    /**
     * 创建基础性能统计组件
     */
    private fun createBasicPerformanceStats(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(createInfoRow("平均延迟", "${snapshot.averageLatencyMs}ms"))
        panel.add(createInfoRow("成功率", String.format("%.1f%%", snapshot.successRate * 100)))

        return panel
    }

    /**
     * 创建详细性能统计组件
     */
    private fun createDetailedPerformanceStats(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(createInfoRow("平均延迟", "${snapshot.averageLatencyMs}ms"))
        panel.add(createInfoRow("成功率", String.format("%.1f%%", snapshot.successRate * 100)))
        panel.add(createInfoRow("日均使用", String.format("%.1f", snapshot.avgRequestsPerDay)))
        panel.add(createInfoRow("高峰时段", "${snapshot.peakUsageHour}:00"))

        return panel
    }

    /**
     * 创建使用模式分析组件
     */
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

    /**
     * 创建趋势分析组件
     */
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

    /**
     * 创建指标卡片
     */
    private fun createMetricCard(title: String, content: JComponent): JComponent {
        val card = JPanel(BorderLayout())
        card.border = EmptyBorder(10, 10, 10, 10)
        card.background = if (card.background != null) Gray._245 else null

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
        labelComponent.preferredSize = Dimension(100, 20)

        val valueComponent = JBLabel(value)

        row.add(labelComponent, BorderLayout.WEST)
        row.add(valueComponent, BorderLayout.CENTER)

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
        button.text = if (currentView == "basic") "详细统计" else "基础统计"
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
            "lastRefreshTime" to lastRefreshTime
        )
    }

    override fun restoreTabState(state: Map<String, Any>) {
        try {
            currentView = state["currentView"] as? String ?: "basic"
            lastRefreshTime = (state["lastRefreshTime"] as? Long) ?: 0L

            // 恢复视图显示
            cardLayout.show(contentPanel, currentView)

            NekoamaLogger.debug("TokenStatsTab", "state restored: view=$currentView")
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
            ActionType.GENERATE_NAMING -> "命名生成"
            ActionType.GENERATE_COMMENT -> "注释生成"
            ActionType.CUSTOM_GENERATE -> "自定义生成"
            ActionType.ANALYZE_UNUSED_CODE -> "代码分析"
        }
    }

    private fun formatErrorType(errorType: ErrorType): String {
        return when (errorType) {
            ErrorType.NETWORK_ERROR -> "网络错误"
            ErrorType.API_ERROR -> "API错误"
            ErrorType.TIMEOUT_ERROR -> "超时错误"
            ErrorType.PARSING_ERROR -> "解析错误"
            ErrorType.UNKNOWN_ERROR -> "未知错误"
        }
    }
}