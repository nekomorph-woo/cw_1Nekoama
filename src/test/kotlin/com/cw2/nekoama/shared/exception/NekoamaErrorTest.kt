package com.cw2.nekoama.shared.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Nekoama 异常系统测试
 *
 * 验证异常类型的创建和功能方法
 */
@DisplayName("Nekoama 异常系统测试")
class NekoamaErrorTest {

    // ==================== 网络错误测试 ====================

    @Nested
    @DisplayName("网络错误测试")
    inner class NetworkErrorTests {

        @Test
        @DisplayName("ConnectionTimeout - 应该创建正确的错误")
        fun `ConnectionTimeout - 应该创建正确的错误`() {
            val error = NekoamaError.NetworkError.ConnectionTimeout()

            assertThat(error.message).isNotEmpty()
            assertThat(error.cause).isNull()
        }

        @Test
        @DisplayName("ConnectionTimeout - 应该能够传递 cause")
        fun `ConnectionTimeout - 应该能够传递 cause`() {
            val cause = RuntimeException("底层连接错误")
            val error = NekoamaError.NetworkError.ConnectionTimeout(cause = cause)

            assertThat(error.cause).isSameAs(cause)
        }

        @Test
        @DisplayName("ReadTimeout - getEnglishMessage 应该返回正确消息")
        fun `ReadTimeout - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.NetworkError.ReadTimeout()

            assertThat(error.getEnglishMessage()).isEqualTo("Service response timeout")
        }

        @Test
        @DisplayName("Generic - getEnglishMessage 应该返回通用消息")
        fun `Generic - getEnglishMessage 应该返回通用消息`() {
            val error = NekoamaError.NetworkError.Generic()

            assertThat(error.getEnglishMessage()).isEqualTo("Network request failed")
        }
    }

    // ==================== 认证错误测试 ====================

    @Nested
    @DisplayName("认证错误测试")
    inner class AuthenticationErrorTests {

        @Test
        @DisplayName("InvalidApiKey - getEnglishMessage 应该返回正确消息")
        fun `InvalidApiKey - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.AuthenticationError.InvalidApiKey()

            assertThat(error.getEnglishMessage()).isEqualTo("Invalid API key")
        }

        @Test
        @DisplayName("ApiKeyNotConfigured - getEnglishMessage 应该返回正确消息")
        fun `ApiKeyNotConfigured - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.AuthenticationError.ApiKeyNotConfigured()

            assertThat(error.getEnglishMessage()).isEqualTo("API key not configured")
        }

        @Test
        @DisplayName("InsufficientPermissions - getEnglishMessage 应该返回正确消息")
        fun `InsufficientPermissions - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.AuthenticationError.InsufficientPermissions()

            assertThat(error.getEnglishMessage()).isEqualTo("Insufficient API permissions")
        }
    }

    // ==================== 限流错误测试 ====================

    @Nested
    @DisplayName("限流错误测试")
    inner class RateLimitErrorTests {

        @Test
        @DisplayName("TooManyRequests - getEnglishMessage 应该返回正确消息")
        fun `TooManyRequests - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.RateLimitError.TooManyRequests()

            assertThat(error.getEnglishMessage()).isEqualTo("Too many requests, please retry later")
        }

        @Test
        @DisplayName("TooManyRequests - 应该能够设置 retryAfter")
        fun `TooManyRequests - 应该能够设置 retryAfter`() {
            val error = NekoamaError.RateLimitError.TooManyRequests(retryAfter = 5000)

            assertThat(error.retryAfter).isEqualTo(5000)
        }

        @Test
        @DisplayName("QuotaExhausted - getEnglishMessage 应该返回正确消息")
        fun `QuotaExhausted - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.RateLimitError.QuotaExhausted()

            assertThat(error.getEnglishMessage()).isEqualTo("API quota exhausted")
        }
    }

    // ==================== API 错误测试 ====================

    @Nested
    @DisplayName("API 错误测试")
    inner class APIErrorTests {

        @Test
        @DisplayName("ServerError - httpCode 应该为 500")
        fun `ServerError - httpCode 应该为 500`() {
            val error = NekoamaError.APIError.ServerError()

            assertThat(error.httpCode).isEqualTo(500)
        }

        @Test
        @DisplayName("ServerError - getEnglishMessage 应该返回正确消息")
        fun `ServerError - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.APIError.ServerError()

            assertThat(error.getEnglishMessage()).isEqualTo("AI service internal error")
        }

        @Test
        @DisplayName("BadRequest - httpCode 应该为 400")
        fun `BadRequest - httpCode 应该为 400`() {
            val error = NekoamaError.APIError.BadRequest()

            assertThat(error.httpCode).isEqualTo(400)
        }

        @Test
        @DisplayName("ModelNotSupported - getEnglishMessage 应该返回正确消息")
        fun `ModelNotSupported - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.APIError.ModelNotSupported()

            assertThat(error.getEnglishMessage()).isEqualTo("AI model not supported")
        }
    }

    // ==================== 解析错误测试 ====================

    @Nested
    @DisplayName("解析错误测试")
    inner class ParseErrorTests {

        @Test
        @DisplayName("JsonParse - getEnglishMessage 应该返回正确消息")
        fun `JsonParse - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.ParseError.JsonParse()

            assertThat(error.getEnglishMessage()).isEqualTo("JSON parsing failed")
        }

        @Test
        @DisplayName("InvalidResponse - getEnglishMessage 应该返回正确消息")
        fun `InvalidResponse - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.ParseError.InvalidResponse()

            assertThat(error.getEnglishMessage()).isEqualTo("Invalid AI service response")
        }

        @Test
        @DisplayName("InvalidConfiguration - getEnglishMessage 应该返回正确消息")
        fun `InvalidConfiguration - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.ParseError.InvalidConfiguration()

            assertThat(error.getEnglishMessage()).isEqualTo("Invalid configuration")
        }
    }

    // ==================== 平台错误测试 ====================

    @Nested
    @DisplayName("平台错误测试")
    inner class PlatformErrorTests {

        @Test
        @DisplayName("IndexNotReady - getEnglishMessage 应该返回正确消息")
        fun `IndexNotReady - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.PlatformError.IndexNotReady()

            assertThat(error.getEnglishMessage()).isEqualTo("IDE index not ready")
        }

        @Test
        @DisplayName("ProjectNotOpen - getEnglishMessage 应该返回正确消息")
        fun `ProjectNotOpen - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.PlatformError.ProjectNotOpen()

            assertThat(error.getEnglishMessage()).isEqualTo("Project not open")
        }

        @Test
        @DisplayName("EditorUnavailable - getEnglishMessage 应该返回正确消息")
        fun `EditorUnavailable - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.PlatformError.EditorUnavailable()

            assertThat(error.getEnglishMessage()).isEqualTo("Editor unavailable")
        }
    }

    // ==================== 超时错误测试 ====================

    @Nested
    @DisplayName("超时错误测试")
    inner class TimeoutErrorTests {

        @Test
        @DisplayName("OperationTimeout - getEnglishMessage 应该返回正确消息")
        fun `OperationTimeout - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.TimeoutError.OperationTimeout()

            assertThat(error.getEnglishMessage()).isEqualTo("Operation timeout")
        }

        @Test
        @DisplayName("OperationTimeout - 应该能够设置超时信息")
        fun `OperationTimeout - 应该能够设置超时信息`() {
            val error = NekoamaError.TimeoutError.OperationTimeout(
                timeoutMs = 30000,
                actualDurationMs = 35000
            )

            assertThat(error.timeoutMs).isEqualTo(30000)
            assertThat(error.actualDurationMs).isEqualTo(35000)
        }

        @Test
        @DisplayName("RequestTimeout - getEnglishMessage 应该返回正确消息")
        fun `RequestTimeout - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.TimeoutError.RequestTimeout()

            assertThat(error.getEnglishMessage()).isEqualTo("Request timeout")
        }
    }

    // ==================== 操作取消和未知错误测试 ====================

    @Nested
    @DisplayName("操作取消和未知错误测试")
    inner class MiscellaneousErrorTests {

        @Test
        @DisplayName("OperationCancelled - getEnglishMessage 应该返回正确消息")
        fun `OperationCancelled - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.OperationCancelled()

            assertThat(error.getEnglishMessage()).isEqualTo("Operation cancelled")
        }

        @Test
        @DisplayName("Unknown - getEnglishMessage 应该返回正确消息")
        fun `Unknown - getEnglishMessage 应该返回正确消息`() {
            val error = NekoamaError.Unknown()

            assertThat(error.getEnglishMessage()).isEqualTo("Unknown error")
        }
    }

    // ==================== 密封类层次测试 ====================

    @Nested
    @DisplayName("密封类层次测试")
    inner class SealedHierarchyTests {

        @Test
        @DisplayName("when 表达式 - 应该能够区分所有错误类型")
        fun `when 表达式 - 应该能够区分所有错误类型`() {
            val errors: List<NekoamaError> = listOf(
                NekoamaError.NetworkError.ConnectionTimeout(),
                NekoamaError.AuthenticationError.InvalidApiKey(),
                NekoamaError.ParseError.JsonParse(),
                NekoamaError.OperationCancelled()
            )

            val results = errors.map { error ->
                when (error) {
                    is NekoamaError.NetworkError -> "Network"
                    is NekoamaError.AuthenticationError -> "Auth"
                    is NekoamaError.ParseError -> "Parse"
                    is NekoamaError.OperationCancelled -> "Cancelled"
                    else -> "Other"
                }
            }

            assertThat(results).containsExactly("Network", "Auth", "Parse", "Cancelled")
        }

        @Test
        @DisplayName("is 检查 - 应该能够识别错误类型")
        fun `is 检查 - 应该能够识别错误类型`() {
            val error: NekoamaError = NekoamaError.NetworkError.ConnectionTimeout()

            assertThat(error is NekoamaError.NetworkError).isTrue()
            assertThat(error is NekoamaError.NetworkError.ConnectionTimeout).isTrue()
            assertThat(error is NekoamaError.AuthenticationError).isFalse()
        }
    }

    // ==================== Cause 传播测试 ====================

    @Nested
    @DisplayName("Cause 传播测试")
    inner class CausePropagationTests {

        @Test
        @DisplayName("错误 - 应该传递底层异常")
        fun `错误 - 应该传递底层异常`() {
            val cause = RuntimeException("底层错误")
            val error = NekoamaError.NetworkError.Generic(cause = cause)

            assertThat(error.cause).isSameAs(cause)
        }

        @Test
        @DisplayName("错误 - cause 可以为 null")
        fun `错误 - cause 可以为 null`() {
            val error = NekoamaError.ParseError.JsonParse(cause = null)

            assertThat(error.cause).isNull()
        }

        @Test
        @DisplayName("错误 - 默认情况下 cause 为 null")
        fun `错误 - 默认情况下 cause 为 null`() {
            val error = NekoamaError.APIError.ServerError()

            assertThat(error.cause).isNull()
        }
    }

    // ==================== 自定义消息测试 ====================

    @Nested
    @DisplayName("自定义消息测试")
    inner class CustomMessageTests {

        @Test
        @DisplayName("错误 - 应该能够使用自定义消息")
        fun `错误 - 应该能够使用自定义消息`() {
            val customMessage = "自定义错误消息"
            val error = NekoamaError.NetworkError.ConnectionTimeout(message = customMessage)

            assertThat(error.message).isEqualTo(customMessage)
        }

        @Test
        @DisplayName("错误 - getEnglishMessage 对于未匹配类型应该返回原始消息")
        fun `错误 - getEnglishMessage 对于未匹配类型应该返回原始消息`() {
            val customMessage = "Unmapped error message"
            val error = NekoamaError.UIError.DialogError(message = customMessage)

            assertThat(error.getEnglishMessage()).isEqualTo(customMessage)
        }
    }

    // ==================== 新增错误类型测试 ====================

    @Nested
    @DisplayName("新增错误类型测试")
    inner class NewErrorTypesTests {

        @Test
        @DisplayName("FileError - FileNotFoundError 应该创建")
        fun `FileError - FileNotFoundError 应该创建`() {
            val error = NekoamaError.FileError.FileNotFoundError("文件未找到")

            assertThat(error.message).isEqualTo("文件未找到")
        }

        @Test
        @DisplayName("AnalysisError - DependencyAnalysisError 应该创建")
        fun `AnalysisError - DependencyAnalysisError 应该创建`() {
            val error = NekoamaError.AnalysisError.DependencyAnalysisError("依赖分析失败")

            assertThat(error.message).isEqualTo("依赖分析失败")
        }

        @Test
        @DisplayName("ExportError - HtmlExportError 应该创建")
        fun `ExportError - HtmlExportError 应该创建`() {
            val error = NekoamaError.ExportError.HtmlExportError("HTML 导出失败")

            assertThat(error.message).isEqualTo("HTML 导出失败")
        }
    }
}
