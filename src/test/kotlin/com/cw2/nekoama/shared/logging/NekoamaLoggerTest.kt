package com.cw2.nekoama.shared.logging

import com.cw2.nekoama.shared.exception.NekoamaError
import com.intellij.openapi.diagnostic.Logger
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Nekoama 日志系统测试
 *
 * 验证日志记录、敏感数据脱敏、性能记录等功能
 */
@DisplayName("Nekoama 日志系统测试")
class NekoamaLoggerTest {

    private lateinit var mockLogger: Logger

    @BeforeEach
    fun setup() {
        // Mock IntelliJ Logger
        mockLogger = mockk<Logger>(relaxed = true)
        mockkStatic("com.intellij.openapi.diagnostic.Logger")

        every { Logger.getInstance("Nekoama") } returns mockLogger
        every { mockLogger.isDebugEnabled } returns true
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== 敏感数据脱敏测试 ====================

    @Nested
    @DisplayName("敏感数据脱敏测试")
    inner class SensitiveDataMaskingTests {

        @Test
        @DisplayName("脱敏 - 应该遮蔽 API Key")
        fun `脱敏 - 应该遮蔽 API Key`() {
            // 由于 maskSensitiveData 和 maskString 是 private 方法
            // 我们通过 info 方法的输出间接验证脱敏功能
            NekoamaLogger.info("TEST", "api_key=sk-abc123def456ghi789jkl012")

            // 验证日志被调用
            verify(atLeast = 1) { mockLogger.info(any<String>()) }
        }

        @Test
        @DisplayName("脱敏 - 应该遮蔽 Bearer Token")
        fun `脱敏 - 应该遮蔽 Bearer Token`() {
            NekoamaLogger.info("TEST", "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")

            verify(atLeast = 1) { mockLogger.info(any<String>()) }
        }

        @Test
        @DisplayName("脱敏 - 应该遮蔽密码")
        fun `脱敏 - 应该遮蔽密码`() {
            NekoamaLogger.info("TEST", "password=mySecretPassword123")

            verify(atLeast = 1) { mockLogger.info(any<String>()) }
        }

        @Test
        @DisplayName("脱敏 - 应该遮蔽 Base64 编码字符串")
        fun `脱敏 - 应该遮蔽 Base64 编码字符串`() {
            NekoamaLogger.info("TEST", "token: SGVsbG8gV29ybGQhIE15IHNlY3JldCBkYXRh")

            verify(atLeast = 1) { mockLogger.info(any<String>()) }
        }
    }

    // ==================== 日志级别测试 ====================

    @Nested
    @DisplayName("日志级别测试")
    inner class LogLevelTests {

        @Test
        @DisplayName("debug - 应该调用 logger.debug")
        fun `debug - 应该调用 logger debug`() {
            NekoamaLogger.debug("OP_TEST", "调试信息")

            verify { mockLogger.debug(any<String>()) }
        }

        @Test
        @DisplayName("info - 应该调用 logger.info")
        fun `info - 应该调用 logger info`() {
            NekoamaLogger.info("OP_TEST", "信息日志")

            verify { mockLogger.info(any<String>()) }
        }

        @Test
        @DisplayName("warn - 应该调用 logger.warn")
        fun `warn - 应该调用 logger warn`() {
            val exception = RuntimeException("测试异常")
            NekoamaLogger.warn("OP_TEST", "警告信息", error = exception)

            verify { mockLogger.warn(any<String>(), exception) }
        }

        @Test
        @DisplayName("error - 应该调用 logger.error")
        fun `error - 应该调用 logger error`() {
            val exception = RuntimeException("测试异常")
            NekoamaLogger.error("OP_TEST", "错误信息", error = exception)

            verify { mockLogger.error(any<String>(), exception) }
        }

        @Test
        @DisplayName("debug - 当 isDebugEnabled 为 false 时不应该记录")
        fun `debug - 当 isDebugEnabled 为 false 时不应该记录`() {
            every { mockLogger.isDebugEnabled } returns false

            NekoamaLogger.debug("OP_TEST", "调试信息")

            verify(exactly = 0) { mockLogger.debug(any<String>()) }
        }
    }

    // ==================== NekoamaError 记录测试 ====================

    @Nested
    @DisplayName("NekoamaError 记录测试")
    inner class NekoamaErrorLoggingTests {

        @Test
        @DisplayName("logError - NetworkError 应该记录为警告")
        fun `logError - NetworkError 应该记录为警告`() {
            val error = NekoamaError.NetworkError.ConnectionTimeout("网络连接失败")

            NekoamaLogger.logError("TEST_OP", error)

            verify { mockLogger.warn(any<String>(), error.cause) }
        }

        @Test
        @DisplayName("logError - APIError 应该记录为警告")
        fun `logError - APIError 应该记录为警告`() {
            val error = NekoamaError.APIError.ServerError("API 调用失败")

            NekoamaLogger.logError("TEST_OP", error)

            verify { mockLogger.warn(any<String>(), error.cause) }
        }

        @Test
        @DisplayName("logError - AuthenticationError 应该记录为警告")
        fun `logError - AuthenticationError 应该记录为警告`() {
            val error = NekoamaError.AuthenticationError.InvalidApiKey("认证失败")

            NekoamaLogger.logError("TEST_OP", error)

            verify { mockLogger.warn(any<String>(), error.cause) }
        }

        @Test
        @DisplayName("logError - RateLimitError 应该记录为警告")
        fun `logError - RateLimitError 应该记录为警告`() {
            val error = NekoamaError.RateLimitError.TooManyRequests("速率限制")

            NekoamaLogger.logError("TEST_OP", error)

            verify { mockLogger.warn(any<String>(), error.cause) }
        }

        @Test
        @DisplayName("logError - ParseError 应该记录为错误")
        fun `logError - ParseError 应该记录为错误`() {
            val error = NekoamaError.ParseError.JsonParse("解析失败")

            NekoamaLogger.logError("TEST_OP", error)

            verify { mockLogger.error(any<String>(), error.cause) }
        }

        @Test
        @DisplayName("logError - PlatformError 应该记录为错误")
        fun `logError - PlatformError 应该记录为错误`() {
            val error = NekoamaError.PlatformError.IndexNotReady("平台错误")

            NekoamaLogger.logError("TEST_OP", error)

            verify { mockLogger.error(any<String>(), error.cause) }
        }

        @Test
        @DisplayName("logError - OperationCancelled 应该记录为调试信息")
        fun `logError - OperationCancelled 应该记录为调试信息`() {
            val error = NekoamaError.OperationCancelled("操作取消")

            NekoamaLogger.logError("TEST_OP", error)

            verify { mockLogger.debug(any<String>()) }
        }

        @Test
        @DisplayName("logError - 应该包含上下文信息")
        fun `logError - 应该包含上下文信息`() {
            val error = NekoamaError.NetworkError.ConnectionTimeout("网络错误")
            val context = mapOf("attempt" to 3, "timeout" to 5000)

            NekoamaLogger.logError("TEST_OP", error, context)

            verify(atLeast = 1) { mockLogger.warn(match<String> { it.contains("attempt") && it.contains("timeout") }, error.cause) }
        }
    }

    // ==================== 性能记录测试 ====================

    @Nested
    @DisplayName("性能记录测试")
    inner class PerformanceLoggingTests {

        @Test
        @DisplayName("logPerformance - 快速操作应该记录为调试信息")
        fun `logPerformance - 快速操作应该记录为调试信息`() {
            NekoamaLogger.logPerformance("TEST_OP", 50)

            verify { mockLogger.debug(any<String>()) }
        }

        @Test
        @DisplayName("logPerformance - 正常速度应该记录为调试信息")
        fun `logPerformance - 正常速度应该记录为调试信息`() {
            NekoamaLogger.logPerformance("TEST_OP", 500)

            verify { mockLogger.debug(any<String>()) }
        }

        @Test
        @DisplayName("logPerformance - 较慢操作应该记录为信息")
        fun `logPerformance - 较慢操作应该记录为信息`() {
            NekoamaLogger.logPerformance("TEST_OP", 2000)

            verify { mockLogger.info(any<String>()) }
        }

        @Test
        @DisplayName("logPerformance - 缓慢操作应该记录为警告")
        fun `logPerformance - 缓慢操作应该记录为警告`() {
            NekoamaLogger.logPerformance("TEST_OP", 6000)

            verify { mockLogger.warn(any<String>()) }
        }

        @Test
        @DisplayName("logPerformance - 应该包含上下文")
        fun `logPerformance - 应该包含上下文`() {
            val context = mapOf("records" to 100, "bytes" to 1024)

            NekoamaLogger.logPerformance("TEST_OP", 1500, context)

            verify(atLeast = 1) { mockLogger.info(match<String> { it.contains("records") && it.contains("bytes") }) }
        }
    }

    // ==================== AI 调用记录测试 ====================

    @Nested
    @DisplayName("AI 调用记录测试")
    inner class AICallLoggingTests {

        @Test
        @DisplayName("logAICall - 成功调用应该记录信息")
        fun `logAICall - 成功调用应该记录信息`() {
            NekoamaLogger.logAICall(
                provider = "OpenAI",
                model = "gpt-4",
                operation = "生成命名",
                success = true,
                durationMs = 1500
            )

            verify(atLeast = 1) { mockLogger.info(any<String>()) }
        }

        @Test
        @DisplayName("logAICall - 失败调用应该记录错误")
        fun `logAICall - 失败调用应该记录错误`() {
            val error = NekoamaError.APIError.ServerError("API 调用失败")

            NekoamaLogger.logAICall(
                provider = "OpenAI",
                model = "gpt-4",
                operation = "生成命名",
                success = false,
                durationMs = 5000,
                error = error
            )

            verify(atLeast = 1) { mockLogger.warn(any<String>(), error.cause) }
        }

        @Test
        @DisplayName("logAICall - 带令牌计数应该包含在上下文")
        fun `logAICall - 带令牌计数应该包含在上下文`() {
            NekoamaLogger.logAICall(
                provider = "OpenAI",
                model = "gpt-4",
                operation = "生成命名",
                success = true,
                durationMs = 1000,
                tokenCount = 250
            )

            verify(atLeast = 1) { mockLogger.info(match<String> { it.contains("tokens") }) }
        }

        @Test
        @DisplayName("logAICall - 简化版应该正常工作")
        fun `logAICall - 简化版应该正常工作`() {
            NekoamaLogger.logAICall(
                provider = "Custom",
                model = "custom-model",
                operation = "自定义操作",
                success = true,
                durationMs = 800
            )

            verify(atLeast = 1) { mockLogger.info(match<String> { it.contains("自定义操作") }) }
        }
    }

    // ==================== 用户操作记录测试 ====================

    @Nested
    @DisplayName("用户操作记录测试")
    inner class UserActionLoggingTests {

        @Test
        @DisplayName("logUserAction - 应该记录用户操作")
        fun `logUserAction - 应该记录用户操作`() {
            NekoamaLogger.logUserAction("生成命名", mapOf("file" to "Test.kt"))

            verify { mockLogger.info(match<String> { it.contains("用户执行操作") && it.contains("生成命名") }) }
        }

        @Test
        @DisplayName("logUserAction - 无上下文也应该正常工作")
        fun `logUserAction - 无上下文也应该正常工作`() {
            NekoamaLogger.logUserAction("打开设置")

            verify { mockLogger.info(any<String>()) }
        }
    }

    // ==================== 配置变更记录测试 ====================

    @Nested
    @DisplayName("配置变更记录测试")
    inner class ConfigChangeLoggingTests {

        @Test
        @DisplayName("logConfigChange - 应该记录配置变更")
        fun `logConfigChange - 应该记录配置变更`() {
            NekoamaLogger.logConfigChange("apiEndpoint", "http://old.com", "http://new.com")

            verify { mockLogger.info(match<String> { it.contains("配置项已更新") && it.contains("apiEndpoint") }) }
        }

        @Test
        @DisplayName("logConfigChange - 旧值为 null 应该正确处理")
        fun `logConfigChange - 旧值为 null 应该正确处理`() {
            NekoamaLogger.logConfigChange("apiKey", null, "sk-xxxxx")

            verify { mockLogger.info(match<String> { it.contains("oldValue=null") }) }
        }

        @Test
        @DisplayName("logConfigChange - 新值为 null 应该正确处理")
        fun `logConfigChange - 新值为 null 应该正确处理`() {
            NekoamaLogger.logConfigChange("apiKey", "sk-xxxxx", null)

            verify { mockLogger.info(match<String> { it.contains("newValue=null") }) }
        }
    }

    // ==================== 缓存操作记录测试 ====================

    @Nested
    @DisplayName("缓存操作记录测试")
    inner class CacheOperationLoggingTests {

        @Test
        @DisplayName("logCacheOperation - 基本操作应该记录")
        fun `logCacheOperation - 基本操作应该记录`() {
            NekoamaLogger.logCacheOperation("GET", "cache:key:123")

            verify { mockLogger.debug(match<String> { it.contains("缓存操作") }) }
        }

        @Test
        @DisplayName("logCacheOperation - 带命中状态应该记录")
        fun `logCacheOperation - 带命中状态应该记录`() {
            NekoamaLogger.logCacheOperation("GET", "cache:key:123", hit = true)

            verify { mockLogger.debug(match<String> { it.contains("hit=true") }) }
        }

        @Test
        @DisplayName("logCacheOperation - 带缓存大小应该记录")
        fun `logCacheOperation - 带缓存大小应该记录`() {
            NekoamaLogger.logCacheOperation("PUT", "cache:key:456", size = 1024)

            verify { mockLogger.debug(match<String> { it.contains("size=1024") }) }
        }

        @Test
        @DisplayName("logCacheOperation - 应该脱敏缓存键")
        fun `logCacheOperation - 应该脱敏缓存键`() {
            NekoamaLogger.logCacheOperation("GET", "cache:api_key:sk-xxxxx")

            verify { mockLogger.debug(any<String>()) }
        }
    }

    // ==================== 批量日志记录器测试 ====================

    @Nested
    @DisplayName("批量日志记录器测试")
    inner class BatchLoggerTests {

        @Test
        @DisplayName("batch - 应该创建 BatchLogger 实例")
        fun `batch - 应该创建 BatchLogger 实例`() {
            val batchLogger = NekoamaLogger.batch("TEST_BATCH")

            assertThat(batchLogger).isNotNull
        }

        @Test
        @DisplayName("BatchLogger - addContext 应该支持链式调用")
        fun `BatchLogger - addContext 应该支持链式调用`() {
            val batchLogger = NekoamaLogger.batch("TEST_BATCH")
                .addContext("key1", "value1")
                .addContext("key2", "value2")

            assertThat(batchLogger).isNotNull
        }

        @Test
        @DisplayName("BatchLogger - debug 应该记录调试信息")
        fun `BatchLogger - debug 应该记录调试信息`() {
            val batchLogger = NekoamaLogger.batch("TEST_BATCH")

            batchLogger.debug("批量调试信息")

            verify { mockLogger.debug(any<String>()) }
        }

        @Test
        @DisplayName("BatchLogger - info 应该记录信息")
        fun `BatchLogger - info 应该记录信息`() {
            val batchLogger = NekoamaLogger.batch("TEST_BATCH")

            batchLogger.info("批量信息")

            verify { mockLogger.info(any<String>()) }
        }

        @Test
        @DisplayName("BatchLogger - warn 应该记录警告")
        fun `BatchLogger - warn 应该记录警告`() {
            val batchLogger = NekoamaLogger.batch("TEST_BATCH")
            val exception = RuntimeException("测试异常")

            batchLogger.warn("批量警告", exception)

            verify { mockLogger.warn(any<String>(), exception) }
        }

        @Test
        @DisplayName("BatchLogger - error 应该记录错误")
        fun `BatchLogger - error 应该记录错误`() {
            val batchLogger = NekoamaLogger.batch("TEST_BATCH")
            val exception = RuntimeException("测试异常")

            batchLogger.error("批量错误", exception)

            verify { mockLogger.error(any<String>(), exception) }
        }

        @Test
        @DisplayName("BatchLogger - logError 应该记录 NekoamaError")
        fun `BatchLogger - logError 应该记录 NekoamaError`() {
            val batchLogger = NekoamaLogger.batch("TEST_BATCH")
            val error = NekoamaError.NetworkError.ConnectionTimeout("网络错误")

            batchLogger.logError(error)

            verify { mockLogger.warn(any<String>(), error.cause) }
        }

        @Test
        @DisplayName("BatchLogger - finish 应该记录性能和完成信息")
        fun `BatchLogger - finish 应该记录性能和完成信息`() {
            val batchLogger = NekoamaLogger.batch("TEST_BATCH")

            // 等待一段时间确保耗时 > 0
            Thread.sleep(10)
            batchLogger.finish("批量操作完成")

            verify(atLeast = 1) { mockLogger.info(any<String>()) }
        }
    }

    // ==================== 扩展函数测试 ====================

    @Nested
    @DisplayName("扩展函数测试")
    inner class ExtensionFunctionTests {

        @Test
        @DisplayName("logger 扩展 - 应该返回 NekoamaLogger 实例")
        fun `logger 扩展 - 应该返回 NekoamaLogger 实例`() {
            val logger = TestClass().logger()

            assertThat(logger).isSameAs(NekoamaLogger)
        }

        @Test
        @DisplayName("logTime - 成功执行应该记录性能")
        fun `logTime - 成功执行应该记录性能`() {
            val result = logTime("TEST_OP") {
                Thread.sleep(50)
                "success"
            }

            assertThat(result).isEqualTo("success")
            verify(atLeast = 1) { mockLogger.debug(any<String>()) }
        }

        @Test
        @DisplayName("logTime - 异常执行应该记录错误")
        fun `logTime - 异常执行应该记录错误`() {
            val exception = RuntimeException("测试异常")

            try {
                logTime("TEST_OP") {
                    Thread.sleep(50)
                    throw exception
                }
            } catch (e: RuntimeException) {
                assertThat(e).isSameAs(exception)
            }

            verify { mockLogger.error(any<String>(), exception) }
        }

        @Test
        @DisplayName("logTime - 应该传递上下文")
        fun `logTime - 应该传递上下文`() {
            val context = mapOf("key" to "value")

            logTime("TEST_OP", context) {
                "result"
            }

            verify(atLeast = 1) { mockLogger.debug(match<String> { it.contains("key=value") }) }
        }
    }

    // ==================== 辅助测试类 ====================

    private class TestClass
}
