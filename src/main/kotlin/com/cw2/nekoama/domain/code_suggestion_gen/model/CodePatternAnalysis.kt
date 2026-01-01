package com.cw2.nekoama.domain.code_suggestion_gen.model

import kotlinx.serialization.Serializable

/**
 * 代码风格分析
 */
@Serializable
data class CodeStyleAnalysis(
    /** 缩进类型，支持 "spaces"（空格）或 "tabs"（制表符） */
    val indentationType: String = "spaces",
    /** 缩进大小，单位为字符数 */
    val indentationSize: Int = 4,
    /** 大括号样式，支持 "same_line"（同行）或 "new_line"（新行） */
    val bracketStyle: String = "same_line",
    /** 注释风格，如 "javadoc"、"kdoc" 等 */
    val commentStyle: String = "javadoc",
    /** 行长度限制，单位为字符数 */
    val lineLength: Int = 120,
    /** 是否使用大括号，true 表示强制使用，false 表示允许省略单行语句的大括号 */
    val useBraces: Boolean = true
)

/**
 * 命名模式分析
 */
@Serializable
data class NamingPatternAnalysis(
    /** 命名约定类型，枚举值定义了具体使用的命名规范 */
    val conventionType: NamingConvention,
    /** 常用命名前缀列表，如 "m"、"s"、"_" 等 */
    val commonPrefixes: List<String> = emptyList(),
    /** 常用命名后缀列表，如 "Impl"、"Manager"、"Controller" 等 */
    val commonSuffixes: List<String> = emptyList()
)
