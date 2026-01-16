package com.cw2.nekoama.interfaces.intellij.toolwindow.tabs

import com.cw2.nekoama.domain.toolwindow.model.TabMetadata
import com.cw2.nekoama.domain.toolwindow.model.TabState
import com.cw2.nekoama.domain.toolwindow.service.TabCoordinatorService
import com.cw2.nekoama.infrastructure.toolwindow.TabThemeManager
import com.cw2.nekoama.interfaces.intellij.toolwindow.BaseTab
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 欢迎Tab（示例实现）
 *
 * 职责：
 * - 演示如何实现BaseTab
 * - 显示欢迎信息和使用说明
 * - 作为未来Tab实现的参考模板
 */
class WelcomeTab(
    project: com.intellij.openapi.project.Project,
    coordinatorService: TabCoordinatorService
) : BaseTab(project, coordinatorService) {

    override val metadata = TabMetadata(
        id = TabMetadata.TabId("welcome"),
        displayName = "Welcome",
        icon = com.intellij.icons.AllIcons.General.Information
    )

    override val stateType = WelcomeTabState::class

    private var state: WelcomeTabState? = null

    override fun createComponentImpl(): JPanel {
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        // 标题
        val titleLabel = JLabel("欢迎使用 Nekoama").apply {
            font = font.deriveFont(Font.BOLD, 18f)
            foreground = TabThemeManager.getTabTextColor()
            alignmentX = 0f
        }
        mainPanel.add(titleLabel)
        mainPanel.add(JPanel().apply {
            preferredSize = java.awt.Dimension(0, 10)
        })

        // 内容
        val contentText = JBTextArea(
            """
            |Nekoama 是一款 AI 驱动的智能代码助手。
            |
            |当前功能：
            |• Name for Any - 智能命名建议
            |• Comment for Me - AI 驱动注释生成
            |• IDEA for Neko - 自定义代码生成
            |
            |未来功能（敬请期待）：
            |• AI 对话助手
            |• 代码质量分析
            |• 代码气味检测
            |
            |---
            |Tool Window Framework v1.0
            |支持扩展的侧边弹窗系统
            """.trimMargin()
        ).apply {
            isEditable = false
            background = TabThemeManager.getTabBackgroundColor()
            foreground = TabThemeManager.getTabTextColor()
            border = null
            lineWrap = true
            wrapStyleWord = true
            alignmentX = 0f
        }
        mainPanel.add(contentText)

        return mainPanel
    }

    override fun onActivated() {
        // 演示：激活时加载状态
        state = loadState(WelcomeTabState::class)
        NekoamaLogger.info("WelcomeTab", "Tab activated", mapOf("state" to (state?.toString() ?: "null")))
    }

    override fun onDeactivated() {
        // 演示：失活时保存状态
        val newState = WelcomeTabState(lastVisited = System.currentTimeMillis())
        saveState(newState)
        NekoamaLogger.info("WelcomeTab", "Tab deactivated", mapOf("state" to newState.toString()))
    }
}

/**
 * Welcome Tab 状态数据
 *
 * 说明：
 * - 实现 TabState 接口
 * - 框架不关心具体内容
 * - 可以扩展任意字段
 */
data class WelcomeTabState(
    val lastVisited: Long = System.currentTimeMillis()
) : TabState {
    override fun validate(): com.cw2.nekoama.shared.model.NekoamaResult<Unit> {
        return if (lastVisited > 0) {
            com.cw2.nekoama.shared.model.NekoamaResult.success(Unit)
        } else {
            com.cw2.nekoama.shared.model.NekoamaResult.error(
                com.cw2.nekoama.shared.exception.NekoamaError.Unknown("lastVisited must be positive")
            )
        }
    }
}
