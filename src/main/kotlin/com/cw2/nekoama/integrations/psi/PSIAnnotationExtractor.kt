package com.cw2.nekoama.integrations.psi

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiLiteralValue

/**
 * HTTP映射信息数据类
 */
data class HttpMappingInfo(
    val method: String,
    val path: String,
    val consumes: List<String> = emptyList(),
    val produces: List<String> = emptyList()
) {
    override fun toString(): String = "$method $path"
}

/**
 * PSI注解属性提取器
 * 直接解析PSI树，提取注解属性值，无需加载注解类
 */
class PSIAnnotationExtractor {

    /**
     * 从注解中提取字符串属性值
     * 支持多种属性名称，按优先级顺序检查
     */
    fun extractStringAttribute(annotation: PsiAnnotation, vararg attributeNames: String): String? {
        for (attrName in attributeNames) {
            val attribute = annotation.findAttributeValue(attrName) ?: continue
            val value = extractStringValue(attribute)
            if (!value.isNullOrEmpty()) {
                return value
            }
        }
        return null
    }

    /**
     * 从注解中提取字符串数组属性值
     */
    fun extractStringArrayAttribute(annotation: PsiAnnotation, vararg attributeNames: String): List<String> {
        for (attrName in attributeNames) {
            val attribute = annotation.findAttributeValue(attrName) ?: continue
            val values = extractStringArrayValue(attribute)
            if (values.isNotEmpty()) {
                return values
            }
        }
        return emptyList()
    }

    /**
     * 从方法中提取HTTP映射信息
     */
    fun extractHttpMapping(method: com.intellij.psi.PsiMethod): HttpMappingInfo? {
        val mappingAnnotations = method.annotations.filter { annotation ->
            AnnotationPatternDetector().isHttpMappingAnnotation(annotation)
        }

        if (mappingAnnotations.isEmpty()) return null

        // 使用第一个HTTP映射注解作为主要信息
        val mainAnnotation = mappingAnnotations.first()
        val httpMethod = AnnotationPatternDetector().extractHttpMethod(mainAnnotation) ?: "ANY"
        val path = extractStringAttribute(mainAnnotation, "value", "path") ?: "/"

        // 提取consumes和produces信息
        val consumes = extractStringArrayAttribute(mainAnnotation, "consumes")
        val produces = extractStringArrayAttribute(mainAnnotation, "produces")

        return HttpMappingInfo(httpMethod, path, consumes, produces)
    }

    /**
     * 从多个注解中提取所有HTTP映射信息
     */
    fun extractAllHttpMappings(method: com.intellij.psi.PsiMethod): List<HttpMappingInfo> {
        val mappingAnnotations = method.annotations.filter { annotation ->
            AnnotationPatternDetector().isHttpMappingAnnotation(annotation)
        }

        return mappingAnnotations.mapNotNull { annotation ->
            val httpMethod = AnnotationPatternDetector().extractHttpMethod(annotation) ?: return@mapNotNull null
            val path = extractStringAttribute(annotation, "value", "path") ?: "/"
            val consumes = extractStringArrayAttribute(annotation, "consumes")
            val produces = extractStringArrayAttribute(annotation, "produces")

            HttpMappingInfo(httpMethod, path, consumes, produces)
        }
    }

    /**
     * 提取字符串值
     */
    private fun extractStringValue(attribute: PsiAnnotationMemberValue): String? {
        return when (attribute) {
            is PsiLiteralValue -> {
                // 处理字面量值
                when (val value = attribute.value) {
                    is String -> value
                    else -> value?.toString()
                }
            }
            is PsiArrayInitializerMemberValue -> {
                // 如果是数组，取第一个非空值
                attribute.initializers
                    .filterIsInstance<PsiLiteralValue>()
                    .mapNotNull { it.value?.toString() }
                    .firstOrNull()
            }
            else -> {
                // 处理其他类型的PSI表达式
                val text = attribute.text
                if (text != null) {
                    // 移除引号和其他格式化字符
                    text.removeSurrounding("\"").removeSurrounding("'").trim()
                } else {
                    null
                }
            }
        }
    }

    /**
     * 提取字符串数组值
     */
    private fun extractStringArrayValue(attribute: PsiAnnotationMemberValue): List<String> {
        return when (attribute) {
            is PsiArrayInitializerMemberValue -> {
                // 处理数组初始化
                attribute.initializers.mapNotNull { element ->
                    when (element) {
                        is PsiLiteralValue -> {
                            when (val value = element.value) {
                                is String -> value
                                else -> value?.toString()
                            }
                        }
                        else -> {
                            element.text?.removeSurrounding("\"")?.removeSurrounding("'")?.trim()
                        }
                    }
                }
            }
            is PsiLiteralValue -> {
                // 如果是单个字面量，包装成列表
                val value = extractStringValue(attribute)
                if (value != null) listOf(value) else emptyList()
            }
            else -> {
                // 尝试解析文本形式的数组
                val text = attribute.text
                if (text != null && text.contains("{")) {
                    // 简单的文本解析（适用于简单情况）
                    text.trim('{', '}')
                        .split(',')
                        .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                        .filter { it.isNotEmpty() }
                } else {
                    val value = extractStringValue(attribute)
                    if (value != null) listOf(value) else emptyList()
                }
            }
        }
    }

    /**
     * 检查注解是否有指定属性
     */
    fun hasAttribute(annotation: PsiAnnotation, attributeName: String): Boolean {
        return annotation.findAttributeValue(attributeName) != null
    }

    /**
     * 获取注解的所有属性名称
     */
    fun getAttributeNames(annotation: PsiAnnotation): List<String> {
        return annotation.parameterList.attributes.mapNotNull { it.name }
    }

    /**
     * 提取所有字符串属性（调试用）
     */
    fun extractAllStringAttributes(annotation: PsiAnnotation): Map<String, String?> {
        val attributes = mutableMapOf<String, String?>()

        annotation.parameterList.attributes.forEach { param ->
            val name = param.name ?: return@forEach
            val value = extractStringValue(param.value ?: return@forEach)
            attributes[name] = value
        }

        return attributes
    }

    /**
     * 从JAX-RS Path注解中提取路径
     */
    fun extractJaxRsPath(annotation: PsiAnnotation): String? {
        return extractStringAttribute(annotation, "value") ?: "/"
    }

    /**
     * 从Spring @RequestMapping注解中提取路径和方法
     */
    fun extractSpringRequestMapping(annotation: PsiAnnotation): HttpMappingInfo {
        val path = extractStringAttribute(annotation, "value", "path") ?: "/"
        val method = extractStringAttribute(annotation, "method") ?: "ANY"
        val consumes = extractStringArrayAttribute(annotation, "consumes")
        val produces = extractStringArrayAttribute(annotation, "produces")

        return HttpMappingInfo(method, path, consumes, produces)
    }

    /**
     * 合并路径（处理类级别和方法级别的路径）
     */
    fun mergePaths(classPath: String, methodPath: String): String {
        val cleanClassPath = classPath.trim('/')
        val cleanMethodPath = methodPath.trim('/')

        return when {
            cleanMethodPath.isEmpty() -> "/$cleanClassPath"
            cleanClassPath.isEmpty() -> "/$cleanMethodPath"
            else -> "/$cleanClassPath/$cleanMethodPath"
        }
    }

    /**
     * 规范化路径（确保以/开头，移除末尾的/）
     */
    fun normalizePath(path: String): String {
        val cleanPath = path.trim('/')
        return if (cleanPath.isEmpty()) "/" else "/$cleanPath"
    }

    /**
     * 从注解文本中提取完整信息（调试用）
     */
    fun extractAnnotationDebugInfo(annotation: PsiAnnotation): Map<String, Any> {
        return mapOf(
            "qualifiedName" to (annotation.qualifiedName ?: "unknown"),
            "text" to (annotation.text ?: ""),
            "simpleName" to AnnotationPatternDetector().getSimpleAnnotationName(annotation),
            "attributes" to extractAllStringAttributes(annotation),
            "attributeNames" to getAttributeNames(annotation)
        )
    }
}