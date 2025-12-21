package com.cw2.nekoama.interfaces.intellij.tool_window.extension.example

import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.interfaces.intellij.tool_window.extension.AbstractTabExtension
import com.cw2.nekoama.interfaces.intellij.tool_window.tab.NekoamaTab
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.FormBuilder
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
    override val displayName: String = NekoamaBundle.message("demo.extension.name")
    override val description: String = NekoamaBundle.message("demo.extension.description")
    override val version: String = "1.0.0"
    override val icon: Icon? = AllIcons.General.Information
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
            "features" to NekoamaBundle.message("demo.config.features.status").split(", ").map { it.trim() }
        )
    }

    /**
     * 演示Tab实现
     */
    inner class DemoTab : NekoamaTab {

        private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
        private val statusLabel = JBLabel(NekoamaBundle.message("demo.status.running"))
        private val infoLabel = JBLabel()
        private val actionButton = JButton(NekoamaBundle.message("demo.button.clickTest"))
        private val refreshButton = JButton(NekoamaBundle.message("demo.button.refresh"))
        private var clickCount = 0

        init {
            setupUI()
            NekoamaLogger.debug("DemoTab", "Demo tab created")
        }

        private fun setupUI() {
            // 创建信息面板
            val infoPanel = FormBuilder.createFormBuilder()
                .addComponent(JBLabel(NekoamaBundle.message("demo.label.extensionInfo")))
                .addLabeledComponent(JBLabel(NekoamaBundle.message("demo.label.extensionId")), JBLabel(extensionId))
                .addLabeledComponent(JBLabel(NekoamaBundle.message("demo.label.version")), JBLabel(version))
                .addLabeledComponent(JBLabel(NekoamaBundle.message("demo.label.description")), JBLabel(description))
                .addComponent(JBLabel(NekoamaBundle.message("demo.label.interactiveFeatures")))
                .addComponent(actionButton, 1)
                .addComponent(refreshButton, 1)
                .addComponent(statusLabel)
                .addComponent(infoLabel)
                .panel

            // 设置按钮事件
            actionButton.addActionListener {
                clickCount++
                statusLabel.text = NekoamaBundle.message("demo.clickCount", clickCount)
                infoLabel.text = NekoamaBundle.message("demo.lastOperation", NekoamaBundle.message("demo.button.clickTest"), getCurrentTime())
                NekoamaLogger.debug("DemoTab", "Button clicked, count: $clickCount")
            }

            refreshButton.addActionListener {
                refresh()
            }

            // 添加到主面板
            mainPanel.add(infoPanel, BorderLayout.CENTER)

            // 添加底部状态栏
            val statusPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT))
            statusPanel.border = BorderFactory.createTitledBorder(NekoamaBundle.message("demo.label.statusInfo"))
            statusPanel.add(statusLabel)
            mainPanel.add(statusPanel, BorderLayout.SOUTH)
        }

        override val tabId: String = NekoamaBundle.message("demo.tab.id.demo")
        override val displayName: String = NekoamaBundle.message("demo.tab.displayName")
        override val icon: Icon? = this@DemoTabExtension.icon
        override val tooltip: String? = NekoamaBundle.message("demo.tab.tooltip")
        override val isCloseable: Boolean = false

        override fun getComponent(): JComponent = mainPanel

        override fun onTabActivated() {
            super.onTabActivated()
            statusLabel.text = NekoamaBundle.message("demo.status.activated") + " (click count: $clickCount)"
            infoLabel.text = NekoamaBundle.message("demo.activationTime", getCurrentTime())
            NekoamaLogger.debug("DemoTab", "Demo tab activated")
        }

        override fun onTabDeactivated() {
            super.onTabDeactivated()
            statusLabel.text = NekoamaBundle.message("demo.status.deactivated")
            NekoamaLogger.debug("DemoTab", "Demo tab deactivated")
        }

        override fun refresh() {
            super.refresh()
            infoLabel.text = NekoamaBundle.message("demo.refreshTime", getCurrentTime())
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