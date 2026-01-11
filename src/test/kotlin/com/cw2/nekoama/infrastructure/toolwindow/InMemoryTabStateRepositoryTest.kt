package com.cw2.nekoama.infrastructure.toolwindow

import com.cw2.nekoama.domain.toolwindow.model.TabState
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.shared.model.NekoamaResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

/**
 * InMemoryTabStateRepository 单元测试
 */
class InMemoryTabStateRepositoryTest {

    private lateinit var repository: InMemoryTabStateRepository

    // 测试用状态类型
    data class TestState(val value: String) : TabState {
        override fun validate(): NekoamaResult<Unit> {
            return if (value.isNotEmpty()) NekoamaResult.success(Unit)
            else NekoamaResult.error(NekoamaError.Unknown("Value cannot be empty"))
        }
    }

    @BeforeEach
    fun setUp() {
        repository = InMemoryTabStateRepository()
    }

    @Test
    fun `保存并加载状态，应该成功`() {
        // Arrange
        val tabId = "test-tab"
        val state = TestState("test-value")

        // Act
        repository.saveState(tabId, state)
        val loaded = repository.loadState(tabId, TestState::class)

        // Assert
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.value).isEqualTo("test-value")
    }

    @Test
    fun `保存无效状态，应该抛出异常`() {
        // Arrange
        val tabId = "test-tab"
        val invalidState = TestState("") // 验证会失败

        // Act & Assert
        var exceptionThrown = false
        try {
            repository.saveState(tabId, invalidState)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertThat(exceptionThrown).isTrue()
    }

    @Test
    fun `加载不存在的状态，应该返回null`() {
        // Act
        val loaded = repository.loadState("non-existent", TestState::class)

        // Assert
        assertThat(loaded).isNull()
    }

    @Test
    fun `加载错误类型的状态，应该返回null`() {
        // Arrange
        val tabId = "test-tab"
        val state = TestState("test-value")
        repository.saveState(tabId, state)

        // Act - 尝试用错误的类型加载
        data class WrongState(val value: Int) : TabState {
            override fun validate(): NekoamaResult<Unit> = NekoamaResult.success(Unit)
        }
        val loaded = repository.loadState(tabId, WrongState::class)

        // Assert
        assertThat(loaded).isNull()
    }

    @Test
    fun `删除状态后，应该无法加载`() {
        // Arrange
        val tabId = "test-tab"
        val state = TestState("test-value")
        repository.saveState(tabId, state)

        // Act
        repository.deleteState(tabId)
        val loaded = repository.loadState(tabId, TestState::class)

        // Assert
        assertThat(loaded).isNull()
    }

    @Test
    fun `hasState应该正确返回状态是否存在`() {
        // Arrange
        val tabId = "test-tab"
        val state = TestState("test-value")

        // Act & Assert
        assertThat(repository.hasState(tabId)).isFalse()
        repository.saveState(tabId, state)
        assertThat(repository.hasState(tabId)).isTrue()
    }

    @Test
    fun `清空所有状态后，所有状态都应该被删除`() {
        // Arrange
        repository.saveState("tab1", TestState("value1"))
        repository.saveState("tab2", TestState("value2"))

        // Act
        repository.clear()

        // Assert
        assertThat(repository.hasState("tab1")).isFalse()
        assertThat(repository.hasState("tab2")).isFalse()
    }
}
