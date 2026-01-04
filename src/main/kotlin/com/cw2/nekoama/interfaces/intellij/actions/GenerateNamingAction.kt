package com.cw2.nekoama.interfaces.intellij.actions

import com.cw2.nekoama.application.usecase.GenerateNamingUseCase
import com.cw2.nekoama.application.usecase.GeneratorFactory
import com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis.CodeAnalysisService
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.infrastructure.code_suggestion_gen.code_analysis.UniversalCodeElementAnalyzer
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.shared.util.NekoamaNotifier
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.shared.exception.NekoamaError
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.*

/**
 * 生成命名建议的动作
 *
 * 职责：
 * - 处理 UI 交互（Editor、AnActionEvent）
 * - 提取 PSI 元素（从光标位置）
 * - 调用 UseCase 执行业务逻辑
 * - 展示生成结果给用户
 *
 * 业务逻辑已移至 GenerateNamingUseCase
 */
internal class GenerateNamingAction : BaseAction() {

    override fun getMenuTextKey(): String = "naming"

    override fun getCustomText(settings: NekoamaSettings): String =
        settings.customNamingMenuText

    override fun perform(project: Project, editor: Editor?, e: AnActionEvent) {
        // 创建 UseCase 实例
        val useCase = GenerateNamingUseCase(
            project = project,
            codeAnalysisService = CodeAnalysisService(UniversalCodeElementAnalyzer(project)),
            generatorFactory = GeneratorFactory()
        )
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: run {
            NekoamaNotifier.warn(NekoamaBundle.message("action.naming.noPsiFile"))
            return
        }

        // 优先获取使用光标位置的 PSI 元素（避免右键点击触发时，菜单中可能包含的 PSI 元素，而是右键位置）
        val element = elementAtCaret(editor!!, psiFile) ?: e.getData(CommonDataKeys.PSI_ELEMENT)
        if (element == null) {
            NekoamaNotifier.warn(NekoamaBundle.message("action.naming.noElement"))
            return
        }

        // 在EDT线程预先获取选中的文本，避免后台线程直接访问 UI
        val selectionText = ReadAction.compute<String?, Throwable> {
            editor.selectionModel.selectedText
        }

        val title = NekoamaBundle.message("action.generateNaming.text")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = NekoamaBundle.message("progress.analyzingContext")

                try {
                    if (indicator.isCanceled) return

                    indicator.text = NekoamaBundle.message("progress.generatingNaming")

                    // 调用 UseCase 生成命名建议
                    val result = runBlocking {
                        useCase.generateNaming(element, selectionText)
                    }

                    if (indicator.isCanceled) return

                    // 处理结果
                    val message = if (result.isSuccess) {
                        val suggestions = result.getOrNull() ?: emptyList()
                        if (suggestions.isNotEmpty()) {
                            val suggestionList = suggestions.take(5).joinToString(" / ") { it.name }
                            NekoamaBundle.message("action.naming.suggestions", suggestionList)
                        } else {
                            NekoamaBundle.message("action.naming.noSuggestions")
                        }
                    } else {
                        val error = result.errorOrNull()
                        val errMsg = error?.message ?: NekoamaBundle.message("common.unknownError")
                        NekoamaBundle.message("action.common.failed", errMsg)
                    }

                    NekoamaNotifier.info(message)

                } catch (t: Throwable) {
                    NekoamaLogger.logError(
                        "GenerateNamingAction",
                        NekoamaError.APIError.ServerError("命名生成异常: ${t.message}"),
                        mapOf("exception" to (t.message ?: "unknown"))
                    )
                    val errMsg = t.message ?: NekoamaBundle.message("common.unknownError")
                    NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                }
            }
        })
    }

    override fun requiresEditor(): Boolean = true

    /**
     * 提取光标位置的 PSI 元素
     * 这是 UI 层的逻辑，需要保留在 Action 中
     */
    private fun elementAtCaret(editor: Editor?, psiFile: PsiFile): PsiElement? {
        val offset = editor!!.caretModel.offset
        val element = psiFile.findElementAt(offset)
        if (element != null) {
            // 尝试查找是否是局部变量（优先 Kotlin 和 Java）
            val ktVar = PsiTreeUtil.getParentOfType(element, KtProperty::class.java)
            if (ktVar != null) return ktVar
            val psiVar = PsiTreeUtil.getParentOfType(element, PsiLocalVariable::class.java)
            if (psiVar != null) return psiVar
            val psiParam = PsiTreeUtil.getParentOfType(element, PsiParameter::class.java)
            if (psiParam != null) return psiParam
        }
        return element
    }
}
