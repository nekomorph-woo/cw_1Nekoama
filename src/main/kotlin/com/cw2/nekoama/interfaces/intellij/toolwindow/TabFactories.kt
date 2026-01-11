package com.cw2.nekoama.interfaces.intellij.toolwindow

import com.cw2.nekoama.interfaces.intellij.toolwindow.tabs.DashboardTab
import com.cw2.nekoama.interfaces.intellij.toolwindow.tabs.WelcomeTab

/**
 * Tab工厂注册表
 *
 * 说明：
 * - 这是内部扩展机制的实现
 * - 新增Tab只需在此处添加工厂
 * - 无需修改其他代码
 */
object TabFactories {
    /**
     * 所有Tab工厂（在此处注册）
     *
     * 扩展方式：
     * 1. 创建新的Tab类（继承 BaseTab）
     * 2. 创建工厂实例
     * 3. 添加到此列表
     */
    val all: List<TabFactory> = listOf(
        // DashboardTab（统计面板）
        TabFactory { project, coordinator ->
            DashboardTab(project, coordinator)
        },

        // WelcomeTab（示例Tab）
        TabFactory { project, coordinator ->
            WelcomeTab(project, coordinator)
        }

        // 未来扩展示例：
        // TabFactory { project, coordinator ->
        //     AIDialogTab(project, coordinator)
        // },
        // TabFactory { project, coordinator ->
        //     CodeAnalysisTab(project, coordinator)
        // }
    )
}
