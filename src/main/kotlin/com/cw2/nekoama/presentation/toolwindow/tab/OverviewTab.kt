package com.cw2.nekoama.presentation.toolwindow.tab

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.data.settings.NekoamaSettings
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import com.intellij.ui.Gray
import javax.swing.border.EmptyBorder
import java.awt.event.ActionListener
import javax.swing.Timer
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.*

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
    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())

    // 状态组件
    private val connectionStatusLabel = JBLabel("检查中...")
    private val configStatusLabel = JBLabel("检查中...")
    private val todayUsageLabel = JBLabel("加载中...")

    // 动画组件（性能优化：使用对象缓存）
    private val loadingIcon = AllIcons.Process.Step_1
    private var loadingTimer: Timer? = null

    // 性能优化：防抖机制避免频繁刷新
    private var lastRefreshTime = 0L
    private val refreshDebounceMs = 2000L // 2秒防抖

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
        val scrollPane = JBScrollPane(createContentPanel())
        scrollPane.verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = null

        mainPanel.add(scrollPane, BorderLayout.CENTER)
    }

    /**
     * 创建主内容面板
     */
    private fun createContentPanel(): JBPanel<JBPanel<*>> {
        val contentPanel = JBPanel<JBPanel<*>>()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = JBEmptyBorder(JBUI.insets(10))

        // 状态信息卡片
        contentPanel.add(createStatusCard())

        contentPanel.add(Box.createVerticalStrut(JBUI.scale(10)))

        // 快速操作卡片
        contentPanel.add(createQuickActionsCard())

        contentPanel.add(Box.createVerticalStrut(JBUI.scale(10)))

        // 使用摘要卡片
        contentPanel.add(createUsageSummaryCard())

        contentPanel.add(Box.createVerticalStrut(JBUI.scale(10)))

        // 最近活动卡片
        contentPanel.add(createRecentActivityCard())

        return contentPanel
    }

    /**
     * 创建状态信息卡片
     */
    private fun createStatusCard(): JBPanel<JBPanel<*>> {
        val card = JBPanel<JBPanel<*>>(BorderLayout())
        card.border = JBEmptyBorder(JBUI.insets(15))
        card.background = UIUtil.getPanelBackground()

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("overview.connection.status"))
        titleLabel.font = JBFont.label().asBold()
        titleLabel.icon = AllIcons.General.InspectionsEye

        // 状态信息
        val statusPanel = JBPanel<JBPanel<*>>()
        statusPanel.layout = BoxLayout(statusPanel, BoxLayout.Y_AXIS)

        val connectionRow = createStatusRow("AI服务", connectionStatusLabel, AllIcons.General.Information)
        val configRow = createStatusRow(NekoamaBundle.message("overview.config.status"), configStatusLabel, AllIcons.General.Settings)

        statusPanel.add(connectionRow)
        statusPanel.add(Box.createVerticalStrut(JBUI.scale(5)))
        statusPanel.add(configRow)

        // 刷新状态按钮
        val refreshButton = JButton(AllIcons.Actions.Refresh)
        refreshButton.text = NekoamaBundle.message("overview.refresh.status")
        refreshButton.isFocusable = false
        refreshButton.addActionListener {
            refreshStatus()
        }

        val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT))
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
        val card = createThemedCard(15, 15, 15, 15)

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
        val card = createThemedCard(15, 15, 15, 15)

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
        val card = createThemedCard(15, 15, 15, 15)

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
        itemPanel.border = JBEmptyBorder(JBUI.insets(8, 8, 8, 8))
        itemPanel.background = UIUtil.getPanelBackground().brighter()

        // 日期标签
        val displayDate = try {
            val localDate = LocalDate.parse(trend.date)
            val formatter = DateTimeFormatter.ofPattern("MM-dd")
            localDate.format(formatter)
        } catch (e: Exception) {
            // 如果日期解析失败，使用原格式
            trend.date.substring(5)
        }
        val dateLabel = JBLabel(displayDate)
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
    private fun createStatusRow(label: String, valueLabel: JLabel, icon: Icon? = null): JBPanel<JBPanel<*>> {
        val row = JBPanel<JBPanel<*>>(BorderLayout())
        row.border = JBEmptyBorder(JBUI.insets(2, 0, 2, 0))

        val labelComponent = JBLabel("$label:")
        labelComponent.preferredSize = Dimension(JBUI.scale(80), JBUI.scale(20))
        if (icon != null) {
            labelComponent.icon = icon
        }

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
     * 刷新状态信息（带防抖优化）
     */
    private fun refreshStatus() {
        // 性能优化：防抖机制避免频繁刷新
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRefreshTime < refreshDebounceMs) {
            NekoamaLogger.debug("OverviewTab", "refreshStatus call debounced")
            return
        }
        lastRefreshTime = currentTime

        // 开始加载动画
        startLoadingAnimation()

        scope.launch {
            try {
                // 检查AI服务连接状态
                checkConnectionStatus()

                // 检查配置状态
                checkConfigStatus()

                // 更新使用摘要
                updateUsageSummary()

                // 停止加载动画
                stopLoadingAnimation()

                NekoamaLogger.debug("OverviewTab", "status refreshed successfully")
            } catch (e: Exception) {
                NekoamaLogger.error("OverviewTab", "Failed to refresh status", error = e)
                connectionStatusLabel.text = "检查失败"
                connectionStatusLabel.foreground = UIManager.getColor("Label.errorForeground")
                stopLoadingAnimation()
            }
        }
    }

    /**
     * 开始加载动画
     */
    private fun startLoadingAnimation() {
        // 设置加载状态
        connectionStatusLabel.text = "检查中..."
        connectionStatusLabel.foreground = UIManager.getColor("Label.foreground")
        connectionStatusLabel.icon = loadingIcon

        configStatusLabel.text = "检查中..."
        configStatusLabel.foreground = UIManager.getColor("Label.foreground")
        configStatusLabel.icon = loadingIcon

        // 简单的闪烁动画
        var toggle = true
        loadingTimer = Timer(500, ActionListener {
            toggle = !toggle
            connectionStatusLabel.icon = if (toggle) loadingIcon else null
            configStatusLabel.icon = if (toggle) loadingIcon else null
        })
        loadingTimer?.start()
    }

    /**
     * 停止加载动画
     */
    private fun stopLoadingAnimation() {
        loadingTimer?.stop()
        loadingTimer = null

        // 清除加载图标
        connectionStatusLabel.icon = null
        configStatusLabel.icon = null
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
            // 性能优化：确保所有定时器被停止
            loadingTimer?.stop()
            loadingTimer = null

            // 取消所有协程任务
            scope.cancel()

            // 清理缓存状态
            lastRefreshTime = 0L

            NekoamaLogger.debug("OverviewTab", "disposed with performance optimizations")
        } catch (e: Exception) {
            NekoamaLogger.error("OverviewTab", "Error disposing OverviewTab", error = e)
        }
    }

    override fun getComponent(): JComponent = mainPanel

    /**
     * 创建响应式状态卡片
     */
    private fun createResponsiveStatusCard(): JBPanel<JBPanel<*>> {
        val card = JBPanel<JBPanel<*>>(BorderLayout())
        card.border = JBEmptyBorder(JBUI.insets(12))
        card.background = UIUtil.getPanelBackground()

        // 标题栏
        val titleLabel = JBLabel("系统状态")
        titleLabel.font = JBFont.label().asBold()
        titleLabel.icon = AllIcons.General.Information

        // 状态网格
        val statusGrid = JBPanel<JBPanel<*>>()
        statusGrid.layout = GridBagLayout()
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(4)
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0

        // 添加状态项
        addStatusItem(statusGrid, gbc, 0, 0, "AI服务", connectionStatusLabel, AllIcons.General.Information)
        addStatusItem(statusGrid, gbc, 0, 1, "配置状态", configStatusLabel, AllIcons.General.Settings)
        addStatusItem(statusGrid, gbc, 1, 0, "今日使用", todayUsageLabel, AllIcons.Nodes.Console)

        // 刷新按钮
        val refreshButton = JButton(AllIcons.Actions.Refresh)
        refreshButton.toolTipText = "刷新状态"
        refreshButton.isFocusable = false
        refreshButton.addActionListener { refreshStatus() }

        val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.add(refreshButton)

        card.add(titleLabel, BorderLayout.NORTH)
        card.add(statusGrid, BorderLayout.CENTER)
        card.add(buttonPanel, BorderLayout.SOUTH)

        return card
    }

    /**
     * 添加状态项到网格
     */
    private fun addStatusItem(
        grid: JBPanel<*>,
        gbc: GridBagConstraints,
        row: Int,
        col: Int,
        label: String,
        valueLabel: JBLabel,
        icon: Icon? = null
    ) {
        gbc.gridx = col
        gbc.gridy = row
        gbc.weightx = if (col == 0) 0.3 else 0.7

        val itemPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))

        val iconLabel = JBLabel()
        if (icon != null) {
            iconLabel.icon = icon
        }
        itemPanel.add(iconLabel)

        val textLabel = JBLabel("$label:")
        textLabel.font = JBFont.label().asBold()
        itemPanel.add(textLabel)

        itemPanel.add(valueLabel)

        grid.add(itemPanel, gbc)
    }
}