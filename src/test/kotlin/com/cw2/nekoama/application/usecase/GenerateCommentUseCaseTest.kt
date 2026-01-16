package com.cw2.nekoama.application.usecase

import com.cw2.nekoama.domain.code_suggestion_gen.model.*
import com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis.CodeAnalysisService
import com.cw2.nekoama.mock.PsiElementMock
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.shared.model.NekoamaResult
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.psi.KtFunction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * GenerateCommentUseCase 测试
 *
 * 验证注释生成用例的功能，包括：
 * - 成功时返回包含 Token 数据的完整 CommentSuggestion
 * - 错误处理（API 未配置、注释已存在、无法构建上下文）
 */
@DisplayName("GenerateCommentUseCase - 注释生成用例测试")
class GenerateCommentUseCaseTest {

    private lateinit var useCase: GenerateCommentUseCase
    private lateinit var mockProject: com.intellij.openapi.project.Project
    private lateinit var mockCodeAnalysisService: CodeAnalysisService
    private lateinit var mockGenerator: CodeSuggestionGenerator
    private lateinit var mockGeneratorFactory: GeneratorFactory
    private lateinit var mockKtFunction: KtFunction

    @BeforeEach
    fun setUp() {
        // 创建 Mock 对象
        mockProject = mockk(relaxed = true)
        mockCodeAnalysisService = mockk(relaxed = true)
        mockGenerator = mockk(relaxed = true)
        mockKtFunction = PsiElementMock.mockKtFunction(
            name = "calculateTotal",
            returnType = "kotlin.Double",
            bodyText = "return items.sumOf { it.price }"
        )

        // 设置 CodeAnalysisService 默认行为
        every { mockCodeAnalysisService.detectLanguage(any()) } returns ProgrammingLanguage.KOTLIN
        every { mockCodeAnalysisService.getProjectMetadata() } returns ProjectMetadata("test-project")
        every { mockCodeAnalysisService.extractSurroundingContext(any()) } returns NekoamaResult.success(
            SurroundingContext(namingPatterns = null)
        )

        // 创建 Mock GeneratorFactory
        mockGeneratorFactory = mockk<GeneratorFactory>(relaxed = true)
        every { mockGeneratorFactory.createGenerator(any()) } returns mockGenerator

        // 创建 UseCase 实例
        useCase = GenerateCommentUseCase(
            project = mockProject,
            codeAnalysisService = mockCodeAnalysisService,
            generatorFactory = mockGeneratorFactory
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== 成功场景测试 ====================

    @Test
    @DisplayName("generateComment - 成功时应该返回包含 Token 数据的 CommentSuggestion")
    fun `generateComment - 成功时应该返回包含 Token 数据的 CommentSuggestion`() = runTest {
        // Given - Mock generator 返回包含 Token 数据的 CommentSuggestion
        val mockSuggestion = CommentSuggestion(
            content = "计算订单总金额",
            format = CommentFormat.KDOC,
            language = CommentLanguage.CHINESE,
            metadata = SuggestionMetadata(
                source = "OpenAI",
                model = "gpt-4",
                promptTokens = 100,
                completionTokens = 50,
                totalTokens = 150
            )
        )
        coEvery { mockGenerator.generateComment(any()) } returns NekoamaResult.success(mockSuggestion)

        // When - 调用 UseCase
        val result = useCase.generateComment(mockKtFunction)

        // Then - 验证返回完整的 CommentSuggestion，包含 Token 数据
        assertThat(result.isSuccess).isTrue()
        val suggestion = result.getOrNull()
        assertThat(suggestion).isNotNull()
        assertThat(suggestion).isInstanceOf(CommentSuggestion::class.java)
        assertThat(suggestion!!.content).isEqualTo("计算订单总金额")
        assertThat(suggestion.metadata.totalTokens).isEqualTo(150)
        assertThat(suggestion.metadata.promptTokens).isEqualTo(100)
        assertThat(suggestion.metadata.completionTokens).isEqualTo(50)
        assertThat(suggestion.metadata.source).isEqualTo("OpenAI")
        assertThat(suggestion.metadata.model).isEqualTo("gpt-4")
    }

    // ==================== 错误场景测试 ====================

    @Test
    @DisplayName("generateComment - GeneratorFactory 返回 null 时应该返回 API 未配置错误")
    fun `generateComment - GeneratorFactory 返回 null 时应该返回 API 未配置错误`() = runTest {
        // Given - Mock factory 返回 null（API 未配置）
        every { mockGeneratorFactory.createGenerator(any()) } returns null

        // When
        val result = useCase.generateComment(mockKtFunction)

        // Then
        assertThat(result.isError).isTrue()
        val error = result.errorOrNull()
        assertThat(error).isNotNull()
        assertThat(error).isInstanceOf(NekoamaError.AuthenticationError.ApiKeyNotConfigured::class.java)
    }

    @Test
    @DisplayName("generateComment - 已存在注释时应该返回错误")
    fun `generateComment - 已存在注释时应该返回错误`() = runTest {
        // Given - Mock 函数已有注释（通过设置 docComment）
        val mockFunctionWithDoc = mockk<KtFunction>(relaxed = true)
        every { mockFunctionWithDoc.docComment } returns mockk(relaxed = true)

        // When
        val result = useCase.generateComment(mockFunctionWithDoc)

        // Then
        assertThat(result.isError).isTrue()
        val error = result.errorOrNull()
        assertThat(error).isNotNull()
        assertThat(error).isInstanceOf(NekoamaError.ParseError.InvalidConfiguration::class.java)
        assertThat(error!!.message).contains("注释已存在")
    }

    @Test
    @DisplayName("generateComment - Generator 返回错误时应该传播错误")
    fun `generateComment - Generator 返回错误时应该传播错误`() = runTest {
        // Given - Mock generator 返回错误
        val testError = NekoamaError.APIError.ServerError("API 请求失败")
        coEvery { mockGenerator.generateComment(any()) } returns NekoamaResult.error(testError)

        // When
        val result = useCase.generateComment(mockKtFunction)

        // Then
        assertThat(result.isError).isTrue()
        val error = result.errorOrNull()
        assertThat(error).isNotNull()
        assertThat(error).isInstanceOf(NekoamaError.APIError.ServerError::class.java)
        assertThat(error!!.message).contains("API 请求失败")
    }

    @Test
    @DisplayName("generateComment - Token 数据为 0 时应该正常处理")
    fun `generateComment - Token 数据为 0 时应该正常处理`() = runTest {
        // Given - Mock generator 返回 Token 为 0 的 CommentSuggestion
        val mockSuggestion = CommentSuggestion(
            content = "简单注释",
            format = CommentFormat.KDOC,
            language = CommentLanguage.CHINESE,
            metadata = SuggestionMetadata(
                promptTokens = 0,
                completionTokens = 0,
                totalTokens = 0
            )
        )
        coEvery { mockGenerator.generateComment(any()) } returns NekoamaResult.success(mockSuggestion)

        // When
        val result = useCase.generateComment(mockKtFunction)

        // Then
        assertThat(result.isSuccess).isTrue()
        val suggestion = result.getOrNull()
        assertThat(suggestion).isNotNull()
        assertThat(suggestion!!.metadata.totalTokens).isEqualTo(0)
        assertThat(suggestion.metadata.promptTokens).isEqualTo(0)
        assertThat(suggestion.metadata.completionTokens).isEqualTo(0)
    }
}
