package com.cw2.nekoama.interfaces.intellij.toolwindow

import com.cw2.nekoama.domain.toolwindow.service.TabCoordinatorService
import com.intellij.openapi.project.Project

/**
 * Tab工厂接口
 *
 * 职责：
 * - 定义Tab创建契约
 * - 支持依赖注入
 * - 实现内部扩展机制
 */
fun interface TabFactory {
    /**
     * 创建Tab实例
     *
     * @param project IntelliJ Project
     * @param coordinatorService Tab协调服务
     * @return Tab实例
     */
    fun create(project: Project, coordinatorService: TabCoordinatorService): BaseTab
}
