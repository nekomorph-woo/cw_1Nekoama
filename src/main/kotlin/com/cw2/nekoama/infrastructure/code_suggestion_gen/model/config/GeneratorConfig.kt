package com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config

import com.cw2.nekoama.shared.model.NekoamaResult

/**
 * 代码建议生成器配置接�?
 *
 * 定义了代码建议生成器的通用配置选项�?
 * 具体实现可能使用 AI 服务、规则引擎或其他方式�?
 */
interface GeneratorConfig {

    /**
     * API 端点地址
     */
    val apiUrl: String

    /**
     * API 密钥
     */
    val apiKey: String

    /**
     * 使用的模型名�?
     */
    val model: String

    /**
     * 最�?Token 数量
     */
    val maxTokens: Int

    /**
     * 生成温度，控制输出的随机�?
     */
    val temperature: Double

    /**
     * 请求超时时间（毫秒）
     */
    val timeoutMs: Long

    /**
     * 最大重试次�?
     */
    val maxRetries: Int

    /**
     * 验证配置是否有效
     */
    fun validate(): NekoamaResult<Unit>
}
