package com.cw2.nekoama.presentation.toolwindow.extension.example

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.presentation.toolwindow.extension.AbstractTabExtension
import com.cw2.nekoama.presentation.toolwindow.tab.NekoamaTab
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*

/**
 * 演示Tab扩展
 *
 * 这是一个示例扩展，展示了如何创建自定义Tab。
 */
class DemoTabExtension : AbstractTabExtension() {

    override val extensionId: String = "com.cw2.nekoama.demo"
    override val displayName: String = "演示扩展"
    override val description: String = "这是一个演示扩展，展示Tab扩展系统的功能"
    override val version: String = "1.0.0"
    override val icon: javax.swing.Icon? = AllIcons.General.Information
    override val priority: Int = 200

    private var isInitialized = false

    override fun initialize() {
        super.initialize()
        isInitialized = true
        NekoamaLogger.info("DemoTabExtension", "Demo extension initialized")
    }

    override fun dispose() {
        super.dispose()
        isInitialized = false
        NekoamaLogger.info("DemoTabExtension", "Demo extension disposed")
    }

    override fun createTab(): NekoamaTab {
        return DemoTab()
    }

    override fun getConfiguration(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized,
            "creationTime" to System.currentTimeMillis(),
            "features" to listOf("状态显示", "交互功能", "配置示例")
        )
    }

    /**
     * 演示Tab实现
     */
    inner class DemoTab : NekoamaTab {

        private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
        private val statusLabel = JBLabel("扩展状态: 运行中")
        private val infoLabel = JBLabel()
        private val actionButton = JButton("点击测试")
        private val refreshButton = JButton("刷新信息")
        private var clickCount = 0

        init {
            setupUI()
            NekoamaLogger.debug("DemoTab", "Demo tab created")
        }

        private fun setupUI() {
            // 创建信息面板
            val infoPanel = FormBuilder.createFormBuilder()
                .addComponent(JBLabel("扩展信息:"))
                .addLabeledComponent(JBLabel("扩展ID:"), JBLabel(extensionId))
                .addLabeledComponent(JBLabel("版本:"), JBLabel(version))
                .addLabeledComponent(JBLabel("描述:"), JBLabel(description))
                .addComponent(JBLabel("交互功能:"))
                .addComponent(actionButton, 1)
                .addComponent(refreshButton, 1)
                .addComponent(statusLabel)
                .addComponent(infoLabel)
                .panel

            // 设置按钮事件
            actionButton.addActionListener {
                clickCount++
                statusLabel.text = "点击次数: $clickCount"
                infoLabel.text = "最后操作: 点击测试按钮 - ${getCurrentTime()}"
                NekoamaLogger.debug("DemoTab", "Button clicked, count: $clickCount")
            }

            refreshButton.addActionListener {
                refresh()
            }

            // 添加到主面板
            mainPanel.add(infoPanel, BorderLayout.CENTER)

            // 添加底部状态栏
            val statusPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT))
            statusPanel.border = BorderFactory.createTitledBorder("状态信息")
            statusPanel.add(statusLabel)
            mainPanel.add(statusPanel, BorderLayout.SOUTH)
        }

        override val tabId: String = "demo_tab"
        override val displayName: String = "演示"
        override val icon: javax.swing.Icon? = this@DemoTabExtension.icon
        override val tooltip: String? = "演示扩展功能展示"
        override val isCloseable: Boolean = false

        override fun getComponent(): JComponent = mainPanel

        override fun onTabActivated() {
            super.onTabActivated()
            statusLabel.text = "扩展状态: 已激活 (点击次数: $clickCount)"
            infoLabel.text = "激活时间: ${getCurrentTime()}"
            NekoamaLogger.debug("DemoTab", "Demo tab activated")
        }

        override fun onTabDeactivated() {
            super.onTabDeactivated()
            statusLabel.text = "扩展状态: 已停用"
            NekoamaLogger.debug("DemoTab", "Demo tab deactivated")
        }

        override fun refresh() {
            super.refresh()
            infoLabel.text = "刷新时间: ${getCurrentTime()}"
            NekoamaLogger.debug("DemoTab", "Demo tab refreshed")
        }

        override fun getTabState(): Map<String, Any> {
            return mapOf(
                "clickCount" to clickCount,
                "lastAction" to (infoLabel.text ?: ""),
                "status" to (statusLabel.text ?: ""),
                "refreshTime" to System.currentTimeMillis()
            )
        }

        override fun restoreTabState(state: Map<String, Any>) {
            super.restoreTabState(state)
            clickCount = (state["clickCount"] as? Int) ?: 0
            val lastAction = state["lastAction"] as? String
            if (lastAction != null) {
                infoLabel.text = lastAction
            }
            NekoamaLogger.debug("DemoTab", "Demo tab state restored")
        }

        override fun dispose() {
            super.dispose()
            NekoamaLogger.debug("DemoTab", "Demo tab disposed")
        }

        private fun getCurrentTime(): String {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }
    }
}