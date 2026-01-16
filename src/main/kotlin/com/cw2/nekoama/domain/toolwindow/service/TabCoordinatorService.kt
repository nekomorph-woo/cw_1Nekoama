package com.cw2.nekoama.domain.toolwindow.service

import com.cw2.nekoama.domain.toolwindow.model.TabState
import com.cw2.nekoama.domain.toolwindow.model.TabMetadata
import com.cw2.nekoama.domain.toolwindow.repository.TabStateRepository

/**
 * Tab协调服务接口
 *
 * 职责：
 * - 管理Tab生命周期
 * - 协调Tab间的切换
 * - 集成事件总线和状态持久化
 */
interface TabCoordinatorService {
    /**
     * 事件总线实例
     */
    val eventBus: TabEventBus

    /**
     * 状态存储仓库（暴露给 Tab 使用）
     */
    val stateRepository: TabStateRepository

    /**
     * 激活Tab（用户切换）
     *
     * 流程：
     * 1. 触发当前activeTab的 onDeactivated()
     * 2. 保存当前Tab状态到 repository
     * 3. 切换到新Tab
     * 4. 从 repository 加载新Tab状态
     * 5. 触发新Tab的 onActivated()
     * 6. 发布 TabActivated 事件
     *
     * @param tabId Tab唯一标识
     */
    fun activateTab(tabId: TabMetadata.TabId)

    /**
     * 保存Tab状态
     *
     * @param tabId Tab唯一标识
     * @param state 状态对象
     */
    fun saveTabState(tabId: String, state: TabState)
}
