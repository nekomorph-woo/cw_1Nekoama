package com.cw2.nekoama.infrastructure.network.client.interceptor

import com.cw2.nekoama.shared.logging.NekoamaLogger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * 请求头配置拦截器
 *
 * 统一管理所有请求头，包括认证头部、用户代理等。
 * 提供请求头的标准化配置和日志记录。
 */
class HeadersInterceptor(
    private val authHeaders: Map<String, String>,
    private val userAgent: String = "nekoama-intellij-plugin/1.0"
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 检查是否已经有Content-Type头部
        val contentType = originalRequest.header("Content-Type")

        // 构建新的请求，添加必要的头部
        val requestBuilder = originalRequest.newBuilder()

        // 添加Content-Type（如果尚未设置）
        if (contentType == null) {
            requestBuilder.header("Content-Type", "application/json")
        }

        // 添加User-Agent
        requestBuilder.header("User-Agent", userAgent)

        // 添加认证头部
        authHeaders.forEach { (name, value) ->
            // 避免覆盖重要的系统头部
            if (!isSystemHeader(name)) {
                requestBuilder.header(name, value)
            } else {
                NekoamaLogger.debug("HeadersInterceptor",
                    "跳过系统头部: $name")
            }
        }

        // 添加请求ID用于跟踪
        val requestId = generateRequestId()
        requestBuilder.header("X-Request-ID", requestId)

        val newRequest = requestBuilder.build()

        // 记录请求信息（不记录敏感的认证信息）
        logRequestInfo(newRequest, requestId)

        return chain.proceed(newRequest)
    }

    /**
     * 检查是否为系统保留头部
     */
    private fun isSystemHeader(name: String): Boolean {
        val systemHeaders = setOf(
            "Host",
            "Connection",
            "Content-Length",
            "Transfer-Encoding",
            "Expect",
            "Keep-Alive",
            "Proxy-Authenticate",
            "Proxy-Authorization",
            "TE",
            "Trailer",
            "Upgrade"
        )
        return systemHeaders.any { it.equals(name, ignoreCase = true) }
    }

    /**
     * 生成请求ID
     */
    private fun generateRequestId(): String {
        return "req-${System.currentTimeMillis()}-${(1000..9999).random()}"
    }

    /**
     * 记录请求信息
     */
    private fun logRequestInfo(request: Request, requestId: String) {
        NekoamaLogger.debug("HeadersInterceptor",
            "请求 [$requestId] ${request.method} ${request.url.encodedPath}")

        // 记录非敏感的请求头
        val safeHeaders = request.headers
            .filter { !isSensitiveHeader(it.first) }
            .joinToString(", ") { "${it.first}: ${it.second}" }

        if (safeHeaders.isNotEmpty()) {
            NekoamaLogger.debug("HeadersInterceptor",
                "请求头 [$requestId]: $safeHeaders")
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
            "x-api-key"
        )
        return sensitiveHeaders.any { it.equals(name, ignoreCase = true) }
    }
}
