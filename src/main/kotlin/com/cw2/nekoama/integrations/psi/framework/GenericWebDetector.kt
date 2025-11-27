package com.cw2.nekoama.integrations.psi.framework

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.search.GlobalSearchScope
import com.cw2.nekoama.integrations.psi.AnnotationPatternDetector
import com.cw2.nekoama.integrations.psi.PSIAnnotationExtractor
import com.intellij.psi.JavaPsiFacade

/**
 * 通用Web框架检测器
 * 基于命名模式、方法签名和启发式规则检测Web相关的类和方法
 */
class GenericWebDetector(project: com.intellij.openapi.project.Project) : AbstractFrameworkDetector(project) {

    private val annotationDetector = AnnotationPatternDetector()
    private val annotationExtractor = PSIAnnotationExtractor()
    private val javaPsiFacade = JavaPsiFacade.getInstance(project)

    companion object {
        // 扩展的Controller类识别模式
        private val CONTROLLER_CLASS_PATTERNS = listOf(
            "*Controller", "*RestController", "*Endpoint", "*Resource",
            "*Handler", "*WebService", "*Api", "*Action"
        )

        private val CONTROLLER_KEYWORDS = listOf(
            "controller", "restcontroller", "endpoint", "resource",
            "handler", "webservice", "api", "action", "service"
        )

        // HTTP方法识别模式
        private val HTTP_METHOD_PATTERNS = mapOf(
            "get" to "GET", "post" to "POST", "put" to "PUT",
            "delete" to "DELETE", "patch" to "PATCH", "head" to "HEAD",
            "options" to "OPTIONS", "handle" to "ANY", "process" to "ANY"
        )

        // Web相关的参数类型
        private val WEB_PARAMETER_TYPES = listOf(
            "httpservletrequest", "httpservletresponse", "httpsession",
            "model", "modelmap", "modelandview", "bindingresult",
            "requestbody", "requestparam", "pathvariable", "requestheader",
            "cookievalue", "requestattribute", "sessionattribute"
        )

        // Web相关的返回类型
        private val WEB_RETURN_TYPES = listOf(
            "responseentity", "modelandview", "model", "string",
            "response", "json", "xml", "html", "void"
        )

        // 通用Web注解模式
        private val GENERIC_WEB_ANNOTATIONS = listOf(
            "mapping", "request", "response", "path", "route",
            "endpoint", "web", "api", "rest", "service"
        )
    }

    override fun detectControllers(scope: GlobalSearchScope): List<PsiClass> {
        try {
            val allClasses = javaPsiFacade.findClasses("*", scope)

            return allClasses.filter { psiClass ->
                isGenericWebController(psiClass)
            }.distinctBy { it.qualifiedName }

        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "GenericWebDetector",
                "检测通用Web Controller失败",
                error = e
            )
            return emptyList()
        }
    }

    override fun extractHttpMapping(method: PsiMethod): HttpMappingInfo? {
        try {
            // 基于方法名推断HTTP方法
            val httpMethod = inferHttpMethodFromMethodName(method.name)

            // 尝试从注解中提取路径
            val annotationPath = extractPathFromAnnotations(method)

            // 基于方法名推断路径
            val nameInferredPath = extractPathFromMethodName(method.name)

            // 合并路径信息
            val path = if (annotationPath.isNotEmpty() && annotationPath != "/") {
                annotationPath
            } else {
                nameInferredPath
            }

            // 提取额外的参数信息
            val consumes = extractGenericConsumes(method)
            val produces = extractGenericProduces(method)

            return HttpMappingInfo(httpMethod, path, consumes, produces)

        } catch (e: Exception) {
            com.cw2.nekoama.core.logging.NekoamaLogger.error(
                "GenericWebDetector",
                "提取HTTP映射失败: ${method.name}",
                error = e
            )
            return null
        }
    }

    override fun getFrameworkName(): String = "Generic Web"

    override fun getDetectionConfidence(): Double = 0.6

    override fun isController(psiClass: PsiClass): Boolean {
        return isGenericWebController(psiClass)
    }

    override fun isHttpEndpoint(method: PsiMethod): Boolean {
        return isGenericHttpEndpoint(method)
    }

    override fun getSupportedAnnotations(): List<String> {
        return GENERIC_WEB_ANNOTATIONS
    }

    override fun createBusinessEntryPoint(
        controller: PsiClass,
        method: PsiMethod,
        mapping: HttpMappingInfo
    ): BusinessEntryPoint {
        return createStandardBusinessEntryPoint(controller, method, mapping, EntryType.WEB_ENDPOINT)
    }

    override fun getEntryPointType(): EntryType = EntryType.WEB_ENDPOINT

    /**
     * 检查类是否是通用Web Controller
     */
    private fun isGenericWebController(psiClass: PsiClass): Boolean {
        return isControllerByNaming(psiClass) ||
               hasHttpMethods(psiClass) ||
               hasWebAnnotations(psiClass) ||
               hasWebParameterTypes(psiClass)
    }

    /**
     * 通过命名模式判断是否是Controller
     */
    private fun isControllerByNaming(psiClass: PsiClass): Boolean {
        val className = psiClass.name ?: return false
        val qualifiedName = psiClass.qualifiedName ?: return false

        // 检查类名模式
        val matchesClassPattern = CONTROLLER_CLASS_PATTERNS.any { pattern ->
            if (pattern.startsWith("*")) {
                className.endsWith(pattern.substring(1))
            } else {
                className.equals(pattern, ignoreCase = true)
            }
        }

        // 检查包名模式
        val matchesPackagePattern = qualifiedName.lowercase().containsAny(*CONTROLLER_KEYWORDS.toTypedArray())

        return matchesClassPattern || matchesPackagePattern
    }

    /**
     * 检查类是否有HTTP方法
     */
    private fun hasHttpMethods(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            isGenericHttpEndpoint(method)
        }
    }

    /**
     * 检查类是否有Web相关注解
     */
    private fun hasWebAnnotations(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotationDetector.hasGenericWebAnnotations(psiClass) ||
            annotationDetector.hasHttpMappingAnnotations(psiClass)
        }
    }

    /**
     * 检查类是否有Web相关的参数类型
     */
    private fun hasWebParameterTypes(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            method.parameterList.parameters.any { param ->
                hasWebParameterType(param)
            }
        }
    }

    /**
     * 检查参数是否有Web相关类型
     */
    private fun hasWebParameterType(param: PsiParameter): Boolean {
        val paramType = param.type.canonicalText.lowercase()
        return WEB_PARAMETER_TYPES.any { paramType.contains(it) }
    }

    /**
     * 检查方法是否是通用HTTP端点
     */
    private fun isGenericHttpEndpoint(method: PsiMethod): Boolean {
        return hasHttpMethodSignature(method) ||
               hasWebAnnotations(method) ||
               hasWebParameterTypes(method) ||
               hasWebReturnType(method)
    }

    /**
     * 检查方法是否有HTTP方法签名
     */
    private fun hasHttpMethodSignature(method: PsiMethod): Boolean {
        val methodName = method.name.lowercase()

        // 检查方法名是否像HTTP方法
        val isHttpMethodName = HTTP_METHOD_PATTERNS.containsKey(methodName)

        // 检查方法名中是否包含HTTP相关词汇
        val hasHttpKeywords = methodName.containsAny("request", "response", "http", "web", "api")

        return isHttpMethodName || hasHttpKeywords
    }

    /**
     * 检查方法是否有Web相关注解
     */
    private fun hasWebAnnotations(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            annotationDetector.hasGenericWebAnnotations(method) ||
            annotationDetector.hasHttpMappingAnnotations(method)
        }
    }

    /**
     * 检查方法是否有Web相关参数类型
     */
    private fun hasWebParameterTypes(method: PsiMethod): Boolean {
        return method.parameterList.parameters.any { param ->
            hasWebParameterType(param)
        }
    }

    /**
     * 检查方法是否有Web相关返回类型
     */
    private fun hasWebReturnType(method: PsiMethod): Boolean {
        val returnType = method.returnType?.canonicalText?.lowercase() ?: return false
        return WEB_RETURN_TYPES.any { returnType.contains(it) }
    }

    /**
     * 从方法名推断HTTP方法
     */
    private fun inferHttpMethodFromMethodName(methodName: String): String {
        val lowerName = methodName.lowercase()

        return HTTP_METHOD_PATTERNS.entries.firstNotNullOfOrNull { (keyword, method) ->
            if (lowerName.contains(keyword)) method else null
        } ?: "GET"
    }

    /**
     * 从注解中提取路径
     */
    private fun extractPathFromAnnotations(method: PsiMethod): String {
        // 尝试从任何包含"path"或"value"的注解中提取路径
        val pathAnnotation = method.annotations.find { annotation ->
            val qualifiedName = annotation.qualifiedName?.lowercase() ?: return@find false
            qualifiedName.contains("path") || qualifiedName.contains("route")
        }

        return if (pathAnnotation != null) {
            annotationExtractor.extractStringAttribute(pathAnnotation, "value", "path") ?: "/"
        } else {
            "/"
        }
    }

    /**
     * 从方法名提取路径
     */
    private fun extractPathFromMethodName(methodName: String): String {
        // 简单的路径提取逻辑
        val name = methodName.lowercase()

        // 移除常见的前缀
        val cleanedName = name.removePrefix("get")
            .removePrefix("post")
            .removePrefix("put")
            .removePrefix("delete")
            .removePrefix("patch")
            .removePrefix("handle")
            .removePrefix("process")

        return if (cleanedName.isEmpty()) {
            "/"
        } else {
            // 转换驼峰命名为路径格式
            val path = cleanedName.replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()
            "/$path"
        }
    }

    /**
     * 提取通用的consumes信息
     */
    private fun extractGenericConsumes(method: PsiMethod): List<String> {
        // 尝试从注解中提取consumes信息
        val consumesAnnotation = method.annotations.find { annotation ->
            val qualifiedName = annotation.qualifiedName?.lowercase() ?: return@find false
            qualifiedName.contains("consumes")
        }

        return if (consumesAnnotation != null) {
            annotationExtractor.extractStringArrayAttribute(consumesAnnotation, "value")
        } else {
            // 基于参数类型推断
            method.parameterList.parameters
                .filter { param ->
                    val paramType = param.type.canonicalText.lowercase()
                    paramType.contains("json") || paramType.contains("xml")
                }
                .mapNotNull { param ->
                    when {
                        param.type.canonicalText.lowercase().contains("json") -> "application/json"
                        param.type.canonicalText.lowercase().contains("xml") -> "application/xml"
                        param.type.canonicalText.lowercase().contains("form") -> "application/x-www-form-urlencoded"
                        else -> null
                    }
                }
                .distinct()
        }
    }

    /**
     * 提取通用的produces信息
     */
    private fun extractGenericProduces(method: PsiMethod): List<String> {
        // 尝试从注解中提取produces信息
        val producesAnnotation = method.annotations.find { annotation ->
            val qualifiedName = annotation.qualifiedName?.lowercase() ?: return@find false
            qualifiedName.contains("produces")
        }

        return if (producesAnnotation != null) {
            annotationExtractor.extractStringArrayAttribute(producesAnnotation, "value")
        } else {
            // 基于返回类型推断
            val returnType = method.returnType?.canonicalText?.lowercase() ?: return emptyList()
            when {
                returnType.contains("string") -> listOf("text/plain")
                returnType.contains("json") -> listOf("application/json")
                returnType.contains("xml") -> listOf("application/xml")
                returnType.contains("html") -> listOf("text/html")
                returnType.contains("responseentity") -> listOf("application/json")
                else -> emptyList()
            }
        }
    }

    /**
     * 获取通用Web框架的统计信息
     */
    fun getGenericWebFrameworkStats(): Map<String, Any> {
        val scope = GlobalSearchScope.allScope(project)
        val allClasses = javaPsiFacade.findClasses("*", scope)

        val controllerCount = allClasses.count { isGenericWebController(it) }
        val endpointCount = allClasses.sumOf { psiClass ->
            psiClass.methods.count { isGenericHttpEndpoint(it) }
        }

        return mapOf(
            "totalClasses" to allClasses.size,
            "controllerCount" to controllerCount,
            "endpointCount" to endpointCount,
            "averageEndpointsPerController" to if (controllerCount > 0) endpointCount.toDouble() / controllerCount else 0.0
        )
    }

    private fun String.containsAny(vararg substrings: String): Boolean {
        return substrings.any { substring -> this.contains(substring, ignoreCase = true) }
    }
}