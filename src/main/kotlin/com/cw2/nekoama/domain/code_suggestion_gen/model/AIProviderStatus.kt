package com.cw2.nekoama.domain.code_suggestion_gen.model

/**
 * AI 服务提供商状态信息
 */
data class AIProviderStatus(
    /** 服务是否可用 */
    val available: Boolean,

    /** 请求延迟时间（毫秒） */
    val latencyMs: Long? = null,

    /** 剩余配额数量 */
    val quotaRemaining: Int? = null,

    /** 总配额数量 */
    val quotaTotal: Int? = null,

    /** 最后一次错误信息 */
    val lastError: String? = null,

    /** 最后一次状态检查的时间戳 */
    val lastCheckTime: Long = System.currentTimeMillis()
)
