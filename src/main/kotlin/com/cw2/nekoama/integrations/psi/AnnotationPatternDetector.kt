package com.cw2.nekoama.integrations.psi

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiModifierListOwner

/**
 * 基于字符串模式的注解检测器
 * 无需依赖实际的注解类，通过字符串匹配识别注解
 */
class AnnotationPatternDetector {

    companion object {
        // Spring MVC注解模式
        val SPRING_PATTERNS = listOf(
            // 类级别注解
            "org.springframework.stereotype.controller",
            "org.springframework.web.bind.annotation.restcontroller",
            "org.springframework.stereotype.restcontroller",
            "org.springframework.stereotype.component",

            // 方法级别注解 - HTTP映射
            "org.springframework.web.bind.annotation.requestmapping",
            "org.springframework.web.bind.annotation.getmapping",
            "org.springframework.web.bind.annotation.postmapping",
            "org.springframework.web.bind.annotation.putmapping",
            "org.springframework.web.bind.annotation.deletemapping",
            "org.springframework.web.bind.annotation.patchmapping",

            // 简化的注解名称（用于模式匹配）
            "controller", "restcontroller", "requestmapping",
            "getmapping", "postmapping", "putmapping",
            "deletemapping", "patchmapping"
        )

        // JAX-RS注解模式
        val JAX_RS_PATTERNS = listOf(
            // JAX-RS核心注解
            "javax.ws.rs.path",
            "javax.ws.rs.get",
            "javax.ws.rs.post",
            "javax.ws.rs.put",
            "javax.ws.rs.delete",
            "javax.ws.rs.patch",
            "javax.ws.rs.consumes",
            "javax.ws.rs.produces",
            "javax.ws.rs.core.response",
            "javax.ws.rs.applicationpath",

            // Jakarta EE (新版本)
            "jakarta.ws.rs.path",
            "jakarta.ws.rs.get",
            "jakarta.ws.rs.post",
            "jakarta.ws.rs.put",
            "jakarta.ws.rs.delete",
            "jakarta.ws.rs.patch",
            "jakarta.ws.rs.consumes",
            "jakarta.ws.rs.produces",
            "jakarta.ws.rs.core.response",
            "jakarta.ws.rs.applicationpath",

            // 简化的注解名称
            "path", "get", "post", "put", "delete", "patch",
            "consumes", "produces", "applicationpath"
        )

        // 通用Web注解模式
        val GENERIC_PATTERNS = listOf(
            "controller", "restcontroller", "endpoint", "resource",
            "mapping", "request", "response", "handler", "service",
            "component", "repository", "entity", "dto"
        )

        // HTTP方法相关注解
        val HTTP_METHOD_PATTERNS = listOf(
            "getmapping", "postmapping", "putmapping", "deletemapping",
            "patchmapping", "requestmapping", "get", "post", "put",
            "delete", "patch", "head", "options"
        )
    }

    /**
     * 检查PSI元素是否有任何指定的注解模式
     */
    fun hasAnyAnnotationPattern(psiElement: PsiModifierListOwner, patterns: List<String>): Boolean {
        return psiElement.annotations.any { annotation ->
            matchesAnyPattern(annotation, patterns)
        }
    }

    /**
     * 检查是否有Spring Controller相关注解
     */
    fun hasSpringControllerAnnotations(psiElement: PsiModifierListOwner): Boolean {
        return hasAnyAnnotationPattern(psiElement, SPRING_PATTERNS)
    }

    /**
     * 检查是否有JAX-RS相关注解
     */
    fun hasJaxRsAnnotations(psiElement: PsiModifierListOwner): Boolean {
        return hasAnyAnnotationPattern(psiElement, JAX_RS_PATTERNS)
    }

    /**
     * 检查是否有HTTP方法映射注解
     */
    fun hasHttpMappingAnnotations(psiElement: PsiModifierListOwner): Boolean {
        return hasAnyAnnotationPattern(psiElement, HTTP_METHOD_PATTERNS)
    }

    /**
     * 检查是否有通用Web相关注解
     */
    fun hasGenericWebAnnotations(psiElement: PsiModifierListOwner): Boolean {
        return hasAnyAnnotationPattern(psiElement, GENERIC_PATTERNS)
    }

    /**
     * 检查注解是否匹配任何模式
     */
    private fun matchesAnyPattern(annotation: PsiAnnotation, patterns: List<String>): Boolean {
        val qualifiedName = annotation.qualifiedName?.lowercase() ?: return false

        // 完全匹配
        if (patterns.any { pattern -> qualifiedName == pattern.lowercase() }) {
            return true
        }

        // 包含匹配
        if (patterns.any { pattern ->
            pattern.lowercase().isNotEmpty() && qualifiedName.contains(pattern.lowercase())
        }) {
            return true
        }

        // 简单名称匹配（不包含包路径）
        val simpleName = getSimpleAnnotationName(annotation).lowercase()
        return patterns.any { pattern ->
            pattern.lowercase().isNotEmpty() && simpleName == pattern.lowercase()
        }
    }

    /**
     * 获取注解的简单名称（不含包路径）
     */
    fun getSimpleAnnotationName(annotation: PsiAnnotation): String {
        val qualifiedName = annotation.qualifiedName ?: return annotation.text ?: ""

        // 从完整类名中提取简单名称
        val lastDotIndex = qualifiedName.lastIndexOf('.')
        return if (lastDotIndex != -1) {
            qualifiedName.substring(lastDotIndex + 1)
        } else {
            qualifiedName
        }
    }

    /**
     * 检查注解是否是Controller类型注解
     */
    fun isControllerAnnotation(annotation: PsiAnnotation): Boolean {
        val annotationName = getSimpleAnnotationName(annotation).lowercase()
        val qualifiedName = annotation.qualifiedName?.lowercase() ?: return false

        return annotationName in listOf("controller", "restcontroller") ||
               qualifiedName.contains("controller") ||
               qualifiedName.contains("restcontroller")
    }

    /**
     * 检查注解是否是HTTP映射注解
     */
    fun isHttpMappingAnnotation(annotation: PsiAnnotation): Boolean {
        val annotationName = getSimpleAnnotationName(annotation).lowercase()
        val qualifiedName = annotation.qualifiedName?.lowercase() ?: return false

        return annotationName.contains("mapping") ||
               HTTP_METHOD_PATTERNS.any { pattern ->
                   annotationName == pattern.lowercase() || qualifiedName.contains(pattern.lowercase())
               }
    }

    /**
     * 从注解中提取HTTP方法
     */
    fun extractHttpMethod(annotation: PsiAnnotation): String? {
        val annotationName = getSimpleAnnotationName(annotation).lowercase()
        val qualifiedName = annotation.qualifiedName?.lowercase() ?: return null

        return when {
            annotationName.contains("getmapping") || qualifiedName.contains("get") -> "GET"
            annotationName.contains("postmapping") || qualifiedName.contains("post") -> "POST"
            annotationName.contains("putmapping") || qualifiedName.contains("put") -> "PUT"
            annotationName.contains("deletemapping") || qualifiedName.contains("delete") -> "DELETE"
            annotationName.contains("patchmapping") || qualifiedName.contains("patch") -> "PATCH"
            annotationName.contains("requestmapping") -> "ANY"
            annotationName.contains("head") -> "HEAD"
            annotationName.contains("options") -> "OPTIONS"
            else -> null
        }
    }

    /**
     * 获取注解的所有模式匹配信息
     */
    fun getAnnotationPatternMatches(annotation: PsiAnnotation): List<String> {
        val matches = mutableListOf<String>()
        val qualifiedName = annotation.qualifiedName?.lowercase() ?: return matches
        val simpleName = getSimpleAnnotationName(annotation).lowercase()

        val allPatterns = SPRING_PATTERNS + JAX_RS_PATTERNS + GENERIC_PATTERNS + HTTP_METHOD_PATTERNS

        for (pattern in allPatterns) {
            val patternLower = pattern.lowercase()
            if (qualifiedName == patternLower || qualifiedName.contains(patternLower) || simpleName == patternLower) {
                matches.add(pattern)
            }
        }

        return matches.distinct()
    }
}