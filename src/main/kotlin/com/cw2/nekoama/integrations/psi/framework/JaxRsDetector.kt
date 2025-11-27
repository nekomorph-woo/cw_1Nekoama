package com.cw2.nekoama.integrations.psi.framework

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.cw2.nekoama.integrations.psi.AnnotationPatternDetector
import com.cw2.nekoama.integrations.psi.PSIAnnotationExtractor
import com.intellij.psi.JavaPsiFacade

/**
 * JAX-RS框架检测器
 * 支持JAX-RS 2.x (javax.ws.rs) 和 Jakarta EE 9+ (jakarta.ws.rs)
 */
class JaxRsDetector(project: com.intellij.openapi.project.Project) : AbstractFrameworkDetector(project) {

    private val annotationDetector = AnnotationPatternDetector()
    private val annotationExtractor = PSIAnnotationExtractor()
    private val javaPsiFacade = JavaPsiFacade.getInstance(project)

    companion object {
        // JAX-RS 2.x 注解 (javax.ws.rs)
        private val JAX_RS_V2_ANNOTATIONS = listOf(
            "javax.ws.rs.Path",
            "javax.ws.rs.GET",
            "javax.ws.rs.POST",
            "javax.ws.rs.PUT",
            "javax.ws.rs.DELETE",
            "javax.ws.rs.PATCH",
            "javax.ws.rs.HEAD",
            "javax.ws.rs.OPTIONS",
            "javax.ws.rs.Consumes",
            "javax.ws.rs.Produces",
            "javax.ws.rs.ApplicationPath",
            "javax.ws.rs.core.Response"
        )

        // Jakarta EE 9+ 注解 (jakarta.ws.rs)
        private val JAKARTA_RS_ANNOTATIONS = listOf(
            "jakarta.ws.rs.Path",
            "jakarta.ws.rs.GET",
            "jakarta.ws.rs.POST",
            "jakarta.ws.rs.PUT",
            "jakarta.ws.rs.DELETE",
            "jakarta.ws.rs.PATCH",
            "jakarta.ws.rs.HEAD",
            "jakarta.ws.rs.OPTIONS",
            "jakarta.ws.rs.Consumes",
            "jakarta.ws.rs.Produces",
            "jakarta.ws.rs.ApplicationPath",
            "jakarta.ws.rs.core.Response"
        )

        // 简化模式
        private val JAX_RS_PATTERNS = listOf(
            "path", "get", "post", "put", "delete", "patch",
            "head", "options", "consumes", "produces", "applicationpath"
        )

        // HTTP方法注解
        private val HTTP_METHOD_ANNOTATIONS = listOf(
            "javax.ws.rs.GET", "javax.ws.rs.POST", "javax.ws.rs.PUT",
            "javax.ws.rs.DELETE", "javax.ws.rs.PATCH", "javax.ws.rs.HEAD", "javax.ws.rs.OPTIONS",
            "jakarta.ws.rs.GET", "jakarta.ws.rs.POST", "jakarta.ws.rs.PUT",
            "jakarta.ws.rs.DELETE", "jakarta.ws.rs.PATCH", "jakarta.ws.rs.HEAD", "jakarta.ws.rs.OPTIONS"
        )

        // JAX-RS相关类名模式
        private val JAX_RS_CLASS_PATTERNS = listOf(
            "resource", "endpoint", "service", "api", "rest"
        )
    }

    override fun detectControllers(scope: GlobalSearchScope): List<PsiClass> {
        try {
            val allClasses = javaPsiFacade.findClasses("*", scope)

            return allClasses.filter { psiClass ->
                isJaxRsResource(psiClass)
            }.distinctBy { it.qualifiedName }

        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "JaxRsDetector",
                "检测JAX-RS资源失败",
                error = e
            )
            return emptyList()
        }
    }

    override fun extractHttpMapping(method: PsiMethod): HttpMappingInfo? {
        try {
            // 检查方法是否有HTTP方法注解
            val httpMethodAnnotation = method.annotations.firstOrNull { annotation ->
                isHttpMethodAnnotation(annotation)
            } ?: return null

            val httpMethod = extractHttpMethodFromAnnotation(httpMethodAnnotation)

            // 获取类级别路径
            val classLevelPath = getClassLevelPath(method.containingClass ?: return null)

            // 获取方法级别路径
            val methodLevelPath = getMethodLevelPath(method)

            // 合并路径
            val fullPath = annotationExtractor.mergePaths(classLevelPath, methodLevelPath)

            // 提取consumes和produces
            val consumes = extractConsumes(method)
            val produces = extractProduces(method)

            return HttpMappingInfo(httpMethod, fullPath, consumes, produces)

        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "JaxRsDetector",
                "提取HTTP映射失败: ${method.name}",
                error = e
            )
            return null
        }
    }

    override fun getFrameworkName(): String = "JAX-RS"

    override fun getDetectionConfidence(): Double = 0.85

    override fun isController(psiClass: PsiClass): Boolean {
        return isJaxRsResource(psiClass)
    }

    override fun isHttpEndpoint(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            isHttpMethodAnnotation(annotation)
        }
    }

    override fun getSupportedAnnotations(): List<String> {
        return JAX_RS_V2_ANNOTATIONS + JAKARTA_RS_ANNOTATIONS
    }

    override fun createBusinessEntryPoint(
        controller: PsiClass,
        method: PsiMethod,
        mapping: HttpMappingInfo
    ): BusinessEntryPoint {
        return createStandardBusinessEntryPoint(controller, method, mapping, EntryType.REST_ENDPOINT)
    }

    override fun getEntryPointType(): EntryType = EntryType.REST_ENDPOINT

    /**
     * 检查类是否是JAX-RS资源类
     */
    private fun isJaxRsResource(psiClass: PsiClass): Boolean {
        return hasJaxRsPathAnnotation(psiClass) ||
               hasJaxRsMethodAnnotations(psiClass) ||
               isJaxRsResourceByNaming(psiClass)
    }

    /**
     * 检查类是否有JAX-RS Path注解
     */
    private fun hasJaxRsPathAnnotation(psiClass: PsiClass): Boolean {
        val allPathAnnotations = JAX_RS_V2_ANNOTATIONS.filter { it.contains("Path") } +
                                JAKARTA_RS_ANNOTATIONS.filter { it.contains("Path") }

        return psiClass.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName ?: return@any false
            allPathAnnotations.any { pathAnnotation ->
                qualifiedName.equals(pathAnnotation, ignoreCase = true)
            }
        }
    }

    /**
     * 检查类是否有JAX-RS方法注解
     */
    private fun hasJaxRsMethodAnnotations(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            method.annotations.any { annotation ->
                isHttpMethodAnnotation(annotation) ||
                isJaxRsAnnotation(annotation)
            }
        }
    }

    /**
     * 通过命名模式判断是否是JAX-RS资源类
     */
    private fun isJaxRsResourceByNaming(psiClass: PsiClass): Boolean {
        val className = psiClass.name?.lowercase() ?: return false
        val qualifiedName = psiClass.qualifiedName?.lowercase() ?: return false

        // 检查类名模式
        val matchesClassName = JAX_RS_CLASS_PATTERNS.any { pattern ->
            className.endsWith(pattern) || className.contains(pattern)
        }

        // 检查包名模式
        val matchesPackage = qualifiedName.containsAny("resource", "rest", "api", "endpoint")

        return matchesClassName || matchesPackage
    }

    /**
     * 检查注解是否是HTTP方法注解
     */
    private fun isHttpMethodAnnotation(annotation: com.intellij.psi.PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName ?: return false

        return HTTP_METHOD_ANNOTATIONS.any { httpAnnotation ->
            qualifiedName.equals(httpAnnotation, ignoreCase = true)
        }
    }

    /**
     * 检查注解是否是JAX-RS注解
     */
    private fun isJaxRsAnnotation(annotation: com.intellij.psi.PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName ?: return false
        val allJaxRsAnnotations = JAX_RS_V2_ANNOTATIONS + JAKARTA_RS_ANNOTATIONS

        return allJaxRsAnnotations.any { jaxRsAnnotation ->
            qualifiedName.equals(jaxRsAnnotation, ignoreCase = true)
        }
    }

    /**
     * 从注解中提取HTTP方法
     */
    private fun extractHttpMethodFromAnnotation(annotation: com.intellij.psi.PsiAnnotation): String {
        val qualifiedName = annotation.qualifiedName ?: return "GET"

        return when {
            qualifiedName.contains(".GET") -> "GET"
            qualifiedName.contains(".POST") -> "POST"
            qualifiedName.contains(".PUT") -> "PUT"
            qualifiedName.contains(".DELETE") -> "DELETE"
            qualifiedName.contains(".PATCH") -> "PATCH"
            qualifiedName.contains(".HEAD") -> "HEAD"
            qualifiedName.contains(".OPTIONS") -> "OPTIONS"
            else -> "GET"
        }
    }

    /**
     * 获取类级别的路径
     */
    private fun getClassLevelPath(psiClass: PsiClass): String {
        val pathAnnotation = psiClass.annotations.find { annotation ->
            val qualifiedName = annotation.qualifiedName ?: return@find false
            qualifiedName.contains(".Path")
        }

        return if (pathAnnotation != null) {
            annotationExtractor.extractStringAttribute(pathAnnotation, "value") ?: "/"
        } else {
            "/"
        }
    }

    /**
     * 获取方法级别的路径
     */
    private fun getMethodLevelPath(method: PsiMethod): String {
        val pathAnnotation = method.annotations.find { annotation ->
            val qualifiedName = annotation.qualifiedName ?: return@find false
            qualifiedName.contains(".Path")
        }

        return if (pathAnnotation != null) {
            annotationExtractor.extractStringAttribute(pathAnnotation, "value") ?: "/"
        } else {
            "/"
        }
    }

    /**
     * 提取方法的consumes信息
     */
    private fun extractConsumes(method: PsiMethod): List<String> {
        // 检查方法级别的Consumes注解
        val consumesAnnotation = method.annotations.find { annotation ->
            val qualifiedName = annotation.qualifiedName ?: return@find false
            qualifiedName.contains(".Consumes")
        }

        if (consumesAnnotation != null) {
            return annotationExtractor.extractStringArrayAttribute(consumesAnnotation, "value")
        }

        // 检查类级别的Consumes注解
        val classConsumesAnnotation = method.containingClass?.annotations?.find { annotation ->
            val qualifiedName = annotation.qualifiedName ?: return@find false
            qualifiedName.contains(".Consumes")
        }

        return if (classConsumesAnnotation != null) {
            annotationExtractor.extractStringArrayAttribute(classConsumesAnnotation, "value")
        } else {
            emptyList()
        }
    }

    /**
     * 提取方法的produces信息
     */
    private fun extractProduces(method: PsiMethod): List<String> {
        // 检查方法级别的Produces注解
        val producesAnnotation = method.annotations.find { annotation ->
            val qualifiedName = annotation.qualifiedName ?: return@find false
            qualifiedName.contains(".Produces")
        }

        if (producesAnnotation != null) {
            return annotationExtractor.extractStringArrayAttribute(producesAnnotation, "value")
        }

        // 检查类级别的Produces注解
        val classProducesAnnotation = method.containingClass?.annotations?.find { annotation ->
            val qualifiedName = annotation.qualifiedName ?: return@find false
            qualifiedName.contains(".Produces")
        }

        return if (classProducesAnnotation != null) {
            annotationExtractor.extractStringArrayAttribute(classProducesAnnotation, "value")
        } else {
            emptyList()
        }
    }

    /**
     * 检测JAX-RS应用配置类
     */
    fun detectJaxRsApplicationClasses(): List<PsiClass> {
        try {
            val scope = GlobalSearchScope.allScope(project)
            val classes = javaPsiFacade.findClasses("*", scope)

            return classes.filter { psiClass ->
                psiClass.annotations.any { annotation ->
                    val qualifiedName = annotation.qualifiedName ?: return@any false
                    qualifiedName.contains("ApplicationPath") ||
                    (qualifiedName.contains("javax.ws.rs") || qualifiedName.contains("jakarta.ws.rs")) &&
                    psiClass.superClass?.qualifiedName?.contains("Application") == true
                }
            }
        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "JaxRsDetector",
                "检测JAX-RS应用配置类失败",
                error = e
            )
            return emptyList()
        }
    }

    /**
     * 获取JAX-RS框架的统计信息
     */
    fun getJaxRsFrameworkStats(): Map<String, Any> {
        val scope = GlobalSearchScope.allScope(project)
        val allClasses = javaPsiFacade.findClasses("*", scope)

        val resourceCount = allClasses.count { isJaxRsResource(it) }
        val applicationClasses = detectJaxRsApplicationClasses()

        return mapOf(
            "totalClasses" to allClasses.size,
            "resourceCount" to resourceCount,
            "applicationClassCount" to applicationClasses.size,
            "applicationClasses" to applicationClasses.map { it.qualifiedName ?: "unknown" }
        )
    }

    /**
     * 验证JAX-RS支持
     */
    fun validateJaxRsSupport(): Boolean {
        try {
            val scope = GlobalSearchScope.allScope(project)

            // 检查是否有JAX-RS相关的类
            val jaxRsClasses = listOf(
                "javax.ws.rs.Path",
                "jakarta.ws.rs.Path"
            )

            val foundJaxRsClasses = jaxRsClasses.mapNotNull { className ->
                javaPsiFacade.findClass(className, scope)?.qualifiedName
            }

            return foundJaxRsClasses.isNotEmpty()
        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "JaxRsDetector",
                "验证JAX-RS支持失败",
                error = e
            )
            return false
        }
    }

    private fun String.containsAny(vararg substrings: String): Boolean {
        return substrings.any { this.contains(it, ignoreCase = true) }
    }
}