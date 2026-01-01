package com.cw2.nekoama.domain.ai.service

import com.cw2.nekoama.infrastructure.network.cleint.CustomAPIHttpClient
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.domain.code_suggestion_gen.model.CodeContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.CommentSuggestion
import com.cw2.nekoama.domain.code_suggestion_gen.model.NamingSuggestion
import com.cw2.nekoama.infrastructure.ai.client.openai.OpenAIMessage
import com.cw2.nekoama.infrastructure.ai.client.openai.OpenAIRequest
import com.cw2.nekoama.infrastructure.ai.client.openai.OpenAIResponseParser
import com.cw2.nekoama.shared.model.Result

/**
 * 自定�?API 服务提供商实�?
 */
class CustomAIService(
    override val config: CustomAPIConfig
) : AIProvider {

    override val name = config.providerName

    // 复用 OpenAI �?HTTP 客户端和模板系统，但使用自定义配�?
    private val httpClient by lazy {
        CustomAPIHttpClient(config)
    }
    private val promptTemplates by lazy {
        PromptService() // 使用相同的提示模�?
    }

    /**
     * 生成代码命名建议
     */
    override suspend fun generateNaming(context: CodeContext): Result<List<NamingSuggestion>> {
        return try {
            val startTime = System.currentTimeMillis()

            val prompt = promptTemplates.createNamingPrompt(context, config.model)
            val response = httpClient.sendRequest(prompt)

            val duration = System.currentTimeMillis() - startTime

            response.flatMap { openAIResponse ->
                NekoamaLogger.logAICallWithActionType(
                    provider = name,
                    model = config.model,
                    operation = "generateNaming",
                    success = true,
                    durationMs = duration,
                    actionType = "GENERATE_NAMING",
                    tokenCount = openAIResponse.usage?.totalTokens
                )
                OpenAIResponseParser.parseNamingResponse(openAIResponse, context)
            }.onError { error ->
                NekoamaLogger.logError("generateNaming", error, mapOf("provider" to name))
            }

        } catch (e: Exception) {
            val error = NekoamaError.APIError.ServerError("命名生成失败: ${e.message}")
            NekoamaLogger.logError("generateNaming", error, mapOf("provider" to name, "exception" to e.message))
            Result.error(error)
        }
    }

    /**
     * 生成代码注释
     */
    override suspend fun generateComment(context: CodeContext): Result<CommentSuggestion> {
        return try {
            val startTime = System.currentTimeMillis()

            val prompt = promptTemplates.createCommentPrompt(context, config.model)
            val response = httpClient.sendRequest(prompt)

            val duration = System.currentTimeMillis() - startTime

            response.flatMap { openAIResponse ->
                NekoamaLogger.logAICallWithActionType(
                    provider = name,
                    model = config.model,
                    operation = "generateComment",
                    success = true,
                    durationMs = duration,
                    actionType = "GENERATE_COMMENT",
                    tokenCount = openAIResponse.usage?.totalTokens
                )
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

    /**
     * 自定义生�?
     */
    override suspend fun generateCustom(prompt: String, context: CodeContext?): Result<String> {
        return try {
            val startTime = System.currentTimeMillis()

            val request = promptTemplates.createCustomPrompt(prompt, context, config.model)
            val response = httpClient.sendRequest(request)

            val duration = System.currentTimeMillis() - startTime

            response.flatMap { openAIResponse ->
                NekoamaLogger.logAICallWithActionType(
                    provider = name,
                    model = config.model,
                    operation = "generateCustom",
                    success = true,
                    durationMs = duration,
                    actionType = "CUSTOM_GENERATE",
                    tokenCount = openAIResponse.usage?.totalTokens
                )
                OpenAIResponseParser.parseCustomResponse(openAIResponse)
            }.onError { error ->
                NekoamaLogger.logError("generateCustom", error, mapOf("provider" to name))
            }

        } catch (e: Exception) {
            val error = NekoamaError.APIError.ServerError("自定义生成失�? ${e.message}")
            NekoamaLogger.logError("generateCustom", error, mapOf("provider" to name, "exception" to e.message))
            Result.error(error)
        }
    }

    /**
     * 检查服务可用�?
     */
    override suspend fun isAvailable(): Result<Boolean> {
        return try {
            // 发送一个简单的测试请求检查服务可用�?
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

    /**
     * 获取服务状�?
     */
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
            val error = NekoamaError.APIError.ServerError("获取状态失�? ${e.message}")
            NekoamaLogger.logError("getStatus", error, mapOf("provider" to name, "exception" to e.message))
            Result.error(error)
        }
    }
}
