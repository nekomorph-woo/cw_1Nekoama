package com.cw2.nekoama.presentation.toolwindow.tab

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.MetricsUpdateListener
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.presentation.toolwindow.extension.*
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import java.awt.Component
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

/**
 * Nekoama Tab管理器
 *
 * 负责Tab的注册、生命周期管理、状态保持和切换逻辑。
 * 提供线程安全的Tab操作接口。
 */
class NekoamaTabManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val registeredTabs = ConcurrentHashMap<String, NekoamaTab>()
    private val tabStates = ConcurrentHashMap<String, Map<String, Any>>()
    private val tabbedPane = JBTabbedPane()
    private val extensionTabs = ConcurrentHashMap<String, TabExtensionAdapter>()

    private var activeTab: NekoamaTab? = null
    private val extensionPoint = TabExtensionPointSingleton.getInstance()
    private val configManager = TabExtensionConfigManager.getInstance()

    // 性能优化：缓存Tab查找结果
    private val tabIndexCache = ConcurrentHashMap<String, Int>()
    private val lastRefreshTime = ConcurrentHashMap<String, Long>()

    // 性能监控
    private var operationCount = 0L

    companion object {
        @Volatile
        private var INSTANCE: NekoamaTabManager? = null

        fun getInstance(): NekoamaTabManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NekoamaTabManager().also { INSTANCE = it }
            }
        }
    }

    init {
        setupTabChangeListener()
        initializeExtensions()
    }

    /**
     * 注册Tab到管理器
     */
    fun registerTab(tab: NekoamaTab) {
        val tabId = tab.tabId

        if (registeredTabs.containsKey(tabId)) {
            NekoamaLogger.warn("NekoamaTabManager", "Tab with id '$tabId' is already registered, skipping...")
            return
        }

        registeredTabs[tabId] = tab

        // 恢复之前保存的状态
        val savedState = tabStates[tabId]
        if (savedState != null) {
            try {
                tab.restoreTabState(savedState)
                NekoamaLogger.debug("NekoamaTabManager", "Restored state for tab '$tabId'")
            } catch (e: Exception) {
                NekoamaLogger.error("NekoamaTabManager", "Failed to restore state for tab '$tabId'", error = e)
            }
        }

        // 如果Tab实现了MetricsUpdateListener，注册到指标采集器
        if (tab is MetricsUpdateListener) {
            EnhancedMetricsCollector.addListener(tab)
        }

        SwingUtilities.invokeLater {
            val tabIndex = tabbedPane.tabCount
            tabbedPane.addTab(tab.displayName, tab.icon, tab.getComponent(), tab.tooltip)

            // 设置Tab是否可关闭
            if (!tab.isCloseable) {
                tabbedPane.setEnabledAt(tabIndex, true)
                // 注意：JBTabbedPane默认不显示关闭按钮，这里暂时不需要额外处理
            }

            tabbedPane.setEnabledAt(tabIndex, tab.isEnabled)
        }

        NekoamaLogger.info("NekoamaTabManager", "Tab '$tabId' registered successfully")
    }

    /**
     * 从管理器注销Tab
     */
    fun unregisterTab(tabId: String) {
        val tab = registeredTabs.remove(tabId) ?: return

        // 保存Tab状态
        try {
            tabStates[tabId] = tab.getTabState()
            NekoamaLogger.debug("NekoamaTabManager", "Saved state for tab '$tabId'")
        } catch (e: Exception) {
            NekoamaLogger.error("NekoamaTabManager", "Failed to save state for tab '$tabId'", error = e)
        }

        // 从指标采集器移除监听器
        if (tab is MetricsUpdateListener) {
            EnhancedMetricsCollector.removeListener(tab)
        }

        SwingUtilities.invokeLater {
            val tabIndex = findTabIndex(tabId)
            if (tabIndex >= 0) {
                tabbedPane.removeTabAt(tabIndex)
            }
        }

        // 释放Tab资源
        try {
            tab.dispose()
        } catch (e: Exception) {
            NekoamaLogger.error("NekoamaTabManager", "Failed to dispose tab '$tabId'", error = e)
        }

        NekoamaLogger.info("NekoamaTabManager", "Tab '$tabId' unregistered successfully")
    }

    /**
     * 获取Tab组件
     */
    fun getTabbedPane(): JBTabbedPane = tabbedPane

    /**
     * 获取当前活跃的Tab
     */
    fun getActiveTab(): NekoamaTab? = activeTab

    /**
     * 根据ID获取Tab
     */
    fun getTab(tabId: String): NekoamaTab? = registeredTabs[tabId]

    /**
     * 获取所有已注册的Tab
     */
    fun getAllTabs(): List<NekoamaTab> = registeredTabs.values.toList()

    /**
     * 切换到指定Tab
     */
    fun switchToTab(tabId: String) {
        val tab = registeredTabs[tabId] ?: return

        SwingUtilities.invokeLater {
            val tabIndex = findTabIndex(tabId)
            if (tabIndex >= 0) {
                tabbedPane.selectedIndex = tabIndex
            }
        }
    }

    /**
     * 刷新所有Tab
     */
    fun refreshAllTabs() {
        scope.launch {
            registeredTabs.values.forEach { tab ->
                try {
                    tab.refresh()
                } catch (e: Exception) {
                    NekoamaLogger.error("NekoamaTabManager", "Failed to refresh tab '${tab.tabId}'", error = e)
                }
            }
        }
    }

    /**
     * 刷新指定Tab（带防抖优化）
     */
    fun refreshTab(tabId: String) {
        val tab = registeredTabs[tabId] ?: return

        // 防抖机制：避免短时间内重复刷新
        val currentTime = System.currentTimeMillis()
        val lastRefresh = lastRefreshTime[tabId] ?: 0L
        if (currentTime - lastRefresh < 1000) { // 1秒防抖
            NekoamaLogger.debug("NekoamaTabManager", "Skipping refresh for tab '$tabId' (rate limited)")
            return
        }

        lastRefreshTime[tabId] = currentTime
        operationCount++

        scope.launch {
            try {
                NekoamaLogger.debug("NekoamaTabManager", "Refreshing tab '$tabId' (operation #$operationCount)")
                tab.refresh()
            } catch (e: Exception) {
                NekoamaLogger.error("NekoamaTabManager", "Failed to refresh tab '$tabId'", error = e)
            }
        }
    }

    /**
     * 释放所有Tab资源
     */
    fun dispose() {
        // 性能监控：记录统计信息
        NekoamaLogger.info("NekoamaTabManager", "Performance stats: operations=$operationCount, tabs=${registeredTabs.size}, cachedIndices=${tabIndexCache.size}")

        kotlinx.coroutines.runBlocking<kotlin.Unit> {
            // 保存所有Tab状态
            registeredTabs.values.forEach { tab ->
                try {
                    tabStates[tab.tabId] = tab.getTabState()
                } catch (e: Exception) {
                    NekoamaLogger.error("NekoamaTabManager", "Failed to save state for tab '${tab.tabId}'", error = e)
                }
            }

            // 释放所有Tab资源
            registeredTabs.values.forEach { tab ->
                try {
                    tab.dispose()
                } catch (e: Exception) {
                    NekoamaLogger.error("NekoamaTabManager", "Failed to dispose tab '${tab.tabId}'", error = e)
                }
            }

            // 清理性能缓存
            registeredTabs.clear()
            tabIndexCache.clear()
            lastRefreshTime.clear()
            tabStates.clear()
            extensionTabs.clear()

            scope.cancel()
        }

        NekoamaLogger.info("NekoamaTabManager", "TabManager disposed")
    }

    /**
     * 设置Tab切换监听器
     */
    private fun setupTabChangeListener() {
        tabbedPane.addChangeListener { e ->
            val selectedIndex = tabbedPane.selectedIndex
            if (selectedIndex >= 0) {
                val newActiveTab = findTabByIndex(selectedIndex)

                // 处理Tab停用
                activeTab?.let { oldTab ->
                    if (oldTab != newActiveTab) {
                        try {
                            oldTab.onTabDeactivated()
                        } catch (ex: Exception) {
                            NekoamaLogger.error("NekoamaTabManager", "Error deactivating tab '${oldTab.tabId}'", error = ex)
                        }
                    }
                }

                // 处理Tab激活
                newActiveTab?.let { newTab ->
                    try {
                        newTab.onTabActivated()
                        activeTab = newTab
                        NekoamaLogger.debug("NekoamaTabManager", "Tab '${newTab.tabId}' activated")
                    } catch (ex: Exception) {
                        NekoamaLogger.error("NekoamaTabManager", "Error activating tab '${newTab.tabId}'", error = ex)
                    }
                }
            }
        }
    }

    /**
     * 根据Tab ID查找在TabbedPane中的索引（带缓存优化）
     */
    private fun findTabIndex(tabId: String): Int {
        // 尝试从缓存获取
        tabIndexCache[tabId]?.let { return it }

        val tab = registeredTabs[tabId] ?: return -1
        val index = findTabIndex(tab)

        // 缓存结果
        if (index != -1) {
            tabIndexCache[tabId] = index
        }

        return index
    }

    /**
     * 根据Tab对象查找在TabbedPane中的索引
     */
    private fun findTabIndex(tab: NekoamaTab): Int {
        val component = tab.getComponent()
        for (i in 0 until tabbedPane.tabCount) {
            if (tabbedPane.getComponentAt(i) === component) {
                return i
            }
        }
        return -1
    }

    /**
     * 根据索引查找Tab对象
     */
    private fun findTabByIndex(index: Int): NekoamaTab? {
        if (index < 0 || index >= tabbedPane.tabCount) {
            return null
        }

        val component = tabbedPane.getComponentAt(index)
        return registeredTabs.values.find { it.getComponent() === component }
    }

    /**
     * 初始化扩展系统
     */
    private fun initializeExtensions() {
        try {
            NekoamaLogger.info("NekoamaTabManager", "Initializing extension system...")

            // 注册扩展变更监听器
            extensionPoint.addListener(object : TabExtensionPoint.ExtensionChangeListener {
                override fun onExtensionRegistered(extension: TabExtension) {
                    onExtensionRegistered(extension)
                }

                override fun onExtensionUnregistered(extensionId: String) {
                    onExtensionUnregistered(extensionId)
                }

                override fun onExtensionsChanged() {
                    reloadExtensions()
                }
            })

            // 加载并注册启用的扩展
            loadEnabledExtensions()

            NekoamaLogger.info("NekoamaTabManager", "Extension system initialized successfully")
        } catch (e: Exception) {
            NekoamaLogger.error("NekoamaTabManager", "Failed to initialize extension system", error = e)
        }
    }

    /**
     * 加载启用的扩展
     */
    private fun loadEnabledExtensions() {
        try {
            val enabledExtensions = extensionPoint.getEnabledExtensions()
            NekoamaLogger.info("NekoamaTabManager", "Loading ${enabledExtensions.size} enabled extensions")

            enabledExtensions.forEach { extension ->
                registerExtensionTab(extension)
            }
        } catch (e: Exception) {
            NekoamaLogger.error("NekoamaTabManager", "Failed to load enabled extensions", error = e)
        }
    }

    /**
     * 注册扩展Tab
     */
    private fun registerExtensionTab(extension: TabExtension) {
        try {
            val tabAdapter = TabExtensionAdapter(extension)
            extensionTabs[extension.extensionId] = tabAdapter

            // 注册到TabManager
            registerTab(tabAdapter)

            NekoamaLogger.info("NekoamaTabManager", "Extension tab registered: ${extension.extensionId}")
        } catch (e: Exception) {
            NekoamaLogger.error("NekoamaTabManager", "Failed to register extension tab: ${extension.extensionId}", error = e)
        }
    }

    /**
     * 注销扩展Tab
     */
    private fun unregisterExtensionTab(extensionId: String) {
        try {
            val tabAdapter = extensionTabs.remove(extensionId) ?: return
            unregisterTab(tabAdapter.tabId)

            NekoamaLogger.info("NekoamaTabManager", "Extension tab unregistered: $extensionId")
        } catch (e: Exception) {
            NekoamaLogger.error("NekoamaTabManager", "Failed to unregister extension tab: $extensionId", error = e)
        }
    }

    /**
     * 扩展注册事件处理
     */
    private fun onExtensionRegistered(extension: TabExtension) {
        if (extension.isEnabled) {
            registerExtensionTab(extension)
        }
    }

    /**
     * 扩展注销事件处理
     */
    private fun onExtensionUnregistered(extensionId: String) {
        unregisterExtensionTab(extensionId)
    }

    /**
     * 重新加载扩展
     */
    private fun reloadExtensions() {
        scope.launch {
            try {
                // 清理现有扩展Tab
                extensionTabs.values.forEach { adapter ->
                    unregisterTab(adapter.tabId)
                }
                extensionTabs.clear()

                // 重新加载扩展
                loadEnabledExtensions()

                NekoamaLogger.info("NekoamaTabManager", "Extensions reloaded successfully")
            } catch (e: Exception) {
                NekoamaLogger.error("NekoamaTabManager", "Failed to reload extensions", error = e)
            }
        }
    }

    /**
     * 手动注册扩展
     */
    fun registerExtension(extension: TabExtension): Boolean {
        return try {
            if (extensionPoint.registerExtension(extension)) {
                NekoamaLogger.info("NekoamaTabManager", "Extension registered manually: ${extension.extensionId}")
                true
            } else {
                NekoamaLogger.warn("NekoamaTabManager", "Failed to register extension: ${extension.extensionId}")
                false
            }
        } catch (e: Exception) {
            NekoamaLogger.error("NekoamaTabManager", "Error registering extension: ${extension.extensionId}", error = e)
            false
        }
    }

    /**
     * 手动注销扩展
     */
    fun unregisterExtension(extensionId: String): Boolean {
        return try {
            if (extensionPoint.unregisterExtension(extensionId)) {
                NekoamaLogger.info("NekoamaTabManager", "Extension unregistered manually: $extensionId")
                true
            } else {
                NekoamaLogger.warn("NekoamaTabManager", "Failed to unregister extension: $extensionId")
                false
            }
        } catch (e: Exception) {
            NekoamaLogger.error("NekoamaTabManager", "Error unregistering extension: $extensionId", error = e)
            false
        }
    }

    /**
     * 获取扩展统计信息
     */
    fun getExtensionStats(): Map<String, Any> {
        return mapOf(
            "totalExtensions" to extensionTabs.size,
            "registeredExtensions" to extensionPoint.getRegisteredExtensions().size,
            "enabledExtensions" to extensionPoint.getEnabledExtensions().size,
            "extensionIds" to extensionTabs.keys.toList()
        )
    }

    /**
     * 获取扩展配置管理器
     */
    fun getExtensionConfigManager(): TabExtensionConfigManager = configManager
}