package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.ComplexityThresholds
import com.cw2.nekoama.ai.model.dependency.Severity

/**
 * 代码分析阈值配置
 * 严格按照 docs/Nekoama新功能-代码结构梳理和质量分析-方案.md 中的建议阈值实现
 * 提供标准化的阈值配置和灵活的自定义选项
 */
object AnalysisThresholds {

    /**
     * 标准阈值配置 - 按照文档建议设置
     */
    val standardThresholds = ComplexityThresholds(
        cyclomaticComplexity = 10,    // ≤10简单，>20严重
        cognitiveComplexity = 15,      // ≤15简单，>25严重
        nestingDepth = 3,              // ≤3正常，>5严重
        methodLength = 50,             // ≤50正常，>100严重
        classLength = 300,             // ≤300正常，>500严重
        parameterCount = 5             // ≤5正常，>5过多
    )

    /**
     * 严格阈值配置 - 用于高质量要求的项目
     */
    val strictThresholds = ComplexityThresholds(
        cyclomaticComplexity = 8,     // 更严格的圈复杂度
        cognitiveComplexity = 12,     // 更严格的认知复杂度
        nestingDepth = 2,             // 更严格的嵌套深度
        methodLength = 30,            // 更严格的方法长度
        classLength = 200,            // 更严格的类长度
        parameterCount = 4             // 更严格的参数数量
    )

    /**
     * 宽松阈值配置 - 用于遗留项目或原型开发
     */
    val lenientThresholds = ComplexityThresholds(
        cyclomaticComplexity = 20,    // 更宽松的圈复杂度
        cognitiveComplexity = 25,     // 更宽松的认知复杂度
        nestingDepth = 5,             // 更宽松的嵌套深度
        methodLength = 100,           // 更宽松的方法长度
        classLength = 500,            // 更宽松的类长度
        parameterCount = 7             // 更宽松的参数数量
    )

    /**
     * 超详细阈值配置 - 包含所有分析指标的阈值
     */
    data class DetailedThresholds(
        // 基础复杂度阈值
        val cyclomaticComplexity: Int,
        val cognitiveComplexity: Int,
        val nestingDepth: Int,
        val methodLength: Int,
        val classLength: Int,
        val parameterCount: Int,

        // 新增的详细阈值
        val booleanParameterCount: Int = 2,       // 布尔参数阈值
        val magicNumberCount: Int = 5,           // 魔法数字阈值
        val longLineLength: Int = 120,           // 长行长度阈值
        val returnStatementCount: Int = 5,        // return语句阈值
        val localVariableCount: Int = 10,        // 局部变量阈值

        // 组合条件阈值
        val longMethodWithHighComplexityLength: Int = 50,    // 长方法+高复杂度组合的长度阈值
        val longMethodWithHighComplexityComplexity: Int = 10, // 长方法+高复杂度组合的复杂度阈值
        val deepNestingWithHighBranchingDepth: Int = 4,      // 深度嵌套+多分支组合的嵌套阈值
        val deepNestingWithHighBranchingComplexity: Int = 15, // 深度嵌套+多分支组合的复杂度阈值

        // 类级阈值
        val methodCountInClass: Int = 20,          // 类中方法数量阈值
        val fieldCountInClass: Int = 15,           // 类中字段数量阈值
        val couplingThreshold: Int = 20,           // 耦合度阈值

        // 代码坏味道检测权重
        val codeSmellWeights: Map<String, Double> = mapOf(
            "LONG_METHOD" to 1.0,
            "LONG_PARAMETER_LIST" to 0.8,
            "LARGE_CLASS" to 1.2,
            "DEEP_NESTING" to 1.0,
            "GOD_CLASS" to 2.0,
            "SPAGHETTI_CODE" to 2.5,
            "MAGIC_NUMBERS" to 0.6,
            "LONG_EXPRESSION" to 0.4,
            "BOOLEAN_PARAMETERS" to 0.5,
            "MULTIPLE_RETURNS" to 0.3
        ),

        // 评分系统阈值
        val complexityScoreThresholds: ComplexityScoreThresholds = ComplexityScoreThresholds()
    ) {
        /**
         * 转换为基础ComplexityThresholds
         */
        fun toBasicThresholds(): ComplexityThresholds {
            return ComplexityThresholds(
                cyclomaticComplexity = cyclomaticComplexity,
                cognitiveComplexity = cognitiveComplexity,
                nestingDepth = nestingDepth,
                methodLength = methodLength,
                classLength = classLength,
                parameterCount = parameterCount
            )
        }
    }

    /**
     * 复杂度评分阈值
     */
    data class ComplexityScoreThresholds(
        val healthyThreshold: Int = 30,      // 健康: 0-30分
        val acceptableThreshold: Int = 50,   // 可接受: 31-50分
        val concerningThreshold: Int = 70,   // 需关注: 51-70分
        val problematicThreshold: Int = 100  // 问题严重: 71-100分
    ) {
        /**
         * 根据评分确定严重程度
         */
        fun determineSeverity(score: Int): Severity {
            return when {
                score >= problematicThreshold -> Severity.CRITICAL
                score >= concerningThreshold -> Severity.HIGH
                score >= acceptableThreshold -> Severity.MEDIUM
                else -> Severity.LOW
            }
        }
    }

    /**
     * 标准详细阈值配置
     */
    val standardDetailedThresholds = DetailedThresholds(
        cyclomaticComplexity = 10,
        cognitiveComplexity = 15,
        nestingDepth = 3,
        methodLength = 50,
        classLength = 300,
        parameterCount = 5,
        booleanParameterCount = 2,
        magicNumberCount = 5,
        longLineLength = 120,
        returnStatementCount = 5,
        localVariableCount = 10,
        longMethodWithHighComplexityLength = 50,
        longMethodWithHighComplexityComplexity = 10,
        deepNestingWithHighBranchingDepth = 4,
        deepNestingWithHighBranchingComplexity = 15,
        methodCountInClass = 20,
        fieldCountInClass = 15,
        couplingThreshold = 20
    )

    /**
     * 预定义的阈值配置集合
     */
    val predefinedConfigs = mapOf(
        "standard" to standardDetailedThresholds,
        "strict" to DetailedThresholds(
            cyclomaticComplexity = 8,
            cognitiveComplexity = 12,
            nestingDepth = 2,
            methodLength = 30,
            classLength = 200,
            parameterCount = 4,
            booleanParameterCount = 1,
            magicNumberCount = 3,
            longLineLength = 100,
            returnStatementCount = 3,
            localVariableCount = 8,
            longMethodWithHighComplexityLength = 30,
            longMethodWithHighComplexityComplexity = 8,
            deepNestingWithHighBranchingDepth = 3,
            deepNestingWithHighBranchingComplexity = 12,
            methodCountInClass = 15,
            fieldCountInClass = 10,
            couplingThreshold = 15
        ),
        "lenient" to DetailedThresholds(
            cyclomaticComplexity = 20,
            cognitiveComplexity = 25,
            nestingDepth = 5,
            methodLength = 100,
            classLength = 500,
            parameterCount = 7,
            booleanParameterCount = 3,
            magicNumberCount = 8,
            longLineLength = 150,
            returnStatementCount = 8,
            localVariableCount = 15,
            longMethodWithHighComplexityLength = 80,
            longMethodWithHighComplexityComplexity = 15,
            deepNestingWithHighBranchingDepth = 6,
            deepNestingWithHighBranchingComplexity = 20,
            methodCountInClass = 30,
            fieldCountInClass = 25,
            couplingThreshold = 30
        ),
        "legacy" to DetailedThresholds(
            cyclomaticComplexity = 30,
            cognitiveComplexity = 40,
            nestingDepth = 7,
            methodLength = 150,
            classLength = 800,
            parameterCount = 10,
            booleanParameterCount = 5,
            magicNumberCount = 15,
            longLineLength = 200,
            returnStatementCount = 12,
            localVariableCount = 20,
            longMethodWithHighComplexityLength = 120,
            longMethodWithHighComplexityComplexity = 25,
            deepNestingWithHighBranchingDepth = 8,
            deepNestingWithHighBranchingComplexity = 30,
            methodCountInClass = 50,
            fieldCountInClass = 40,
            couplingThreshold = 50
        )
    )

    /**
     * 创建自定义阈值配置
     */
    fun createCustomThresholds(
        baseConfig: DetailedThresholds = standardDetailedThresholds,
        customizations: Map<String, Int> = emptyMap()
    ): DetailedThresholds {
        return baseConfig.copy(
            cyclomaticComplexity = customizations["cyclomaticComplexity"] ?: baseConfig.cyclomaticComplexity,
            cognitiveComplexity = customizations["cognitiveComplexity"] ?: baseConfig.cognitiveComplexity,
            nestingDepth = customizations["nestingDepth"] ?: baseConfig.nestingDepth,
            methodLength = customizations["methodLength"] ?: baseConfig.methodLength,
            classLength = customizations["classLength"] ?: baseConfig.classLength,
            parameterCount = customizations["parameterCount"] ?: baseConfig.parameterCount,
            booleanParameterCount = customizations["booleanParameterCount"] ?: baseConfig.booleanParameterCount,
            magicNumberCount = customizations["magicNumberCount"] ?: baseConfig.magicNumberCount,
            longLineLength = customizations["longLineLength"] ?: baseConfig.longLineLength,
            returnStatementCount = customizations["returnStatementCount"] ?: baseConfig.returnStatementCount,
            localVariableCount = customizations["localVariableCount"] ?: baseConfig.localVariableCount
        )
    }

    /**
     * 根据项目类型推荐合适的阈值配置
     */
    fun recommendThresholds(projectType: ProjectType): DetailedThresholds {
        return when (projectType) {
            ProjectType.NEW_PROJECT -> standardDetailedThresholds
            ProjectType.HIGH_QUALITY -> predefinedConfigs["strict"]!!
            ProjectType.LEGACY_SYSTEM -> predefinedConfigs["legacy"]!!
            ProjectType.PROTOTYPE -> predefinedConfigs["lenient"]!!
            ProjectType.CRITICAL_SYSTEM -> predefinedConfigs["strict"]!!
        }
    }

    /**
     * 验证阈值配置的合理性
     */
    fun validateThresholds(thresholds: DetailedThresholds): List<String> {
        val warnings = mutableListOf<String>()

        // 基础逻辑检查
        if (thresholds.cyclomaticComplexity <= 0) {
            warnings.add("圈复杂度阈值必须大于0")
        }

        if (thresholds.cognitiveComplexity <= 0) {
            warnings.add("认知复杂度阈值必须大于0")
        }

        if (thresholds.methodLength <= 0) {
            warnings.add("方法长度阈值必须大于0")
        }

        if (thresholds.classLength <= thresholds.methodLength) {
            warnings.add("类长度阈值应该大于方法长度阈值")
        }

        // 合理性检查
        if (thresholds.cyclomaticComplexity > 50) {
            warnings.add("圈复杂度阈值过高(>50)，建议调整为更合理的值")
        }

        if (thresholds.cognitiveComplexity > 100) {
            warnings.add("认知复杂度阈值过高(>100)，建议调整为更合理的值")
        }

        if (thresholds.nestingDepth > 10) {
            warnings.add("嵌套深度阈值过高(>10)，建议调整为更合理的值")
        }

        if (thresholds.methodLength > 200) {
            warnings.add("方法长度阈值过高(>200)，建议调整为更合理的值")
        }

        if (thresholds.classLength > 1000) {
            warnings.add("类长度阈值过高(>1000)，建议调整为更合理的值")
        }

        return warnings
    }

    /**
     * 项目类型枚举
     */
    enum class ProjectType {
        NEW_PROJECT,         // 新项目
        HIGH_QUALITY,        // 高质量要求
        LEGACY_SYSTEM,       // 遗留系统
        PROTOTYPE,           // 原型项目
        CRITICAL_SYSTEM      // 关键系统
    }

    /**
     * 阈值配置管理器
     */
    class ThresholdsManager {
        private var currentConfig: DetailedThresholds = standardDetailedThresholds
        private val configHistory = mutableListOf<DetailedThresholds>()

        /**
         * 获取当前配置
         */
        fun getCurrentConfig(): DetailedThresholds = currentConfig

        /**
         * 设置新配置
         */
        fun setConfig(newConfig: DetailedThresholds) {
            configHistory.add(currentConfig)
            currentConfig = newConfig
        }

        /**
         * 应用预定义配置
         */
        fun applyPredefinedConfig(configName: String) {
            predefinedConfigs[configName]?.let { config ->
                setConfig(config)
            }
        }

        /**
         * 恢复到上一个配置
         */
        fun rollback(): Boolean {
            return if (configHistory.isNotEmpty()) {
                currentConfig = configHistory.removeAt(configHistory.size - 1)
                true
            } else {
                false
            }
        }

        /**
         * 获取配置历史
         */
        fun getConfigHistory(): List<DetailedThresholds> = configHistory.toList()

        /**
         * 重置到标准配置
         */
        fun resetToStandard() {
            setConfig(standardDetailedThresholds)
        }
    }

    /**
     * 全局阈值管理器实例
     */
    val globalManager = ThresholdsManager()
}