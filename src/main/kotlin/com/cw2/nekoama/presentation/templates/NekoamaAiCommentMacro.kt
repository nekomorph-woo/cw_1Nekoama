package com.cw2.nekoama.presentation.templates

import com.cw2.nekoama.ai.model.CodeContext
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.data.settings.NekoamaSettings
import com.cw2.nekoama.integrations.psi.UniversalCodeAnalyzer
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Macro
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.util.UUID

/**
 * Nekoama 自定义 Live Template 宏：异步生成注释
 *
 * 使用占位符 + 后台生成策略，避免 UI 阻塞。
 */
class NekoamaAiCommentMacro : Macro() {
    override fun getName(): String = "nekoamaAiComment"

    override fun getPresentableName(): String = "nekoamaAiComment()"

    override fun calculateResult(params: Array<Expression>, context: ExpressionContext): Result? {
        val project = context.project ?: return TextResult("Nekoama: 生成注释…")
        val editor = context.editor ?: return TextResult("Nekoama: 生成注释…")
        val document = editor.document

        // 生成一个独特占位符，后续用它来进行文本替换
        val placeholder = "/* Nekoama_Generating_Comment_${UUID.randomUUID().toString().substring(0, 8)} */"

        // 在后台线程执行 AI 生成逻辑
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                generateAndReplaceAsync(project, editor, document, context, placeholder)
            } catch (pc: ProcessCanceledException) {
                // 用户取消或 IDE 关闭等情况：按平台要求重新抛出，以便上层及时终止
                NekoamaLogger.debug("NekoamaCommentMacro", "生成过程被取消", mapOf("reason" to "ProcessCanceledException"))
                throw pc
            } catch (t: Throwable) {
                // 失败时仅记录日志，不阻塞 UI
                NekoamaLogger.error("NekoamaCommentMacro", "异步注释生成失败", mapOf("exception" to (t.message ?: "unknown")), t)
            }
        }

        // 立即返回占位符，让模板先行展开
        return TextResult(placeholder)
    }

    private fun generateAndReplaceAsync(
        project: Project,
        editor: Editor,
        document: Document,
        context: ExpressionContext,
        placeholder: String
    ) {
        // 读取 PSI 元素要在 ReadAction 内进行（此处只做最小读取）
        val psiAtOffset: PsiElement? = try {
            com.intellij.openapi.application.ReadAction.compute<PsiElement?, RuntimeException> {
                context.psiElementAtStartOffset
            }
        } catch (t: Throwable) {
            null
        }

        val analyzer = UniversalCodeAnalyzer(project)
        val codeElement = psiAtOffset
        if (codeElement == null) {
            NekoamaLogger.warn("NekoamaCommentMacro", "找不到 PSI 元素，使用简单上下文")
        }

        // 根据 PSI 元素推断语言与上下文；若失败，回退到 OTHER 与最小上下文
        val language = try { analyzer.detectLanguage(codeElement ?: return) } catch (_: Throwable) { com.cw2.nekoama.ai.model.ProgrammingLanguage.OTHER }
        val projectInfo = analyzer.getProjectInfo()
        val surrounding = if (codeElement != null) {
            analyzer.extractSurroundingContext(codeElement).getOrNull()
        } else null
        val surroundingContext = surrounding ?: com.cw2.nekoama.ai.model.SurroundingContext(
            precedingCode = emptyList(),
            followingCode = emptyList(),
            imports = emptyList(),
            packageDeclaration = null,
            fileComments = emptyList(),
            siblingElements = emptyList(),
            namingPatterns = null,
            codeStyleAnalysis = null
        )

        // 这里构造一个最小可用的 CodeContext（方法/类/变量难以可靠判定，使用通用 MethodContext 近似）
        // 为什么：为最小改动与演示效果，选择一个信息较丰富的上下文类型，后续可基于 PSI 元素精细化。
        val codeContext: CodeContext = com.cw2.nekoama.ai.model.MethodContext(
            language = language,
            projectInfo = projectInfo,
            surroundingContext = surroundingContext,
            methodName = null,
            parameters = emptyList(),
            returnType = com.cw2.nekoama.ai.model.TypeInfo("Unit"),
            modifiers = emptyList(),
            annotations = emptyList(),
            exceptions = emptyList(),
            methodBody = null,
            isConstructor = false,
            isAbstract = false,
            containingClass = null
        )

        // 从设置中读取用户偏好，作为后续提示增强（当前未深入介入提示模板，以中文注释记录设计）
        val settings = NekoamaSettings.getInstance()
        val userLangPref = settings.languagePreference
        val userCommentFormat = settings.commentFormat
        val userNamingStyle = settings.namingStyle

        // 创建Custom API Provider实例
        // 优先走安全存储，向后兼容读取旧字段与环境变量
        val secureKey = com.cw2.nekoama.data.settings.NekoamaSecureStorage.getApiKeySync()
        val resolvedKey = if (secureKey.isNotBlank()) secureKey else settings.apiKey.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }

        if (resolvedKey.isBlank() || settings.apiEndpoint.isBlank()) {
            NekoamaLogger.warn("NekoamaCommentMacro", "API key or endpoint not configured")
            // 替换占位符为错误信息
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    val text = document.text
                    val newText = text.replace(placeholder, "// AI comment generation failed: API not configured")
                    document.setText(newText)
                }
            }
            return // 直接返回，不执行后续逻辑
        }

        val provider = com.cw2.nekoama.ai.provider.custom.CustomAPIProvider(
            com.cw2.nekoama.ai.provider.custom.CustomAPIConfig(
                providerName = "Custom API",
                apiUrl = settings.apiEndpoint,
                apiKey = resolvedKey,
                model = settings.model,
                // 温度从整型百分比转换为 0.0-1.0
                temperature = settings.modelTemperature / 100.0,
                timeoutMs = settings.requestTimeoutMs.toLong(),
                maxTokens = 200
            )
        )

        // 调用 AI 生成注释内容
        val result = try {
            // 使用运行阻塞的方式是为了简化：后台线程内调用挂起函数
            kotlinx.coroutines.runBlocking {
                provider.generateComment(codeContext)
            }
        } catch (t: Throwable) {
            NekoamaLogger.error("NekoamaCommentMacro", "调用 AI 失败", mapOf("exception" to (t.message ?: "unknown")), t)
            null
        }

        val finalText = when {
            result == null -> null
            result.isSuccess -> {
                val text = result.getOrNull()?.content ?: "生成失败"
                // 简单根据用户偏好调整格式（示例）：
                when (userCommentFormat) {
                    CommentFormat.JAVADOC.name -> "/**\n * $text\n */"
                    CommentFormat.JSDOC.name -> "/**\n * $text\n */"
                    else -> "// $text"
                }
            }
            else -> null
        }

        if (finalText == null) return

        // 在写命令中把占位符替换为最终内容
        replacePlaceholder(project, editor, document, placeholder, finalText)
    }

    private fun replacePlaceholder(project: Project, editor: Editor, document: Document, placeholder: String, finalText: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val text = document.text
            val idx = text.indexOf(placeholder)
            if (idx >= 0) {
                document.replaceString(idx, idx + placeholder.length, finalText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            } else {
                // 找不到占位符（用户已编辑或撤销），将结果插入到光标处，尽量不打扰用户
                val caret = editor.caretModel.currentCaret
                val offset = caret.offset
                document.insertString(offset, "$finalText\n")
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
        }
    }
}

/**
 * 用户注释格式偏好（与设置页保持一致）
 */
enum class CommentFormat { LINE, JAVADOC, JSDOC }
