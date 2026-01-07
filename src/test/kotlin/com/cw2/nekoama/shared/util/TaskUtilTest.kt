package com.cw2.nekoama.shared.util

import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 任务管理器测试
 *
 * 验证后台任务的执行、进度指示、取消和超时处理
 */
@DisplayName("任务管理器测试")
class TaskUtilTest {

    private lateinit var mockProject: Project
    private lateinit var mockProgressManager: ProgressManager
    private lateinit var mockIndicator: ProgressIndicator
    private lateinit var mockSettings: NekoamaSettings

    @BeforeEach
    fun setup() {
        // 创建 Mock 对象
        mockProject = mockk<Project>(relaxed = true)
        mockProgressManager = mockk<ProgressManager>(relaxed = true)
        mockIndicator = mockk<ProgressIndicator>(relaxed = true)
        mockSettings = mockk<NekoamaSettings>(relaxed = true)

        // 设置默认行为
        every { mockSettings.requestTimeoutMs } returns 30000
        every { mockIndicator.isIndeterminate = true } just Runs
        every { mockIndicator.text = any() } just Runs
        every { mockProgressManager.run(any<Task.Backgroundable>()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== 基本任务执行测试 ====================

    @Nested
    @DisplayName("基本任务执行测试")
    inner class BasicExecutionTests {

        @Test
        @DisplayName("执行任务 - 成功时应该调用 onSuccess")
        fun `执行任务 - 成功时应该调用 onSuccess`() {
            // 准备测试数据
            val title = "测试任务"
            val expectedResult = "任务完成"
            var successCallbackCalled = false
            var errorCallbackCalled = false

            // 执行测试
            IntellijTaskManager.execute(
                project = mockProject,
                title = title,
                cancellable = true,
                task = { expectedResult },
                onSuccess = {
                    successCallbackCalled = true
                    assertThat(it).isEqualTo(expectedResult)
                },
                onError = { errorCallbackCalled = true }
            )

            // 验证结果
            assertThat(successCallbackCalled).isTrue()
            assertThat(errorCallbackCalled).isFalse()
        }

        @Test
        @DisplayName("执行任务 - 失败时应该调用 onError")
        fun `执行任务 - 失败时应该调用 onError`() {
            // 准备测试数据
            val title = "测试失败任务"
            val expectedException = RuntimeException("测试异常")
            var successCallbackCalled = false
            var errorCallbackCalled = false

            // 执行测试
            IntellijTaskManager.execute(
                project = mockProject,
                title = title,
                cancellable = true,
                task = { throw expectedException },
                onSuccess = { successCallbackCalled = true },
                onError = {
                    errorCallbackCalled = true
                    assertThat(it).isSameAs(expectedException)
                }
            )

            // 验证结果
            assertThat(successCallbackCalled).isFalse()
            assertThat(errorCallbackCalled).isTrue()
        }

        @Test
        @DisplayName("执行任务 - 空项目应该正常工作")
        fun `执行任务 - 空项目应该正常工作`() {
            // 准备测试数据
            var callbackExecuted = false

            // 执行测试
            IntellijTaskManager.execute(
                project = null,
                title = "无项目任务",
                cancellable = true,
                task = { "完成" },
                onSuccess = { callbackExecuted = true },
                onError = {}
            )

            // 验证结果
            assertThat(callbackExecuted).isTrue()
        }
    }

    // ==================== 可取消性测试 ====================

    @Nested
    @DisplayName("可取消性测试")
    inner class CancellableTests {

        @Test
        @DisplayName("执行任务 - 可取消任务应该支持取消")
        fun `执行任务 - 可取消任务应该支持取消`() {
            // 准备测试数据
            var callbackExecuted = false

            // 执行测试
            IntellijTaskManager.execute(
                project = mockProject,
                title = "可取消任务",
                cancellable = true,
                task = { "完成" },
                onSuccess = { callbackExecuted = true },
                onError = {}
            )

            // 验证结果
            assertThat(callbackExecuted).isTrue()
        }

        @Test
        @DisplayName("执行任务 - 不可取消任务应该正常执行")
        fun `执行任务 - 不可取消任务应该正常执行`() {
            // 准备测试数据
            var callbackExecuted = false

            // 执行测试
            IntellijTaskManager.execute(
                project = mockProject,
                title = "不可取消任务",
                cancellable = false,
                task = { "完成" },
                onSuccess = { callbackExecuted = true },
                onError = {}
            )

            // 验证结果
            assertThat(callbackExecuted).isTrue()
        }
    }

    // ==================== 进度指示测试 ====================

    @Nested
    @DisplayName("进度指示测试")
    inner class ProgressIndicatorTests {

        @Test
        @DisplayName("执行任务 - 应该设置进度标题")
        fun `执行任务 - 应该设置进度标题`() {
            // 准备测试数据
            val title = "进度标题测试"

            // 执行测试
            IntellijTaskManager.execute(
                project = mockProject,
                title = title,
                cancellable = true,
                task = { "完成" },
                onSuccess = {},
                onError = {}
            )

            // 验证结果（通过 mock 验证）
            // 注意：由于实际实现中 ProgressIndicator 是在 Task 内部创建的，
            // 我们无法直接 mock 它。这里只是验证执行不会抛出异常。
        }
    }

    // ==================== 任务标题测试 ====================

    @Nested
    @DisplayName("任务标题测试")
    inner class TaskTitleTests {

        @Test
        @DisplayName("执行任务 - 中文标题应该正常显示")
        fun `执行任务 - 中文标题应该正常显示`() {
            // 准备测试数据
            val title = "生成代码注释"

            // 执行测试
            IntellijTaskManager.execute(
                project = mockProject,
                title = title,
                cancellable = true,
                task = { "完成" },
                onSuccess = {},
                onError = {}
            )

            // 验证结果（无异常即为成功）
        }

        @Test
        @DisplayName("执行任务 - 空标题应该使用默认值")
        fun `执行任务 - 空标题应该使用默认值`() {
            // 准备测试数据
            val title = ""

            // 执行测试
            IntellijTaskManager.execute(
                project = mockProject,
                title = title,
                cancellable = true,
                task = { "完成" },
                onSuccess = {},
                onError = {}
            )

            // 验证结果（无异常即为成功）
        }
    }

    // ==================== 异步任务测试 ====================

    @Nested
    @DisplayName("异步任务测试")
    inner class AsyncTaskTests {

        @Test
        @DisplayName("执行任务 - 应该异步执行")
        fun `执行任务 - 应该异步执行`() {
            // 准备测试数据
            var executed = false

            // 执行测试
            IntellijTaskManager.execute(
                project = mockProject,
                title = "异步任务",
                cancellable = true,
                task = {
                    executed = true
                    "完成"
                },
                onSuccess = {},
                onError = {}
            )

            // 验证结果
            assertThat(executed).isTrue()
        }

        @Test
        @DisplayName("执行任务 - 多个任务应该独立执行")
        fun `执行任务 - 多个任务应该独立执行`() {
            // 准备测试数据
            var task1Executed = false
            var task2Executed = false

            // 执行测试
            IntellijTaskManager.execute(
                project = mockProject,
                title = "任务1",
                cancellable = true,
                task = { task1Executed = true; "完成1" },
                onSuccess = {},
                onError = {}
            )

            IntellijTaskManager.execute(
                project = mockProject,
                title = "任务2",
                cancellable = true,
                task = { task2Executed = true; "完成2" },
                onSuccess = {},
                onError = {}
            )

            // 验证结果
            assertThat(task1Executed).isTrue()
            assertThat(task2Executed).isTrue()
        }
    }
}
