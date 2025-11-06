package com.cw2.nekoama.presentation.toolwindow

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.cw2.nekoama.presentation.toolwindow.tab.NekoamaTabManager
import com.cw2.nekoama.presentation.toolwindow.tab.TokenStatsTab
import com.cw2.nekoama.presentation.toolwindow.tab.OverviewTab
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBEmptyBorder
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import com.intellij.util.ui.JBFont
import javax.swing.border.EmptyBorder

/**
 * 模块化的Nekoama工具窗口
 *
 * 使用Tab管理器来管理各个功能模块，支持动态Tab加载和状态保持。
 * 这是重构后的主要工具窗口实现。
 */
class ModularToolWindow {

    private val mainPanel = JPanel(BorderLayout())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tabManager = NekoamaTabManager.getInstance()

    init {
        setupUI()
        registerDefaultTabs()
        NekoamaLogger.info("ModularToolWindow", "initialized")
    }

    /**
     * 设置UI布局
     */
    private fun setupUI() {
        mainPanel.border = JBEmptyBorder(10)

        // 创建标题栏
        val headerPanel = createHeaderPanel()

        // 创建Tab容器
        val tabbedPane = tabManager.getTabbedPane()

        mainPanel.add(headerPanel, BorderLayout.NORTH)
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
    }

    /**
     * 创建标题栏
     */
    private fun createHeaderPanel(): JPanel {
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = EmptyBorder(0, 0, 10, 0)

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("toolwindow.title"))
        titleLabel.font = titleLabel.font.deriveFont(18f).deriveFont(JBFont.BOLD)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        // 控制按钮面板
        val controlPanel = createControlPanel()
        headerPanel.add(controlPanel, BorderLayout.EAST)

        return headerPanel
    }

    /**
     * 创建控制按钮面板
     */
    private fun createControlPanel(): JPanel {
        val controlPanel = JPanel()
        controlPanel.layout = BoxLayout(controlPanel, BoxLayout.X_AXIS)

        // 刷新所有Tab按钮
        val refreshAllButton = JButton("刷新所有")
        refreshAllButton.toolTipText = "刷新所有Tab的内容"
        refreshAllButton.addActionListener {
            refreshAllTabs()
        }

        // 设置按钮（打开设置页面）
        val settingsButton = JButton("设置")
        settingsButton.toolTipText = "打开Nekoama设置"
        settingsButton.addActionListener {
            openSettings()
        }

        controlPanel.add(refreshAllButton)
        controlPanel.add(Box.createHorizontalStrut(5))
        controlPanel.add(settingsButton)

        return controlPanel
    }

    /**
     * 注册默认的Tab
     */
    private fun registerDefaultTabs() {
        scope.launch {
            try {
                // 注册Token统计Tab（重构现有的EnhancedNekoamaToolWindow）
                val tokenStatsTab = TokenStatsTab()
                tabManager.registerTab(tokenStatsTab)

                // 注册概览Tab（新的快速访问功能）
                val overviewTab = OverviewTab()
                tabManager.registerTab(overviewTab)

                NekoamaLogger.info("ModularToolWindow", "Default tabs registered successfully")
            } catch (e: Exception) {
                NekoamaLogger.error("ModularToolWindow", "Failed to register default tabs", error = e)
            }
        }
    }

    /**
     * 刷新所有Tab
     */
    private fun refreshAllTabs() {
        scope.launch {
            try {
                tabManager.refreshAllTabs()
                NekoamaLogger.debug("ModularToolWindow", "All tabs refreshed")
            } catch (e: Exception) {
                NekoamaLogger.error("ModularToolWindow", "Failed to refresh all tabs", error = e)
            }
        }
    }

    /**
     * 打开设置页面
     */
    private fun openSettings() {
        scope.launch {
            try {
                // 备用方案：显示设置信息
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "请在 File -> Settings -> Tools -> Nekoama 中配置插件设置",
                    "打开设置",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                NekoamaLogger.error("ModularToolWindow", "Failed to open settings", error = e)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "无法打开设置页面: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    /**
     * 获取主组件
     */
    fun getComponent(): JComponent = mainPanel

    /**
     * 释放资源
     */
    fun dispose() {
        try {
            scope.cancel()
            // TabManager由全局管理，这里不直接dispose
            NekoamaLogger.info("ModularToolWindow", "disposed")
        } catch (e: Exception) {
            NekoamaLogger.error("ModularToolWindow", "Error during disposal", error = e)
        }
    }
}