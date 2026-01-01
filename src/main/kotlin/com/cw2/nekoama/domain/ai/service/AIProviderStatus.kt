package com.cw2.nekoama.domain.ai.service

/**
 * AI 服务提供商状态信息
 *
 * 用于表示 AI 服务提供商在某个时间点的运行状态，包括可用性、延迟、配额使用情况等信息。
 * 该状态信息会定期更新以反映服务的最新健康状况。
 *
 * @property available 服务是否可用，true 表示服务正常运行且可响应请求
 * @property latencyMs 请求延迟时间（毫秒），null 表示未检测或无法测量
 * @property quotaRemaining 剩余配额数量，null 表示未提供配额信息
 * @property quotaTotal 总配额数量，null 表示未提供配额信息
 * @property rateLimit 速率限制详情，null 表示未提供速率限制信息
 * @property lastError 最后一次错误信息，null 表示无错误或错误信息不可用
 * @property lastCheckTime 最后一次状态检查的时间戳（毫秒），默认为当前系统时间
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
