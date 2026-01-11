package com.cw2.nekoama.interfaces.intellij.toolwindow.tabs

import com.cw2.nekoama.domain.statistics.service.NetworkTestService
import com.cw2.nekoama.domain.statistics.service.StatisticsService
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
import com.intellij.openapi.project.Project as IjProject
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
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
import com.intellij.util.concurrency.EdtExecutor
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.ActionEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
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
    private val statisticsService: StatisticsService?
        get() = try {
            project.service()
        } catch (e: Exception) {
            NekoamaLogger.warn("DashboardTab", "StatisticsService not available: ${e.message}")
            null
        }

    private val networkTestService: NetworkTestService?
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
    private lateinit var networkStatusLabel: JBLabel
    private lateinit var tokenStatsPanel: JPanel
    private lateinit var tokenStatsLabel: JBLabel
    private lateinit var usageStatsPanel: JPanel
    private lateinit var usageStatsLabel: JBLabel

    override fun createComponentImpl(): JPanel {
        mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        }

        // 标题
        mainPanel.add(createHeaderPanel())
        mainPanel.add(createSpacer(16))

        // 快捷操作按钮
        quickActionsPanel = createQuickActionsPanel()
        mainPanel.add(quickActionsPanel)
        mainPanel.add(createSpacer(16))

        // 网络状态面板
        networkStatusPanel = createNetworkStatusPanel()
        mainPanel.add(networkStatusPanel)
        mainPanel.add(createSpacer(12))

        // Token 统计面板
        tokenStatsPanel = createTokenStatsPanel()
        mainPanel.add(tokenStatsPanel)
        mainPanel.add(createSpacer(12))

        // 使用统计面板
        usageStatsPanel = createUsageStatsPanel()
        mainPanel.add(usageStatsPanel)

        return mainPanel
    }

    private fun createHeaderPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT

            val titleLabel = JBLabel(NekoamaBundle.message("dashboard.tab.title")).apply {
                font = font.deriveFont(Font.BOLD, 20f)
                foreground = TabThemeManager.getTabTextColor()
            }
            add(titleLabel)

            add(javax.swing.Box.createHorizontalGlue())

            val refreshButton = JButton(NekoamaBundle.message("dashboard.button.refresh")).apply {
                addActionListener { refreshData() }
            }
            add(refreshButton)
        }
    }

    /**
     * 创建快捷操作按钮面板
     */
    private fun createQuickActionsPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TabThemeManager.getBorderColor()),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
            )

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
            add(javax.swing.Box.createHorizontalStrut(8))
            add(guideButton)
            add(javax.swing.Box.createHorizontalStrut(8))
            add(testConnectionButton)
            add(javax.swing.Box.createHorizontalGlue())
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
            networkStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
            networkStatusLabel.foreground = JBColor.RED
            return
        }

        // 更新为测试中状态
        networkStatusLabel.text = NekoamaBundle.message("dashboard.status.testing")
        networkStatusLabel.foreground = JBColor.GRAY

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            NekoamaBundle.message("dashboard.progress.testing.connection"),
            true  // 可取消
        ) {
            private var testResult: com.cw2.nekoama.domain.statistics.model.ConnectivityStatus? = null
            private var testError: Throwable? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = NekoamaBundle.message("dashboard.progress.testing.connection")

                try {
                    // 使用 runBlocking 在后台线程中执行挂起函数
                    testResult = kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeout(15_000) {  // 15 秒超时
                            service.testConnectivity(null)
                        }
                    }
                } catch (e: Exception) {
                    testError = e
                }
            }

            override fun onSuccess() {
                // onSuccess 自动在 EDT 上执行
                if (testError != null) {
                    networkStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
                    networkStatusLabel.foreground = JBColor.RED
                    NekoamaLogger.error("DashboardTab", "Test connection failed", mapOf("error" to (testError?.message ?: "unknown")))
                } else if (testResult != null) {
                    val status = testResult!!
                    if (status.isConnected) {
                        val timeStr = if (status.responseTime > 0) {
                            " (${status.responseTime}ms)"
                        } else {
                            ""
                        }
                        networkStatusLabel.text = NekoamaBundle.message("dashboard.status.connected", timeStr)
                        networkStatusLabel.foreground = JBColor.GREEN
                    } else {
                        networkStatusLabel.text = status.message
                        networkStatusLabel.foreground = JBColor.RED
                    }
                }
            }

            override fun onThrowable(error: Throwable) {
                // 错误处理（在 EDT 上）
                networkStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
                networkStatusLabel.foreground = JBColor.RED
                NekoamaLogger.error("DashboardTab", "Test connection error", mapOf("error" to (error.message ?: "unknown")))
            }
        })
    }

    private fun createNetworkStatusPanel(): JPanel {
        return JPanel().apply {
            layout = BorderLayout(8, 8)
            background = TabThemeManager.getTabBackgroundColor()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TabThemeManager.getBorderColor()),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
            )

            val titleLabel = JBLabel(NekoamaBundle.message("dashboard.section.network")).apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }
            add(titleLabel, BorderLayout.NORTH)

            networkStatusLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
                foreground = JBColor.GRAY
            }
            add(networkStatusLabel, BorderLayout.CENTER)
        }
    }

    private fun createTokenStatsPanel(): JPanel {
        return JPanel().apply {
            layout = BorderLayout(8, 8)
            background = TabThemeManager.getTabBackgroundColor()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TabThemeManager.getBorderColor()),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
            )

            val titleLabel = JBLabel(NekoamaBundle.message("dashboard.section.tokens")).apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }
            add(titleLabel, BorderLayout.NORTH)

            tokenStatsLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
                foreground = JBColor.GRAY
            }
            add(tokenStatsLabel, BorderLayout.CENTER)
        }
    }

    private fun createUsageStatsPanel(): JPanel {
        return JPanel().apply {
            layout = BorderLayout(8, 8)
            background = TabThemeManager.getTabBackgroundColor()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TabThemeManager.getBorderColor()),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
            )

            val titleLabel = JBLabel(NekoamaBundle.message("dashboard.section.usage")).apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }
            add(titleLabel, BorderLayout.NORTH)

            usageStatsLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
                foreground = JBColor.GRAY
            }
            add(usageStatsLabel, BorderLayout.CENTER)
        }
    }

    private fun createSpacer(height: Int): JPanel {
        return JPanel().apply {
            preferredSize = java.awt.Dimension(0, height)
            maximumSize = java.awt.Dimension(Integer.MAX_VALUE, height)
            background = TabThemeManager.getTabBackgroundColor()
        }
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
                SwingUtilities.invokeLater {
                    networkStatusLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
                    tokenStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
                    usageStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
                }
            }
        }
    }

    private suspend fun refreshNetworkStatus() {
        val service = networkTestService
        if (service == null) {
            SwingUtilities.invokeLater {
                networkStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
            }
            return
        }

        try {
            val status = service.testConnectivity(null)
            SwingUtilities.invokeLater {
                if (status.isConnected) {
                    val timeStr = if (status.responseTime > 0) {
                        " (${status.responseTime}ms)"
                    } else {
                        ""
                    }
                    networkStatusLabel.text = NekoamaBundle.message("dashboard.status.connected", timeStr)
                    networkStatusLabel.foreground = JBColor.GREEN
                } else {
                    networkStatusLabel.text = status.message
                    networkStatusLabel.foreground = JBColor.RED
                }
            }
        } catch (e: Exception) {
            SwingUtilities.invokeLater {
                networkStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
                networkStatusLabel.foreground = JBColor.RED
            }
        }
    }

    private suspend fun refreshTokenStats() {
        val service = statisticsService
        if (service == null) {
            SwingUtilities.invokeLater {
                tokenStatsLabel.text = NekoamaBundle.message("dashboard.error.service.unavailable")
            }
            return
        }

        try {
            val stats = withContext(Dispatchers.IO) {
                service.getTokenStatistics()
            }
            SwingUtilities.invokeLater {
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
            SwingUtilities.invokeLater {
                tokenStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
            }
        }
    }

    private suspend fun refreshUsageStats() {
        val service = statisticsService
        if (service == null) {
            SwingUtilities.invokeLater {
                usageStatsLabel.text = NekoamaBundle.message("dashboard.error.service.unavailable")
            }
            return
        }

        try {
            val stats = withContext(Dispatchers.IO) {
                service.getUsageStatistics()
            }
            SwingUtilities.invokeLater {
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
            SwingUtilities.invokeLater {
                usageStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
            }
        }
    }

    override fun onActivated() {
        state = loadState(DashboardTabState::class)
        // 延迟刷新，确保组件完全加载
        SwingUtilities.invokeLater {
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
