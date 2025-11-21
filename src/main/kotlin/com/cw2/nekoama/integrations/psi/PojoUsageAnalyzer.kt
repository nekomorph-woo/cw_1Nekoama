package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil

/**
 * POJO使用情况分析器
 * 实现POJO的识别、分类、使用统计和跨边界分析
 * 严格按照 docs/Nekoama新功能-代码结构梳理和质量分析-方案.md 中的要求实现
 */
class PojoUsageAnalyzer {

    private val logger = NekoamaLogger
    private val dependencyExtractor = JavaDependencyExtractor()

    /**
     * 分析项目中所有POJO的使用情况
     */
    fun analyzePojoUsage(
        psiClasses: List<PsiClass>,
        methodCalls: List<MethodCall>,
        config: AnalysisConfig
    ): List<PojoUsage> {
        val pojoUsages = mutableListOf<PojoUsage>()

        try {
            // 1. 识别所有POJO类
            val pojoClasses = psiClasses.filter { isPojoClass(it) }

            pojoClasses.forEach { pojoClass ->
                // 2. 分析每个POJO的使用情况
                val usage = analyzeSinglePojoUsage(pojoClass, psiClasses, methodCalls, config)
                pojoUsages.add(usage)
            }

            logger.info("PojoUsageAnalyzer", "分析了 ${pojoUsages.size} 个POJO的使用情况")

        } catch (e: Exception) {
            logger.error("PojoUsageAnalyzer", "POJO使用情况分析失败", error = e)
        }

        return pojoUsages
    }

    /**
     * 分析单个POJO的使用情况
     */
    private fun analyzeSinglePojoUsage(
        pojoClass: PsiClass,
        allClasses: List<PsiClass>,
        methodCalls: List<MethodCall>,
        config: AnalysisConfig
    ): PojoUsage {
        val qualifiedName = pojoClass.qualifiedName ?: ""
        val packageName = (pojoClass.containingFile as? PsiJavaFile)?.packageName ?: ""

        // 1. 分类POJO类型
        val category = classifyPojo(pojoClass)

        // 2. 统计使用情况
        val usageStats = calculatePojoUsageStats(pojoClass, allClasses, methodCalls)

        // 3. 提取字段信息
        val fields = extractPojoFields(pojoClass)

        // 4. 分析跨边界使用
        val crossBoundaryUsages = analyzeCrossBoundaryUsage(pojoClass, allClasses, methodCalls)

        return PojoUsage(
            id = qualifiedName,
            name = pojoClass.name ?: "",
            qualifiedName = qualifiedName,
            packageId = packageName,
            category = category,
            usage = usageStats,
            fields = fields,
            crossBoundaryUsage = crossBoundaryUsages
        )
    }

    /**
     * 判断类是否为POJO
     * 改进的动态识别算法：基于多维度评分系统
     */
    fun isPojoClass(psiClass: PsiClass): Boolean {
        // 排除接口、枚举、注解
        if (psiClass.isInterface || psiClass.isEnum || psiClass.isAnnotationType) {
            return false
        }

        // 排除测试类
        if (psiClass.name?.endsWith("Test") == true ||
            psiClass.name?.startsWith("Test") == true) {
            return false
        }

        val methods = psiClass.methods
        val fields = psiClass.fields

        // 1. 必须有字段
        if (fields.isEmpty()) {
            return false
        }

        // 2. 多维度评分系统
        val pojoScore = calculatePojoScore(psiClass, methods.toList(), fields.toList())

        // 3. 动态阈值：根据类的大小调整期望值
        val dynamicThreshold = calculateDynamicThreshold(methods.size, fields.size)

        // 4. 综合判断
        return pojoScore >= dynamicThreshold
    }

    /**
     * 计算POJO评分（0-100分）
     */
    private fun calculatePojoScore(psiClass: PsiClass, methods: List<PsiMethod>, fields: List<PsiField>): Int {
        var score = 0

        // 1. 字段和getter/setter匹配度（40分）
        val fieldsList = fields.toList()
        val getterSetterCount = methods.count { method ->
            isGetterOrSetter(method, fieldsList)
        }

        val accessorScore = if (fields.isNotEmpty()) {
            (getterSetterCount.toDouble() / (fields.size * 2)) * 40 // 每个字段预期有getter和setter
        } else 0.0
        score += accessorScore.toInt()

        // 2. 方法复杂度评分（25分）
        val complexityScore = methods.filter { !it.isConstructor }.mapNotNull { method ->
            method.body?.let { body ->
                calculateMethodComplexityScore(body)
            }
        }.average().let { avgComplexity ->
            if (avgComplexity.isNaN()) 25.0 else (1.0 - avgComplexity.coerceAtMost(1.0)) * 25
        }
        score += complexityScore.toInt()

        // 3. 注解模式评分（20分）
        val annotationScore = calculateAnnotationScore(psiClass, fields, methods)
        score += annotationScore

        // 4. 命名约定评分（10分）
        val namingScore = calculateNamingScore(psiClass.name ?: "")
        score += namingScore

        // 5. 继承层级评分（5分）
        val inheritanceScore = calculateInheritanceScore(psiClass)
        score += inheritanceScore

        return score.coerceAtMost(100)
    }

    /**
     * 计算方法复杂度评分（0.0-1.0，1.0表示最复杂）
     */
    private fun calculateMethodComplexityScore(body: PsiCodeBlock): Double {
        var complexity = 0.0
        var statementCount = 0

        body.accept(object : JavaRecursiveElementVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                super.visitIfStatement(statement)
                complexity += 0.3
            }

            override fun visitForStatement(statement: PsiForStatement) {
                super.visitForStatement(statement)
                complexity += 0.5
            }

            override fun visitWhileStatement(statement: PsiWhileStatement) {
                super.visitWhileStatement(statement)
                complexity += 0.5
            }

            override fun visitTryStatement(statement: PsiTryStatement) {
                super.visitTryStatement(statement)
                complexity += 0.4
            }

            override fun visitExpressionStatement(statement: PsiExpressionStatement) {
                super.visitExpressionStatement(statement)
                statementCount++
            }
        })

        // 基于语句数量的基础复杂度
        val baseComplexity = (statementCount / 10.0).coerceAtMost(0.5)

        return (complexity + baseComplexity).coerceAtMost(1.0)
    }

    /**
     * 计算注解模式评分
     */
    private fun calculateAnnotationScore(psiClass: PsiClass, fields: List<PsiField>, methods: List<PsiMethod>): Int {
        var score = 20 // 基础分

        // 检查常见POJO注解
        val pojoAnnotations = listOf("Data", "Getter", "Setter", "NoArgsConstructor",
                                   "AllArgsConstructor", "Builder", "JsonProperty")

        val hasPojoAnnotations = psiClass.modifierList?.annotations?.any { annotation ->
            pojoAnnotations.any { anno -> annotation.qualifiedName?.contains(anno) == true }
        } ?: false

        if (hasPojoAnnotations) {
            score = 20
        } else {
            // 检查字段注解
            val hasFieldAnnotations = fields.any { field ->
                field.modifierList?.annotations?.isNotEmpty() == true
            }

            if (hasFieldAnnotations) score -= 5

            // 检查方法上的业务逻辑注解
            val hasBusinessAnnotations = methods.any { method ->
                val businessAnnotations = listOf("Service", "Component", "Repository", "Controller",
                                                "Transactional", "RequestMapping", "GetMapping")
                method.modifierList.annotations.any { annotation ->
                    businessAnnotations.any { anno -> annotation.qualifiedName?.contains(anno) == true }
                }
            }

            if (hasBusinessAnnotations) score -= 10
        }

        return score.coerceAtLeast(0)
    }

    /**
     * 计算命名约定评分
     */
    private fun calculateNamingScore(className: String): Int {
        var score = 10

        // 常见POJO模式后缀
        val pojoSuffixes = listOf("DTO", "VO", "DO", "Entity", "Model", "Bean", "Form", "Request", "Response")
        val pojoPrefixes = listOf("Tbl", "Tmp")

        val hasPojoSuffix = pojoSuffixes.any { suffix -> className.endsWith(suffix) }
        val hasPojoPrefix = pojoPrefixes.any { prefix -> className.startsWith(prefix) }

        if (hasPojoSuffix || hasPojoPrefix) {
            return 10
        }

        // 常见业务逻辑类名（扣分项）
        val businessSuffixes = listOf("Service", "Manager", "Controller", "Handler", "Processor",
                                    "Util", "Helper", "Factory", "Builder", "Strategy")

        val hasBusinessSuffix = businessSuffixes.any { suffix -> className.endsWith(suffix) }
        if (hasBusinessSuffix) {
            score = 2
        }

        return score
    }

    /**
     * 计算继承层级评分
     */
    private fun calculateInheritanceScore(psiClass: PsiClass): Int {
        val superClass = psiClass.superClass
        val interfaces = psiClass.interfaces

        // 继承自常见的POJO基类加分
        val pojoBaseClasses = listOf("Object", "Serializable")

        val superClassScore = if (superClass != null &&
                                pojoBaseClasses.any { base -> superClass.qualifiedName == base }) {
            5
        } else if (superClass != null && superClass.qualifiedName != "java.lang.Object") {
            // 继承了非POJO类，扣分
            1
        } else {
            3 // 只继承Object
        }

        // 实现太多接口可能是业务逻辑类
        val interfaceScore = when {
            interfaces.isEmpty() -> 5
            interfaces.size <= 2 -> 3
            else -> 1
        }

        return (superClassScore + interfaceScore) / 2
    }

    /**
     * 计算动态阈值
     * 根据类的复杂度调整期望的POJO评分
     */
    private fun calculateDynamicThreshold(methodCount: Int, fieldCount: Int): Int {
        val baseThreshold = 60

        // 方法越多，期望的阈值越低（因为有更多的业务逻辑可能性）
        val methodAdjustment = when {
            methodCount <= 5 -> 0
            methodCount <= 10 -> -5
            methodCount <= 20 -> -10
            else -> -15
        }

        // 字段越多，更可能是数据类
        val fieldAdjustment = when {
            fieldCount <= 2 -> -5
            fieldCount <= 5 -> 0
            fieldCount <= 10 -> 5
            else -> 10
        }

        return (baseThreshold + methodAdjustment + fieldAdjustment).coerceIn(40, 80)
    }

    /**
     * 判断方法是否为getter或setter
     */
    private fun isGetterOrSetter(method: PsiMethod, fields: List<PsiField>): Boolean {
        val methodName = method.name
        val parameters = method.parameterList.parametersCount

        // getter方法：无参数，以get开头或is开头（boolean）
        if (parameters == 0) {
            if ((methodName.startsWith("get") && methodName.length > 3) ||
                (methodName.startsWith("is") && methodName.length > 2)) {
                val fieldName = if (methodName.startsWith("get")) {
                    methodName.substring(3).replaceFirstChar { it.lowercase() }
                } else {
                    methodName.substring(2).replaceFirstChar { it.lowercase() }
                }
                return fields.any { it.name.equals(fieldName, ignoreCase = true) }
            }
        }

        // setter方法：一个参数，以set开头
        if (parameters == 1 && methodName.startsWith("set") && methodName.length > 3) {
            val fieldName = methodName.substring(3).replaceFirstChar { it.lowercase() }
            return fields.any { it.name.equals(fieldName, ignoreCase = true) }
        }

        return false
    }

    /**
     * POJO分类
     */
    private fun classifyPojo(psiClass: PsiClass): PojoCategory {
        val className = psiClass.name ?: ""
        val packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: ""

        return when {
            // Entity - 实体类
            className.endsWith("Entity") ||
            packageName.contains(".entity.") ||
            hasEntityAnnotation(psiClass) -> PojoCategory.ENTITY

            // DTO - 数据传输对象
            className.endsWith("DTO") || className.endsWith("Dto") ||
            packageName.contains(".dto.") ||
            hasSerializableAnnotation(psiClass) -> PojoCategory.DTO

            // VO - 值对象
            className.endsWith("VO") || className.endsWith("Vo") ||
            packageName.contains(".vo.") -> PojoCategory.VO

            // DO - 领域对象
            className.endsWith("DO") || className.endsWith("Do") ||
            packageName.contains(".do.") ||
            packageName.contains(".domain.") -> PojoCategory.DO

            // Config - 配置对象
            className.endsWith("Config") || className.endsWith("Configuration") ||
            packageName.contains(".config.") ||
            hasConfigurationAnnotation(psiClass) -> PojoCategory.CONFIG

            // 默认为DOMAIN
            else -> PojoCategory.DOMAIN
        }
    }

    /**
     * 检查是否有实体类注解
     */
    private fun hasEntityAnnotation(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName?.let { name ->
                name == "javax.persistence.Entity" ||
                name == "jakarta.persistence.Entity" ||
                name == "org.springframework.data.annotation.Id" ||
                name == "javax.persistence.Id" ||
                name.endsWith(".Table") ||
                name.endsWith(".Column")
            } ?: false
        }
    }

    /**
     * 检查是否有序列化注解
     */
    private fun hasSerializableAnnotation(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName?.let { name ->
                name == "java.io.Serializable" ||
                name.endsWith(".JsonProperty") ||
                name.endsWith(".SerializedName") ||
                name.endsWith(".XmlElement")
            } ?: false
        }
    }

    /**
     * 检查是否有配置注解
     */
    private fun hasConfigurationAnnotation(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName?.let { name ->
                name.endsWith(".ConfigurationProperties") ||
                name.endsWith(".Configuration") ||
                name.endsWith(".PropertySource")
            } ?: false
        }
    }

    /**
     * 计算POJO使用统计
     */
    private fun calculatePojoUsageStats(
        pojoClass: PsiClass,
        allClasses: List<PsiClass>,
        methodCalls: List<MethodCall>
    ): PojoUsageStats {
        val pojoQualifiedName = pojoClass.qualifiedName ?: ""

        // 1. 统计作为参数的使用
        val asParameter = countAsParameterUsage(pojoQualifiedName, allClasses)

        // 2. 统计作为返回值的使用
        val asReturnType = countAsReturnTypeUsage(pojoQualifiedName, allClasses)

        // 3. 统计作为字段类型的使用
        val asFieldType = countAsFieldTypeUsage(pojoQualifiedName, allClasses)

        // 4. 统计作为局部变量的使用
        val asLocalVariable = countAsLocalVariableUsage(pojoQualifiedName, allClasses)

        // 5. 计算使用方统计
        val usedByMethodsCount = asParameter + asReturnType + asLocalVariable
        val usedByClassesCount = countUsingClasses(pojoQualifiedName, allClasses)
        val usedByPackagesCount = countUsingPackages(pojoQualifiedName, allClasses)

        return PojoUsageStats(
            usedByMethodsCount = usedByMethodsCount,
            usedByClassesCount = usedByClassesCount,
            usedByPackagesCount = usedByPackagesCount,
            usageTypes = UsageTypes(
                asParameter = asParameter,
                asReturnType = asReturnType,
                asFieldType = asFieldType,
                asLocalVariable = asLocalVariable
            )
        )
    }

    /**
     * 统计作为参数的使用次数
     */
    private fun countAsParameterUsage(pojoQualifiedName: String, allClasses: List<PsiClass>): Int {
        return allClasses.sumOf { psiClass ->
            psiClass.methods.sumOf { method ->
                method.parameterList.parameters.count { parameter ->
                    parameter.type.canonicalText == pojoQualifiedName
                }
            }
        }
    }

    /**
     * 统计作为返回值的使用次数
     */
    private fun countAsReturnTypeUsage(pojoQualifiedName: String, allClasses: List<PsiClass>): Int {
        return allClasses.sumOf { psiClass ->
            psiClass.methods.count { method ->
                method.returnType?.canonicalText == pojoQualifiedName
            }
        }
    }

    /**
     * 统计作为字段类型的使用次数
     */
    private fun countAsFieldTypeUsage(pojoQualifiedName: String, allClasses: List<PsiClass>): Int {
        return allClasses.sumOf { psiClass ->
            psiClass.fields.count { field ->
                field.type.canonicalText == pojoQualifiedName
            }
        }
    }

    /**
     * 统计作为局部变量的使用次数
     */
    private fun countAsLocalVariableUsage(pojoQualifiedName: String, allClasses: List<PsiClass>): Int {
        var count = 0

        allClasses.forEach { psiClass ->
            psiClass.methods.forEach { method ->
                method.body?.accept(object : JavaRecursiveElementVisitor() {
                    override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
                        super.visitDeclarationStatement(statement)
                        statement.declaredElements.forEach { element ->
                            if (element is PsiLocalVariable) {
                                if (element.type.canonicalText == pojoQualifiedName) {
                                    count++
                                }
                            }
                        }
                    }
                })
            }
        }

        return count
    }

    /**
     * 统计使用该POJO的类数量
     */
    private fun countUsingClasses(pojoQualifiedName: String, allClasses: List<PsiClass>): Int {
        return allClasses.count { psiClass ->
            val usesPojo = psiClass.methods.any { method ->
                method.parameterList.parameters.any { param ->
                    param.type.canonicalText == pojoQualifiedName
                } ||
                method.returnType?.canonicalText == pojoQualifiedName
            } ||
            psiClass.fields.any { field ->
                field.type.canonicalText == pojoQualifiedName
            }

            usesPojo && psiClass.qualifiedName != pojoQualifiedName
        }
    }

    /**
     * 统计使用该POJO的包数量
     */
    private fun countUsingPackages(pojoQualifiedName: String, allClasses: List<PsiClass>): Int {
        val usingPackages = mutableSetOf<String>()

        allClasses.forEach { psiClass ->
            val packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: ""
            val usesPojo = psiClass.methods.any { method ->
                method.parameterList.parameters.any { param ->
                    param.type.canonicalText == pojoQualifiedName
                } ||
                method.returnType?.canonicalText == pojoQualifiedName
            } ||
            psiClass.fields.any { field ->
                field.type.canonicalText == pojoQualifiedName
            }

            if (usesPojo && psiClass.qualifiedName != pojoQualifiedName) {
                usingPackages.add(packageName)
            }
        }

        return usingPackages.size
    }

    /**
     * 提取POJO字段信息
     */
    private fun extractPojoFields(pojoClass: PsiClass): List<PojoField> {
        return pojoClass.fields.map { field ->
            PojoField(
                name = field.name ?: "",
                type = field.type.canonicalText
            )
        }
    }

    /**
     * 分析跨边界使用
     */
    private fun analyzeCrossBoundaryUsage(
        pojoClass: PsiClass,
        allClasses: List<PsiClass>,
        methodCalls: List<MethodCall>
    ): List<CrossBoundaryUsage> {
        val crossBoundaryUsages = mutableListOf<CrossBoundaryUsage>()
        val pojoQualifiedName = pojoClass.qualifiedName ?: ""
        val pojoPackage = (pojoClass.containingFile as? PsiJavaFile)?.packageName ?: ""

        // 定义架构边界的规则
        val boundaryRules = mapOf(
            // Controller -> Service
            "controller" to listOf("service", "repository", "dao"),
            // Service -> Repository/DAO
            "service" to listOf("repository", "dao"),
            // Repository 不应该依赖 Service
            "repository" to listOf("service", "controller"),
            // DAO 不应该依赖 Service
            "dao" to listOf("service", "controller")
        )

        allClasses.forEach { usingClass ->
            val usingPackage = (usingClass.containingFile as? PsiJavaFile)?.packageName ?: ""

            if (usingPackage == pojoPackage) return@forEach // 忽略同包使用

            val usesPojo = usingClass.methods.any { method ->
                method.parameterList.parameters.any { param ->
                    param.type.canonicalText == pojoQualifiedName
                } ||
                method.returnType?.canonicalText == pojoQualifiedName
            }

            if (usesPojo) {
                val isExpected = isExpectedCrossBoundaryUsage(usingPackage, pojoPackage, boundaryRules)
                val usageCount = countUsageCount(pojoQualifiedName, usingClass)

                crossBoundaryUsages.add(
                    CrossBoundaryUsage(
                        fromPackage = usingPackage,
                        toPackage = pojoPackage,
                        usageCount = usageCount,
                        isExpected = isExpected
                    )
                )
            }
        }

        return crossBoundaryUsages
    }

    /**
     * 判断跨边界使用是否为预期
     */
    private fun isExpectedCrossBoundaryUsage(
        fromPackage: String,
        toPackage: String,
        boundaryRules: Map<String, List<String>>
    ): Boolean {
        val fromLayer = identifyArchitecturalLayer(fromPackage)
        val toLayer = identifyArchitecturalLayer(toPackage)

        if (fromLayer == null || toLayer == null) return true // 无法识别则认为是预期的

        val restrictedTargets = boundaryRules[fromLayer] ?: return true
        return !restrictedTargets.contains(toLayer)
    }

    /**
     * 识别架构层级
     */
    private fun identifyArchitecturalLayer(packageName: String): String? {
        val lowerCasePackage = packageName.lowercase()
        return when {
            lowerCasePackage.contains(".controller.") ||
            lowerCasePackage.contains(".web.") ||
            lowerCasePackage.endsWith(".controller") ||
            lowerCasePackage.endsWith(".web") -> "controller"

            lowerCasePackage.contains(".service.") ||
            lowerCasePackage.endsWith(".service") -> "service"

            lowerCasePackage.contains(".repository.") ||
            lowerCasePackage.contains(".dao.") ||
            lowerCasePackage.endsWith(".repository") ||
            lowerCasePackage.endsWith(".dao") -> {
                if (lowerCasePackage.contains(".repository")) "repository" else "dao"
            }

            lowerCasePackage.contains(".entity.") ||
            lowerCasePackage.contains(".model.") ||
            lowerCasePackage.contains(".domain.") ||
            lowerCasePackage.endsWith(".entity") ||
            lowerCasePackage.endsWith(".model") ||
            lowerCasePackage.endsWith(".domain") -> "domain"

            else -> null
        }
    }

    /**
     * 统计使用次数
     */
    private fun countUsageCount(pojoQualifiedName: String, usingClass: PsiClass): Int {
        return usingClass.methods.sumOf { method ->
            var count = 0

            // 参数使用
            count += method.parameterList.parameters.count { param ->
                param.type.canonicalText == pojoQualifiedName
            }

            // 返回值使用
            if (method.returnType?.canonicalText == pojoQualifiedName) {
                count++
            }

            // 局部变量使用
            method.body?.accept(object : JavaRecursiveElementVisitor() {
                override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
                    super.visitDeclarationStatement(statement)
                    statement.declaredElements.forEach { element ->
                        if (element is PsiLocalVariable) {
                            if (element.type.canonicalText == pojoQualifiedName) {
                                count++
                            }
                        }
                    }
                }
            })

            count
        }
    }
}