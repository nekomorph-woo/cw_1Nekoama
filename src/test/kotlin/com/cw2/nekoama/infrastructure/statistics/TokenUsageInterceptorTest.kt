package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.service.StatisticsService
import com.cw2.nekoama.domain.statistics.service.TokenUsageData
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

@DisplayName("TokenUsageInterceptor - Token 拦截测试")
class TokenUsageInterceptorTest {

    private lateinit var interceptor: TokenUsageInterceptor
    private lateinit var mockStatisticsService: StatisticsService
    private lateinit var mockChain: Interceptor.Chain

    @BeforeEach
    fun setUp() {
        mockStatisticsService = mockk()
        interceptor = TokenUsageInterceptor(mockStatisticsService)
        mockChain = mockk()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    @DisplayName("拦截成功响应 - 应该提取 usage 数据并记录")
    fun `拦截成功响应 - 应该提取 usage 数据并记录`() = runTest {
        // Given
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .build()

        val responseBody = """
        {
            "id": "chatcmpl-123",
            "object": "chat.completion",
            "usage": {
                "prompt_tokens": 100,
                "completion_tokens": 50,
                "total_tokens": 150
            }
        }
        """.trimIndent()

        val response = Response.Builder()
            .request(request)
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .body(ResponseBody.create("application/json".toMediaType(), responseBody))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response
        coEvery { mockStatisticsService.recordTokenUsage(any()) } just Runs

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.code).isEqualTo(200)

        // Verify async call was made
        coVerify(timeout = 1000) { mockStatisticsService.recordTokenUsage(TokenUsageData(100, 50, 150)) }
    }

    @Test
    @DisplayName("拦截无 usage 字段响应 - 应该静默忽略")
    fun `拦截无 usage 字段响应 - 应该静默忽略`() = runTest {
        // Given
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .build()

        val responseBody = """
        {
            "id": "chatcmpl-123",
            "object": "chat.completion"
        }
        """.trimIndent()

        val response = Response.Builder()
            .request(request)
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .body(ResponseBody.create("application/json".toMediaType(), responseBody))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertThat(result).isNotNull()
        coVerify(exactly = 0) { mockStatisticsService.recordTokenUsage(any()) }
    }

    @Test
    @DisplayName("拦截失败响应 - 应该直接返回不记录")
    fun `拦截失败响应 - 应该直接返回不记录`() = runTest {
        // Given
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .build()

        val response = Response.Builder()
            .request(request)
            .code(401)
            .protocol(Protocol.HTTP_1_1)
            .message("Unauthorized")
            .body(ResponseBody.create("application/json".toMediaType(), "{}"))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertThat(result.code).isEqualTo(401)
        coVerify(exactly = 0) { mockStatisticsService.recordTokenUsage(any()) }
    }

    @Test
    @DisplayName("JSON 解析失败 - 应该返回原始响应")
    fun `JSON 解析失败 - 应该返回原始响应`() = runTest {
        // Given
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .build()

        val response = Response.Builder()
            .request(request)
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .body(ResponseBody.create("application/json".toMediaType(), "invalid json"))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.code).isEqualTo(200)
    }

    @Test
    @DisplayName("空响应体 - 应该直接返回不记录")
    fun `空响应体 - 应该直接返回不记录`() = runTest {
        // Given
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .build()

        val response = Response.Builder()
            .request(request)
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .body(null)
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertThat(result).isNotNull()
        coVerify(exactly = 0) { mockStatisticsService.recordTokenUsage(any()) }
    }

    @Test
    @DisplayName("usage 字段缺少必要字段 - 应该使用默认值 0")
    fun `usage 字段缺少必要字段 - 应该使用默认值 0`() = runTest {
        // Given
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .build()

        val responseBody = """
        {
            "id": "chatcmpl-123",
            "usage": {
                "total_tokens": 150
            }
        }
        """.trimIndent()

        val response = Response.Builder()
            .request(request)
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .body(ResponseBody.create("application/json".toMediaType(), responseBody))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response
        coEvery { mockStatisticsService.recordTokenUsage(any()) } just Runs

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.code).isEqualTo(200)

        // Verify with default values for missing fields
        coVerify(timeout = 1000) { mockStatisticsService.recordTokenUsage(TokenUsageData(0, 0, 150)) }
    }
}
