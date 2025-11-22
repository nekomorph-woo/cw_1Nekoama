package com.cw2.nekoama.presentation.toolwindow.extension

import com.cw2.nekoama.core.logging.NekoamaLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tab扩展点实现
 *
 * 负责管理Tab扩展的注册、生命周期和事件通知。
 */
class TabExtensionPointImpl : TabExtensionPoint {

    private val registeredExtensions = ConcurrentHashMap<String, TabExtension>()
    private val listeners = CopyOnWriteArrayList<TabExtensionPoint.ExtensionChangeListener>()

    private val logger = NekoamaLogger

    override fun registerExtension(extension: TabExtension): Boolean {
        return try {
            // 检查是否已注册
            if (registeredExtensions.containsKey(extension.extensionId)) {
                logger.warn("TabExtensionPoint", "Extension already registered: ${extension.extensionId}")
                return false
            }

            // 验证扩展
            val validationResult = validateExtension(extension)
            if (validationResult is ValidationResult.Invalid) {
                logger.error("TabExtensionPoint", "Extension validation failed: ${extension.extensionId} - ${validationResult.reason}")
                return false
            }

            // 检查兼容性
            val pluginVersion = getCurrentPluginVersion()
            if (!extension.isCompatible(pluginVersion)) {
                logger.warn("TabExtensionPoint", "Extension incompatible: ${extension.extensionId}")
                return false
            }

            // 注册扩展
            registeredExtensions[extension.extensionId] = extension
            extension.initialize()

            // 通知监听器
            listeners.forEach { listener ->
                try {
                    listener.onExtensionRegistered(extension)
                } catch (e: Exception) {
                    logger.error("TabExtensionPoint", "Error notifying listener of extension registration", error = e)
                }
            }

            logger.info("TabExtensionPoint", "Extension registered successfully: ${extension.extensionId}")
            true

        } catch (e: Exception) {
            logger.error("TabExtensionPoint", "Failed to register extension: ${extension.extensionId}", error = e)
            false
        }
    }

    override fun unregisterExtension(extensionId: String): Boolean {
        return try {
            val extension = registeredExtensions.remove(extensionId) ?: return false

            // 销毁扩展
            extension.dispose()

            // 通知监听器
            listeners.forEach { listener ->
                try {
                    listener.onExtensionUnregistered(extensionId)
                } catch (e: Exception) {
                    logger.error("TabExtensionPoint", "Error notifying listener of extension unregistration", error = e)
                }
            }

            logger.info("TabExtensionPoint", "Extension unregistered successfully: $extensionId")
            true

        } catch (e: Exception) {
            logger.error("TabExtensionPoint", "Failed to unregister extension: $extensionId", error = e)
            false
        }
    }

    override fun getRegisteredExtensions(): List<TabExtension> {
        return registeredExtensions.values.toList()
    }

    override fun getExtension(extensionId: String): TabExtension? {
        return registeredExtensions[extensionId]
    }

    override fun getEnabledExtensions(): List<TabExtension> {
        return registeredExtensions.values
            .filter { it.isEnabled }
            .sortedBy { it.priority }
    }

    override fun isExtensionRegistered(extensionId: String): Boolean {
        return registeredExtensions.containsKey(extensionId)
    }

    override fun reloadExtensions() {
        try {
            logger.info("TabExtensionPoint", "Reloading extensions...")

            val oldExtensions = registeredExtensions.values.toList()

            // 清理所有现有扩展
            oldExtensions.forEach { extension ->
                unregisterExtension(extension.extensionId)
            }

            // 重新发现和注册扩展
            discoverAndRegisterExtensions()

            // 通知监听器
            listeners.forEach { listener ->
                try {
                    listener.onExtensionsChanged()
                } catch (e: Exception) {
                    logger.error("TabExtensionPoint", "Error notifying listener of extensions change", error = e)
                }
            }

            logger.info("TabExtensionPoint", "Extensions reloaded successfully")
        } catch (e: Exception) {
            logger.error("TabExtensionPoint", "Failed to reload extensions", error = e)
        }
    }

    override fun validateExtension(extension: TabExtension): ValidationResult {
        try {
            // 检查必需字段
            if (extension.extensionId.isBlank()) {
                return ValidationResult.Invalid("Extension ID cannot be blank")
            }

            if (extension.displayName.isBlank()) {
                return ValidationResult.Invalid("Display name cannot be blank")
            }

            if (extension.version.isBlank()) {
                return ValidationResult.Invalid("Version cannot be blank")
            }

            // 检查ID格式
            if (!isValidExtensionId(extension.extensionId)) {
                return ValidationResult.Warning("Extension ID format is not recommended")
            }

            // 尝试创建Tab实例验证
            try {
                val tab = extension.createTab()
                if (tab.getComponent() == null) {
                    return ValidationResult.Invalid("Tab component cannot be null")
                }
                // 注意：这里不调用dispose()，因为Tab可能被实际使用
            } catch (e: Exception) {
                return ValidationResult.Invalid("Failed to create tab instance: ${e.message}")
            }

            return ValidationResult.Valid

        } catch (e: Exception) {
            logger.error("TabExtensionPoint", "Error during extension validation", error = e)
            return ValidationResult.Invalid("Validation error: ${e.message}")
        }
    }

    override fun addListener(listener: TabExtensionPoint.ExtensionChangeListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: TabExtensionPoint.ExtensionChangeListener) {
        listeners.remove(listener)
    }

    /**
     * 发现并注册扩展
     */
    private fun discoverAndRegisterExtensions() {
        try {
            logger.debug("TabExtensionPoint", "Starting extension discovery and registration...")

            val discovery = ExtensionDiscovery.getInstance()
            val discoveredExtensions = discovery.discoverExtensions()

            discoveredExtensions.forEach { extension ->
                try {
                    if (registerExtension(extension)) {
                        logger.debug("TabExtensionPoint", "Auto-registered extension: ${extension.extensionId}")
                    }
                } catch (e: Exception) {
                    logger.error("TabExtensionPoint", "Failed to register discovered extension: ${extension.extensionId}", error = e)
                }
            }

            logger.debug("TabExtensionPoint", "Extension discovery completed. ${discoveredExtensions.size} extensions processed")
        } catch (e: Exception) {
            logger.error("TabExtensionPoint", "Failed during extension discovery", error = e)
        }
    }

    /**
     * 检查扩展ID格式
     */
    private fun isValidExtensionId(extensionId: String): Boolean {
        // 简单的格式检查：反向域名格式
        return extensionId.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))
    }

    /**
     * 获取当前插件版本
     * 这里应该从实际的插件元数据中获取
     */
    private fun getCurrentPluginVersion(): String {
        try {
            // 这里应该从实际的位置获取版本号
            // 例如：从插件配置、build.gradle等
            return "1.1.0" // 临时返回，应该从实际源获取
        } catch (e: Exception) {
            logger.error("TabExtensionPoint", "Failed to get plugin version", error = e)
            return "1.1.0"
        }
    }

    /**
     * 获取扩展统计信息
     */
    fun getExtensionStats(): Map<String, Any> {
        return mapOf(
            "totalExtensions" to registeredExtensions.size,
            "enabledExtensions" to getEnabledExtensions().size,
            "registeredIds" to registeredExtensions.keys.toList()
        )
    }

    /**
     * 清理所有扩展
     */
    fun dispose() {
        try {
            logger.info("TabExtensionPoint", "Disposing all extensions...")

            registeredExtensions.values.forEach { extension ->
                try {
                    extension.dispose()
                } catch (e: Exception) {
                    logger.error("TabExtensionPoint", "Error disposing extension: ${extension.extensionId}", error = e)
                }
            }

            registeredExtensions.clear()
            listeners.clear()

            logger.info("TabExtensionPoint", "All extensions disposed")
        } catch (e: Exception) {
            logger.error("TabExtensionPoint", "Error during disposal", error = e)
        }
    }
}

/**
 * 扩展点单例
 */
object TabExtensionPointSingleton {

    private val instance = TabExtensionPointImpl()

    /**
     * 获取扩展点实例
     */
    fun getInstance(): TabExtensionPointImpl = instance
}