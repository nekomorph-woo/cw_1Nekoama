package com.cw2.nekoama.interfaces.intellij.toolwindow

import com.cw2.nekoama.domain.toolwindow.service.TabCoordinatorServiceImpl
import com.cw2.nekoama.infrastructure.toolwindow.InMemoryTabEventBus
import com.cw2.nekoama.infrastructure.toolwindow.InMemoryTabStateRepository
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Tool Window 工厂类
 *
 * 职责：
 * - 实现 IntelliJ ToolWindowFactory 接口
 * - 创建 Tool Window 内容
 * - 依赖注入组装所有组件
 *
 * 说明：这是框架的入口点，在 plugin.xml 中注册
 */
class NekoamaToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        NekoamaLogger.info("ToolWindow", "Creating Nekoama Tool Window")

        // 1. 创建基础设施层实例
        val eventBus = InMemoryTabEventBus()
        val stateRepository = InMemoryTabStateRepository()

        // 2. 创建领域服务
        val coordinatorService = TabCoordinatorServiceImpl(eventBus, stateRepository)

        // 3. 创建主内容面板
        val contentPanel = NekoamaToolWindowContent(
            project = project,
            coordinatorService = coordinatorService
        ).createContent()

        // 4. 注册到Tool Window
        toolWindow.contentManager.addContent(contentPanel)

        NekoamaLogger.info("ToolWindow", "Nekoama Tool Window created successfully")
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}
