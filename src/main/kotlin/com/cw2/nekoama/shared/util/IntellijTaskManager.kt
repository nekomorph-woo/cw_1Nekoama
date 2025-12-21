package com.cw2.nekoama.shared.util

import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * 后台任务执行器
 *
 * 提供带进度的后台任务执行，支持取消与超时检查。
 */
object IntellijTaskManager {

    /**
     * 在后台执行任务，显示进度，可取消。
     * @param project 所在项目（可空，用于窗口归属）
     * @param title 任务标题（显示于进度窗口）
     * @param cancellable 是否可取消
     * @param task 实际执行体，接受 ProgressIndicator
     * @param onSuccess 成功回调
     * @param onError 失败回调
     */
    fun <T> execute(
        project: Project?,
        title: String,
        cancellable: Boolean = true,
        task: (indicator: ProgressIndicator) -> T,
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val timeoutMs = NekoamaSettings.Companion.getInstance().requestTimeoutMs
        val start = System.currentTimeMillis()
        val backgroundTask = object : Task.Backgroundable(project, title, cancellable) {
            private var result: T? = null
            private var error: Throwable? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = true
                    indicator.text = title
                    // 执行主逻辑：调用方可在内部更新 indicator.text2 以提示细节
                    result = task(indicator)
                } catch (t: Throwable) {
                    error = t
                }
            }

            override fun onSuccess() {
                error?.let { onError(it) } ?: result?.let { onSuccess(it) }
            }

            override fun onCancel() {
                // 中文：用户主动取消
                NekoamaLogger.info("AITaskManager", "AI task cancelled by user: $title")
            }

            override fun onFinished() {
                // 中文：软性超时提示（不强制终止线程）
                val cost = System.currentTimeMillis() - start
                if (cost > timeoutMs) {
                    NekoamaLogger.warn("AITaskManager", "AI task exceeded timeout: cost=${cost}ms > ${timeoutMs}ms, title=$title")
                }
            }
        }
        ProgressManager.getInstance().run(backgroundTask)
    }
}