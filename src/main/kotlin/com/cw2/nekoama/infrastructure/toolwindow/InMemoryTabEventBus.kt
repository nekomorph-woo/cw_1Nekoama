package com.cw2.nekoama.infrastructure.toolwindow

import com.cw2.nekoama.domain.toolwindow.model.TabEvent
import com.cw2.nekoama.domain.toolwindow.model.TabMetadata
import com.cw2.nekoama.domain.toolwindow.service.SubscriptionHandle
import com.cw2.nekoama.domain.toolwindow.service.TabEventBus
import com.cw2.nekoama.shared.logging.NekoamaLogger
import kotlin.reflect.KClass

/**
 * 内存事件总线实现
 *
 * 特性：
 * - 线程安全（使用 synchronized）
 * - 支持事件订阅/发布/取消订阅
 * - 同步事件分发（订阅者应快速处理）
 * - 异常隔离：单个订阅者抛异常不影响其他订阅者
 */
class InMemoryTabEventBus : TabEventBus {
    /**
     * 订阅者映射：事件类型 -> (订阅者ID -> 事件处理器)
     */
    private val subscribers = mutableMapOf<KClass<*>, MutableMap<TabMetadata.TabId, (TabEvent) -> Unit>>()

    override fun <T : TabEvent> subscribe(
        eventType: KClass<T>,
        subscriber: TabMetadata.TabId,
        handler: (T) -> Unit
    ): SubscriptionHandle {
        synchronized(subscribers) {
            subscribers.getOrPut(eventType) { mutableMapOf() }[subscriber] = handler as (TabEvent) -> Unit
        }
        return SubscriptionHandle { unsubscribe(subscriber) }
    }

    override fun publish(event: TabEvent) {
        val handlers = synchronized(subscribers) {
            subscribers[event::class]?.values?.toList() ?: emptyList()
        }
        // 异常隔离：单个订阅者抛异常不影响其他订阅者
        handlers.forEach { handler ->
            try {
                handler(event)
            } catch (e: Exception) {
                NekoamaLogger.error(
                    "EventBus",
                    "Subscriber threw exception while handling event",
                    mapOf(
                        "event" to event::class.simpleName,
                        "subscriber" to handler.toString().take(100)
                    ),
                    e
                )
            }
        }
    }

    override fun unsubscribe(subscriber: TabMetadata.TabId) {
        synchronized(subscribers) {
            subscribers.values.forEach { it.remove(subscriber) }
        }
    }

    override fun clear() {
        synchronized(subscribers) {
            subscribers.clear()
        }
    }
}
