package com.cw2.nekoama.infra.network.cleint

import com.cw2.nekoama.infra.ai.client.openai.OpenAIRequest
import com.cw2.nekoama.infra.ai.client.openai.OpenAIResponse
import com.cw2.nekoama.shared.model.Result
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.shared.util.fromJson
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

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
        } catch (e: TimeoutException) {
            handleTimeoutException("sendRequest", e, isTimeoutException = true)
        } catch (e: SocketTimeoutException) {
            handleTimeoutException("sendRequest", e, isSocketTimeout = true)
        } catch (e: ConnectException) {
            handleConnectionException("sendRequest", e)
        } catch (e: UnknownHostException) {
            handleHostException("sendRequest", e)
        } catch (e: IOException) {
            handleIOException("sendRequest", e)
        } catch (e: TimeoutCancellationException) {
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
        } catch (e: TimeoutException) {
            handleTimeoutException("sendRequestSync", e, isTimeoutException = true)
        } catch (e: SocketTimeoutException) {
            handleTimeoutException("sendRequestSync", e, isSocketTimeout = true)
        } catch (e: ConnectException) {
            handleConnectionException("sendRequestSync", e)
        } catch (e: UnknownHostException) {
            handleHostException("sendRequestSync", e)
        } catch (e: IOException) {
            handleIOException("sendRequestSync", e)
        } catch (e: TimeoutCancellationException) {
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
                val error = NekoamaError.AuthenticationError.InvalidApiKey("API认证失败，请检查API密钥")
                NekoamaLogger.logError(methodName, error)
                Result.error(error)
            }
            403 -> {
                val error = NekoamaError.AuthenticationError.InsufficientPermissions("API权限不足，请检查API密钥权限")
                NekoamaLogger.logError(methodName, error)
                Result.error(error)
            }
            407 -> {
                // 🔧 修复：添加HTTP 407代理认证错误的专门处理
                val error = NekoamaError.NetworkError.ProxyAuthenticationRequired(
                    "代理认证失败：请检查IDEA代理配置中的用户名和密码是否正确"
                )
                NekoamaLogger.logError(methodName, error, context = mapOf(
                    "statusCode" to 407,
                    "suggestion" to "请检查IDEA的代理设置：File → Settings → HTTP Proxy",
                    "proxyConfigTip" to "确保代理服务器地址、端口、用户名和密码都正确配置"
                ))
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
    private fun handleTimeoutException(
        methodName: String,
        e: Exception,
        isTimeoutException: Boolean = false,
        isSocketTimeout: Boolean = false
    ): Result<OpenAIResponse> {
        val error = when {
            isTimeoutException -> NekoamaError.TimeoutError.RequestTimeout("请求超时")
            isSocketTimeout -> NekoamaError.NetworkError.ReadTimeout("Socket读取超时")
            e is TimeoutCancellationException -> NekoamaError.TimeoutError.OperationTimeout("协程操作超时")
            else -> NekoamaError.NetworkError.ReadTimeout("请求超时")
        }
        NekoamaLogger.logError(methodName, error, context = mapOf(
            "exceptionType" to e::class.simpleName,
            "exceptionMessage" to e.message
        ))
        return Result.error(error)
    }

    /**
     * 连接异常处理
     */
    private fun handleConnectionException(methodName: String, e: ConnectException): Result<OpenAIResponse> {
        val error = NekoamaError.NetworkError.ConnectionTimeout("连接失败: ${e.message}")
        NekoamaLogger.logError(methodName, error, context = mapOf(
            "exceptionType" to "ConnectException",
            "exceptionMessage" to e.message
        ))
        return Result.error(error)
    }

    /**
     * 主机异常处理
     */
    private fun handleHostException(methodName: String, e: UnknownHostException): Result<OpenAIResponse> {
        val error = NekoamaError.NetworkError.NetworkUnreachable("主机名解析失败: ${e.message}")
        NekoamaLogger.logError(methodName, error, context = mapOf(
            "exceptionType" to "UnknownHostException",
            "exceptionMessage" to e.message
        ))
        return Result.error(error)
    }

    /**
     * IO异常处理
     */
    private fun handleIOException(methodName: String, e: IOException): Result<OpenAIResponse> {
        val error = if (e.message?.contains("407") == true) {
            NekoamaError.NetworkError.ProxyAuthenticationRequired("代理认证失败")
        } else if (e.message?.contains("401") == true) {
            NekoamaError.AuthenticationError.InvalidApiKey("API认证失败")
        } else if (e.message?.contains("403") == true) {
            NekoamaError.AuthenticationError.InsufficientPermissions("API权限不足")
        } else if (e.message?.contains("429") == true) {
            NekoamaError.RateLimitError.TooManyRequests()
        } else {
            NekoamaError.NetworkError.Generic("网络IO错误: ${e.message}")
        }
        NekoamaLogger.logError(methodName, error, context = mapOf(
            "exceptionType" to "IOException",
            "exceptionMessage" to e.message
        ))
        return Result.error(error)
    }

    /**
     * 统一的通用异常处理
     */
    private fun handleGenericException(methodName: String, e: Exception): Result<OpenAIResponse> {
        val error = NekoamaError.NetworkError.Generic("网络请求失败: ${e.message}")
        NekoamaLogger.logError(methodName, error, context = mapOf(
            "exceptionType" to e::class.simpleName,
            "exceptionMessage" to e.message
        ))
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