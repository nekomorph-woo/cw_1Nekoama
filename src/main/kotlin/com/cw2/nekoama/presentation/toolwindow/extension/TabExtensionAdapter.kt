package com.cw2.nekoama.presentation.toolwindow.extension

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.presentation.toolwindow.tab.NekoamaTab
import java.awt.Component
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

/**
 * Tab扩展适配器
 *
 * 将TabExtension适配到NekoamaTab接口，使扩展可以无缝集成到Tab系统中。
 */
class TabExtensionAdapter(private val extension: TabExtension) : NekoamaTab {

    private var currentTab: NekoamaTab? = null
    private var isInitialized = false
    private var isDisposed = false

    companion object {
        private val adapterCounter = ConcurrentHashMap<String, Int>()
        private fun getNextAdapterId(extensionId: String): String {
            val count = adapterCounter.compute(extensionId) { _, v -> (v ?: 0) + 1 } ?: 1
            return "${extensionId}_adapter_$count"
        }
    }

    init {
        initializeExtension()
    }

    /**
     * 初始化扩展
     */
    private fun initializeExtension() {
        try {
            if (!isInitialized) {
                extension.initialize()
                isInitialized = true
                NekoamaLogger.debug("TabExtensionAdapter", "Extension initialized: ${extension.extensionId}")
            }
        } catch (e: Exception) {
            NekoamaLogger.error("TabExtensionAdapter", "Failed to initialize extension: ${extension.extensionId}", error = e)
        }
    }

    /**
     * 获取或创建Tab实例
     */
    private fun getOrCreateTab(): NekoamaTab {
        if (currentTab == null || isDisposed) {
            try {
                currentTab = extension.createTab()
                NekoamaLogger.debug("TabExtensionAdapter", "Tab created for extension: ${extension.extensionId}")
            } catch (e: Exception) {
                NekoamaLogger.error("TabExtensionAdapter", "Failed to create tab for extension: ${extension.extensionId}", error = e)
                throw e
            }
        }
        return currentTab!!
    }

    override val tabId: String
        get() = "ext_${extension.extensionId}"

    override val displayName: String
        get() = extension.displayName

    override val icon: javax.swing.Icon?
        get() = extension.icon

    override val tooltip: String?
        get() = extension.description.takeIf { it.isNotEmpty() }

    override val isCloseable: Boolean
        get() = false // 扩展Tab默认不可关闭

    override val isEnabled: Boolean
        get() = extension.isEnabled

    override fun getComponent(): Component {
        return getOrCreateTab().getComponent()
    }

    override fun onTabActivated() {
        try {
            getOrCreateTab().onTabActivated()
            NekoamaLogger.debug("TabExtensionAdapter", "Tab activated: ${extension.extensionId}")
        } catch (e: Exception) {
            NekoamaLogger.error("TabExtensionAdapter", "Error activating tab: ${extension.extensionId}", error = e)
        }
    }

    override fun onTabDeactivated() {
        try {
            currentTab?.onTabDeactivated()
            NekoamaLogger.debug("TabExtensionAdapter", "Tab deactivated: ${extension.extensionId}")
        } catch (e: Exception) {
            NekoamaLogger.error("TabExtensionAdapter", "Error deactivating tab: ${extension.extensionId}", error = e)
        }
    }

    override fun refresh() {
        try {
            currentTab?.refresh()
            NekoamaLogger.debug("TabExtensionAdapter", "Tab refreshed: ${extension.extensionId}")
        } catch (e: Exception) {
            NekoamaLogger.error("TabExtensionAdapter", "Error refreshing tab: ${extension.extensionId}", error = e)
        }
    }

    override fun getTabState(): Map<String, Any> {
        return try {
            val baseState = mutableMapOf<String, Any>(
                "extensionId" to extension.extensionId,
                "extensionVersion" to extension.version,
                "extensionEnabled" to extension.isEnabled,
                "adapterInitialized" to isInitialized,
                "adapterDisposed" to isDisposed
            )

            val tabState = currentTab?.getTabState() ?: emptyMap<String, Any>()
            val extensionState = extension.getConfiguration()

            tabState.forEach { (key, value) -> baseState[key] = value }
            extensionState.forEach { (key, value) -> baseState[key] = value }
            baseState
        } catch (e: Exception) {
            NekoamaLogger.error("TabExtensionAdapter", "Error getting tab state: ${extension.extensionId}", error = e)
            mapOf(
                "extensionId" to extension.extensionId,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }

    override fun restoreTabState(state: Map<String, Any>) {
        try {
            // 恢复扩展配置
            val extensionState = state.filterKeys { it.startsWith("ext_") || it in extension.getConfiguration().keys }
            if (extensionState.isNotEmpty()) {
                // 这里可以扩展配置恢复逻辑
                NekoamaLogger.debug("TabExtensionAdapter", "Extension state restored: ${extension.extensionId}")
            }

            // 恢复Tab状态
            currentTab?.restoreTabState(state)

            NekoamaLogger.debug("TabExtensionAdapter", "Tab state restored: ${extension.extensionId}")
        } catch (e: Exception) {
            NekoamaLogger.error("TabExtensionAdapter", "Error restoring tab state: ${extension.extensionId}", error = e)
        }
    }

    override fun dispose() {
        try {
            if (!isDisposed) {
                // 释放当前Tab
                currentTab?.dispose()
                currentTab = null

                // 释放扩展
                if (isInitialized) {
                    extension.dispose()
                }

                isDisposed = true
                isInitialized = false

                NekoamaLogger.debug("TabExtensionAdapter", "Adapter disposed: ${extension.extensionId}")
            }
        } catch (e: Exception) {
            NekoamaLogger.error("TabExtensionAdapter", "Error disposing adapter: ${extension.extensionId}", error = e)
        }
    }

    /**
     * 获取扩展实例
     */
    fun getExtension(): TabExtension = extension

    /**
     * 检查适配器是否已释放
     */
    fun isDisposed(): Boolean = isDisposed

    /**
     * 检查适配器是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * 强制重新创建Tab实例
     */
    fun recreateTab() {
        try {
            currentTab?.dispose()
            currentTab = null
            isDisposed = false

            NekoamaLogger.debug("TabExtensionAdapter", "Tab recreated: ${extension.extensionId}")
        } catch (e: Exception) {
            NekoamaLogger.error("TabExtensionAdapter", "Error recreating tab: ${extension.extensionId}", error = e)
        }
    }

    override fun toString(): String {
        return "TabExtensionAdapter(extensionId=${extension.extensionId}, displayName=${extension.displayName}, version=${extension.version})"
    }
}