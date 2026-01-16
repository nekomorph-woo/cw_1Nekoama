package com.cw2.nekoama.domain.toolwindow.service

import com.cw2.nekoama.domain.toolwindow.model.TabEvent
import com.cw2.nekoama.domain.toolwindow.model.TabMetadata
import kotlin.reflect.KClass

/**
 * 订阅句柄（用于取消订阅）
 *
 * 说明：Domain层的Disposable接口，不依赖IntelliJ平台
 */
fun interface SubscriptionHandle {
    /**
     * 取消订阅
     */
    fun dispose()
}

/**
 * Tab事件总线接口
 *
 * 职责：
 * - 管理事件订阅者
 * - 分发事件到订阅者
 * - 线程安全保证
 *
 * 设计模式：观察者模式
 */
interface TabEventBus {
    /**
     * 订阅事件
     *
     * @param eventType 事件类型（KClass）
     * @param subscriber 订阅者（Tab ID 或 事件处理器）
     * @param handler 事件处理回调
     * @return SubscriptionHandle 用于取消订阅
     */
    fun <T : TabEvent> subscribe(
        eventType: KClass<T>,
        subscriber: TabMetadata.TabId,
        handler: (T) -> Unit
    ): SubscriptionHandle

    /**
     * 发布事件
     *
     * @param event 事件实例
     */
    fun publish(event: TabEvent)

    /**
     * 取消订阅
     *
     * @param subscriber 订阅者ID
     */
    fun unsubscribe(subscriber: TabMetadata.TabId)

    /**
     * 清空所有订阅
     */
    fun clear()
}
