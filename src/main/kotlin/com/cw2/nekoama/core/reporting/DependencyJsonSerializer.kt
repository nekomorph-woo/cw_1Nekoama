package com.cw2.nekoama.core.reporting

import com.cw2.nekoama.ai.model.dependency.DependencyAnalysisResult
import com.cw2.nekoama.ai.model.dependency.CallEdgeType
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.serialization.JsonConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * 代码依赖分析JSON数据序列化器
 *
 * 提供标准的JSON数据导出功能，支持：
 * - 美化格式输出
 * - 压缩格式输出
 * - 自定义过滤和转换
 * - 元数据增强
 */
class DependencyJsonSerializer {

    private val logger = NekoamaLogger
    private val jsonConfig = JsonConfig

    /**
     * 导出美化格式的JSON文件
     */
    suspend fun exportPrettyJson(
        analysisResult: DependencyAnalysisResult,
        outputPath: Path
    ): JsonExportResult = withContext(Dispatchers.IO) {
        try {
            logger.info("JsonExport", "开始导出美化格式JSON: ${outputPath.fileName}")

            // 确保输出目录存在
            Files.createDirectories(outputPath.parent)

            // 增强分析结果元数据
            val enhancedResult = enhanceAnalysisResult(analysisResult)

            // 序列化为美化格式JSON
            val jsonString = jsonConfig.json.encodeToString(
                DependencyAnalysisResult.serializer(),
                enhancedResult
            )

            // 写入文件
            Files.writeString(
                outputPath,
                jsonString,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            logger.info("JsonExport", "美化格式JSON导出完成: ${outputPath.toAbsolutePath()}")

            JsonExportResult(
                success = true,
                outputPath = outputPath,
                fileSize = Files.size(outputPath),
                format = JsonFormat.PRETTY,
                message = "美化格式JSON导出成功"
            )

        } catch (e: Exception) {
            logger.error("JsonExport", "导出美化格式JSON失败", error = e)
            JsonExportResult(
                success = false,
                outputPath = outputPath,
                format = JsonFormat.PRETTY,
                message = "美化格式JSON导出失败: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 导出压缩格式的JSON文件
     */
    suspend fun exportCompactJson(
        analysisResult: DependencyAnalysisResult,
        outputPath: Path
    ): JsonExportResult = withContext(Dispatchers.IO) {
        try {
            logger.info("JsonExport", "开始导出压缩格式JSON: ${outputPath.fileName}")

            Files.createDirectories(outputPath.parent)

            // 增强分析结果元数据
            val enhancedResult = enhanceAnalysisResult(analysisResult)

            // 序列化为压缩格式JSON
            val jsonString = jsonConfig.json.encodeToString(
                DependencyAnalysisResult.serializer(),
                enhancedResult
            )

            Files.writeString(
                outputPath,
                jsonString,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            logger.info("JsonExport", "压缩格式JSON导出完成: ${outputPath.toAbsolutePath()}")

            JsonExportResult(
                success = true,
                outputPath = outputPath,
                fileSize = Files.size(outputPath),
                format = JsonFormat.COMPACT,
                message = "压缩格式JSON导出成功"
            )

        } catch (e: Exception) {
            logger.error("JsonExport", "导出压缩格式JSON失败", error = e)
            JsonExportResult(
                success = false,
                outputPath = outputPath,
                format = JsonFormat.COMPACT,
                message = "压缩格式JSON导出失败: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 导出过滤后的JSON数据
     */
    suspend fun exportFilteredJson(
        analysisResult: DependencyAnalysisResult,
        outputPath: Path,
        filterConfig: JsonFilterConfig
    ): JsonExportResult = withContext(Dispatchers.IO) {
        try {
            logger.info("JsonExport", "开始导出过滤JSON数据: ${outputPath.fileName}")

            Files.createDirectories(outputPath.parent)

            // 应用过滤器
            val filteredResult = applyFilters(analysisResult, filterConfig)

            // 增强过滤后的结果
            val enhancedResult = enhanceAnalysisResult(filteredResult)

            val jsonString = jsonConfig.json.encodeToString(
                DependencyAnalysisResult.serializer(),
                enhancedResult
            )

            Files.writeString(
                outputPath,
                jsonString,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            logger.info("JsonExport", "过滤JSON数据导出完成: ${outputPath.toAbsolutePath()}")

            JsonExportResult(
                success = true,
                outputPath = outputPath,
                fileSize = Files.size(outputPath),
                format = JsonFormat.FILTERED,
                message = "过滤JSON数据导出成功"
            )

        } catch (e: Exception) {
            logger.error("JsonExport", "导出过滤JSON数据失败", error = e)
            JsonExportResult(
                success = false,
                outputPath = outputPath,
                format = JsonFormat.FILTERED,
                message = "过滤JSON数据导出失败: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 导出G6可视化数据格式
     */
    suspend fun exportG6VisualizationData(
        analysisResult: DependencyAnalysisResult,
        outputPath: Path,
        visualizationType: G6VisualizationType
    ): JsonExportResult = withContext(Dispatchers.IO) {
        try {
            logger.info("JsonExport", "开始导出G6可视化数据: ${outputPath.fileName}")

            Files.createDirectories(outputPath.parent)

            // 转换为G6数据格式
            val g6Data = convertToG6Format(analysisResult, visualizationType)

            // 序列化G6数据
            val jsonString = jsonConfig.json.encodeToString(G6GraphData.serializer(), g6Data)

            Files.writeString(
                outputPath,
                jsonString,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            logger.info("JsonExport", "G6可视化数据导出完成: ${outputPath.toAbsolutePath()}")

            JsonExportResult(
                success = true,
                outputPath = outputPath,
                fileSize = Files.size(outputPath),
                format = JsonFormat.G6_VISUALIZATION,
                message = "G6可视化数据导出成功"
            )

        } catch (e: Exception) {
            logger.error("JsonExport", "导出G6可视化数据失败", error = e)
            JsonExportResult(
                success = false,
                outputPath = outputPath,
                format = JsonFormat.G6_VISUALIZATION,
                message = "G6可视化数据导出失败: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 增强分析结果的元数据
     */
    private fun enhanceAnalysisResult(result: DependencyAnalysisResult): DependencyAnalysisResult {
        val currentTimestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        return result.copy(
            metadata = result.metadata.copy(
                analysisTime = "${currentTimestamp.year}-${currentTimestamp.monthNumber}-${currentTimestamp.dayOfMonth} " +
                        "${currentTimestamp.hour}:${currentTimestamp.minute}:${currentTimestamp.second}"
            ),
            // 可以在这里添加其他增强信息
            analysisConfig = result.analysisConfig.copy(
                // 更新分析配置信息
            )
        )
    }

    /**
     * 应用过滤器
     */
    private fun applyFilters(
        result: DependencyAnalysisResult,
        filterConfig: JsonFilterConfig
    ): DependencyAnalysisResult {
        var filteredResult = result

        // 按包名过滤
        if (filterConfig.includePackages.isNotEmpty()) {
            filteredResult = filteredResult.copy(
                packages = filteredResult.packages.filter { pkg ->
                    filterConfig.includePackages.any { pattern ->
                        pkg.fullName.contains(pattern, ignoreCase = true)
                    }
                },
                classes = filteredResult.classes.filter { cls ->
                    filterConfig.includePackages.any { pattern ->
                        cls.qualifiedName.contains(pattern, ignoreCase = true)
                    }
                },
                methods = filteredResult.methods.filter { method ->
                    filterConfig.includePackages.any { pattern ->
                        method.qualifiedSignature.contains(pattern, ignoreCase = true)
                    }
                }
            )
        }

        // 排除特定包
        if (filterConfig.excludePackages.isNotEmpty()) {
            filteredResult = filteredResult.copy(
                packages = filteredResult.packages.filter { pkg ->
                    filterConfig.excludePackages.none { pattern ->
                        pkg.fullName.contains(pattern, ignoreCase = true)
                    }
                },
                classes = filteredResult.classes.filter { cls ->
                    filterConfig.excludePackages.none { pattern ->
                        cls.qualifiedName.contains(pattern, ignoreCase = true)
                    }
                },
                methods = filteredResult.methods.filter { method ->
                    filterConfig.excludePackages.none { pattern ->
                        method.qualifiedSignature.contains(pattern, ignoreCase = true)
                    }
                }
            )
        }

        // 按复杂度过滤
        if (filterConfig.minComplexityScore > 0) {
            filteredResult = filteredResult.copy(
                classes = filteredResult.classes.filter { cls ->
                    (cls.metrics.complexityScore ?: 0) >= filterConfig.minComplexityScore
                },
                codeSmells = filteredResult.codeSmells.filter { smell ->
                    // 根据过滤条件决定是否保留代码坏味道
                    true
                }
            )
        }

        // 按严重程度过滤代码坏味道
        if (filterConfig.minSeverity != null) {
            val severityLevels = listOf("INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL")
            val minLevelIndex = severityLevels.indexOf(filterConfig.minSeverity.name)

            filteredResult = filteredResult.copy(
                codeSmells = filteredResult.codeSmells.filter { smell ->
                    val levelIndex = severityLevels.indexOf(smell.severity.name)
                    levelIndex >= minLevelIndex
                }
            )
        }

        return filteredResult
    }

    /**
     * 转换为G6可视化格式
     */
    private fun convertToG6Format(
        result: DependencyAnalysisResult,
        visualizationType: G6VisualizationType
    ): G6GraphData {
        return when (visualizationType) {
            G6VisualizationType.PACKAGE_LEVEL -> convertToPackageLevelG6Data(result)
            G6VisualizationType.CLASS_LEVEL -> convertToClassLevelG6Data(result)
            G6VisualizationType.METHOD_LEVEL -> convertToMethodLevelG6Data(result)
            G6VisualizationType.SCENE_LEVEL -> convertToSceneLevelG6Data(result)
        }
    }

    /**
     * 转换为包级G6数据
     */
    private fun convertToPackageLevelG6Data(result: DependencyAnalysisResult): G6GraphData {
        val nodes = mutableListOf<G6Node>()
        val edges = mutableListOf<G6Edge>()

        // 添加包节点
        result.packages.forEach { pkg ->
            val complexity = calculatePackageComplexity(pkg, result)
            nodes.add(
                G6Node(
                    id = pkg.id,
                    label = pkg.name,
                    value = pkg.classCount,
                    category = "package",
                    style = G6NodeStyle(
                        fill = getComplexityColor(complexity),
                        stroke = "#5B8FF9",
                        lineWidth = 2
                    ),
                    size = 60,
                    data = mapOf(
                        "classCount" to pkg.classCount.toString(),
                        "fanIn" to pkg.metrics.fanIn.toString(),
                        "fanOut" to pkg.metrics.fanOut.toString(),
                        "instability" to pkg.metrics.instability.toString(),
                        "complexity" to complexity.toString()
                    )
                )
            )
        }

        // 添加依赖边
        result.packageDependencies.forEach { dep ->
            dep.dependencies.forEach { targetPkg ->
                edges.add(
                    G6Edge(
                        source = dep.packageName,
                        target = targetPkg,
                        label = "${dep.dependencyCount}",
                        style = G6EdgeStyle(
                            stroke = "#e2e2e2",
                            lineWidth = 2
                        )
                    )
                )
            }
        }

        return G6GraphData(nodes, edges)
    }

    /**
     * 转换为类级G6数据
     */
    private fun convertToClassLevelG6Data(result: DependencyAnalysisResult): G6GraphData {
        val nodes = mutableListOf<G6Node>()
        val edges = mutableListOf<G6Edge>()

        // 添加类节点
        result.classes.forEach { cls ->
            val complexity = cls.metrics.complexityScore
            nodes.add(
                G6Node(
                    id = cls.id,
                    label = cls.name,
                    category = getClassCategory(cls),
                    style = G6NodeStyle(
                        fill = getClassTypeColor(cls),
                        stroke = getComplexityBorderColor(complexity),
                        lineWidth = 2
                    ),
                    size = 40,
                    data = mapOf(
                        "type" to cls.type.toString(),
                        "methodCount" to cls.metrics.methodCount.toString(),
                        "fieldCount" to cls.metrics.fieldCount.toString(),
                        "complexity" to complexity.toString(),
                        "packageId" to cls.packageId,
                        "qualifiedName" to cls.qualifiedName,
                        "isTest" to cls.isTest.toString(),
                        "annotations" to cls.annotations.joinToString(",")
                    )
                )
            )
        }

        // 添加依赖边
        result.classDependencies.forEach { dep ->
            dep.dependencies.forEach { ref ->
                edges.add(
                    G6Edge(
                        source = dep.className,
                        target = ref.className,
                        label = ref.referenceType.toString(),
                        category = ref.referenceType.toString(),
                        style = G6EdgeStyle(
                            stroke = getReferenceTypeColor(ref.referenceType),
                            lineWidth = 1
                        )
                    )
                )
            }
        }

        return G6GraphData(nodes, edges)
    }

    /**
     * 转换为方法级G6数据（完整调用关系图实现）
     */
    private fun convertToMethodLevelG6Data(result: DependencyAnalysisResult): G6GraphData {
        val nodes = mutableSetOf<String>()
        val edges = mutableListOf<G6Edge>()
        val methodMap = result.methods.associateBy { it.id }

        // ✅ 使用 callGraph.edges 构建真正的调用关系图
        result.callGraph.edges.forEach { edge ->
            // 收集涉及的所有方法节点
            nodes.add(edge.source)
            nodes.add(edge.target)

            // 查找方法信息
            val sourceMethod = methodMap[edge.source]
            val targetMethod = methodMap[edge.target]

            // 创建调用关系边
            edges.add(
                G6Edge(
                    source = edge.source,
                    target = edge.target,
                    label = "${edge.callContext.callCount}次调用",
                    category = when(edge.type) {
                        CallEdgeType.METHOD_CALL -> "method_call"
                        CallEdgeType.CONSTRUCTOR_CALL -> "constructor_call"
                        CallEdgeType.SUPER_CALL -> "super_call"
                    },
                    style = G6EdgeStyle(
                        stroke = getCallTypeColor(edge.type),
                        lineWidth = (edge.weight / 2).coerceIn(1, 8),
                        endArrow = G6Arrow(
                            path = "M 0,0 L 8,4 L 0,8",
                            fill = getCallTypeColor(edge.type)
                        )
                    ),
                    data = mapOf(
                        "callCount" to edge.callContext.callCount.toString(),
                        "callType" to edge.type.name,
                        "depth" to edge.depth.toString(),
                        "weight" to edge.weight.toString(),
                        "sourceMethod" to (sourceMethod?.let { "${it.className}.${it.name}" } ?: edge.source),
                        "targetMethod" to (targetMethod?.let { "${it.className}.${it.name}" } ?: edge.target),
                        "callLocations" to edge.callContext.callLocations.size.toString()
                    )
                )
            )
        }

        // 构建方法节点，包含复杂度和调用信息
        val methodNodes = nodes.map { methodId ->
            val method = methodMap[methodId]
            val callCount = result.callGraph.edges.count { it.source == methodId }
            val calledByCount = result.callGraph.edges.count { it.target == methodId }

            G6Node(
                id = methodId,
                label = method?.let { "${it.className}.${it.name}" } ?: methodId,
                category = "method",
                style = G6NodeStyle(
                    fill = getNodeColorByComplexity(method?.metrics?.complexityScore ?: 0),
                    stroke = "#5B8FF9",
                    lineWidth = if (method?.metrics?.complexityScore ?: 0 > 50) 3 else 2
                ),
                size = calculateNodeSize(callCount, method?.metrics?.complexityScore ?: 0),
                data = mapOf(
                    "className" to (method?.className ?: ""),
                    "methodName" to (method?.name ?: ""),
                    "complexity" to (method?.metrics?.complexityScore ?: 0).toString(),
                    "linesOfCode" to (method?.metrics?.linesOfCode ?: 0).toString(),
                    "parameterCount" to (method?.metrics?.parameterCount ?: 0).toString(),
                    "callCount" to callCount.toString(),
                    "calledByCount" to calledByCount.toString()
                )
            )
        }

        return G6GraphData(methodNodes, edges)
    }

    /**
     * 转换为场景级G6数据
     */
    private fun convertToSceneLevelG6Data(result: DependencyAnalysisResult): G6GraphData {
        val nodes = mutableListOf<G6Node>()
        val edges = mutableListOf<G6Edge>()

        // 添加场景节点
        result.sceneDefinitions.forEach { scene ->
            nodes.add(
                G6Node(
                    id = scene.id,
                    label = scene.name,
                    category = "scene",
                    style = G6NodeStyle(
                        fill = "#FFE7BA",
                        stroke = "#FA8C16",
                        lineWidth = 2
                    ),
                    size = 50,
                    data = mapOf(
                        "category" to scene.category.toString(),
                        "description" to scene.description,
                        "entryMethodCount" to scene.entryMethods.size.toString(),
                        "coverage" to scene.coverage.toString()
                    )
                )
            )

            // 添加入口方法节点
            scene.entryMethods.forEachIndexed { index, methodId ->
                nodes.add(
                    G6Node(
                        id = "${scene.id}_entry_${index}",
                        label = "入口 ${index + 1}",
                        category = "entry",
                        style = G6NodeStyle(
                            fill = "#52C41A",
                            stroke = "#389E0D",
                            lineWidth = 2
                        ),
                        size = 30
                    )
                )

                edges.add(
                    G6Edge(
                        source = scene.id,
                        target = "${scene.id}_entry_${index}",
                        style = G6EdgeStyle(
                            stroke = "#52C41A",
                            lineWidth = 2
                        )
                    )
                )
            }
        }

        return G6GraphData(nodes, edges)
    }

    /**
     * 计算包复杂度
     */
    private fun calculatePackageComplexity(pkg: com.cw2.nekoama.ai.model.dependency.PackageInfo, result: DependencyAnalysisResult): Int {
        var totalComplexity = 0
        var classCount = 0

        result.classes.forEach { cls ->
            if (cls.packageId == pkg.id) {
                totalComplexity += cls.metrics.complexityScore
                classCount++
            }
        }

        return if (classCount > 0) totalComplexity / classCount else 0
    }

    /**
     * 获取复杂度颜色
     */
    private fun getComplexityColor(complexity: Int): String {
        return when {
            complexity > 50 -> "#FF4D4F"  // 高复杂度 - 红色
            complexity > 30 -> "#FA8C16"  // 中高复杂度 - 橙色
            complexity > 15 -> "#FAAD14"  // 中等复杂度 - 黄色
            else -> "#52C41A"             // 低复杂度 - 绿色
        }
    }

    /**
     * 根据复杂度获取边框颜色
     */
    private fun getComplexityBorderColor(complexity: Int): String {
        return when {
            complexity > 70 -> "#FF4D4F"
            complexity > 40 -> "#FA8C16"
            else -> "#5B8FF9"
        }
    }

    /**
     * 根据类类型获取颜色
     */
    private fun getClassTypeColor(cls: com.cw2.nekoama.ai.model.dependency.ClassInfo): String {
        return when {
            cls.annotations.any { it.contains("Controller", ignoreCase = true) } -> "#FF7875"
            cls.annotations.any { it.contains("Service", ignoreCase = true) } -> "#69C0FF"
            cls.annotations.any { it.contains("Repository", ignoreCase = true) } -> "#95DE64"
            cls.name.contains("DTO", ignoreCase = true) || cls.name.contains("VO", ignoreCase = true) -> "#FFD666"
            else -> "#C6E5FF"
        }
    }

    /**
     * 获取类类别
     */
    private fun getClassCategory(cls: com.cw2.nekoama.ai.model.dependency.ClassInfo): String {
        return when {
            cls.annotations.any { it.contains("Controller", ignoreCase = true) } -> "controller"
            cls.annotations.any { it.contains("Service", ignoreCase = true) } -> "service"
            cls.annotations.any { it.contains("Repository", ignoreCase = true) } -> "repository"
            cls.name.contains("DTO", ignoreCase = true) || cls.name.contains("VO", ignoreCase = true) -> "pojo"
            else -> "class"
        }
    }

    /**
     * 根据引用类型获取颜色
     */
    private fun getReferenceTypeColor(referenceType: com.cw2.nekoama.ai.model.dependency.ReferenceType): String {
        return when (referenceType) {
            com.cw2.nekoama.ai.model.dependency.ReferenceType.INHERITANCE -> "#FF4D4F"
            com.cw2.nekoama.ai.model.dependency.ReferenceType.IMPLEMENTATION -> "#FA8C16"
            com.cw2.nekoama.ai.model.dependency.ReferenceType.COMPOSITION -> "#52C41A"
            com.cw2.nekoama.ai.model.dependency.ReferenceType.AGGREGATION -> "#13C2C2"
            com.cw2.nekoama.ai.model.dependency.ReferenceType.ASSOCIATION -> "#1890FF"
            com.cw2.nekoama.ai.model.dependency.ReferenceType.DEPENDENCY -> "#722ED1"
            com.cw2.nekoama.ai.model.dependency.ReferenceType.ANNOTATION -> "#FAAD14"
        }
    }

    /**
     * 获取调用类型对应的颜色
     */
    private fun getCallTypeColor(callType: CallEdgeType): String {
        return when (callType) {
            CallEdgeType.METHOD_CALL -> "#1890FF"      // 蓝色 - 普通方法调用
            CallEdgeType.CONSTRUCTOR_CALL -> "#52C41A"  // 绿色 - 构造器调用
            CallEdgeType.SUPER_CALL -> "#FA8C16"        // 橙色 - 父类调用
        }
    }

    /**
     * 根据复杂度获取节点颜色
     */
    private fun getNodeColorByComplexity(complexity: Int): String {
        return when {
            complexity > 50 -> "#FF4D4F"    // 红色 - 极高复杂度
            complexity > 30 -> "#FA8C16"    // 橙色 - 高复杂度
            complexity > 15 -> "#FAAD14"    // 黄色 - 中等复杂度
            complexity > 5 -> "#52C41A"     // 绿色 - 低复杂度
            else -> "#1890FF"               // 蓝色 - 极低复杂度
        }
    }

    /**
     * 计算节点大小（基于调用次数和复杂度）
     */
    private fun calculateNodeSize(callCount: Int, complexity: Int): Int {
        val baseSize = 30
        val callBonus = (callCount.coerceAtMost(50) / 10).coerceAtMost(20)
        val complexityBonus = (complexity.coerceAtMost(100) / 20).coerceAtMost(15)

        return baseSize + callBonus + complexityBonus
    }
}

/**
 * JSON导出结果
 */
data class JsonExportResult(
    val success: Boolean,
    val outputPath: Path,
    val fileSize: Long = 0,
    val format: JsonFormat,
    val message: String,
    val error: Throwable? = null
)

/**
 * JSON格式枚举
 */
enum class JsonFormat {
    PRETTY,
    COMPACT,
    FILTERED,
    G6_VISUALIZATION
}

/**
 * JSON过滤配置
 */
data class JsonFilterConfig(
    val includePackages: List<String> = emptyList(),
    val excludePackages: List<String> = emptyList(),
    val minComplexityScore: Int = 0,
    val minSeverity: com.cw2.nekoama.ai.model.dependency.Severity? = null,
    val includeTestClasses: Boolean = false,
    val maxNodes: Int = Int.MAX_VALUE
)

/**
 * G6可视化类型
 */
enum class G6VisualizationType {
    PACKAGE_LEVEL,
    CLASS_LEVEL,
    METHOD_LEVEL,
    SCENE_LEVEL
}

/**
 * G6图数据结构
 */
@kotlinx.serialization.Serializable
data class G6GraphData(
    val nodes: List<G6Node>,
    val edges: List<G6Edge>
)

/**
 * G6节点
 */
@kotlinx.serialization.Serializable
data class G6Node(
    val id: String,
    val label: String? = null,
    val value: Int? = null,
    val category: String? = null,
    val style: G6NodeStyle,
    val size: Int,
    val data: Map<String, String>? = null
)

/**
 * G6节点样式
 */
@kotlinx.serialization.Serializable
data class G6NodeStyle(
    val fill: String,
    val stroke: String,
    val lineWidth: Int
)

/**
 * G6边
 */
@kotlinx.serialization.Serializable
data class G6Edge(
    val source: String,
    val target: String,
    val label: String? = null,
    val category: String? = null,
    val style: G6EdgeStyle,
    val data: Map<String, String>? = null
)

/**
 * G6边样式
 */
@kotlinx.serialization.Serializable
data class G6EdgeStyle(
    val stroke: String,
    val lineWidth: Int,
    val endArrow: G6Arrow? = null
)

/**
 * G6箭头
 */
@kotlinx.serialization.Serializable
data class G6Arrow(
    val path: String,
    val fill: String
)