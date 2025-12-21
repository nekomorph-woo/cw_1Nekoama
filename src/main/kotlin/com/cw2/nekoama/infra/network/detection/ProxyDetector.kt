package com.cw2.nekoama.infra.network.detection

import com.cw2.nekoama.infra.network.config.ProxyConfig
import com.cw2.nekoama.infra.network.config.ProxyType
import com.cw2.nekoama.shared.logging.NekoamaLogger
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
                    ProxyConfig.Companion.direct()
                }

                httpConfigurable.USE_HTTP_PROXY -> {
                    // 检测代理类型（HTTP/HTTPS/SOCKS）
                    val proxyType = detectProxyType(httpConfigurable)

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
                    ProxyConfig.Companion.direct()
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "代理检测失败，使用直连: ${e.message}")
            ProxyConfig.Companion.direct()
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
                return ProxyConfig.Companion.direct()
            }

            val proxyConfig = parseProxyString(proxyString)
            val bypassHosts = noProxy?.split(",")?.map { it.trim() } ?: emptyList()

            val config = proxyConfig.copy(bypassHosts = bypassHosts)
            NekoamaLogger.info(LOG_TAG, "检测到环境变量代理: $proxyString")
            config
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "环境变量代理解析失败: ${e.message}")
            ProxyConfig.Companion.direct()
        }
    }

    /**
     * 检测IDEA配置的代理类型
     *
     * 使用多维度评分系统提高检测准确性
     */
    private fun detectProxyType(httpConfigurable: HttpConfigurable): ProxyType {
        return try {
            val port = httpConfigurable.PROXY_PORT
            val host = httpConfigurable.PROXY_HOST
            val hasAuth = httpConfigurable.getProxyLogin()?.isNotBlank() == true

            // 使用评分系统
            val socksScore = calculateSocksScore(port, host, hasAuth)
            val httpScore = calculateHttpScore(port, host, hasAuth)

            val detectedType = when {
                socksScore > httpScore + 10 -> {
                    NekoamaLogger.info(LOG_TAG, "多维度检测确定为SOCKS代理 (SOCKS: $socksScore, HTTP: $httpScore)")
                    ProxyType.SOCKS
                }
                httpScore > socksScore + 10 -> {
                    NekoamaLogger.info(LOG_TAG, "多维度检测确定为HTTP代理 (HTTP: $httpScore, SOCKS: $socksScore)")
                    ProxyType.HTTP
                }
                socksScore == httpScore -> {
                    NekoamaLogger.warn(LOG_TAG, "代理类型检测不明确 (SOCKS: $socksScore, HTTP: $httpScore)，默认使用HTTP代理")
                    ProxyType.HTTP
                }
                else -> {
                    // 分数接近时的处理
                    val diff = Math.abs(socksScore - httpScore)
                    if (diff < 10) {
                        NekoamaLogger.warn(LOG_TAG, "代理类型检测置信度较低 (SOCKS: $socksScore, HTTP: $httpScore, 差异: $diff)")
                        NekoamaLogger.info(LOG_TAG, "如果检测错误，请检查代理端口是否在协议典型范围内")
                    }

                    // 选择分数较高的类型
                    if (socksScore > httpScore) ProxyType.SOCKS else ProxyType.HTTP
                }
            }

            // 记录详细的检测信息
            logDetectionDetails(port, host, hasAuth, socksScore, httpScore, detectedType)

            detectedType
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "代理类型检测失败: ${e.message}，使用HTTP代理")
            ProxyType.HTTP
        }
    }

    /**
     * 计算SOCKS代理的可能性分数
     */
    private fun calculateSocksScore(port: Int, host: String?, hasAuth: Boolean): Int {
        var score = 0

        // 端口评分（权重最高）
        score += when {
            port == 1080 || port == 1081 -> 50  // 标准SOCKS端口
            port in 1082..1089 -> 40              // SOCKS扩展端口
            port in 10000..65535 -> 30            // 高端口（动态代理）
            port in 1024..1089 -> 25              // 保留端口范围
            port in 9000..9999 -> 15              // 可能的SOCKS端口
            else -> 0
        }

        // 主机名评分
        if (host != null) {
            when {
                host.contains("socks", ignoreCase = true) -> score += 20
                host.contains("tunnel", ignoreCase = true) -> score += 15
                host.contains("gateway", ignoreCase = true) -> score += 10
                host.contains("proxy", ignoreCase = true) -> score += 5
                else -> score += 0
            }
        }

        // 认证评分（SOCKS更常用认证）
        if (hasAuth) {
            score += 10
        }

        return score
    }

    /**
     * 计算HTTP代理的可能性分数
     */
    private fun calculateHttpScore(port: Int, host: String?, hasAuth: Boolean): Int {
        var score = 0

        // 端口评分（权重最高）
        score += when {
            port == 3128 || port == 3129 -> 50     // 标准HTTP代理端口
            port == 8080 || port == 8081 -> 45     // 常用HTTP代理端口
            port in 8082..8089 -> 40                // HTTP代理扩展端口
            port == 8000 || port == 8001 -> 35     // 开发代理端口
            port in 8888..8899 -> 30                // 备用HTTP代理端口
            port in 7000..7999 -> 20                // 可能的HTTP代理端口
            port in 80..90 -> 15                    // HTTP相关端口
            port in 3000..5000 -> 10               // 开发服务器端口
            else -> 0
        }

        // 主机名评分
        if (host != null) {
            when {
                host.contains("http", ignoreCase = true) -> score += 20
                host.contains("web", ignoreCase = true) -> score += 15
                host.contains("cache", ignoreCase = true) -> score += 10
                host.contains("squid", ignoreCase = true) -> score += 15
                host.contains("nginx", ignoreCase = true) -> score += 10
                host.contains("apache", ignoreCase = true) -> score += 10
                host.contains("proxy", ignoreCase = true) -> score += 5
                else -> score += 0
            }
        }

        // HTTP代理相对较少需要认证（除非企业环境）
        if (hasAuth) {
            score += 5
        }

        return score
    }

    /**
     * 记录详细的检测信息
     */
    private fun logDetectionDetails(port: Int, host: String?, hasAuth: Boolean, socksScore: Int, httpScore: Int, detectedType: ProxyType) {
        NekoamaLogger.info(LOG_TAG, "=== 代理类型检测详情 ===")
        NekoamaLogger.info(LOG_TAG, "端口: $port")
        NekoamaLogger.info(LOG_TAG, "主机: $host")
        NekoamaLogger.info(LOG_TAG, "认证: ${if (hasAuth) "是" else "否"}")
        NekoamaLogger.info(LOG_TAG, "SOCKS评分: $socksScore")
        NekoamaLogger.info(LOG_TAG, "HTTP评分: $httpScore")
        NekoamaLogger.info(LOG_TAG, "检测结果: $detectedType")
        NekoamaLogger.info(LOG_TAG, "置信度: ${if (Math.abs(socksScore - httpScore) > 20) "高" else if (Math.abs(socksScore - httpScore) > 10) "中" else "低"}")
        NekoamaLogger.info(LOG_TAG, "=========================")
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
                    ProxyConfig.Companion.direct()
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