package com.cw2.nekoama.interfaces.intellij.actions

import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/**
 * Dashboard 刷新动作
 *
 * 功能：
 * - 刷新 Dashboard Tab 数据
 * - 快捷键: Ctrl+Shift+R
 */
class DashboardRefreshAction : DumbAwareAction() {

    init {
        templatePresentation.text = NekoamaBundle.message("dashboard.button.refresh")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 获取 ToolWindow
        val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("Nekoama.Main")

        toolWindow?.contentManager?.let { contentManager ->
            val selectedContent = contentManager.selectedContent
            selectedContent?.let { content ->
                // 通过反射调用 performRefresh 方法
                val component = content.component
                tryInvokeRefresh(component)
            }
        }
    }

    private fun tryInvokeRefresh(component: java.awt.Component?) {
        if (component == null) return

        // 尝试通过反射调用 performRefresh 方法
        try {
            val method = component.javaClass.getDeclaredMethod("performRefresh")
            method.isAccessible = true
            method.invoke(component)
            return
        } catch (e: NoSuchMethodException) {
            // 方法不存在，继续查找子组件
        }

        // 递归查找子组件
        if (component is java.awt.Container) {
            for (child in component.components) {
                tryInvokeRefresh(child)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            .getToolWindow("Nekoama.Main")

        val isVisible = toolWindow?.isVisible == true
        e.presentation.isEnabled = isVisible
        e.presentation.isVisible = true
    }
}
