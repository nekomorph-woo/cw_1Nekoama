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
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
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
import javax.swing.Timer

/**
 * Dashboard Tab
 *
 * 显示：
 * - 网络连通性状态
 * - Token 使用统计
 * - 功能使用统计
 */
class DashboardTab(
    project: com.intellij.openapi.project.Project,
    coordinatorService: TabCoordinatorService
) : BaseTab(project, coordinatorService) {

    // 通过服务定位器模式获取依赖
    private val statisticsService: StatisticsService
        get() = project.service()
    private val networkTestService: NetworkTestService
        get() = project.service()

    override val metadata = TabMetadata(
        id = TabMetadata.TabId("dashboard"),
        displayName = NekoamaBundle.message("dashboard.tab.title"),
        icon = AllIcons.General.Web
    )

    override val stateType = DashboardTabState::class

    private var state: DashboardTabState? = null
    private var refreshTimer: Timer? = null

    // UI 组件引用
    private lateinit var mainPanel: JPanel
    private lateinit var networkStatusPanel: JPanel
    private lateinit var tokenStatsPanel: JPanel
    private lateinit var usageStatsPanel: JPanel

    override fun createComponentImpl(): JPanel {
        mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        }

        // 标题
        mainPanel.add(createHeaderPanel())
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

            val contentLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
                foreground = JBColor.GRAY
            }
            add(contentLabel, BorderLayout.CENTER)
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

            val contentLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
                foreground = JBColor.GRAY
            }
            add(contentLabel, BorderLayout.CENTER)
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

            val contentLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
                foreground = JBColor.GRAY
            }
            add(contentLabel, BorderLayout.CENTER)
        }
    }

    private fun createSpacer(height: Int): JPanel {
        return JPanel().apply {
            preferredSize = java.awt.Dimension(0, height)
            maximumSize = java.awt.Dimension(Integer.MAX_VALUE, height)
            background = TabThemeManager.getTabBackgroundColor()
        }
    }

    private fun refreshData() {
        // TODO: 实现数据刷新逻辑
        NekoamaLogger.info("DashboardTab", "Refreshing data...")
    }

    override fun onActivated() {
        state = loadState(DashboardTabState::class)
        refreshData()
    }

    override fun onDeactivated() {
        val newState = DashboardTabState(lastRefreshed = System.currentTimeMillis())
        saveState(newState)
    }

    override fun onDestroy() {
        refreshTimer?.stop()
        refreshTimer = null
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
