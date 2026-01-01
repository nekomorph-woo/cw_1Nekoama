package com.cw2.nekoama.infrastructure.ai.client.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * OpenAI API 请求模型
 */
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 150,
    val stream: Boolean = false
)

/**
 * OpenAI API 响应模型
 */
@Serializable
data class OpenAIResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

/**
 * OpenAI 消息模型
 */
@Serializable
data class OpenAIMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

/**
 * OpenAI 选择模型
 */
@Serializable
data class OpenAIChoice(
    val index: Int,
    val message: OpenAIMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * OpenAI 使用统计模型
 */
@Serializable
data class OpenAIUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)