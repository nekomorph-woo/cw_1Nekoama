package com.cw2.nekoama.interfaces.intellij.actions

import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.infrastructure.config.MenuTextProvider
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.shared.util.NekoamaNotifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * 所有 Nekoama 动作的基类
 *
 * 设计动机（为什么）：
 * - 统一可用性检查（无 Project/Editor 时禁用）
 * - 复用通用的参数提取逻辑
 * - 降低各 Action 的样板代码
 * - 统一处理 Smart Mode 检查，确保所有功能在索引完成后执行
 */
internal abstract class BaseAction : AnAction(), DumbAware {

    final override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val enabled = project != null && (requiresEditor() || editor != null)
        e.presentation.isEnabledAndVisible = enabled

        // 动态更新菜单文本（解决 addTextOverride 不刷新的问题）
        if (enabled) {
            updateMenuText(e)
        }
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (project == null || (requiresEditor() && editor == null)) {
            NekoamaNotifier.warn(NekoamaBundle.message("base.action.missingContext"))
            return
        }

        try {
            // 统一在 Smart 模式下执行所有 Action，避免 LoadingState 错误
            // 这确保了 PSI 访问、索引系统等在 IDE 完全初始化后才被调用
            if (shouldWaitForSmartMode()) {
                DumbService.getInstance(project).runWhenSmart {
                    perform(project, editor, e)
                }
            } else {
                perform(project, editor, e)
            }
        } catch (t: Throwable) {
            // 错误提示简化处理，避免暴露敏感信息
            NekoamaNotifier.error(NekoamaBundle.message("base.action.failed", t.message ?: NekoamaBundle.message("common.unknownError")))
        }
    }

    /**
     * 子类可重写此方法来控制是否等待 Smart 模式
     * 默认为 true（需要等待索引完成），因为大多数功能都涉及 PSI 访问
     *
     * @return true 表示等待 Smart 模式，false 表示立即执行
     */
    protected open fun shouldWaitForSmartMode(): Boolean = true

    /**
     * 动态更新菜单文本
     * 根据 NekoamaSettings 中的配置动态设置菜单显示文本
     */
    private fun updateMenuText(e: AnActionEvent) {
        val settings = NekoamaSettings.getInstance()
        val menuText = MenuTextProvider.getMenuText(
            menuTextKey = getMenuTextKey(),
            style = settings.menuDisplayNameStyle,
            customText = getCustomText(settings)
        )
        e.presentation.text = menuText
    }

    /**
     * 子类提供菜单文本的国际化 key（用于默认文本）
     *
     * @return 菜单类型标识 ("naming", "comment", "generate")
     */
    protected abstract fun getMenuTextKey(): String

    /**
     * 子类提供自定义文本字段（当 style = CUSTOM 时使用）
     *
     * @param settings 当前设置对象
     * @return 自定义菜单文本
     */
    protected abstract fun getCustomText(settings: NekoamaSettings): String

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
