package com.cw2.nekoama.ai.model.dependency

import kotlinx.serialization.Serializable

/**
 * 代码分析指标统计
 * 用于生成分析报告和统计图表
 */
@Serializable
data class AnalysisMetrics(
    /**
     * 项目规模指标
     */
    val projectScale: ProjectScaleMetrics,

    /**
     * 复杂度分布统计
     */
    val complexityDistribution: ComplexityDistribution,

    /**
     * 代码质量指标
     */
    val qualityMetrics: QualityMetrics,

    /**
     * 架构健康度指标
     */
    val architectureHealth: ArchitectureHealthMetrics,

    /**
     * 重构建议优先级
     */
    val refactoringPriorities: List<RefactoringPriority>
)

/**
 * 项目规模指标
 */
@Serializable
data class ProjectScaleMetrics(
    val totalPackages: Int,
    val totalClasses: Int,
    val totalInterfaces: Int,
    val totalEnums: Int,
    val totalAnnotations: Int,
    val totalMethods: Int,
    val totalFields: Int,
    val totalLinesOfCode: Int,
    val averageClassSize: Double,
    val averageMethodSize: Double,
    val packageDistribution: Map<String, Int> // 包名 -> 类数量
)

/**
 * 复杂度分布统计
 */
@Serializable
data class ComplexityDistribution(
    val cyclomaticComplexity: ComplexityStats,
    val cognitiveComplexity: ComplexityStats,
    val nestingDepth: ComplexityStats,
    val methodLength: ComplexityStats,
    val classLength: ComplexityStats,
    val parameterCount: ComplexityStats
)

/**
 * 复杂度统计信息
 */
@Serializable
data class ComplexityStats(
    val minimum: Int,
    val maximum: Int,
    val average: Double,
    val median: Double,
    val percentile95: Int,
    val standardDeviation: Double,
    val distribution: Map<String, Int> // 范围 -> 数量 (如 "1-5": 100, "6-10": 50)
)

/**
 * 代码质量指标
 */
@Serializable
data class QualityMetrics(
    val codeSmellCount: Map<CodeSmellType, Int>,
    val codeSmellBySeverity: Map<Severity, Int>,
    val codeSmellByPackage: Map<String, Int>,
    val testCoverage: TestCoverageMetrics,
    val duplicatedCodeStats: DuplicatedCodeStats,
    val technicalDebt: TechnicalDebtMetrics
)

/**
 * 测试覆盖率指标
 */
@Serializable
data class TestCoverageMetrics(
    val classCoverage: Double,
    val methodCoverage: Double,
    val lineCoverage: Double,
    val branchCoverage: Double,
    val totalTestClasses: Int,
    val totalTestMethods: Int
)

/**
 * 重复代码统计
 */
@Serializable
data class DuplicatedCodeStats(
    val duplicatedBlocks: Int,
    val duplicatedLines: Int,
    val duplicationPercentage: Double,
    val largestDuplicateBlock: Int
)

/**
 * 技术债务指标
 */
@Serializable
data class TechnicalDebtMetrics(
    val totalDebtHours: Double,
    val debtRatio: Double, // 债务时间 / 代码行数
    val debtByCategory: Map<String, Double>,
    val priorityDebtHours: Double // 高优先级债务时间
)

/**
 * 架构健康度指标
 */
@Serializable
data class ArchitectureHealthMetrics(
    val couplingMetrics: CouplingHealthMetrics,
    val cohesionMetrics: CohesionHealthMetrics,
    val stabilityMetrics: StabilityHealthMetrics,
    val abstractionMetrics: AbstractionHealthMetrics,
    val dependencyMetrics: DependencyHealthMetrics
)

/**
 * 耦合健康度指标
 */
@Serializable
data class CouplingHealthMetrics(
    val averageAfferentCoupling: Double,
    val averageEfferentCoupling: Double,
    val highCouplingClasses: Int,
    val couplingViolations: List<CouplingViolation>
)

/**
 * 耦合违规信息
 */
@Serializable
data class CouplingViolation(
    val className: String,
    val violationType: String,
    val actualValue: Double,
    val threshold: Double
)

/**
 * 内聚健康度指标
 */
@Serializable
data class CohesionHealthMetrics(
    val averageLcom: Double, // LCOM4 内聚度
    val lowCohesionClasses: Int,
    val singleResponsibilityViolations: Int
)

/**
 * 稳定性指标
 */
@Serializable
data class StabilityHealthMetrics(
    val averageInstability: Double,
    val stableAbstractions: Int,
    val unstableConcreteClasses: Int,
    val zonePain: List<ZonePainClass>
)

/**
 * 架构疼痛区域
 */
@Serializable
data class ZonePainClass(
    val className: String,
    val instability: Double,
    val abstractness: Double,
    val distance: Double
)

/**
 * 抽象健康度指标
 */
@Serializable
data class AbstractionHealthMetrics(
    val abstractionRatio: Double,
    val interfaceCount: Int,
    val abstractClassCount: Int,
    val concreteClassCount: Int
)

/**
 * 依赖健康度指标
 */
@Serializable
data class DependencyHealthMetrics(
    val cyclicDependencies: Int,
    val longestDependencyChain: Int,
    val fanInFanOutViolations: Int,
    val tangles: List<DependencyTangle>
)

/**
 * 依赖混乱信息
 */
@Serializable
data class DependencyTangle(
    val involvedClasses: List<String>,
    val tangleType: String,
    val severity: Severity
)

