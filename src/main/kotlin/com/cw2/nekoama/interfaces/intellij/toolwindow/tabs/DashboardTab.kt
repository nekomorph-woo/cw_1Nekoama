package com.cw2.nekoama.interfaces.intellij.toolwindow.tabs

import com.cw2.nekoama.domain.statistics.service.NetworkTestService
import com.cw2.nekoama.domain.statistics.service.NetworkTestServiceImpl
import com.cw2.nekoama.domain.statistics.service.StatisticsService
import com.cw2.nekoama.domain.statistics.service.StatisticsServiceImpl
import com.cw2.nekoama.domain.toolwindow.model.TabMetadata
import com.cw2.nekoama.domain.toolwindow.model.TabState
import com.cw2.nekoama.domain.toolwindow.service.TabCoordinatorService
import com.cw2.nekoama.infrastructure.toolwindow.TabThemeManager
import com.cw2.nekoama.interfaces.intellij.toolwindow.BaseTab
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project as IjProject
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ActionEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.Timer

/**
 * Dashboard Tab
 *
 * 显示：
 * - 快捷操作按钮
 * - 网络连通性状态
 * - Token 使用统计
 * - 功能使用统计
 */
class DashboardTab(
    project: IjProject,
    coordinatorService: TabCoordinatorService
) : BaseTab(project, coordinatorService) {

    // 延迟初始化服务（避免在组件加载时访问）
    private val statisticsService: StatisticsServiceImpl?
        get() = try {
            project.service()
        } catch (e: Exception) {
            NekoamaLogger.warn("DashboardTab", "StatisticsService not available: ${e.message}")
            null
        }

    private val networkTestService: NetworkTestServiceImpl?
        get() = try {
            project.service()
        } catch (e: Exception) {
            NekoamaLogger.warn("DashboardTab", "NetworkTestService not available: ${e.message}")
            null
        }

    // 生命周期感知的协程 Scope
    private val tabScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val metadata = TabMetadata(
        id = TabMetadata.TabId("dashboard"),
        displayName = NekoamaBundle.message("dashboard.tab.title"),
        icon = AllIcons.General.Web
    )

    override val stateType = DashboardTabState::class

    private var state: DashboardTabState? = null

    // UI 组件引用
    private lateinit var mainPanel: JPanel
    private lateinit var quickActionsPanel: JPanel
    private lateinit var networkStatusPanel: JPanel

    // 网络状态面板的各个子组件
    private lateinit var proxyLabel: JBLabel
    private lateinit var endpointLabel: JBLabel
    private lateinit var modelLabel: JBLabel
    private lateinit var connectionStatusLabel: JBLabel
    private lateinit var troubleshootingPanel: JPanel
    private lateinit var troubleshootingLabel: JBLabel

    private lateinit var tokenStatsPanel: JPanel
    private lateinit var tokenStatsLabel: JBLabel
    private lateinit var usageStatsPanel: JPanel
    private lateinit var usageStatsLabel: JBLabel

    override fun createComponentImpl(): JPanel {
        // 创建主容器面板（使用 BorderLayout 居中内容）
        val containerPanel = JPanel(BorderLayout()).apply {
            background = TabThemeManager.getTabBackgroundColor()
            border = JBUI.Borders.empty(12)
        }

        // 创建内容面板（垂直布局，顶部对齐）
        mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT
            alignmentY = Component.TOP_ALIGNMENT
        }

        // 标题栏
        mainPanel.add(createHeaderPanel())
        mainPanel.add(Box.createVerticalStrut(12))

        // 分隔线
        mainPanel.add(JSeparator())
        mainPanel.add(Box.createVerticalStrut(12))

        // 快捷操作按钮
        quickActionsPanel = createQuickActionsPanel()
        mainPanel.add(quickActionsPanel)
        mainPanel.add(Box.createVerticalStrut(12))

        // 分隔线
        mainPanel.add(JSeparator())
        mainPanel.add(Box.createVerticalStrut(8))

        // 网络状态面板
        networkStatusPanel = createNetworkStatusPanel()
        mainPanel.add(networkStatusPanel)
        mainPanel.add(Box.createVerticalStrut(8))

        // Token 统计面板
        tokenStatsPanel = createTokenStatsPanel()
        mainPanel.add(tokenStatsPanel)
        mainPanel.add(Box.createVerticalStrut(8))

        // 使用统计面板
        usageStatsPanel = createUsageStatsPanel()
        mainPanel.add(usageStatsPanel)

        // 用 JScrollPane 包裹，支持滚动（内容少时不显示滚动条）
        val scrollPane = JBScrollPane(mainPanel).apply {
            border = null
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            viewport.background = TabThemeManager.getTabBackgroundColor()
        }

        containerPanel.add(scrollPane, BorderLayout.NORTH)

        return containerPanel
    }

    private fun createHeaderPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT
            alignmentY = Component.TOP_ALIGNMENT
            border = JBUI.Borders.empty(0, 0, 4, 0)

            val titleLabel = JBLabel(NekoamaBundle.message("dashboard.tab.title")).apply {
                font = font.deriveFont(Font.BOLD, 18f)
                foreground = UIUtil.getLabelForeground()
            }
            add(titleLabel)

            add(Box.createHorizontalGlue())

            val refreshButton = JButton(NekoamaBundle.message("dashboard.button.refresh")).apply {
                font = font.deriveFont(12f)
                isFocusPainted = false
                addActionListener { refreshData() }
            }
            add(refreshButton)
        }
    }

    /**
     * 创建快捷操作按钮面板（极简风格）
     */
    private fun createQuickActionsPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT
            alignmentY = Component.TOP_ALIGNMENT

            // 设置按钮
            val settingsButton = createQuickActionButton(
                NekoamaBundle.message("dashboard.button.settings"),
                AllIcons.General.Settings,
                NekoamaBundle.message("dashboard.button.settings.tooltip")
            ) {
                openSettings()
            }

            // 使用指南按钮
            val guideButton = createQuickActionButton(
                NekoamaBundle.message("dashboard.button.guide"),
                AllIcons.Actions.Help,
                NekoamaBundle.message("dashboard.button.guide.tooltip")
            ) {
                openUserGuide()
            }

            // 测试连接按钮
            val testConnectionButton = createQuickActionButton(
                NekoamaBundle.message("dashboard.button.test.connection"),
                AllIcons.General.Web,
                NekoamaBundle.message("dashboard.button.test.connection.tooltip")
            ) {
                testConnection()
            }

            add(settingsButton)
            add(guideButton)
            add(testConnectionButton)
        }
    }

    private fun createQuickActionButton(
        text: String,
        icon: javax.swing.Icon,
        tooltip: String,
        onClick: () -> Unit
    ): JButton {
        return JButton(text, icon).apply {
            toolTipText = tooltip
            font = font.deriveFont(12f)
            isFocusPainted = false
            margin = JBUI.insets(4, 8, 4, 8)
            addActionListener { onClick() }
        }
    }

    private fun openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Nekoama.settings")
    }

    private fun openUserGuide() {
        val url = NekoamaBundle.message("dashboard.guide.url")
        BrowserUtil.browse(url)
    }

    private fun testConnection() {
        val service = networkTestService ?: run {
            // Service 不可用时直接更新 UI（已在 EDT 中）
            proxyLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
            endpointLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
            modelLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
            connectionStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
            connectionStatusLabel.foreground = JBColor.RED
            troubleshootingPanel.isVisible = false
            return
        }

        // 更新为测试中状态
        connectionStatusLabel.text = NekoamaBundle.message("dashboard.status.testing")
        connectionStatusLabel.foreground = JBColor.GRAY

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            NekoamaBundle.message("dashboard.progress.testing.connection"),
            true  // 可取消
        ) {
            private var testResult: com.cw2.nekoama.domain.statistics.model.ConnectivityStatus? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = NekoamaBundle.message("dashboard.progress.testing.connection")

                // 使用 runBlocking 在后台线程中执行挂起函数
                // 不捕获异常，让 Task.Backgroundable 框架处理，会调用 onThrowable()
                testResult = kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeout(15_000) {  // 15 秒超时
                        service.testConnectivity(null)
                    }
                }
            }

            override fun onSuccess() {
                // onSuccess 自动在 EDT 上执行，只处理成功情况
                if (testResult != null) {
                    val status = testResult!!

                    // 更新代理配置
                    proxyLabel.text = NekoamaBundle.message("dashboard.network.proxy", formatProxyConfig(status.proxyConfig))

                    // 更新端点
                    endpointLabel.text = NekoamaBundle.message("dashboard.network.endpoint", status.endpoint)

                    // 更新模型
                    modelLabel.text = NekoamaBundle.message("dashboard.network.model", status.model)

                    // 更新连接状态
                    if (status.isConnected) {
                        val timeStr = if (status.responseTime > 0) {
                            " (${status.responseTime}ms)"
                        } else {
                            ""
                        }
                        connectionStatusLabel.text = NekoamaBundle.message("dashboard.status.connected", timeStr)
                        connectionStatusLabel.foreground = JBColor.GREEN
                    } else {
                        connectionStatusLabel.text = status.message
                        connectionStatusLabel.foreground = JBColor.RED
                    }

                    // 更新排查指南
                    updateTroubleshootingGuide(status.troubleshootingGuide)
                }
            }

            override fun onThrowable(error: Throwable) {
                // 错误处理（在 EDT 上），处理所有错误（包括超时）
                proxyLabel.text = NekoamaBundle.message("dashboard.status.loading")
                endpointLabel.text = NekoamaBundle.message("dashboard.status.loading")
                modelLabel.text = NekoamaBundle.message("dashboard.status.loading")
                connectionStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
                connectionStatusLabel.foreground = JBColor.RED
                troubleshootingPanel.isVisible = false
                NekoamaLogger.error("DashboardTab", "Test connection error", mapOf("error" to (error.message ?: "unknown")))
            }
        })
    }

    /**
     * 创建网络状态面板（极简风格）
     */
    private fun createNetworkStatusPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT
            alignmentY = Component.TOP_ALIGNMENT
            border = JBUI.Borders.empty(8, 0)
        }

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("dashboard.section.network")).apply {
            font = font.deriveFont(Font.BOLD, 13f)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(titleLabel)
        panel.add(Box.createVerticalStrut(4))

        // 分隔线
        panel.add(JSeparator())
        panel.add(Box.createVerticalStrut(8))

        // 内容区域
        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT
            alignmentY = Component.TOP_ALIGNMENT
        }

        // 代理配置标签
        proxyLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
            foreground = UIUtil.getLabelForeground().darker()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        contentPanel.add(proxyLabel)
        contentPanel.add(Box.createVerticalStrut(4))

        // 端点标签
        endpointLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
            foreground = UIUtil.getLabelForeground().darker()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        contentPanel.add(endpointLabel)
        contentPanel.add(Box.createVerticalStrut(4))

        // 模型标签
        modelLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
            foreground = UIUtil.getLabelForeground().darker()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        contentPanel.add(modelLabel)
        contentPanel.add(Box.createVerticalStrut(4))

        // 连接状态标签
        connectionStatusLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
            foreground = UIUtil.getLabelForeground().darker()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        contentPanel.add(connectionStatusLabel)

        // 排查指南面板（CardLayout，默认隐藏）
        troubleshootingPanel = JPanel(CardLayout()).apply {
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT
            isVisible = false
        }
        troubleshootingLabel = JBLabel().apply {
            foreground = UIUtil.getLabelForeground().darker()
        }
        troubleshootingPanel.add(troubleshootingLabel, "guide")
        contentPanel.add(Box.createVerticalStrut(8))
        contentPanel.add(troubleshootingPanel)

        panel.add(contentPanel)
        return panel
    }

    /**
     * 创建 Token 统计面板（极简风格）
     */
    private fun createTokenStatsPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT
            alignmentY = Component.TOP_ALIGNMENT
            border = JBUI.Borders.empty(8, 0)
        }

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("dashboard.section.tokens")).apply {
            font = font.deriveFont(Font.BOLD, 13f)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(titleLabel)
        panel.add(Box.createVerticalStrut(4))

        // 分隔线
        panel.add(JSeparator())
        panel.add(Box.createVerticalStrut(8))

        // 内容
        tokenStatsLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
            foreground = UIUtil.getLabelForeground().darker()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(tokenStatsLabel)

        return panel
    }

    /**
     * 创建使用统计面板（极简风格）
     */
    private fun createUsageStatsPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT
            alignmentY = Component.TOP_ALIGNMENT
            border = JBUI.Borders.empty(8, 0)
        }

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("dashboard.section.usage")).apply {
            font = font.deriveFont(Font.BOLD, 13f)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(titleLabel)
        panel.add(Box.createVerticalStrut(4))

        // 分隔线
        panel.add(JSeparator())
        panel.add(Box.createVerticalStrut(8))

        // 内容
        usageStatsLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
            foreground = UIUtil.getLabelForeground().darker()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(usageStatsLabel)

        return panel
    }

    /**
     * 格式化代理配置为显示字符串
     */
    private fun formatProxyConfig(proxyConfig: com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig?): String {
        if (proxyConfig == null) {
            return NekoamaBundle.message("dashboard.network.proxy.direct")
        }

        val type = when (proxyConfig.type) {
            com.cw2.nekoama.infrastructure.network.proxy.ProxyType.HTTP -> "HTTP"
            com.cw2.nekoama.infrastructure.network.proxy.ProxyType.HTTPS -> "HTTPS"
            com.cw2.nekoama.infrastructure.network.proxy.ProxyType.SOCKS -> "SOCKS"
            com.cw2.nekoama.infrastructure.network.proxy.ProxyType.DIRECT -> "Direct"
        }

        val host = proxyConfig.host ?: "unknown"
        val port = proxyConfig.port ?: 0

        return if (proxyConfig.type == com.cw2.nekoama.infrastructure.network.proxy.ProxyType.DIRECT) {
            NekoamaBundle.message("dashboard.network.proxy.direct")
        } else {
            NekoamaBundle.message("dashboard.network.proxy", "$type $host:$port")
        }
    }

    /**
     * 更新排查指南面板
     */
    private fun updateTroubleshootingGuide(guide: List<String>?) {
        if (guide.isNullOrEmpty()) {
            troubleshootingPanel.isVisible = false
            return
        }

        troubleshootingPanel.isVisible = true
        val cardLayout = troubleshootingPanel.layout as CardLayout
        cardLayout.show(troubleshootingPanel, "guide")

        val html = buildString {
            append("<html><div style='padding: 8px;'>")
            append("<b>${NekoamaBundle.message("dashboard.network.troubleshooting.title")}</b><br>")
            guide.forEach { step ->
                append(step).append("<br>")
            }
            append("</div></html>")
        }

        troubleshootingLabel.text = html
    }

    /**
     * 刷新所有面板数据
     */
    private fun refreshData() {
        NekoamaLogger.info("DashboardTab", "Refreshing data...")

        // 使用 tabScope 在后台协程中执行
        tabScope.launch {
            try {
                // 1. 刷新网络状态
                refreshNetworkStatus()

                // 2. 刷新 Token 统计
                refreshTokenStats()

                // 3. 刷新使用统计
                refreshUsageStats()
            } catch (e: Exception) {
                NekoamaLogger.error("DashboardTab", "Failed to refresh data", mapOf("error" to (e.message ?: "unknown")))
                ApplicationManager.getApplication().invokeLater {
                    connectionStatusLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
                    connectionStatusLabel.foreground = JBColor.RED
                    tokenStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
                    usageStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
                }
            }
        }
    }

    /**
     * 刷新网络状态
     */
    private suspend fun refreshNetworkStatus() {
        val service = networkTestService
        if (service == null) {
            NekoamaLogger.error("DashboardTab", "NetworkTestService is not available")
            ApplicationManager.getApplication().invokeLater {
                proxyLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
                endpointLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
                modelLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
                connectionStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
                connectionStatusLabel.foreground = JBColor.RED
                troubleshootingPanel.isVisible = false
            }
            return
        }

        try {
            // 添加 15 秒超时控制
            val status = kotlinx.coroutines.withTimeout(15_000) {
                service.testConnectivity(null)
            }
            ApplicationManager.getApplication().invokeLater {
                // 更新代理配置
                proxyLabel.text = NekoamaBundle.message("dashboard.network.proxy", formatProxyConfig(status.proxyConfig))

                // 更新端点
                endpointLabel.text = NekoamaBundle.message("dashboard.network.endpoint", status.endpoint)

                // 更新模型
                modelLabel.text = NekoamaBundle.message("dashboard.network.model", status.model)

                // 更新连接状态
                if (status.isConnected) {
                    val timeStr = if (status.responseTime > 0) {
                        " (${status.responseTime}ms)"
                    } else {
                        ""
                    }
                    connectionStatusLabel.text = NekoamaBundle.message("dashboard.status.connected", timeStr)
                    connectionStatusLabel.foreground = JBColor.GREEN
                } else {
                    connectionStatusLabel.text = status.message
                    connectionStatusLabel.foreground = JBColor.RED
                }

                // 更新排查指南
                updateTroubleshootingGuide(status.troubleshootingGuide)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            NekoamaLogger.error("DashboardTab", "Network test timeout after 15 seconds")
            ApplicationManager.getApplication().invokeLater {
                proxyLabel.text = NekoamaBundle.message("dashboard.status.loading")
                endpointLabel.text = NekoamaBundle.message("dashboard.status.loading")
                modelLabel.text = NekoamaBundle.message("dashboard.status.loading")
                connectionStatusLabel.text = NekoamaBundle.message("dashboard.error.timeout")
                connectionStatusLabel.foreground = JBColor.RED
                troubleshootingPanel.isVisible = false
            }
        } catch (e: Exception) {
            // Log detailed error information in English to avoid console encoding issues
            NekoamaLogger.error("DashboardTab", "Network status refresh failed",
                mapOf(
                    "error_class" to e.javaClass.simpleName,
                    "error_message" to (e.message ?: "null"),
                    "stack_trace" to (e.stackTraceToString().take(500))
                )
            )
            ApplicationManager.getApplication().invokeLater {
                // Show error details to user for debugging
                proxyLabel.text = NekoamaBundle.message("dashboard.status.loading")
                endpointLabel.text = NekoamaBundle.message("dashboard.status.loading")
                modelLabel.text = NekoamaBundle.message("dashboard.status.loading")
                connectionStatusLabel.text = "Error: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}"
                connectionStatusLabel.foreground = JBColor.RED
                troubleshootingPanel.isVisible = false
            }
        }
    }

    private suspend fun refreshTokenStats() {
        val service = statisticsService
        if (service == null) {
            ApplicationManager.getApplication().invokeLater {
                tokenStatsLabel.text = NekoamaBundle.message("dashboard.error.service.unavailable")
            }
            return
        }

        try {
            val stats = withContext(Dispatchers.IO) {
                service.getTokenStatistics()
            }
            ApplicationManager.getApplication().invokeLater {
                val totalFormatted = stats.formatTokenCount(stats.totalTokens)
                val currentFormatted = stats.formatTokenCount(stats.currentMonthData.totalTokens)

                val growth = stats.getMonthOverMonthGrowth()
                val growthStr = if (growth != null && growth >= 0) "+%.1f%%".format(growth)
                                 else if (growth != null) "%.1f%%".format(growth)
                                 else "N/A"

                // 使用 JBColor 以支持主题切换
                val growthColor = if (growth != null && growth >= 0) {
                    JBColor(0x00AA00, 0x50C878)  // Light: 深绿, Dark: 亮绿
                } else {
                    JBColor(0xCC0000, 0xFF6B6B)  // Light: 深红, Dark: 亮红
                }

                tokenStatsLabel.text = """
                    <html>
                    <div style='padding: 8px;'>
                        <div><b>${NekoamaBundle.message("dashboard.tokens.total")}</b> $totalFormatted</div>
                        <div style='margin-top: 4px;'><b>${NekoamaBundle.message("dashboard.tokens.current")}</b> $currentFormatted</div>
                        <div style='margin-top: 4px; color: ${String.format("#%06X", 0xFFFFFF and growthColor.rgb)};'>
                            <b>${NekoamaBundle.message("dashboard.tokens.growth")}</b> $growthStr
                        </div>
                    </div>
                    </html>
                """.trimIndent()
            }
        } catch (e: Exception) {
            ApplicationManager.getApplication().invokeLater {
                tokenStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
            }
        }
    }

    private suspend fun refreshUsageStats() {
        val service = statisticsService
        if (service == null) {
            ApplicationManager.getApplication().invokeLater {
                usageStatsLabel.text = NekoamaBundle.message("dashboard.error.service.unavailable")
            }
            return
        }

        try {
            val stats = withContext(Dispatchers.IO) {
                service.getUsageStatistics()
            }
            ApplicationManager.getApplication().invokeLater {
                val namingPercent = stats.getPercentage(com.cw2.nekoama.domain.statistics.model.ActionType.NAMING)
                val commentPercent = stats.getPercentage(com.cw2.nekoama.domain.statistics.model.ActionType.COMMENT)
                val customPercent = stats.getPercentage(com.cw2.nekoama.domain.statistics.model.ActionType.CUSTOM_GENERATE)

                usageStatsLabel.text = """
                    <html>
                    <div style='padding: 8px;'>
                        <div><b>${NekoamaBundle.message("dashboard.usage.naming")}</b>: ${stats.namingCount} (${"%.1f".format(namingPercent)}%)</div>
                        <div style='margin-top: 4px;'><b>${NekoamaBundle.message("dashboard.usage.comment")}</b>: ${stats.commentCount} (${"%.1f".format(commentPercent)}%)</div>
                        <div style='margin-top: 4px;'><b>${NekoamaBundle.message("dashboard.usage.custom")}</b>: ${stats.customGenerateCount} (${"%.1f".format(customPercent)}%)</div>
                        <div style='margin-top: 8px; color: #888888;'>Total: ${stats.totalCount}</div>
                    </div>
                    </html>
                """.trimIndent()
            }
        } catch (e: Exception) {
            ApplicationManager.getApplication().invokeLater {
                usageStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
            }
        }
    }

    override fun onActivated() {
        state = loadState(DashboardTabState::class)
        // 延迟刷新，确保组件完全加载
        ApplicationManager.getApplication().invokeLater {
            refreshData()
        }
    }

    override fun onDeactivated() {
        val newState = DashboardTabState(lastRefreshed = System.currentTimeMillis())
        saveState(newState)
    }

    override fun onDestroy() {
        // 取消所有协程
        tabScope.cancel()
    }
}

/**
 * Dashboard Tab 状态数据
 */
data class DashboardTabState(
    val lastRefreshed: Long = System.currentTimeMillis()
) : TabState {
    override fun validate(): com.cw2.nekoama.shared.model.NekoamaResult<Unit> {
        return com.cw2.nekoama.shared.model.NekoamaResult.success(Unit)
    }
}
