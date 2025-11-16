package com.cw2.nekoama.ai.provider.custom

import com.cw2.nekoama.ai.provider.openai.OpenAIRequest
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.serialization.toJson
import com.cw2.nekoama.core.network.ProxyDetector
import com.cw2.nekoama.core.network.ProxyConfig
import com.cw2.nekoama.core.network.HttpClientProxyConfigurator
import com.cw2.nekoama.core.network.ProxyType
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.net.URI
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
     * 创建 HTTP 客户端，支持自定义 SSL 设置和代理认证
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

        // 🔧 修复：显式配置代理和认证，解决HTTP 407问题
        val proxyConfig = ProxyDetector.detectSystemProxy(config.buildEndpointUrl())

        if (proxyConfig.type != ProxyType.DIRECT && proxyConfig.isValid()) {
            try {
                // 创建代理选择器
                val javaProxy = proxyConfig.toJavaProxy()
                val proxySelector = object : java.net.ProxySelector() {
                    override fun select(uri: URI?): List<java.net.Proxy> {
                        return listOf(javaProxy)
                    }

                    override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: IOException?) {
                        NekoamaLogger.warn("CustomAPIHttpClient",
                            "代理连接失败: ${uri?.host}:${uri?.port} - ${ioe?.message}")
                    }
                }
                builder.proxy(proxySelector)

                // 配置代理认证器（关键修复）
                if (!proxyConfig.username.isNullOrBlank()) {
                    val authenticator = object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication {
                            return when (requestingHost) {
                                proxyConfig.host -> {
                                    NekoamaLogger.debug("CustomAPIHttpClient",
                                        "使用代理认证: ${proxyConfig.username}@${proxyConfig.host}:${proxyConfig.port}")
                                    PasswordAuthentication(
                                        proxyConfig.username,
                                        proxyConfig.password?.toCharArray() ?: charArrayOf()
                                    )
                                }
                                else -> {
                                    // 其他请求不使用此认证器
                                    PasswordAuthentication("", charArrayOf())
                                }
                            }
                        }
                    }
                    builder.authenticator(authenticator)
                    NekoamaLogger.info("CustomAPIHttpClient",
                        "已配置${proxyConfig.type}代理认证: ${proxyConfig.username}@${proxyConfig.host}:${proxyConfig.port}")
                } else {
                    NekoamaLogger.warn("CustomAPIHttpClient",
                        "代理${proxyConfig.host}:${proxyConfig.port}未配置认证信息，可能导致HTTP 407错误")
                }

                // 同时配置系统代理作为后备
                HttpClientProxyConfigurator.configureSystemProxy(proxyConfig)

            } catch (e: Exception) {
                NekoamaLogger.error("CustomAPIHttpClient",
                    "代理配置失败，将使用直连: ${e.message}", error = e)
                // 代理配置失败时回退到直连
                val directSelector = object : java.net.ProxySelector() {
                    override fun select(uri: URI?): List<java.net.Proxy> {
                        return listOf(java.net.Proxy.NO_PROXY)
                    }

                    override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: IOException?) {
                        // 直连失败，无需处理
                    }
                }
                builder.proxy(directSelector)
            }
        } else {
            NekoamaLogger.debug("CustomAPIHttpClient", "使用直连模式")
        }

        NekoamaLogger.info("CustomAPIHttpClient",
            "最终代理配置: ${ProxyDetector.getProxyStatus(proxyConfig)}")

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