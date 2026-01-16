package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.service.StatisticsService
import com.cw2.nekoama.shared.logging.NekoamaLogger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * Token 使用拦截器
 *
 * 职责：
 * - 拦截 OpenAI 兼容的 API 响应
 * - 提取 response.body.usage 字段
 * - 异步记录到统计服务
 * - 唯一的异常处理：不影响主流程（静默失败）
 *
 * 注意：
 * - 无需容错配置
 * - 无需可控开关
 * - 默认始终开启
 */
class TokenUsageInterceptor(
    private val statisticsService: StatisticsService
) : Interceptor {

    companion object {
        private const val LOG_TAG = "TokenUsageInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // 只处理成功的响应
        if (!response.isSuccessful) {
            return response
        }

        // 提取响应体
        val responseBody = response.body ?: return response

        return try {
            val rawJson = responseBody.string()

            // 提取 usage 数据
            val tokenUsage = extractTokenUsage(rawJson)

            if (tokenUsage != null) {
                // 异步记录，不阻塞请求，不影响主流程
                GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        statisticsService.recordTokenUsage(tokenUsage)
                    } catch (e: Exception) {
                        // 静默失败，仅记录日志
                        NekoamaLogger.debug(LOG_TAG, "记录 Token 使用失败: ${e.message}")
                    }
                }
            }

            // 重新构建响应（因为 string() 只能调用一次）
            response.newBuilder()
                .body(ResponseBody.create(responseBody.contentType(), rawJson))
                .build()

        } catch (e: Exception) {
            // 任何异常都不影响主流程，返回原始响应
            NekoamaLogger.debug(LOG_TAG, "拦截器处理失败，返回原始响应: ${e.message}")
            response
        }
    }

    /**
     * 从 OpenAI 兼容响应中提取 usage 数据
     *
     * OpenAI API 响应格式：
     * {
     *   "usage": {
     *     "prompt_tokens": 100,
     *     "completion_tokens": 50,
     *     "total_tokens": 150
     *   }
     * }
     */
    private fun extractTokenUsage(jsonStr: String): com.cw2.nekoama.domain.statistics.service.TokenUsageData? {
        return try {
            val jsonElement = Json.parseToJsonElement(jsonStr)
            val usageObject = jsonElement.jsonObject["usage"]?.jsonObject ?: return null

            com.cw2.nekoama.domain.statistics.service.TokenUsageData(
                promptTokens = usageObject["prompt_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                completionTokens = usageObject["completion_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                totalTokens = usageObject["total_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            // 静默失败，返回 null
            null
        }
    }
}
