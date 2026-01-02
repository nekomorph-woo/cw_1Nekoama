package com.cw2.nekoama.domain.code_suggestion_gen.model

import kotlinx.serialization.Serializable

/**
 * 注释格式类型
 */
@Serializable
enum class CommentFormat {
    JAVADOC, KDOC, JSDOC, SINGLE_LINE, MULTI_LINE, PLAIN
}

/**
 * 注释语言
 */
@Serializable
enum class CommentLanguage {
    CHINESE, ENGLISH, AUTO
}

/**
 * 注释覆盖方面
 */
@Serializable
enum class CommentAspect {
    FUNCTIONALITY,      // 功能描述
    PARAMETERS,         // 参数说明
    RETURN_VALUE,       // 返回值
    EXCEPTIONS,         // 异常情况
    EXAMPLES,           // 使用示例
    SIDE_EFFECTS,       // 副作用
    COMPLEXITY,         // 复杂度说明
    THREAD_SAFETY,      // 线程安全性
    DEPRECATED,         // 废弃说明
    SEE_ALSO           // 相关引用
}
