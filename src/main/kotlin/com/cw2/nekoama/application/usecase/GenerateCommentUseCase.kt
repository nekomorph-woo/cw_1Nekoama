package com.cw2.nekoama.application.usecase

import com.cw2.nekoama.domain.code_suggestion_gen.model.*
import com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis.CodeAnalysisService
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.shared.model.Result
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * 生成代码注释的应用服务用例
 *
 * 职责：
 * - 编排注释生成的业务流程
 * - 分析 PSI 元素并构建 CodeContext
 * - 检测是否已存在注释
 * - 调用 AI 生成注释
 * - 返回生成结果供 Action 层插入
 *
 * @param project IntelliJ 项目实例
 * @param codeAnalysisService 代码分析服务
 * @param generatorFactory 生成器工厂
 */
class GenerateCommentUseCase(
    private val project: com.intellij.openapi.project.Project,
    private val codeAnalysisService: CodeAnalysisService,
    private val generatorFactory: GeneratorFactory
) {

    /**
     * 生成代码注释
     *
     * @param element 目标 PSI 元素
     * @return 生成结果，成功时返回注释内容，失败时返回错误信息
     */
    suspend fun generateComment(element: PsiElement): Result<String> {
        // 创建生成器
        val generator = generatorFactory.createGenerator(maxTokens = 300)
            ?: return Result.error(NekoamaError.AuthenticationError.ApiKeyNotConfigured("API 未配置"))

        // 检测是否已存在注释
        if (hasExistingDoc(element)) {
            return Result.error(NekoamaError.ParseError.InvalidConfiguration("注释已存在"))
        }

        // 构建代码上下文
        val codeContext = buildCodeContext(element)
            ?: return Result.error(NekoamaError.ParseError.InvalidConfiguration("无法构建代码上下文"))

        // 调用 AI 生成注释
        val result = generator.generateComment(codeContext)

        // 提取注释内容
        return result.map { it.content }
    }

    /**
     * 检测是否已存在注释
     */
    private fun hasExistingDoc(element: PsiElement): Boolean {
        return ReadAction.compute<Boolean, Throwable> {
            when (element) {
                is KtProperty -> element.docComment != null
                is com.intellij.psi.PsiField -> element.docComment != null
                is KtFunction -> element.docComment != null
                is com.intellij.psi.PsiMethod -> element.docComment != null
                is KtClass -> element.docComment != null
                is com.intellij.psi.PsiClass -> element.docComment != null
                else -> false
            }
        }
    }

    /**
     * 构建代码上下文对象（专为注释生成优化）
     */
    private fun buildCodeContext(element: PsiElement): CodeContext? {
        return ReadAction.compute<CodeContext?, Throwable> {
            try {
                val language = codeAnalysisService.detectLanguage(element)
                val projectMetadata = codeAnalysisService.getProjectMetadata()
                val surroundingContext = codeAnalysisService.extractSurroundingContext(element).getOrNull()
                    ?: SurroundingContext(namingPatterns = null)

                when (element) {
                    is KtFunction -> {
                        val analyzeResult = codeAnalysisService.analyzeMethod(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? MethodContext
                        } else {
                            MethodContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                methodName = element.name,
                                parameters = emptyList(),
                                returnType = TypeMetadata("Unit"),
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

                    is com.intellij.psi.PsiMethod -> {
                        val analyzeResult = codeAnalysisService.analyzeMethod(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? MethodContext
                        } else {
                            MethodContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                methodName = element.name,
                                parameters = emptyList(),
                                returnType = TypeMetadata(element.returnType?.presentableText ?: "void"),
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
                        val analyzeResult = codeAnalysisService.analyzeVariable(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? VariableContext
                        } else {
                            VariableContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                variableName = element.name,
                                variableType = TypeMetadata("Any"),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = element.initializer?.text,
                                scope = VariableScope.LOCAL,
                                isConstant = !element.isVar,
                                isStatic = false,
                                containingClass = null,
                                containingMethod = null
                            )
                        }
                    }

                    is com.intellij.psi.PsiField -> {
                        val analyzeResult = codeAnalysisService.analyzeVariable(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? VariableContext
                        } else {
                            VariableContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                variableName = element.name,
                                variableType = TypeMetadata(element.type.presentableText),
                                modifiers = emptyList(),
                                annotations = emptyList(),
                                initializer = element.initializer?.text,
                                scope = if (element.hasModifierProperty(PsiModifier.STATIC)) VariableScope.STATIC_FIELD else VariableScope.FIELD,
                                isConstant = element.hasModifierProperty(PsiModifier.FINAL),
                                isStatic = element.hasModifierProperty(PsiModifier.STATIC),
                                containingClass = null,
                                containingMethod = null
                            )
                        }
                    }

                    is KtClass -> {
                        val analyzeResult = codeAnalysisService.analyzeClass(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? ClassContext
                        } else {
                            ClassContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                className = element.name,
                                superClass = null,
                                packageName = element.containingKtFile.packageDirective?.fqName?.asString() ?: "",
                                isInterface = element.isInterface(),
                                isAbstract = element.hasModifier(KtTokens.ABSTRACT_KEYWORD),
                                isEnum = element.isEnum()
                            )
                        }
                    }

                    is com.intellij.psi.PsiClass -> {
                        val analyzeResult = codeAnalysisService.analyzeClass(element)
                        if (analyzeResult.isSuccess) {
                            analyzeResult.getOrNull() as? ClassContext
                        } else {
                            ClassContext(
                                language = language,
                                projectMeta = projectMetadata,
                                surroundingContext = surroundingContext,
                                className = element.name,
                                superClass = null,
                                packageName = (element.containingFile as? com.intellij.psi.PsiJavaFile)?.packageName ?: "",
                                isInterface = element.isInterface,
                                isAbstract = element.hasModifierProperty(PsiModifier.ABSTRACT),
                                isEnum = element.isEnum
                            )
                        }
                    }

                    else -> {
                        MethodContext(
                            language = language,
                            projectMeta = projectMetadata,
                            surroundingContext = surroundingContext,
                            methodName = null,
                            parameters = emptyList(),
                            returnType = TypeMetadata("void"),
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
            } catch (t: Throwable) {
                NekoamaLogger.logError(
                    "GenerateCommentUseCase.buildCodeContext",
                    NekoamaError.ParseError.InvalidConfiguration("构建代码上下文失败: ${t.message}"),
                    mapOf("exception" to (t.message ?: "unknown"))
                )
                null
            }
        }
    }
}
