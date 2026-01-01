package com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config

import com.cw2.nekoama.shared.model.Result

/**
 * AI 服务提供商配置接口
 *
 * 定义了所有 AI 服务提供商的通用配置选项
 */
interface AIProviderConfig {

    /**
     * API 端点地址
     */
    val apiUrl: String

    /**
     * API 密钥
     */
    val apiKey: String

    /**
     * 使用的模型名称
     */
    val model: String

    /**
     * 最大 Token 数量
     */
    val maxTokens: Int

    /**
     * 生成温度，控制输出的随机性
     */
    val temperature: Double

    /**
     * 请求超时时间（毫秒）
     */
    val timeoutMs: Long

    /**
     * 最大重试次数
     */
    val maxRetries: Int

    /**
     * 验证配置是否有效
     */
    fun validate(): Result<Unit>
}
