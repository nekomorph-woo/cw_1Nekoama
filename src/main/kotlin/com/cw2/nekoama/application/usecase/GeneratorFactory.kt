package com.cw2.nekoama.application.usecase

import com.cw2.nekoama.domain.code_suggestion_gen.model.CodeSuggestionGenerator
import com.cw2.nekoama.domain.settings.service.NekoamaSecureStorage
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.infrastructure.code_suggestion_gen.client.openai.OpenAIGenerator
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config.CustomGeneratorConfig

/**
 * 代码建议生成器工厂
 *
 * 职责：
 * - 统一管理 CodeSuggestionGenerator 的创建逻辑
 * - 处理 API Key 的安全获取（优先从安全存储，其次从配置，最后从环境变量）
 * - 为不同的生成场景提供配置好的生成器实例
 */
class GeneratorFactory {

    /**
     * 创建代码建议生成器
     *
     * @param maxTokens 最大 token 数（不同功能可能有不同需求）
     * @return 配置好的生成器实例，如果配置不完整则返回 null
     */
    fun createGenerator(maxTokens: Int): CodeSuggestionGenerator? {
        val settings = NekoamaSettings.getInstance()
        val secureKey = NekoamaSecureStorage.getApiKeySync()
        val resolvedKey =
            if (secureKey.isNotBlank()) secureKey else settings.apiKey.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }

        if (resolvedKey.isBlank() || settings.apiEndpoint.isBlank()) return null

        return OpenAIGenerator(
            CustomGeneratorConfig(
                generatorName = "Custom API",
                apiUrl = settings.apiEndpoint,
                apiKey = resolvedKey,
                model = settings.model,
                temperature = settings.modelTemperature / 100.0,
                timeoutMs = settings.requestTimeoutMs.toLong(),
                maxTokens = maxTokens
            )
        )
    }
}
