package com.cw2.nekoama.ai.model.dependency

import kotlinx.serialization.Serializable

/**
 * 代码依赖分析的核心数据模型
 * 严格按照 docs/Nekoama新功能-代码结构梳理和质量分析-方案.md 中的JSON Schema设计
 */
@Serializable
data class DependencyAnalysisResult(
    /**
     * 分析元数据
     */
    val metadata: AnalysisMetadata,

    /**
     * 包信息
     */
    val packages: List<PackageInfo>,

    /**
     * 类信息
     */
    val classes: List<ClassInfo>,

    /**
     * 方法信息
     */
    val methods: List<MethodInfo>,

    /**
     * 字段信息
     */
    val fields: List<FieldInfo>,

    /**
     * POJO使用情况
     */
    val pojos: List<PojoUsage>,

    /**
     * 调用关系图
     */
    val callGraph: CallGraph,

    /**
     * 场景定义
     */
    val sceneDefinitions: List<SceneDefinition>,

    /**
     * 项目基本信息
     */
    val projectInfo: ProjectInfo,

    /**
     * 包级依赖关系
     */
    val packageDependencies: List<PackageDependency>,

    /**
     * 类级依赖关系
     */
    val classDependencies: List<ClassDependency>,

    /**
     * 方法级调用关系
     */
    val methodCalls: List<MethodCall>,

    /**
     * 业务场景入口点
     */
    val businessEntryPoints: List<BusinessEntryPoint>,

    /**
     * 复杂度指标
     */
    val complexityMetrics: Map<String, ClassComplexityMetrics>,

    /**
     * 代码坏味道检测结果
     */
    val codeSmells: List<CodeSmell>,

    /**
     * 分析配置
     */
    val analysisConfig: AnalysisConfig,

    /**
     * 分析时间戳
     */
    val timestamp: Long
)

/**
 * 项目基本信息
 */
@Serializable
data class ProjectInfo(
    val name: String,
    val rootPackage: String,
    val totalClasses: Int,
    val totalPackages: Int,
    val totalMethods: Int
)

/**
 * 包级依赖关系
 */
@Serializable
data class PackageDependency(
    val packageName: String,
    val dependencies: List<String>,
    val dependents: List<String>,
    val dependencyCount: Int,
    val cycles: List<List<String>> // 循环依赖
)

/**
 * 类级依赖关系
 */
@Serializable
data class ClassDependency(
    val className: String,
    val packageName: String,
    val superClass: String?,
    val interfaces: List<String>,
    val dependencies: List<ClassReference>,
    val dependents: List<String>,
    val dependencyCount: Int,
    val isPojo: Boolean,
    val isController: Boolean,
    val isService: Boolean,
    val isRepository: Boolean
)

/**
 * 类引用信息
 */
@Serializable
data class ClassReference(
    val className: String,
    val referenceType: ReferenceType,
    val location: SourceLocation
)

/**
 * 引用类型枚举
 */
@Serializable
enum class ReferenceType {
    INHERITANCE,      // 继承
    IMPLEMENTATION,   // 实现
    COMPOSITION,      // 组合
    AGGREGATION,      // 聚合
    ASSOCIATION,      // 关联
    DEPENDENCY,       // 依赖
    ANNOTATION        // 注解
}

/**
 * 方法调用关系
 */
@Serializable
data class MethodCall(
    val callerClass: String,
    val callerMethod: String,
    val calleeClass: String,
    val calleeMethod: String,
    val callType: CallType,
    val location: SourceLocation,
    val callDepth: Int
)

/**
 * 调用类型枚举
 */
@Serializable
enum class CallType {
    DIRECT,          // 直接调用
    INDIRECT,        // 间接调用
    REFLECTION,      // 反射调用
    LAMBDA,          // Lambda表达式
    STREAM           // Stream API调用
}

/**
 * 业务场景入口点
 */
@Serializable
data class BusinessEntryPoint(
    val className: String,
    val methodName: String,
    val entryType: EntryType,
    val annotations: List<String>,
    val businessScenario: String,
    val parameters: List<ParameterInfo>,
    val httpMapping: String? = null
)

/**
 * 入口点类型枚举
 */
@Serializable
enum class EntryType {
    CONTROLLER,      // HTTP控制器
    SERVICE,         // 服务入口
    SCHEDULED,       // 定时任务
    EVENT_LISTENER,  // 事件监听器
    MESSAGE_CONSUMER, // 消息消费者
    MAIN             // 主程序入口
}

/**
 * 参数信息
 */
@Serializable
data class ParameterInfo(
    val name: String,
    val type: String,
    val annotations: List<String>
)

/**
 * 类复杂度指标
 */
@Serializable
data class ClassComplexityMetrics(
    val className: String,
    val cyclomaticComplexity: Int,        // 圈复杂度
    val cognitiveComplexity: Int,         // 认知复杂度
    val nestingDepth: Int,                // 最大嵌套深度
    val methodCount: Int,                 // 方法数量
    val fieldCount: Int,                  // 字段数量
    val lineOfCode: Int,                  // 代码行数
    val parameterCount: Int,              // 参数总数
    val longestMethod: MethodComplexityInfo, // 最长方法
    val mostComplexMethod: MethodComplexityInfo, // 最复杂方法
    val couplingMetrics: CouplingMetrics  // 耦合度指标
)

/**
 * 方法复杂度信息
 */
@Serializable
data class MethodComplexityInfo(
    val methodName: String,
    val complexity: Int,
    val lineOfCode: Int,
    val parameterCount: Int,
    val nestingDepth: Int
)

/**
 * 耦合度指标
 */
@Serializable
data class CouplingMetrics(
    val afferentCoupling: Int,   // 传入耦合(Ca)
    val efferentCoupling: Int,   // 传出耦合(Ce)
    val instability: Double,     // 不稳定性 I = Ce / (Ca + Ce)
    val abstractness: Double,    // 抽象性 A = Na / Nc
    val distance: Double         // 距离 D = |A + I - 1|
)

/**
 * 代码坏味道
 */
@Serializable
data class CodeSmell(
    val type: CodeSmellType,
    val severity: Severity,
    val className: String,
    val methodName: String?,
    val description: String,
    val location: SourceLocation,
    val metrics: Map<String, Int>
)

/**
 * 代码坏味道类型
 */
@Serializable
enum class CodeSmellType {
    LONG_METHOD,              // 长方法
    LONG_PARAMETER_LIST,      // 长参数列表
    LARGE_CLASS,              // 大类
    DEEP_NESTING,             // 深度嵌套
    DUPLICATE_CODE,           // 重复代码
    GOD_CLASS,                // 上帝类
    DATA_CLASS,               // 数据类
    FEATURE_ENVY,             // 特性嫉妒
    INAPPROPRIATE_INTIMACY,    // 不适当的亲密
    CYCLIC_DEPENDENCY,        // 循环依赖
    SPAGHETTI_CODE,          // 意大利面条代码
    SHOTGUN_SURGERY,         // 霰弹式手术
    MAGIC_NUMBERS,           // 魔法数字
    LONG_EXPRESSIONS,         // 长表达式
    MULTIPLE_RETURNS,        // 多个return语句
    BOOLEAN_PARAMETER_SMELL, // 布尔参数过多
    HIGH_COMPLEXITY_METHOD,  // 高复杂度方法
    POOR_COHESION,           // 低内聚度
    PRIMITIVE_OBSESSION,     // 基本类型偏执
    COMMENTED_OUT_CODE,      // 注释掉的代码
    SWITCH_STATEMENTS,       // 过多的switch语句
    FEATURE_ENVY_ENHANCED,   // 增强版特性嫉妒
    DATA_CLUMPS,             // 数据簇
    BLOB_CLASS,              // 大对象类
    CONTROLLER_OVERLOAD,     // 控制器过载
    NULL_CHECKS,             // 过多的空值检查
    EXCEPTION_HANDLING,      // 异常处理问题
    UNUSED_IMPORTS,          // 未使用的导入
    DUPLICATE_CONDITIONALS,  // 重复的条件
    DEAD_CODE,               // 死代码
    TRUST_BOUNDARY_VIOLATION // 信任边界违规
}

/**
 * 严重程度
 */
@Serializable
enum class Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

/**
 * 源码位置
 */
@Serializable
data class SourceLocation(
    val filePath: String,
    val lineNumber: Int,
    val columnNumber: Int
)

/**
 * 分析配置
 */
@Serializable
data class AnalysisConfig(
    val maxDepth: Int,
    val excludePackages: List<String>,
    val includeTestClasses: Boolean,
    val complexityThresholds: ComplexityThresholds,
    val includeExternalDependencies: Boolean = false,
    val excludedFrameworkPackages: Set<String> = setOf(
        "java",
        "javax",
        "kotlin",
        "org.springframework",
        "org.apache",
        "com.fasterxml",
        "org.slf4j",
        "lombok",
        "org.junit",
        "org.mockito"
    )
)

/**
 * 复杂度阈值
 */
@Serializable
data class ComplexityThresholds(
    val cyclomaticComplexity: Int,
    val cognitiveComplexity: Int,
    val nestingDepth: Int,
    val methodLength: Int,
    val classLength: Int,
    val parameterCount: Int
)

/**
 * 入口点复杂度信息
 */
@Serializable
data class EntryPointComplexity(
    val name: String,
    val businessScenario: String,
    val parameterCount: Int,
    val hasComplexAnnotations: Boolean,
    val hasPathVariable: Boolean,
    val hasRequestBody: Boolean
)

// ==================== 新增的数据模型 ====================

/**
 * 分析元数据
 */
@Serializable
data class AnalysisMetadata(
    val projectName: String,
    val moduleName: String,
    val analysisTime: String,
    val scope: AnalysisScope,
    val statistics: AnalysisStatistics
)

/**
 * 分析范围
 */
@Serializable
data class AnalysisScope(
    val rootPackage: String,
    val includedPackages: List<String>,
    val excludedPackages: List<String>,
    val maxDepth: Int
)

/**
 * 分析统计
 */
@Serializable
data class AnalysisStatistics(
    val totalPackages: Int,
    val totalClasses: Int,
    val totalMethods: Int,
    val totalCallEdges: Int
)

/**
 * 包信息
 */
@Serializable
data class PackageInfo(
    val id: String,
    val name: String,
    val fullName: String,
    val parentPackage: String,
    val level: Int,
    val classCount: Int,
    val metrics: PackageMetrics
)

/**
 * 包级指标
 */
@Serializable
data class PackageMetrics(
    val fanIn: Int,
    val fanOut: Int,
    val instability: Double
)

/**
 * 类信息
 */
@Serializable
data class ClassInfo(
    val id: String,
    val name: String,
    val qualifiedName: String,
    val packageId: String,
    val type: ClassType,
    val modifiers: List<String>,
    val isTest: Boolean,
    val sourceFile: String,
    val annotations: List<String>,
    val superClass: String?,
    val interfaces: List<String>,
    val metrics: ClassDetailedMetrics
)

/**
 * 类类型
 */
@Serializable
enum class ClassType {
    CLASS,
    INTERFACE,
    ABSTRACT_CLASS,
    ENUM,
    RECORD
}

/**
 * 类详细指标
 */
@Serializable
data class ClassDetailedMetrics(
    val methodCount: Int,
    val fieldCount: Int,
    val linesOfCode: Int,
    val fanIn: Int,
    val fanOut: Int,
    val coupling: Int,
    val cohesion: Double,
    val codeSmells: List<MethodCodeSmell>,
    val complexityScore: Int,
    val refactoringPriority: RefactoringPriority,
    val location: SourceLocation,
    val usedTypes: List<String>,
    val tags: MethodTags
)

/**
 * 方法代码坏味道
 */
@Serializable
data class MethodCodeSmell(
    val type: String,
    val severity: String,
    val description: String,
    val suggestion: String
)

/**
 * 重构优先级
 */
@Serializable
data class RefactoringPriority(
    val level: String,
    val reason: String,
    val riskLevel: String
)

/**
 * 方法标签
 */
@Serializable
data class MethodTags(
    val isEntryPoint: Boolean,
    val isPublicApi: Boolean,
    val isDeprecated: Boolean,
    val sceneNames: List<String>
)

/**
 * 方法信息
 */
@Serializable
data class MethodInfo(
    val id: String,
    val name: String,
    val className: String,
    val classId: String,
    val packageId: String,
    val signature: String,
    val qualifiedSignature: String,
    val modifiers: List<String>,
    val isStatic: Boolean,
    val isConstructor: Boolean,
    val isAbstract: Boolean,
    val annotations: List<String>,
    val parameters: List<ParameterDetail>,
    val returnType: String,
    val throwsExceptions: List<String>,
    val metrics: MethodMetrics,
    val location: SourceLocation,
    val usedTypes: List<String>,
    val tags: MethodTags
)

/**
 * 参数详情
 */
@Serializable
data class ParameterDetail(
    val name: String,
    val type: String,
    val annotations: List<String>
)

/**
 * 方法指标
 */
@Serializable
data class MethodMetrics(
    val linesOfCode: Int,
    val cyclomaticComplexity: Int,
    val cognitiveComplexity: Int,
    val nestingDepth: Int,
    val fanIn: Int,
    val fanOut: Int,
    val parameterCount: Int,
    val maxCallDepth: Int,
    val localVariableCount: Int,
    val magicNumberCount: Int,
    val longLineCount: Int,
    val returnStatementCount: Int,
    val booleanParameterCount: Int,
    val codeSmells: List<MethodCodeSmell>,
    val complexityScore: Int,
    val refactoringPriority: RefactoringPriority
)

/**
 * 字段信息
 */
@Serializable
data class FieldInfo(
    val id: String,
    val name: String,
    val classId: String,
    val type: String,
    val modifiers: List<String>,
    val isStatic: Boolean,
    val isFinal: Boolean,
    val annotations: List<String>,
    val initializer: String?
)

/**
 * POJO使用情况
 */
@Serializable
data class PojoUsage(
    val id: String,
    val name: String,
    val qualifiedName: String,
    val packageId: String,
    val category: PojoCategory,
    val usage: PojoUsageStats,
    val fields: List<PojoField>,
    val crossBoundaryUsage: List<CrossBoundaryUsage>
)

/**
 * POJO类别
 */
@Serializable
enum class PojoCategory {
    DTO,
    ENTITY,
    VO,
    DO,
    DOMAIN,
    CONFIG
}

/**
 * POJO使用统计
 */
@Serializable
data class PojoUsageStats(
    val usedByMethodsCount: Int,
    val usedByClassesCount: Int,
    val usedByPackagesCount: Int,
    val usageTypes: UsageTypes
)

/**
 * 使用类型
 */
@Serializable
data class UsageTypes(
    val asParameter: Int,
    val asReturnType: Int,
    val asFieldType: Int,
    val asLocalVariable: Int
)

/**
 * POJO字段
 */
@Serializable
data class PojoField(
    val name: String,
    val type: String
)

/**
 * 跨边界使用
 */
@Serializable
data class CrossBoundaryUsage(
    val fromPackage: String,
    val toPackage: String,
    val usageCount: Int,
    val isExpected: Boolean
)

/**
 * 调用关系图
 */
@Serializable
data class CallGraph(
    val edges: List<CallEdge>
)

/**
 * 调用边
 */
@Serializable
data class CallEdge(
    val id: String,
    val source: String,
    val target: String,
    val type: CallEdgeType,
    val callContext: CallContext,
    val depth: Int,
    val weight: Int
)

/**
 * 调用边类型
 */
@Serializable
enum class CallEdgeType {
    METHOD_CALL,
    CONSTRUCTOR_CALL,
    SUPER_CALL
}

/**
 * 调用上下文
 */
@Serializable
data class CallContext(
    val callCount: Int,
    val callLocations: List<CallLocation>
)

/**
 * 调用位置
 */
@Serializable
data class CallLocation(
    val line: Int,
    val column: Int,
    val context: String
)

/**
 * 场景定义
 */
@Serializable
data class SceneDefinition(
    val id: String,
    val name: String,
    val description: String,
    val entryMethods: List<String>,
    val category: SceneCategory,
    val tags: List<String>,
    val coverage: SceneCoverage
)

/**
 * 场景类别
 */
@Serializable
enum class SceneCategory {
    USER_TRIGGER,
    SCHEDULED,
    EVENT_DRIVEN,
    API
}

/**
 * 场景覆盖范围
 */
@Serializable
data class SceneCoverage(
    val methodCount: Int,
    val classCount: Int,
    val packageCount: Int,
    val maxDepth: Int
)