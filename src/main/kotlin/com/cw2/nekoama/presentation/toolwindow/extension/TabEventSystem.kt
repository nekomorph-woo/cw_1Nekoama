package com.cw2.nekoama.presentation.toolwindow.extension

import com.cw2.nekoama.core.logging.NekoamaLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tab事件系统
 *
 * 提供Tab扩展之间以及扩展与核心系统之间的通信机制。
 * 支持事件发布/订阅模式和消息传递。
 */
class TabEventSystem {

    private val eventListeners = ConcurrentHashMap<Class<out TabEvent>, CopyOnWriteArrayList<TabEventHandler<*>>>()
    private val messageHandlers = ConcurrentHashMap<String, TabMessageHandler>()
    private val logger = NekoamaLogger

    /**
     * 发布事件
     *
     * @param event 要发布的事件
     */
    fun publishEvent(event: TabEvent) {
        try {
            val eventType = event.javaClass
            val handlers = eventListeners[eventType]?.toList() ?: emptyList<TabEventHandler<*>>()

            handlers.forEach { handler ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    (handler as TabEventHandler<TabEvent>).handleEvent(event)
                } catch (e: Exception) {
                    logger.error("TabEventSystem", "Error handling event: ${eventType.simpleName}", error = e)
                }
            }

            logger.debug("TabEventSystem", "Event published: ${eventType.simpleName} to ${handlers.count()} handlers")

        } catch (e: Exception) {
            logger.error("TabEventSystem", "Failed to publish event: ${event.javaClass.simpleName}", error = e)
        }
    }

    /**
     * 订阅事件
     *
     * @param eventType 事件类型
     * @param handler 事件处理器
     */
    fun <T : TabEvent> subscribe(eventType: Class<T>, handler: TabEventHandler<T>) {
        @Suppress("UNCHECKED_CAST")
        eventListeners.computeIfAbsent(eventType) { CopyOnWriteArrayList<TabEventHandler<*>>() }.add(handler as TabEventHandler<*>)
        logger.debug("TabEventSystem", "Event subscribed: ${eventType.simpleName}")
    }

    /**
     * 取消事件订阅
     *
     * @param eventType 事件类型
     * @param handler 事件处理器
     */
    fun <T : TabEvent> unsubscribe(eventType: Class<T>, handler: TabEventHandler<T>) {
        eventListeners[eventType]?.remove(handler)
        logger.debug("TabEventSystem", "Event unsubscribed: ${eventType.simpleName}")
    }

    /**
     * 发送消息
     *
     * @param target 目标标识
     * @param message 消息内容
     */
    fun sendMessage(target: String, message: TabMessage) {
        try {
            val handler = messageHandlers[target]
            if (handler != null) {
                handler.handleMessage(message)
                logger.debug("TabEventSystem", "Message sent to $target: ${message.type}")
            } else {
                logger.warn("TabEventSystem", "No handler found for target: $target")
            }
        } catch (e: Exception) {
            logger.error("TabEventSystem", "Failed to send message to $target", error = e)
        }
    }

    /**
     * 注册消息处理器
     *
     * @param target 目标标识
     * @param handler 消息处理器
     */
    fun registerMessageHandler(target: String, handler: TabMessageHandler) {
        messageHandlers[target] = handler
        logger.debug("TabEventSystem", "Message handler registered: $target")
    }

    /**
     * 注销消息处理器
     *
     * @param target 目标标识
     */
    fun unregisterMessageHandler(target: String) {
        messageHandlers.remove(target)
        logger.debug("TabEventSystem", "Message handler unregistered: $target")
    }

    /**
     * 清理所有监听器
     */
    fun dispose() {
        try {
            eventListeners.clear()
            messageHandlers.clear()
            logger.info("TabEventSystem", "Event system disposed")
        } catch (e: Exception) {
            logger.error("TabEventSystem", "Error during disposal", error = e)
        }
    }
}

/**
 * Tab事件基类
 */
abstract class TabEvent {
    val timestamp: Long = System.currentTimeMillis()
    open val source: String? = null
}

/**
 * Tab激活事件
 */
data class TabActivatedEvent(
    val tabId: String,
    override val source: String? = null
) : TabEvent()

/**
 * Tab停用事件
 */
data class TabDeactivatedEvent(
    val tabId: String,
    override val source: String? = null
) : TabEvent()

/**
 * Tab刷新事件
 */
data class TabRefreshEvent(
    val tabId: String,
    override val source: String? = null
) : TabEvent()

/**
 * Tab状态变更事件
 */
data class TabStateChangedEvent(
    val tabId: String,
    val oldState: Map<String, Any>,
    val newState: Map<String, Any>,
    override val source: String? = null
) : TabEvent()

/**
 * 扩展注册事件
 */
data class ExtensionRegisteredEvent(
    val extensionId: String,
    val extension: TabExtension,
    override val source: String? = null
) : TabEvent()

/**
 * 扩展注销事件
 */
data class ExtensionUnregisteredEvent(
    val extensionId: String,
    override val source: String? = null
) : TabEvent()

/**
 * 扩展配置变更事件
 */
data class ExtensionConfigurationChangedEvent(
    val extensionId: String,
    val oldConfig: Map<String, Any>,
    val newConfig: Map<String, Any>,
    override val source: String? = null
) : TabEvent()

/**
 * 事件处理器接口
 */
interface TabEventHandler<T : TabEvent> {
    fun handleEvent(event: T)
}

/**
 * 消息基类
 */
abstract class TabMessage {
    abstract val type: String
    abstract val target: String
    val timestamp: Long = System.currentTimeMillis()
    open val source: String? = null
}

/**
 * 配置更新消息
 */
data class ConfigurationUpdateMessage(
    override val target: String,
    val configuration: Map<String, Any>,
    override val source: String? = null
) : TabMessage() {
    override val type = "configuration_update"
}

/**
 * 状态同步消息
 */
data class StateSyncMessage(
    override val target: String,
    val stateData: Map<String, Any>,
    override val source: String? = null
) : TabMessage() {
    override val type = "state_sync"
}

/**
 * 功能调用消息
 */
data class FunctionCallMessage(
    override val target: String,
    val functionName: String,
    val parameters: Map<String, Any> = emptyMap(),
    override val source: String? = null
) : TabMessage() {
    override val type = "function_call"
}

/**
 * 消息处理器接口
 */
interface TabMessageHandler {
    fun handleMessage(message: TabMessage)
}

/**
 * 事件系统单例
 */
object TabEventSystemSingleton {

    private val instance = TabEventSystem()

    /**
     * 获取事件系统实例
     */
    fun getInstance(): TabEventSystem = instance
}

/**
 * 事件总线
 *
 * 提供更高级的事件管理功能，如事件过滤、转换、批量处理等。
 */
class TabEventBus {

    private val eventSystem = TabEventSystemSingleton.getInstance()
    private val eventFilters = CopyOnWriteArrayList<TabEventFilter>()
    private val eventTransformers = CopyOnWriteArrayList<TabEventTransformer>()

    /**
     * 添加事件过滤器
     *
     * @param filter 事件过滤器
     */
    fun addFilter(filter: TabEventFilter) {
        eventFilters.add(filter)
    }

    /**
     * 移除事件过滤器
     *
     * @param filter 事件过滤器
     */
    fun removeFilter(filter: TabEventFilter) {
        eventFilters.remove(filter)
    }

    /**
     * 添加事件转换器
     *
     * @param transformer 事件转换器
     */
    fun addTransformer(transformer: TabEventTransformer) {
        eventTransformers.add(transformer)
    }

    /**
     * 移除事件转换器
     *
     * @param transformer 事件转换器
     */
    fun removeTransformer(transformer: TabEventTransformer) {
        eventTransformers.remove(transformer)
    }

    /**
     * 发布事件（经过过滤和转换）
     *
     * @param event 要发布的事件
     */
    fun publishEvent(event: TabEvent) {
        try {
            var processedEvent = event

            // 应用过滤器
            for (filter in eventFilters) {
                if (!filter.shouldPublish(processedEvent)) {
                    return // 事件被过滤掉
                }
            }

            // 应用转换器
            for (transformer in eventTransformers) {
                processedEvent = transformer.transform(processedEvent) ?: return
            }

            // 发布处理后的事件
            eventSystem.publishEvent(processedEvent)

        } catch (e: Exception) {
            NekoamaLogger.error("TabEventBus", "Failed to publish event: ${event.javaClass.simpleName}", error = e)
        }
    }

    /**
     * 订阅事件
     *
     * @param eventType 事件类型
     * @param handler 事件处理器
     */
    fun <T : TabEvent> subscribe(eventType: Class<T>, handler: TabEventHandler<T>) {
        eventSystem.subscribe(eventType, handler)
    }

    /**
     * 取消事件订阅
     *
     * @param eventType 事件类型
     * @param handler 事件处理器
     */
    fun <T : TabEvent> unsubscribe(eventType: Class<T>, handler: TabEventHandler<T>) {
        eventSystem.unsubscribe(eventType, handler)
    }

    /**
     * 清理资源
     */
    fun dispose() {
        eventFilters.clear()
        eventTransformers.clear()
    }
}

/**
 * 事件过滤器接口
 */
interface TabEventFilter {
    fun shouldPublish(event: TabEvent): Boolean
}

/**
 * 事件转换器接口
 */
interface TabEventTransformer {
    fun transform(event: TabEvent): TabEvent?
}