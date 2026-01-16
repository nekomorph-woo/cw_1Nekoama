package com.cw2.nekoama.interfaces.intellij.actions

import com.cw2.nekoama.application.usecase.GenerateCommentUseCase
import com.cw2.nekoama.application.usecase.GeneratorFactory
import com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis.CodeAnalysisService
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.domain.code_suggestion_gen.model.CommentSuggestion
import com.cw2.nekoama.domain.statistics.model.ActionType
import com.cw2.nekoama.domain.statistics.service.StatisticsService
import com.cw2.nekoama.domain.statistics.service.TokenUsageData
import com.cw2.nekoama.infrastructure.code_suggestion_gen.code_analysis.UniversalCodeElementAnalyzer
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.shared.util.NekoamaNotifier
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.shared.exception.NekoamaError
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.*

/**
 * 生成注释（KDoc/JavaDoc）
 *
 * 职责：
 * - 处理 UI 交互（Editor、AnActionEvent）
 * - 提取 PSI 元素（从光标位置）
 * - 调用 UseCase 执行业务逻辑
 * - 将生成的注释插入到编辑器
 *
 * 业务逻辑已移至 GenerateCommentUseCase
 */
internal class GenerateCommentAction : BaseAction() {

    override fun getMenuTextKey(): String = "comment"

    override fun getCustomText(settings: NekoamaSettings): String =
        settings.customCommentMenuText

    override fun perform(project: Project, editor: Editor?, e: AnActionEvent) {
        // 创建 UseCase 实例
        val useCase = GenerateCommentUseCase(
            project = project,
            codeAnalysisService = CodeAnalysisService(UniversalCodeElementAnalyzer(project)),
            generatorFactory = GeneratorFactory()
        )
        val psiFile: PsiFile = e.getData(CommonDataKeys.PSI_FILE) ?: run {
            NekoamaNotifier.warn(NekoamaBundle.message("action.comment.noPsiFile"))
            return
        }
        val offset = editor!!.caretModel.offset
        val elementAndLang = ReadAction.compute<Pair<PsiElement, com.cw2.nekoama.domain.code_suggestion_gen.model.ProgrammingLanguage>?, Throwable> {
            val element = psiFile.findElementAt(offset)
            if (element != null) {
                // 查找Kotlin字段/方法
                val ktProp = PsiTreeUtil.getParentOfType(element, KtProperty::class.java)
                if (ktProp != null) return@compute Pair(ktProp, com.cw2.nekoama.domain.code_suggestion_gen.model.ProgrammingLanguage.KOTLIN)
                // 查找Java字段
                val jmField = PsiTreeUtil.getParentOfType(element, PsiField::class.java)
                if (jmField != null) return@compute Pair(jmField, com.cw2.nekoama.domain.code_suggestion_gen.model.ProgrammingLanguage.JAVA)
                // 查找Kotlin方法
                val kt = PsiTreeUtil.getParentOfType(element, KtFunction::class.java)
                if (kt != null) return@compute Pair(kt, com.cw2.nekoama.domain.code_suggestion_gen.model.ProgrammingLanguage.KOTLIN)
                // 查找Java方法
                val jm = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
                if (jm != null) return@compute Pair(jm, com.cw2.nekoama.domain.code_suggestion_gen.model.ProgrammingLanguage.JAVA)
                // 查找Kotlin类
                val kc = PsiTreeUtil.getParentOfType(element, KtClass::class.java)
                if (kc != null) return@compute Pair(kc, com.cw2.nekoama.domain.code_suggestion_gen.model.ProgrammingLanguage.KOTLIN)
                // 查找Java类
                val jc = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                if (jc != null) return@compute Pair(jc, com.cw2.nekoama.domain.code_suggestion_gen.model.ProgrammingLanguage.JAVA)
            }
            null
        }
        if (elementAndLang == null) {
            NekoamaNotifier.warn(NekoamaBundle.message("action.comment.notSupportedHere"))
            return
        }
        val (element, detectedLang) = elementAndLang

        val title = NekoamaBundle.message("action.generateComment.text")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = NekoamaBundle.message("progress.checkingCommentStatus")

                try {
                    indicator.text = NekoamaBundle.message("progress.analyzingTargetContext")

                    // 调用 UseCase 生成注释
                    val result = runBlocking {
                        useCase.generateComment(element)
                    }

                    if (indicator.isCanceled) return

                    // 处理结果并插入注释
                    if (result.isSuccess) {
                        val suggestion = result.getOrNull()
                            ?: run {
                                NekoamaNotifier.warn(NekoamaBundle.message("action.comment.generatedPlaceholder"))
                                return
                            }

                        val commentContent = suggestion.content

                        // 写命令：将AI生成的注释（KDoc/JavaDoc）插入到代码
                        WriteCommandAction.runWriteCommandAction(project, title, null, Runnable {
                            when (val el = element) {
                                is KtProperty -> {
                                    val psiFactory = KtPsiFactory(project)
                                    val doc = psiFactory.createComment("/**\n * $commentContent\n */")
                                    (el as KtDeclaration).addBefore(doc, el.firstChild)
                                }

                                is PsiField -> {
                                    val factory = JavaPsiFacade.getElementFactory(project)
                                    val doc = factory.createDocCommentFromText("/**\n * $commentContent\n */")
                                    el.addBefore(doc, el.firstChild)
                                    CodeStyleManager.getInstance(project).reformat(el)
                                }

                                is KtFunction -> {
                                    val psiFactory = KtPsiFactory(project)
                                    val doc = psiFactory.createComment("/**\n * $commentContent\n */")
                                    (el as KtDeclaration).addBefore(doc, el.firstChild)
                                }

                                is PsiMethod -> {
                                    val factory = JavaPsiFacade.getElementFactory(project)
                                    val doc = factory.createDocCommentFromText("/**\n * $commentContent\n */")
                                    el.addBefore(doc, el.firstChild)
                                    CodeStyleManager.getInstance(project).reformat(el)
                                }

                                is KtClass -> {
                                    val psiFactory = KtPsiFactory(project)
                                    val doc = psiFactory.createComment("/**\n * $commentContent\n */")
                                    (el as KtDeclaration).addBefore(doc, el.firstChild)
                                }

                                is PsiClass -> {
                                    val factory = JavaPsiFacade.getElementFactory(project)
                                    val doc = factory.createDocCommentFromText("/**\n * $commentContent\n */")
                                    el.addBefore(doc, el.firstChild)
                                    CodeStyleManager.getInstance(project).reformat(el)
                                }

                                else -> {
                                    // 未知类型，暂不处理
                                }
                            }
                            NekoamaNotifier.info(NekoamaBundle.message("action.comment.generatedOk"))
                        })

                        // 记录使用统计
                        project.service<StatisticsService>()?.let { service ->
                            CoroutineScope(Dispatchers.IO).launch {
                                // 记录功能使用次数
                                service.recordUsage(ActionType.COMMENT)

                                // 记录 Token 使用量
                                service.recordTokenUsage(
                                    TokenUsageData(
                                        promptTokens = suggestion.metadata.promptTokens,
                                        completionTokens = suggestion.metadata.completionTokens,
                                        totalTokens = suggestion.metadata.totalTokens
                                    )
                                )
                            }
                        }
                    } else {
                        val error = result.errorOrNull()
                        val errMsg = error?.message ?: NekoamaBundle.message("common.unknownError")
                        NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                    }

                } catch (t: Throwable) {
                    NekoamaLogger.logError(
                        "GenerateCommentAction",
                        NekoamaError.APIError.ServerError("注释生成异常: ${t.message}"),
                        mapOf("exception" to (t.message ?: "unknown"))
                    )
                    val errMsg = t.message ?: NekoamaBundle.message("common.unknownError")
                    NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                }
            }
        })
    }

    override fun requiresEditor(): Boolean = true
}
