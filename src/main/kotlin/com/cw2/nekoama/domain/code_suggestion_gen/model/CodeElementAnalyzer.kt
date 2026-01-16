package com.cw2.nekoama.domain.code_suggestion_gen.model

import com.intellij.psi.PsiElement
import com.cw2.nekoama.shared.model.NekoamaResult

/**
 * 代码元素分析器接口（防腐层接口）
 *
 * 定义了领域层需要的代码分析能力，隔离基础设施层的 PSI 实现细节。
 * 遵循依赖倒置原则：领域层定义接口，基础设施层提供实现。
 */
interface CodeElementAnalyzer {

    /**
     * 分析方法信息
     */
    fun analyzeMethod(method: PsiElement): NekoamaResult<MethodContext>

    /**
     * 分析类信息
     */
    fun analyzeClass(clazz: PsiElement): NekoamaResult<ClassContext>

    /**
     * 分析变量信息
     */
    fun analyzeVariable(variable: PsiElement): NekoamaResult<VariableContext>

    /**
     * 提取周围代码上下文
     */
    fun extractSurroundingContext(element: PsiElement, radius: Int = 5): NekoamaResult<SurroundingContext>

    /**
     * 检测编程语言
     */
    fun detectLanguage(element: PsiElement): ProgrammingLanguage

    /**
     * 获取项目元数据
     */
    fun getProjectMetadata(): ProjectMetadata
}
