package com.cw2.nekoama.domain.ai.service

import kotlinx.serialization.Serializable

/**
 * 认证方式枚举
 */
@Serializable
enum class AuthenticationType {
    /** Authorization: Bearer <token> */
    BEARER_TOKEN,

    /** api-key: <key> (Azure OpenAI) */
    API_KEY_HEADER,

    /** X-API-Key: <key> */
    X_API_KEY,

    /** 通过 customHeaders 自定义 */
    CUSTOM
}
