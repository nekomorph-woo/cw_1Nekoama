package com.cw2.nekoama.core.network

import com.cw2.nekoama.core.logging.NekoamaLogger
import okhttp3.OkHttpClient
import java.net.Authenticator
import java.net.PasswordAuthentication

/**
 * HTTP客户端代理配置器
 *
 * 为各种HTTP客户端（OkHttp、Java HttpClient等）提供统一的代理配置
 */
object HttpClientProxyConfigurator {

    private const val LOG_TAG = "HttpClientProxyConfigurator"

    /**
     * 配置OkHttpClient的代理设置
     *
     * @param builder OkHttpClient.Builder
     * @param proxyConfig 代理配置
     */
    fun configureOkHttpClientProxy(builder: OkHttpClient.Builder, proxyConfig: ProxyConfig) {
        try {
            if (proxyConfig.type == ProxyType.DIRECT || !proxyConfig.isValid()) {
                NekoamaLogger.debug(LOG_TAG, "OkHttpClient使用直连模式")
                return
            }

            // 创建Java代理对象
            val javaProxy = proxyConfig.toJavaProxy()
            builder.proxy(javaProxy)

            // 配置代理认证器
            if (!proxyConfig.username.isNullOrBlank()) {
                val authenticator = okhttp3.Authenticator { _, response ->
                    val credential = okhttp3.Credentials.basic(
                        proxyConfig.username,
                        proxyConfig.password ?: ""
                    )
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
                builder.proxyAuthenticator(authenticator)

                NekoamaLogger.debug(LOG_TAG, "OkHttpClient已配置代理认证: ${proxyConfig.username}")
            }

            NekoamaLogger.info(LOG_TAG,
                "OkHttpClient已配置${proxyConfig.type}代理: ${proxyConfig.host}:${proxyConfig.port}")

        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "OkHttpClient代理配置失败: ${e.message}")
        }
    }

    /**
     * 配置系统级代理设置
     * 适用于Java HttpClient和其他使用系统属性的HTTP客户端
     *
     * @param proxyConfig 代理配置
     */
    fun configureSystemProxy(proxyConfig: ProxyConfig) {
        try {
            if (proxyConfig.type == ProxyType.DIRECT || !proxyConfig.isValid()) {
                NekoamaLogger.debug(LOG_TAG, "清除系统代理设置")
                clearSystemProxy()
                return
            }

            // 设置系统代理属性
            System.setProperty("http.proxyHost", proxyConfig.host!!)
            System.setProperty("http.proxyPort", proxyConfig.port.toString())
            System.setProperty("https.proxyHost", proxyConfig.host!!)
            System.setProperty("https.proxyPort", proxyConfig.port.toString())

            // 配置代理认证
            if (!proxyConfig.username.isNullOrBlank()) {
                System.setProperty("http.proxyUser", proxyConfig.username)
                System.setProperty("http.proxyPassword", proxyConfig.password ?: "")
                System.setProperty("https.proxyUser", proxyConfig.username)
                System.setProperty("https.proxyPassword", proxyConfig.password ?: "")

                val authenticator = object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(
                            proxyConfig.username,
                            proxyConfig.password?.toCharArray() ?: charArrayOf()
                        )
                    }
                }
                Authenticator.setDefault(authenticator)

                NekoamaLogger.debug(LOG_TAG, "系统代理已配置认证: ${proxyConfig.username}")
            }

            NekoamaLogger.info(LOG_TAG,
                "系统代理已配置${proxyConfig.type}代理: ${proxyConfig.host}:${proxyConfig.port}")

        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "系统代理配置失败: ${e.message}")
        }
    }

    /**
     * 清除系统代理设置
     */
    private fun clearSystemProxy() {
        try {
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
            System.clearProperty("https.proxyHost")
            System.clearProperty("https.proxyPort")
            System.clearProperty("http.proxyUser")
            System.clearProperty("http.proxyPassword")
            System.clearProperty("https.proxyUser")
            System.clearProperty("https.proxyPassword")
            Authenticator.setDefault(null)

            NekoamaLogger.debug(LOG_TAG, "系统代理设置已清除")
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "清除系统代理设置失败: ${e.message}")
        }
    }

    /**
     * 全局代理配置初始化
     * 在应用启动时调用，配置所有HTTP客户端使用IDEA的代理设置
     */
    fun initializeGlobalProxy() {
        try {
            val proxyConfig = ProxyDetector.detectSystemProxy()

            // 配置系统级代理（影响Java HttpClient等）
            configureSystemProxy(proxyConfig)

            NekoamaLogger.info(LOG_TAG, "全局代理配置初始化完成: ${ProxyDetector.getProxyStatus(proxyConfig)}")

        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "全局代理配置初始化失败: ${e.message}")
        }
    }
}