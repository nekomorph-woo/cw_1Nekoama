package com.cw2.nekoama.domain.code_suggestion_gen.model

import kotlinx.serialization.Serializable

/**
 * 自定义生成建议数据类
 *
 * 包含AI生成的自定义内容及其元数据信息（如 Token 使用量）。
 */
@Serializable
data class CustomSuggestion(
    /**
     * 生成的主要内容
     */
    val content: String,

    /**
     * 生成时间戳
     */
    val generatedAt: Long = System.currentTimeMillis(),

    /**
     * 建议的元数据信息
     */
    val metadata: SuggestionMetadata = SuggestionMetadata()
)
