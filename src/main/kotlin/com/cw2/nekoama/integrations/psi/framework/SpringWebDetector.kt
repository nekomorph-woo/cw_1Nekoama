package com.cw2.nekoama.integrations.psi.framework

import com.cw2.nekoama.ai.model.dependency.BusinessEntryPoint
import com.cw2.nekoama.ai.model.dependency.EntryType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.cw2.nekoama.integrations.psi.AnnotationPatternDetector
import com.cw2.nekoama.integrations.psi.HttpMappingInfo
import com.cw2.nekoama.integrations.psi.PSIAnnotationExtractor
import com.intellij.psi.JavaPsiFacade

/**
 * Spring Web框架检测器（无依赖版本）
 * 基于字符串模式检测Spring MVC相关注解和HTTP映射
 */
class SpringWebDetector(project: com.intellij.openapi.project.Project) : AbstractFrameworkDetector(project) {

    private val annotationDetector = AnnotationPatternDetector()
    private val annotationExtractor = PSIAnnotationExtractor()
    private val javaPsiFacade = JavaPsiFacade.getInstance(project)

    companion object {
        // Spring MVC相关注解模式
        private val SPRING_CONTROLLER_ANNOTATIONS = listOf(
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.stereotype.RestController"
        )

        private val SPRING_MAPPING_ANNOTATIONS = listOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping"
        )

        // 简化模式 - 扩展Controller命名模式
        private val CONTROLLER_PATTERNS = listOf(
            "controller", "restcontroller", "webcontroller", "apicontroller"
        )
        private val MAPPING_PATTERNS = listOf(
            "requestmapping", "getmapping", "postmapping", "putmapping",
            "deletemapping", "patchmapping"
        )

        // Spring相关类名模式 - 扩展匹配范围
        private val SPRING_CLASS_PATTERNS = listOf(
            "controller", "restcontroller", "web", "api", "endpoint", "resource"
        )

        // Spring Boot常见的包路径模式
        private val SPRING_PACKAGE_PATTERNS = listOf(
            ".controller.", ".rest.", ".web.", ".api.", ".endpoint.", ".resource."
        )
    }

    override fun detectControllers(scope: GlobalSearchScope): List<PsiClass> {
        val logger = com.cw2.nekoama.core.logging.NekoamaLogger
        logger.info("SpringWebDetector", "开始检测Spring Controller类")

        // 多级搜索策略
        val searchStrategies = listOf(
            SearchStrategy("allScope", GlobalSearchScope.allScope(project), true),
            SearchStrategy("projectScope", GlobalSearchScope.projectScope(project), false),
            SearchStrategy("projectScopeWithLibs", GlobalSearchScope.everythingScope(project), false)
        )

        for (strategy in searchStrategies) {
            try {
                logger.info("SpringWebDetector", "尝试使用搜索策略: ${strategy.name}")
                val controllers = detectControllersWithStrategy(strategy)

                if (controllers.isNotEmpty()) {
                    logger.info(
                        "SpringWebDetector",
                        "使用${strategy.name}策略成功找到${controllers.size}个Spring Controller"
                    )
                    return controllers
                } else {
                    logger.info("SpringWebDetector", "使用${strategy.name}策略未找到Controller，尝试下一个策略")
                }

            } catch (e: Exception) {
                logger.warn("SpringWebDetector", "搜索策略${strategy.name}失败: ${e.message}")
                if (strategy.isPrimary) {
                    // 主策略失败时记录详细错误信息
                    logSearchFailureDetails(e)
                }
            }
        }

        // 所有策略都失败，尝试增强的命名模式检测
        logger.warn("SpringWebDetector", "所有搜索策略失败，尝试增强的命名模式检测")
        return detectControllersByEnhancedNaming()
    }

    /**
     * 搜索策略数据类
     */
    private data class SearchStrategy(
        val name: String,
        val scope: GlobalSearchScope,
        val isPrimary: Boolean
    )

    /**
     * 使用指定策略检测Controller
     */
    private fun detectControllersWithStrategy(strategy: SearchStrategy): List<PsiClass> {
        val logger = com.cw2.nekoama.core.logging.NekoamaLogger

        val allClasses = javaPsiFacade.findClasses("*", strategy.scope)
        logger.debug("SpringWebDetector", "在${strategy.name}中找到${allClasses.size}个类")

        // 首先尝试基于注解的检测
        val annotationBasedControllers = allClasses.filter { psiClass ->
            isControllerByAnnotations(psiClass, SPRING_CONTROLLER_ANNOTATIONS)
        }

        if (annotationBasedControllers.isNotEmpty()) {
            logger.debug("SpringWebDetector", "通过注解检测找到${annotationBasedControllers.size}个Controller")
            return annotationBasedControllers.distinctBy { it.qualifiedName }
        }

        // 注解检测失败，尝试方法注解检测
        val methodBasedControllers = allClasses.filter { psiClass ->
            hasSpringMethodAnnotations(psiClass)
        }

        if (methodBasedControllers.isNotEmpty()) {
            logger.debug("SpringWebDetector", "通过方法注解检测找到${methodBasedControllers.size}个Controller")
            return methodBasedControllers.distinctBy { it.qualifiedName }
        }

        // 最后尝试命名模式检测
        val namingBasedControllers = allClasses.filter { psiClass ->
            isControllerByNaming(psiClass)
        }

        logger.debug("SpringWebDetector", "通过命名模式检测找到${namingBasedControllers.size}个Controller")
        return namingBasedControllers.distinctBy { it.qualifiedName }
    }

    /**
     * 增强的命名模式检测
     */
    private fun detectControllersByEnhancedNaming(): List<PsiClass> {
        val logger = com.cw2.nekoama.core.logging.NekoamaLogger

        try {
            val projectScope = GlobalSearchScope.projectScope(project)
            val allClasses = javaPsiFacade.findClasses("*", projectScope)

            val controllers = allClasses.filter { psiClass ->
                isControllerByEnhancedNaming(psiClass)
            }.distinctBy { it.qualifiedName }

            logger.info("SpringWebDetector", "增强命名模式检测找到${controllers.size}个Controller")
            return controllers

        } catch (e: Exception) {
            logger.error("SpringWebDetector", "增强命名模式检测失败", error = e)
            return emptyList()
        }
    }

    /**
     * 记录搜索失败的详细信息
     */
    private fun logSearchFailureDetails(e: Exception) {
        val logger = com.cw2.nekoama.core.logging.NekoamaLogger

        logger.error("SpringWebDetector", "主搜索策略失败详情", mapOf(
            "errorType" to e.javaClass.simpleName,
            "errorMessage" to (e.message ?: "Unknown error"),
            "projectBasePath" to (project.basePath ?: "null"),
            "projectName" to project.name
        ))

        // 检查Spring注解类的可用性
        try {
            val testScope = GlobalSearchScope.projectScope(project)
            val springClasses = listOf(
                "org.springframework.stereotype.Controller",
                "org.springframework.web.bind.annotation.RestController",
                "org.springframework.web.bind.annotation.RequestMapping"
            )

            springClasses.forEach { className ->
                val psiClass = javaPsiFacade.findClass(className, testScope)
                logger.debug("SpringWebDetector", "Spring类可用性检查: $className -> ${psiClass != null}")
            }
        } catch (testException: Exception) {
            logger.warn("SpringWebDetector", "Spring类可用性检查失败: ${testException.message}")
        }
    }

    override fun extractHttpMapping(method: PsiMethod): HttpMappingInfo? {
        val logger = com.cw2.nekoama.core.logging.NekoamaLogger

        try {
            // 查找HTTP映射注解
            val mappingAnnotations = method.annotations.filter { annotation ->
                isSpringMappingAnnotation(annotation)
            }

            if (mappingAnnotations.isEmpty()) {
                logger.debug("SpringWebDetector", "方法${method.name}未找到HTTP映射注解")
                return null
            }

            // 按优先级排序映射注解
            val prioritizedAnnotations = prioritizeMappingAnnotations(mappingAnnotations)

            // 使用优先级最高的映射注解
            val mainAnnotation = prioritizedAnnotations.first()
            val httpMethod = extractHttpMethodFromAnnotation(mainAnnotation)
            val path = extractPathFromAnnotation(mainAnnotation)
            val consumes = annotationExtractor.extractStringArrayAttribute(mainAnnotation, "consumes")
            val produces = annotationExtractor.extractStringArrayAttribute(mainAnnotation, "produces")

            val mappingInfo = HttpMappingInfo(httpMethod, path, consumes, produces)

            logger.debug(
                "SpringWebDetector",
                "成功提取HTTP映射: ${method.name} -> ${mappingInfo.method} ${mappingInfo.path}"
            )

            return mappingInfo

        } catch (e: Exception) {
            logger.error(
                "SpringWebDetector",
                "提取HTTP映射失败: ${method.name}",
                mapOf("error" to e.message)
            )

            // 尝试基于方法名称推断HTTP映射（降级方案）
            return inferHttpMappingFromMethodName(method)
        }
    }

    /**
     * 按优先级排序映射注解
     */
    private fun prioritizeMappingAnnotations(annotations: List<com.intellij.psi.PsiAnnotation>): List<com.intellij.psi.PsiAnnotation> {
        val priorityMap = mapOf(
            "getmapping" to 10,
            "postmapping" to 10,
            "putmapping" to 10,
            "deletemapping" to 10,
            "patchmapping" to 10,
            "requestmapping" to 8,
            "path" to 6,
            "get" to 5,
            "post" to 5,
            "put" to 5,
            "delete" to 5,
            "patch" to 5
        )

        return annotations.sortedByDescending { annotation ->
            val simpleName = annotationDetector.getSimpleAnnotationName(annotation).lowercase()
            priorityMap.entries.find { (pattern, _) ->
                simpleName.contains(pattern) || simpleName == pattern
            }?.value ?: 0
        }
    }

    /**
     * 从注解中提取路径信息 - 增强版本
     */
    private fun extractPathFromAnnotation(annotation: com.intellij.psi.PsiAnnotation): String {
        // 尝试多个属性名
        val pathAttributes = listOf("value", "path", "url", "uri")

        for (attr in pathAttributes) {
            val path = annotationExtractor.extractStringAttribute(annotation, attr)
            if (path != null && path.isNotEmpty()) {
                return normalizePath(path)
            }
        }

        // 尝试从注解的默认属性提取
        try {
            val defaultAttributes = annotation.parameterList.attributes
            for (attr in defaultAttributes) {
                if (attr.name == null || attr.name!!.isEmpty()) {
                    // 这是默认属性
                    val value = extractAnnotationAttributeValue(attr.value ?: continue)
                    if (value != null && value.isNotEmpty()) {
                        return normalizePath(value)
                    }
                }
            }
        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.debug(
                "SpringWebDetector",
                "提取注解默认属性失败: ${e.message}"
            )
        }

        return "/" // 默认路径
    }

    /**
     * 提取注解属性值
     */
    private fun extractAnnotationAttributeValue(attribute: com.intellij.psi.PsiAnnotationMemberValue): String? {
        return try {
            when (attribute) {
                is com.intellij.psi.PsiLiteralValue -> attribute.value?.toString()
                is com.intellij.psi.PsiArrayInitializerMemberValue -> {
                    attribute.initializers
                        .filterIsInstance<com.intellij.psi.PsiLiteralValue>()
                        .firstNotNullOfOrNull { it.value?.toString() }
                }
                else -> attribute.text?.removeSurrounding("\"")
            }
        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.debug(
                "SpringWebDetector",
                "提取注解属性值失败: ${e.message}"
            )
            null
        }
    }

    /**
     * 规范化路径
     */
    private fun normalizePath(path: String): String {
        var normalized = path.trim()

        // 处理空路径
        if (normalized.isEmpty()) {
            return "/"
        }

        // 移除前后引号
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length - 1).trim()
        }

        // 确保以/开头
        if (!normalized.startsWith("/")) {
            normalized = "/$normalized"
        }

        // 移除多余的/
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/")
        }

        return normalized
    }

    /**
     * 从方法名称推断HTTP映射（降级方案）
     */
    private fun inferHttpMappingFromMethodName(method: PsiMethod): HttpMappingInfo? {
        val methodName = method.name.lowercase()
        val logger = com.cw2.nekoama.core.logging.NekoamaLogger

        val httpMethod = when {
            methodName.startsWith("get") || methodName.startsWith("find") || methodName.startsWith("query") -> "GET"
            methodName.startsWith("create") || methodName.startsWith("add") || methodName.startsWith("insert") -> "POST"
            methodName.startsWith("update") || methodName.startsWith("modify") || methodName.startsWith("edit") -> "PUT"
            methodName.startsWith("delete") || methodName.startsWith("remove") -> "DELETE"
            methodName.startsWith("patch") -> "PATCH"
            else -> {
                logger.debug("SpringWebDetector", "无法从方法名推断HTTP方法: ${method.name}")
                return null
            }
        }

        // 尝试从类名推断基础路径
        val className = method.containingClass?.name?.lowercase() ?: ""
        val baseResource = extractResourceNameFromClassName(className)
        val path = if (baseResource.isNotEmpty()) "/$baseResource" else "/"

        logger.debug(
            "SpringWebDetector",
            "从方法名推断HTTP映射: ${method.name} -> $httpMethod $path"
        )

        return HttpMappingInfo(httpMethod, path, emptyList(), emptyList())
    }

    /**
     * 从类名提取资源名称
     */
    private fun extractResourceNameFromClassName(className: String): String {
        // 移除常见的Controller后缀
        val resourcePatterns = listOf(
            "controller", "restcontroller", "webcontroller", "apicontroller",
            "controllerk", "restcontrollerk"
        )

        var resourceName = className
        for (pattern in resourcePatterns) {
            if (resourceName.endsWith(pattern)) {
                resourceName = resourceName.substring(0, resourceName.length - pattern.length)
                break
            }
        }

        // 如果是复数形式，转换为单数
        if (resourceName.endsWith("s") && resourceName.length > 1) {
            val singular = resourceName.substring(0, resourceName.length - 1)
            if (singular.isNotEmpty()) {
                resourceName = singular
            }
        }

        return resourceName
    }

    override fun getFrameworkName(): String = "Spring Web"

    override fun getDetectionConfidence(): Double = 0.9

    override fun isController(psiClass: PsiClass): Boolean {
        return isControllerByAnnotations(psiClass, SPRING_CONTROLLER_ANNOTATIONS) ||
               isControllerByNaming(psiClass) ||
               hasSpringMethodAnnotations(psiClass)
    }

    override fun isHttpEndpoint(method: PsiMethod): Boolean {
        return isHttpEndpointByAnnotations(method, SPRING_MAPPING_ANNOTATIONS)
    }

    override fun getSupportedAnnotations(): List<String> {
        return SPRING_CONTROLLER_ANNOTATIONS + SPRING_MAPPING_ANNOTATIONS
    }

    override fun createBusinessEntryPoint(
        controller: PsiClass,
        method: PsiMethod,
        mapping: HttpMappingInfo
    ): BusinessEntryPoint {
        return createStandardBusinessEntryPoint(controller, method, mapping, EntryType.CONTROLLER)
    }

    override fun getEntryPointType(): EntryType = EntryType.CONTROLLER

    /**
     * 通过命名模式判断是否是Controller
     */
    private fun isControllerByNaming(psiClass: PsiClass): Boolean {
        val className = psiClass.name?.lowercase() ?: return false
        val qualifiedName = psiClass.qualifiedName?.lowercase() ?: return false

        // 检查类名模式
        val matchesClassName = CONTROLLER_PATTERNS.any { pattern ->
            className.endsWith(pattern) || className.contains(pattern)
        }

        // 检查包名模式
        val matchesPackage = SPRING_CLASS_PATTERNS.any { pattern ->
            qualifiedName.contains(pattern)
        }

        return matchesClassName || matchesPackage
    }

    /**
     * 增强的命名模式检测 - 更智能的Controller识别
     */
    private fun isControllerByEnhancedNaming(psiClass: PsiClass): Boolean {
        val className = psiClass.name?.lowercase() ?: return false
        val qualifiedName = psiClass.qualifiedName ?: return false
        val packageName = psiClass.containingClass?.qualifiedName?.substringBeforeLast('.')
            ?: qualifiedName.substringBeforeLast('.')

        val logger = com.cw2.nekoama.core.logging.NekoamaLogger

        // 1. 高置信度的类名匹配
        val highConfidenceClassNames = listOf(
            "controller", "restcontroller", "webcontroller", "apicontroller"
        )
        val matchesHighConfidenceClass = highConfidenceClassNames.any { pattern ->
            className.endsWith(pattern) || className.endsWith("${pattern}k") // Kotlin类后缀
        }

        if (matchesHighConfidenceClass) {
            logger.debug("SpringWebDetector", "高置信度类名匹配: $className")
            return true
        }

        // 2. 中置信度的包路径匹配
        val matchesHighConfidencePackage = SPRING_PACKAGE_PATTERNS.any { pattern ->
            packageName.contains(pattern)
        }

        // 3. RESTful API常见的命名模式
        val restfulPatterns = listOf("resource", "endpoint", "api", "handler", "service")
        val matchesRestfulPattern = restfulPatterns.any { pattern ->
            className.contains(pattern) && (
                className.startsWith("get") || className.startsWith("post") ||
                className.startsWith("put") || className.startsWith("delete") ||
                className.startsWith("find") || className.startsWith("create") ||
                className.startsWith("update") || className.startsWith("remove")
            )
        }

        // 4. 业务相关的Controller模式（收紧条件）
        val businessPatterns = listOf("user", "order", "product", "payment", "auth", "admin")
        val matchesBusinessPattern = businessPatterns.any { business ->
            className.contains(business) && (
                className.contains("controller") || className.contains("api") ||
                className.contains("resource") || className.contains("endpoint") ||
                className.contains("handler") || className.contains("web")
            ) && !className.contains("service") && !className.contains("repository") && !className.contains("dao")
        }

        // 5. 检查是否有HTTP相关的方法命名（使用更严格的条件）
        val hasHttpMethods = psiClass.methods.any { method ->
            val methodName = method.name.lowercase()
            val hasHttpAnnotations = method.annotations.any { annotation ->
                val annotationName = annotation.qualifiedName?.lowercase() ?: ""
                annotationName.contains("mapping") || annotationName.contains("request") ||
                annotationName.contains("getmapping") || annotationName.contains("postmapping") ||
                annotationName.contains("putmapping") || annotationName.contains("deletemapping")
            }

            // 如果有HTTP注解，直接认为是HTTP方法
            if (hasHttpAnnotations) return@any true

            // 否则检查方法名，但排除常见的业务方法模式
            (methodName.startsWith("get") || methodName.startsWith("post") ||
             methodName.startsWith("put") || methodName.startsWith("delete") ||
             methodName.startsWith("patch")) &&
            !methodName.startsWith("getby") && !methodName.startsWith("findby") &&
            !methodName.startsWith("save") && !methodName.startsWith("update") &&
            method.parameterList.parameters.isNotEmpty() // HTTP端点通常有参数
        }

        // 组合判断逻辑
        val result = when {
            matchesHighConfidenceClass -> {
                logger.info("SpringWebDetector", "高置信度匹配: $className - 类名匹配高置信度模式")
                true
            }
            matchesHighConfidencePackage && hasHttpMethods -> {
                logger.info("SpringWebDetector", "中置信度匹配: $className - 包路径匹配($packageName)且包含HTTP方法")
                true
            }
            matchesRestfulPattern && hasHttpMethods -> {
                logger.info("SpringWebDetector", "RESTful模式匹配: $className - RESTful命名且包含HTTP方法")
                true
            }
            matchesBusinessPattern && hasHttpMethods -> {
                logger.info("SpringWebDetector", "业务模式匹配: $className - 业务命名且包含HTTP方法")
                true
            }
            else -> {
                logger.debug("SpringWebDetector", "未匹配: $className - 不符合Controller模式")
                false
            }
        }

        if (result) {
            logger.info("SpringWebDetector", "增强命名模式检测匹配: $className (包: $packageName)")
        }

        return result
    }

    /**
     * 检查类是否有Spring方法注解
     */
    private fun hasSpringMethodAnnotations(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            method.annotations.any { annotation ->
                isSpringMappingAnnotation(annotation)
            }
        }
    }

    /**
     * 检查注解是否是Spring HTTP映射注解 - 增强版本
     */
    private fun isSpringMappingAnnotation(annotation: com.intellij.psi.PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName ?: return false
        val simpleName = annotationDetector.getSimpleAnnotationName(annotation).lowercase()

        // 1. 标准Spring注解匹配
        val standardMatch = SPRING_MAPPING_ANNOTATIONS.any { pattern ->
            qualifiedName.equals(pattern, ignoreCase = true) ||
            simpleName == pattern.substringAfterLast(".").lowercase()
        }

        if (standardMatch) {
            return true
        }

        // 2. 模糊匹配（处理注解简写或变体）
        val fuzzyMatch = MAPPING_PATTERNS.any { pattern ->
            simpleName.contains(pattern)
        }

        if (fuzzyMatch) {
            return true
        }

        // 3. 扩展的HTTP方法注解模式
        val extendedHttpPatterns = listOf(
            "getmapping", "postmapping", "putmapping", "deletemapping",
            "patchmapping", "requestmapping", "httpmethod"
        )

        val extendedMatch = extendedHttpPatterns.any { pattern ->
            simpleName == pattern || simpleName.contains(pattern)
        }

        if (extendedMatch) {
            return true
        }

        // 4. JAX-RS和其他Web框架的注解
        val jaxRsPatterns = listOf(
            "javax.ws.rs.GET", "javax.ws.rs.POST", "javax.ws.rs.PUT",
            "javax.ws.rs.DELETE", "javax.ws.rs.PATCH", "javax.ws.rs.Path",
            "jakarta.ws.rs.GET", "jakarta.ws.rs.POST", "jakarta.ws.rs.PUT",
            "jakarta.ws.rs.DELETE", "jakarta.ws.rs.PATCH", "jakarta.ws.rs.Path"
        )

        val jaxRsMatch = jaxRsPatterns.any { pattern ->
            qualifiedName.equals(pattern, ignoreCase = true) ||
            simpleName.equals(pattern.substringAfterLast("."), ignoreCase = true)
        }

        if (jaxRsMatch) {
            return true
        }

        // 5. 基于HTTP方法的简单注解名称
        val httpMethodNames = listOf("get", "post", "put", "delete", "patch", "head", "options")
        val httpMethodMatch = httpMethodNames.any { method ->
            simpleName.equals(method, ignoreCase = true) || simpleName.equals("${method}mapping", ignoreCase = true)
        }

        return httpMethodMatch
    }

    /**
     * 从注解中提取HTTP方法
     */
    private fun extractHttpMethodFromAnnotation(annotation: com.intellij.psi.PsiAnnotation): String {
        val annotationName = annotationDetector.getSimpleAnnotationName(annotation).lowercase()
        val qualifiedName = annotation.qualifiedName?.lowercase() ?: return "ANY"

        return when {
            annotationName.contains("getmapping") -> "GET"
            annotationName.contains("postmapping") -> "POST"
            annotationName.contains("putmapping") -> "PUT"
            annotationName.contains("deletemapping") -> "DELETE"
            annotationName.contains("patchmapping") -> "PATCH"
            annotationName.contains("requestmapping") -> {
                // 对于RequestMapping，尝试从method属性获取HTTP方法
                val methodValue = annotationExtractor.extractStringAttribute(annotation, "method")
                methodValue?.uppercase() ?: "ANY"
            }
            qualifiedName.contains("get") -> "GET"
            qualifiedName.contains("post") -> "POST"
            qualifiedName.contains("put") -> "PUT"
            qualifiedName.contains("delete") -> "DELETE"
            qualifiedName.contains("patch") -> "PATCH"
            else -> "ANY"
        }
    }

    /**
     * 检测Spring Boot应用的主要配置
     */
    fun detectSpringBootApplication(): PsiClass? {
        try {
            val scope = GlobalSearchScope.allScope(project)
            val springBootApplicationPattern = "org.springframework.boot.autoconfigure.SpringBootApplication"

            val classes = javaPsiFacade.findClasses("*", scope)
            return classes.find { psiClass ->
                psiClass.annotations.any { annotation ->
                    annotation.qualifiedName == springBootApplicationPattern
                }
            }
        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "SpringWebDetector",
                "检测SpringBootApplication失败",
                error = e
            )
            return null
        }
    }

    /**
     * 检测Spring配置类
     */
    fun detectSpringConfigurationClasses(): List<PsiClass> {
        try {
            val scope = GlobalSearchScope.allScope(project)
            val configurationPattern = "org.springframework.context.annotation.Configuration"

            val classes = javaPsiFacade.findClasses("*", scope)
            return classes.filter { psiClass ->
                psiClass.annotations.any { annotation ->
                    annotation.qualifiedName == configurationPattern
                }
            }
        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "SpringWebDetector",
                "检测Spring配置类失败",
                error = e
            )
            return emptyList()
        }
    }

    /**
     * 获取Spring框架的统计信息
     */
    fun getSpringFrameworkStats(): Map<String, Any> {
        val scope = GlobalSearchScope.allScope(project)
        val allClasses = javaPsiFacade.findClasses("*", scope)

        val controllerCount = allClasses.count { isController(it) }
        val springBootApplication = detectSpringBootApplication()
        val configurationClasses = detectSpringConfigurationClasses()

        return mapOf(
            "totalClasses" to allClasses.size,
            "controllerCount" to controllerCount,
            "hasSpringBootApplication" to (springBootApplication != null),
            "configurationClassCount" to configurationClasses.size,
            "springBootApplicationClass" to (springBootApplication?.qualifiedName ?: "null")
        )
    }

    /**
     * 验证Spring Web支持
     */
    fun validateSpringWebSupport(): Boolean {
        try {
            val scope = GlobalSearchScope.allScope(project)

            // 检查是否有Spring相关的类
            val springClasses = listOf(
                "org.springframework.stereotype.Controller",
                "org.springframework.web.bind.annotation.RequestMapping"
            )

            val foundSpringClasses = springClasses.mapNotNull { className ->
                javaPsiFacade.findClass(className, scope)?.qualifiedName
            }

            return foundSpringClasses.isNotEmpty()
        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "SpringWebDetector",
                "验证Spring Web支持失败",
                error = e
            )
            return false
        }
    }
}