package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.BusinessEntryPoint
import com.cw2.nekoama.ai.model.dependency.EntryType
import com.cw2.nekoama.ai.model.dependency.ParameterInfo
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.cw2.nekoama.integrations.psi.framework.ControllerDetectionManager
import com.cw2.nekoama.integrations.psi.framework.DetectionResult

/**
 * 业务边界入口点检测器
 * 基于注解和命名模式自动识别业务场景入口
 * 重构版本：集成了新的ControllerDetectionManager架构
 */
class BoundaryEntryPointDetector(private val project: Project) {

    private val logger = NekoamaLogger
    private val javaPsiFacade = JavaPsiFacade.getInstance(project)
    private val controllerDetectionManager = ControllerDetectionManager(project)

    /**
     * 检测所有业务入口点
     */
    fun detectBusinessEntryPoints(): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        try {
            // 在ReadAction中执行所有索引和PSI操作
            com.intellij.openapi.application.ReadAction.run<RuntimeException> {
                ProgressManager.checkCanceled()

                // 0. 检查项目索引状态
                checkProjectIndexStatus()

                // 1. 检测HTTP Controller入口
                entryPoints.addAll(detectControllerEntryPoints())

                // 2. 检测Service入口
                entryPoints.addAll(detectServiceEntryPoints())

                // 3. 检测定时任务入口
                entryPoints.addAll(detectScheduledEntryPoints())

                // 4. 检测事件监听器入口
                entryPoints.addAll(detectEventListenerEntryPoints())

                // 5. 检测消息消费者入口
                entryPoints.addAll(detectMessageConsumerEntryPoints())

                // 6. 检测Main方法入口
                entryPoints.addAll(detectMainEntryPoints())

                // 7. 检测批处理入口
                entryPoints.addAll(detectBatchJobEntryPoints())
            }

            // 详细记录各类入口点的检测结果
            val controllerEntryPoints = entryPoints.filter { it.entryType == EntryType.CONTROLLER }
            val scheduledEntryPoints = entryPoints.filter { it.entryType == EntryType.SCHEDULED }
            val eventListenerEntryPoints = entryPoints.filter { it.entryType == EntryType.EVENT_LISTENER }
            val messageConsumerEntryPoints = entryPoints.filter { it.entryType == EntryType.MESSAGE_CONSUMER }
            val mainEntryPoints = entryPoints.filter { it.entryType == EntryType.MAIN }
            val serviceEntryPoints = entryPoints.filter { it.entryType == EntryType.SERVICE }

            logger.info("BoundaryEntryPointDetector", "检测到 ${entryPoints.size} 个业务入口点，分类统计:")
            logger.info("BoundaryEntryPointDetector", "  - HTTP Controller入口: ${controllerEntryPoints.size} 个")
            logger.info("BoundaryEntryPointDetector", "  - 服务入口: ${serviceEntryPoints.size} 个")
            logger.info("BoundaryEntryPointDetector", "  - 定时任务入口: ${scheduledEntryPoints.size} 个")
            logger.info("BoundaryEntryPointDetector", "  - 事件监听器入口: ${eventListenerEntryPoints.size} 个")
            logger.info("BoundaryEntryPointDetector", "  - 消息消费者入口: ${messageConsumerEntryPoints.size} 个")
            logger.info("BoundaryEntryPointDetector", "  - Main方法入口: ${mainEntryPoints.size} 个")

            // 记录具体的入口方法（仅记录前10个，避免日志过长）
            entryPoints.take(10).forEach { entryPoint ->
                logger.info("BoundaryEntryPointDetector", "  [${entryPoint.entryType}] ${entryPoint.className}.${entryPoint.methodName}")
            }
            if (entryPoints.size > 10) {
                logger.info("BoundaryEntryPointDetector", "  ... 还有 ${entryPoints.size - 10} 个入口方法")
            }

        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.error("BoundaryEntryPointDetector", "检测业务入口点失败", error = e)
            throw e
        }

        return entryPoints.distinctBy { "${it.className}.${it.methodName}" }
    }

    /**
     * 检查项目索引状态
     */
    private fun checkProjectIndexStatus() {
        try {
            logger.info("BoundaryEntryPointDetector", "检查项目索引状态")

            // 简化的项目状态检查
            if (project.basePath.isNullOrEmpty()) {
                logger.warn("BoundaryEntryPointDetector", "项目基础路径为空")
                return
            }

            logger.info("BoundaryEntryPointDetector", "项目基础路径: ${project.basePath}")

            // 简化的索引状态检查
            try {
                val dumbService = com.intellij.openapi.project.DumbService.getInstance(project)
                if (dumbService.isDumb) {
                    logger.info("BoundaryEntryPointDetector", "IDEA正在构建索引，可能影响检测结果")
                } else {
                    logger.info("BoundaryEntryPointDetector", "IDEA索引状态正常")
                }
            } catch (e: Exception) {
                logger.debug("BoundaryEntryPointDetector", "无法检查索引状态: ${e.message}")
            }

        } catch (e: Exception) {
            logger.warn("BoundaryEntryPointDetector", "检查项目索引状态时出错: ${e.message}")
        }
    }

    /**
     * 检测HTTP Controller入口点 - 重构版本
     * 使用新的ControllerDetectionManager架构
     */
    private fun detectControllerEntryPoints(): List<BusinessEntryPoint> {
        logger.info("BoundaryEntryPointDetector", "开始使用新架构检测Controller入口点")

        try {
            // 使用新的ControllerDetectionManager
            val detectionResult = controllerDetectionManager.detectAllControllers()

            // 转换框架检测结果为业务入口点
            val entryPoints = detectionResult.entryPoints.map { frameworkEntryPoint ->
                convertFrameworkEntryPoint(frameworkEntryPoint)
            }

            // 记录检测统计信息
            logDetectionStatistics(detectionResult)

            logger.info("BoundaryEntryPointDetector", "新架构Controller检测完成，共找到${entryPoints.size}个入口点")

            // 如果新架构检测结果过少，同时使用传统方法补充
            if (entryPoints.size < 10) {
                logger.info("BoundaryEntryPointDetector", "新架构检测结果较少(${entryPoints.size}个)，启用传统检测作为补充")
                val legacyEntryPoints = detectControllerEntryPointsLegacy()
                val allEntryPoints = (entryPoints + legacyEntryPoints).distinctBy { "${it.className}.${it.methodName}" }
                logger.info("BoundaryEntryPointDetector", "传统检测额外找到${legacyEntryPoints.size}个Controller入口点，总计${allEntryPoints.size}个")
                return allEntryPoints
            }

            return entryPoints

        } catch (e: Exception) {
            logger.error("BoundaryEntryPointDetector", "新架构Controller检测失败，降级到传统方法", error = e)

            // 降级到原始实现（保留备用方案）
            return detectControllerEntryPointsLegacy()
        }
    }

    /**
     * 传统Controller检测方法（备用方案）
     */
    private fun detectControllerEntryPointsLegacy(): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        // 使用多级搜索策略：首先尝试allScope（包含依赖库），然后降级到projectScope
        val allScope = GlobalSearchScope.allScope(project)
        val projectScope = GlobalSearchScope.projectScope(project)

        logger.info("BoundaryEntryPointDetector", "使用传统方法检测Controller入口点")

        // 主要搜索：使用allScope查找Spring注解
        var controllerAnnotation = javaPsiFacade.findClass("org.springframework.stereotype.Controller", allScope)
        var restControllerAnnotation =
            javaPsiFacade.findClass("org.springframework.web.bind.annotation.RestController", allScope)

        // 详细的依赖诊断日志
        logger.info(
            "BoundaryEntryPointDetector", "Spring Controller注解查找结果 (allScope): " +
                    "Controller=${controllerAnnotation != null}, RestController=${restControllerAnnotation != null}"
        )

        // 如果在allScope中未找到，尝试在projectScope中查找
        if (controllerAnnotation == null || restControllerAnnotation == null) {
            logger.info("BoundaryEntryPointDetector", "在allScope中未找到所有注解，尝试projectScope搜索")
            controllerAnnotation = controllerAnnotation ?: javaPsiFacade.findClass(
                "org.springframework.stereotype.Controller",
                projectScope
            )
            restControllerAnnotation = restControllerAnnotation
                ?: javaPsiFacade.findClass("org.springframework.web.bind.annotation.RestController", projectScope)
        }

        // 确定最终使用的搜索范围
        val effectiveScope = if (controllerAnnotation != null || restControllerAnnotation != null) {
            allScope
        } else {
            projectScope
        }

        logger.info("BoundaryEntryPointDetector", "使用有效搜索范围: $effectiveScope")

        if (controllerAnnotation != null) {
            val controllerClasses = AnnotatedElementsSearch.searchPsiClasses(controllerAnnotation, effectiveScope)
            val controllerCount = controllerClasses.count()
            logger.info("BoundaryEntryPointDetector", "找到 $controllerCount 个带@Controller注解的类")

            controllerClasses.forEach { psiClass ->
                logger.debug("BoundaryEntryPointDetector", "处理Controller类: ${psiClass.qualifiedName}")
                entryPoints.addAll(extractControllerEntryPoints(psiClass))
            }
        } else {
            logger.warn("BoundaryEntryPointDetector", "未找到@Controller注解，可能Spring依赖未正确加载")
        }

        if (restControllerAnnotation != null) {
            val restControllerClasses =
                AnnotatedElementsSearch.searchPsiClasses(restControllerAnnotation, effectiveScope)
            val restControllerCount = restControllerClasses.count()
            logger.info("BoundaryEntryPointDetector", "找到 $restControllerCount 个带@RestController注解的类")

            restControllerClasses.forEach { psiClass ->
                logger.debug("BoundaryEntryPointDetector", "处理RestController类: ${psiClass.qualifiedName}")
                entryPoints.addAll(extractControllerEntryPoints(psiClass))
            }
        } else {
            logger.warn("BoundaryEntryPointDetector", "未找到@RestController注解，可能Spring Web依赖未正确加载")
        }

        // 如果注解检测失败，尝试备用策略
        if (controllerAnnotation == null && restControllerAnnotation == null) {
            logger.info("BoundaryEntryPointDetector", "注解检测失败，尝试命名模式检测")
            entryPoints.addAll(detectControllersByNamingPattern(effectiveScope))
        }

        logger.info("BoundaryEntryPointDetector", "传统方法Controller检测完成，共找到 ${entryPoints.size} 个入口点")
        return entryPoints
    }

    /**
     * 转换框架检测结果为业务入口点
     */
    private fun convertFrameworkEntryPoint(frameworkEntry: BusinessEntryPoint): BusinessEntryPoint {
        return frameworkEntry // 现在使用统一的BusinessEntryPoint类型，无需转换
    }

    
  
    /**
     * 记录检测统计信息
     */
    private fun logDetectionStatistics(detectionResult: DetectionResult) {
        val summary = controllerDetectionManager.getDetectionSummary(detectionResult)
        logger.info("BoundaryEntryPointDetector", "=== Controller检测统计 ===")
        summary.lines().forEach { line ->
            logger.info("BoundaryEntryPointDetector", line)
        }

        // 记录检测质量评估
        val quality = controllerDetectionManager.getDetectionQuality(detectionResult)
        logger.info("BoundaryEntryPointDetector", "检测质量分数: ${quality["qualityScore"]}/100")
        logger.info("BoundaryEntryPointDetector", "平均置信度: ${quality["averageConfidence"]}")

        // 记录验证结果
        val issues = controllerDetectionManager.validateDetectionResult(detectionResult)
        if (issues.isNotEmpty()) {
            logger.warn("BoundaryEntryPointDetector", "检测结果验证发现${issues.size}个问题:")
            issues.forEach { issue ->
                logger.warn("BoundaryEntryPointDetector", "- $issue")
            }
        }
    }

    /**
     * 提取Controller类的入口点
     */
    private fun extractControllerEntryPoints(controllerClass: PsiClass): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        controllerClass.methods.forEach { method ->
            val httpMapping = extractHttpMapping(method)
            if (httpMapping != null) {
                val annotations = method.annotations.mapNotNull { it.qualifiedName }

                entryPoints.add(
                    BusinessEntryPoint(
                        className = controllerClass.qualifiedName ?: "",
                        methodName = method.name,
                        entryType = com.cw2.nekoama.ai.model.dependency.EntryType.CONTROLLER,
                        annotations = annotations,
                        businessScenario = determineControllerScenario(controllerClass, method, httpMapping),
                        httpMapping = httpMapping,
                        parameters = extractParameterInfo(method)
                    )
                )
            }
        }

        return entryPoints
    }

    /**
     * 提取HTTP映射信息
     */
    private fun extractHttpMapping(method: PsiMethod): String? {
        return try {
            com.intellij.openapi.application.ReadAction.compute<String?, Throwable> {
                ProgressManager.checkCanceled()

                val mappingAnnotations = mapOf(
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
                    "javax.ws.rs.PATCH" to "PATCH",
                    "javax.ws.rs.Path" to "ANY"
                )

                method.annotations.forEach { annotation ->
                    ProgressManager.checkCanceled()
                    val annotationName = annotation.qualifiedName
                    if (annotationName != null && mappingAnnotations.containsKey(annotationName)) {
                        val httpMethod = mappingAnnotations[annotationName] ?: "ANY"
                        val path = extractAnnotationValue(annotation, "value", "path")
                        return@compute "$httpMethod ${path ?: "/"}"
                    }
                }

                return@compute null
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.debug("BoundaryEntryPointDetector", "提取HTTP映射信息失败", mapOf("error" to e.message))
            null
        }
    }

    /**
     * 提取注解属性值
     */
    private fun extractAnnotationValue(
        annotation: PsiAnnotation,
        vararg attributeNames: String
    ): String? {
        return try {
            com.intellij.openapi.application.ReadAction.compute<String?, Throwable> {
                ProgressManager.checkCanceled()
                for (attrName in attributeNames) {
                    ProgressManager.checkCanceled()
                    val attribute = annotation.findAttributeValue(attrName)
                    if (attribute != null) {
                        return@compute when (attribute) {
                            is PsiLiteralValue -> attribute.value?.toString()
                            is PsiArrayInitializerMemberValue -> {
                                attribute.initializers
                                    .filterIsInstance<PsiLiteralValue>().firstNotNullOfOrNull { it.value?.toString() }
                            }

                            else -> attribute.text?.removeSurrounding("\"")
                        }
                    }
                }
                return@compute null
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.debug("BoundaryEntryPointDetector", "提取注解属性值失败", mapOf("error" to e.message))
            null
        }
    }

    /**
     * 确定Controller业务场景
     */
    private fun determineControllerScenario(
        controllerClass: PsiClass,
        method: PsiMethod,
        httpMapping: String
    ): String {
        val className = controllerClass.name?.lowercase() ?: ""
        val methodName = method.name.lowercase()
        val httpMethod = httpMapping.split(" ").firstOrNull() ?: ""

        return when {
            // 用户相关
            className.contains("user") && methodName.contains("login") -> "用户登录"
            className.contains("user") && methodName.contains("register") -> "用户注册"
            className.contains("user") && methodName.contains("logout") -> "用户登出"
            className.contains("user") && methodName.contains("profile") -> "用户资料管理"

            // 订单相关
            className.contains("order") && methodName.contains("create") -> "订单创建"
            className.contains("order") && methodName.contains("update") -> "订单更新"
            className.contains("order") && methodName.contains("cancel") -> "订单取消"
            className.contains("order") && methodName.contains("query") -> "订单查询"
            className.contains("order") && methodName.contains("pay") -> "订单支付"

            // 商品相关
            className.contains("product") && methodName.contains("search") -> "商品搜索"
            className.contains("product") && methodName.contains("detail") -> "商品详情"
            className.contains("product") && methodName.contains("list") -> "商品列表"
            className.contains("product") && methodName.contains("create") -> "商品创建"
            className.contains("product") && methodName.contains("update") -> "商品更新"

            // 支付相关
            className.contains("payment") && methodName.contains("process") -> "支付处理"
            className.contains("payment") && methodName.contains("callback") -> "支付回调"
            className.contains("payment") && methodName.contains("refund") -> "退款处理"

            // 文件相关
            className.contains("file") && methodName.contains("upload") -> "文件上传"
            className.contains("file") && methodName.contains("download") -> "文件下载"
            className.contains("file") && methodName.contains("delete") -> "文件删除"

            // 通知相关
            className.contains("notification") && methodName.contains("send") -> "通知发送"
            className.contains("notification") && methodName.contains("read") -> "通知阅读"

            // 根据HTTP方法判断
            httpMethod == "GET" && methodName.contains("list") -> "数据查询"
            httpMethod == "POST" && methodName.contains("create") -> "数据创建"
            httpMethod == "PUT" -> "数据更新"
            httpMethod == "DELETE" -> "数据删除"

            // 默认情况
            else -> "${className}-${methodName}"
        }
    }

    /**
     * 检测Service入口点
     */
    private fun detectServiceEntryPoints(): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        // 使用多级搜索策略
        val allScope = GlobalSearchScope.allScope(project)
        val projectScope = GlobalSearchScope.projectScope(project)

        val serviceAnnotations = listOf(
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Component"
        )

        logger.info("BoundaryEntryPointDetector", "开始检测Service入口点")

        serviceAnnotations.forEach { annotationName ->
            // 首先尝试allScope
            var annotationClass = javaPsiFacade.findClass(annotationName, allScope)
            var usedScope = "allScope"

            // 如果未找到，降级到projectScope
            if (annotationClass == null) {
                annotationClass = javaPsiFacade.findClass(annotationName, projectScope)
                usedScope = "projectScope"
            }

            if (annotationClass != null) {
                val effectiveScope = if (usedScope == "allScope") allScope else projectScope
                val annotatedElements = AnnotatedElementsSearch.searchPsiClasses(annotationClass, effectiveScope)
                val elementCount = annotatedElements.count()

                logger.info(
                    "BoundaryEntryPointDetector",
                    "找到 $elementCount 个带${annotationName.substringAfterLast(".")}注解的类 (使用$usedScope)"
                )

                annotatedElements.forEach { psiClass ->
                    entryPoints.addAll(extractServiceEntryPoints(psiClass))
                }
            } else {
                logger.debug("BoundaryEntryPointDetector", "未找到注解: $annotationName")
            }
        }

        logger.info("BoundaryEntryPointDetector", "Service入口点检测完成，共找到 ${entryPoints.size} 个入口点")
        return entryPoints
    }

    /**
     * 提取Service类的入口点
     */
    private fun extractServiceEntryPoints(serviceClass: PsiClass): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        serviceClass.methods.forEach { method ->
            // 只处理public方法，排除getter/setter和private方法
            if (method.hasModifierProperty(PsiModifier.PUBLIC) &&
                !isAccessorMethod(method) &&
                !method.hasModifierProperty(PsiModifier.STATIC)
            ) {

                val annotations = method.annotations.mapNotNull { it.qualifiedName }

                entryPoints.add(
                    BusinessEntryPoint(
                        className = serviceClass.qualifiedName ?: "",
                        methodName = method.name,
                        entryType = com.cw2.nekoama.ai.model.dependency.EntryType.SERVICE,
                        annotations = annotations,
                        businessScenario = determineServiceScenario(serviceClass, method),
                        parameters = extractParameterInfo(method)
                    )
                )
            }
        }

        return entryPoints
    }

    /**
     * 判断是否为访问器方法（getter/setter）
     */
    private fun isAccessorMethod(method: PsiMethod): Boolean {
        return try {
            com.intellij.openapi.application.ReadAction.compute<Boolean, Throwable> {
                ProgressManager.checkCanceled()
                val methodName = method.name
                (methodName.startsWith("get") || methodName.startsWith("set") || methodName.startsWith("is")) &&
                        method.parameterList.parametersCount <= 1
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.debug("BoundaryEntryPointDetector", "判断访问器方法失败", mapOf("error" to e.message))
            false
        }
    }

    /**
     * 确定Service业务场景
     */
    private fun determineServiceScenario(serviceClass: PsiClass, method: PsiMethod): String {
        val className = serviceClass.name?.lowercase() ?: ""
        val methodName = method.name.lowercase()

        return when {
            // 业务流程场景
            className.contains("order") && methodName.contains("process") -> "订单处理流程"
            className.contains("payment") && methodName.contains("process") -> "支付处理流程"
            className.contains("user") && methodName.contains("register") -> "用户注册流程"
            className.contains("user") && methodName.contains("auth") -> "用户认证流程"

            // 数据处理场景
            methodName.contains("validate") -> "数据验证"
            methodName.contains("transform") -> "数据转换"
            methodName.contains("calculate") -> "业务计算"
            methodName.contains("generate") -> "数据生成"

            // 默认情况
            else -> "${className}-${methodName}"
        }
    }

    /**
     * 检测定时任务入口点
     */
    private fun detectScheduledEntryPoints(): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()
        val scope = GlobalSearchScope.projectScope(project)

        val scheduledAnnotations = listOf(
            "org.springframework.scheduling.annotation.Scheduled",
            "org.springframework.scheduling.annotation.Schedules",
            "java.util.concurrent.ScheduledExecutorService",
            "javax.ejb.Schedule",
            "javax.ejb.Schedules"
        )

        scheduledAnnotations.forEach { annotationName ->
            val annotationClass = javaPsiFacade.findClass(annotationName, scope)
            if (annotationClass != null) {
                val annotatedElements = AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope)
                annotatedElements.forEach { psiClass ->
                    psiClass.methods.forEach { element ->
                        entryPoints.add(
                            BusinessEntryPoint(
                                className = psiClass.qualifiedName ?: "",
                                methodName = element.name,
                                entryType = com.cw2.nekoama.ai.model.dependency.EntryType.SCHEDULED,
                                annotations = element.annotations.mapNotNull { it.qualifiedName },
                                businessScenario = determineScheduledScenario(element),
                                parameters = extractParameterInfo(element)
                            )
                        )
                    }
                }
            }
        }

        return entryPoints
    }

    /**
     * 确定定时任务业务场景
     */
    private fun determineScheduledScenario(method: PsiMethod): String {
        val methodName = method.name.lowercase()

        return when {
            methodName.contains("backup") -> "数据备份任务"
            methodName.contains("cleanup") -> "数据清理任务"
            methodName.contains("sync") -> "数据同步任务"
            methodName.contains("report") -> "报表生成任务"
            methodName.contains("notification") -> "通知推送任务"
            methodName.contains("audit") -> "审计任务"
            methodName.contains("cache") -> "缓存刷新任务"
            else -> "定时任务-${method.name}"
        }
    }

    /**
     * 检测事件监听器入口点
     */
    private fun detectEventListenerEntryPoints(): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()
        val scope = GlobalSearchScope.projectScope(project)

        val eventListenerAnnotations = listOf(
            "org.springframework.context.event.EventListener",
            "org.springframework.kafka.annotation.KafkaListener",
            "org.springframework.amqp.rabbit.annotation.RabbitListener",
            "javax.jms.MessageListener",
            "org.springframework.jms.annotation.JmsListener"
        )

        eventListenerAnnotations.forEach { annotationName ->
            val annotationClass = javaPsiFacade.findClass(annotationName, scope)
            if (annotationClass != null) {
                val annotatedElements = AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope)
                annotatedElements.forEach { psiClass ->
                    psiClass.methods.forEach { element ->
                        entryPoints.add(
                            BusinessEntryPoint(
                                className = psiClass.qualifiedName ?: "",
                                methodName = element.name,
                                entryType = com.cw2.nekoama.ai.model.dependency.EntryType.EVENT_LISTENER,
                                annotations = element.annotations.mapNotNull { it.qualifiedName },
                                businessScenario = determineEventListenerScenario(element),
                                parameters = extractParameterInfo(element)
                            )
                        )
                    }
                }
            }
        }

        return entryPoints
    }

    /**
     * 确定事件监听器业务场景
     */
    private fun determineEventListenerScenario(method: PsiMethod): String {
        val className = method.containingClass?.name?.lowercase() ?: ""
        val methodName = method.name.lowercase()

        return when {
            className.contains("order") && methodName.contains("created") -> "订单创建事件处理"
            className.contains("order") && methodName.contains("updated") -> "订单更新事件处理"
            className.contains("user") && methodName.contains("registered") -> "用户注册事件处理"
            className.contains("user") && methodName.contains("logged") -> "用户登录事件处理"
            className.contains("payment") && methodName.contains("completed") -> "支付完成事件处理"
            methodName.contains("handle") && methodName.contains("event") -> "通用事件处理"
            else -> "事件处理-${method.name}"
        }
    }

    /**
     * 检测消息消费者入口点
     */
    private fun detectMessageConsumerEntryPoints(): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()
        val scope = GlobalSearchScope.projectScope(project)

        val messageConsumerAnnotations = listOf(
            "org.springframework.amqp.rabbit.annotation.RabbitListener",
            "org.springframework.kafka.annotation.KafkaListener",
            "org.springframework.jms.annotation.JmsListener",
            "javax.jms.MessageListener"
        )

        messageConsumerAnnotations.forEach { annotationName ->
            val annotationClass = javaPsiFacade.findClass(annotationName, scope)
            if (annotationClass != null) {
                val annotatedElements = AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope)
                annotatedElements.forEach { psiClass ->
                    psiClass.methods.forEach { element ->
                        entryPoints.add(
                            BusinessEntryPoint(
                                className = psiClass.qualifiedName ?: "",
                                methodName = element.name,
                                entryType = com.cw2.nekoama.ai.model.dependency.EntryType.MESSAGE_CONSUMER,
                                annotations = element.annotations.mapNotNull { it.qualifiedName },
                                businessScenario = determineMessageConsumerScenario(element),
                                parameters = extractParameterInfo(element)
                            )
                        )
                    }
                }
            }
        }

        return entryPoints
    }

    /**
     * 确定消息消费者业务场景
     */
    private fun determineMessageConsumerScenario(method: PsiMethod): String {
        val className = method.containingClass?.name?.lowercase() ?: ""
        val methodName = method.name.lowercase()

        return when {
            className.contains("order") && methodName.contains("process") -> "订单消息处理"
            className.contains("notification") && methodName.contains("send") -> "通知消息处理"
            className.contains("email") && methodName.contains("send") -> "邮件发送处理"
            className.contains("sms") && methodName.contains("send") -> "短信发送处理"
            methodName.contains("process") && methodName.contains("message") -> "通用消息处理"
            else -> "消息消费-${method.name}"
        }
    }

    /**
     * 检测Main方法入口点
     */
    private fun detectMainEntryPoints(): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        try {
            ProgressManager.checkCanceled()
            val scope = GlobalSearchScope.projectScope(project)

            // 搜索包含main方法的类
            val mainMethodCandidates = javaPsiFacade.findClasses("*", scope)
            mainMethodCandidates.forEach { psiClass ->
                ProgressManager.checkCanceled()
                psiClass.methods.forEach { method ->
                    if (method.name == "main" &&
                        method.hasModifierProperty(PsiModifier.PUBLIC) &&
                        method.hasModifierProperty(PsiModifier.STATIC) &&
                        method.parameterList.parametersCount == 1
                    ) {

                        val paramType = method.parameterList.parameters[0].type
                        if (paramType is PsiArrayType &&
                            paramType.componentType?.canonicalText == "java.lang.String"
                        ) {

                            entryPoints.add(
                                BusinessEntryPoint(
                                    className = psiClass.qualifiedName ?: "",
                                    methodName = "main",
                                    entryType = com.cw2.nekoama.ai.model.dependency.EntryType.MAIN,
                                    annotations = emptyList(),
                                    businessScenario = determineMainScenario(psiClass),
                                    parameters = extractParameterInfo(method)
                                )
                            )
                        }
                    }
                }
            }

        } catch (e: Exception) {
            logger.warn("BoundaryEntryPointDetector", "Main方法入口点检测失败", mapOf("error" to e.message))
            throw e
        }

        return entryPoints
    }

    /**
     * 确定Main方法业务场景
     */
    private fun determineMainScenario(mainClass: PsiClass): String {
        val className = mainClass.name?.lowercase() ?: ""

        return when {
            className.contains("application") || className.contains("app") -> "应用程序启动"
            className.contains("server") -> "服务器启动"
            className.contains("worker") -> "工作进程启动"
            className.contains("daemon") -> "守护进程启动"
            className.contains("migration") -> "数据迁移程序"
            className.contains("import") -> "数据导入程序"
            className.contains("export") -> "数据导出程序"
            className.contains("batch") -> "批处理程序"
            else -> "主程序启动-${mainClass.name}"
        }
    }

    /**
     * 检测批处理作业入口点
     */
    private fun detectBatchJobEntryPoints(): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()
        val scope = GlobalSearchScope.projectScope(project)

        val batchAnnotations = listOf(
            "org.springframework.batch.core.configuration.annotation.StepScope",
            "org.springframework.batch.core.configuration.annotation.JobScope",
            "org.springframework.batch.core.step.tasklet.Tasklet",
            "javax.batch.api.chunk.ItemReader",
            "javax.batch.api.chunk.ItemWriter",
            "javax.batch.api.chunk.ItemProcessor"
        )

        batchAnnotations.forEach { annotationName ->
            val annotationClass = javaPsiFacade.findClass(annotationName, scope)
            if (annotationClass != null) {
                val annotatedElements = AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope)
                annotatedElements.forEach { psiClass ->
                    psiClass.methods.forEach { method ->
                        entryPoints.add(
                            BusinessEntryPoint(
                                className = psiClass.qualifiedName ?: "",
                                methodName = method.name,
                                entryType = com.cw2.nekoama.ai.model.dependency.EntryType.SERVICE, // 批处理作业归类为Service类型
                                annotations = method.annotations.mapNotNull { it.qualifiedName },
                                businessScenario = determineBatchJobScenario(method),
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
     * 确定批处理作业业务场景
     */
    private fun determineBatchJobScenario(element: PsiElement): String {
        val name = when (element) {
            is PsiClass -> element.name ?: ""
            is PsiMethod -> element.name
            else -> ""
        }.lowercase()

        return when {
            name.contains("import") -> "数据导入批处理"
            name.contains("export") -> "数据导出批处理"
            name.contains("migration") -> "数据迁移批处理"
            name.contains("cleanup") -> "数据清理批处理"
            name.contains("backup") -> "数据备份批处理"
            name.contains("report") -> "报表生成批处理"
            name.contains("sync") -> "数据同步批处理"
            else -> "批处理作业-$name"
        }
    }

    /**
     * 检查Spring依赖是否正确加载
     */
    private fun checkSpringDependencies(allScope: GlobalSearchScope, projectScope: GlobalSearchScope) {
        // 检查核心Spring类是否存在
        val springClasses = listOf(
            "org.springframework.context.ApplicationContext" to "Spring Context",
            "org.springframework.stereotype.Component" to "Spring Stereotype",
            "org.springframework.web.bind.annotation.RestController" to "Spring Web MVC",
            "org.springframework.boot.autoconfigure.SpringBootApplication" to "Spring Boot"
        )

        logger.info("BoundaryEntryPointDetector", "开始Spring依赖检查")

        springClasses.forEach { (className, displayName) ->
            val allScopeResult = javaPsiFacade.findClass(className, allScope)
            val projectScopeResult = javaPsiFacade.findClass(className, projectScope)

            logger.debug("BoundaryEntryPointDetector", "依赖检查 $displayName:")
            logger.debug("BoundaryEntryPointDetector", "  allScope: ${allScopeResult != null}")
            logger.debug("BoundaryEntryPointDetector", "  projectScope: ${projectScopeResult != null}")

            if (allScopeResult != null || projectScopeResult != null) {
                logger.info("BoundaryEntryPointDetector", "$displayName: ✓ 可用")
            } else {
                logger.warn("BoundaryEntryPointDetector", "$displayName: ✗ 未找到")
            }
        }

        val allScopeMissing = springClasses.count { (className, _) ->
            javaPsiFacade.findClass(className, allScope) == null
        }

        val projectScopeMissing = springClasses.count { (className, _) ->
            javaPsiFacade.findClass(className, projectScope) == null
        }

        if (allScopeMissing == springClasses.size && projectScopeMissing == springClasses.size) {
            logger.warn("BoundaryEntryPointDetector", "所有Spring依赖均未找到，可能存在问题")
            logger.info(
                "BoundaryEntryPointDetector",
                "建议检查: 1) 项目是否包含Spring依赖 2) 依赖是否正确解析 3) IDEA索引是否构建完成"
            )
        } else {
            logger.info("BoundaryEntryPointDetector", "Spring依赖检查完成，发现可用依赖")
        }
    }

    /**
     * 通过命名模式检测Controller类（备用策略）
     */
    private fun detectControllersByNamingPattern(scope: GlobalSearchScope): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        try {
            logger.info("BoundaryEntryPointDetector", "开始通过命名模式检测Controller类")
            ProgressManager.checkCanceled()

            // 搜索所有Java类
            val allClasses = javaPsiFacade.findClasses("*", scope)
            val controllerCandidates = allClasses.filter { psiClass ->
                ProgressManager.checkCanceled()
                val className = psiClass.name?.lowercase() ?: ""
                val qualifiedName = psiClass.qualifiedName?.lowercase() ?: ""

                // 命名模式：以Controller结尾或在controller包中
                className.endsWith("controller") ||
                        qualifiedName.contains("controller") ||
                        qualifiedName.contains("web") ||
                        qualifiedName.contains("rest")
            }

            logger.info(
                "BoundaryEntryPointDetector",
                "通过命名模式找到 ${controllerCandidates.size} 个候选Controller类"
            )

            controllerCandidates.forEach { psiClass ->
                ProgressManager.checkCanceled()
                // 检查类中是否有类似HTTP mapping的方法
                val httpMethods = psiClass.methods.filter { method ->
                    val methodName = method.name.lowercase()
                    methodName in listOf("get", "post", "put", "delete", "patch") ||
                            method.annotations.any { annotation ->
                                val annotationName = annotation.qualifiedName?.lowercase() ?: ""
                                annotationName.contains("mapping") ||
                                        annotationName.contains("request") ||
                                        annotationName.contains("path")
                            }
                }

                if (httpMethods.isNotEmpty()) {
                    logger.debug("BoundaryEntryPointDetector", "通过命名模式识别Controller: ${psiClass.qualifiedName}")
                    httpMethods.forEach { method ->
                        entryPoints.add(
                            BusinessEntryPoint(
                                className = psiClass.qualifiedName ?: "",
                                methodName = method.name,
                                entryType = com.cw2.nekoama.ai.model.dependency.EntryType.CONTROLLER,
                                annotations = method.annotations.mapNotNull { it.qualifiedName },
                                businessScenario = determineControllerScenario(psiClass, method, "ANY /"),
                                parameters = extractParameterInfo(method)
                            )
                        )
                    }
                }
            }

            logger.info("BoundaryEntryPointDetector", "命名模式检测完成，找到 ${entryPoints.size} 个入口点")

        } catch (e: Exception) {
            logger.error("BoundaryEntryPointDetector", "命名模式检测失败", error = e)
            throw e
        }

        return entryPoints
    }

    /**
     * 提取参数信息
     */
    private fun extractParameterInfo(method: PsiMethod): List<ParameterInfo> {
        return try {
            com.intellij.openapi.application.ReadAction.compute<List<ParameterInfo>, Throwable> {
                ProgressManager.checkCanceled()
                method.parameterList.parameters.map { parameter ->
                    ProgressManager.checkCanceled()
                    ParameterInfo(
                        name = parameter.name ?: "param",
                        type = parameter.type.canonicalText,
                        annotations = parameter.annotations.mapNotNull { it.qualifiedName }
                    )
                }
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.debug("BoundaryEntryPointDetector", "提取参数信息失败", mapOf("error" to e.message))
            emptyList()
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