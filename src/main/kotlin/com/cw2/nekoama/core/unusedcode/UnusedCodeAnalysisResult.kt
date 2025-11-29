package com.cw2.nekoama.core.unusedcode

/**
 * 未使用代码分析结果
 */
data class UnusedCodeAnalysisResult(
    val unusedClasses: List<UnusedClass>,
    val unusedMethods: List<UnusedMethod>,
    val unusedFields: List<UnusedField>,
    val totalClasses: Int,
    val totalMethods: Int,
    val totalFields: Int
)

/**
 * 未使用的类
 */
data class UnusedClass(
    val className: String,
    val location: String,
    val filePath: String,
    val lineCount: Int
)

/**
 * 未使用的方法
 */
data class UnusedMethod(
    val className: String,
    val methodName: String,
    val location: String,
    val filePath: String,
    val lineNumber: Int,
    val complexity: Int
)

/**
 * 未使用的属性
 */
data class UnusedField(
    val className: String,
    val fieldName: String,
    val location: String,
    val filePath: String,
    val lineNumber: Int,
    val fieldType: String
)