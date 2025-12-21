package com.cw2.nekoama.infra.network.interceptor

import com.cw2.nekoama.shared.logging.NekoamaLogger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.pow

/**
 * OkHttp 重试拦截器
 *
 * 提供智能重试机制，支持：
 * - 可配置的重试次数和延迟
 * - 指数退避策略
 * - 可重试的异常和状态码过滤
 * - 请求幂等性检查
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 1000,
    private val maxDelayMs: Long = 30000,
    private val retryableExceptions: Set<Class<out IOException>> = setOf(
        SocketTimeoutException::class.java,
        IOException::class.java
    ),
    private val retryableStatusCodes: Set<Int> = setOf(
        408, // Request Timeout
        429, // Too Many Requests
        500, // Internal Server Error
        502, // Bad Gateway
        503, // Service Unavailable
        504  // Gateway Timeout
    )
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: Exception? = null
        var lastResponse: Response? = null

        // 检查请求是否幂等（只有幂等请求才能安全重试）
        if (!isRequestIdempotent(request)) {
            return chain.proceed(request)
        }

        repeat(maxRetries + 1) { attempt ->
            try {
                val response = chain.proceed(request)

                // 检查响应状态码是否需要重试
                if (!isRetryableStatusCode(response.code)) {
                    return response
                }

                // 如果是最后一次尝试，直接返回响应
                if (attempt == maxRetries) {
                    return response
                }

                // 关闭响应体以准备重试
                response.close()
                lastResponse = response

                NekoamaLogger.warn("RetryInterceptor",
                    "Request failed with status ${response.code}, retrying... (${attempt + 1}/$maxRetries)")

            } catch (e: Exception) {
                lastException = e

                // 检查异常是否可重试
                if (!isRetryableException(e)) {
                    throw e
                }

                // 如果是最后一次尝试，抛出异常
                if (attempt == maxRetries) {
                    throw e
                }

                NekoamaLogger.warn("RetryInterceptor",
                    "Request failed with ${e::class.simpleName}: ${e.message}, retrying... (${attempt + 1}/$maxRetries)")
            }

            // 计算退避延迟并等待
            if (attempt < maxRetries) {
                val delayMs = calculateBackoffDelay(attempt)
                try {
                    Thread.sleep(delayMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Retry interrupted", ie)
                }
            }
        }

        // 不应该到达这里，但以防万一
        return lastResponse ?: throw (lastException ?: IOException("Retry failed"))
    }

    /**
     * 检查请求是否幂等（可以安全重试）
     */
    private fun isRequestIdempotent(request: Request): Boolean {
        val method = request.method
        return when (method) {
            "GET", "HEAD", "OPTIONS", "TRACE", "PUT", "DELETE" -> true
            "POST" -> {
                // POST请求通常不是幂等的，但某些AI API可能支持
                // 这里我们保守处理，不重试POST请求
                false
            }
            else -> false
        }
    }

    /**
     * 检查状态码是否可重试
     */
    private fun isRetryableStatusCode(statusCode: Int): Boolean {
        return retryableStatusCodes.contains(statusCode)
    }

    /**
     * 检查异常是否可重试
     */
    private fun isRetryableException(exception: Exception): Boolean {
        return retryableExceptions.any { retryableClass ->
            retryableClass.isAssignableFrom(exception::class.java)
        }
    }

    /**
     * 计算指数退避延迟
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val delayMs = (initialDelayMs * (2.0.pow(attempt))).toLong()
        return minOf(delayMs, maxDelayMs)
    }
}