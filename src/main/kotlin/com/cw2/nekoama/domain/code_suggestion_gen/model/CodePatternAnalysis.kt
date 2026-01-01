package com.cw2.nekoama.domain.code_suggestion_gen.model

import kotlinx.serialization.Serializable

/**
 * 命名模式分析
 */
@Serializable
data class NamingPatternAnalysis(
    /** 命名约定类型，枚举值定义了具体使用的命名规范 */
    val conventionType: NamingConvention
)
