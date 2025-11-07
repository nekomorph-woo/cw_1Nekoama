package com.cw2.nekoama.presentation.toolwindow

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.cw2.nekoama.presentation.toolwindow.tab.NekoamaTabManager
import com.cw2.nekoama.presentation.toolwindow.tab.TokenStatsTab
import com.cw2.nekoama.presentation.toolwindow.tab.OverviewTab
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import com.intellij.ui.Gray
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * 模块化的Nekoama工具窗口
 *
 * 使用Tab管理器来管理各个功能模块，支持动态Tab加载和状态保持。
 * 这是重构后的主要工具窗口实现。
 */
class ModularToolWindow {

    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tabManager = NekoamaTabManager.getInstance()

    // 性能优化：缓存按钮组件避免重复创建
    private var cachedButtons: List<JButton>? = null

    init {
        setupUI()
        registerDefaultTabs()
        NekoamaLogger.info("ModularToolWindow", "initialized")
    }

    /**
     * 设置UI布局
     */
    private fun setupUI() {
        mainPanel.border = JBEmptyBorder(JBUI.insets(10))

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
    private fun createHeaderPanel(): JBPanel<JBPanel<*>> {
        val headerPanel = JBPanel<JBPanel<*>>(BorderLayout())
        headerPanel.border = JBEmptyBorder(JBUI.insets(0, 0, 10, 0))

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("toolwindow.title"))
        titleLabel.font = JBFont.label().asBold()
        titleLabel.icon = AllIcons.General.Settings
        headerPanel.add(titleLabel, BorderLayout.WEST)

        // 控制按钮面板
        val controlPanel = createControlPanel()
        headerPanel.add(controlPanel, BorderLayout.EAST)

        return headerPanel
    }

    /**
     * 创建控制按钮面板（带性能优化）
     */
    private fun createControlPanel(): JBPanel<JBPanel<*>> {
        val controlPanel = JBPanel<JBPanel<*>>()
        controlPanel.layout = BoxLayout(controlPanel, BoxLayout.X_AXIS)

        // 性能优化：使用缓存的按钮组件
        val buttons = cachedButtons ?: createButtonList().also {
            cachedButtons = it
        }

        buttons.forEach { button ->
            controlPanel.add(button)
            controlPanel.add(Box.createHorizontalStrut(JBUI.scale(4)))
        }

        return controlPanel
    }

    /**
     * 创建按钮列表（缓存用）
     */
    private fun createButtonList(): List<JButton> {
        return listOf(
            createToolbarButton(
                icon = AllIcons.Actions.Refresh,
                tooltip = "刷新所有Tab的内容",
                action = { refreshAllTabs() }
            ),
            createToolbarButton(
                icon = AllIcons.General.Information,
                tooltip = "查看扩展系统信息",
                action = { showExtensionInfo() }
            ),
            createToolbarButton(
                icon = AllIcons.General.Settings,
                tooltip = "打开Nekoama设置",
                action = { openSettings() }
            )
        )
    }

    /**
     * 创建工具栏按钮
     */
    private fun createToolbarButton(
        icon: Icon,
        tooltip: String,
        action: () -> Unit
    ): JButton {
        val button = JButton(icon)
        button.toolTipText = tooltip
        button.isFocusable = false
        button.margin = JBUI.insets(2)
        button.putClientProperty("JButton.buttonType", "square")
        button.addActionListener { action() }

        // 设置统一的按钮尺寸
        val size = JBUI.size(24, 24)
        button.preferredSize = size
        button.minimumSize = size
        button.maximumSize = size

        // 添加悬停效果
        addHoverEffect(button)

        return button
    }

    /**
     * 添加按钮悬停效果
     */
    private fun addHoverEffect(button: JButton) {
        val originalIcon = button.icon

        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                button.border = BorderFactory.createLineBorder(Gray._225, 1)
                button.background = UIUtil.getPanelBackground().brighter()
            }

            override fun mouseExited(e: MouseEvent) {
                button.border = null
                button.background = UIUtil.getPanelBackground()
            }

            override fun mousePressed(e: MouseEvent) {
                button.icon = AllIcons.Process.Step_1 // 临时使用加载图标
            }

            override fun mouseReleased(e: MouseEvent) {
                button.icon = originalIcon
            }
        })
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
                // 显示加载状态
                showLoadingState("正在刷新所有Tab...")

                tabManager.refreshAllTabs()

                // 延迟显示完成状态
                kotlinx.coroutines.delay(500)
                showSuccessState("所有Tab刷新完成")

                NekoamaLogger.debug("ModularToolWindow", "All tabs refreshed")
            } catch (e: Exception) {
                NekoamaLogger.error("ModularToolWindow", "Failed to refresh all tabs", error = e)
                showErrorState("刷新失败: ${e.message}")
            }
        }
    }

    /**
     * 显示加载状态
     */
    private fun showLoadingState(message: String) {
        val loadingLabel = JBLabel("$message...", AllIcons.General.Information, SwingConstants.LEADING)

        JOptionPane.showMessageDialog(
            mainPanel,
            loadingLabel,
            "加载中",
            JOptionPane.INFORMATION_MESSAGE,
            AllIcons.General.Information
        )
    }

    /**
     * 显示成功状态
     */
    private fun showSuccessState(message: String) {
        JOptionPane.showMessageDialog(
            mainPanel,
            message,
            "成功",
            JOptionPane.INFORMATION_MESSAGE,
            AllIcons.General.InspectionsOK
        )
    }

    /**
     * 显示错误状态
     */
    private fun showErrorState(message: String) {
        JOptionPane.showMessageDialog(
            mainPanel,
            message,
            "错误",
            JOptionPane.ERROR_MESSAGE,
            AllIcons.General.Error
        )
    }

    /**
     * 打开设置页面
     */
    private fun openSettings() {
        scope.launch {
            try {
                // 备用方案：显示设置信息
                val message = """
                    请在 File -> Settings -> Tools -> Nekoama 中配置插件设置

                    可配置项：
                    • API 密钥配置
                    • AI 服务提供商选择
                    • Token 使用限制
                    • 自动保存设置
                """.trimIndent()

                JOptionPane.showMessageDialog(
                    mainPanel,
                    message,
                    "Nekoama 设置",
                    JOptionPane.INFORMATION_MESSAGE,
                    AllIcons.General.Settings
                )
            } catch (e: Exception) {
                NekoamaLogger.error("ModularToolWindow", "Failed to open settings", error = e)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "无法打开设置页面: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE,
                    AllIcons.General.Error
                )
            }
        }
    }

    /**
     * 获取主组件
     */
    fun getComponent(): JComponent = mainPanel

    /**
     * 显示扩展系统信息
     */
    private fun showExtensionInfo() {
        scope.launch {
            try {
                val stats = tabManager.getExtensionStats()
                val message = buildString {
                    appendLine("扩展系统状态信息")
                    appendLine("=" .repeat(30))
                    appendLine()
                    appendLine("📊 统计信息:")
                    appendLine("  已注册扩展总数: ${stats["totalExtensions"]}")
                    appendLine("  已启用扩展数量: ${stats["enabledExtensions"]}")
                    appendLine("  扩展点注册数量: ${stats["registeredExtensions"]}")
                    appendLine()
                    appendLine("📦 扩展列表:")
                    @Suppress("UNCHECKED_CAST")
                    val extensionIds = stats["extensionIds"] as? List<String> ?: emptyList()
                    if (extensionIds.isEmpty()) {
                        appendLine("  (暂无扩展)")
                    } else {
                        extensionIds.forEach { id ->
                            appendLine("  • $id")
                        }
                    }
                    appendLine()
                    appendLine("🔧 系统信息:")
                    appendLine("  扩展系统版本: 1.0.0")
                    appendLine("  支持功能: 动态加载、配置管理、事件通信")
                }

                JOptionPane.showMessageDialog(
                    mainPanel,
                    message,
                    "扩展系统信息",
                    JOptionPane.INFORMATION_MESSAGE,
                    AllIcons.General.Information
                )

                NekoamaLogger.debug("ModularToolWindow", "Extension info displayed")
            } catch (e: Exception) {
                NekoamaLogger.error("ModularToolWindow", "Failed to show extension info", error = e)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "无法获取扩展信息: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE,
                    AllIcons.General.Error
                )
            }
        }
    }

    /**
     * 释放资源（带性能优化）
     */
    fun dispose() {
        try {
            // 性能优化：清理缓存的按钮组件
            cachedButtons = null

            // 取消所有协程任务
            scope.cancel()

            // TabManager由全局管理，这里不直接dispose
            NekoamaLogger.info("ModularToolWindow", "disposed with performance optimizations")
        } catch (e: Exception) {
            NekoamaLogger.error("ModularToolWindow", "Error during disposal", error = e)
        }
    }
}