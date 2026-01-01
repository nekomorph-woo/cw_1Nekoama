package com.cw2.nekoama.domain.code_suggestion_gen.model

import kotlinx.serialization.Serializable

/**
 * 注解信息
 */
@Serializable
data class AnnotationMetadata(
    /** 注解名称 */
    val name: String
)

/**
 * 类型信息
 */
@Serializable
data class TypeMetadata(
    /** 类型名称 */
    val typeName: String
)

/**
 * 参数信息
 */
@Serializable
data class ParameterMetadata(
    /** 参数名称 */
    val name: String,
    /** 参数类型 */
    val type: TypeMetadata,
    /** 注解列表 */
    val annotations: List<AnnotationMetadata> = emptyList()
)

/**
 * 方法信息
 */
@Serializable
data class MethodMetadata(
    /** 方法名称 */
    val name: String,
    /** 返回类型 */
    val returnType: TypeMetadata,
    /** 参数列表 */
    val parameters: List<ParameterMetadata> = emptyList()
)

/**
 * 字段信息
 */
@Serializable
data class FieldMetadata(
    /** 字段名称 */
    val name: String,
    /** 字段类型 */
    val type: TypeMetadata
)

/**
 * 类信息
 */
@Serializable
data class ClassMetadata(
    /** 类名称 */
    val name: String,
    /** 包名 */
    val packageName: String? = null
)

/**
 * 项目信息
 */
@Serializable
data class ProjectMetadata(
    /** 项目名称 */
    val projectName: String
)
