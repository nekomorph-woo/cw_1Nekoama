package com.cw2.nekoama.domain.toolwindow.service

import com.cw2.nekoama.domain.toolwindow.model.TabEvent
import com.cw2.nekoama.domain.toolwindow.model.TabMetadata
import com.cw2.nekoama.domain.toolwindow.model.TabState
import com.cw2.nekoama.domain.toolwindow.repository.TabStateRepository

/**
 * Tab协调服务实现
 *
 * 职责：
 * - 管理Tab生命周期
 * - 协调Tab间的切换
 * - 集成事件总线和状态持久化
 */
class TabCoordinatorServiceImpl(
    override val eventBus: TabEventBus,
    override val stateRepository: TabStateRepository
) : TabCoordinatorService {

    private var _activeTabId: TabMetadata.TabId? = null
    val activeTabId: TabMetadata.TabId? get() = _activeTabId

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
    override fun activateTab(tabId: TabMetadata.TabId) {
        // 1. 发布Tab失活事件（如果有当前激活的Tab）
        _activeTabId?.let { currentTabId ->
            eventBus.publish(TabEvent.TabDeactivated(currentTabId))
        }

        // 2. 切换到新Tab
        _activeTabId = tabId

        // 3. 发布Tab激活事件
        eventBus.publish(TabEvent.TabActivated(tabId))
    }

    /**
     * 保存Tab状态
     *
     * @param tabId Tab唯一标识
     * @param state 状态对象
     */
    override fun saveTabState(tabId: String, state: TabState) {
        stateRepository.saveState(tabId, state)
    }
}

/**
 * 类型安全的Tab状态获取扩展函数
 *
 * 使用 inline reified 泛型确保类型安全
 *
 * @param T Tab状态类型
 * @param tabId Tab唯一标识
 * @return 状态对象，如果不存在返回null
 */
inline fun <reified T : TabState> TabCoordinatorService.loadTabState(tabId: TabMetadata.TabId): T? {
    return stateRepository.loadState(tabId.value, T::class)
}
