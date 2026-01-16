package com.cw2.nekoama.domain.toolwindow.model

import com.cw2.nekoama.domain.toolwindow.model.TabMetadata.TabId

/**
 * Tab事件定义（密封类，类型安全的模式匹配）
 *
 * 设计原则：
 * - 事件是不可变的
 * - 使用密封类确保完整的类型安全
 * - 支持事件携带任意类型的payload
 */
sealed class TabEvent {
    /**
     * Tab激活事件
     */
    data class TabActivated(
        val tabId: TabId,
        val timestamp: Long = System.currentTimeMillis()
    ) : TabEvent()

    /**
     * Tab失活事件
     */
    data class TabDeactivated(
        val tabId: TabId,
        val timestamp: Long = System.currentTimeMillis()
    ) : TabEvent()

    /**
     * 通用数据事件（用于Tab间业务数据传递）
     *
     * 使用示例：
     * - 代码选择事件：DataType = CodeSelectionData
     * - AI消息事件：DataType = AIMessageData
     *
     * @param sourceId 发布事件的Tab ID
     * @param dataType 数据类型标识（用于反序列化）
     * @param payload 任意序列化数据
     */
    data class DataEvent<T : Any>(
        val sourceId: TabId,
        val dataType: String,
        val payload: T
    ) : TabEvent()
}
