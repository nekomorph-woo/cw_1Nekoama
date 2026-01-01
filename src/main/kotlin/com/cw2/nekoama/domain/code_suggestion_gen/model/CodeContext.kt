package com.cw2.nekoama.domain.code_suggestion_gen.model

import kotlinx.serialization.Serializable

// ============================================================================
// 周围上下文
// ============================================================================

/**
 * 周围上下文环境信息
 *
 * 描述代码元素周围的环境信息，包括相邻代码、导入语句、项目命名规范等。
 */
@Serializable
data class SurroundingContext(
    /**
     * 前置代码片段（当前元素前n行代码）
     */
    val precedingCode: List<String>,

    /**
     * 后续代码片段（当前元素后n行代码）
     */
    val followingCode: List<String>,

    /**
     * 导入语句列表
     */
    val imports: List<String>,

    /**
     * 包声明
     */
    val packageDeclaration: String? = null,

    /**
     * 文件级注释
     */
    val fileComments: List<String>,

    /**
     * 相邻的同类型元素（如同一类中的其他方法）
     */
    val siblingElements: List<String>,

    /**
     * 项目命名模式分析
     */
    val namingPatterns: NamingPatternAnalysis? = null,

    /**
     * 代码风格分析
     */
    val codeStyleAnalysis: CodeStyleAnalysis? = null
)

// ============================================================================
// 代码上下文基类
// ============================================================================

/**
 * 代码上下文基础类
 *
 * 定义了所有代码元素分析的基础信息结构，包含语言类型、项目信息和周围环境等通用属性。
 */
@Serializable
sealed class CodeContext {
    /**
     * 编程语言类型
     */
    abstract val language: ProgrammingLanguage

    /**
     * 项目信息
     */
    abstract val projectMeta: ProjectMetadata

    /**
     * 周围上下文环境
     */
    abstract val surroundingContext: SurroundingContext

    /**
     * 用户意图描述（用于特殊符号生成场景）
     */
    abstract val userIntent: String?

    /**
     * 代码元素类型
     */
    abstract val elementType: CodeElementType
}

// ============================================================================
// 具体上下文实现
// ============================================================================

/**
 * 类上下文信息
 *
 * 包含类的继承关系、实现接口、成员信息等，用于生成合适的类命名建议和类级别注释。
 */
@Serializable
data class ClassContext(
    override val language: ProgrammingLanguage,
    override val projectMeta: ProjectMetadata,
    override val surroundingContext: SurroundingContext,
    override val userIntent: String? = null,

    /**
     * 类名称（当前名称或待生成）
     */
    val className: String? = null,

    /**
     * 父类信息
     */
    val superClass: TypeMetadata? = null,

    /**
     * 实现的接口列表
     */
    val interfaces: List<TypeMetadata>,

    /**
     * 类修饰符
     */
    val modifiers: List<String>,

    /**
     * 类注解信息
     */
    val annotations: List<AnnotationMetadata>,

    /**
     * 类成员字段
     */
    val fields: List<FieldMetadata>,

    /**
     * 类方法列表
     */
    val methods: List<MethodMetadata>,

    /**
     * 内部类列表
     */
    val innerClasses: List<ClassMetadata>,

    /**
     * 是否为接口
     */
    val isInterface: Boolean = false,

    /**
     * 是否为抽象类
     */
    val isAbstract: Boolean = false,

    /**
     * 是否为枚举类
     */
    val isEnum: Boolean = false,

    /**
     * 包名
     */
    val packageName: String
) : CodeContext() {
    override val elementType = CodeElementType.CLASS
}

/**
 * 方法上下文信息
 *
 * 包含方法的完整签名信息、参数详情、返回值类型等，用于生成精确的方法命名建议和注释。
 */
@Serializable
data class MethodContext(
    override val language: ProgrammingLanguage,
    override val projectMeta: ProjectMetadata,
    override val surroundingContext: SurroundingContext,
    override val userIntent: String? = null,

    /**
     * 方法名称（当前名称或待生成）
     */
    val methodName: String? = null,

    /**
     * 方法参数列表
     */
    val parameters: List<ParameterMetadata>,

    /**
     * 返回值类型信息
     */
    val returnType: TypeMetadata,

    /**
     * 方法修饰符（public、private、static等）
     */
    val modifiers: List<String>,

    /**
     * 方法注解信息
     */
    val annotations: List<AnnotationMetadata>,

    /**
     * 抛出的异常类型
     */
    val exceptions: List<TypeMetadata>,

    /**
     * 方法体代码片段（用于理解方法逻辑）
     */
    val methodBody: String? = null,

    /**
     * 是否为构造方法
     */
    val isConstructor: Boolean = false,

    /**
     * 是否为抽象方法
     */
    val isAbstract: Boolean = false,

    /**
     * 所属类的信息
     */
    val containingClass: ClassMetadata? = null
) : CodeContext() {
    override val elementType = CodeElementType.METHOD
}

/**
 * 变量上下文信息
 *
 * 包含变量的类型、作用域、初始化信息等，用于生成准确的变量命名建议。
 */
@Serializable
data class VariableContext(
    override val language: ProgrammingLanguage,
    override val projectMeta: ProjectMetadata,
    override val surroundingContext: SurroundingContext,
    override val userIntent: String? = null,

    /**
     * 变量名称（当前名称或待生成）
     */
    val variableName: String? = null,

    /**
     * 变量类型信息
     */
    val variableType: TypeMetadata,

    /**
     * 变量修饰符
     */
    val modifiers: List<String>,

    /**
     * 变量注解
     */
    val annotations: List<AnnotationMetadata>,

    /**
     * 初始化表达式
     */
    val initializer: String? = null,

    /**
     * 变量作用域类型
     */
    val scope: VariableScope,

    /**
     * 是否为常量
     */
    val isConstant: Boolean = false,

    /**
     * 是否为静态变量
     */
    val isStatic: Boolean = false,

    /**
     * 所属类信息（如果是类成员变量）
     */
    val containingClass: ClassMetadata? = null,

    /**
     * 所属方法信息（如果是局部变量或参数）
     */
    val containingMethod: MethodMetadata? = null
) : CodeContext() {
    override val elementType = CodeElementType.VARIABLE
}
