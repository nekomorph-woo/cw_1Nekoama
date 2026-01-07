package com.cw2.nekoama.infrastructure.code_suggestion_gen.client.openai

import com.cw2.nekoama.domain.code_suggestion_gen.model.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * OpenAI 提示词模板服务测试
 *
 * 验证提示词生成的正确性，包括系统提示词、用户提示词的结构和内容
 */
@DisplayName("OpenAI 提示词模板服务测试")
class PromptTemplateServiceTest {

    private lateinit var promptTemplateService: PromptTemplateService

    @BeforeEach
    fun setup() {
        promptTemplateService = PromptTemplateService()
    }

    @AfterEach
    fun tearDown() {
        // 清理资源（如果需要）
    }

    // ==================== 命名建议提示词测试 ====================

    @Nested
    @DisplayName("命名建议提示词测试")
    inner class NamingPromptTests {

        @Test
        @DisplayName("创建命名提示词 - 应该包含基本系统提示词")
        fun `创建命名提示词 - 应该包含基本系统提示词`() {
            // 准备测试数据
            val context = createTestMethodContext()

            // 执行测试
            val request = promptTemplateService.createNamingPrompt(context)

            // 验证结果
            assertThat(request.model).isEqualTo("gpt-4")
            assertThat(request.messages).isNotEmpty
            assertThat(request.messages[0].role).isEqualTo("system")
            assertThat(request.messages[0].content).contains("professional code assistant")
        }

        @Test
        @DisplayName("创建命名提示词 - 应该包含代码元素信息")
        fun `创建命名提示词 - 应该包含代码元素信息`() {
            // 准备测试数据
            val context = createTestMethodContext(
                methodName = "calculateTotal",
                returnType = "Double"
            )

            // 执行测试
            val request = promptTemplateService.createNamingPrompt(context)

            // 验证结果
            val userMessage = request.messages.first { it.role == "user" }
            assertThat(userMessage.content).contains("calculateTotal")
            assertThat(userMessage.content).contains("Double")
        }

        @Test
        @DisplayName("创建命名提示词 - 应该设置正确的温度参数")
        fun `创建命名提示词 - 应该设置正确的温度参数`() {
            // 准备测试数据
            val context = createTestMethodContext()

            // 执行测试
            val request = promptTemplateService.createNamingPrompt(context)

            // 验证结果
            assertThat(request.temperature).isEqualTo(0.7)
            assertThat(request.maxTokens).isEqualTo(300)
        }

        @Test
        @DisplayName("创建命名提示词 - 方法上下文应该包含参数信息")
        fun `创建命名提示词 - 方法上下文应该包含参数信息`() {
            // 准备测试数据
            val context = createTestMethodContext(
                methodName = "processUser",
                parameters = listOf(
                    ParameterMetadata("userId", TypeMetadata("String")),
                    ParameterMetadata("includeInactive", TypeMetadata("Boolean"))
                )
            )

            // 执行测试
            val request = promptTemplateService.createNamingPrompt(context)

            // 验证结果
            val userMessage = request.messages.first { it.role == "user" }
            assertThat(userMessage.content).contains("userId")
            assertThat(userMessage.content).contains("includeInactive")
        }
    }

    // ==================== 注释生成提示词测试 ====================

    @Nested
    @DisplayName("注释生成提示词测试")
    inner class CommentPromptTests {

        @Test
        @DisplayName("创建注释提示词 - 应该包含注释系统提示词")
        fun `创建注释提示词 - 应该包含注释系统提示词`() {
            // 准备测试数据
            val context = createTestMethodContext()

            // 执行测试
            val request = promptTemplateService.createCommentPrompt(context)

            // 验证结果
            assertThat(request.messages).isNotEmpty
            val systemMessage = request.messages.first { it.role == "system" }
            assertThat(systemMessage.content).contains("comment generation")
        }

        @Test
        @DisplayName("创建注释提示词 - 应该包含代码签名信息")
        fun `创建注释提示词 - 应该包含代码签名信息`() {
            // 准备测试数据
            val context = createTestMethodContext(
                methodName = "validateInput",
                returnType = "Boolean"
            )

            // 执行测试
            val request = promptTemplateService.createCommentPrompt(context)

            // 验证结果
            val userMessage = request.messages.first { it.role == "user" }
            assertThat(userMessage.content).contains("validateInput")
            assertThat(userMessage.content).contains("Boolean")
        }

        @Test
        @DisplayName("创建注释提示词 - 应该设置正确的温度参数")
        fun `创建注释提示词 - 应该设置正确的温度参数`() {
            // 准备测试数据
            val context = createTestMethodContext()

            // 执行测试
            val request = promptTemplateService.createCommentPrompt(context)

            // 验证结果
            assertThat(request.temperature).isEqualTo(0.6)
            assertThat(request.maxTokens).isEqualTo(800)
        }

        @Test
        @DisplayName("创建注释提示词 - 抽象方法应该添加契约说明")
        fun `创建注释提示词 - 抽象方法应该添加契约说明`() {
            // 准备测试数据
            val context = createTestMethodContext(
                methodName = "processData",
                isAbstract = true
            )

            // 执行测试
            val request = promptTemplateService.createCommentPrompt(context)

            // 验证结果
            val userMessage = request.messages.first { it.role == "user" }
            assertThat(userMessage.content).contains("CONTRACT")
        }
    }

    // ==================== 自定义内容生成提示词测试 ====================

    @Nested
    @DisplayName("自定义内容生成提示词测试")
    inner class CustomPromptTests {

        @Test
        @DisplayName("创建自定义提示词 - 应该包含用户请求")
        fun `创建自定义提示词 - 应该包含用户请求`() {
            // 准备测试数据
            val userPrompt = "请解释这段代码的作用"
            val context = createTestMethodContext()

            // 执行测试
            val request = promptTemplateService.createCustomPrompt(userPrompt, context)

            // 验证结果
            val userMessage = request.messages.first { it.role == "user" }
            assertThat(userMessage.content).contains("请解释这段代码的作用")
        }

        @Test
        @DisplayName("创建自定义提示词 - 无上下文应该正常工作")
        fun `创建自定义提示词 - 无上下文应该正常工作`() {
            // 准备测试数据
            val userPrompt = "什么是单例模式？"

            // 执行测试
            val request = promptTemplateService.createCustomPrompt(userPrompt, null)

            // 验证结果
            assertThat(request.model).isEqualTo("gpt-4")
            assertThat(request.maxTokens).isEqualTo(8192)
        }

        @Test
        @DisplayName("创建自定义提示词 - 应该设置正确的温度参数")
        fun `创建自定义提示词 - 应该设置正确的温度参数`() {
            // 准备测试数据
            val userPrompt = "重构建议"
            val context = createTestMethodContext()

            // 执行测试
            val request = promptTemplateService.createCustomPrompt(userPrompt, context)

            // 验证结果
            assertThat(request.temperature).isEqualTo(0.7)
            assertThat(request.maxTokens).isEqualTo(8192)
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的方法上下文
     */
    private fun createTestMethodContext(
        methodName: String = "testMethod",
        returnType: String = "String",
        parameters: List<ParameterMetadata> = emptyList(),
        isAbstract: Boolean = false
    ) = MethodContext(
        language = ProgrammingLanguage.KOTLIN,
        projectMeta = ProjectMetadata("test-project"),
        surroundingContext = SurroundingContext(null),
        userIntent = null,
        methodName = methodName,
        parameters = parameters,
        returnType = TypeMetadata(returnType),
        modifiers = emptyList(),
        annotations = emptyList(),
        exceptions = emptyList(),
        methodBody = null,
        isConstructor = false,
        isAbstract = isAbstract,
        containingClass = null
    )
}
