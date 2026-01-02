package com.cw2.nekoama.application.usecase

import com.cw2.nekoama.domain.code_suggestion_gen.model.CodeSuggestionGenerator
import com.cw2.nekoama.shared.model.Result
import com.cw2.nekoama.shared.exception.NekoamaError

/**
 * 自定义内容生成的应用服务用例
 *
 * 职责：
 * - 编排自定义生成的业务流程
 * - 调用 AI 生成自定义内容
 * - 返回生成结果供 Action 层插入
 *
 * @param generatorFactory 生成器工厂
 */
class CustomGenerateUseCase(
    private val generatorFactory: GeneratorFactory
) {

    /**
     * 生成自定义内容
     *
     * @param selectionText 用户选中的文本
     * @return 生成结果，成功时返回生成内容，失败时返回错误信息
     */
    suspend fun generateCustom(selectionText: String): Result<String> {
        // 创建生成器
        val generator = generatorFactory.createGenerator(maxTokens = 2000)
            ?: return Result.error(NekoamaError.AuthenticationError.ApiKeyNotConfigured("API 未配置"))

        // 调用 AI 进行自定义生成
        return generator.generateCustom(selectionText, null)
    }
}
