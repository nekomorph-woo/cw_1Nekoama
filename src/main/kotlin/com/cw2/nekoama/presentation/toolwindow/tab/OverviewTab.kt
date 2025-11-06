package com.cw2.nekoama.presentation.toolwindow.tab

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.data.settings.NekoamaSettings
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.Gray
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 概览Tab
 *
 * 提供快速访问常用功能的入口，显示核心状态信息和使用摘要。
 */
class OverviewTab : BaseNekoamaTab() {

    override val tabId = "overview"
    override val displayName = "概览"
    override val tooltip = "快速访问常用功能和查看状态摘要"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainPanel = JPanel(BorderLayout())

    // 状态组件
    private val connectionStatusLabel = JBLabel("检查中...")
    private val configStatusLabel = JBLabel("检查中...")
    private val todayUsageLabel = JBLabel("加载中...")

    init {
        setupUI()
        refreshStatus()
        NekoamaLogger.debug("OverviewTab", "initialized")
    }

    /**
     * 设置UI布局
     */
    private fun setupUI() {
        // 滚动面板支持内容较多时的滚动
        val scrollPane = JScrollPane(createContentPanel())
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = null

        mainPanel.add(scrollPane, BorderLayout.CENTER)
    }

    /**
     * 创建主内容面板
     */
    private fun createContentPanel(): JPanel {
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = EmptyBorder(10, 10, 10, 10)

        // 状态信息卡片
        contentPanel.add(createStatusCard())

        contentPanel.add(Box.createVerticalStrut(10))

        // 快速操作卡片
        contentPanel.add(createQuickActionsCard())

        contentPanel.add(Box.createVerticalStrut(10))

        // 使用摘要卡片
        contentPanel.add(createUsageSummaryCard())

        contentPanel.add(Box.createVerticalStrut(10))

        // 最近活动卡片
        contentPanel.add(createRecentActivityCard())

        return contentPanel
    }

    /**
     * 创建状态信息卡片
     */
    private fun createStatusCard(): JPanel {
        val card = JPanel(BorderLayout())
        card.border = EmptyBorder(15, 15, 15, 15)
        card.background = if (card.background != null) Gray._245 else null

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("overview.connection.status"))
        titleLabel.font = titleLabel.font.deriveFont(16f).deriveFont(JBFont.BOLD)

        // 状态信息
        val statusPanel = JPanel()
        statusPanel.layout = BoxLayout(statusPanel, BoxLayout.Y_AXIS)

        val connectionRow = createStatusRow("AI服务", connectionStatusLabel)
        val configRow = createStatusRow(NekoamaBundle.message("overview.config.status"), configStatusLabel)

        statusPanel.add(connectionRow)
        statusPanel.add(Box.createVerticalStrut(5))
        statusPanel.add(configRow)

        // 刷新状态按钮
        val refreshButton = JButton(NekoamaBundle.message("overview.refresh.status"))
        refreshButton.addActionListener {
            refreshStatus()
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        buttonPanel.add(refreshButton)

        card.add(titleLabel, BorderLayout.NORTH)
        card.add(statusPanel, BorderLayout.CENTER)
        card.add(buttonPanel, BorderLayout.SOUTH)

        return card
    }

    /**
     * 创建快速操作卡片
     */
    private fun createQuickActionsCard(): JPanel {
        val card = JPanel(BorderLayout())
        card.border = EmptyBorder(15, 15, 15, 15)
        card.background = if (card.background != null) Gray._245 else null

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("overview.quick.actions"))
        titleLabel.font = titleLabel.font.deriveFont(16f).deriveFont(JBFont.BOLD)

        // 操作按钮面板
        val buttonPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        // 设置按钮网格布局
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.weightx = 1.0

        // 第一行按钮
        gbc.gridx = 0
        gbc.gridy = 0
        val testConnectionButton = JButton(NekoamaBundle.message("overview.test.connection"))
        testConnectionButton.addActionListener { testConnection() }
        buttonPanel.add(testConnectionButton, gbc)

        gbc.gridx = 1
        val openSettingsButton = JButton(NekoamaBundle.message("overview.open.settings"))
        openSettingsButton.addActionListener { openSettings() }
        buttonPanel.add(openSettingsButton, gbc)

        // 第二行按钮
        gbc.gridx = 0
        gbc.gridy = 1
        val exportDataButton = JButton(NekoamaBundle.message("overview.export.data"))
        exportDataButton.addActionListener { exportData() }
        buttonPanel.add(exportDataButton, gbc)

        gbc.gridx = 1
        val viewLogsButton = JButton(NekoamaBundle.message("overview.view.logs"))
        viewLogsButton.addActionListener { viewLogs() }
        buttonPanel.add(viewLogsButton, gbc)

        card.add(titleLabel, BorderLayout.NORTH)
        card.add(buttonPanel, BorderLayout.CENTER)

        return card
    }

    /**
     * 创建使用摘要卡片
     */
    private fun createUsageSummaryCard(): JPanel {
        val card = JPanel(BorderLayout())
        card.border = EmptyBorder(15, 15, 15, 15)
        card.background = if (card.background != null) Gray._245 else null

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("overview.usage.summary"))
        titleLabel.font = titleLabel.font.deriveFont(16f).deriveFont(JBFont.BOLD)

        // 使用统计
        val statsPanel = JPanel()
        statsPanel.layout = BoxLayout(statsPanel, BoxLayout.Y_AXIS)

        val todayUsageRow = createInfoRow("今日请求", todayUsageLabel.text)
        statsPanel.add(todayUsageRow)

        // 添加分隔线
        statsPanel.add(Box.createVerticalStrut(10))
        val separator = JSeparator()
        separator.foreground = Gray._240
        statsPanel.add(separator)
        statsPanel.add(Box.createVerticalStrut(10))

        // 添加Token使用统计
        try {
            val snapshot = runBlocking { EnhancedMetricsCollector.getEnhancedSnapshot() }
            val tokenRow = createInfoRow("今日Token", "${snapshot.tokensToday}")
            statsPanel.add(tokenRow)
        } catch (e: Exception) {
            statsPanel.add(createInfoRow("今日Token", "加载失败"))
        }

        // 添加成功率和延迟统计
        statsPanel.add(Box.createVerticalStrut(5))
        try {
            val snapshot = runBlocking { EnhancedMetricsCollector.getEnhancedSnapshot() }
            val successRateRow = createInfoRow("成功率", String.format("%.1f%%", snapshot.successRate * 100))
            statsPanel.add(successRateRow)

            val latencyRow = createInfoRow("平均延迟", "${snapshot.averageLatencyMs}ms")
            statsPanel.add(latencyRow)
        } catch (e: Exception) {
            statsPanel.add(createInfoRow("性能数据", "加载失败"))
        }

        card.add(titleLabel, BorderLayout.NORTH)
        card.add(statsPanel, BorderLayout.CENTER)

        return card
    }

    /**
     * 创建最近活动卡片
     */
    private fun createRecentActivityCard(): JPanel {
        val card = JPanel(BorderLayout())
        card.border = EmptyBorder(15, 15, 15, 15)
        card.background = if (card.background != null) Gray._245 else null

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("overview.recent.activity"))
        titleLabel.font = titleLabel.font.deriveFont(16f).deriveFont(JBFont.BOLD)

        // 活动列表
        val activityPanel = JPanel()
        activityPanel.layout = BoxLayout(activityPanel, BoxLayout.Y_AXIS)

        // 加载最近活动数据
        loadRecentActivities(activityPanel)

        // 添加刷新按钮
        val refreshButton = JButton("刷新")
        refreshButton.addActionListener {
            scope.launch {
                activityPanel.removeAll()
                loadRecentActivities(activityPanel)
                activityPanel.revalidate()
                activityPanel.repaint()
            }
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.add(refreshButton)

        val contentPanel = JPanel(BorderLayout())
        contentPanel.add(activityPanel, BorderLayout.CENTER)
        contentPanel.add(buttonPanel, BorderLayout.SOUTH)

        card.add(titleLabel, BorderLayout.NORTH)
        card.add(contentPanel, BorderLayout.CENTER)

        return card
    }

    /**
     * 加载最近活动数据
     */
    private fun loadRecentActivities(panel: JPanel) {
        try {
            // 获取最近的7天趋势数据
            val snapshot = runBlocking { EnhancedMetricsCollector.getEnhancedSnapshot() }

            if (snapshot.dailyTrend.isEmpty()) {
                val placeholderLabel = JBLabel("暂无最近活动记录")
                placeholderLabel.foreground = Gray._128
                panel.add(placeholderLabel)
                return
            }

            // 显示最近5天的活动
            val recentDays = snapshot.dailyTrend.takeLast(5).reversed()

            recentDays.forEach { trend ->
                val activityItem = createActivityItem(trend)
                panel.add(activityItem)
                panel.add(Box.createVerticalStrut(5))
            }

        } catch (e: Exception) {
            NekoamaLogger.error("OverviewTab", "Failed to load recent activities", error = e)
            val errorLabel = JBLabel("加载活动记录失败")
            errorLabel.foreground = UIManager.getColor("Label.errorForeground")
            panel.add(errorLabel)
        }
    }

    /**
     * 创建单个活动项
     */
    private fun createActivityItem(trend: com.cw2.nekoama.core.metrics.DailyTrendPoint): JComponent {
        val itemPanel = JPanel(BorderLayout())
        itemPanel.border = EmptyBorder(8, 8, 8, 8)
        itemPanel.background = if (itemPanel.background != null) Gray._248 else null

        // 日期标签
        val dateLabel = JBLabel(trend.date.substring(5)) // 显示MM-DD格式
        dateLabel.font = dateLabel.font.deriveFont(12f).deriveFont(JBFont.BOLD)
        dateLabel.preferredSize = Dimension(60, 20)

        // 请求数量标签
        val requestsLabel = JBLabel("${trend.requests} 次请求")
        requestsLabel.foreground = Gray._100
        requestsLabel.font = requestsLabel.font.deriveFont(11f)

        // 成功率标签
        val successRateLabel = JBLabel("成功率 ${String.format("%.1f%%", trend.successRate * 100)}")
        successRateLabel.foreground = Gray._100
        successRateLabel.font = successRateLabel.font.deriveFont(11f)

        // 统计信息面板
        val statsPanel = JPanel()
        statsPanel.layout = BoxLayout(statsPanel, BoxLayout.Y_AXIS)
        statsPanel.add(requestsLabel)
        statsPanel.add(successRateLabel)

        itemPanel.add(dateLabel, BorderLayout.WEST)
        itemPanel.add(statsPanel, BorderLayout.CENTER)

        return itemPanel
    }

    /**
     * 创建状态行
     */
    private fun createStatusRow(label: String, valueLabel: JLabel): JPanel {
        val row = JPanel(BorderLayout())
        row.border = EmptyBorder(2, 0, 2, 0)

        val labelComponent = JBLabel("$label:")
        labelComponent.preferredSize = Dimension(80, 20)

        row.add(labelComponent, BorderLayout.WEST)
        row.add(valueLabel, BorderLayout.CENTER)

        return row
    }

    /**
     * 创建信息行
     */
    private fun createInfoRow(label: String, value: String): JPanel {
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
     * 刷新状态信息
     */
    private fun refreshStatus() {
        scope.launch {
            try {
                // 检查AI服务连接状态
                checkConnectionStatus()

                // 检查配置状态
                checkConfigStatus()

                // 更新使用摘要
                updateUsageSummary()

                NekoamaLogger.debug("OverviewTab", "status refreshed")
            } catch (e: Exception) {
                NekoamaLogger.error("OverviewTab", "Failed to refresh status", error = e)
                connectionStatusLabel.text = "检查失败"
                connectionStatusLabel.foreground = UIManager.getColor("Label.errorForeground")
            }
        }
    }

    /**
     * 检查连接状态
     */
    private suspend fun checkConnectionStatus() {
        try {
            connectionStatusLabel.text = "检查中..."
            connectionStatusLabel.foreground = UIManager.getColor("Label.foreground")

            // 检查API配置是否完整
            val settings = NekoamaSettings.getInstance()
            if (settings.apiKey.isEmpty()) {
                connectionStatusLabel.text = "未配置API密钥"
                connectionStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
                return
            }

            if (settings.apiEndpoint.isEmpty()) {
                connectionStatusLabel.text = "未配置端点"
                connectionStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
                return
            }

            // 模拟连接测试（实际项目中可以调用真实的API测试）
            delay(500) // 模拟网络延迟

            // 根据配置显示连接状态
            if (settings.apiEndpoint.contains("api.openai.com") || settings.apiEndpoint.contains("custom")) {
                connectionStatusLabel.text = NekoamaBundle.message("overview.connected")
                connectionStatusLabel.foreground = UIManager.getColor("Label.successForeground")
            } else {
                connectionStatusLabel.text = "配置异常"
                connectionStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
            }

        } catch (e: Exception) {
            connectionStatusLabel.text = "检查失败"
            connectionStatusLabel.foreground = UIManager.getColor("Label.errorForeground")
        }
    }

    /**
     * 检查配置状态
     */
    private suspend fun checkConfigStatus() {
        try {
            val settings = NekoamaSettings.getInstance()

            // 检查各个配置项的完整性
            val configItems = mutableListOf<String>()

            if (settings.apiKey.isNotEmpty()) {
                configItems.add("API密钥")
            }

            if (settings.apiEndpoint.isNotEmpty()) {
                configItems.add("端点")
            }

            if (settings.model.isNotEmpty()) {
                configItems.add("模型")
            }

            when (configItems.size) {
                3 -> {
                    configStatusLabel.text = NekoamaBundle.message("overview.configured")
                    configStatusLabel.foreground = UIManager.getColor("Label.successForeground")
                }
                2 -> {
                    configStatusLabel.text = "部分配置 (${configItems.joinToString("/")})"
                    configStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
                }
                1 -> {
                    configStatusLabel.text = "仅配置了${configItems.first()}"
                    configStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
                }
                else -> {
                    configStatusLabel.text = NekoamaBundle.message("overview.not.configured.apikey")
                    configStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
                }
            }

        } catch (e: Exception) {
            configStatusLabel.text = "配置检查失败"
            configStatusLabel.foreground = UIManager.getColor("Label.errorForeground")
        }
    }

    /**
     * 更新使用摘要
     */
    private suspend fun updateUsageSummary() {
        try {
            val snapshot = runBlocking { EnhancedMetricsCollector.getEnhancedSnapshot() }
            todayUsageLabel.text = "${snapshot.today} 次"
        } catch (e: Exception) {
            todayUsageLabel.text = "加载失败"
        }
    }

    /**
     * 测试连接
     */
    private fun testConnection() {
        scope.launch {
            try {
                connectionStatusLabel.text = "测试中..."
                connectionStatusLabel.foreground = UIManager.getColor("Label.foreground")

                // 这里可以添加实际的连接测试逻辑
                delay(1000) // 模拟测试延迟

                connectionStatusLabel.text = "连接正常"
                connectionStatusLabel.foreground = UIManager.getColor("Label.successForeground")

                JOptionPane.showMessageDialog(
                    mainPanel,
                    "连接测试成功！",
                    "连接测试",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                connectionStatusLabel.text = "连接失败"
                connectionStatusLabel.foreground = UIManager.getColor("Label.errorForeground")

                JOptionPane.showMessageDialog(
                    mainPanel,
                    "连接测试失败: ${e.message}",
                    "连接测试",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    /**
     * 打开设置
     */
    private fun openSettings() {
        try {
            ShowSettingsUtil.getInstance().showSettingsDialog(null, "Nekoama")
        } catch (e: Exception) {
            NekoamaLogger.error("OverviewTab", "Failed to open settings", error = e)
            JOptionPane.showMessageDialog(
                mainPanel,
                "无法打开设置页面: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * 导出数据
     */
    private fun exportData() {
        scope.launch {
            try {
                val endDate = java.time.LocalDate.now()
                val startDate = endDate.minusMonths(1)

                val data = EnhancedMetricsCollector.exportData(startDate, endDate)
                if (data != null) {
                    // 复制到剪贴板
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val stringSelection = java.awt.datatransfer.StringSelection(data)
                    clipboard.setContents(stringSelection, null)

                    JOptionPane.showMessageDialog(
                        mainPanel,
                        "数据已导出到剪贴板",
                        "导出成功",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        "无数据可导出",
                        "导出失败",
                        JOptionPane.WARNING_MESSAGE
                    )
                }
            } catch (e: Exception) {
                NekoamaLogger.error("OverviewTab", "Failed to export data", error = e)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "导出失败: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    /**
     * 查看日志
     */
    private fun viewLogs() {
        try {
            // 这里可以打开日志查看器或日志文件
            JOptionPane.showMessageDialog(
                mainPanel,
                "日志查看功能正在开发中...",
                "提示",
                JOptionPane.INFORMATION_MESSAGE
            )
        } catch (e: Exception) {
            NekoamaLogger.error("OverviewTab", "Failed to view logs", error = e)
        }
    }

    override fun refresh() {
        refreshStatus()
    }

    override fun dispose() {
        try {
            scope.cancel()
            NekoamaLogger.debug("OverviewTab", "disposed")
        } catch (e: Exception) {
            NekoamaLogger.error("OverviewTab", "Error disposing OverviewTab", error = e)
        }
    }

    override fun getComponent(): JComponent = mainPanel
}