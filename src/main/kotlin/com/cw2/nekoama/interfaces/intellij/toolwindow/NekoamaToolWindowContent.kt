package com.cw2.nekoama.interfaces.intellij.toolwindow

import com.cw2.nekoama.domain.toolwindow.service.TabCoordinatorService
import com.cw2.nekoama.infrastructure.toolwindow.TabThemeManager
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * Tool Window 主内容面板
 *
 * 职责：
 * - 创建UI组件（JTabbedPane）
 * - 注册所有Tab工厂
 * - 管理 Tab 切换生命周期
 * - 委托 TabCoordinatorService 管理事件和状态
 *
 * 说明：遵循 Swing UI 规范，所有UI操作在EDT执行
 */
class NekoamaToolWindowContent(
    private val project: Project,
    private val coordinatorService: TabCoordinatorService
) {
    private val tabbedPane = JTabbedPane()

    // 维护 Tab 列表（按索引顺序）
    private val tabs = mutableListOf<BaseTab>()

    // 当前激活的 Tab 索引
    private var currentTabIndex = -1

    fun createContent(): Content {
        NekoamaLogger.info("ToolWindowContent", "Creating Tool Window content")

        // 1. 注册所有Tab
        registerTabs()

        // 2. 注册 Tab 切换监听器
        registerTabChangeListener()

        // 3. 激活第一个Tab（如果存在）
        if (tabs.isNotEmpty()) {
            handleTabSwitch(-1, 0)
            currentTabIndex = 0
            NekoamaLogger.info("ToolWindowContent", "Activated first tab: ${tabs[0].metadata.id.value}")
        }

        // 4. 创建主面板
        val mainPanel = JPanel(BorderLayout()).apply {
            background = TabThemeManager.getTabBackgroundColor()
            add(createToolbar(), BorderLayout.NORTH)
            add(tabbedPane, BorderLayout.CENTER)
        }

        // 5. 返回Content对象
        val content = ContentFactory.getInstance().createContent(
            mainPanel,
            "",
            false
        )

        NekoamaLogger.info("ToolWindowContent", "Tool Window content created")
        return content
    }

    private fun registerTabs() {
        NekoamaLogger.info("ToolWindowContent", "Registering tabs")

        // 从 TabFactories 获取所有Tab工厂并注册
        TabFactories.all.forEach { factory ->
            val tab = factory.create(project, coordinatorService)
            val component = tab.createComponent()

            // 添加Tab到JTabbedPane
            tabbedPane.addTab(tab.metadata.displayName, tab.metadata.icon, component)

            // 维护 Tab 列表
            tabs.add(tab)

            NekoamaLogger.info("ToolWindowContent", "Registered tab: ${tab.metadata.id.value}")
        }
    }

    private fun registerTabChangeListener() {
        tabbedPane.addChangeListener(object : ChangeListener {
            override fun stateChanged(e: ChangeEvent) {
                val newIndex = tabbedPane.selectedIndex

                // 如果索引发生变化，处理 Tab 切换
                if (newIndex != currentTabIndex && newIndex >= 0) {
                    handleTabSwitch(currentTabIndex, newIndex)
                    currentTabIndex = newIndex
                }
            }
        })

        NekoamaLogger.info("ToolWindowContent", "Tab change listener registered")
    }

    private fun handleTabSwitch(oldIndex: Int, newIndex: Int) {
        // 1. 失活旧 Tab
        if (oldIndex >= 0 && oldIndex < tabs.size) {
            val oldTab = tabs[oldIndex]
            oldTab.deactivate()
            NekoamaLogger.info("ToolWindowContent", "Deactivated tab: ${oldTab.metadata.id.value}")
        }

        // 2. 激活新 Tab
        if (newIndex >= 0 && newIndex < tabs.size) {
            val newTab = tabs[newIndex]
            newTab.activate()

            // 3. 通过协调服务发布激活事件
            coordinatorService.activateTab(newTab.metadata.id)

            NekoamaLogger.info("ToolWindowContent", "Activated tab: ${newTab.metadata.id.value}")
        }
    }

    private fun createToolbar(): JComponent {
        // 创建工具栏（可选）
        return JPanel().apply {
            border = EmptyBorder(5, 5, 5, 5)
            background = TabThemeManager.getTabBackgroundColor()
            preferredSize = java.awt.Dimension(0, 30)
        }
    }
}
