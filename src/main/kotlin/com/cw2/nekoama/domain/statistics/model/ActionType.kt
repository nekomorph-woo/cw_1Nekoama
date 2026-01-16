package com.cw2.nekoama.domain.statistics.model

import kotlinx.serialization.Serializable

/**
 * 功能类型枚举
 */
@Serializable
enum class ActionType {
    /** 命名建议 */
    NAMING,

    /** 注释生成 */
    COMMENT,

    /** 自定义生成 */
    CUSTOM_GENERATE
}
