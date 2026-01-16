package com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config

import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.shared.model.NekoamaResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 自定义生成器配置测试
 *
 * 验证 CustomGeneratorConfig 的配置验证和功能方法
 */
@DisplayName("自定义生成器配置测试")
class CustomGeneratorConfigTest {

    // ==================== 默认值测试 ====================

    @Nested
    @DisplayName("默认值测试")
    inner class DefaultValueTests {

        @Test
        @DisplayName("默认值 - maxTokens 应该为 150")
        fun `默认值 - maxTokens 应该为 150`() {
            val config = createValidConfig().copy(
                maxTokens = 150
            )

            assertThat(config.maxTokens).isEqualTo(150)
        }

        @Test
        @DisplayName("默认值 - temperature 应该为 0.7")
        fun `默认值 - temperature 应该为 0_7`() {
            val config = createValidConfig().copy(
                temperature = 0.7
            )

            assertThat(config.temperature).isEqualTo(0.7)
        }

        @Test
        @DisplayName("默认值 - timeoutMs 应该为 30000")
        fun `默认值 - timeoutMs 应该为 30000`() {
            val config = createValidConfig().copy(
                timeoutMs = 30000
            )

            assertThat(config.timeoutMs).isEqualTo(30000)
        }

        @Test
        @DisplayName("默认值 - maxRetries 应该为 3")
        fun `默认值 - maxRetries 应该为 3`() {
            val config = createValidConfig().copy(
                maxRetries = 3
            )

            assertThat(config.maxRetries).isEqualTo(3)
        }

        @Test
        @DisplayName("默认值 - authType 应该为 BEARER_TOKEN")
        fun `默认值 - authType 应该为 BEARER_TOKEN`() {
            val config = createValidConfig().copy(
                authType = AuthenticationType.BEARER_TOKEN
            )

            assertThat(config.authType).isEqualTo(AuthenticationType.BEARER_TOKEN)
        }

        @Test
        @DisplayName("默认值 - pathTemplate 应该为 /chat/completions")
        fun `默认值 - pathTemplate 应该为 _chat_completions`() {
            val config = createValidConfig()

            assertThat(config.pathTemplate).isEqualTo("/chat/completions")
        }

        @Test
        @DisplayName("默认值 - verifySSL 应该为 true")
        fun `默认值 - verifySSL 应该为 true`() {
            val config = createValidConfig()

            assertThat(config.verifySSL).isTrue()
        }
    }

    // ==================== 验证测试 ====================

    @Nested
    @DisplayName("配置验证测试")
    inner class ValidationTests {

        @Test
        @DisplayName("validate - 有效配置应该成功")
        fun `validate - 有效配置应该成功`() {
            val config = createValidConfig()

            val result = config.validate()

            assertThat(result.isSuccess).isTrue()
        }

        @Test
        @DisplayName("validate - 空生成器名称应该失败")
        fun `validate - 空生成器名称应该失败`() {
            val config = createValidConfig().copy(
                generatorName = ""
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
            assertThat(result.errorOrNull()).isInstanceOf(NekoamaError.ParseError.InvalidConfiguration::class.java)
        }

        @Test
        @DisplayName("validate - 空 API URL 应该失败")
        fun `validate - 空 API URL 应该失败`() {
            val config = createValidConfig().copy(
                apiUrl = ""
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
        }

        @Test
        @DisplayName("validate - 非 http 开头的 URL 应该失败")
        fun `validate - 非 http 开头的 URL 应该失败`() {
            val config = createValidConfig().copy(
                apiUrl = "ftp://api.example.com"
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
        }

        @Test
        @DisplayName("validate - 空 API Key 应该失败")
        fun `validate - 空 API Key 应该失败`() {
            val config = createValidConfig().copy(
                apiKey = ""
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
            assertThat(result.errorOrNull()).isInstanceOf(NekoamaError.AuthenticationError.ApiKeyNotConfigured::class.java)
        }

        @Test
        @DisplayName("validate - 空模型名称应该失败")
        fun `validate - 空模型名称应该失败`() {
            val config = createValidConfig().copy(
                model = ""
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
        }

        @Test
        @DisplayName("validate - maxTokens 为 0 应该失败")
        fun `validate - maxTokens 为 0 应该失败`() {
            val config = createValidConfig().copy(
                maxTokens = 0
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
        }

        @Test
        @DisplayName("validate - maxTokens 为负数应该失败")
        fun `validate - maxTokens 为负数应该失败`() {
            val config = createValidConfig().copy(
                maxTokens = -100
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
        }

        @Test
        @DisplayName("validate - temperature 为负数应该失败")
        fun `validate - temperature 为负数应该失败`() {
            val config = createValidConfig().copy(
                temperature = -0.5
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
        }

        @Test
        @DisplayName("validate - temperature 超过 2.0 应该失败")
        fun `validate - temperature 超过 2_0 应该失败`() {
            val config = createValidConfig().copy(
                temperature = 2.5
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
        }

        @Test
        @DisplayName("validate - timeoutMs 为 0 应该失败")
        fun `validate - timeoutMs 为 0 应该失败`() {
            val config = createValidConfig().copy(
                timeoutMs = 0
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
        }

        @Test
        @DisplayName("validate - maxRetries 为负数应该失败")
        fun `validate - maxRetries 为负数应该失败`() {
            val config = createValidConfig().copy(
                maxRetries = -1
            )

            val result = config.validate()

            assertThat(result.isError).isTrue()
        }
    }

    // ==================== 端点 URL 构建测试 ====================

    @Nested
    @DisplayName("端点 URL 构建测试")
    inner class EndpointUrlBuilderTests {

        @Test
        @DisplayName("buildEndpointUrl - 标准配置应该返回完整 URL")
        fun `buildEndpointUrl - 标准配置应该返回完整 URL`() {
            val config = createValidConfig().copy(
                apiUrl = "https://api.example.com"
            )

            val url = config.buildEndpointUrl()

            assertThat(url).isEqualTo("https://api.example.com/chat/completions")
        }

        @Test
        @DisplayName("buildEndpointUrl - 应该移除末尾斜杠")
        fun `buildEndpointUrl - 应该移除末尾斜杠`() {
            val config = createValidConfig().copy(
                apiUrl = "https://api.example.com/"
            )

            val url = config.buildEndpointUrl()

            assertThat(url).isEqualTo("https://api.example.com/chat/completions")
        }

        @Test
        @DisplayName("buildEndpointUrl - Azure 配置应该包含部署路径")
        fun `buildEndpointUrl - Azure 配置应该包含部署路径`() {
            val config = createValidConfig().copy(
                apiUrl = "https://api.openai.com",
                deploymentName = "my-deployment",
                apiVersion = "2023-05-15"
            )

            val url = config.buildEndpointUrl()

            assertThat(url).isEqualTo("https://api.openai.com/openai/deployments/my-deployment/chat/completions?api-version=2023-05-15")
        }

        @Test
        @DisplayName("buildEndpointUrl - 自定义路径模板应该被使用")
        fun `buildEndpointUrl - 自定义路径模板应该被使用`() {
            val config = createValidConfig().copy(
                apiUrl = "https://custom.api.com",
                pathTemplate = "/v1/chat"
            )

            val url = config.buildEndpointUrl()

            assertThat(url).isEqualTo("https://custom.api.com/v1/chat")
        }
    }

    // ==================== 认证头部测试 ====================

    @Nested
    @DisplayName("认证头部测试")
    inner class AuthHeadersTests {

        @Test
        @DisplayName("getAuthHeaders - BEARER_TOKEN 应该生成 Bearer 认证")
        fun `getAuthHeaders - BEARER_TOKEN 应该生成 Bearer 认证`() {
            val config = createValidConfig().copy(
                apiKey = "sk-test-key",
                authType = AuthenticationType.BEARER_TOKEN
            )

            val headers = config.getAuthHeaders()

            assertThat(headers["Authorization"]).isEqualTo("Bearer sk-test-key")
        }

        @Test
        @DisplayName("getAuthHeaders - API_KEY_HEADER 应该生成 api-key 头部")
        fun `getAuthHeaders - API_KEY_HEADER 应该生成 api-key 头部`() {
            val config = createValidConfig().copy(
                apiKey = "test-key",
                authType = AuthenticationType.API_KEY_HEADER
            )

            val headers = config.getAuthHeaders()

            assertThat(headers["api-key"]).isEqualTo("test-key")
        }

        @Test
        @DisplayName("getAuthHeaders - X_API_KEY 应该生成 X-API-Key 头部")
        fun `getAuthHeaders - X_API_KEY 应该生成 X-API-Key 头部`() {
            val config = createValidConfig().copy(
                apiKey = "custom-key",
                authType = AuthenticationType.X_API_KEY
            )

            val headers = config.getAuthHeaders()

            assertThat(headers["X-API-Key"]).isEqualTo("custom-key")
        }

        @Test
        @DisplayName("getAuthHeaders - CUSTOM 不应该添加认证头部")
        fun `getAuthHeaders - CUSTOM 不应该添加认证头部`() {
            val config = createValidConfig().copy(
                apiKey = "ignored",
                authType = AuthenticationType.CUSTOM
            )

            val headers = config.getAuthHeaders()

            assertThat(headers.containsKey("Authorization")).isFalse()
            assertThat(headers.containsKey("api-key")).isFalse()
            assertThat(headers.containsKey("X-API-Key")).isFalse()
        }

        @Test
        @DisplayName("getAuthHeaders - 应该添加组织 ID")
        fun `getAuthHeaders - 应该添加组织 ID`() {
            val config = createValidConfig().copy(
                organizationId = "org-123"
            )

            val headers = config.getAuthHeaders()

            assertThat(headers["OpenAI-Organization"]).isEqualTo("org-123")
        }

        @Test
        @DisplayName("getAuthHeaders - 应该合并自定义头部")
        fun `getAuthHeaders - 应该合并自定义头部`() {
            val config = createValidConfig().copy(
                customHeaders = mapOf("X-Custom-Header" to "custom-value")
            )

            val headers = config.getAuthHeaders()

            assertThat(headers["X-Custom-Header"]).isEqualTo("custom-value")
        }

        @Test
        @DisplayName("getAuthHeaders - 自定义头部应该覆盖默认头部")
        fun `getAuthHeaders - 自定义头部应该覆盖默认头部`() {
            val config = createValidConfig().copy(
                authType = AuthenticationType.BEARER_TOKEN,
                apiKey = "original-key",
                customHeaders = mapOf("Authorization" to "Custom token123")
            )

            val headers = config.getAuthHeaders()

            assertThat(headers["Authorization"]).isEqualTo("Custom token123")
        }
    }

    // ==================== 辅助方法 ====================

    private fun createValidConfig() = CustomGeneratorConfig(
        generatorName = "TestGenerator",
        apiUrl = "https://api.example.com",
        apiKey = "test-api-key",
        model = "gpt-4"
    )
}
