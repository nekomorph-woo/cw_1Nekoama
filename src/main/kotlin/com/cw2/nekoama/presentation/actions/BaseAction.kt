package com.cw2.nekoama.presentation.actions

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.runBlocking

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
        val enabled = project != null && editor != null
        e.presentation.isEnabledAndVisible = enabled
        // 在 Dumb 模式下也允许显示，但避免做索引相关操作（各子类在执行时需注意）
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (project == null || editor == null) {
            NekoamaNotifier.warn(com.cw2.nekoama.presentation.messages.NekoamaBundle.message("base.action.missingContext"))
            return
        }

        // 统一埋点计时（中文说明：不侵入各子类逻辑，只统计顶层调用耗时）
        val start = System.currentTimeMillis()
        var success = true
        var errorMessage: String? = null

        try {
            perform(project, editor, e)
        } catch (t: Throwable) {
            success = false
            errorMessage = t.message
            // 错误提示简化处理，避免暴露敏感信息
            NekoamaNotifier.error(com.cw2.nekoama.presentation.messages.NekoamaBundle.message("base.action.failed", t.message ?: com.cw2.nekoama.presentation.messages.NekoamaBundle.message("common.unknownError")))
        } finally {
            val cost = System.currentTimeMillis() - start

            // 获取文件信息
            val fileName = getCurrentFileName(editor)

            // 使用增强版指标收集器记录详细信息
            runBlocking {
                EnhancedMetricsCollector.record(
                    actionType = getActionType(),
                    success = success,
                    latencyMs = cost,
                    tokensUsed = 0, // 将在具体的AI调用中更新
                    errorMessage = errorMessage,
                    project = project,
                    fileName = fileName
                )
            }
        }
    }

    /**
     * 子类实现具体处理逻辑
     */
    protected abstract fun perform(project: Project, editor: Editor, e: AnActionEvent)

    /**
     * 子类需要实现此方法来返回操作类型
     */
    protected abstract fun getActionType(): ActionType

    /**
     * 获取当前文件名
     */
    private fun getCurrentFileName(editor: Editor): String? {
        return try {
            val virtualFile: VirtualFile? = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(editor.document)
            virtualFile?.name
        } catch (e: Exception) {
            null
        }
    }
}
