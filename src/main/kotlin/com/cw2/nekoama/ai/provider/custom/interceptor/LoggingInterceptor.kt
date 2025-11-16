package com.cw2.nekoama.ai.provider.custom.interceptor

import com.cw2.nekoama.core.logging.NekoamaLogger
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Request
import okio.Buffer
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.system.measureTimeMillis

/**
 * OkHttp 日志拦截器
 *
 * 提供详细的HTTP请求和响应日志记录，包括：
 * - 请求URL、方法、头部信息
 * - 响应状态码、响应时间、响应大小
 * - 请求/响应体（仅限非敏感内容）
 */
class LoggingInterceptor(
    private val logLevel: LogLevel = LogLevel.BASIC
) : Interceptor {

    enum class LogLevel {
        NONE,    // 不记录日志
        BASIC,   // 记录基本信息（URL、方法、状态码、时间）
        HEADERS, // 记录头部信息
        BODY     // 记录完整请求和响应体（小心敏感信息）
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (logLevel == LogLevel.NONE) {
            return chain.proceed(request)
        }

        val requestId = generateRequestId()
        val startTime = System.currentTimeMillis()

        // 记录请求信息
        logRequest(request, requestId)

        // 执行请求并测量时间
        var response: Response
        val duration = measureTimeMillis {
            response = chain.proceed(request)
        }

        // 记录响应信息
        logResponse(response, requestId, duration)

        return response
    }

    /**
     * 记录请求信息
     */
    private fun logRequest(request: Request, requestId: String) {
        when (logLevel) {
            LogLevel.NONE -> { /* 不记录 */ }
            LogLevel.BASIC -> {
                NekoamaLogger.debug("LoggingInterceptor",
                    "[$requestId] ${request.method} ${request.url}")
            }
            LogLevel.HEADERS -> {
                logBasicRequestInfo(request, requestId)
                logRequestHeaders(request, requestId)
            }
            LogLevel.BODY -> {
                logBasicRequestInfo(request, requestId)
                logRequestHeaders(request, requestId)
                logRequestBody(request, requestId)
            }
        }
    }

    /**
     * 记录响应信息
     */
    private fun logResponse(response: Response, requestId: String, duration: Long) {
        when (logLevel) {
            LogLevel.NONE -> { /* 不记录 */ }
            LogLevel.BASIC -> {
                NekoamaLogger.info("LoggingInterceptor",
                    "[$requestId] ${response.code} ${response.message} (${duration}ms)")
            }
            LogLevel.HEADERS -> {
                logBasicResponseInfo(response, requestId, duration)
                logResponseHeaders(response, requestId)
            }
            LogLevel.BODY -> {
                logBasicResponseInfo(response, requestId, duration)
                logResponseHeaders(response, requestId)
                logResponseBody(response, requestId)
            }
        }
    }

    /**
     * 记录基本请求信息
     */
    private fun logBasicRequestInfo(request: Request, requestId: String) {
        NekoamaLogger.info("LoggingInterceptor",
            "[$requestId] Request: ${request.method} ${request.url}")
    }

    /**
     * 记录请求头
     */
    private fun logRequestHeaders(request: Request, requestId: String) {
        val safeHeaders = request.headers
            .filter { !isSensitiveHeader(it.first) }
            .joinToString(", ") { "${it.first}: ${it.second}" }

        if (safeHeaders.isNotEmpty()) {
            NekoamaLogger.debug("LoggingInterceptor",
                "[$requestId] Request Headers: $safeHeaders")
        }
    }

    /**
     * 记录请求体
     */
    private fun logRequestBody(request: Request, requestId: String) {
        val body = request.body
        if (body != null) {
            try {
                val buffer = Buffer()
                body.writeTo(buffer)
                val charset = body.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                val bodyString = buffer.readString(charset)

                // 限制日志长度，避免过长的请求体
                val truncatedBody = if (bodyString.length > 1000) {
                    bodyString.take(1000) + "... (truncated)"
                } else {
                    bodyString
                }

                NekoamaLogger.debug("LoggingInterceptor",
                    "[$requestId] Request Body (${body.contentLength()} bytes): $truncatedBody")
            } catch (e: Exception) {
                NekoamaLogger.warn("LoggingInterceptor",
                    "[$requestId] Failed to log request body: ${e.message}")
            }
        }
    }

    /**
     * 记录基本响应信息
     */
    private fun logBasicResponseInfo(response: Response, requestId: String, duration: Long) {
        NekoamaLogger.info("LoggingInterceptor",
            "[$requestId] Response: ${response.code} ${response.message} (${duration}ms, ${response.body?.contentLength()} bytes)")
    }

    /**
     * 记录响应头
     */
    private fun logResponseHeaders(response: Response, requestId: String) {
        val headers = response.headers
            .joinToString(", ") { "${it.first}: ${it.second}" }

        NekoamaLogger.debug("LoggingInterceptor",
            "[$requestId] Response Headers: $headers")
    }

    /**
     * 记录响应体
     */
    private fun logResponseBody(response: Response, requestId: String) {
        val responseBody = response.body
        if (responseBody != null) {
            try {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer

                val charset = responseBody.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                val bodyString = buffer.clone().readString(charset)

                // 限制日志长度，避免过长的响应体
                val truncatedBody = if (bodyString.length > 1000) {
                    bodyString.take(1000) + "... (truncated)"
                } else {
                    bodyString
                }

                NekoamaLogger.debug("LoggingInterceptor",
                    "[$requestId] Response Body: $truncatedBody")
            } catch (e: Exception) {
                NekoamaLogger.warn("LoggingInterceptor",
                    "[$requestId] Failed to log response body: ${e.message}")
            }
        }
    }

    /**
     * 检查是否为敏感头部
     */
    private fun isSensitiveHeader(name: String): Boolean {
        val sensitiveHeaders = setOf(
            "authorization",
            "proxy-authorization",
            "api-key",
            "x-api-key",
            "cookie",
            "set-cookie"
        )
        return sensitiveHeaders.any { it.equals(name, ignoreCase = true) }
    }

    /**
     * 生成请求ID
     */
    private fun generateRequestId(): String {
        return "req-${System.currentTimeMillis()}-${(1000..9999).random()}"
    }
}