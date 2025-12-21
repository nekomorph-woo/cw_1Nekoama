package com.cw2.nekoama.interfaces.intellij.tool_window.extension

import com.cw2.nekoama.interfaces.intellij.tool_window.tab.NekoamaTab
import javax.swing.Icon

/**
 * Tab扩展接口
 *
 * 允许第三方插件或模块动态注册自定义Tab到Nekoama工具窗口中。
 * 提供了完整的Tab生命周期管理和配置能力。
 */
interface TabExtension {

    /**
     * 扩展的唯一标识符
     * 建议使用反向域名格式，如：com.example.myplugin.MyTab
     */
    val extensionId: String

    /**
     * 扩展的显示名称
     * 用户在Tab标签上看到的名称
     */
    val displayName: String

    /**
     * 扩展的描述信息
     * 可用于工具提示或帮助文档
     */
    val description: String

    /**
     * 扩展的版本号
     * 用于版本控制和兼容性检查
     */
    val version: String

    /**
     * 扩展的图标（可选）
     * 显示在Tab标签上的图标
     */
    val icon: Icon? get() = null

    /**
     * 扩展的优先级
     * 数值越小优先级越高，影响Tab的排序顺序
     */
    val priority: Int get() = 100

    /**
     * 扩展是否启用
     * 可以根据配置或条件动态控制
     */
    val isEnabled: Boolean get() = true

    /**
     * 创建Tab实例
     * 每次需要显示Tab时调用此方法创建新的实例
     *
     * @return 新的Tab实例
     */
    fun createTab(): NekoamaTab

    /**
     * 获取扩展的配置信息
     * 返回扩展的配置参数，用于自定义行为
     *
     * @return 配置信息Map
     */
    fun getConfiguration(): Map<String, Any> = emptyMap()

    /**
     * 扩展初始化
     * 在扩展首次加载时调用，用于执行初始化逻辑
     */
    fun initialize() {}

    /**
     * 扩展销毁
     * 在扩展卸载时调用，用于清理资源
     */
    fun dispose() {}

    /**
     * 检查扩展兼容性
     * 用于检查扩展与当前插件版本的兼容性
     *
     * @param pluginVersion 插件版本
     * @return 是否兼容
     */
    fun isCompatible(pluginVersion: String): Boolean = true
}

/**
 * 扩展的Tab工厂接口
 *
 * 提供更灵活的Tab创建方式，支持延迟加载和条件创建。
 */
interface TabFactory {

    /**
     * 检查是否应该创建Tab
     * 可以根据环境条件决定是否创建Tab
     *
     * @return 是否应该创建Tab
     */
    fun shouldCreateTab(): Boolean = true

    /**
     * 创建Tab实例
     *
     * @return 新的Tab实例，如果不需要创建则返回null
     */
    fun createTab(): NekoamaTab?

    /**
     * 获取工厂信息
     *
     * @return 工厂信息描述
     */
    fun getFactoryInfo(): String = "Default Tab Factory"
}

/**
 * 抽象Tab扩展基类
 *
 * 提供默认实现，简化扩展开发。
 */
abstract class AbstractTabExtension : TabExtension {

    override val priority: Int = 100
    override val icon: Icon? = null
    override val isEnabled: Boolean = true

    override fun initialize() {
        // 默认空实现
    }

    override fun dispose() {
        // 默认空实现
    }
}

/**
 * 简单Tab扩展实现
 *
 * 适用于快速创建简单Tab的场景。
 */
class SimpleTabExtension(
    override val extensionId: String,
    override val displayName: String,
    override val description: String,
    override val version: String = "1.1.0",
    private val tabFactory: () -> NekoamaTab,
    override val priority: Int = 100
) : AbstractTabExtension() {

    override fun createTab(): NekoamaTab = tabFactory()
}

/**
 * 扩展点接口
 *
 * 定义扩展系统的核心接口，用于管理Tab扩展的注册、发现和生命周期。
 */
interface TabExtensionPoint {

    /**
     * 注册Tab扩展
     *
     * @param extension 要注册的扩展
     * @return 注册是否成功
     */
    fun registerExtension(extension: TabExtension): Boolean

    /**
     * 注销Tab扩展
     *
     * @param extensionId 扩展ID
     * @return 注销是否成功
     */
    fun unregisterExtension(extensionId: String): Boolean

    /**
     * 获取所有已注册的扩展
     *
     * @return 扩展列表
     */
    fun getRegisteredExtensions(): List<TabExtension>

    /**
     * 根据ID获取扩展
     *
     * @param extensionId 扩展ID
     * @return 扩展实例，如果不存在则返回null
     */
    fun getExtension(extensionId: String): TabExtension?

    /**
     * 获取启用的扩展
     *
     * @return 启用的扩展列表，按优先级排序
     */
    fun getEnabledExtensions(): List<TabExtension>

    /**
     * 检查扩展是否已注册
     *
     * @param extensionId 扩展ID
     * @return 是否已注册
     */
    fun isExtensionRegistered(extensionId: String): Boolean

    /**
     * 重新加载所有扩展
     * 用于配置变更后重新加载扩展
     */
    fun reloadExtensions()

    /**
     * 验证扩展
     * 验证扩展的完整性和有效性
     *
     * @param extension 要验证的扩展
     * @return 验证结果
     */
    fun validateExtension(extension: TabExtension): ValidationResult

    /**
     * 添加监听器
     *
     * @param listener 监听器
     */
    fun addListener(listener: ExtensionChangeListener)

    /**
     * 移除监听器
     *
     * @param listener 监听器
     */
    fun removeListener(listener: ExtensionChangeListener)

    /**
     * 扩展变更监听器
     */
    interface ExtensionChangeListener {
        fun onExtensionRegistered(extension: TabExtension)
        fun onExtensionUnregistered(extensionId: String)
        fun onExtensionsChanged()
    }
}

/**
 * 验证结果
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
}