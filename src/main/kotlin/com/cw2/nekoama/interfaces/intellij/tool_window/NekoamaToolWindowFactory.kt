package com.cw2.nekoama.interfaces.intellij.tool_window

import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Nekoama工具窗口工厂
 *
 * 使用新的模块化架构，支持Tab管理和状态保持。
 */
class NekoamaToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        try {
            // 使用新的模块化工具窗口
            val modularToolWindow = ModularToolWindow()
            val component = modularToolWindow.getComponent()
            val content = ContentFactory.getInstance().createContent(component, null, false)
            toolWindow.contentManager.addContent(content)

            NekoamaLogger.info("NekoamaToolWindowFactory", "Nekoama tool window created successfully")
        } catch (e: Exception) {
            NekoamaLogger.error("NekoamaToolWindowFactory", "Failed to create Nekoama tool window", error = e)

            // 备用方案：使用旧的工具窗口
            try {
                val fallbackWindow = FullFeaturedToolWindow()
                val component = fallbackWindow.getComponent()
                val content = ContentFactory.getInstance().createContent(component, null, false)
                toolWindow.contentManager.addContent(content)

                NekoamaLogger.warn("NekoamaToolWindowFactory", "Fallback to FullFeaturedToolWindow due to ModularToolWindow failure")
            } catch (fallbackError: Exception) {
                NekoamaLogger.error("NekoamaToolWindowFactory", "Failed to create fallback tool window", error = fallbackError)
            }
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
