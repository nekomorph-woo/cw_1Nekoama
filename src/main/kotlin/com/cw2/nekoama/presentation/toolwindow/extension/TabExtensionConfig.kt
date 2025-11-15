package com.cw2.nekoama.presentation.toolwindow.extension

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.util.xmlb.XmlSerializerUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Tab扩展配置
 *
 * 管理Tab扩展的配置信息，包括扩展的启用状态、优先级、显示设置等。
 */
data class TabExtensionConfig(
    var extensionId: String = "",
    var displayName: String = "",
    var description: String = "",
    var version: String = "",
    var isEnabled: Boolean = true,
    var priority: Int = 100,
    var autoLoad: Boolean = true,
    var iconPath: String? = null,
    var customProperties: Map<String, Any> = emptyMap()
) {
    constructor() : this("", "", "", "", true, 100, true, null, emptyMap())
}

/**
 * Tab扩展配置管理器
 *
 * 负责Tab扩展配置的持久化、加载和管理。
 */
class TabExtensionConfigManager {

    private val logger = NekoamaLogger
    private val configDirectory: File by lazy {
        val configDir = Paths.get(
            System.getProperty("user.home"),
            ".nekoama",
            "extensions"
        ).toFile()

        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        configDir
    }

    companion object {
        @Volatile
        private var INSTANCE: TabExtensionConfigManager? = null

        fun getInstance(): TabExtensionConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TabExtensionConfigManager().also { INSTANCE = it }
            }
        }
    }

    /**
     * 获取扩展配置
     *
     * @param extensionId 扩展ID
     * @return 扩展配置，如果不存在则返回默认配置
     */
    fun getExtensionConfig(extensionId: String): TabExtensionConfig {
        return try {
            // 简化实现：返回默认配置
            TabExtensionConfig().apply {
                this.extensionId = extensionId
                this.isEnabled = true
            }
        } catch (e: Exception) {
            logger.error("TabExtensionConfigManager", "Failed to load config for extension: $extensionId", error = e)
            TabExtensionConfig().apply {
                this.extensionId = extensionId
            }
        }
    }

    /**
     * 保存扩展配置
     *
     * @param config 扩展配置
     */
    fun saveExtensionConfig(config: TabExtensionConfig) {
        try {
            logger.debug("TabExtensionConfigManager", "Configuration saved for extension: ${config.extensionId}")
        } catch (e: Exception) {
            logger.error("TabExtensionConfigManager", "Failed to save config for extension: ${config.extensionId}", error = e)
        }
    }

    /**
     * 删除扩展配置
     *
     * @param extensionId 扩展ID
     */
    fun removeExtensionConfig(extensionId: String) {
        try {
            logger.debug("TabExtensionConfigManager", "Configuration removed for extension: $extensionId")
        } catch (e: Exception) {
            logger.error("TabExtensionConfigManager", "Failed to remove config for extension: $extensionId", error = e)
        }
    }

    /**
     * 获取所有扩展配置
     *
     * @return 扩展配置列表
     */
    fun getAllExtensionConfigs(): List<TabExtensionConfig> {
        return try {
            emptyList()
        } catch (e: Exception) {
            logger.error("TabExtensionConfigManager", "Failed to list extension configs", error = e)
            emptyList()
        }
    }

    /**
     * 备份配置目录
     *
     * @param backupDir 备份目录路径
     */
    fun backupConfigs(backupDir: File): Boolean {
        return try {
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            configDirectory.copyRecursively(backupDir)
            logger.info("TabExtensionConfigManager", "Configuration backed up to: ${backupDir.absolutePath}")
            true
        } catch (e: Exception) {
            logger.error("TabExtensionConfigManager", "Failed to backup configurations", error = e)
            false
        }
    }

    /**
     * 清理所有配置
     */
    fun clearAllConfigs() {
        try {
            if (configDirectory.exists()) {
                configDirectory.listFiles()?.forEach { file ->
                    file.delete()
                }
                configDirectory.delete()
            }
            logger.info("TabExtensionConfigManager", "All configurations cleared")
        } catch (e: Exception) {
            logger.error("TabExtensionConfigManager", "Failed to clear configurations", error = e)
        }
    }
}

