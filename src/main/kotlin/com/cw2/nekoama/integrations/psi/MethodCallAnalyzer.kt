package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.MethodSource
import com.cw2.nekoama.ai.model.dependency.MethodCall
import com.cw2.nekoama.ai.model.dependency.CallType
import com.cw2.nekoama.ai.model.dependency.SourceLocation
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * 链式调用分解器
 * 增强方法调用分析，支持链式调用分解
 */
class MethodCallAnalyzer {

    /**
     * 调用信息内部数据结构
     */
    private data class CallInfo(
        val className: String,
        val methodName: String,
        val expression: PsiExpression,
        val location: SourceLocation,
        val resolvedMethod: PsiMethod?
    )

    companion object {
        /**
         * 提取带有来源信息的方法调用
         * @param callerClass 调用者类
         * @param method 调用者方法
         * @param projectPackage 项目根包名
         * @return 方法调用列表
         */
        fun extractMethodCallsWithSource(
            callerClass: PsiClass,
            method: PsiMethod,
            projectPackage: String
        ): List<MethodCall> {
            val methodCalls = mutableListOf<MethodCall>()

            method.accept(object : JavaRecursiveElementVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)

                    // 处理链式调用
                    extractChainCalls(expression, callerClass, method, methodCalls, projectPackage)
                }

                override fun visitNewExpression(expression: PsiNewExpression) {
                    super.visitNewExpression(expression)

                    // 处理构造函数链式调用
                    extractChainCalls(expression, callerClass, method, methodCalls, projectPackage)
                }
            })

            return methodCalls
        }

        /**
         * 提取链式调用
         */
        private fun extractChainCalls(
            expression: PsiExpression,
            callerClass: PsiClass,
            callerMethod: PsiMethod,
            methodCalls: MutableList<MethodCall>,
            projectPackage: String
        ) {
            val chainCalls = mutableListOf<CallInfo>()

            // 递归分析链式调用
            analyzeExpressionChain(expression, chainCalls, callerClass, callerMethod)

            // 转换为MethodCall对象
            chainCalls.forEachIndexed { index, callInfo ->
                methodCalls.add(
                    MethodCall(
                        callerClass = callerClass.qualifiedName ?: "",
                        callerMethod = callerMethod.name,
                        calleeClass = callInfo.className,
                        calleeMethod = callInfo.methodName,
                        callType = determineCallType(callInfo.expression),
                        location = callInfo.location,
                        callDepth = index,
                        sourceMethod = MethodSourceAnalyzer.analyzeMethodSource(
                            callInfo.resolvedMethod ?: return@forEachIndexed, projectPackage
                        )
                    )
                )
            }
        }

        /**
         * 递归分析表达式链
         */
        private fun analyzeExpressionChain(
            expression: PsiExpression,
            chainCalls: MutableList<CallInfo>,
            callerClass: PsiClass,
            callerMethod: PsiMethod
        ) {
            when (expression) {
                is PsiMethodCallExpression -> {
                    // 处理方法调用
                    processMethodCallExpression(expression, chainCalls, callerClass, callerMethod)
                }
                is PsiNewExpression -> {
                    // 处理构造函数调用
                    processNewExpression(expression, chainCalls, callerClass, callerMethod)
                }
                is PsiReferenceExpression -> {
                    // 处理引用表达式
                    processReferenceExpression(expression, chainCalls, callerClass, callerMethod)
                }
                else -> {
                    // 处理其他表达式类型
                    processOtherExpression(expression, chainCalls, callerClass, callerMethod)
                }
            }
        }

        /**
         * 处理方法调用表达式
         */
        private fun processMethodCallExpression(
            expression: PsiMethodCallExpression,
            chainCalls: MutableList<CallInfo>,
            callerClass: PsiClass,
            callerMethod: PsiMethod
        ) {
            val method = expression.resolveMethod()
            if (method != null) {
                val containingClass = method.containingClass
                if (containingClass != null) {
                    val location = SourceLocation(
                        filePath = expression.containingFile.virtualFile?.path ?: expression.containingFile.name,
                        lineNumber = getLineNumber(expression),
                        columnNumber = 0
                    )

                    chainCalls.add(
                        CallInfo(
                            className = containingClass.qualifiedName ?: "",
                            methodName = method.name,
                            expression = expression,
                            location = location,
                            resolvedMethod = method
                        )
                    )
                }
            }

            // 递归处理调用者表达式（链式调用的前一部分）
            val qualifier = expression.methodExpression.qualifierExpression
            if (qualifier != null) {
                analyzeExpressionChain(qualifier, chainCalls, callerClass, callerMethod)
            }
        }

        /**
         * 处理构造函数调用表达式
         */
        private fun processNewExpression(
            expression: PsiNewExpression,
            chainCalls: MutableList<CallInfo>,
            callerClass: PsiClass,
            callerMethod: PsiMethod
        ) {
            val constructor = expression.resolveConstructor()
            if (constructor != null) {
                val containingClass = constructor.containingClass
                if (containingClass != null) {
                    val location = SourceLocation(
                        filePath = expression.containingFile.virtualFile?.path ?: expression.containingFile.name,
                        lineNumber = getLineNumber(expression),
                        columnNumber = 0
                    )

                    chainCalls.add(
                        CallInfo(
                            className = containingClass.qualifiedName ?: "",
                            methodName = "<init>", // 构造函数的特殊标识
                            expression = expression,
                            location = location,
                            resolvedMethod = constructor
                        )
                    )
                }
            }
        }

        /**
         * 处理引用表达式
         */
        private fun processReferenceExpression(
            expression: PsiReferenceExpression,
            chainCalls: MutableList<CallInfo>,
            callerClass: PsiClass,
            callerMethod: PsiMethod
        ) {
            // 对于简单的字段访问或静态方法调用，通常不添加到调用链中
            // 但可以根据需要进一步处理
        }

        /**
         * 处理其他表达式类型
         */
        private fun processOtherExpression(
            expression: PsiExpression,
            chainCalls: MutableList<CallInfo>,
            callerClass: PsiClass,
            callerMethod: PsiMethod
        ) {
            // 对于其他类型的表达式，如类型转换、条件表达式等
            // 通常不直接产生方法调用，但可能包含嵌套的方法调用
            expression.children?.forEach { child ->
                if (child is PsiExpression) {
                    analyzeExpressionChain(child, chainCalls, callerClass, callerMethod)
                }
            }
        }

        /**
         * 确定调用类型
         */
        private fun determineCallType(expression: PsiExpression): CallType {
            return when (expression) {
                is PsiMethodCallExpression -> {
                    // 检查是否为Lambda表达式中的调用
                    if (isLambdaCall(expression)) {
                        CallType.LAMBDA
                    } else if (isStreamCall(expression)) {
                        CallType.STREAM
                    } else if (isReflectionCall(expression)) {
                        CallType.REFLECTION
                    } else {
                        CallType.DIRECT
                    }
                }
                is PsiNewExpression -> CallType.CONSTRUCTOR_CALL
                else -> CallType.INDIRECT
            }
        }

        /**
         * 检查是否为Lambda调用
         */
        private fun isLambdaCall(expression: PsiMethodCallExpression): Boolean {
            // 简单检查是否在Lambda表达式上下文中
            val parent = PsiTreeUtil.getParentOfType(expression, PsiLambdaExpression::class.java)
            return parent != null
        }

        /**
         * 检查是否为Stream API调用
         */
        private fun isStreamCall(expression: PsiMethodCallExpression): Boolean {
            val method = expression.resolveMethod() ?: return false
            val containingClass = method.containingClass
            return containingClass?.qualifiedName?.startsWith("java.util.stream.") == true ||
                   expression.methodExpression.referenceName in listOf("stream", "filter", "map", "collect", "forEach")
        }

        /**
         * 检查是否为反射调用
         */
        private fun isReflectionCall(expression: PsiMethodCallExpression): Boolean {
            val method = expression.resolveMethod() ?: return false
            val containingClass = method.containingClass
            return containingClass?.qualifiedName in listOf(
                "java.lang.reflect.Method",
                "java.lang.Class",
                "java.lang.reflect.Constructor"
            )
        }

        /**
         * 获取PSI元素的行号
         */
        private fun getLineNumber(element: PsiElement): Int {
            val file = element.containingFile
            val virtualFile = file.virtualFile
            return if (virtualFile != null) {
                val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile)
                document?.getLineNumber(element.textOffset)?.plus(1) ?: 0
            } else {
                // 如果没有VirtualFile，使用文本计算行号
                val text = file.text.substring(0, element.textOffset)
                text.count { it == '\n' } + 1
            }
        }
    }
}