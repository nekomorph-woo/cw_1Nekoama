package com.cw2.nekoama.domain.code_suggestion_gen.model

import kotlinx.serialization.Serializable

/**
 * 编程语言类型枚举
 */
@Serializable
enum class ProgrammingLanguage {
    JAVA, KOTLIN, OTHER
}

/**
 * 代码元素类型
 */
@Serializable
enum class CodeElementType {
    METHOD, CLASS, VARIABLE, PARAMETER, FIELD, PACKAGE, MODULE, INTERFACE, ENUM, ANNOTATION
}

/**
 * 变量作用域类型
 */
@Serializable
enum class VariableScope {
    LOCAL, PARAMETER, FIELD, STATIC_FIELD, GLOBAL
}

/**
 * 命名约定类型
 */
@Serializable
enum class NamingConvention {
    CAMEL_CASE, PASCAL_CASE, SNAKE_CASE, KEBAB_CASE, UPPER_SNAKE_CASE, MIXED
}
