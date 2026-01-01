package com.cw2.nekoama.domain.code_suggestion_gen.model

import kotlinx.serialization.Serializable

/**
 * 注解信息
 */
@Serializable
data class AnnotationMetadata(
    /** 注解名称 */
    val name: String,
    /** 注解完全限定名 */
    val fullQualifiedName: String? = null
)

/**
 * 类型信息
 */
@Serializable
data class TypeMetadata(
    /** 类型名称 */
    val typeName: String,
    /** 类型完全限定名 */
    val fullQualifiedName: String? = null,
    /** 是否可空 */
    val isNullable: Boolean = false,
    /** 是否为基本类型 */
    val isPrimitive: Boolean = false
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
    val annotations: List<AnnotationMetadata> = emptyList(),
    /** 是否有默认值 */
    val hasDefaultValue: Boolean = false,
    /** 默认值表达式 */
    val defaultValue: String? = null
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
    val parameters: List<ParameterMetadata> = emptyList(),
    /** 修饰符列表（如 public、private、static 等） */
    val modifiers: List<String> = emptyList(),
    /** 注解列表 */
    val annotations: List<AnnotationMetadata> = emptyList()
)

/**
 * 字段信息
 */
@Serializable
data class FieldMetadata(
    /** 字段名称 */
    val name: String,
    /** 字段类型 */
    val type: TypeMetadata,
    /** 修饰符列表（如 public、private、static、final 等） */
    val modifiers: List<String> = emptyList(),
    /** 注解列表 */
    val annotations: List<AnnotationMetadata> = emptyList()
)

/**
 * 类信息
 */
@Serializable
data class ClassMetadata(
    /** 类名称 */
    val name: String,
    /** 类完全限定名 */
    val fullQualifiedName: String? = null,
    /** 包名 */
    val packageName: String? = null,
    /** 修饰符列表（如 public、final 等） */
    val modifiers: List<String> = emptyList(),
    /** 是否为接口 */
    val isInterface: Boolean = false,
    /** 是否为抽象类 */
    val isAbstract: Boolean = false,
    /** 是否为枚举类 */
    val isEnum: Boolean = false
)

/**
 * 项目信息
 */
@Serializable
data class ProjectMetadata(
    /** 项目名称 */
    val projectName: String
)