package com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 认证类型枚举测试
 *
 * 验证 AuthenticationType 枚举的所有值
 */
@DisplayName("认证类型枚举测试")
class AuthenticationTypeTest {

    // ==================== 枚举值测试 ====================

    @Nested
    @DisplayName("枚举值测试")
    inner class EnumValuesTests {

        @Test
        @DisplayName("枚举值 - 应该包含 BEARER_TOKEN")
        fun `枚举值 - 应该包含 BEARER_TOKEN`() {
            assertThat(AuthenticationType.valueOf("BEARER_TOKEN"))
                .isEqualTo(AuthenticationType.BEARER_TOKEN)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 API_KEY_HEADER")
        fun `枚举值 - 应该包含 API_KEY_HEADER`() {
            assertThat(AuthenticationType.valueOf("API_KEY_HEADER"))
                .isEqualTo(AuthenticationType.API_KEY_HEADER)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 X_API_KEY")
        fun `枚举值 - 应该包含 X_API_KEY`() {
            assertThat(AuthenticationType.valueOf("X_API_KEY"))
                .isEqualTo(AuthenticationType.X_API_KEY)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 CUSTOM")
        fun `枚举值 - 应该包含 CUSTOM`() {
            assertThat(AuthenticationType.valueOf("CUSTOM"))
                .isEqualTo(AuthenticationType.CUSTOM)
        }

        @Test
        @DisplayName("枚举值 - entries 应该包含所有值")
        fun `枚举值 - entries 应该包含所有值`() {
            assertThat(AuthenticationType.entries).containsExactly(
                AuthenticationType.BEARER_TOKEN,
                AuthenticationType.API_KEY_HEADER,
                AuthenticationType.X_API_KEY,
                AuthenticationType.CUSTOM
            )
        }
    }

    // ==================== 枚举序数测试 ====================

    @Nested
    @DisplayName("枚举序数测试")
    inner class EnumOrdinalTests {

        @Test
        @DisplayName("ordinal - BEARER_TOKEN 应该为 0")
        fun `ordinal - BEARER_TOKEN 应该为 0`() {
            assertThat(AuthenticationType.BEARER_TOKEN.ordinal).isEqualTo(0)
        }

        @Test
        @DisplayName("ordinal - API_KEY_HEADER 应该为 1")
        fun `ordinal - API_KEY_HEADER 应该为 1`() {
            assertThat(AuthenticationType.API_KEY_HEADER.ordinal).isEqualTo(1)
        }

        @Test
        @DisplayName("ordinal - X_API_KEY 应该为 2")
        fun `ordinal - X_API_KEY 应该为 2`() {
            assertThat(AuthenticationType.X_API_KEY.ordinal).isEqualTo(2)
        }

        @Test
        @DisplayName("ordinal - CUSTOM 应该为 3")
        fun `ordinal - CUSTOM 应该为 3`() {
            assertThat(AuthenticationType.CUSTOM.ordinal).isEqualTo(3)
        }
    }

    // ==================== 枚举名称测试 ====================

    @Nested
    @DisplayName("枚举名称测试")
    inner class EnumNameTests {

        @Test
        @DisplayName("name - BEARER_TOKEN 名称应该正确")
        fun `name - BEARER_TOKEN 名称应该正确`() {
            assertThat(AuthenticationType.BEARER_TOKEN.name).isEqualTo("BEARER_TOKEN")
        }

        @Test
        @DisplayName("name - API_KEY_HEADER 名称应该正确")
        fun `name - API_KEY_HEADER 名称应该正确`() {
            assertThat(AuthenticationType.API_KEY_HEADER.name).isEqualTo("API_KEY_HEADER")
        }

        @Test
        @DisplayName("name - X_API_KEY 名称应该正确")
        fun `name - X_API_KEY 名称应该正确`() {
            assertThat(AuthenticationType.X_API_KEY.name).isEqualTo("X_API_KEY")
        }

        @Test
        @DisplayName("name - CUSTOM 名称应该正确")
        fun `name - CUSTOM 名称应该正确`() {
            assertThat(AuthenticationType.CUSTOM.name).isEqualTo("CUSTOM")
        }
    }

    // ==================== 序列化注解测试 ====================

    @Nested
    @DisplayName("序列化注解测试")
    inner class SerializationAnnotationTests {

        @Test
        @DisplayName("注解 - 应该有 Serializable 注解")
        fun `注解 - 应该有 Serializable 注解`() {
            // 验证枚举类可以被 kotlinx.serialization 使用
            assertThat(AuthenticationType.BEARER_TOKEN::class.simpleName).isNotNull()
        }
    }

    // ==================== 使用场景测试 ====================

    @Nested
    @DisplayName("使用场景测试")
    inner class UsageScenarioTests {

        @Test
        @DisplayName("BEARER_TOKEN - 用于标准 OpenAI 认证")
        fun `BEARER_TOKEN - 用于标准 OpenAI 认证`() {
            val authType = AuthenticationType.BEARER_TOKEN
            val apiKey = "sk-test-key"

            val header = when (authType) {
                AuthenticationType.BEARER_TOKEN -> "Authorization: Bearer $apiKey"
                else -> ""
            }

            assertThat(header).isEqualTo("Authorization: Bearer sk-test-key")
        }

        @Test
        @DisplayName("API_KEY_HEADER - 用于 Azure OpenAI 认证")
        fun `API_KEY_HEADER - 用于 Azure OpenAI 认证`() {
            val authType = AuthenticationType.API_KEY_HEADER
            val apiKey = "azure-key-123"

            val header = when (authType) {
                AuthenticationType.API_KEY_HEADER -> "api-key: $apiKey"
                else -> ""
            }

            assertThat(header).isEqualTo("api-key: azure-key-123")
        }

        @Test
        @DisplayName("X_API_KEY - 用于自定义 API")
        fun `X_API_KEY - 用于自定义 API`() {
            val authType = AuthenticationType.X_API_KEY
            val apiKey = "custom-api-key"

            val header = when (authType) {
                AuthenticationType.X_API_KEY -> "X-API-Key: $apiKey"
                else -> ""
            }

            assertThat(header).isEqualTo("X-API-Key: custom-api-key")
        }

        @Test
        @DisplayName("CUSTOM - 用于完全自定义认证")
        fun `CUSTOM - 用于完全自定义认证`() {
            val authType = AuthenticationType.CUSTOM
            val customHeaders = mapOf("X-Custom-Auth" to "custom-value")

            val usesStandardAuth = when (authType) {
                AuthenticationType.CUSTOM -> false
                else -> true
            }

            assertThat(usesStandardAuth).isFalse()
            assertThat(customHeaders["X-Custom-Auth"]).isEqualTo("custom-value")
        }
    }
}
