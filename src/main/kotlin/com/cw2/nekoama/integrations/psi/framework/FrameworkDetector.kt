package com.cw2.nekoama.integrations.psi.framework

import com.cw2.nekoama.ai.model.dependency.BusinessEntryPoint
import com.cw2.nekoama.ai.model.dependency.EntryType
import com.cw2.nekoama.ai.model.dependency.ParameterInfo
import com.cw2.nekoama.integrations.psi.HttpMappingInfo
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope

/**
 * 框架检测器接口
 * 定义了检测不同Web框架Controller和HTTP映射的统一接口
 */
interface FrameworkDetector {

    /**
     * 检测指定搜索范围内的Controller类
     *
     * @param scope 搜索范围
     * @return 检测到的Controller类列表
     */
    fun detectControllers(scope: GlobalSearchScope): List<PsiClass>

    /**
     * 从方法中提取HTTP映射信息
     *
     * @param method 要分析的方法
     * @return HTTP映射信息，如果不是HTTP方法则返回null
     */
    fun extractHttpMapping(method: PsiMethod): HttpMappingInfo?

    /**
     * 获取框架名称
     *
     * @return 框架名称（如"Spring Web", "JAX-RS"等）
     */
    fun getFrameworkName(): String

    /**
     * 获取检测置信度
     *
     * @return 置信度值（0.0-1.0），表示检测结果的可靠性
     */
    fun getDetectionConfidence(): Double

    /**
     * 检查类是否是该框架的Controller
     *
     * @param psiClass 要检查的类
     * @return 如果是Controller则返回true
     */
    fun isController(psiClass: PsiClass): Boolean

    /**
     * 检查方法是否是HTTP端点
     *
     * @param method 要检查的方法
     * @return 如果是HTTP端点则返回true
     */
    fun isHttpEndpoint(method: PsiMethod): Boolean

    /**
     * 获取支持的注解列表
     *
     * @return 该框架支持的注解类名列表
     */
    fun getSupportedAnnotations(): List<String>

    /**
     * 创建业务入口点
     *
     * @param controller Controller类
     * @param method HTTP方法
     * @param mapping HTTP映射信息
     * @return 业务入口点对象
     */
    fun createBusinessEntryPoint(
        controller: PsiClass,
        method: PsiMethod,
        mapping: HttpMappingInfo
    ): BusinessEntryPoint

    /**
     * 获取入口点类型
     *
     * @return 该框架的入口点类型
     */
    fun getEntryPointType(): EntryType
}

/**
 * 抽象框架检测器基类
 * 提供通用功能实现，减少重复代码
 */
abstract class AbstractFrameworkDetector(
    protected val project: com.intellij.openapi.project.Project
) : FrameworkDetector {

    /**
     * 通用的Controller检测逻辑
     * 基于注解模式匹配
     */
    protected fun isControllerByAnnotations(psiClass: PsiClass, annotationPatterns: List<String>): Boolean {
        val patternDetector = com.cw2.nekoama.integrations.psi.AnnotationPatternDetector()
        return patternDetector.hasAnyAnnotationPattern(psiClass, annotationPatterns)
    }

    /**
     * 通用的HTTP端点检测逻辑
     */
    protected fun isHttpEndpointByAnnotations(method: PsiMethod, annotationPatterns: List<String>): Boolean {
        val patternDetector = com.cw2.nekoama.integrations.psi.AnnotationPatternDetector()
        return patternDetector.hasAnyAnnotationPattern(method, annotationPatterns)
    }

    /**
     * 创建标准业务入口点
     */
    protected fun createStandardBusinessEntryPoint(
        controller: PsiClass,
        method: PsiMethod,
        mapping: HttpMappingInfo,
        entryType: EntryType
    ): BusinessEntryPoint {
        val annotations = method.annotations.mapNotNull { it.qualifiedName }
        val parameters = extractParameterInfo(method)

        return BusinessEntryPoint(
            className = controller.qualifiedName ?: "",
            methodName = method.name,
            entryType = entryType,
            annotations = annotations,
            businessScenario = determineBusinessScenario(controller, method, mapping.toString()),
            parameters = parameters,
            httpMapping = mapping.toString()
        )
    }

    /**
     * 提取方法参数信息
     */
    private fun extractParameterInfo(method: PsiMethod): List<ParameterInfo> {
        return method.parameterList.parameters.mapIndexed { index, param ->
            val paramName = param.name ?: "param$index"
            val paramType = param.type.canonicalText
            val paramAnnotations = param.annotations.mapNotNull { it.qualifiedName }

            ParameterInfo(
                name = paramName,
                type = paramType,
                annotations = paramAnnotations
            )
        }
    }

    /**
     * 确定业务场景
     */
    private fun determineBusinessScenario(
        controller: PsiClass,
        method: PsiMethod,
        httpMapping: String
    ): String {
        val controllerName = controller.name ?: ""
        val methodName = method.name

        // 基于类名和方法名推断业务场景
        return when {
            controllerName.contains("User", ignoreCase = true) -> "用户管理"
            controllerName.contains("Order", ignoreCase = true) -> "订单管理"
            controllerName.contains("Product", ignoreCase = true) -> "商品管理"
            controllerName.contains("Payment", ignoreCase = true) -> "支付处理"
            methodName.contains("create", ignoreCase = true) -> "创建操作"
            methodName.contains("update", ignoreCase = true) -> "更新操作"
            methodName.contains("delete", ignoreCase = true) -> "删除操作"
            methodName.contains("query", ignoreCase = true) -> "查询操作"
            methodName.contains("get", ignoreCase = true) -> "获取操作"
            else -> "业务操作"
        }
    }
}

/**
 * 框架检测器工具类
 */
object FrameworkDetectorUtils {

    /**
     * 获取所有可用的框架检测器
     */
    fun getAllDetectors(project: com.intellij.openapi.project.Project): List<FrameworkDetector> {
        return listOf(
            SpringWebDetector(project),
            JaxRsDetector(project),
            GenericWebDetector(project)
        )
    }

    /**
     * 根据置信度排序检测器
     */
    fun sortDetectorsByConfidence(detectors: List<FrameworkDetector>): List<FrameworkDetector> {
        return detectors.sortedByDescending { it.getDetectionConfidence() }
    }

    /**
     * 检查项目是否使用了特定框架
     */
    fun hasFrameworkSupport(project: com.intellij.openapi.project.Project, frameworkName: String): Boolean {
        val javaPsiFacade = com.intellij.psi.JavaPsiFacade.getInstance(project)
        val scope = com.intellij.psi.search.GlobalSearchScope.allScope(project)

        // 尝试查找框架特定的类
        val frameworkClasses = when (frameworkName.lowercase()) {
            "spring web" -> listOf(
                "org.springframework.stereotype.Controller",
                "org.springframework.web.bind.annotation.RequestMapping"
            )
            "jax-rs" -> listOf(
                "javax.ws.rs.Path",
                "jakarta.ws.rs.Path"
            )
            else -> emptyList()
        }

        return frameworkClasses.any { className ->
            javaPsiFacade.findClass(className, scope) != null
        }
    }
}