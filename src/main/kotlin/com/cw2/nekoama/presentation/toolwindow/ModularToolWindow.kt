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
import java.util.Locale
import javax.swing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import java.awt.event.ActionEvent

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
        // 设置Locale为英文，确保弹窗按钮显示为"OK"
        Locale.setDefault(Locale.ENGLISH)

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
                tooltip = NekoamaBundle.message("toolbar.button.refresh.tooltip"),
                action = { refreshAllTabs() }
            ),
            createToolbarButton(
                icon = AllIcons.General.Information,
                tooltip = NekoamaBundle.message("toolbar.button.extension.info.tooltip"),
                action = { showExtensionInfo() }
            ),
            createToolbarButton(
                icon = AllIcons.General.Settings,
                tooltip = NekoamaBundle.message("toolbar.button.settings.tooltip"),
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
                showLoadingState(NekoamaBundle.message("toolbar.refreshing.status"))

                tabManager.refreshAllTabs()

                // 延迟显示完成状态
                kotlinx.coroutines.delay(500)
                showSuccessState(NekoamaBundle.message("toolbar.refresh.success"))

                NekoamaLogger.debug("ModularToolWindow", "All tabs refreshed")
            } catch (e: Exception) {
                NekoamaLogger.error("ModularToolWindow", "Failed to refresh all tabs", error = e)
                showErrorState(NekoamaBundle.message("toolbar.refresh.failed", e.message ?: ""))
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
            NekoamaBundle.message("toolbar.dialog.loading.title"),
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
            NekoamaBundle.message("toolbar.dialog.success.title"),
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
            NekoamaBundle.message("toolbar.dialog.error.title"),
            JOptionPane.ERROR_MESSAGE,
            AllIcons.General.Error
        )
    }

    /**
     * 打开设置页面
     */
    private fun openSettings() {
        // 确保在 EDT 上执行
        ApplicationManager.getApplication().invokeLater {
            try {
                val contentMessage = NekoamaBundle.message("settings.info.content")
                val itemsMessage = NekoamaBundle.message("settings.info.configurable.items").replace("\\n", "\n")
                val message = contentMessage + "\n\n" + itemsMessage

                NekoamaLogger.debug("ModularToolWindow", "Showing settings dialog")

                // 使用标准的 showConfirmDialog 而不是复杂的自定义按钮方案
                val result = JOptionPane.showConfirmDialog(
                    mainPanel,
                    message,
                    NekoamaBundle.message("settings.info.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    AllIcons.General.Settings
                )

                NekoamaLogger.debug("ModularToolWindow", "Settings dialog result: $result")

                // YES_OPTION 对应第一个按钮（Yes/去设置），NO_OPTION 对应第二个按钮（No/取消）
                if (result == JOptionPane.YES_OPTION) {
                    openNekoamaSettings()
                }
            } catch (e: Exception) {
                NekoamaLogger.error("ModularToolWindow", "Failed to open settings", error = e)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    NekoamaBundle.message("settings.info.error.failed", e.message ?: ""),
                    NekoamaBundle.message("toolbar.dialog.error.title"),
                    JOptionPane.ERROR_MESSAGE,
                    AllIcons.General.Error
                )
            }
        }
    }

    /**
     * 打开Nekoama设置页面
     */
    private fun openNekoamaSettings() {
        try {
            NekoamaLogger.debug("ModularToolWindow", "Attempting to open Nekoama settings")

            // 获取当前项目
            val project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()

            if (project != null) {
                NekoamaLogger.debug("ModularToolWindow", "Found project: ${project.name}, opening settings")

                // 修复：使用正确的设置页面ID "Nekoama.settings"
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "Nekoama.settings")
                NekoamaLogger.info("ModularToolWindow", "Successfully opened Nekoama settings dialog")
            } else {
                NekoamaLogger.warn("ModularToolWindow", "No open project found")

                // 如果没有打开的项目，显示错误信息
                ApplicationManager.getApplication().invokeLater {
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        NekoamaBundle.message("settings.info.no.project"),
                        NekoamaBundle.message("toolbar.dialog.error.title"),
                        JOptionPane.ERROR_MESSAGE,
                        AllIcons.General.Error
                    )
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.error("ModularToolWindow", "Failed to open Nekoama settings", error = e)
            val errorMessage = NekoamaBundle.message("settings.info.error.failed", e.message ?: "")

            ApplicationManager.getApplication().invokeLater {
                JOptionPane.showMessageDialog(
                    mainPanel,
                    errorMessage,
                    NekoamaBundle.message("toolbar.dialog.error.title"),
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
                    appendLine(NekoamaBundle.message("extension.info.header"))
                    appendLine("=" .repeat(30))
                    appendLine()
                    appendLine(NekoamaBundle.message("extension.info.statistics.title"))
                    appendLine("  ${NekoamaBundle.message("extension.info.total.extensions", stats["totalExtensions"] ?: "0")}")
                    appendLine("  ${NekoamaBundle.message("extension.info.enabled.extensions", stats["enabledExtensions"] ?: "0")}")
                    appendLine("  ${NekoamaBundle.message("extension.info.registered.extensions", stats["registeredExtensions"] ?: "0")}")
                    appendLine()
                    appendLine(NekoamaBundle.message("extension.info.list.title"))
                    @Suppress("UNCHECKED_CAST")
                    val extensionIds = stats["extensionIds"] as? List<String> ?: emptyList()
                    if (extensionIds.isEmpty()) {
                        appendLine("  ${NekoamaBundle.message("extension.info.no.extensions")}")
                    } else {
                        extensionIds.forEach { id ->
                            appendLine("  • $id")
                        }
                    }
                    appendLine()
                    appendLine(NekoamaBundle.message("extension.info.system.title"))
                    appendLine("  ${NekoamaBundle.message("extension.info.system.version", "1.1.0")}")
                    appendLine("  ${NekoamaBundle.message("extension.info.system.features")}")
                }

                JOptionPane.showMessageDialog(
                    mainPanel,
                    message,
                    NekoamaBundle.message("extension.info.dialog.title"),
                    JOptionPane.INFORMATION_MESSAGE,
                    AllIcons.General.Information
                )

                NekoamaLogger.debug("ModularToolWindow", "Extension info displayed")
            } catch (e: Exception) {
                NekoamaLogger.error("ModularToolWindow", "Failed to show extension info", error = e)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    NekoamaBundle.message("extension.info.error.failed", e.message ?: ""),
                    NekoamaBundle.message("toolbar.dialog.error.title"),
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