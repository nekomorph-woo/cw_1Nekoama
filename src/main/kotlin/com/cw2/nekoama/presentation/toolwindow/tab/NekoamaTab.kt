package com.cw2.nekoama.presentation.toolwindow.tab

import com.cw2.nekoama.core.metrics.MetricsUpdateListener
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import java.awt.Component
import javax.swing.Icon

/**
 * Nekoama工具窗口Tab接口
 *
 * 所有工具窗口的Tab都必须实现此接口，确保统一的Tab行为和状态管理。
 */
interface NekoamaTab {

    /**
     * Tab的唯一标识符
     */
    val tabId: String

    /**
     * Tab的显示名称（支持国际化）
     */
    val displayName: String
        get() = NekoamaBundle.message("tab.${tabId}.title")

    /**
     * Tab的图标（可选）
     */
    val icon: Icon?
        get() = null

    /**
     * Tab的工具提示文本（可选）
     */
    val tooltip: String?
        get() = null

    /**
     * Tab是否可关闭
     */
    val isCloseable: Boolean
        get() = false

    /**
     * Tab是否启用
     */
    val isEnabled: Boolean
        get() = true

    /**
     * 获取Tab的主组件
     */
    fun getComponent(): Component

    /**
     * Tab被激活时调用
     */
    fun onTabActivated() {}

    /**
     * Tab被停用时调用
     */
    fun onTabDeactivated() {}

    /**
     * 刷新Tab内容
     */
    fun refresh() {}

    /**
     * 释放Tab资源
     */
    fun dispose() {}

    /**
     * 获取Tab的当前状态，用于状态保持
     */
    fun getTabState(): Map<String, Any> = emptyMap()

    /**
     * 恢复Tab状态
     */
    fun restoreTabState(state: Map<String, Any>) {}
}

/**
 * 基础Tab实现，提供通用的默认行为
 */
abstract class BaseNekoamaTab : NekoamaTab, MetricsUpdateListener {

    protected var isActive = false
        private set

    final override fun onTabActivated() {
        isActive = true
        onActivated()
    }

    final override fun onTabDeactivated() {
        isActive = false
        onDeactivated()
    }

    /**
     * 子类可重写此方法来实现激活时的具体逻辑
     */
    protected open fun onActivated() {}

    /**
     * 子类可重写此方法来实现停用时的具体逻辑
     */
    protected open fun onDeactivated() {}

    override fun onMetricsUpdated(record: com.cw2.nekoama.core.metrics.ActionRecord) {
        // 默认实现：当Tab处于活跃状态时自动刷新
        if (isActive) {
            refresh()
        }
    }
}

/**
 * 简单的静态内容Tab实现
 *
 * 适用于内容相对固定、不需要复杂状态管理的Tab
 */
class StaticNekoamaTab(
    override val tabId: String,
    override val displayName: String,
    private val component: Component,
    override val icon: Icon? = null,
    override val tooltip: String? = null
) : NekoamaTab {

    override fun getComponent(): Component = component

    override fun toString(): String = "StaticTab($tabId, $displayName)"
}