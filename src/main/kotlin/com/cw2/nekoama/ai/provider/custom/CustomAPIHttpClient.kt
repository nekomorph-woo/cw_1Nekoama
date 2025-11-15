package com.cw2.nekoama.ai.provider.custom

import com.cw2.nekoama.ai.provider.openai.OpenAIRequest
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.serialization.toJson
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * 自定义 API HTTP 客户端
 *
 * 支持灵活的认证方式、自定义端点和可选的 SSL 验证。
 * 兼容 OpenAI API 格式，但允许使用不同的服务提供商。
 */
class CustomAPIHttpClient(
    private val config: CustomAPIConfig
) : BaseHttpClient() {
    
    private val httpClient = createHttpClient()
    
    /**
     * 创建 HTTP 客户端，支持自定义 SSL 设置
     */
    private fun createHttpClient(): HttpClient {
        // 连接超时设置为总超时的1/4，为AI服务响应留出更多时间
        val connectTimeoutMs = (config.timeoutMs / 4).coerceAtLeast(10000L).coerceAtMost(15000L)
        
        val builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
        
        // 如果禁用 SSL 验证，使用自定义 SSL 上下文
        if (!config.verifySSL) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                builder.sslContext(sslContext)
                
                NekoamaLogger.warn("CustomAPIHttpClient", "SSL 验证已禁用，仅用于开发环境")
            } catch (e: Exception) {
                NekoamaLogger.warn("CustomAPIHttpClient", "无法禁用 SSL 验证: ${e.message}")
            }
        }
        
        return builder.build()
    }
    
    /**
     * 实现父类的异步HTTP请求发送方法
     */
    override suspend fun sendHttpRequest(request: OpenAIRequest): HttpResponseData {
        // 移除协程超时，使用HTTP请求本身的超时设置
        val httpRequest = buildHttpRequest(request)
        val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        return HttpResponseData(
            statusCode = httpResponse.statusCode(),
            body = httpResponse.body(),
            headers = httpResponse.headers().map()
        )
    }
    
    /**
     * 构建 HTTP 请求
     */
    private fun buildHttpRequest(request: OpenAIRequest): HttpRequest {
        val jsonBody = request.toJson().getOrNull() 
            ?: throw IllegalArgumentException("无法序列化请求")
        
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(config.buildEndpointUrl()))
            .header("Content-Type", "application/json")
            .header("User-Agent", "nekoama-intellij-plugin/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofMillis(config.timeoutMs))
        
        // 添加认证和自定义头部
        config.getAuthHeaders().forEach { (name, value) ->
            requestBuilder.header(name, value)
        }
        
        return requestBuilder.build()
    }
    
        
    /**
     * 实现父类的同步HTTP请求发送方法
     */
    override fun sendHttpRequestSync(request: OpenAIRequest): HttpResponseData {
        val httpRequest = buildHttpRequest(request)
        val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        return HttpResponseData(
            statusCode = httpResponse.statusCode(),
            body = httpResponse.body(),
            headers = httpResponse.headers().map()
        )
    }
}