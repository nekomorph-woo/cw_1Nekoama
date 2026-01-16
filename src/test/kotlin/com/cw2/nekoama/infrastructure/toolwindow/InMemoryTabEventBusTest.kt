package com.cw2.nekoama.infrastructure.toolwindow

import com.cw2.nekoama.domain.toolwindow.model.TabEvent
import com.cw2.nekoama.domain.toolwindow.model.TabMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

/**
 * InMemoryTabEventBus 单元测试
 */
class InMemoryTabEventBusTest {

    private lateinit var eventBus: InMemoryTabEventBus

    // 测试用事件类型（使用 TabEvent.DataEvent 作为测试事件）
    private val testSubscriberId = TabMetadata.TabId("test-subscriber")

    @BeforeEach
    fun setUp() {
        eventBus = InMemoryTabEventBus()
    }

    @Test
    fun `订阅并发布事件，应该被接收`() {
        // Arrange
        val receivedMessages = mutableListOf<String>()
        eventBus.subscribe(TabEvent.DataEvent::class, testSubscriberId) { event ->
            @Suppress("UNCHECKED_CAST")
            val dataEvent = event as TabEvent.DataEvent<String>
            receivedMessages.add(dataEvent.payload)
        }

        // Act
        eventBus.publish(TabEvent.DataEvent(
            sourceId = TabMetadata.TabId("source"),
            dataType = "test",
            payload = "Hello"
        ))
        eventBus.publish(TabEvent.DataEvent(
            sourceId = TabMetadata.TabId("source"),
            dataType = "test",
            payload = "World"
        ))

        // Assert
        assertThat(receivedMessages).hasSize(2)
        assertThat(receivedMessages[0]).isEqualTo("Hello")
        assertThat(receivedMessages[1]).isEqualTo("World")
    }

    @Test
    fun `取消订阅后，不应再接收事件`() {
        // Arrange
        val receivedMessages = mutableListOf<String>()
        val subscription = eventBus.subscribe(TabEvent.DataEvent::class, testSubscriberId) { event ->
            @Suppress("UNCHECKED_CAST")
            val dataEvent = event as TabEvent.DataEvent<String>
            receivedMessages.add(dataEvent.payload)
        }

        // Act
        eventBus.publish(TabEvent.DataEvent(
            sourceId = TabMetadata.TabId("source"),
            dataType = "test",
            payload = "Before"
        ))
        subscription.dispose()
        eventBus.publish(TabEvent.DataEvent(
            sourceId = TabMetadata.TabId("source"),
            dataType = "test",
            payload = "After"
        ))

        // Assert
        assertThat(receivedMessages).hasSize(1)
        assertThat(receivedMessages[0]).isEqualTo("Before")
    }

    @Test
    fun `多个订阅者都应该接收事件`() {
        // Arrange
        val subscriber1 = TabMetadata.TabId("subscriber-1")
        val subscriber2 = TabMetadata.TabId("subscriber-2")
        val messages1 = mutableListOf<String>()
        val messages2 = mutableListOf<String>()

        eventBus.subscribe(TabEvent.DataEvent::class, subscriber1) { event ->
            @Suppress("UNCHECKED_CAST")
            val dataEvent = event as TabEvent.DataEvent<String>
            messages1.add(dataEvent.payload)
        }
        eventBus.subscribe(TabEvent.DataEvent::class, subscriber2) { event ->
            @Suppress("UNCHECKED_CAST")
            val dataEvent = event as TabEvent.DataEvent<String>
            messages2.add(dataEvent.payload)
        }

        // Act
        eventBus.publish(TabEvent.DataEvent(
            sourceId = TabMetadata.TabId("source"),
            dataType = "test",
            payload = "Test"
        ))

        // Assert
        assertThat(messages1).hasSize(1)
        assertThat(messages2).hasSize(1)
        assertThat(messages1[0]).isEqualTo("Test")
        assertThat(messages2[0]).isEqualTo("Test")
    }

    @Test
    fun `清空所有订阅后，不应再接收事件`() {
        // Arrange
        val receivedMessages = mutableListOf<String>()
        eventBus.subscribe(TabEvent.DataEvent::class, testSubscriberId) { event ->
            @Suppress("UNCHECKED_CAST")
            val dataEvent = event as TabEvent.DataEvent<String>
            receivedMessages.add(dataEvent.payload)
        }

        // Act
        eventBus.clear()
        eventBus.publish(TabEvent.DataEvent(
            sourceId = TabMetadata.TabId("source"),
            dataType = "test",
            payload = "After clear"
        ))

        // Assert
        assertThat(receivedMessages).isEmpty()
    }

    @Test
    fun `单个订阅者抛异常，不应影响其他订阅者接收事件`() {
        // Arrange
        val subscriber1 = TabMetadata.TabId("subscriber-1")
        val subscriber2 = TabMetadata.TabId("subscriber-2")
        val subscriber3 = TabMetadata.TabId("subscriber-3")
        val messages1 = mutableListOf<String>()
        val messages2 = mutableListOf<String>()
        val messages3 = mutableListOf<String>()

        // subscriber1 正常接收
        eventBus.subscribe(TabEvent.DataEvent::class, subscriber1) { event ->
            @Suppress("UNCHECKED_CAST")
            val dataEvent = event as TabEvent.DataEvent<String>
            messages1.add(dataEvent.payload)
        }

        // subscriber2 会抛异常
        eventBus.subscribe(TabEvent.DataEvent::class, subscriber2) { _ ->
            throw RuntimeException("Subscriber 2 failed!")
        }

        // subscriber3 正常接收
        eventBus.subscribe(TabEvent.DataEvent::class, subscriber3) { event ->
            @Suppress("UNCHECKED_CAST")
            val dataEvent = event as TabEvent.DataEvent<String>
            messages3.add(dataEvent.payload)
        }

        // Act
        eventBus.publish(TabEvent.DataEvent(
            sourceId = TabMetadata.TabId("source"),
            dataType = "test",
            payload = "Test"
        ))

        // Assert - subscriber1 和 subscriber3 都应该收到事件，即使 subscriber2 抛异常
        assertThat(messages1).hasSize(1)
        assertThat(messages1[0]).isEqualTo("Test")
        assertThat(messages2).isEmpty() // subscriber2 抛异常，但不会记录消息
        assertThat(messages3).hasSize(1)
        assertThat(messages3[0]).isEqualTo("Test")
    }
}
