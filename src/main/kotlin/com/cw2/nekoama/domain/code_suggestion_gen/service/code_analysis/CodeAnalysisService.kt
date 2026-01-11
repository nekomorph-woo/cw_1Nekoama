package com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis

import com.cw2.nekoama.domain.code_suggestion_gen.model.*
import com.cw2.nekoama.shared.model.NekoamaResult
import com.intellij.psi.PsiElement
import com.intellij.openapi.project.Project

/**
 * 代码分析应用服务
 *
 * 职责：业务流程编排，协调代码分析能力。
 * 依赖 CodeElementAnalyzer 接口，遵循依赖倒置原则。
 */
class CodeAnalysisService(
    private val analyzer: CodeElementAnalyzer
) {

    /**
     * 分析方法信息
     */
    fun analyzeMethod(method: PsiElement): NekoamaResult<MethodContext> {
        return analyzer.analyzeMethod(method)
    }

    /**
     * 分析类信息
     */
    fun analyzeClass(clazz: PsiElement): NekoamaResult<ClassContext> {
        return analyzer.analyzeClass(clazz)
    }

    /**
     * 分析变量信息
     */
    fun analyzeVariable(variable: PsiElement): NekoamaResult<VariableContext> {
        return analyzer.analyzeVariable(variable)
    }

    /**
     * 提取周围代码上下文
     */
    fun extractSurroundingContext(element: PsiElement, radius: Int = 5): NekoamaResult<SurroundingContext> {
        return analyzer.extractSurroundingContext(element, radius)
    }

    /**
     * 检测编程语言
     */
    fun detectLanguage(element: PsiElement): ProgrammingLanguage {
        return analyzer.detectLanguage(element)
    }

    /**
     * 获取项目元数据
     */
    fun getProjectMetadata(): ProjectMetadata {
        return analyzer.getProjectMetadata()
    }
}
