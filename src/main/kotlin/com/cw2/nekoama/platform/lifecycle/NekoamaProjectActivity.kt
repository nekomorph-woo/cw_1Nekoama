package com.cw2.nekoama.platform.lifecycle

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.integrations.editor.NekoamaTypedActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/*
 * 项目启动活动（兼容 2024+ 平台）
 *
 * 中文说明：
 * - 使用 ProjectActivity（suspend execute）在项目可用时安装自定义 TypedActionHandler。
 * - 包裹原处理器，确保非目标输入走默认逻辑，避免影响 IDE 行为。
 */
internal class NekoamaProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        try {
            val actionManager: EditorActionManager = EditorActionManager.getInstance()
            val typedAction: TypedAction = actionManager.typedAction
            val original: TypedActionHandler? = typedAction.handler

            typedAction.setupHandler(NekoamaTypedActionHandler(original))
            NekoamaLogger.info("STARTUP", "NekoamaTypedActionHandler installed (ProjectActivity)")
        } catch (e: Throwable) {
            NekoamaLogger.warn("STARTUP", "Failed to install typed handler (ProjectActivity)", error = e)
        }
    }
}
