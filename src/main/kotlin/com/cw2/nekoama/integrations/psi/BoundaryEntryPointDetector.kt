package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.BusinessEntryPoint
import com.cw2.nekoama.ai.model.dependency.EntryType
import com.cw2.nekoama.ai.model.dependency.ParameterInfo
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

/**
 * 业务边界入口点检测器（简化版本）
 *
 * 根据M2阶段重构要求，简化为4种常见类型的检测：
 * 1. Controller入口：@RestController, @RequestMapping，@PostMapping, @GetMapping, @PutMapping, @DeleteMapping
 * 2. 定时任务：@Scheduled
 * 3. Spring-Kafka：@KafkaListener
 * 4. 经典Java main方法
 *
 * 移除了复杂的多级搜索和降级机制，保留检测框架确保与现有分析流程兼容
 */
class BoundaryEntryPointDetector(private val project: Project) {

    private val logger = NekoamaLogger
    private val javaPsiFacade = JavaPsiFacade.getInstance(project)

    /**
     * 检测所有业务入口点（简化版本）
     *
     * 根据M2阶段重构要求，只检测4种常见类型：
     * 1. Controller入口
     * 2. 定时任务入口
     * 3. Spring-Kafka消息监听器入口
     * 4. Java main方法入口
     *
     * @param allClasses 需要检测的所有类，如果不提供则扫描整个项目
     */
    fun detectEntryPoints(allClasses: List<PsiClass>? = null): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        try {
            // 在ReadAction中执行所有索引和PSI操作
            com.intellij.openapi.application.ReadAction.run<RuntimeException> {
                ProgressManager.checkCanceled()

                val classesToAnalyze = allClasses ?: scanAllClasses()

                // 1. 检测Controller入口
                logger.info("BoundaryEntryPointDetector", "检测Controller入口...")
                entryPoints.addAll(detectControllerEntryPoints(classesToAnalyze))

                // 2. 检测定时任务入口
                logger.info("BoundaryEntryPointDetector", "检测定时任务入口...")
                entryPoints.addAll(detectScheduledEntryPoints(classesToAnalyze))

                // 3. 检测Spring-Kafka消息监听器入口
                logger.info("BoundaryEntryPointDetector", "检测Kafka消息监听器入口...")
                entryPoints.addAll(detectKafkaListenerEntryPoints(classesToAnalyze))

                // 4. 检测Java main方法入口
                logger.info("BoundaryEntryPointDetector", "检测Java main方法入口...")
                entryPoints.addAll(detectMainMethodEntryPoints(classesToAnalyze))
            }

            // 记录检测结果统计
            val controllerEntryPoints = entryPoints.filter { it.entryType == EntryType.CONTROLLER }
            val scheduledEntryPoints = entryPoints.filter { it.entryType == EntryType.SCHEDULED }
            val messageConsumerEntryPoints = entryPoints.filter { it.entryType == EntryType.MESSAGE_CONSUMER }
            val mainEntryPoints = entryPoints.filter { it.entryType == EntryType.MAIN }

            logger.info("BoundaryEntryPointDetector", "入口点检测完成，共检测到 ${entryPoints.size} 个入口点:")
            logger.info("BoundaryEntryPointDetector", "  - Controller入口: ${controllerEntryPoints.size} 个")
            logger.info("BoundaryEntryPointDetector", "  - 定时任务入口: ${scheduledEntryPoints.size} 个")
            logger.info("BoundaryEntryPointDetector", "  - Kafka监听器入口: ${messageConsumerEntryPoints.size} 个")
            logger.info("BoundaryEntryPointDetector", "  - Main方法入口: ${mainEntryPoints.size} 个")

        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("BoundaryEntryPointDetector", "检测业务入口点失败", error = e)
            throw e
        }

        return entryPoints.distinctBy { "${it.className}.${it.methodName}" }
    }

    /**
     * 向后兼容方法，保持与现有代码的兼容性
     */
    fun detectBusinessEntryPoints(): List<BusinessEntryPoint> {
        return detectEntryPoints()
    }

    /**
     * 扫描项目中的所有类
     */
    private fun scanAllClasses(): List<PsiClass> {
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val classes = mutableListOf<PsiClass>()

        FileTypeIndex.processFiles(
            StdFileTypes.JAVA,
            { virtualFile ->
                ProgressManager.checkCanceled()
                val psiFile = psiManager.findFile(virtualFile)
                if (psiFile is PsiJavaFile) {
                    classes.addAll(psiFile.classes)
                }
                true
            },
            scope
        )

        return classes
    }

    /**
     * 检测Controller入口点
     *
     * 检测规则：
     * - 类级别：@RestController, @Controller
     * - 方法级别：@RequestMapping, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping
     */
    private fun detectControllerEntryPoints(classes: List<PsiClass>): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()
        val controllerAnnotations = setOf(
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.stereotype.Controller",
            "javax.ws.rs.Path" // JAX-RS support
        )

        val httpMappingAnnotations = mapOf(
            "org.springframework.web.bind.annotation.RequestMapping" to "ANY",
            "org.springframework.web.bind.annotation.GetMapping" to "GET",
            "org.springframework.web.bind.annotation.PostMapping" to "POST",
            "org.springframework.web.bind.annotation.PutMapping" to "PUT",
            "org.springframework.web.bind.annotation.DeleteMapping" to "DELETE",
            "org.springframework.web.bind.annotation.PatchMapping" to "PATCH",
            // JAX-RS annotations
            "javax.ws.rs.GET" to "GET",
            "javax.ws.rs.POST" to "POST",
            "javax.ws.rs.PUT" to "PUT",
            "javax.ws.rs.DELETE" to "DELETE",
            "javax.ws.rs.PATCH" to "PATCH"
        )

        classes.forEach { psiClass ->
            ProgressManager.checkCanceled()

            // 检查类是否有Controller注解
            val hasControllerAnnotation = psiClass.annotations.any { annotation ->
                annotation.qualifiedName in controllerAnnotations
            }

            if (hasControllerAnnotation) {
                // 查找类中的HTTP映射方法
                psiClass.methods.forEach { method ->
                    val httpMapping = method.annotations.firstNotNullOfOrNull { annotation ->
                        val annotationName = annotation.qualifiedName
                        if (annotationName != null && httpMappingAnnotations.containsKey(annotationName)) {
                            val httpMethod = httpMappingAnnotations[annotationName] ?: "ANY"
                            val path = extractAnnotationValue(annotation, "value", "path")
                            "$httpMethod ${path ?: "/"}"
                        } else null
                    }

                    if (httpMapping != null) {
                        entryPoints.add(
                            BusinessEntryPoint(
                                className = psiClass.qualifiedName ?: "",
                                methodName = method.name,
                                entryType = EntryType.CONTROLLER,
                                annotations = method.annotations.mapNotNull { it.qualifiedName },
                                businessScenario = "HTTP-${httpMapping.split(" ")[0]}",
                                httpMapping = httpMapping,
                                parameters = extractParameterInfo(method)
                            )
                        )
                    }
                }
            }
        }

        return entryPoints
    }

    /**
     * 检测定时任务入口点
     *
     * 检测规则：
     * - @Scheduled注解的方法
     */
    private fun detectScheduledEntryPoints(classes: List<PsiClass>): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()
        val scheduledAnnotation = "org.springframework.scheduling.annotation.Scheduled"

        classes.forEach { psiClass ->
            ProgressManager.checkCanceled()

            psiClass.methods.forEach { method ->
                val hasScheduledAnnotation = method.annotations.any { annotation ->
                    annotation.qualifiedName == scheduledAnnotation
                }

                if (hasScheduledAnnotation) {
                    entryPoints.add(
                        BusinessEntryPoint(
                            className = psiClass.qualifiedName ?: "",
                            methodName = method.name,
                            entryType = EntryType.SCHEDULED,
                            annotations = method.annotations.mapNotNull { it.qualifiedName },
                            businessScenario = "定时任务",
                            httpMapping = null,
                            parameters = extractParameterInfo(method)
                        )
                    )
                }
            }
        }

        return entryPoints
    }

    /**
     * 检测Spring-Kafka消息监听器入口点
     *
     * 检测规则：
     * - @KafkaListener注解的方法
     */
    private fun detectKafkaListenerEntryPoints(classes: List<PsiClass>): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()
        val kafkaListenerAnnotation = "org.springframework.kafka.annotation.KafkaListener"

        classes.forEach { psiClass ->
            ProgressManager.checkCanceled()

            psiClass.methods.forEach { method ->
                val hasKafkaAnnotation = method.annotations.any { annotation ->
                    annotation.qualifiedName == kafkaListenerAnnotation
                }

                if (hasKafkaAnnotation) {
                    entryPoints.add(
                        BusinessEntryPoint(
                            className = psiClass.qualifiedName ?: "",
                            methodName = method.name,
                            entryType = EntryType.MESSAGE_CONSUMER,
                            annotations = method.annotations.mapNotNull { it.qualifiedName },
                            businessScenario = "Kafka消息处理",
                            httpMapping = null,
                            parameters = extractParameterInfo(method)
                        )
                    )
                }
            }
        }

        return entryPoints
    }

    /**
     * 检测Java main方法入口点
     *
     * 检测规则：
     * - public static void main(String[] args)方法
     */
    private fun detectMainMethodEntryPoints(classes: List<PsiClass>): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        classes.forEach { psiClass ->
            ProgressManager.checkCanceled()

            psiClass.methods.forEach { method ->
                // 检查是否是main方法
                if (method.name == "main" &&
                    method.hasModifierProperty(PsiModifier.PUBLIC) &&
                    method.hasModifierProperty(PsiModifier.STATIC) &&
                    method.returnType == PsiTypes.voidType()) {

                    val parameters = method.parameterList.parameters
                    if (parameters.size == 1 &&
                        parameters[0].type.canonicalText == "java.lang.String[]") {

                        entryPoints.add(
                            BusinessEntryPoint(
                                className = psiClass.qualifiedName ?: "",
                                methodName = method.name,
                                entryType = EntryType.MAIN,
                                annotations = emptyList(),
                                businessScenario = "应用程序入口",
                                httpMapping = null,
                                parameters = extractParameterInfo(method)
                            )
                        )
                    }
                }
            }
        }

        return entryPoints
    }

    /**
     * 提取注解属性值
     */
    private fun extractAnnotationValue(annotation: PsiAnnotation, vararg attributeNames: String): String? {
        for (attributeName in attributeNames) {
            val attribute = annotation.findAttributeValue(attributeName)
            if (attribute != null) {
                return when (attribute) {
                    is PsiLiteral -> attribute.value?.toString()
                    is PsiArrayInitializerMemberValue -> {
                        val values = attribute.initializers.mapNotNull {
                            (it as? PsiLiteral)?.value?.toString()
                        }
                        values.firstOrNull() // 返回第一个值
                    }
                    else -> attribute.text
                }
            }
        }
        return null
    }

    /**
     * 提取方法参数信息
     */
    private fun extractParameterInfo(method: PsiMethod): List<ParameterInfo> {
        return method.parameterList.parameters.map { parameter ->
            ParameterInfo(
                name = parameter.name ?: "param",
                type = parameter.type.canonicalText,
                annotations = parameter.annotations.mapNotNull { it.qualifiedName }
            )
        }
    }

    /**
     * 按业务场景分组入口点
     */
    fun groupEntryPointsByScenario(entryPoints: List<BusinessEntryPoint>): Map<String, List<BusinessEntryPoint>> {
        return entryPoints.groupBy { it.businessScenario }
    }

    /**
     * 按入口类型分组
     */
    fun groupEntryPointsByType(entryPoints: List<BusinessEntryPoint>): Map<EntryType, List<BusinessEntryPoint>> {
        return entryPoints.groupBy { it.entryType }
    }
}