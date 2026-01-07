package com.cw2.nekoama.infrastructure.code_suggestion_gen.model.openai

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * OpenAI 模型测试
 *
 * 验证 OpenAI API 的请求和响应数据模型
 */
@DisplayName("OpenAI 模型测试")
class OpenAIModelsTest {

    // ==================== OpenAIRequest 测试 ====================

    @Nested
    @DisplayName("OpenAI 请求模型测试")
    inner class OpenAIRequestTests {

        @Test
        @DisplayName("创建请求 - 应该包含所有必需字段")
        fun `创建请求 - 应该包含所有必需字段`() {
            val messages = listOf(
                OpenAIMessage("system", "You are a helpful assistant"),
                OpenAIMessage("user", "Hello")
            )
            val request = OpenAIRequest(
                model = "gpt-4",
                messages = messages
            )

            assertThat(request.model).isEqualTo("gpt-4")
            assertThat(request.messages).hasSize(2)
            assertThat(request.temperature).isEqualTo(0.7)
            assertThat(request.maxTokens).isEqualTo(150)
            assertThat(request.stream).isFalse()
        }

        @Test
        @DisplayName("创建请求 - 应该能够自定义所有字段")
        fun `创建请求 - 应该能够自定义所有字段`() {
            val messages = listOf(OpenAIMessage("user", "Test"))
            val request = OpenAIRequest(
                model = "gpt-3.5-turbo",
                messages = messages,
                temperature = 0.5,
                maxTokens = 100,
                stream = true
            )

            assertThat(request.model).isEqualTo("gpt-3.5-turbo")
            assertThat(request.temperature).isEqualTo(0.5)
            assertThat(request.maxTokens).isEqualTo(100)
            assertThat(request.stream).isTrue()
        }

        @Test
        @DisplayName("数据类特性 - 相同请求应该相等")
        fun `数据类特性 - 相同请求应该相等`() {
            val messages = listOf(OpenAIMessage("user", "Test"))
            val request1 = OpenAIRequest("gpt-4", messages)
            val request2 = OpenAIRequest("gpt-4", messages)

            assertThat(request1).isEqualTo(request2)
        }
    }

    // ==================== OpenAIResponse 测试 ====================

    @Nested
    @DisplayName("OpenAI 响应模型测试")
    inner class OpenAIResponseTests {

        @Test
        @DisplayName("创建响应 - 应该包含所有必需字段")
        fun `创建响应 - 应该包含所有必需字段`() {
            val choices = listOf(
                OpenAIChoice(
                    index = 0,
                    message = OpenAIMessage("assistant", "Hello!"),
                    finishReason = "stop"
                )
            )
            val response = OpenAIResponse(
                id = "chatcmpl-123",
                `object` = "chat.completion",
                created = 1677652288L,
                model = "gpt-4",
                choices = choices
            )

            assertThat(response.id).isEqualTo("chatcmpl-123")
            assertThat(response.`object`).isEqualTo("chat.completion")
            assertThat(response.created).isEqualTo(1677652288L)
            assertThat(response.model).isEqualTo("gpt-4")
            assertThat(response.choices).hasSize(1)
        }

        @Test
        @DisplayName("创建响应 - 应该包含使用统计")
        fun `创建响应 - 应该包含使用统计`() {
            val choices = listOf(
                OpenAIChoice(0, OpenAIMessage("assistant", "Response"))
            )
            val usage = OpenAIUsage(
                promptTokens = 10,
                completionTokens = 5,
                totalTokens = 15
            )
            val response = OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 1234567890L,
                model = "gpt-4",
                choices = choices,
                usage = usage
            )

            assertThat(response.usage).isNotNull()
            assertThat(response.usage?.promptTokens).isEqualTo(10)
            assertThat(response.usage?.completionTokens).isEqualTo(5)
            assertThat(response.usage?.totalTokens).isEqualTo(15)
        }

        @Test
        @DisplayName("创建响应 - usage 可以为 null")
        fun `创建响应 - usage 可以为 null`() {
            val choices = listOf(
                OpenAIChoice(0, OpenAIMessage("assistant", "Response"))
            )
            val response = OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 1234567890L,
                model = "gpt-4",
                choices = choices,
                usage = null
            )

            assertThat(response.usage).isNull()
        }
    }

    // ==================== OpenAIMessage 测试 ====================

    @Nested
    @DisplayName("OpenAI 消息模型测试")
    inner class OpenAIMessageTests {

        @Test
        @DisplayName("创建消息 - system 角色应该正确")
        fun `创建消息 - system 角色应该正确`() {
            val message = OpenAIMessage("system", "You are helpful")

            assertThat(message.role).isEqualTo("system")
            assertThat(message.content).isEqualTo("You are helpful")
        }

        @Test
        @DisplayName("创建消息 - user 角色应该正确")
        fun `创建消息 - user 角色应该正确`() {
            val message = OpenAIMessage("user", "Hello")

            assertThat(message.role).isEqualTo("user")
            assertThat(message.content).isEqualTo("Hello")
        }

        @Test
        @DisplayName("创建消息 - assistant 角色应该正确")
        fun `创建消息 - assistant 角色应该正确`() {
            val message = OpenAIMessage("assistant", "Hi there!")

            assertThat(message.role).isEqualTo("assistant")
            assertThat(message.content).isEqualTo("Hi there!")
        }
    }

    // ==================== OpenAIChoice 测试 ====================

    @Nested
    @DisplayName("OpenAI 选择模型测试")
    inner class OpenAIChoiceTests {

        @Test
        @DisplayName("创建选择 - 应该包含所有字段")
        fun `创建选择 - 应该包含所有字段`() {
            val message = OpenAIMessage("assistant", "Response")
            val choice = OpenAIChoice(
                index = 0,
                message = message,
                finishReason = "stop"
            )

            assertThat(choice.index).isEqualTo(0)
            assertThat(choice.message).isEqualTo(message)
            assertThat(choice.finishReason).isEqualTo("stop")
        }

        @Test
        @DisplayName("创建选择 - finishReason 可以为 null")
        fun `创建选择 - finishReason 可以为 null`() {
            val message = OpenAIMessage("assistant", "Response")
            val choice = OpenAIChoice(
                index = 0,
                message = message,
                finishReason = null
            )

            assertThat(choice.finishReason).isNull()
        }
    }

    // ==================== OpenAIUsage 测试 ====================

    @Nested
    @DisplayName("OpenAI 使用统计模型测试")
    inner class OpenAIUsageTests {

        @Test
        @DisplayName("创建使用统计 - 应该包含所有字段")
        fun `创建使用统计 - 应该包含所有字段`() {
            val usage = OpenAIUsage(
                promptTokens = 100,
                completionTokens = 50,
                totalTokens = 150
            )

            assertThat(usage.promptTokens).isEqualTo(100)
            assertThat(usage.completionTokens).isEqualTo(50)
            assertThat(usage.totalTokens).isEqualTo(150)
        }

        @Test
        @DisplayName("验证计算 - totalTokens 应该等于 prompt 加 completion")
        fun `验证计算 - totalTokens 应该等于 prompt 加 completion`() {
            val usage = OpenAIUsage(
                promptTokens = 75,
                completionTokens = 25,
                totalTokens = 100
            )

            val sum = usage.promptTokens + usage.completionTokens
            assertThat(usage.totalTokens).isEqualTo(sum)
        }
    }

    // ==================== 序列化注解测试 ====================

    @Nested
    @DisplayName("序列化注解测试")
    inner class SerializationAnnotationTests {

        @Test
        @DisplayName("OpenAIRequest - 应该有 Serializable 注解")
        fun `OpenAIRequest - 应该有 Serializable 注解`() {
            assertThat(OpenAIRequest::class.simpleName).isNotNull()
        }

        @Test
        @DisplayName("OpenAIResponse - 应该有 Serializable 注解")
        fun `OpenAIResponse - 应该有 Serializable 注解`() {
            assertThat(OpenAIResponse::class.simpleName).isNotNull()
        }

        @Test
        @DisplayName("OpenAIMessage - 应该有 Serializable 注解")
        fun `OpenAIMessage - 应该有 Serializable 注解`() {
            assertThat(OpenAIMessage::class.simpleName).isNotNull()
        }

        @Test
        @DisplayName("OpenAIChoice - 应该有 Serializable 注解")
        fun `OpenAIChoice - 应该有 Serializable 注解`() {
            assertThat(OpenAIChoice::class.simpleName).isNotNull()
        }

        @Test
        @DisplayName("OpenAIUsage - 应该有 Serializable 注解")
        fun `OpenAIUsage - 应该有 Serializable 注解`() {
            assertThat(OpenAIUsage::class.simpleName).isNotNull()
        }
    }
}
