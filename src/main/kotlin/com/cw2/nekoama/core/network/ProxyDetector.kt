package com.cw2.nekoama.core.network

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.util.net.HttpConfigurable
import java.net.URI

/**
 * IDEA系统代理检测器
 *
 * 自动检测和获取IDEA的系统代理设置，支持所有主流代理类型
 */
object ProxyDetector {

    private const val LOG_TAG = "ProxyDetector"

    /**
     * 检测并获取系统代理配置
     *
     * @param targetUrl 目标URL，用于确定代理选择
     * @return 代理配置
     */
    fun detectSystemProxy(targetUrl: String? = null): ProxyConfig {
        return try {
            val httpConfigurable = HttpConfigurable.getInstance()

            when {
                !httpConfigurable.USE_HTTP_PROXY && !httpConfigurable.USE_PROXY_PAC -> {
                    NekoamaLogger.info(LOG_TAG, "IDEA代理未启用，使用直连")
                    ProxyConfig.direct()
                }

                httpConfigurable.USE_HTTP_PROXY -> {
                    // HTTP代理
                    val proxyType = ProxyType.HTTP // 暂时简化处理

                    val config = ProxyConfig(
                        type = proxyType,
                        host = httpConfigurable.PROXY_HOST,
                        port = httpConfigurable.PROXY_PORT,
                        username = httpConfigurable.getProxyLogin()?.takeIf { it.isNotBlank() },
                        password = httpConfigurable.getPlainProxyPassword()?.takeIf { it.isNotBlank() }
                    )
                    NekoamaLogger.info(LOG_TAG, "检测到${proxyType}代理: ${httpConfigurable.PROXY_HOST}:${httpConfigurable.PROXY_PORT}")
                    config
                }

                httpConfigurable.USE_PROXY_PAC -> {
                    // PAC代理暂不支持，回退到环境变量检测
                    NekoamaLogger.info(LOG_TAG, "检测到PAC代理，回退到环境变量检测")
                    detectEnvironmentProxy()
                }

                else -> {
                    NekoamaLogger.info(LOG_TAG, "未检测到适用的代理配置，使用直连")
                    ProxyConfig.direct()
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "代理检测失败，使用直连: ${e.message}")
            ProxyConfig.direct()
        }
    }

    /**
     * 从环境变量检测代理配置
     *
     * @return 代理配置，如果没有找到则返回直连
     */
    fun detectEnvironmentProxy(): ProxyConfig {
        return try {
            val httpProxy = System.getenv("HTTP_PROXY") ?: System.getenv("http_proxy")
            val httpsProxy = System.getenv("HTTPS_PROXY") ?: System.getenv("https_proxy")
            val noProxy = System.getenv("NO_PROXY") ?: System.getenv("no_proxy")

            val proxyString = httpsProxy ?: httpProxy

            if (proxyString.isNullOrBlank()) {
                NekoamaLogger.info(LOG_TAG, "未找到环境变量代理配置")
                return ProxyConfig.direct()
            }

            val proxyConfig = parseProxyString(proxyString)
            val bypassHosts = noProxy?.split(",")?.map { it.trim() } ?: emptyList()

            val config = proxyConfig.copy(bypassHosts = bypassHosts)
            NekoamaLogger.info(LOG_TAG, "检测到环境变量代理: $proxyString")
            config
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "环境变量代理解析失败: ${e.message}")
            ProxyConfig.direct()
        }
    }

    /**
     * 解析代理字符串
     *
     * 支持格式：
     * - http://proxy.example.com:8080
     * - https://proxy.example.com:8080
     * - socks5://user:pass@proxy.example.com:1080
     * - proxy.example.com:8080
     *
     * @param proxyString 代理字符串
     * @return 代理配置
     */
    private fun parseProxyString(proxyString: String): ProxyConfig {
        try {
            val uri = URI.create(proxyString)

            val proxyType = when (uri.scheme?.lowercase()) {
                "http" -> ProxyType.HTTP
                "https" -> ProxyType.HTTPS
                "socks", "socks4", "socks5" -> ProxyType.SOCKS
                else -> ProxyType.HTTP // 默认HTTP
            }

            val userInfo = uri.userInfo
            val (username, password) = if (userInfo != null && userInfo.contains(":")) {
                val parts = userInfo.split(":", limit = 2)
                parts[0] to parts.getOrNull(1)
            } else {
                null to null
            }

            val host = uri.host
            val port = uri.port.takeIf { it > 0 } ?: when (proxyType) {
                ProxyType.SOCKS -> 1080
                else -> 8080
            }

            return ProxyConfig(
                type = proxyType,
                host = host,
                port = port,
                username = username,
                password = password
            )
        } catch (e: Exception) {
            // 尝试简单的 host:port 格式
            val parts = proxyString.split(":")
            return when (parts.size) {
                2 -> ProxyConfig(
                    type = ProxyType.HTTP,
                    host = parts[0].trim(),
                    port = parts[1].trim().toIntOrNull() ?: 8080
                )
                else -> {
                    NekoamaLogger.warn(LOG_TAG, "无法解析代理字符串: $proxyString")
                    ProxyConfig.direct()
                }
            }
        }
    }

    
    /**
     * 获取代理状态信息
     *
     * @param proxyConfig 代理配置
     * @return 状态描述字符串
     */
    fun getProxyStatus(proxyConfig: ProxyConfig): String {
        return when (proxyConfig.type) {
            ProxyType.DIRECT -> "直连"
            else -> {
                val auth = if (proxyConfig.username.isNullOrBlank()) "无认证" else "已认证"
                "${proxyConfig.type.name}代理: ${proxyConfig.host}:${proxyConfig.port} ($auth)"
            }
        }
    }
}