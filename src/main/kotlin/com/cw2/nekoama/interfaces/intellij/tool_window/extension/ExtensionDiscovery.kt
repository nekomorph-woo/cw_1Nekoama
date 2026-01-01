package com.cw2.nekoama.interfaces.intellij.tool_window.extension

import com.cw2.nekoama.shared.logging.NekoamaLogger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 扩展发现器
 *
 * 负责自动发现和加载Tab扩展。
 */
class ExtensionDiscovery {

    private val discoveredExtensions = CopyOnWriteArrayList<TabExtension>()
    private val logger = NekoamaLogger

    companion object {
        @Volatile
        private var INSTANCE: ExtensionDiscovery? = null

        fun getInstance(): ExtensionDiscovery {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExtensionDiscovery().also { INSTANCE = it }
            }
        }
    }

    /**
     * 发现所有可用的扩展
     */
    fun discoverExtensions(): List<TabExtension> {
        if (discoveredExtensions.isNotEmpty()) {
            return discoveredExtensions.toList()
        }

        try {
            logger.info("ExtensionDiscovery", "Starting extension discovery...")

            // 发现内置扩展
            discoverBuiltinExtensions()

            // 发现配置文件扩展
            discoverConfigFileExtensions()

            // 发现插件扩展
            discoverPluginExtensions()

            logger.info("ExtensionDiscovery", "Extension discovery completed. Found ${discoveredExtensions.size} extensions")

        } catch (e: Exception) {
            logger.error("ExtensionDiscovery", "Failed to discover extensions", error = e)
        }

        return discoveredExtensions.toList()
    }

    /**
     * 发现内置扩展
     */
    private fun discoverBuiltinExtensions() {
        try {
            logger.debug("ExtensionDiscovery", "Discovering builtin extensions...")
            // 内置扩展已移除，这里可以添加其他内置扩展
        } catch (e: Exception) {
            logger.error("ExtensionDiscovery", "Failed to discover builtin extensions", error = e)
        }
    }

    /**
     * 发现配置文件扩展
     */
    private fun discoverConfigFileExtensions() {
        try {
            logger.debug("ExtensionDiscovery", "Discovering config file extensions...")

            // 这里可以实现从配置文件加载扩展的逻辑
            // 例如：从 JSON/XML/YAML 配置文件中读取扩展定义
            // 目前为空实现，保留扩展点

            logger.debug("ExtensionDiscovery", "Config file extensions discovery completed")

        } catch (e: Exception) {
            logger.error("ExtensionDiscovery", "Failed to discover config file extensions", error = e)
        }
    }

    /**
     * 发现插件扩展
     */
    private fun discoverPluginExtensions() {
        try {
            logger.debug("ExtensionDiscovery", "Discovering plugin extensions...")

            // 这里可以实现从插件系统发现扩展的逻辑
            // 例如：通过 IntelliJ 插件系统的扩展点发现其他插件提供的扩展
            // 目前为空实现，保留扩展点

            logger.debug("ExtensionDiscovery", "Plugin extensions discovery completed")

        } catch (e: Exception) {
            logger.error("ExtensionDiscovery", "Failed to discover plugin extensions", error = e)
        }
    }

    /**
     * 根据ID获取扩展
     */
    fun getExtension(extensionId: String): TabExtension? {
        return discoveredExtensions.find { it.extensionId == extensionId }
    }

    /**
     * 获取所有发现的扩展
     */
    fun getAllExtensions(): List<TabExtension> {
        return discoveredExtensions.toList()
    }

    /**
     * 手动注册扩展
     */
    fun registerExtension(extension: TabExtension) {
        if (!discoveredExtensions.any { it.extensionId == extension.extensionId }) {
            discoveredExtensions.add(extension)
            logger.info("ExtensionDiscovery", "Extension manually registered: ${extension.extensionId}")
        } else {
            logger.warn("ExtensionDiscovery", "Extension already registered: ${extension.extensionId}")
        }
    }

    /**
     * 手动注销扩展
     */
    fun unregisterExtension(extensionId: String) {
        val removed = discoveredExtensions.removeAll { it.extensionId == extensionId }
        if (removed) {
            logger.info("ExtensionDiscovery", "Extension unregistered: $extensionId")
        } else {
            logger.warn("ExtensionDiscovery", "Extension not found for unregistration: $extensionId")
        }
    }

    /**
     * 重新发现扩展
     */
    fun rediscoverExtensions(): List<TabExtension> {
        discoveredExtensions.clear()
        return discoverExtensions()
    }

    /**
     * 获取扩展统计信息
     */
    fun getDiscoveryStats(): Map<String, Any> {
        return mapOf(
            "totalExtensions" to discoveredExtensions.size,
            "extensionIds" to discoveredExtensions.map { it.extensionId },
            "enabledExtensions" to discoveredExtensions.filter { it.isEnabled }.map { it.extensionId },
            "lastDiscoveryTime" to System.currentTimeMillis()
        )
    }
}