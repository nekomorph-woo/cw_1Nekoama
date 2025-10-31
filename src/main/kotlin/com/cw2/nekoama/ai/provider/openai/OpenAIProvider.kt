package com.cw2.nekoama.ai.provider.openai

import com.cw2.nekoama.ai.provider.AIProvider
import com.cw2.nekoama.ai.provider.AIProviderConfig
import com.cw2.nekoama.ai.provider.AIProviderStatus
import com.cw2.nekoama.ai.model.*
import com.cw2.nekoama.core.result.Result
import com.cw2.nekoama.core.exception.NekoamaError
import com.cw2.nekoama.core.logging.NekoamaLogger
import kotlinx.serialization.Serializable

/**
 * OpenAI API 服务提供商实现
 * 
 * 实现了与 OpenAI API 兼容接口的通信，支持代码命名建议、注释生成和自定义生成功能。
 * 包含完整的错误处理、重试机制和性能监控。
 */
class OpenAIProvider(
    override val config: OpenAIConfig
) : AIProvider {
    
    override val name = "OpenAI"
    
    private val httpClient by lazy { OpenAIHttpClient(config) }
    private val promptTemplates by lazy { OpenAIPromptTemplates() }
    
    /**
     * 生成代码命名建议
     */
    override suspend fun generateNaming(context: CodeContext): Result<List<NamingSuggestion>> {
        return try {
            NekoamaLogger.logAICall(name, config.model, "generateNaming", true, 0)
            
            val prompt = promptTemplates.createNamingPrompt(context, config.model)
            val response = httpClient.sendRequest(prompt)
            
            response.flatMap { openAIResponse ->
                OpenAIResponseParser.parseNamingResponse(openAIResponse, context)
            }
            
        } catch (e: Exception) {
            NekoamaLogger.logError("generateNaming", NekoamaError.APIError.ServerError(), mapOf("provider" to name))
            Result.error(NekoamaError.APIError.ServerError())
        }
    }
    
    override suspend fun generateComment(context: CodeContext): Result<CommentSuggestion> {
        return try {
            val startTime = System.currentTimeMillis()
            
            val prompt = promptTemplates.createCommentPrompt(context, config.model)
            val response = httpClient.sendRequest(prompt)
            
            val duration = System.currentTimeMillis() - startTime
            
            response.flatMap { openAIResponse ->
                NekoamaLogger.logAICall(name, config.model, "generateComment", true, duration,
                    tokenCount = openAIResponse.usage?.totalTokens)
                OpenAIResponseParser.parseCommentResponse(openAIResponse, context)
            }.onError { error ->
                NekoamaLogger.logError("generateComment", error, mapOf("provider" to name))
            }
            
        } catch (e: Exception) {
            val error = NekoamaError.APIError.ServerError("注释生成失败: ${e.message}")
            NekoamaLogger.logError("generateComment", error, mapOf("provider" to name, "exception" to e.message))
            Result.error(error)
        }
    }
    
    override suspend fun generateCustom(prompt: String, context: CodeContext?): Result<String> {
        return try {
            val startTime = System.currentTimeMillis()
            
            val request = promptTemplates.createCustomPrompt(prompt, context, config.model)
            val response = httpClient.sendRequest(request)
            
            val duration = System.currentTimeMillis() - startTime
            
            response.flatMap { openAIResponse ->
                NekoamaLogger.logAICall(name, config.model, "generateCustom", true, duration,
                    tokenCount = openAIResponse.usage?.totalTokens)
                OpenAIResponseParser.parseCustomResponse(openAIResponse)
            }.onError { error ->
                NekoamaLogger.logError("generateCustom", error, mapOf("provider" to name))
            }
            
        } catch (e: Exception) {
            val error = NekoamaError.APIError.ServerError("自定义生成失败: ${e.message}")
            NekoamaLogger.logError("generateCustom", error, mapOf("provider" to name, "exception" to e.message))
            Result.error(error)
        }
    }
    
    override suspend fun isAvailable(): Result<Boolean> {
        return try {
            // 发送一个简单的测试请求检查服务可用性
            val testRequest = OpenAIRequest(
                model = config.model,
                messages = listOf(
                    OpenAIMessage("user", "test")
                ),
                maxTokens = 1
            )
            
            val response = httpClient.sendRequest(testRequest)
            response.map { true }.onError { error ->
                NekoamaLogger.logError("isAvailable", error, mapOf("provider" to name))
            }
            
        } catch (e: Exception) {
            NekoamaLogger.logError("isAvailable", NekoamaError.NetworkError.Generic(),
                mapOf("provider" to name, "exception" to e.message))
            Result.success(false)
        }
    }
    
    override suspend fun getStatus(): Result<AIProviderStatus> {
        return try {
            val startTime = System.currentTimeMillis()
            val available = isAvailable()
            val latency = System.currentTimeMillis() - startTime
            
            available.map { isAvailable ->
                AIProviderStatus(
                    available = isAvailable,
                    latencyMs = latency,
                    lastCheckTime = System.currentTimeMillis()
                )
            }
            
        } catch (e: Exception) {
            val error = NekoamaError.APIError.ServerError("获取状态失败: ${e.message}")
            NekoamaLogger.logError("getStatus", error, mapOf("provider" to name, "exception" to e.message))
            Result.error(error)
        }
    }
}

/**
 * OpenAI 配置实现
 */
@Serializable
data class OpenAIConfig(
    override val apiUrl: String = "https://api.openai.com/v1",
    override val apiKey: String,
    override val model: String = "gpt-4",
    override val maxTokens: Int = 150,
    override val temperature: Double = 0.7,
    override val timeoutMs: Long = 30000,
    override val maxRetries: Int = 3
) : AIProviderConfig {
    
    override fun validate(): Result<Unit> {
        return when {
            apiKey.isBlank() -> Result.error(NekoamaError.AuthenticationError.ApiKeyNotConfigured())
            !apiUrl.startsWith("http") -> Result.error(NekoamaError.ParseError.InvalidConfiguration("无效的API URL"))
            maxTokens <= 0 -> Result.error(NekoamaError.ParseError.InvalidConfiguration("maxTokens必须大于0"))
            temperature < 0.0 || temperature > 2.0 -> Result.error(NekoamaError.ParseError.InvalidConfiguration("temperature必须在0.0-2.0之间"))
            else -> Result.success(Unit)
        }
    }
}