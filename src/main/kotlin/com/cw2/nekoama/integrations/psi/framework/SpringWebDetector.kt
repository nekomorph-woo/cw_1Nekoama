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

        // 简化模式
        private val CONTROLLER_PATTERNS = listOf("controller", "restcontroller")
        private val MAPPING_PATTERNS = listOf("requestmapping", "getmapping", "postmapping",
                                          "putmapping", "deletemapping", "patchmapping")

        // Spring相关类名模式
        private val SPRING_CLASS_PATTERNS = listOf(
            "controller", "restcontroller", "web", "api"
        )
    }

    override fun detectControllers(scope: GlobalSearchScope): List<PsiClass> {
        try {
            // 优先使用allScope进行更全面的搜索
            val allScope = GlobalSearchScope.allScope(project)
            val allClasses = javaPsiFacade.findClasses("*", allScope)

            val controllers = allClasses.filter { psiClass ->
                isController(psiClass)
            }.distinctBy { it.qualifiedName }

            com.cw2.nekoama.core.logging.NekoamaLogger.info(
                "SpringWebDetector",
                "在${allClasses.size}个类中找到${controllers.size}个Spring Controller"
            )

            return controllers

        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "SpringWebDetector",
                "使用allScope检测失败，尝试使用传入的scope",
                error = e
            )

            // 降级到传入的scope
            return try {
                val fallbackClasses = javaPsiFacade.findClasses("*", scope)
                fallbackClasses.filter { psiClass ->
                    isController(psiClass)
                }.distinctBy { it.qualifiedName }
            } catch (fallbackException: Exception) {
                com.cw2.nekoama.core.logging.NekoamaLogger.error(
                    "SpringWebDetector",
                    "降级搜索也失败",
                    error = fallbackException
                )
                emptyList()
            }
        }
    }

    override fun extractHttpMapping(method: PsiMethod): HttpMappingInfo? {
        try {
            // 查找HTTP映射注解
            val mappingAnnotations = method.annotations.filter { annotation ->
                isSpringMappingAnnotation(annotation)
            }

            if (mappingAnnotations.isEmpty()) {
                return null
            }

            // 使用第一个映射注解作为主要信息
            val mainAnnotation = mappingAnnotations.first()
            val httpMethod = extractHttpMethodFromAnnotation(mainAnnotation)
            val path = annotationExtractor.extractStringAttribute(mainAnnotation, "value", "path") ?: "/"
            val consumes = annotationExtractor.extractStringArrayAttribute(mainAnnotation, "consumes")
            val produces = annotationExtractor.extractStringArrayAttribute(mainAnnotation, "produces")

            return HttpMappingInfo(httpMethod, path, consumes, produces)

        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "SpringWebDetector",
                "提取HTTP映射失败: ${method.name}",
                error = e
            )
            return null
        }
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
     * 检查注解是否是Spring HTTP映射注解
     */
    private fun isSpringMappingAnnotation(annotation: com.intellij.psi.PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName ?: return false
        val simpleName = annotationDetector.getSimpleAnnotationName(annotation).lowercase()

        return SPRING_MAPPING_ANNOTATIONS.any { pattern ->
            qualifiedName.equals(pattern, ignoreCase = true) ||
            simpleName == pattern.substringAfterLast(".").lowercase()
        } || MAPPING_PATTERNS.any { pattern ->
            simpleName.contains(pattern)
        }
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