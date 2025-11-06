package com.cw2.nekoama.presentation.toolwindow.tab

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.MetricsUpdateListener
import com.cw2.nekoama.core.logging.NekoamaLogger
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

    private var activeTab: NekoamaTab? = null

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
     * 刷新指定Tab
     */
    fun refreshTab(tabId: String) {
        val tab = registeredTabs[tabId] ?: return

        scope.launch {
            try {
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

            registeredTabs.clear()
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
     * 根据Tab ID查找在TabbedPane中的索引
     */
    private fun findTabIndex(tabId: String): Int {
        val tab = registeredTabs[tabId] ?: return -1
        return findTabIndex(tab)
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
}