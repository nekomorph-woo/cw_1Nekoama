package com.cw2.nekoama.ai.provider.custom

import com.cw2.nekoama.ai.provider.openai.OpenAIRequest
import com.cw2.nekoama.ai.provider.openai.OpenAIResponse
import com.cw2.nekoama.core.result.Result
import com.cw2.nekoama.core.exception.NekoamaError
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.serialization.fromJson

/**
 * HTTP客户端基础类
 *
 * 提供统一的HTTP状态码处理和异常处理机制
 * 子类只需实现具体的HTTP请求发送逻辑
 */
abstract class BaseHttpClient {

    /**
     * 子类需要实现的异步请求发送方法
     */
    protected abstract suspend fun sendHttpRequest(request: OpenAIRequest): HttpResponseData

    /**
     * 子类需要实现的同步请求发送方法
     */
    protected abstract fun sendHttpRequestSync(request: OpenAIRequest): HttpResponseData

    /**
     * 统一的异步请求处理
     */
    suspend fun sendRequest(request: OpenAIRequest): Result<OpenAIResponse> {
        return try {
            val startTime = System.currentTimeMillis()
            val httpResponse = sendHttpRequest(request)
            val duration = System.currentTimeMillis() - startTime

            handleHttpResponse(httpResponse, "sendRequest")
        } catch (e: java.net.http.HttpTimeoutException) {
            handleTimeoutException("sendRequest", e)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            handleTimeoutException("sendRequest", e)
        } catch (e: Exception) {
            handleGenericException("sendRequest", e)
        }
    }

    /**
     * 统一的同步请求处理
     */
    fun sendRequestSync(request: OpenAIRequest): Result<OpenAIResponse> {
        return try {
            val startTime = System.currentTimeMillis()
            val httpResponse = sendHttpRequestSync(request)
            val duration = System.currentTimeMillis() - startTime

            handleHttpResponse(httpResponse, "sendRequestSync")
        } catch (e: java.net.http.HttpTimeoutException) {
            handleTimeoutException("sendRequestSync", e)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            handleTimeoutException("sendRequestSync", e)
        } catch (e: Exception) {
            handleGenericException("sendRequestSync", e)
        }
    }

    /**
     * 统一的HTTP响应处理
     */
    private fun handleHttpResponse(httpResponse: HttpResponseData, methodName: String): Result<OpenAIResponse> {
        return when (httpResponse.statusCode) {
            200 -> {
                val response = parseSuccessResponse(httpResponse.body)
                response.onSuccess {
                    // 移除HttpClient层的日志调用，避免重复埋点
                    // 日志记录由Provider层的logAICallWithActionType处理
                }
                response
            }
            400 -> {
                val error = NekoamaError.APIError.BadRequest("请求格式错误: ${httpResponse.body}")
                NekoamaLogger.logError(methodName, error)
                Result.error(error)
            }
            401 -> {
                val error = NekoamaError.AuthenticationError.InvalidApiKey("认证失败")
                NekoamaLogger.logError(methodName, error)
                Result.error(error)
            }
            403 -> {
                val error = NekoamaError.AuthenticationError.InsufficientPermissions("权限不足")
                NekoamaLogger.logError(methodName, error)
                Result.error(error)
            }
            429 -> {
                val retryAfter = parseRetryAfter(httpResponse.headers)
                val error = NekoamaError.RateLimitError.TooManyRequests(retryAfter = retryAfter)
                NekoamaLogger.logError(methodName, error)
                Result.error(error)
            }
            500, 502, 503, 504 -> {
                val error = NekoamaError.APIError.ServerError("服务器错误: ${httpResponse.statusCode}")
                NekoamaLogger.logError(methodName, error)
                Result.error(error)
            }
            else -> {
                val error = NekoamaError.APIError.ServerError("未知错误: ${httpResponse.statusCode}")
                NekoamaLogger.logError(methodName, error)
                Result.error(error)
            }
        }
    }

    /**
     * 统一的超时异常处理
     */
    private fun handleTimeoutException(methodName: String, e: Exception): Result<OpenAIResponse> {
        val error = NekoamaError.NetworkError.ReadTimeout("请求超时")
        NekoamaLogger.logError(methodName, error)
        return Result.error(error)
    }

    /**
     * 统一的通用异常处理
     */
    private fun handleGenericException(methodName: String, e: Exception): Result<OpenAIResponse> {
        val error = NekoamaError.NetworkError.Generic("网络请求失败: ${e.message}")
        NekoamaLogger.logError(methodName, error, context = mapOf("exception" to e.message))
        return Result.error(error)
    }

    /**
     * 解析成功响应
     */
    private fun parseSuccessResponse(body: String): Result<OpenAIResponse> {
        return try {
            body.fromJson<OpenAIResponse>()
        } catch (e: Exception) {
            val error = NekoamaError.ParseError.JsonParse("解析响应失败: ${e.message}")
            NekoamaLogger.logError("parseSuccessResponse", error, context = mapOf("exception" to e.message))
            Result.error(error)
        }
    }

    /**
     * 解析 Retry-After 头部
     */
    protected fun parseRetryAfter(headers: Map<String, List<String>>): Long? {
        return headers["retry-after"]?.firstOrNull()?.toLongOrNull()?.times(1000)
    }

    /**
     * HTTP响应数据类
     */
    data class HttpResponseData(
        val statusCode: Int,
        val body: String,
        val headers: Map<String, List<String>>
    )
}