package com.cw2.nekoama.interfaces.intellij.actions

import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.shared.util.NekoamaNotifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * 所有 Nekoama 动作的基类
 *
 * 设计动机（为什么）：
 * - 统一可用性检查（无 Project/Editor 时禁用）
 * - 复用通用的参数提取逻辑
 * - 降低各 Action 的样板代码
 */
internal abstract class BaseAction : AnAction(), DumbAware {

    final override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val enabled = project != null && (requiresEditor() || editor != null)
        e.presentation.isEnabledAndVisible = enabled
        // 在 Dumb 模式下也允许显示，但避免做索引相关操作（各子类在执行时需注意）
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (project == null || (requiresEditor() && editor == null)) {
            NekoamaNotifier.warn(NekoamaBundle.message("base.action.missingContext"))
            return
        }

        try {
            perform(project, editor, e)
        } catch (t: Throwable) {
            // 错误提示简化处理，避免暴露敏感信息
            NekoamaNotifier.error(NekoamaBundle.message("base.action.failed", t.message ?: NekoamaBundle.message("common.unknownError")))
        }
    }

    /**
     * 子类实现具体处理逻辑
     */
    protected abstract fun perform(project: Project, editor: Editor?, e: AnActionEvent)

    /**
     * 子类需要实现此方法来指定是否需要editor上下文
     * 返回true表示需要editor（如编辑器中的代码操作）
     * 返回false表示不需要editor（如项目级别的扫描操作）
     */
    protected abstract fun requiresEditor(): Boolean
}
