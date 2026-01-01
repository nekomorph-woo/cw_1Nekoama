package com.cw2.nekoama.interfaces.intellij.tool_window.tab

import com.cw2.nekoama.application.metrics.service.MetricsCollector
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.domain.settings.service.NekoamaSecureStorage
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.infrastructure.network.diagnostic.ProxyConnectionTester
import com.cw2.nekoama.domain.code_suggestion_gen.model.CodeSuggestionGenerator
import com.cw2.nekoama.infrastructure.code_suggestion_gen.client.openai.OpenAIGenerator
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config.CustomGeneratorConfig
import com.cw2.nekoama.domain.metrics.model.DailyTrendPoint
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
import javax.swing.BorderFactory
import java.awt.event.ActionListener
import javax.swing.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.FlowLayout
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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
    override val displayName = NekoamaBundle.message("tab.overview.title")
    override val tooltip = NekoamaBundle.message("overview.tab.tooltip")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())

    // 状态组件
    private val connectionStatusLabel = JBLabel(NekoamaBundle.message("overview.status.checking"))
    private val configStatusLabel = JBLabel(NekoamaBundle.message("overview.status.checking"))
    private val todayUsageLabel = JBLabel(NekoamaBundle.message("overview.status.loading"))

    // 使用摘要UI组件引用（修复静态文本问题）
    private lateinit var todayUsageValueLabel: JBLabel
    private lateinit var todayTokensValueLabel: JBLabel
    private lateinit var successRateValueLabel: JBLabel
    private lateinit var avgLatencyValueLabel: JBLabel

    // 动画组件（性能优化：使用对象缓存）
    private val loadingIcon = AllIcons.Process.Step_1
    private var loadingTimer: Timer? = null

    // 性能优化：防抖机制避免频繁刷新
    private var lastRefreshTime = 0L
    private val refreshDebounceMs = 2000L // 2秒防抖

    init {
        setupUI()
        // 🔧 修复：移除启动时的自动连接检查，避免HTTP 407错误
        scope.launch {
            refreshStatus(skipConnectionCheck = true) // 跳过连接检查
        }
        NekoamaLogger.debug("OverviewTab", "initialized (connection check deferred)")
    }

    /**
     * 设置UI布局
     */
    private fun setupUI() {
        // 创建占位内容面板，后续异步加载真实内容
        val placeholderPanel = JBPanel<JBPanel<*>>()
        placeholderPanel.layout = BoxLayout(placeholderPanel, BoxLayout.Y_AXIS)
        placeholderPanel.border = JBEmptyBorder(JBUI.insets(10))

        // 添加加载提示
        val loadingLabel = JBLabel(NekoamaBundle.message("overview.status.loading"))
        loadingLabel.alignmentX = Component.CENTER_ALIGNMENT
        placeholderPanel.add(Box.createVerticalStrut(50))
        placeholderPanel.add(loadingLabel)

        // 滚动面板支持内容较多时的滚动
        val scrollPane = JBScrollPane(placeholderPanel)
        scrollPane.verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = null

        mainPanel.add(scrollPane, BorderLayout.CENTER)

        // 异步创建真实内容
        scope.launch {
            try {
                val realContent = createContentPanel()
                // 在EDT线程更新UI
                withContext(Dispatchers.Main) {
                    scrollPane.setViewportView(realContent)
                }
                NekoamaLogger.debug("OverviewTab", "UI content loaded successfully")
            } catch (e: Exception) {
                NekoamaLogger.error("OverviewTab", "Failed to load UI content", error = e)
                // 显示错误信息
                withContext(Dispatchers.Main) {
                    val errorPanel = JBPanel<JBPanel<*>>()
                    errorPanel.layout = BoxLayout(errorPanel, BoxLayout.Y_AXIS)
                    errorPanel.border = JBEmptyBorder(JBUI.insets(20))

                    val errorLabel = JBLabel("Failed to load content: ${e.message}")
                    errorLabel.foreground = UIManager.getColor("Label.errorForeground")
                    errorLabel.alignmentX = Component.CENTER_ALIGNMENT

                    errorPanel.add(errorLabel)
                    scrollPane.setViewportView(errorPanel)
                }
            }
        }
    }

    /**
     * 创建主内容面板
     */
    private suspend fun createContentPanel(): JBPanel<JBPanel<*>> {
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

        val connectionRow = createStatusRow(NekoamaBundle.message("overview.status.ai.service"), connectionStatusLabel, AllIcons.General.Information)
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

        // 创建动态使用统计行
        val (todayUsageRow, todayUsageRef) = createInfoRowWithDynamicLabel(
            NekoamaBundle.message("overview.usage.today.requests"),
            NekoamaBundle.message("overview.status.loading")
        )
        todayUsageValueLabel = todayUsageRef
        statsPanel.add(todayUsageRow)

        // 添加分隔线
        statsPanel.add(Box.createVerticalStrut(10))
        val separator = JSeparator()
        separator.foreground = Gray._240
        statsPanel.add(separator)
        statsPanel.add(Box.createVerticalStrut(10))

        // 创建动态Token使用统计行
        val (tokenRow, tokenRef) = createInfoRowWithDynamicLabel(
            NekoamaBundle.message("overview.usage.today.tokens"),
            NekoamaBundle.message("overview.status.loading")
        )
        todayTokensValueLabel = tokenRef
        statsPanel.add(tokenRow)

        // 创建动态成功率和延迟统计行
        statsPanel.add(Box.createVerticalStrut(5))

        val (successRateRow, successRateRef) = createInfoRowWithDynamicLabel(
            NekoamaBundle.message("overview.usage.success.rate"),
            NekoamaBundle.message("overview.status.loading")
        )
        successRateValueLabel = successRateRef
        statsPanel.add(successRateRow)

        val (latencyRow, latencyRef) = createInfoRowWithDynamicLabel(
            NekoamaBundle.message("overview.usage.avg.latency"),
            NekoamaBundle.message("overview.status.loading")
        )
        avgLatencyValueLabel = latencyRef
        statsPanel.add(latencyRow)

        card.add(titleLabel, BorderLayout.NORTH)
        card.add(statsPanel, BorderLayout.CENTER)

        return card
    }

    /**
     * 创建最近活动卡片
     */
    private suspend fun createRecentActivityCard(): JPanel {
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
        val refreshButton = JButton(NekoamaBundle.message("overview.button.refresh"))
        refreshButton.addActionListener {
            NekoamaLogger.info("OverviewTab", "Recent activities refresh button clicked")
            val refreshStartTime = System.currentTimeMillis()

            scope.launch {
                try {
                    // 在后台加载数据
                    val activitiesLoaded = withContext(Dispatchers.IO) {
                        NekoamaLogger.debug("OverviewTab", "Starting recent activities data reload")
                        activityPanel.removeAll()
                        loadRecentActivities(activityPanel)
                        true
                    }

                    if (activitiesLoaded) {
                        NekoamaLogger.debug("OverviewTab", "Recent activities data loaded successfully")
                    }

                    // 在EDT线程更新UI
                    withContext(Dispatchers.Main) {
                        NekoamaLogger.debug("OverviewTab", "Updating recent activities UI")
                        activityPanel.revalidate()
                        activityPanel.repaint()
                    }

                    // 同时强制刷新所有状态信息，确保数据一致性
                    NekoamaLogger.debug("OverviewTab", "Triggering full status refresh from activities button")
                    refreshStatus(forceRefresh = true)

                    val refreshDuration = System.currentTimeMillis() - refreshStartTime
                    NekoamaLogger.info("OverviewTab", "Recent activities refresh completed", mapOf(
                        "duration" to "${refreshDuration}ms"
                    ))

                } catch (e: Exception) {
                    NekoamaLogger.error("OverviewTab", "Failed to refresh recent activities", error = e)

                    // 在EDT显示错误信息
                    withContext(Dispatchers.Main) {
                        val errorLabel = JBLabel(NekoamaBundle.message("overview.dataLoadFailed", e.message ?: ""))
                        errorLabel.foreground = UIManager.getColor("Label.errorForeground")
                        activityPanel.add(errorLabel)
                        activityPanel.revalidate()
                        activityPanel.repaint()
                    }
                }
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
    private suspend fun loadRecentActivities(panel: JPanel) {
        try {
            // 先强制同步数据，确保获取最新数据
            MetricsCollector.forceSync()
            NekoamaLogger.debug("OverviewTab", "Recent activities data sync completed")

            // 在后台线程获取最近的7天趋势数据
            val snapshot = withContext(Dispatchers.IO) {
                MetricsCollector.getEnhancedSnapshot()
            }
            NekoamaLogger.debug("OverviewTab", "Recent activities snapshot loaded", mapOf(
                "trendSize" to snapshot.dailyTrend.size,
                "todayRequests" to snapshot.today
            ))

            if (snapshot.dailyTrend.isEmpty()) {
                // 在EDT线程创建UI组件
                withContext(Dispatchers.Main) {
                    val placeholderLabel = JBLabel(NekoamaBundle.message("overview.activity.no.records"))
                    placeholderLabel.foreground = Gray._128
                    panel.add(placeholderLabel)
                }
                return
            }

            // 显示最近5天的活动
            val recentDays = snapshot.dailyTrend.takeLast(5).reversed()

            // 在EDT线程创建UI组件
            withContext(Dispatchers.Main) {
                recentDays.forEach { trend ->
                    val activityItem = createActivityItem(trend)
                    panel.add(activityItem)
                    panel.add(Box.createVerticalStrut(5))
                }
            }

        } catch (e: Exception) {
            NekoamaLogger.error("OverviewTab", "Failed to load recent activities", error = e)
            // 在EDT线程创建错误UI组件
            withContext(Dispatchers.Main) {
                val errorLabel = JBLabel(NekoamaBundle.message("overview.activity.load.failed"))
                errorLabel.foreground = UIManager.getColor("Label.errorForeground")
                panel.add(errorLabel)
            }
        }
    }

    /**
     * 创建单个活动项
     */
    private fun createActivityItem(trend: DailyTrendPoint): JComponent {
        val itemPanel = JPanel(BorderLayout())
        itemPanel.border = JBEmptyBorder(JBUI.insets(8, 8, 8, 8))
        // 使用主题感知的背景色，参考TokenStatsTab的createMetricCard方式
        itemPanel.background = UIUtil.getPanelBackground()

        // 添加微妙的边框以提供视觉分离，同时保持主题适配
        itemPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIUtil.getPanelBackground().darker(), 1),
            JBEmptyBorder(JBUI.insets(8, 8, 8, 8))
        )

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
        val requestsLabel = JBLabel("${trend.requests} " + NekoamaBundle.message("overview.activity.requests"))
        requestsLabel.foreground = Gray._100
        requestsLabel.font = requestsLabel.font.deriveFont(11f)

        // 成功率标签
        val successRateLabel = JBLabel(NekoamaBundle.message("overview.activity.success.rate", String.format("%.1f%%", trend.successRate * 100)))
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

        // 左侧标签面板
        val leftPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
        leftPanel.isOpaque = false

        val labelComponent = JBLabel("$label:")
        // 移除固定宽度限制，让文本完整显示
        if (icon != null) {
            labelComponent.icon = icon
        }

        leftPanel.add(labelComponent)

        // 右侧值标签 - 确保右对齐
        valueLabel.horizontalAlignment = SwingConstants.RIGHT

        row.add(leftPanel, BorderLayout.WEST)
        row.add(valueLabel, BorderLayout.EAST)

        return row
    }

    /**
     * 创建信息行
     */
    private fun createInfoRow(label: String, value: String): JPanel {
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
     * 创建动态信息行（返回可更新的标签引用）
     */
    private fun createInfoRowWithDynamicLabel(label: String, initialValue: String): Pair<JPanel, JBLabel> {
        val row = JPanel(BorderLayout())
        row.border = EmptyBorder(2, 0, 2, 0)

        val labelComponent = JBLabel("$label:")
        labelComponent.horizontalAlignment = SwingConstants.LEFT

        val valueComponent = JBLabel(initialValue)
        valueComponent.horizontalAlignment = SwingConstants.RIGHT

        row.add(labelComponent, BorderLayout.WEST)
        row.add(valueComponent, BorderLayout.EAST)

        return Pair(row, valueComponent)
    }

    /**
     * 刷新状态信息（带防抖优化）
     */
    private fun refreshStatus(forceRefresh: Boolean = false, skipConnectionCheck: Boolean = false) {
        // 性能优化：防抖机制避免频繁刷新
        val currentTime = System.currentTimeMillis()
        if (!forceRefresh && currentTime - lastRefreshTime < refreshDebounceMs) {
            NekoamaLogger.debug("OverviewTab", "refreshStatus call debounced")
            return
        }
        lastRefreshTime = currentTime

        // 开始加载动画
        startLoadingAnimation()

        scope.launch {
            val refreshStartTime = System.currentTimeMillis()
            NekoamaLogger.debug("OverviewTab", "Starting status refresh", mapOf(
                "forceRefresh" to forceRefresh,
                "currentTime" to refreshStartTime
            ))

            try {
                // 在后台线程执行慢操作
                val connectionResult = if (skipConnectionCheck) {
                    // 🔧 修复：启动时跳过连接检查，避免HTTP 407错误
                    ConnectionStatusResult(
                        status = ConnectionStatus.UNKNOWN,
                        message = NekoamaBundle.message("overview.status.click.to.check"),
                        isWarning = false
                    )
                } else {
                    withContext(Dispatchers.IO) {
                        NekoamaLogger.debug("OverviewTab", "Checking connection status in background")
                        // 检查AI服务连接状态（涉及密码存储访问）
                        checkConnectionStatusInBackground()
                    }
                }

                NekoamaLogger.debug("OverviewTab", "Connection status check completed", mapOf(
                    "status" to connectionResult.status.name,
                    "message" to connectionResult.message,
                    "isWarning" to connectionResult.isWarning
                ))

                val configResult = withContext(Dispatchers.IO) {
                    NekoamaLogger.debug("OverviewTab", "Checking configuration status in background")
                    // 检查配置状态
                    checkConfigStatusInBackground()
                }

                NekoamaLogger.debug("OverviewTab", "Configuration status check completed", mapOf(
                    "configComplete" to configResult
                ))

                withContext(Dispatchers.IO) {
                    NekoamaLogger.debug("OverviewTab", "Starting usage summary update in background")
                    // 更新使用摘要（涉及数据收集器访问）
                    updateUsageSummary()
                }

                // 在EDT上更新UI
                NekoamaLogger.debug("OverviewTab", "Updating UI on EDT thread")
                updateConnectionStatusUI(connectionResult)
                updateConfigStatusUI(configResult)

                // 停止加载动画（UI操作，回到EDT）
                stopLoadingAnimation()

                val refreshDuration = System.currentTimeMillis() - refreshStartTime
                NekoamaLogger.info("OverviewTab", "Status refresh completed successfully", mapOf(
                    "duration" to "${refreshDuration}ms",
                    "forceRefresh" to forceRefresh
                ))

                // 通知TabManager同步刷新所有Tab，确保数据一致性
                try {
                    NekoamaTabManager.getInstance().refreshAllTabs()
                } catch (e: Exception) {
                    NekoamaLogger.warn("OverviewTab", "Failed to sync tabs refresh", error = e)
                }
            } catch (e: Exception) {
                NekoamaLogger.error("OverviewTab", "Failed to refresh status", error = e)
                connectionStatusLabel.text = NekoamaBundle.message("overview.status.check.failed")
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
        connectionStatusLabel.text = NekoamaBundle.message("overview.status.checking")
        connectionStatusLabel.foreground = UIManager.getColor("Label.foreground")
        connectionStatusLabel.icon = loadingIcon

        configStatusLabel.text = NekoamaBundle.message("overview.status.checking")
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
     * 检查连接状态（后台线程）
     */
    private suspend fun checkConnectionStatusInBackground(): ConnectionStatusResult {
        try {
            NekoamaLogger.debug("OverviewTab", "Checking connection status in background")

            // 使用createCodeSuggestionGenerator方法验证配置
            val generator = createCodeSuggestionGenerator()
            if (generator == null) {
                // 检查具体是哪项配置缺失
                val settings = NekoamaSettings.getInstance()
                val secureKey = NekoamaSecureStorage.getApiKey()
                val resolvedKey = if (secureKey.isNotBlank()) {
                    secureKey
                } else {
                    settings.apiKey.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }
                }

                val result = when {
                    resolvedKey.isBlank() -> ConnectionStatusResult(
                        status = ConnectionStatus.NOT_CONFIGURED,
                        message = NekoamaBundle.message("overview.api.key.not.configured"),
                        isWarning = true
                    )
                    settings.apiEndpoint.isBlank() -> ConnectionStatusResult(
                        status = ConnectionStatus.NOT_CONFIGURED,
                        message = NekoamaBundle.message("overview.endpoint.not.configured"),
                        isWarning = true
                    )
                    settings.model.isBlank() -> ConnectionStatusResult(
                        status = ConnectionStatus.NOT_CONFIGURED,
                        message = NekoamaBundle.message("overview.config.item.model") + " " +
                                NekoamaBundle.message("overview.not.configured"),
                        isWarning = true
                    )
                    else -> ConnectionStatusResult(
                        status = ConnectionStatus.NOT_CONFIGURED,
                        message = NekoamaBundle.message("overview.config.incomplete"),
                        isWarning = true
                    )
                }
                return result
            }

            // 测试实际连接
            val available = generator.isAvailable()
            return if (available.isSuccess) {
                ConnectionStatusResult(
                    status = ConnectionStatus.CONNECTED,
                    message = NekoamaBundle.message("overview.status.connected"),
                    isWarning = false
                )
            } else {
                ConnectionStatusResult(
                    status = ConnectionStatus.FAILED,
                    message = NekoamaBundle.message("overview.status.failed"),
                    isWarning = true
                )
            }

        } catch (e: Exception) {
            NekoamaLogger.error("checkConnectionStatusInBackground", "Failed to check connection status", error = e)
            return ConnectionStatusResult(
                status = ConnectionStatus.ERROR,
                message = NekoamaBundle.message("overview.status.error"),
                isWarning = true
            )
        }
    }

    /**
     * 更新连接状态UI（EDT线程）
     */
    private fun updateConnectionStatusUI(result: ConnectionStatusResult) {
        connectionStatusLabel.text = result.message
        connectionStatusLabel.foreground = if (result.isWarning) {
            UIManager.getColor("Label.warningForeground")
        } else {
            UIManager.getColor("Label.foreground")
        }

        // 🔧 修复：当状态为UNKNOWN时，添加点击事件监听器
        if (result.status == ConnectionStatus.UNKNOWN) {
            connectionStatusLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            connectionStatusLabel.foreground = UIManager.getColor("Label.linkForeground")

            // 移除旧的监听器（如果有）
            connectionStatusLabel.mouseListeners.forEach { listener ->
                connectionStatusLabel.removeMouseListener(listener)
            }

            // 添加点击事件监听器
            connectionStatusLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    NekoamaLogger.debug("OverviewTab", "Connection status label clicked, triggering connection check")
                    scope.launch {
                        refreshStatus(forceRefresh = true, skipConnectionCheck = false)
                    }
                }
            })
        } else {
            // 恢复普通状态
            connectionStatusLabel.cursor = Cursor.getDefaultCursor()
            connectionStatusLabel.mouseListeners.forEach { listener ->
                connectionStatusLabel.removeMouseListener(listener)
            }
        }
    }

    /**
     * 更新配置状态UI（EDT线程）
     */
    private fun updateConfigStatusUI(result: ConfigStatusResult) {
        val message = if (result.isFullyConfigured) {
            NekoamaBundle.message("overview.config.complete")
        } else {
            NekoamaBundle.message("overview.config.missing") + ": " + result.missingItems.joinToString(", ")
        }

        configStatusLabel.text = message
        configStatusLabel.foreground = if (result.isFullyConfigured) {
            UIManager.getColor("Label.foreground")
        } else {
            UIManager.getColor("Label.warningForeground")
        }
    }

    /**
     * 检查配置状态（后台线程）
     */
    private suspend fun checkConfigStatusInBackground(): ConfigStatusResult {
        try {
            val settings = NekoamaSettings.getInstance()
            val configuredItems = mutableListOf<String>()
            val missingItems = mutableListOf<String>()

            // 检查各个配置项
            if (settings.apiKey.isNotBlank() || NekoamaSecureStorage.getApiKey().isNotBlank()) {
                configuredItems.add(NekoamaBundle.message("overview.config.item.api.key"))
            } else {
                missingItems.add(NekoamaBundle.message("overview.config.item.api.key"))
            }

            if (settings.apiEndpoint.isNotBlank()) {
                configuredItems.add(NekoamaBundle.message("overview.config.item.endpoint"))
            } else {
                missingItems.add(NekoamaBundle.message("overview.config.item.endpoint"))
            }

            if (settings.model.isNotBlank()) {
                configuredItems.add(NekoamaBundle.message("overview.config.item.model"))
            } else {
                missingItems.add(NekoamaBundle.message("overview.config.item.model"))
            }

            return ConfigStatusResult(
                configuredItems = configuredItems,
                missingItems = missingItems,
                isFullyConfigured = missingItems.isEmpty()
            )
        } catch (e: Exception) {
            NekoamaLogger.error("checkConfigStatusInBackground", "Failed to check config status", error = e)
            return ConfigStatusResult(
                configuredItems = emptyList(),
                missingItems = listOf(NekoamaBundle.message("overview.status.error")),
                isFullyConfigured = false
            )
        }
    }

    /**
     * 检查连接状态的简化版本（仅用于设置检查中状态）
     */
    private suspend fun checkConnectionStatus() {
        // 设置检查中状态（UI操作）
        connectionStatusLabel.text = NekoamaBundle.message("overview.status.checking")
        connectionStatusLabel.foreground = UIManager.getColor("Label.foreground")

        NekoamaLogger.debug("OverviewTab", "Checking connection status")
    }
// 
//             // 使用createCodeSuggestionGenerator方法验证配置
//             val generator = createCodeSuggestionGenerator()
//             if (generator == null) {
//                 // 检查具体是哪项配置缺失
//                 val settings = NekoamaSettings.getInstance()
//                 val secureKey = NekoamaSecureStorage.getApiKey()
//                 val resolvedKey = if (secureKey.isNotBlank()) {
//                     secureKey
//                 } else {
//                     settings.apiKey.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }
//                 }
// 
//                 when {
//                     resolvedKey.isBlank() -> {
//                         connectionStatusLabel.text = NekoamaBundle.message("overview.api.key.not.configured")
//                         connectionStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
//                     }
//                     settings.apiEndpoint.isBlank() -> {
//                         connectionStatusLabel.text = NekoamaBundle.message("overview.endpoint.not.configured")
//                         connectionStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
//                     }
//                     settings.model.isBlank() -> {
//                         connectionStatusLabel.text = NekoamaBundle.message("overview.config.item.model") + " " +
//                                 NekoamaBundle.message("overview.not.configured")
//                         connectionStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
//                     }
//                     else -> {
//                         connectionStatusLabel.text = NekoamaBundle.message("overview.config.abnormal")
//                         connectionStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
//                     }
//                 }
//                 return
//             }
// 
//             // 使用AI提供商进行轻量级连接检查
//             val result = generator.isAvailable()
// 
//             when {
//                 result.isSuccess && result.getOrNull() == true -> {
//                     connectionStatusLabel.text = NekoamaBundle.message("overview.connected")
//                     connectionStatusLabel.foreground = UIManager.getColor("Label.successForeground")
//                     NekoamaLogger.debug("OverviewTab", "Connection check successful")
//                 }
//                 result.getOrNull() == false -> {
//                     val error = try { throw Exception("Connection test failed") } catch (e: Exception) { e }
//                     when {
//                         error?.message?.contains("401") == true ||
//                         error?.message?.contains("authentication") == true -> {
//                             connectionStatusLabel.text = NekoamaBundle.message("overview.connection.error.invalid.key")
//                             connectionStatusLabel.foreground = UIManager.getColor("Label.errorForeground")
//                         }
//                         error?.message?.contains("timeout") == true -> {
//                             connectionStatusLabel.text = NekoamaBundle.message("overview.connection.error.timeout")
//                             connectionStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
//                         }
//                         else -> {
//                             connectionStatusLabel.text = NekoamaBundle.message("overview.connection.failed")
//                             connectionStatusLabel.foreground = UIManager.getColor("Label.errorForeground")
//                         }
//                     }
//                     NekoamaLogger.debug("OverviewTab", "Connection check failed: ${error?.message}")
//                 }
//                 else -> {
//                     connectionStatusLabel.text = NekoamaBundle.message("overview.connection.error.service.unavailable")
//                     connectionStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
//                     NekoamaLogger.debug("OverviewTab", "Service unavailable")
//                 }
//             }
// 
//         } catch (e: Exception) {
//             connectionStatusLabel.text = NekoamaBundle.message("overview.status.check.failed")
//             connectionStatusLabel.foreground = UIManager.getColor("Label.errorForeground")
//             NekoamaLogger.error("OverviewTab", "Connection status check failed", error = e)
//         }
//     }
// 
//     /**
//      * 检查配置状态
//      */
//     private suspend fun checkConfigStatus() {
//         try {
//             val settings = NekoamaSettings.getInstance()
// 
//             // 检查各个配置项的完整性
//             val configItems = mutableListOf<String>()
// 
//             if (settings.apiKey.isNotEmpty()) {
//                 configItems.add(NekoamaBundle.message("overview.config.item.api.key"))
//             }
// 
//             if (settings.apiEndpoint.isNotEmpty()) {
//                 configItems.add(NekoamaBundle.message("overview.config.item.endpoint"))
//             }
// 
//             if (settings.model.isNotEmpty()) {
//                 configItems.add(NekoamaBundle.message("overview.config.item.model"))
//             }
// 
//             when (configItems.size) {
//                 3 -> {
//                     configStatusLabel.text = NekoamaBundle.message("overview.configured")
//                     configStatusLabel.foreground = UIManager.getColor("Label.successForeground")
//                 }
//                 2 -> {
//                     configStatusLabel.text = NekoamaBundle.message("overview.config.partial", configItems.joinToString("/"))
//                     configStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
//                 }
//                 1 -> {
//                     configStatusLabel.text = NekoamaBundle.message("overview.config.single", configItems.first())
//                     configStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
//                 }
//                 else -> {
//                     configStatusLabel.text = NekoamaBundle.message("overview.not.configured.apikey")
//                     configStatusLabel.foreground = UIManager.getColor("Label.warningForeground")
//                 }
//             }
// 
//         } catch (e: Exception) {
//             configStatusLabel.text = NekoamaBundle.message("overview.config.check.failed")
//             configStatusLabel.foreground = UIManager.getColor("Label.errorForeground")
//         }
//     }
// 
//     /**
//      * 验证EnhancedMetricsCollector数据
//      */
    private suspend fun validateMetricsData(): Boolean {
        return try {
            val snapshot = MetricsCollector.getEnhancedSnapshot()
            NekoamaLogger.debug("OverviewTab", "Data validation: today=${snapshot.today}, total=${snapshot.total}, tokensToday=${snapshot.tokensToday}")

            // 检查是否有实际数据
            val hasData = snapshot.today > 0 || snapshot.total > 0 || snapshot.tokensToday > 0

            if (hasData) {
                NekoamaLogger.info("OverviewTab", "Found valid metrics data: ${snapshot.today} requests, ${snapshot.tokensToday} tokens")
            } else {
                NekoamaLogger.info("OverviewTab", "No metrics data found yet - this is normal if no AI operations have been performed")
            }

            hasData
        } catch (e: Exception) {
            NekoamaLogger.error("OverviewTab", "Failed to validate metrics data", error = e)
            false
        }
    }

    /**
     * 更新使用摘要（改进版本：完全分离数据获取和UI更新）
     */
    private suspend fun updateUsageSummary() {
        NekoamaLogger.debug("OverviewTab", "Starting usage summary update")

        // 在后台线程执行所有数据操作
        val updateData = withContext(Dispatchers.IO) {
            try {
                // 首先验证数据可用性
                val hasValidData = validateMetricsData()

                if (!hasValidData) {
                    return@withContext UsageUpdateData(
                        hasData = false,
                        message = NekoamaBundle.message("overview.usage.no.data")
                    )
                }

                // 强制同步数据：确保内存统计已持久化并重新加载
                MetricsCollector.forceSync()
                NekoamaLogger.debug("OverviewTab", "Data sync completed in updateUsageSummary")

                // 获取实际数据
                val snapshot = MetricsCollector.getEnhancedSnapshot()

                return@withContext UsageUpdateData(
                    hasData = true,
                    todayRequests = snapshot.today,
                    todayTokens = snapshot.tokensToday,
                    successRate = snapshot.successRate,
                    avgLatency = snapshot.averageLatencyMs.toLong()
                )

            } catch (e: Exception) {
                NekoamaLogger.error("OverviewTab", "Failed to prepare usage summary data", error = e)
                return@withContext UsageUpdateData(
                    hasData = false,
                    message = NekoamaBundle.message("overview.status.load.failed")
                )
            }
        }

        // 在EDT线程更新UI
        withContext(Dispatchers.Main) {
            applyUsageDataToUI(updateData)
        }
    }

    /**
     * 使用摘要更新数据类
     */
    private data class UsageUpdateData(
        val hasData: Boolean,
        val todayRequests: Int = 0,
        val todayTokens: Int = 0,
        val successRate: Double = 0.0,
        val avgLatency: Long = 0,
        val message: String = ""
    )

    /**
     * 将使用数据应用到UI（在EDT线程执行）
     */
    private fun applyUsageDataToUI(data: UsageUpdateData) {
        try {
            if (data.hasData) {
                // 更新所有动态UI组件
                todayUsageValueLabel.text = "${data.todayRequests} " + NekoamaBundle.message("overview.usage.times")
                todayTokensValueLabel.text = "${data.todayTokens}"
                successRateValueLabel.text = String.format("%.1f%%", data.successRate * 100)
                avgLatencyValueLabel.text = "${data.avgLatency}ms"

                // 同时更新旧变量以保持兼容性
                todayUsageLabel.text = "${data.todayRequests} " + NekoamaBundle.message("overview.usage.times")

                NekoamaLogger.debug("OverviewTab", "Usage data applied to UI successfully", mapOf(
                    "requests" to data.todayRequests,
                    "tokens" to data.todayTokens
                ))
            } else {
                // 显示"无数据"或错误状态
                val displayMsg = if (data.message.isNotEmpty()) data.message else "--"
                todayUsageValueLabel.text = displayMsg
                todayTokensValueLabel.text = displayMsg
                successRateValueLabel.text = "--"
                avgLatencyValueLabel.text = "--"
                todayUsageLabel.text = displayMsg

                NekoamaLogger.debug("OverviewTab", "No data state applied to UI", mapOf("message" to data.message))
            }
        } catch (e: Exception) {
            NekoamaLogger.error("OverviewTab", "Failed to apply usage data to UI", error = e)

            // 显示错误状态
            val errorMsg = NekoamaBundle.message("overview.status.load.failed")
            todayUsageValueLabel.text = errorMsg
            todayTokensValueLabel.text = errorMsg
            successRateValueLabel.text = errorMsg
            avgLatencyValueLabel.text = errorMsg
            todayUsageLabel.text = errorMsg
        }
    }

    /**
     * 创建AI提供商实例
     */
    private fun createCodeSuggestionGenerator(): CodeSuggestionGenerator? {
        try {
            val settings = NekoamaSettings.getInstance()

            // 获取API Key的优先级：安全存储 > 设置 > 环境变量
            val secureKey = NekoamaSecureStorage.getApiKeySync()
            val resolvedKey = if (secureKey.isNotBlank()) {
                secureKey
            } else {
                settings.apiKey.ifBlank {
                    System.getenv("OPENAI_API_KEY") ?: ""
                }
            }

            // 验证必要的配置
            if (resolvedKey.isBlank()) {
                NekoamaLogger.warn("OverviewTab", "API key not configured")
                return null
            }

            if (settings.apiEndpoint.isBlank()) {
                NekoamaLogger.warn("OverviewTab", "API endpoint not configured")
                return null
            }

            if (settings.model.isBlank()) {
                NekoamaLogger.warn("OverviewTab", "Model not configured")
                return null
            }

            // 创建Custom API Provider实例
            return OpenAIGenerator(
                CustomGeneratorConfig(
                    generatorName = "Custom API",
                    apiUrl = settings.apiEndpoint,
                    apiKey = resolvedKey,
                    model = settings.model,
                    temperature = settings.modelTemperature / 100.0,
                    timeoutMs = settings.requestTimeoutMs.toLong(),
                    maxTokens = 1 // 连接测试用最小Token数
                )
            )
        } catch (e: Exception) {
            NekoamaLogger.error("OverviewTab", "Failed to create AI provider", error = e)
            return null
        }
    }

    /**
     * 显示连接错误
     */
    private fun showConnectionError(message: String) {
        connectionStatusLabel.text = NekoamaBundle.message("overview.connection.failed")
        connectionStatusLabel.foreground = UIManager.getColor("Label.errorForeground")

        JOptionPane.showMessageDialog(
            mainPanel,
            NekoamaBundle.message("overview.connection.test.failed", message),
            NekoamaBundle.message("overview.connection.test.title"),
            JOptionPane.ERROR_MESSAGE
        )
    }

    /**
     * 测试连接
     */
    private fun testConnection() {
        scope.launch {
            try {
                connectionStatusLabel.text = NekoamaBundle.message("overview.connection.testing")
                connectionStatusLabel.foreground = UIManager.getColor("Label.foreground")

                NekoamaLogger.debug("OverviewTab", "Starting connection test")

                // 获取端点URL
                val settings = NekoamaSettings.getInstance()
                val endpoint = settings.apiEndpoint
                if (endpoint.isBlank()) {
                    showConnectionError(NekoamaBundle.message("overview.endpoint.not.configured"))
                    return@launch
                }

                NekoamaLogger.debug("OverviewTab", "Testing connection to: $endpoint")

                // 使用新的代理测试方法进行连接测试
                val startTime = System.currentTimeMillis()
                val result = ProxyConnectionTester.testCurrentIDEAProxy(endpoint)
                val latency = System.currentTimeMillis() - startTime

                NekoamaLogger.debug("OverviewTab", "Connection test result: $result, latency: ${latency}ms")

                when {
                    result.success -> {
                        connectionStatusLabel.text = NekoamaBundle.message("overview.connected")
                        connectionStatusLabel.foreground = UIManager.getColor("Label.successForeground")

                        val successMessage = if (result.statusCode == 200) {
                            NekoamaBundle.message("settings.ai.test.success.proxy", result.responseTime)
                        } else {
                            NekoamaBundle.message("settings.ai.test.success.reachable", result.statusCode, result.responseTime)
                        } + "\n" + NekoamaBundle.message("overview.connection.test.latency", latency)

                        JOptionPane.showMessageDialog(
                            mainPanel,
                            successMessage,
                            NekoamaBundle.message("overview.connection.test.title"),
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                    else -> {
                        val errorMessage = when {
                            result.message.contains("401") == true ||
                            result.message.contains("authentication") == true ->
                                NekoamaBundle.message("overview.connection.error.invalid.key")
                            result.message.contains("timeout") == true ->
                                NekoamaBundle.message("overview.connection.error.timeout")
                            result.message.contains("network") == true ||
                            result.message.contains("connection") == true ->
                                NekoamaBundle.message("overview.connection.error.network.error")
                            else -> NekoamaBundle.message("settings.ai.test.failed", result.message)
                        }
                        showConnectionError(errorMessage)
                    }
                }

            } catch (e: Exception) {
                NekoamaLogger.error("OverviewTab", "Connection test failed", error = e)
                showConnectionError(NekoamaBundle.message("settings.ai.test.exception", e.message ?: ""))
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
                NekoamaBundle.message("overview.settings.open.failed", e.message ?: ""),
                NekoamaBundle.message("overview.dialog.error"),
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
                val endDate = LocalDate.now()
                val startDate = endDate.minusMonths(1)

                val data = MetricsCollector.exportData(startDate, endDate)
                if (data != null) {
                    // 复制到剪贴板
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    val stringSelection = StringSelection(data)
                    clipboard.setContents(stringSelection, null)

                    JOptionPane.showMessageDialog(
                        mainPanel,
                        NekoamaBundle.message("overview.export.success.clipboard"),
                        NekoamaBundle.message("overview.export.success.title"),
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        NekoamaBundle.message("overview.export.no.data"),
                        NekoamaBundle.message("overview.export.failed.title"),
                        JOptionPane.WARNING_MESSAGE
                    )
                }
            } catch (e: Exception) {
                NekoamaLogger.error("OverviewTab", "Failed to export data", error = e)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    NekoamaBundle.message("overview.export.failed", e.message ?: ""),
                    NekoamaBundle.message("overview.dialog.error"),
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
                NekoamaBundle.message("overview.logs.development"),
                NekoamaBundle.message("overview.dialog.info"),
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
        val titleLabel = JBLabel(NekoamaBundle.message("overview.system.status.title"))
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
        addStatusItem(statusGrid, gbc, 0, 0, NekoamaBundle.message("overview.status.ai.service"), connectionStatusLabel, AllIcons.General.Information)
        addStatusItem(statusGrid, gbc, 0, 1, NekoamaBundle.message("overview.config.status"), configStatusLabel, AllIcons.General.Settings)
        addStatusItem(statusGrid, gbc, 1, 0, NekoamaBundle.message("overview.usage.today"), todayUsageLabel, AllIcons.Nodes.Console)

        // 刷新按钮
        val refreshButton = JButton(AllIcons.Actions.Refresh)
        refreshButton.toolTipText = NekoamaBundle.message("overview.refresh.status.tooltip")
        refreshButton.isFocusable = false
        refreshButton.addActionListener { refreshStatus(forceRefresh = true) }

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

/**
 * 连接状态结果
 */
data class ConnectionStatusResult(
    val status: ConnectionStatus,
    val message: String,
    val isWarning: Boolean
)

/**
 * 配置状态结果
 */
data class ConfigStatusResult(
    val configuredItems: List<String>,
    val missingItems: List<String>,
    val isFullyConfigured: Boolean
)

/**
 * 连接状态枚举
 */
enum class ConnectionStatus {
    CHECKING,
    CONNECTED,
    FAILED,
    NOT_CONFIGURED,
    ERROR,
    UNKNOWN  // 🔧 新增：未知状态，用于启动时不进行检查
}