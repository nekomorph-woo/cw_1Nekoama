package com.cw2.nekoama.infrastructure.code_suggestion_gen.client.openai

import com.cw2.nekoama.domain.code_suggestion_gen.model.*
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.openai.OpenAIResponse
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.openai.OpenAIChoice
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.openai.OpenAIMessage
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.openai.OpenAIUsage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * OpenAI 响应解析器测试
 *
 * 验证 OpenAI API 响应的解析逻辑，包括 JSON 解析、纯文本降级、错误处理等
 */
@DisplayName("OpenAI 响应解析器测试")
class OpenAIResponseParserTest {

    // 创建测试用的 MethodContext
    private fun createTestMethodContext(
        methodName: String = "test",
        returnType: String = "Int"
    ) = MethodContext(
        language = ProgrammingLanguage.KOTLIN,
        projectMeta = ProjectMetadata("test-project"),
        surroundingContext = SurroundingContext(null),
        userIntent = null,
        methodName = methodName,
        parameters = emptyList(),
        returnType = TypeMetadata(returnType),
        modifiers = emptyList(),
        annotations = emptyList(),
        exceptions = emptyList(),
        methodBody = null,
        isConstructor = false,
        isAbstract = false,
        containingClass = null
    )

    // ==================== 命名建议解析测试 ====================

    @Nested
    @DisplayName("命名建议解析测试")
    inner class NamingResponseTests {

        @Test
        @DisplayName("解析命名响应 - 标准 JSON 格式应该返回建议列表")
        fun `解析命名响应 - 标准 JSON 格式应该返回建议列表`() {
            // 准备测试数据
            val responseContent = """
                {
                  "suggestions": [
                    {
                      "name": "calculateTotal",
                      "description": "计算订单总金额",
                      "score": 0.95
                    },
                    {
                      "name": "computeSum",
                      "description": "计算总和",
                      "score": 0.85
                    }
                  ]
                }
            """.trimIndent()

            val openAIResponse = OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 1234567890,
                model = "gpt-4",
                choices = listOf(
                    OpenAIChoice(
                        index = 0,
                        message = OpenAIMessage(role = "assistant", content = responseContent),
                        finishReason = "stop"
                    )
                )
            )

            val context = createTestMethodContext()

            // 执行测试
            val result = OpenAIResponseParser.parseNamingResponse(openAIResponse, context)

            // 验证结果
            assertThat(result.isSuccess).isTrue()
            val suggestions = result.getOrNull()!!
            assertThat(suggestions).hasSize(2)
            assertThat(suggestions[0].name).isEqualTo("calculateTotal")
            assertThat(suggestions[0].description).isEqualTo("计算订单总金额")
            assertThat(suggestions[0].score).isEqualTo(0.95)
        }

        @Test
        @DisplayName("解析命名响应 - 纯文本格式应该提取名称")
        fun `解析命名响应 - 纯文本格式应该提取名称`() {
            // 准备测试数据（纯文本格式）
            val responseContent = """
                • calculateTotal - 计算订单总金额
                • computeSum - 计算总和
                • getTotalAmount - 获取总金额
            """.trimIndent()

            val openAIResponse = OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 1234567890,
                model = "gpt-4",
                choices = listOf(
                    OpenAIChoice(
                        index = 0,
                        message = OpenAIMessage(role = "assistant", content = responseContent),
                        finishReason = "stop"
                    )
                )
            )

            val context = createTestMethodContext()

            // 执行测试
            val result = OpenAIResponseParser.parseNamingResponse(openAIResponse, context)

            // 验证结果（纯文本降级）
            assertThat(result.isSuccess).isTrue()
            val suggestions = result.getOrNull()!!
            assertThat(suggestions).isNotEmpty()
            assertThat(suggestions[0].name).isEqualTo("calculateTotal")
        }
    }

    // ==================== 注释生成解析测试 ====================

    @Nested
    @DisplayName("注释生成解析测试")
    inner class CommentResponseTests {

        @Test
        @DisplayName("解析注释响应 - 应该提取单行注释")
        fun `解析注释响应 - 应该提取单行注释`() {
            // 准备测试数据
            val responseContent = """
                {
                  "content": "计算订单总金额"
                }
            """.trimIndent()

            val openAIResponse = OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 1234567890,
                model = "gpt-4",
                choices = listOf(
                    OpenAIChoice(
                        index = 0,
                        message = OpenAIMessage(role = "assistant", content = responseContent),
                        finishReason = "stop"
                    )
                )
            )

            val context = createTestMethodContext("calculateTotal", "Double")

            // 执行测试
            val result = OpenAIResponseParser.parseCommentResponse(openAIResponse, context)

            // 验证结果
            assertThat(result.isSuccess).isTrue()
            val comment = result.getOrNull()!!
            assertThat(comment.content).isEqualTo("计算订单总金额")
            assertThat(comment.format).isEqualTo(CommentFormat.KDOC)
        }

        @Test
        @DisplayName("parseCommentResponse - 成功时应该包含 Token 数据")
        fun `parseCommentResponse - 成功时应该包含 Token 数据`() {
            val responseContent = """{"content": "测试注释"}"""

            val response = OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 1234567890L,
                model = "gpt-4",
                choices = listOf(
                    OpenAIChoice(
                        index = 0,
                        message = OpenAIMessage("assistant", responseContent),
                        finishReason = "stop"
                    )
                ),
                usage = OpenAIUsage(
                    promptTokens = 100,
                    completionTokens = 50,
                    totalTokens = 150
                )
            )
            val context = createTestMethodContext()

            val result = OpenAIResponseParser.parseCommentResponse(response, context)

            assertThat(result.isSuccess).isTrue()
            val suggestion = result.getOrNull()!!
            assertThat(suggestion.metadata.promptTokens).isEqualTo(100)
            assertThat(suggestion.metadata.completionTokens).isEqualTo(50)
            assertThat(suggestion.metadata.totalTokens).isEqualTo(150)
        }

        @Test
        @DisplayName("parseCommentResponse - usage 为 null 时 Token 数据应该为 0")
        fun `parseCommentResponse - usage 为 null 时 Token 数据应该为 0`() {
            val responseContent = """{"content": "测试注释"}"""

            val response = OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 1234567890L,
                model = "gpt-4",
                choices = listOf(
                    OpenAIChoice(
                        index = 0,
                        message = OpenAIMessage("assistant", responseContent),
                        finishReason = "stop"
                    )
                ),
                usage = null
            )
            val context = createTestMethodContext()

            val result = OpenAIResponseParser.parseCommentResponse(response, context)

            assertThat(result.isSuccess).isTrue()
            val suggestion = result.getOrNull()!!
            assertThat(suggestion.metadata.promptTokens).isEqualTo(0)
            assertThat(suggestion.metadata.completionTokens).isEqualTo(0)
            assertThat(suggestion.metadata.totalTokens).isEqualTo(0)
        }
    }

    // ==================== 自定义响应解析测试 ====================

    @Nested
    @DisplayName("自定义响应解析测试")
    inner class CustomResponseTests {

        @Test
        @DisplayName("解析自定义响应 - 应该返回原始内容")
        fun `解析自定义响应 - 应该返回原始内容`() {
            // 准备测试数据
            val responseContent = """
                这是一个自定义的生成内容。
                可以包含任意格式的文本。
            """.trimIndent()

            val openAIResponse = OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 1234567890,
                model = "gpt-4",
                choices = listOf(
                    OpenAIChoice(
                        index = 0,
                        message = OpenAIMessage(role = "assistant", content = responseContent),
                        finishReason = "stop"
                    )
                )
            )

            // 执行测试
            val result = OpenAIResponseParser.parseCustomResponse(openAIResponse)

            // 验证结果
            assertThat(result.isSuccess).isTrue()
            val content = result.getOrNull()!!
            assertThat(content.content).contains("自定义的生成内容")
        }
    }

    // ==================== 错误处理测试 ====================

    @Nested
    @DisplayName("错误处理测试")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("解析响应 - 响应内容为空应该返回错误")
        fun `解析响应 - 响应内容为空应该返回错误`() {
            // 准备测试数据（空响应）
            val openAIResponse = OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 1234567890,
                model = "gpt-4",
                choices = emptyList()
            )

            val context = createTestMethodContext()

            // 执行测试
            val result = OpenAIResponseParser.parseNamingResponse(openAIResponse, context)

            // 验证结果
            assertThat(result.isError).isTrue()
        }
    }
}
