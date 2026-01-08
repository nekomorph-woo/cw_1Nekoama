package com.cw2.nekoama.domain.toolwindow.model

/**
 * Tab生命周期状态
 */
enum class TabLifecycle {
    /**
     * Tab已创建但未初始化UI
     */
    CREATED,

    /**
     * Tab正在初始化UI组件
     */
    INITIALIZING,

    /**
     * Tab已就绪，可以显示
     */
    READY,

    /**
     * Tab当前激活（用户选中）
     */
    ACTIVE,

    /**
     * Tab已失活（用户切换到其他Tab）
     */
    INACTIVE,

    /**
     * Tab已销毁
     */
    DESTROYED
}
