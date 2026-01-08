package com.cw2.nekoama.domain.toolwindow.service

import com.cw2.nekoama.domain.toolwindow.model.TabEvent
import com.cw2.nekoama.domain.toolwindow.model.TabMetadata
import com.cw2.nekoama.domain.toolwindow.model.TabState
import com.cw2.nekoama.domain.toolwindow.repository.TabStateRepository
import com.cw2.nekoama.shared.model.Result
import com.cw2.nekoama.shared.exception.NekoamaError
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * TabCoordinatorServiceImpl 单元测试
 *
 * 验证 Tab 切换逻辑、状态保存和事件发布
 */
class TabCoordinatorServiceImplTest {

    private lateinit var coordinatorService: TabCoordinatorServiceImpl
    private lateinit var mockEventBus: TabEventBus
    private lateinit var mockStateRepository: TabStateRepository

    private val tabId1 = TabMetadata.TabId("tab-1")
    private val tabId2 = TabMetadata.TabId("tab-2")

    @BeforeEach
    fun setUp() {
        mockEventBus = mockk()
        mockStateRepository = mockk()

        coordinatorService = TabCoordinatorServiceImpl(
            eventBus = mockEventBus,
            stateRepository = mockStateRepository
        )
    }

    @Test
    fun `激活Tab时应该发布TabDeactivated和TabActivated事件`() {
        // Arrange
        every { mockEventBus.publish(any<TabEvent>()) } returns Unit

        // Act - 先激活 tab1，再切换到 tab2
        coordinatorService.activateTab(tabId1)
        coordinatorService.activateTab(tabId2)

        // Assert - 验证事件发布顺序
        verify(exactly = 1) {
            mockEventBus.publish(TabEvent.TabDeactivated(tabId1))
        }
        verify(exactly = 1) {
            mockEventBus.publish(TabEvent.TabActivated(tabId1))
        }
        verify(exactly = 1) {
            mockEventBus.publish(TabEvent.TabActivated(tabId2))
        }

        // 验证当前激活的 Tab
        assertThat(coordinatorService.activeTabId).isEqualTo(tabId2)
    }

    @Test
    fun `首次激活Tab时不应发布TabDeactivated事件`() {
        // Arrange
        every { mockEventBus.publish(any<TabEvent>()) } returns Unit

        // Act
        coordinatorService.activateTab(tabId1)

        // Assert - 只发布了 TabActivated 事件
        verify(exactly = 1) {
            mockEventBus.publish(TabEvent.TabActivated(tabId1))
        }
        verify(exactly = 0) {
            mockEventBus.publish(TabEvent.TabDeactivated(any()))
        }
    }

    @Test
    fun `保存Tab状态应该调用repository`() {
        // Arrange
        val testState = TestTabState()
        every { mockStateRepository.saveState(any(), any()) } returns Unit

        // Act
        coordinatorService.saveTabState("tab-1", testState)

        // Assert
        verify(exactly = 1) {
            mockStateRepository.saveState("tab-1", testState)
        }
    }

    @Test
    fun `使用扩展函数加载Tab状态应该类型安全`() {
        // Arrange
        val testState = TestTabState()
        every { mockStateRepository.loadState("tab-1", TestTabState::class) } returns testState

        // Act - 使用扩展函数（通过 import 导入）
        val loadedState: TestTabState? = coordinatorService.loadTabState(tabId1)

        // Assert
        assertThat(loadedState).isNotNull
        assertThat(loadedState).isEqualTo(testState)
        verify(exactly = 1) {
            mockStateRepository.loadState("tab-1", TestTabState::class)
        }
    }

    @Test
    fun `加载不存在的Tab状态应该返回null`() {
        // Arrange
        every { mockStateRepository.loadState("tab-1", TestTabState::class) } returns null

        // Act
        val loadedState: TestTabState? = coordinatorService.loadTabState(tabId1)

        // Assert
        assertThat(loadedState).isNull()
    }

    @Test
    fun `重复激活同一个Tab应该只发布一次TabActivated事件`() {
        // Arrange
        every { mockEventBus.publish(any<TabEvent>()) } returns Unit

        // Act - 重复激活同一个 Tab
        coordinatorService.activateTab(tabId1)
        coordinatorService.activateTab(tabId1)
        coordinatorService.activateTab(tabId1)

        // Assert - 只有第一次会发布 TabActivated，后续不会发布 TabDeactivated
        verify(exactly = 1) {
            mockEventBus.publish(TabEvent.TabActivated(tabId1))
        }
        verify(exactly = 0) {
            mockEventBus.publish(TabEvent.TabDeactivated(any()))
        }
    }

    /**
     * 测试用的 TabState 实现
     */
    data class TestTabState(
        override val version: Int = 1
    ) : TabState {
        override fun validate(): Result<Unit> = Result.success(Unit)
    }
}
