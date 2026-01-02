package com.cw2.nekoama.infrastructure.network.proxy

import com.cw2.nekoama.shared.logging.NekoamaLogger

/**
 * 代理初始化管理器
 *
 * 在插件启动时初始化全局代理配置，确保所有HTTP客户端都能正确使用IDEA的代理设置
 */
object ProxyInitializationManager {

    private const val LOG_TAG = "ProxyInitializationManager"
    private var isInitialized = false

    /**
     * 初始化全局代理配置
     * 在插件启动时调用
     */
    fun initialize() {
        if (isInitialized) {
            NekoamaLogger.debug(LOG_TAG, "代理配置已经初始化，跳过重复初始化")
            return
        }

        try {
            NekoamaLogger.info(LOG_TAG, "开始初始化全局代理配置...")

            // 初始化全局代理配置
            HttpClientProxyConfigurator.initializeGlobalProxy()

            isInitialized = true
            NekoamaLogger.info(LOG_TAG, "全局代理配置初始化完成")

        } catch (e: Exception) {
            NekoamaLogger.error(LOG_TAG, "全局代理配置初始化失败: ${e.message}")
        }
    }

    /**
     * 重新初始化代理配置
     * 当IDEA代理设置发生变化时调用
     */
    fun reinitialize() {
        NekoamaLogger.info(LOG_TAG, "重新初始化代理配置...")
        isInitialized = false
        initialize()
    }

    /**
     * 获取当前代理状态
     */
    fun getProxyStatus(): String {
        return try {
            val proxyConfig = ProxyDetector.detectSystemProxy()
            ProxyDetector.getProxyStatus(proxyConfig)
        } catch (e: Exception) {
            "代理状态获取失败: ${e.message}"
        }
    }

    /**
     * 检查代理配置是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
}
