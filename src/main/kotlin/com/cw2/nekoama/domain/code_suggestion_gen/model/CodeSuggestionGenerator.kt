package com.cw2.nekoama.domain.code_suggestion_gen.model

import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config.GeneratorConfig
import com.cw2.nekoama.shared.model.NekoamaResult

/**
 * 代码建议生成器抽象接口
 *
 * 定义了代码建议生成器必须实现的核心方法，支持代码命名建议、注释生成和自定义生成功能。
 * 所有实现类都应该支持异步操作，并且具备良好的错误处理能力。
 *
 * 这是一个业务领域的抽象，具体实现可以使用 AI 服务（如 OpenAI）、规则引擎或其他方式。
 */
interface CodeSuggestionGenerator {

    /**
     * 获取生成器名称
     */
    val name: String

    /**
     * 获取生成器配置
     */
    val config: GeneratorConfig

    /**
     * 生成代码命名建议
     *
     * @param context 代码上下文信息，包含待命名元素的详细信息
     * @return 包含多个命名建议的结果，每个建议都包含名称和描述
     */
    suspend fun generateNaming(context: CodeContext): NekoamaResult<List<NamingSuggestion>>

    /**
     * 生成代码注释
     *
     * @param context 代码上下文信息，包含需要注释的代码元素信息
     * @return 包含生成注释内容的结果
     */
    suspend fun generateComment(context: CodeContext): NekoamaResult<CommentSuggestion>

    /**
     * 自定义生成
     *
     * @param prompt 用户自定义的提示内容
     * @param context 可选的代码上下文信息，为null时仅使用prompt
     * @return 包含生成内容及其元数据（如 Token 使用量）的结果
     */
    suspend fun generateCustom(prompt: String, context: CodeContext? = null): NekoamaResult<com.cw2.nekoama.domain.code_suggestion_gen.model.CustomSuggestion>

    /**
     * 检查服务是否可用
     *
     * @return 服务可用性检查结果
     */
    suspend fun isAvailable(): NekoamaResult<Boolean>

    /**
     * 获取服务状态信息
     *
     * @return 服务状态详情
     */
    suspend fun getStatus(): NekoamaResult<GeneratorStatus>
}
