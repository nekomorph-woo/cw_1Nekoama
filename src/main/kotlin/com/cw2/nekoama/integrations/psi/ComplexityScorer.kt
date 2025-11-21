package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger

/**
 * 代码复杂度综合评分系统
 * 实现基于多维度指标的复杂度评分算法和重构优先级计算
 * 严格按照 docs/Nekoama新功能-代码结构梳理和质量分析-方案.md 中的评分要求实现
 */
class ComplexityScorer {

    private val logger = NekoamaLogger

    /**
     * 计算方法的综合复杂度评分
     * 评分范围：0-100，分数越高越需要重构
     */
    fun calculateMethodComplexityScore(
        metrics: MethodMetrics,
        thresholds: AnalysisThresholds.DetailedThresholds
    ): Int {
        var score = 0

        // 1. 圈复杂度评分 (权重: 25%)
        val cyclomaticScore = calculateCyclomaticComplexityScore(
            metrics.cyclomaticComplexity,
            thresholds.cyclomaticComplexity
        )
        score += (cyclomaticScore * 0.25).toInt()

        // 2. 认知复杂度评分 (权重: 30%)
        val cognitiveScore = calculateCognitiveComplexityScore(
            metrics.cognitiveComplexity,
            thresholds.cognitiveComplexity
        )
        score += (cognitiveScore * 0.30).toInt()

        // 3. 方法长度评分 (权重: 20%)
        val lengthScore = calculateMethodLengthScore(
            metrics.linesOfCode,
            thresholds.methodLength
        )
        score += (lengthScore * 0.20).toInt()

        // 4. 参数数量评分 (权重: 10%)
        val parameterScore = calculateParameterCountScore(
            metrics.parameterCount,
            thresholds.parameterCount
        )
        score += (parameterScore * 0.10).toInt()

        // 5. 嵌套深度评分 (权重: 10%)
        val nestingScore = calculateNestingDepthScore(
            metrics.nestingDepth,
            thresholds.nestingDepth
        )
        score += (nestingScore * 0.10).toInt()

        // 6. 其他指标评分 (权重: 5%)
        val otherScore = calculateOtherMetricsScore(metrics, thresholds)
        score += (otherScore * 0.05).toInt()

        return score.coerceIn(0, 100)
    }

    /**
     * 计算类的综合复杂度评分
     */
    fun calculateClassComplexityScore(
        classMetrics: ClassComplexityMetrics,
        methodMetrics: Map<String, MethodMetrics>,
        codeSmells: List<CodeSmell>,
        thresholds: AnalysisThresholds.DetailedThresholds
    ): Int {
        var score = 0

        // 1. 类级基础指标评分 (权重: 40%)
        val baseScore = calculateClassBaseScore(classMetrics, thresholds)
        score += (baseScore * 0.40).toInt()

        // 2. 方法级评分汇总 (权重: 35%)
        val methodScore = calculateClassMethodScore(methodMetrics, thresholds)
        score += (methodScore * 0.35).toInt()

        // 3. 代码坏味道评分 (权重: 20%)
        val smellScore = calculateCodeSmellScore(codeSmells, thresholds)
        score += (smellScore * 0.20).toInt()

        // 4. 耦合度评分 (权重: 5%)
        val couplingScore = calculateCouplingScore(classMetrics.couplingMetrics, thresholds)
        score += (couplingScore * 0.05).toInt()

        return score.coerceIn(0, 100)
    }

    /**
     * 计算重构优先级
     */
    fun calculateRefactoringPriority(
        complexityScore: Int,
        impactScope: ImpactScope,
        businessCriticality: BusinessCriticality
    ): RefactoringPriority {
        val level = when {
            complexityScore >= 80 -> "P0"  // 立即重构
            complexityScore >= 60 -> "P1"  // 高优先级
            complexityScore >= 40 -> "P2"  // 中等优先级
            else -> "P3"  // 低优先级
        }

        val riskLevel = calculateRiskLevel(complexityScore, impactScope, businessCriticality)
        val reason = generateRefactoringReason(complexityScore, impactScope, businessCriticality)

        return RefactoringPriority(
            level = level,
            reason = reason,
            riskLevel = riskLevel
        )
    }

    /**
     * 计算圈复杂度评分
     */
    private fun calculateCyclomaticComplexityScore(complexity: Int, threshold: Int): Int {
        return when {
            complexity <= threshold -> 0
            complexity <= threshold * 1.5 -> 30
            complexity <= threshold * 2 -> 60
            complexity <= threshold * 3 -> 85
            else -> 100
        }
    }

    /**
     * 计算认知复杂度评分
     */
    private fun calculateCognitiveComplexityScore(complexity: Int, threshold: Int): Int {
        return when {
            complexity <= threshold -> 0
            complexity <= threshold * 1.5 -> 25
            complexity <= threshold * 2 -> 50
            complexity <= threshold * 3 -> 80
            else -> 100
        }
    }

    /**
     * 计算方法长度评分
     */
    private fun calculateMethodLengthScore(length: Int, threshold: Int): Int {
        return when {
            length <= threshold -> 0
            length <= threshold * 1.5 -> 20
            length <= threshold * 2 -> 45
            length <= threshold * 3 -> 75
            else -> 100
        }
    }

    /**
     * 计算参数数量评分
     */
    private fun calculateParameterCountScore(count: Int, threshold: Int): Int {
        return when {
            count <= threshold -> 0
            count <= threshold + 1 -> 30
            count <= threshold + 2 -> 60
            count <= threshold + 3 -> 85
            else -> 100
        }
    }

    /**
     * 计算嵌套深度评分
     */
    private fun calculateNestingDepthScore(depth: Int, threshold: Int): Int {
        return when {
            depth <= threshold -> 0
            depth <= threshold + 1 -> 25
            depth <= threshold + 2 -> 55
            depth <= threshold + 3 -> 85
            else -> 100
        }
    }

    /**
     * 计算其他指标评分
     */
    private fun calculateOtherMetricsScore(
        metrics: MethodMetrics,
        thresholds: AnalysisThresholds.DetailedThresholds
    ): Int {
        var score = 0

        // 魔法数字评分
        score += (metrics.magicNumberCount.toFloat() / thresholds.magicNumberCount * 20).toInt().coerceAtMost(20)

        // 长行代码评分
        score += (metrics.longLineCount.toFloat() / 3 * 15).toInt().coerceAtMost(15)

        // return语句评分
        score += (metrics.returnStatementCount.toFloat() / thresholds.returnStatementCount * 15).toInt().coerceAtMost(15)

        // 布尔参数评分
        score += (metrics.booleanParameterCount.toFloat() / thresholds.booleanParameterCount * 10).toInt().coerceAtMost(10)

        // 局部变量评分
        score += (metrics.localVariableCount.toFloat() / thresholds.localVariableCount * 10).toInt().coerceAtMost(10)

        return score.coerceIn(0, 100)
    }

    /**
     * 计算类级基础评分
     */
    private fun calculateClassBaseScore(
        classMetrics: ClassComplexityMetrics,
        thresholds: AnalysisThresholds.DetailedThresholds
    ): Int {
        var score = 0

        // 类长度评分
        score += calculateMethodLengthScore(classMetrics.lineOfCode, thresholds.classLength)

        // 方法数量评分
        if (classMetrics.methodCount > thresholds.methodCountInClass) {
            score += 40
        } else if (classMetrics.methodCount > thresholds.methodCountInClass * 0.8) {
            score += 20
        }

        // 字段数量评分
        if (classMetrics.fieldCount > thresholds.fieldCountInClass) {
            score += 30
        }

        return score.coerceIn(0, 100)
    }

    /**
     * 计算类方法评分汇总
     */
    private fun calculateClassMethodScore(
        methodMetrics: Map<String, MethodMetrics>,
        thresholds: AnalysisThresholds.DetailedThresholds
    ): Int {
        if (methodMetrics.isEmpty()) return 0

        val methodScores = methodMetrics.values.map { metrics ->
            calculateMethodComplexityScore(metrics, thresholds)
        }

        return when {
            methodScores.isEmpty() -> 0
            methodScores.all { it <= 30 } -> methodScores.average().toInt()
            methodScores.any { it >= 80 } -> 90
            methodScores.any { it >= 60 } -> 70
            methodScores.any { it >= 40 } -> 50
            else -> methodScores.average().toInt()
        }
    }

    /**
     * 计算代码坏味道评分
     */
    private fun calculateCodeSmellScore(
        codeSmells: List<CodeSmell>,
        thresholds: AnalysisThresholds.DetailedThresholds
    ): Int {
        var score = 0

        codeSmells.forEach { smell ->
            val weight = thresholds.codeSmellWeights[smell.type.name] ?: 1.0
            val severityScore = when (smell.severity) {
                Severity.CRITICAL -> 100
                Severity.HIGH -> 80
                Severity.MEDIUM -> 60
                Severity.LOW -> 40
                Severity.INFO -> 20
            }

            score += (severityScore * weight).toInt()
        }

        // 对多个坏味道进行惩罚
        if (codeSmells.size > 5) {
            score += 20
        } else if (codeSmells.size > 3) {
            score += 10
        }

        return score.coerceIn(0, 100)
    }

    /**
     * 计算耦合度评分
     */
    private fun calculateCouplingScore(
        couplingMetrics: CouplingMetrics,
        thresholds: AnalysisThresholds.DetailedThresholds
    ): Int {
        var score = 0

        // 传出耦合评分
        if (couplingMetrics.efferentCoupling > thresholds.couplingThreshold) {
            score += 50
        } else if (couplingMetrics.efferentCoupling > thresholds.couplingThreshold * 0.7) {
            score += 25
        }

        // 不稳定性评分
        if (couplingMetrics.instability > 0.8) {
            score += 30
        } else if (couplingMetrics.instability > 0.6) {
            score += 15
        }

        // 距离评分
        if (couplingMetrics.distance > 0.5) {
            score += 20
        }

        return score.coerceIn(0, 100)
    }

    /**
     * 计算风险等级
     */
    private fun calculateRiskLevel(
        complexityScore: Int,
        impactScope: ImpactScope,
        businessCriticality: BusinessCriticality
    ): String {
        var riskLevel = 0

        // 复杂度风险
        riskLevel += when {
            complexityScore >= 80 -> 4
            complexityScore >= 60 -> 3
            complexityScore >= 40 -> 2
            else -> 1
        }

        // 影响范围风险
        riskLevel += when (impactScope) {
            ImpactScope.PROJECT_WIDE -> 4
            ImpactScope.MODULE_WIDE -> 3
            ImpactScope.PACKAGE_WIDE -> 2
            ImpactScope.CLASS_LOCAL -> 1
        }

        // 业务关键性风险
        riskLevel += when (businessCriticality) {
            BusinessCriticality.CRITICAL -> 4
            BusinessCriticality.HIGH -> 3
            BusinessCriticality.MEDIUM -> 2
            BusinessCriticality.LOW -> 1
        }

        return when {
            riskLevel >= 10 -> "VERY_HIGH"
            riskLevel >= 8 -> "HIGH"
            riskLevel >= 5 -> "MEDIUM"
            else -> "LOW"
        }
    }

    /**
     * 生成重构原因
     */
    private fun generateRefactoringReason(
        complexityScore: Int,
        impactScope: ImpactScope,
        businessCriticality: BusinessCriticality
    ): String {
        val reasons = mutableListOf<String>()

        when {
            complexityScore >= 80 -> reasons.add("复杂度极高(${complexityScore}分)")
            complexityScore >= 60 -> reasons.add("复杂度较高(${complexityScore}分)")
            complexityScore >= 40 -> reasons.add("复杂度中等(${complexityScore}分)")
        }

        when (impactScope) {
            ImpactScope.PROJECT_WIDE -> reasons.add("影响整个项目")
            ImpactScope.MODULE_WIDE -> reasons.add("影响多个模块")
            ImpactScope.PACKAGE_WIDE -> reasons.add("影响包内多个类")
            ImpactScope.CLASS_LOCAL -> reasons.add("仅影响当前类")
        }

        when (businessCriticality) {
            BusinessCriticality.CRITICAL -> reasons.add("业务关键功能")
            BusinessCriticality.HIGH -> reasons.add("重要业务功能")
            BusinessCriticality.MEDIUM -> reasons.add("一般业务功能")
            BusinessCriticality.LOW -> reasons.add("辅助功能")
        }

        return reasons.joinToString("，")
    }

    /**
     * 生成重构建议报告
     */
    fun generateRefactoringReport(
        complexityResults: Map<String, Int>,
        thresholds: AnalysisThresholds.DetailedThresholds
    ): RefactoringReport {
        val sortedResults = complexityResults.toList().sortedByDescending { it.second }

        val highPriorityItems = sortedResults.filter { it.second >= 70 }.take(10)
        val mediumPriorityItems = sortedResults.filter { it.second in 40..69 }.take(10)
        val lowPriorityItems = sortedResults.filter { it.second in 20..39 }.take(10)

        val statistics = RefactoringStatistics(
            totalItems = complexityResults.size,
            highPriorityCount = highPriorityItems.size,
            mediumPriorityCount = mediumPriorityItems.size,
            lowPriorityCount = lowPriorityItems.size,
            averageComplexity = complexityResults.values.average().toInt()
        )

        return RefactoringReport(
            highPriorityItems = highPriorityItems,
            mediumPriorityItems = mediumPriorityItems,
            lowPriorityItems = lowPriorityItems,
            statistics = statistics,
            recommendations = generateRecommendations(statistics)
        )
    }

    /**
     * 生成重构建议
     */
    private fun generateRecommendations(statistics: RefactoringStatistics): List<String> {
        val recommendations = mutableListOf<String>()

        if (statistics.highPriorityCount > 5) {
            recommendations.add("项目存在大量高复杂度代码，建议优先重构核心模块")
        }

        if (statistics.averageComplexity > 60) {
            recommendations.add("整体复杂度偏高，建议制定系统性重构计划")
        }

        if (statistics.totalItems > 50) {
            recommendations.add("代码规模较大，建议分阶段进行重构")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("代码质量良好，继续保持当前的开发规范")
        }

        return recommendations
    }
}

/**
 * 影响范围枚举
 */
enum class ImpactScope {
    PROJECT_WIDE,   // 项目级影响
    MODULE_WIDE,    // 模块级影响
    PACKAGE_WIDE,   // 包级影响
    CLASS_LOCAL     // 类内影响
}

/**
 * 业务关键性枚举
 */
enum class BusinessCriticality {
    CRITICAL,   // 关键业务功能
    HIGH,       // 重要业务功能
    MEDIUM,     // 一般业务功能
    LOW         // 辅助功能
}

/**
 * 重构报告
 */
data class RefactoringReport(
    val highPriorityItems: List<Pair<String, Int>>,
    val mediumPriorityItems: List<Pair<String, Int>>,
    val lowPriorityItems: List<Pair<String, Int>>,
    val statistics: RefactoringStatistics,
    val recommendations: List<String>
)

/**
 * 重构统计信息
 */
data class RefactoringStatistics(
    val totalItems: Int,
    val highPriorityCount: Int,
    val mediumPriorityCount: Int,
    val lowPriorityCount: Int,
    val averageComplexity: Int
)