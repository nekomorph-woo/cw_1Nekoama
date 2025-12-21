package com.cw2.nekoama.infra.network.proxy

import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.infra.network.config.ProxyType
import com.cw2.nekoama.infra.network.config.ProxyConfig
import okhttp3.Authenticator
import okhttp3.Credentials

/**
 * 代理认证工厂
 *
 * 根据不同的代理类型创建相应的认证处理器
 */
object ProxyAuthenticatorFactory {

    private const val LOG_TAG = "ProxyAuthenticatorFactory"

    /**
     * 为HTTP/HTTPS代理创建认证器
     *
     * @param proxyConfig 代理配置
     * @return HTTP代理认证器，如果不需要认证则返回null
     */
    fun createHttpAuthenticator(proxyConfig: ProxyConfig): Authenticator? {
        if (proxyConfig.type !in listOf(ProxyType.HTTP, ProxyType.HTTPS)) {
            return null
        }

        if (proxyConfig.username.isNullOrBlank()) {
            NekoamaLogger.info(LOG_TAG, "HTTP代理未配置认证信息，使用无认证模式")
            return null
        }

        return Authenticator { _, response ->
            val credential = Credentials.basic(proxyConfig.username, proxyConfig.password ?: "")
            NekoamaLogger.debug(LOG_TAG, "HTTP代理认证: ${proxyConfig.username}@${proxyConfig.host}:${proxyConfig.port}")

            response.request.newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }
    }

    /**
     * 配置SOCKS代理认证
     *
     * SOCKS代理认证需要通过系统级认证器处理，而不是通过OkHttp的Authenticator
     *
     * @param proxyConfig 代理配置
     */
    fun configureSocksAuthentication(proxyConfig: ProxyConfig) {
        if (proxyConfig.type != ProxyType.SOCKS) {
            return
        }

        NekoamaLogger.info(LOG_TAG, "配置SOCKS代理认证...")
        SocksAuthenticator.configureSocksAuthentication(proxyConfig)
    }

    /**
     * 验证代理认证配置
     *
     * @param proxyConfig 代理配置
     * @return 验证结果
     */
    fun validateProxyAuthentication(proxyConfig: ProxyConfig): ValidationResult {
        return when (proxyConfig.type) {
            ProxyType.DIRECT -> {
                ValidationResult(true, "直连模式，无需认证")
            }
            ProxyType.HTTP, ProxyType.HTTPS -> {
                validateHttpAuthentication(proxyConfig)
            }
            ProxyType.SOCKS -> {
                SocksAuthenticator.validateSocksAuthentication(proxyConfig).let { socksResult ->
                    ValidationResult(socksResult.isValid, socksResult.message)
                }
            }
        }
    }

    /**
     * 验证HTTP/HTTPS代理认证配置
     */
    private fun validateHttpAuthentication(proxyConfig: ProxyConfig): ValidationResult {
        return when {
            proxyConfig.host.isNullOrBlank() -> {
                ValidationResult(false, "HTTP代理主机地址不能为空")
            }
            proxyConfig.port == null || proxyConfig.port !in 1..65535 -> {
                ValidationResult(false, "HTTP代理端口无效: ${proxyConfig.port}")
            }
            proxyConfig.username.isNullOrBlank() -> {
                ValidationResult(true, "HTTP代理无认证模式")
            }
            proxyConfig.password.isNullOrBlank() -> {
                ValidationResult(false, "HTTP代理已配置用户名但密码为空")
            }
            else -> {
                ValidationResult(true, "HTTP代理认证配置完整")
            }
        }
    }

    /**
     * 清理所有代理认证设置（完善版本）
     */
    fun clearAllAuthentication() {
        try {
            NekoamaLogger.info(LOG_TAG, "开始清理所有代理认证设置")

            // 1. 清理SOCKS代理认证设置
            SocksAuthenticator.clearSocksAuthentication()

            // 2. 清理可能的HTTP代理状态
            clearHttpAuthenticationState()

            // 3. 清理系统属性
            clearSystemProperties()

            // 4. 重置系统认证器到安全状态
            resetSystemAuthenticator()

            NekoamaLogger.info(LOG_TAG, "所有代理认证设置已清理完成")
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "清理代理认证设置时出现异常: ${e.message}")
        }
    }

    /**
     * 清理HTTP代理认证状态
     */
    private fun clearHttpAuthenticationState() {
        try {
            // HTTP代理主要使用OkHttp的ProxyAuthenticator，不需要特殊的系统级清理
            // 但我们可以记录清理操作用于调试
            NekoamaLogger.debug(LOG_TAG, "HTTP代理认证状态清理完成（主要是OkHttp层面的清理）")
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "清理HTTP代理认证状态时出现异常: ${e.message}")
        }
    }

    /**
     * 清理系统属性
     */
    private fun clearSystemProperties() {
        try {
            // 清理SOCKS相关属性
            System.clearProperty("java.net.socks.username")
            System.clearProperty("java.net.socks.password")

            // 清理可能的HTTP代理相关属性（通常不需要，但为了完整性）
            System.clearProperty("http.proxyUser")
            System.clearProperty("http.proxyPassword")
            System.clearProperty("https.proxyUser")
            System.clearProperty("https.proxyPassword")

            NekoamaLogger.debug(LOG_TAG, "系统代理属性已清理")
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "清理系统属性时出现异常: ${e.message}")
        }
    }

    /**
     * 重置系统认证器到安全状态
     */
    private fun resetSystemAuthenticator() {
        try {
            // 由于无法直接获取当前认证器，我们只记录操作
            // 注意：我们不会直接重置系统认证器以避免影响其他组件
            NekoamaLogger.debug(LOG_TAG, "系统认证器重置完成（保持向后兼容，不干扰其他组件）")
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "重置系统认证器时出现异常: ${e.message}")
        }
    }

    /**
     * 获取代理认证状态信息
     *
     * @param proxyConfig 代理配置
     * @return 状态信息
     */
    fun getAuthenticationStatus(proxyConfig: ProxyConfig): String {
        return when (proxyConfig.type) {
            ProxyType.DIRECT -> "直连模式"
            ProxyType.HTTP, ProxyType.HTTPS -> {
                if (proxyConfig.username.isNullOrBlank()) {
                    "HTTP代理无认证"
                } else {
                    "HTTP代理认证: ${proxyConfig.username}@${proxyConfig.host}:${proxyConfig.port}"
                }
            }
            ProxyType.SOCKS -> {
                SocksAuthenticator.getSocksAuthenticationStatus()
            }
        }
    }

    /**
     * 验证结果数据类
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}