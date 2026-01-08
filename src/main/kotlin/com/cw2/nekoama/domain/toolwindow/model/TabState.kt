package com.cw2.nekoama.domain.toolwindow.model

import com.cw2.nekoama.shared.model.Result

/**
 * Tab状态数据接口
 *
 * 设计理念：
 * - 这是一个通用能力接口，不绑定具体业务
 * - 每个Tab实现自己的State类
 * - 框架只负责存储，不关心State的具体内容
 *
 * 使用示例：
 * ```kotlin
 * // AI对话Tab的状态
 * data class AIDialogTabState(
 *     val conversationHistory: List<ChatMessage>,
 *     val selectedCodeFragments: List<CodeFragment>
 * ) : TabState
 *
 * // 代码分析Tab的状态
 * data class CodeAnalysisTabState(
 *     val analysisResults: Map<String, CodeSmell>,
 *     val selectedFile: String?
 * ) : TabState
 * ```
 *
 * @property version 状态版本号（用于迁移和兼容性检查）
 */
interface TabState {
    val version: Int
        get() = 1

    /**
     * 验证状态是否有效
     */
    fun validate(): Result<Unit>
}
