package com.cw2.nekoama.interfaces.intellij.toolwindow

import com.cw2.nekoama.domain.toolwindow.model.TabEvent
import com.cw2.nekoama.domain.toolwindow.model.TabLifecycle
import com.cw2.nekoama.domain.toolwindow.model.TabMetadata
import com.cw2.nekoama.domain.toolwindow.model.TabState
import com.cw2.nekoama.domain.toolwindow.service.SubscriptionHandle
import com.cw2.nekoama.domain.toolwindow.service.TabCoordinatorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import kotlin.reflect.KClass

/**
 * Tab抽象基类
 *
 * 职责：
 * - 定义Tab生命周期方法
 * - 提供事件发布/订阅的便捷方法
 * - 提供状态保存/加载的便捷方法
 *
 * 说明：
 * - 所有Tab实现都必须继承此类
 * - 框架保证生命周期方法的调用顺序
 * - 使用模板方法模式
 */
abstract class BaseTab(
    protected val project: Project,
    protected val coordinatorService: TabCoordinatorService
) : Disposable {

    /**
     * Tab元数据（子类必须提供）
     */
    abstract val metadata: TabMetadata

    /**
     * Tab状态类型（子类提供，用于序列化）
     */
    protected abstract val stateType: KClass<out TabState>

    private var _lifecycle = TabLifecycle.CREATED
    val lifecycle: TabLifecycle get() = _lifecycle

    private var _component: javax.swing.JComponent? = null

    /**
     * 创建UI组件（子类实现）
     *
     * 说明：此方法仅在首次调用时执行，结果会被缓存
     */
    protected abstract fun createComponentImpl(): javax.swing.JComponent

    /**
     * Tab激活回调（子类可选实现）
     *
     * 说明：
     * - 用户切换到此Tab时调用
     * - 可以在这里加载状态、刷新UI
     * - 在EDT线程执行
     */
    protected open fun onActivated() {
        // 默认空实现
    }

    /**
     * Tab失活回调（子类可选实现）
     *
     * 说明：
     * - 用户切换到其他Tab时调用
     * - 可以在这里保存状态、暂停操作
     * - 在EDT线程执行
     */
    protected open fun onDeactivated() {
        // 默认空实现
    }

    /**
     * Tab销毁回调（子类可选实现）
     *
     * 说明：
     * - Tool Window关闭时调用
     * - 应该在这里释放资源、取消订阅
     */
    protected open fun onDestroy() {
        // 默认空实现
    }

    /**
     * 创建组件（公开方法，带缓存）
     */
    fun createComponent(): javax.swing.JComponent {
        return _component ?: run {
            _lifecycle = TabLifecycle.INITIALIZING
            val component = createComponentImpl()
            _lifecycle = TabLifecycle.READY
            _component = component
            component
        }
    }

    /**
     * 内部方法：激活Tab（由框架调用）
     */
    internal fun activate() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (_lifecycle != TabLifecycle.DESTROYED) {
            _lifecycle = TabLifecycle.ACTIVE
            onActivated()
        }
    }

    /**
     * 内部方法：失活Tab（由框架调用）
     */
    internal fun deactivate() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (_lifecycle == TabLifecycle.ACTIVE) {
            _lifecycle = TabLifecycle.INACTIVE
            onDeactivated()
        }
    }

    /**
     * 内部方法：销毁Tab（由框架调用）
     */
    internal fun destroy() {
        onDestroy()
        _lifecycle = TabLifecycle.DESTROYED
        _component = null
    }

    /**
     * 便捷方法：发布事件
     */
    protected fun publishEvent(event: TabEvent) {
        coordinatorService.eventBus.publish(event)
    }

    /**
     * 便捷方法：订阅事件
     */
    protected fun <T : TabEvent> subscribeEvent(
        eventType: KClass<T>,
        handler: (T) -> Unit
    ): SubscriptionHandle {
        return coordinatorService.eventBus.subscribe(
            eventType,
            metadata.id,
            handler
        )
    }

    /**
     * 便捷方法：保存状态
     */
    protected fun saveState(state: TabState) {
        coordinatorService.saveTabState(metadata.id.value, state)
    }

    /**
     * 便捷方法：加载状态（类型安全）
     *
     * 注意：由于 Kotlin 限制，此方法需要显式传递类型
     */
    protected fun <T : TabState> loadState(clazz: KClass<T>): T? {
        return coordinatorService.stateRepository.loadState(metadata.id.value, clazz)
    }

    override fun dispose() {
        destroy()
    }
}
