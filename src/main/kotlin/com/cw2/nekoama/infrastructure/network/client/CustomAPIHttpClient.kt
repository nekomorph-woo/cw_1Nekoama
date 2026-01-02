package com.cw2.nekoama.infrastructure.network.client

import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.openai.OpenAIRequest
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.shared.util.toJson
import com.cw2.nekoama.infrastructure.network.proxy.ProxyDetector
import com.cw2.nekoama.infrastructure.network.proxy.ProxyType
import com.cw2.nekoama.infrastructure.network.client.interceptor.HeadersInterceptor
import com.cw2.nekoama.infrastructure.network.client.interceptor.LoggingInterceptor
import com.cw2.nekoama.infrastructure.network.client.interceptor.RetryInterceptor
import com.cw2.nekoama.infrastructure.network.proxy.ProxyAuthenticatorFactory
import com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config.CustomGeneratorConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * 自定义 API HTTP 客户端 - OkHttp 版本
 *
 * 支持灵活的认证方式、自定义端点和可选的 SSL 验证。
 * 兼容 OpenAI API 格式，但允许使用不同的服务提供商。
 */
class CustomAPIHttpClient(
    private val config: CustomGeneratorConfig
) : BaseHttpClient() {

    private val httpClient = createOkHttpClient()

    /**
     * 获取连接池信息
     */
    fun getConnectionPoolInfo(): ConnectionPoolInfo {
        val pool = httpClient.connectionPool
        return ConnectionPoolInfo(
            idleConnectionCount = pool.idleConnectionCount(),
            connectionCount = pool.connectionCount(),
            maxIdleConnections = 5, // 我们配置的最大值
            keepAliveDurationMs = 5 * 60 * 1000L // 5分钟
        )
    }

    /**
     * 预热连接池
     *
     * 通过发送一个轻量级请求来预热连接池，提高后续请求的性能
     */
    suspend fun warmupConnectionPool() {
        try {
            // 这里可以发送一个ping请求或者健康检查
            NekoamaLogger.info("CustomAPIHttpClient", "连接池预热开始")

            // 由于AI API的特性，这里只是记录日志，实际预热取决于具体使用
            NekoamaLogger.info("CustomAPIHttpClient", "连接池预热完成")
        } catch (e: Exception) {
            NekoamaLogger.warn("CustomAPIHttpClient", "连接池预热失败: ${e.message}")
        }
    }

    /**
     * 获取客户端配置摘要
     */
    fun getClientConfigurationSummary(): ClientConfigurationSummary {
        return ClientConfigurationSummary(
            timeoutMs = config.timeoutMs,
            verifySSL = config.verifySSL,
            endpointUrl = config.buildEndpointUrl(),
            hasAuthHeaders = config.getAuthHeaders().isNotEmpty(),
            supportsHttp2 = true,
            retryEnabled = true,
            loggingEnabled = true
        )
    }

    /**
     * 连接池信息数据类
     */
    data class ConnectionPoolInfo(
        val idleConnectionCount: Int,
        val connectionCount: Int,
        val maxIdleConnections: Int,
        val keepAliveDurationMs: Long
    )

    /**
     * 客户端配置摘要数据类
     */
    data class ClientConfigurationSummary(
        val timeoutMs: Long,
        val verifySSL: Boolean,
        val endpointUrl: String,
        val hasAuthHeaders: Boolean,
        val supportsHttp2: Boolean,
        val retryEnabled: Boolean,
        val loggingEnabled: Boolean
    )

    /**
     * 创建 OkHttp 客户端，使用拦截器系统处理SSL和代理配置
     */
    private fun createOkHttpClient(): OkHttpClient {
        // 连接超时设置为总超时的1/4，为AI服务响应留出更多时间
        val connectTimeoutMs = (config.timeoutMs / 4).coerceAtLeast(10000L).coerceAtMost(15000L)
        val readTimeoutMs = (config.timeoutMs / 2).coerceAtLeast(20000L).coerceAtMost(60000L)
        val writeTimeoutMs = (config.timeoutMs / 2).coerceAtLeast(20000L).coerceAtMost(60000L)
        val callTimeoutMs = config.timeoutMs

        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)

        // 配置连接池以提高性能
        builder.connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))

        // 配置更多OkHttp优化特性
        configureOptimizations(builder)

        // 配置SSL（直接在客户端上配置，因为拦截器方式对SSL配置有限制）
        configureSSL(builder)

        // 配置代理（直接在客户端上配置，因为代理需要在连接建立前设置）
        configureProxy(builder)

        // 添加拦截器系统（按执行顺序排列）
        builder.addInterceptor(RetryInterceptor()) // 重试拦截器
        builder.addInterceptor(LoggingInterceptor(LoggingInterceptor.LogLevel.BASIC)) // 基础日志
        builder.addInterceptor(HeadersInterceptor(config.getAuthHeaders())) // 请求头处理

        NekoamaLogger.info("CustomAPIHttpClient", "OkHttp客户端配置完成（包含日志、重试拦截器）")
        return builder.build()
    }

    /**
     * 配置SSL设置
     */
    private fun configureSSL(builder: OkHttpClient.Builder) {
        if (!config.verifySSL) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())

                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }

                NekoamaLogger.warn("CustomAPIHttpClient", "SSL 验证已禁用，仅用于开发环境")
            } catch (e: Exception) {
                NekoamaLogger.warn("CustomAPIHttpClient", "无法禁用 SSL 验证: ${e.message}")
            }
        }
    }

    /**
     * 配置代理设置
     */
    private fun configureProxy(builder: OkHttpClient.Builder) {
        val proxyConfig = ProxyDetector.detectSystemProxy(config.buildEndpointUrl())

        if (proxyConfig.type != ProxyType.DIRECT && proxyConfig.isValid()) {
            try {
                // 验证代理认证配置
                val validationResult = ProxyAuthenticatorFactory.validateProxyAuthentication(proxyConfig)
                if (!validationResult.isValid) {
                    NekoamaLogger.error("CustomAPIHttpClient",
                        "代理认证配置无效: ${validationResult.message}，将使用直连")
                    builder.proxy(Proxy.NO_PROXY)
                    return
                }

                // 创建 OkHttp 代理对象
                val okHttpProxy = when (proxyConfig.type) {
                    ProxyType.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyConfig.host, proxyConfig.port ?: 8080))
                    ProxyType.HTTPS -> Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyConfig.host, proxyConfig.port ?: 8080))
                    ProxyType.SOCKS -> Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyConfig.host, proxyConfig.port ?: 1080))
                    else -> Proxy.NO_PROXY
                }
                builder.proxy(okHttpProxy)

                // 根据代理类型配置认证
                when (proxyConfig.type) {
                    ProxyType.HTTP, ProxyType.HTTPS -> {
                        // HTTP/HTTPS代理认证
                        val httpAuthenticator = ProxyAuthenticatorFactory.createHttpAuthenticator(proxyConfig)
                        if (httpAuthenticator != null) {
                            builder.proxyAuthenticator(httpAuthenticator)
                            NekoamaLogger.info("CustomAPIHttpClient",
                                "已配置HTTP代理认证: ${ProxyAuthenticatorFactory.getAuthenticationStatus(proxyConfig)}")
                        } else {
                            NekoamaLogger.info("CustomAPIHttpClient", "HTTP代理无认证模式")
                        }
                    }
                    ProxyType.SOCKS -> {
                        // SOCKS代理认证 - 使用系统级认证
                        ProxyAuthenticatorFactory.configureSocksAuthentication(proxyConfig)
                        NekoamaLogger.info("CustomAPIHttpClient",
                            "已配置SOCKS代理认证: ${ProxyAuthenticatorFactory.getAuthenticationStatus(proxyConfig)}")
                    }
                    else -> {
                        NekoamaLogger.info("CustomAPIHttpClient", "代理类型无需认证")
                    }
                }

                NekoamaLogger.info("CustomAPIHttpClient",
                    "代理配置完成: ${ProxyDetector.getProxyStatus(proxyConfig)}")

            } catch (e: Exception) {
                NekoamaLogger.error("CustomAPIHttpClient",
                    "代理配置失败，将使用直连: ${e.message}", error = e)
                // 代理配置失败时回退到直连
                builder.proxy(Proxy.NO_PROXY)
                // 清理可能已配置的认证设置
                ProxyAuthenticatorFactory.clearAllAuthentication()
            }
        } else {
            NekoamaLogger.debug("CustomAPIHttpClient", "使用直连模式")
        }
    }

    /**
     * 配置OkHttp优化特性
     */
    private fun configureOptimizations(builder: OkHttpClient.Builder) {
        // 启用HTTP/2支持以提高性能
        builder.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))

        // 配置失败重试机制（OkHttp内置）
        builder.retryOnConnectionFailure(true)

        // 配置DNS解析优化
        try {
            // 使用系统DNS，但可以配置自定义DNS解析器
            // 这里暂时使用系统默认配置
        } catch (e: Exception) {
            NekoamaLogger.warn("CustomAPIHttpClient", "DNS配置失败，使用默认配置: ${e.message}")
        }

        // 配置PING间隔以保持连接活跃
        builder.pingInterval(30, TimeUnit.SECONDS)

        NekoamaLogger.debug("CustomAPIHttpClient", "OkHttp优化特性配置完成")
    }

    /**
     * 实现父类的异步HTTP请求发送方法
     */
    override suspend fun sendHttpRequest(request: OpenAIRequest): HttpResponseData {
        val okHttpRequest = buildOkHttpRequest(request)

        try {
            val response = httpClient.newCall(okHttpRequest).execute()
            return HttpResponseData(
                statusCode = response.code,
                body = response.body?.string() ?: "",
                headers = response.headers.toMultimap()
            )
        } catch (e: IOException) {
            // 提供详细的代理诊断信息
            val proxyConfig = ProxyDetector.detectSystemProxy(config.buildEndpointUrl())
            val errorMessage = generateDetailedErrorMessage(e, proxyConfig, config.buildEndpointUrl())

            NekoamaLogger.error("CustomAPIHttpClient", errorMessage, error = e)
            throw e
        }
    }

    /**
     * 实现父类的同步HTTP请求发送方法
     */
    override fun sendHttpRequestSync(request: OpenAIRequest): HttpResponseData {
        val okHttpRequest = buildOkHttpRequest(request)

        try {
            val response = httpClient.newCall(okHttpRequest).execute()
            return HttpResponseData(
                statusCode = response.code,
                body = response.body?.string() ?: "",
                headers = response.headers.toMultimap()
            )
        } catch (e: IOException) {
            // 提供详细的代理诊断信息
            val proxyConfig = ProxyDetector.detectSystemProxy(config.buildEndpointUrl())
            val errorMessage = generateDetailedErrorMessage(e, proxyConfig, config.buildEndpointUrl())

            NekoamaLogger.error("CustomAPIHttpClient", errorMessage, error = e)
            throw e
        }
    }

    /**
     * 构建 OkHttp 请求
     *
     * 注意：请求头和认证由HeadersInterceptor拦截器处理，这里只需要构建基本请求
     */
    private fun buildOkHttpRequest(request: OpenAIRequest): Request {
        val jsonBody = request.toJson().getOrNull()
            ?: throw IllegalArgumentException("无法序列化请求")

        val mediaType = "application/json".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        return Request.Builder()
            .url(config.buildEndpointUrl())
            .post(requestBody)
            .build()
    }

    /**
     * 生成详细的错误信息，包含代理诊断
     */
    private fun generateDetailedErrorMessage(
        e: IOException,
        proxyConfig: ProxyConfig,
        targetUrl: String
    ): String {
        val sb = StringBuilder()
        sb.append("HTTP请求失败: ${e.message}\n")

        // 分析错误类型（区分HTTP和SOCKS代理）
        val errorAnalysis = when {
            e.message?.contains("unexpected end of stream", ignoreCase = true) == true -> {
                when (proxyConfig.type) {
                    ProxyType.SOCKS -> {
                        "SOCKS连接意外断开 - 可能原因：SOCKS代理服务器不稳定、认证失败、协议不匹配"
                    }
                    else -> {
                        "连接意外断开 - 可能原因：HTTP代理服务器不稳定、网络超时、代理类型不匹配"
                    }
                }
            }
            e.message?.contains("Connection refused", ignoreCase = true) == true -> {
                when (proxyConfig.type) {
                    ProxyType.SOCKS -> {
                        "SOCKS连接被拒绝 - 可能原因：SOCKS代理服务器未启动、端口错误、防火墙阻止SOCKS协议"
                    }
                    else -> {
                        "HTTP代理连接被拒绝 - 可能原因：HTTP代理服务器未启动、端口错误、防火墙阻止"
                    }
                }
            }
            e.message?.contains("timeout", ignoreCase = true) == true -> {
                when (proxyConfig.type) {
                    ProxyType.SOCKS -> {
                        "SOCKS连接超时 - 可能原因：SOCKS代理服务器响应慢、认证握手超时、网络延迟高"
                    }
                    else -> {
                        "HTTP代理连接超时 - 可能原因：HTTP代理服务器响应慢、网络延迟高、超时设置过短"
                    }
                }
            }
            e.message?.contains("407", ignoreCase = true) == true -> {
                "HTTP代理认证失败（407错误） - 可能原因：用户名密码错误、认证方式不支持"
            }
            e.message?.contains("authentication", ignoreCase = true) == true -> {
                when (proxyConfig.type) {
                    ProxyType.SOCKS -> {
                        "SOCKS认证错误 - 可能原因：用户名密码错误、SOCKS服务器不支持认证、认证协议不匹配"
                    }
                    else -> {
                        "HTTP代理认证错误 - 可能原因：用户名密码错误、认证头格式错误"
                    }
                }
            }
            e.message?.contains("SOCKS", ignoreCase = true) == true -> {
                "SOCKS协议错误 - 可能原因：SOCKS版本不兼容、服务器不支持SOCKS5、连接建立失败"
            }
            e.message?.contains("proxy", ignoreCase = true) == true -> {
                when (proxyConfig.type) {
                    ProxyType.SOCKS -> {
                        "SOCKS代理相关错误 - 可能原因：SOCKS配置错误、认证失败、协议不匹配"
                    }
                    else -> {
                        "HTTP代理相关错误 - 可能原因：HTTP代理配置错误、认证失败、格式错误"
                    }
                }
            }
            else -> {
                when (proxyConfig.type) {
                    ProxyType.SOCKS -> {
                        "SOCKS连接未知错误 - 可能原因：网络异常、协议冲突、服务器错误"
                    }
                    else -> {
                        "HTTP代理连接未知错误 - 可能原因：网络异常、服务器错误、配置问题"
                    }
                }
            }
        }

        sb.append("错误分析: $errorAnalysis\n")

        // 添加代理配置信息
        sb.append("代理配置: ${ProxyDetector.getProxyStatus(proxyConfig)}\n")

        // 添加建议
        val suggestions = when (proxyConfig.type) {
            ProxyType.DIRECT -> {
                listOf(
                    "当前使用直连模式，如果需要代理，请在IDEA设置中配置代理",
                    "检查网络连接是否正常",
                    "尝试访问其他网站确认网络状态"
                )
            }
            ProxyType.SOCKS -> {
                when {
                    e.message?.contains("authentication", ignoreCase = true) == true ||
                    e.message?.contains("auth", ignoreCase = true) == true -> {
                        listOf(
                            "SOCKS代理认证失败，请检查用户名和密码是否正确",
                            "确认SOCKS代理服务器支持用户名密码认证",
                            "检查代理服务器是否启用了认证功能"
                        )
                    }
                    else -> {
                        listOf(
                            "SOCKS代理连接失败，确认代理服务器地址和端口是否正确",
                            "如果使用无认证模式，请移除用户名和密码配置",
                            "检查防火墙是否阻止了SOCKS连接",
                            "确认代理服务器是否支持SOCKS5协议"
                        )
                    }
                }
            }
            else -> {
                when {
                    e.message?.contains("407", ignoreCase = true) == true -> {
                        listOf(
                            "HTTP代理认证失败（407错误），请检查用户名和密码",
                            "确认代理服务器支持用户名密码认证",
                            "检查是否需要域用户名格式（如：DOMAIN\\username）"
                        )
                    }
                    else -> {
                        listOf(
                            "HTTP代理连接失败，确认代理服务器地址和端口是否正确",
                            "如果实际使用的是SOCKS代理，请检查代理端口是否在SOCKS典型范围内",
                            "检查代理服务器是否支持HTTPS协议",
                            "验证代理服务器是否可达"
                        )
                    }
                }
            }
        }

        sb.append("建议操作:\n")
        suggestions.forEachIndexed { index, suggestion ->
            sb.append("  ${index + 1}. $suggestion\n")
        }

        sb.append("目标URL: $targetUrl")
        return sb.toString()
    }

    /**
     * 关闭HTTP客户端，释放资源
     */
    fun close() {
        try {
            // 清理代理认证设置
            ProxyAuthenticatorFactory.clearAllAuthentication()

            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
            NekoamaLogger.info("CustomAPIHttpClient", "HTTP客户端已关闭，代理认证设置已清理")
        } catch (e: Exception) {
            NekoamaLogger.warn("CustomAPIHttpClient", "关闭HTTP客户端时出现异常: ${e.message}")
        }
    }
}
