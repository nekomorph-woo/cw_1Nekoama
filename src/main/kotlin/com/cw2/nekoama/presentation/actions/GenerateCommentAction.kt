package com.cw2.nekoama.presentation.actions

import com.cw2.nekoama.ai.model.*
import com.cw2.nekoama.ai.provider.custom.CustomAPIConfig
import com.cw2.nekoama.ai.provider.custom.CustomAPIProvider
import com.cw2.nekoama.ai.provider.openai.OpenAIConfig
import com.cw2.nekoama.ai.provider.openai.OpenAIProvider
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.data.settings.NekoamaSecureStorage
import com.cw2.nekoama.data.settings.NekoamaSettings
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.integrations.psi.UniversalCodeAnalyzer
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.*

/**
 * 生成注释（KDoc/JavaDoc）
 *
 * 实现策略：
 * - 在后台任务中分析当前光标处的代码元素
 * - 构建适当的 CodeContext 传递给 AI Provider
 * - 调用配置的 AI Provider 生成真实的注释内容
 * - 使用写命令将AI生成的注释插入到代码中
 */
internal class GenerateCommentAction : BaseAction() {

    override fun perform(project: Project, editor: Editor, e: AnActionEvent) {
        val psiFile: PsiFile = e.getData(CommonDataKeys.PSI_FILE) ?: run {
            NekoamaNotifier.warn(NekoamaBundle.message("action.comment.noPsiFile"))
            return
        }
        val offset = editor.caretModel.offset
        val elementAndLang = ReadAction.compute<Pair<PsiElement, ProgrammingLanguage>?, Throwable> {
            val element = psiFile.findElementAt(offset)
            if (element != null) {
                // 检查Kotlin字段/属性
                val ktProp = PsiTreeUtil.getParentOfType(element, KtProperty::class.java)
                if (ktProp != null) return@compute Pair(ktProp, ProgrammingLanguage.KOTLIN)
                // 检查Java字段
                val jmField = PsiTreeUtil.getParentOfType(element, PsiField::class.java)
                if (jmField != null) return@compute Pair(jmField, ProgrammingLanguage.JAVA)
                // 检查Kotlin方法
                val kt = PsiTreeUtil.getParentOfType(element, KtFunction::class.java)
                if (kt != null) return@compute Pair(kt, ProgrammingLanguage.KOTLIN)
                // 检查Java方法
                val jm = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
                if (jm != null) return@compute Pair(jm, ProgrammingLanguage.JAVA)
                // 检查Kotlin类
                val kc = PsiTreeUtil.getParentOfType(element, KtClass::class.java)
                if (kc != null) return@compute Pair(kc, ProgrammingLanguage.KOTLIN)
                // 检查Java类
                val jc = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                if (jc != null) return@compute Pair(jc, ProgrammingLanguage.JAVA)
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
                    // 检查是否已存在注释
                    val hasExistingDoc = ReadAction.compute<Boolean, Throwable> {
                        when (val el = element) {
                            is KtProperty -> el.docComment != null
                            is PsiField -> el.docComment != null
                            is KtFunction -> el.docComment != null
                            is PsiMethod -> el.docComment != null
                            is KtClass -> el.docComment != null
                            is PsiClass -> el.docComment != null
                            else -> false
                        }
                    }
                    if (hasExistingDoc) {
                        NekoamaNotifier.info(NekoamaBundle.message("action.comment.alreadyExists"))
                        return
                    }

                    if (indicator.isCanceled) return

                    // 创建AI Provider实例
                    val provider = createAIProvider()
                    if (provider == null) {
                        NekoamaNotifier.warn(NekoamaBundle.message("settings.api.notConfigured"))
                        return
                    }

                    indicator.text = NekoamaBundle.message("progress.analyzingTargetContext")

                    // 构建代码上下文
                    val codeContext = buildCodeContext(project, element, indicator)
                    if (codeContext == null || indicator.isCanceled) return

                    indicator.text = NekoamaBundle.message("progress.generatingComment")

                    // 调用AI生成注释
                    val result = runBlocking {
                        provider.generateComment(codeContext)
                    }

                    if (indicator.isCanceled) return

                    // 处理结果并插入注释
                    if (result.isSuccess) {
                        val commentSuggestion = result.getOrNull()
                        val commentContent =
                            commentSuggestion?.content ?: NekoamaBundle.message("action.comment.generatedPlaceholder")

                        // 写命令：插入AI生成的注释（KDoc/JavaDoc）
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
                                    // 未知类型：不进行插入
                                }
                            }
                            NekoamaNotifier.info(NekoamaBundle.message("action.comment.generatedOk"))
                        })
                    } else {
                        val error = result.errorOrNull()
                        run {
                            val errMsg = error?.message ?: NekoamaBundle.message("common.unknownError")
                            NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                        }
                    }

                } catch (t: Throwable) {
                    NekoamaLogger.logError(
                        "GenerateCommentAction",
                        com.cw2.nekoama.core.exception.NekoamaError.APIError.ServerError("注释生成异常: ${t.message}"),
                        mapOf("exception" to (t.message ?: "unknown"))
                    )
                    run {
                        val errMsg = t.message ?: NekoamaBundle.message("common.unknownError")
                        NekoamaNotifier.warn(NekoamaBundle.message("action.common.failed", errMsg))
                    }
                }
            }
        })
    }

    /**
     * 根据设置创建AI Provider实例
     */
    private fun createAIProvider(): com.cw2.nekoama.ai.provider.AIProvider? {
        val settings = NekoamaSettings.getInstance()
        val secureKey = NekoamaSecureStorage.getApiKeySync()
        val resolvedKey =
            if (secureKey.isNotBlank()) secureKey else settings.apiKey.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }

        if (resolvedKey.isBlank()) return null

        return when (settings.aiProvider) {
            "Custom" -> {
                if (settings.apiEndpoint.isBlank()) return null
                CustomAPIProvider(
                    CustomAPIConfig(
                        providerName = "Custom API",
                        apiUrl = settings.apiEndpoint,
                        apiKey = resolvedKey,
                        model = settings.model,
                        temperature = settings.modelTemperature / 100.0,
                        timeoutMs = settings.requestTimeoutMs.toLong(),
                        maxTokens = 300
                    )
                )
            }

            else -> {
                OpenAIProvider(
                    OpenAIConfig(
                        apiKey = resolvedKey,
                        model = settings.model,
                        temperature = settings.modelTemperature / 100.0,
                        timeoutMs = settings.requestTimeoutMs.toLong(),
                        maxTokens = 300
                    )
                )
            }
        }
    }

    /**
     * 构建代码上下文（专为注释生成优化）
     */
    private fun buildCodeContext(project: Project, element: PsiElement, indicator: ProgressIndicator): CodeContext? {
        return try {
            ReadAction.compute<CodeContext?, Throwable> {
                val analyzer = UniversalCodeAnalyzer(project)
                val language = analyzer.detectLanguage(element)
                val projectInfo = analyzer.getProjectInfo()
                val surroundingContext = analyzer.extractSurroundingContext(element).getOrNull() ?: SurroundingContext(
                    precedingCode = emptyList(),
                    followingCode = emptyList(),
                    imports = emptyList(),
                    packageDeclaration = null,
                    fileComments = emptyList(),
                    siblingElements = emptyList(),
                    namingPatterns = null,
                    codeStyleAnalysis = null
                )

                when (element) {
                    is KtFunction -> {
                        val analyzeResult = analyzer.analyzeMethod(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? MethodContext
                        } else {
                            MethodContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                methodName = element.name,
                                parameters = emptyList(),
                                returnType = TypeInfo("Unit"),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                exceptions = emptyList(),
                                methodBody = element.bodyExpression?.text,
                                isConstructor = false,
                                isAbstract = false,
                                containingClass = null
                            )
                        }
                    }

                    is PsiMethod -> {
                        val analyzeResult = analyzer.analyzeMethod(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? MethodContext
                        } else {
                            MethodContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                methodName = element.name,
                                parameters = emptyList(),
                                returnType = TypeInfo(element.returnType?.presentableText ?: "void"),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                exceptions = emptyList(),
                                methodBody = element.body?.text,
                                isConstructor = element.isConstructor,
                                isAbstract = element.hasModifierProperty(PsiModifier.ABSTRACT),
                                containingClass = null
                            )
                        }
                    }

                    is KtProperty -> {
                        val analyzeResult = analyzer.analyzeVariable(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? VariableContext
                        } else {
                            VariableContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                variableName = element.name,
                                variableType = TypeInfo("Any"),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = element.initializer?.text,
                                scope = com.cw2.nekoama.ai.model.VariableScope.LOCAL,
                                isConstant = !element.isVar,
                                isStatic = false,
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }
                    }

                    is PsiField -> {
                        val analyzeResult = analyzer.analyzeVariable(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? VariableContext
                        } else {
                            VariableContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                variableName = element.name,
                                variableType = TypeInfo(element.type.presentableText),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = element.initializer?.text,
                                scope = if (element.hasModifierProperty(PsiModifier.STATIC)) com.cw2.nekoama.ai.model.VariableScope.STATIC_FIELD else com.cw2.nekoama.ai.model.VariableScope.FIELD,
                                isConstant = element.hasModifierProperty(PsiModifier.FINAL),
                                isStatic = element.hasModifierProperty(PsiModifier.STATIC),
                                usagePattern = null,
                                containingClass = null,
                                containingMethod = null
                            )
                        }
                    }

                    is KtClass -> {
                        val analyzeResult = analyzer.analyzeClass(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? ClassContext
                        } else {
                            ClassContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                className = element.name,
                                superClass = null,
                                interfaces = emptyList(),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                fields = emptyList(),
                                methods = emptyList(),
                                innerClasses = emptyList(),
                                isInterface = false,
                                isAbstract = false,
                                isEnum = false,
                                packageName = surroundingContext.packageDeclaration ?: ""
                            )
                        }
                    }

                    is PsiClass -> {
                        val analyzeResult = analyzer.analyzeClass(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? ClassContext
                        } else {
                            ClassContext(
                                language = language,
                                projectInfo = projectInfo,
                                surroundingContext = surroundingContext,
                                className = element.name,
                                superClass = null,
                                interfaces = emptyList(),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                fields = emptyList(),
                                methods = emptyList(),
                                innerClasses = emptyList(),
                                isInterface = element.isInterface,
                                isAbstract = element.hasModifierProperty(PsiModifier.ABSTRACT),
                                isEnum = element.isEnum,
                                packageName = surroundingContext.packageDeclaration ?: ""
                            )
                        }
                    }

                    else -> {
                        MethodContext(
                            language = language,
                            projectInfo = projectInfo,
                            surroundingContext = surroundingContext,
                            methodName = null,
                            parameters = emptyList(),
                            returnType = TypeInfo("void"),
                            modifiers = emptyList(),
                            annotations = emptyList(),
                            exceptions = emptyList(),
                            methodBody = null,
                            isConstructor = false,
                            isAbstract = false,
                            containingClass = null
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            NekoamaLogger.logError(
                "buildCodeContext",
                com.cw2.nekoama.core.exception.NekoamaError.ParseError.InvalidConfiguration("构建代码上下文失败: ${t.message}"),
                mapOf("exception" to (t.message ?: "unknown"))
            )
            null
        }
    }

    override fun getActionType(): ActionType = ActionType.GENERATE_COMMENT
}
