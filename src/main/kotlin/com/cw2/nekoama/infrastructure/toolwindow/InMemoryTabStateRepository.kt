package com.cw2.nekoama.infrastructure.toolwindow

import com.cw2.nekoama.domain.toolwindow.model.TabState
import com.cw2.nekoama.domain.toolwindow.repository.TabStateRepository
import kotlin.reflect.KClass

/**
 * 内存状态存储实现
 *
 * 说明：
 * - 这是初始实现，状态仅在运行时保留
 * - 不依赖文件系统，适合快速验证框架
 * - 未来可以无缝替换为持久化实现
 *
 * 线程安全：使用 synchronized 保证并发安全
 */
class InMemoryTabStateRepository : TabStateRepository {
    private val states = mutableMapOf<String, TabState>()

    override fun saveState(tabId: String, state: TabState) {
        // 验证状态
        val validationResult = state.validate()
        if (validationResult.isError) {
            throw IllegalArgumentException("Invalid state for tab $tabId: ${validationResult.errorOrNull()}")
        }
        synchronized(states) {
            states[tabId] = state
        }
    }

    override fun <T : TabState> loadState(tabId: String, clazz: KClass<T>): T? {
        val state = synchronized(states) { states[tabId] }
        return if (clazz.isInstance(state)) {
            @Suppress("UNCHECKED_CAST")
            state as? T
        } else {
            null
        }
    }

    override fun deleteState(tabId: String) {
        synchronized(states) { states.remove(tabId) }
    }

    override fun hasState(tabId: String): Boolean {
        return synchronized(states) { states.containsKey(tabId) }
    }

    override fun clear() {
        synchronized(states) { states.clear() }
    }
}
