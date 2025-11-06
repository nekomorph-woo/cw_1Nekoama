package com.cw2.nekoama.presentation.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * 工具窗口工厂（最小实现）
 *
 * 说明：
 * - 提供一个欢迎面板，后续再扩展统计/状态/日志等子面板
 */
class NekoamaToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 使用功能完整的工具窗口，包含统计、分析、历史数据等所有功能
        val fullFeaturedToolWindow = FullFeaturedToolWindow()
        val component = fullFeaturedToolWindow.getComponent()
        val content = ContentFactory.getInstance().createContent(component, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
