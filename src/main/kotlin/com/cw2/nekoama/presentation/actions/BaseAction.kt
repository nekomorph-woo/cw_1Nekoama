package com.cw2.nekoama.presentation.actions

import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
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
 * - 轻量埋点：记录一次动作调用的成功/失败与耗时，用于工具窗口统计
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
            NekoamaNotifier.warn(com.cw2.nekoama.presentation.messages.NekoamaBundle.message("base.action.missingContext"))
            return
        }

        // 统一埋点计时（中文说明：不侵入各子类逻辑，只统计顶层调用耗时）
        val start = System.currentTimeMillis()
        var success = true
        var errorMessage: String? = null
        var tokensUsed = 0

        try {
            tokensUsed = perform(project, editor, e)
        } catch (t: Throwable) {
            success = false
            errorMessage = t.message
            // 错误提示简化处理，避免暴露敏感信息
            NekoamaNotifier.error(com.cw2.nekoama.presentation.messages.NekoamaBundle.message("base.action.failed", t.message ?: com.cw2.nekoama.presentation.messages.NekoamaBundle.message("common.unknownError")))
        } finally {
            // 注意：统计已移至Provider层，避免重复记录
            // 这里保留finally块用于可能的清理操作
        }
    }

    /**
     * 子类实现具体处理逻辑
     * @return 返回使用的Token数量，如果无法获取则返回0
     */
    protected abstract fun perform(project: Project, editor: Editor?, e: AnActionEvent): Int

    /**
     * 子类需要实现此方法来返回操作类型
     */
    protected abstract fun getActionType(): ActionType

    /**
     * 子类需要实现此方法来指定是否需要editor上下文
     * 返回true表示需要editor（如编辑器中的代码操作）
     * 返回false表示不需要editor（如项目级别的扫描操作）
     */
    protected abstract fun requiresEditor(): Boolean

    /**
     * 获取当前文件名
     */
    private fun getCurrentFileName(project: Project, editor: Editor?): String? {
        return try {
            if (editor != null) {
                val virtualFile: VirtualFile? = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(editor.document)
                virtualFile?.name
            } else {
                // 对于不需要editor的Action，返回项目名称
                project.name
            }
        } catch (e: Exception) {
            null
        }
    }
}
