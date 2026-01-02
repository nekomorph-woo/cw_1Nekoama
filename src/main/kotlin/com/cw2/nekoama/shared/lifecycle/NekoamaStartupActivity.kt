package com.cw2.nekoama.shared.lifecycle

import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.domain.editor.service.NekoamaTypedActionHandler
import com.cw2.nekoama.infrastructure.network.proxy.ProxyInitializationManager
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/*
 * 启动活动：在项目启动后安装自定义键入处理器
 *
 * 中文说明：
 * - 为了兼容新版平台扩展点变动，这里通过运行时替换的方式包裹原有 TypedActionHandler。
 * - 不依赖已标记废弃或将移除的扩展点，降低兼容风险。
 */
internal class NekoamaStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        try {
            // 初始化全局代理配置
            ProxyInitializationManager.initialize()

            val actionManager: EditorActionManager = EditorActionManager.getInstance()
            val typedAction: TypedAction = actionManager.typedAction
            val original: TypedActionHandler? = typedAction.handler

            // 包裹原处理器，形成链式调用
            typedAction.setupHandler(NekoamaTypedActionHandler(original))
            NekoamaLogger.info("STARTUP", "NekoamaTypedActionHandler installed")
        } catch (e: Throwable) {
            // 启动失败不应影响 IDE 使用，记录日志即可
            NekoamaLogger.warn("STARTUP", "Failed to install typed handler", error = e)
        }
    }
}
