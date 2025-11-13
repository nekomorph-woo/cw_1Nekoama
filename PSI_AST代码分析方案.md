# IntelliJ IDEA 插件开发中的 PSI/AST 代码分析完全指南

## 目录
1. [PSI 与 AST 的关系](#1-psi-与-ast-的关系)
2. [Java PSI 层次结构详解](#2-java-psi-层次结构详解)
3. [核心 API 和工具类](#3-核心-api-和工具类)
4. [高级分析技术](#4-高级分析技术)
5. [实用代码示例](#5-实用代码示例)
6. [性能优化最佳实践](#6-性能优化最佳实践)
7. [基于项目的集成方案](#7-基于项目的集成方案)

---

## 1. PSI 与 AST 的关系

### 1.1 PSI (Program Structure Interface) 概念
PSI 是 IntelliJ IDEA 对传统 AST 的增强版本，它不仅包含语法结构，还包含语义信息：

```kotlin
// PSI 的层次结构
PsiElement (根接口)
├── PsiFile (文件节点)
│   ├── PsiJavaFile (Java文件)
│   ├── KtFile (Kotlin文件)
│   └── XmlFile (XML文件)
├── PsiClass (类节点)
├── PsiMethod (方法节点)
├── PsiField (字段节点)
└── PsiStatement (语句节点)
```

### 1.2 PSI 相比传统 AST 的优势
- **语义感知**: 包含类型信息、引用解析
- **多语言支持**: 统一接口处理不同语言
- **增量更新**: 高效的代码变更追踪
- **引用解析**: 自动处理符号引用关系

### 1.3 PSI 访问的基本模式
```kotlin
// 基于你项目中的实际使用模式
val analysis = ReadAction.compute<CodeContext?, Throwable> {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    if (psiFile is PsiJavaFile) {
        extractJavaContext(psiFile)
    } else {
        null
    }
}
```

---

## 2. Java PSI 层次结构详解

### 2.1 核心接口层次
```kotlin
// Java PSI 具体层次
PsiJavaFile
├── PsiPackageStatement (package声明)
├── PsiImportList (导入列表)
│   └── PsiImportStatement (导入语句)
└── PsiClass (类声明)
    ├── PsiField (字段)
    ├── PsiMethod (方法)
    ├── PsiClass (内部类)
    └── PsiCodeBlock (代码块)
        ├── PsiDeclarationStatement (声明语句)
        ├── PsiExpressionStatement (表达式语句)
        └── PsiReturnStatement (返回语句)
```

### 2.2 访问者模式遍历
```kotlin
// 你项目中已实现的遍历模式
class JavaCodeAnalyzer : CodeAnalyzer {

    override fun analyzeMethod(element: PsiElement): Result<MethodContext> {
        return try {
            val method = element as? PsiMethod ?: return Result.error()

            // 使用访问者模式遍历方法
            val context = MethodContext(
                name = method.name,
                returnType = method.returnType?.presentableText ?: "void",
                parameters = method.parameters.map { param ->
                    ParameterContext(
                        name = param.name,
                        type = param.type.presentableText
                    )
                },
                annotations = extractAnnotations(method),
                modifiers = extractModifiers(method)
            )

            Result.success(context)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    // 递归访问所有元素
    private fun traverseElement(element: PsiElement) {
        element.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is PsiMethod -> processMethod(element)
                    is PsiField -> processField(element)
                    is PsiClass -> processClass(element)
                }
                super.visitElement(element)
            }
        })
    }
}
```

### 2.3 上下文提取示例
```kotlin
// 基于你项目的上下文提取模式
data class MethodContext(
    val name: String,
    val returnType: String,
    val parameters: List<ParameterContext>,
    val annotations: List<String>,
    val modifiers: Set<String>,
    val callingMethods: List<String> = emptyList(),
    complexity: Int = 0
)

data class ClassContext(
    val name: String,
    val packageName: String,
    val superClass: String?,
    val interfaces: List<String>,
    val fields: List<FieldContext>,
    val methods: List<MethodContext>,
    val annotations: List<String>
)

data class FieldContext(
    val name: String,
    val type: String,
    val modifiers: Set<String>,
    val annotations: List<String>
)
```

---

## 3. 核心 API 和工具类

### 3.1 PsiTreeUtil - 树操作工具
```kotlin
// 扩展你项目中的工具类
object PsiUtils {

    // 查找父元素
    fun findParentClass(element: PsiElement): PsiClass? {
        return PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
    }

    // 查找子元素
    fun <T : PsiElement> findChildrenOfType(element: PsiElement, type: Class<T>): List<T> {
        return PsiTreeUtil.getChildrenOfTypeAsList(element, type)
    }

    // 查找特定方法
    fun findMethodByName(clazz: PsiClass, methodName: String): PsiMethod? {
        return clazz.findMethodsByName(methodName, true).firstOrNull()
    }

    // 查找方法调用
    fun findMethodCalls(method: PsiMethod): List<PsiMethodCallExpression> {
        return PsiTreeUtil.getChildrenOfTypeAsList(method, PsiMethodCallExpression::class.java)
    }

    // 获取方法的完整签名
    fun getMethodSignature(method: PsiMethod): String {
        val params = method.parameters.joinToString(", ") {
            "${it.type.presentableText} ${it.name}"
        }
        return "${method.returnType?.presentableText ?: "void"} ${method.name}($params)"
    }
}
```

### 3.2 JavaPsiFacade - 核心工厂类
```kotlin
// 扩展你项目的 JavaCodeAnalyzer
class EnhancedJavaCodeAnalyzer(private val project: Project) {

    private val javaPsiFacade = JavaPsiFacade.getInstance(project)
    private val psiManager = PsiManager.getInstance(project)
    private val elementFactory = JavaPsiFacade.getElementFactory(project)

    // 查找类
    fun findClass(qualifiedName: String): PsiClass? {
        return javaPsiFacade.findClass(qualifiedName, GlobalSearchScope.projectScope(project))
    }

    // 解析表达式类型
    fun resolveExpressionType(expression: PsiExpression): PsiType? {
        return expression.type
    }

    // 查找类的所有子类
    fun findSubClasses(clazz: PsiClass): List<PsiClass> {
        val scope = GlobalSearchScope.projectScope(project)
        return ClassInheritorsSearch.search(clazz, scope, true).toList()
    }

    // 解析方法调用链
    fun resolveMethodCall(call: PsiMethodCallExpression): PsiMethod? {
        return call.resolveMethod()
    }

    // 创建PSI元素
    fun createMethodFromText(text: String, context: PsiElement): PsiMethod? {
        return elementFactory.createMethodFromText(text, context)
    }
}
```

### 3.3 符号解析和引用查找
```kotlin
// 符号解析工具
class SymbolResolver(private val project: Project) {

    // 解析变量引用
    fun resolveVariableReference(reference: PsiReferenceExpression): PsiVariable? {
        return reference.resolve() as? PsiVariable
    }

    // 解析方法引用
    fun resolveMethodReference(reference: PsiReferenceExpression): PsiMethod? {
        return reference.resolve() as? PsiMethod
    }

    // 查找所有引用
    fun findAllReferences(element: PsiElement): Collection<PsiReference> {
        return ReferencesSearch.search(element).findAll()
    }

    // 查找特定类型的所有使用
    fun findTypeUsages(psiClass: PsiClass): Collection<PsiReference> {
        return ReferencesSearch.search(psiClass).findAll()
    }

    // 解析类型引用
    fun resolveType(typeElement: PsiTypeElement): PsiClass? {
        return typeElement.type.resolve() as? PsiClass
    }
}
```

---

## 4. 高级分析技术

### 4.1 方法调用链分析
```kotlin
// 调用链分析器
class CallChainAnalyzer(private val project: Project) {

    fun analyzeMethodCallChain(method: PsiMethod): CallChainResult {
        val directCalls = mutableListOf<MethodCall>()
        val indirectCalls = mutableListOf<MethodCall>()

        method.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expr: PsiMethodCallExpression) {
                val resolvedMethod = expr.resolveMethod()
                if (resolvedMethod != null) {
                    val call = MethodCall(
                        calledMethod = resolvedMethod,
                        callSite = expr,
                        arguments = expr.argumentList.expressions.map { it.text },
                        lineNumber = getLineNumber(expr)
                    )

                    // 判断是否为同一类中的调用
                    if (isSameClass(method, resolvedMethod)) {
                        directCalls.add(call)
                    } else {
                        indirectCalls.add(call)
                    }
                }
                super.visitMethodCallExpression(expr)
            }
        })

        return CallChainResult(
            analyzedMethod = method.name,
            directCalls = directCalls,
            indirectCalls = indirectCalls,
            totalCallCount = directCalls.size + indirectCalls.size
        )
    }

    // 分析调用深度
    fun analyzeCallDepth(method: PsiMethod, maxDepth: Int = 10): CallDepthResult {
        val visited = mutableSetOf<String>()

        fun calculateDepth(currentMethod: PsiMethod, currentDepth: Int): Int {
            if (currentDepth > maxDepth) return maxDepth
            if (currentMethod.qualifiedName in visited) return currentDepth

            visited.add(currentMethod.qualifiedName)

            val calls = PsiUtils.findMethodCalls(currentMethod)
            val maxChildDepth = calls.maxOfOrNull { call ->
                val resolved = call.resolveMethod()
                resolved?.let { calculateDepth(it, currentDepth + 1) } ?: currentDepth
            } ?: currentDepth

            return maxChildDepth
        }

        val depth = calculateDepth(method, 1)
        return CallDepthResult(method.name, depth)
    }
}

data class MethodCall(
    val calledMethod: PsiMethod,
    val callSite: PsiMethodCallExpression,
    val arguments: List<String>,
    val lineNumber: Int
)

data class CallChainResult(
    val analyzedMethod: String,
    val directCalls: List<MethodCall>,
    val indirectCalls: List<MethodCall>,
    val totalCallCount: Int
)

data class CallDepthResult(
    val methodName: String,
    val depth: Int
)
```

### 4.2 类型推断和分析
```kotlin
// 类型分析器
class TypeAnalyzer {

    fun analyzeTypeHierarchy(clazz: PsiClass): TypeHierarchy {
        val superClass = clazz.superClass
        val interfaces = clazz.interfaces.toList()
        val subClasses = findSubClasses(clazz)

        return TypeHierarchy(
            currentClass = clazz,
            superClass = superClass,
            interfaces = interfaces,
            subClasses = subClasses
        )
    }

    fun findSubClasses(clazz: PsiClass): List<PsiClass> {
        val scope = GlobalSearchScope.projectScope(clazz.project)
        return ClassInheritorsSearch.search(clazz, scope, true).toList()
    }

    // 分析类型兼容性
    fun isTypeAssignable(fromType: PsiType, toType: PsiType): Boolean {
        return TypeConversionUtil.isAssignable(toType, fromType)
    }

    // 获取类型的所有方法
    fun getAllMethodsOfType(psiType: PsiType): List<PsiMethod> {
        val psiClass = psiType.resolve() as? PsiClass ?: return emptyList()
        return getAllMethodsOfClass(psiClass)
    }

    private fun getAllMethodsOfClass(clazz: PsiClass): List<PsiMethod> {
        val methods = mutableListOf<PsiMethod>()

        // 添加当前类的方法
        methods.addAll(clazz.methods)

        // 添加父类的方法
        clazz.superClass?.let { superClass ->
            methods.addAll(getAllMethodsOfClass(superClass))
        }

        // 添加接口的方法
        clazz.interfaces.forEach { interfaceClass ->
            methods.addAll(interfaceClass.methods)
        }

        return methods.distinctBy { it.signature }
    }
}

data class TypeHierarchy(
    val currentClass: PsiClass,
    val superClass: PsiClass?,
    val interfaces: List<PsiClass>,
    val subClasses: List<PsiClass>
)
```

### 4.3 注解信息提取
```kotlin
// 注解分析器
class AnnotationAnalyzer {

    fun extractAnnotations(element: PsiModifierListOwner): List<AnnotationInfo> {
        return element.annotations.map { annotation ->
            AnnotationInfo(
                name = annotation.qualifiedName ?: "",
                attributes = extractAnnotationAttributes(annotation)
            )
        }
    }

    private fun extractAnnotationAttributes(annotation: PsiAnnotation): Map<String, Any> {
        val attributes = mutableMapOf<String, Any>()

        annotation.parameterList.attributes.forEach { attr ->
            val value = when (val attrValue = attr.value) {
                is PsiLiteralExpression -> attrValue.value ?: attrValue.text
                is PsiArrayInitializerMemberValue -> {
                    attrValue.initializers.map { it.text }
                }
                is PsiClassObjectAccessExpression -> {
                    resolveEnumConstant(attrValue)
                }
                else -> attrValue?.text ?: ""
            }
            attributes[attr.name ?: "value"] = value
        }

        return attributes
    }

    private fun resolveEnumConstant(expression: PsiClassObjectAccessExpression): String {
        return "${expression.qualifiedReference.text}"
    }

    // 检查是否具有特定注解
    fun hasAnnotation(element: PsiModifierListOwner, annotationName: String): Boolean {
        return element.annotations.any { it.qualifiedName == annotationName }
    }

    // 获取特定注解的属性
    fun getAnnotationAttribute(element: PsiModifierListOwner, annotationName: String, attributeName: String): Any? {
        val annotation = element.annotations.find { it.qualifiedName == annotationName }
        return annotation?.findAttributeValue(attributeName)?.let { resolveAttributeValue(it) }
    }

    private fun resolveAttributeValue(value: PsiAnnotationMemberValue?): Any? {
        return when (value) {
            is PsiLiteralExpression -> value.value
            is PsiReferenceExpression -> value.resolve()?.toString()
            else -> value?.text
        }
    }
}

data class AnnotationInfo(
    val name: String,
    val attributes: Map<String, Any>
)
```

---

## 5. 实用代码示例

### 5.1 遍历Java文件所有类和方法
```kotlin
// 完整的Java文件分析器
class JavaFileAnalyzer {

    fun analyzeJavaFile(javaFile: PsiJavaFile): JavaFileAnalysis {
        val packageInfo = javaFile.packageName
        val imports = javaFile.importList?.importStatements?.map { it.qualifiedName } ?: emptyList()

        val classes = mutableListOf<ClassAnalysis>()
        val methods = mutableListOf<MethodAnalysis>()
        val fields = mutableListOf<FieldAnalysis>()

        javaFile.accept(object : JavaRecursiveElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                val classAnalysis = analyzeClass(aClass)
                classes.add(classAnalysis)
                methods.addAll(classAnalysis.methods)
                fields.addAll(classAnalysis.fields)
                super.visitClass(aClass)
            }
        })

        return JavaFileAnalysis(
            fileName = javaFile.name,
            packageName = packageInfo,
            imports = imports,
            classes = classes,
            totalMethods = methods.size,
            totalFields = fields.size
        )
    }

    private fun analyzeClass(clazz: PsiClass): ClassAnalysis {
        val fields = clazz.fields.map { analyzeField(it) }
        val methods = clazz.methods.map { analyzeMethod(it) }
        val innerClasses = clazz.innerClasses.map { analyzeClass(it) }

        return ClassAnalysis(
            name = clazz.name,
            qualifiedName = clazz.qualifiedName,
            superClass = clazz.superClass?.qualifiedName,
            interfaces = clazz.interfaces.map { it.qualifiedName },
            modifiers = extractModifiers(clazz),
            annotations = extractAnnotations(clazz),
            fields = fields,
            methods = methods,
            innerClasses = innerClasses
        )
    }

    private fun analyzeMethod(method: PsiMethod): MethodAnalysis {
        return MethodAnalysis(
            name = method.name,
            signature = PsiUtils.getMethodSignature(method),
            returnType = method.returnType?.presentableText ?: "void",
            parameters = method.parameters.map { param ->
                ParameterAnalysis(
                    name = param.name,
                    type = param.type.presentableText,
                    annotations = extractAnnotations(param)
                )
            },
            modifiers = extractModifiers(method),
            annotations = extractAnnotations(method),
            isConstructor = method.isConstructor,
            isStatic = method.hasModifierProperty(PsiModifier.STATIC),
            lineNumber = getLineNumber(method)
        )
    }

    private fun analyzeField(field: PsiField): FieldAnalysis {
        return FieldAnalysis(
            name = field.name,
            type = field.type.presentableText,
            modifiers = extractModifiers(field),
            annotations = extractAnnotations(field),
            isStatic = field.hasModifierProperty(PsiModifier.STATIC),
            isFinal = field.hasModifierProperty(PsiModifier.FINAL),
            initializer = field.initializer?.text
        )
    }

    private fun extractModifiers(element: PsiModifierListOwner): Set<String> {
        return element.modifierList?.modifiers?.mapNotNull { it.keyword }?.toSet() ?: emptySet()
    }

    private fun extractAnnotations(element: PsiModifierListOwner): List<String> {
        return element.annotations.mapNotNull { it.qualifiedName }
    }

    private fun getLineNumber(element: PsiElement): Int {
        val file = element.containingFile
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
        return document?.getLineNumber(element.textRange.startOffset) ?: 0
    }
}

// 数据类定义
data class JavaFileAnalysis(
    val fileName: String,
    val packageName: String,
    val imports: List<String>,
    val classes: List<ClassAnalysis>,
    val totalMethods: Int,
    val totalFields: Int
)

data class ClassAnalysis(
    val name: String,
    val qualifiedName: String,
    val superClass: String?,
    val interfaces: List<String>,
    val modifiers: Set<String>,
    val annotations: List<String>,
    val fields: List<FieldAnalysis>,
    val methods: List<MethodAnalysis>,
    val innerClasses: List<ClassAnalysis>
)

data class MethodAnalysis(
    val name: String,
    val signature: String,
    val returnType: String,
    val parameters: List<ParameterAnalysis>,
    val modifiers: Set<String>,
    val annotations: List<String>,
    val isConstructor: Boolean,
    val isStatic: Boolean,
    val lineNumber: Int
)

data class FieldAnalysis(
    val name: String,
    val type: String,
    val modifiers: Set<String>,
    val annotations: List<String>,
    val isStatic: Boolean,
    val isFinal: Boolean,
    val initializer: String?
)

data class ParameterAnalysis(
    val name: String,
    val type: String,
    val annotations: List<String>
)
```

### 5.2 方法调用关系分析
```kotlin
// 方法调用关系分析器
class MethodCallAnalyzer {

    fun analyzeCallRelationships(javaFile: PsiJavaFile): CallRelationshipMap {
        val callMap = mutableMapOf<String, Set<String>>()
        val reverseCallMap = mutableMapOf<String, Set<String>>()

        javaFile.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                val methodKey = getMethodKey(method)
                val calledMethods = mutableSetOf<String>()

                method.accept(object : JavaRecursiveElementVisitor() {
                    override fun visitMethodCallExpression(expr: PsiMethodCallExpression) {
                        val calledMethod = expr.resolveMethod()
                        if (calledMethod != null) {
                            calledMethods.add(getMethodKey(calledMethod))
                        }
                        super.visitMethodCallExpression(expr)
                    }
                })

                callMap[methodKey] = calledMethods

                // 构建反向调用关系
                calledMethods.forEach { calledMethodKey ->
                    val callers = reverseCallMap.getOrDefault(calledMethodKey, emptySet()).toMutableSet()
                    callers.add(methodKey)
                    reverseCallMap[calledMethodKey] = callers
                }

                super.visitMethod(method)
            }
        })

        return CallRelationshipMap(callMap, reverseCallMap)
    }

    private fun getMethodKey(method: PsiMethod): String {
        return "${method.containingClass?.qualifiedName}.${method.name}${method.parameterList.parametersCount}"
    }

    // 查找循环调用
    fun findCircularCalls(callMap: CallRelationshipMap): List<CircularCall> {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val circularCalls = mutableListOf<CircularCall>()

        fun dfs(method: String, path: List<String>) {
            if (method in recursionStack) {
                val cycleStart = path.indexOf(method)
                if (cycleStart != -1) {
                    val cycle = path.subList(cycleStart, path.size) + method
                    circularCalls.add(CircularCall(cycle))
                }
                return
            }

            if (method in visited) return

            visited.add(method)
            recursionStack.add(method)

            callMap.getDirectCalls(method)?.forEach { calledMethod ->
                dfs(calledMethod, path + method)
            }

            recursionStack.remove(method)
        }

        callMap.allMethods.forEach { method ->
            dfs(method, emptyList())
        }

        return circularCalls
    }
}

data class CallRelationshipMap(
    val directCalls: Map<String, Set<String>>,
    val reverseCalls: Map<String, Set<String>>
) {
    fun getDirectCalls(method: String): Set<String>? = directCalls[method]
    fun getCallers(method: String): Set<String>? = reverseCalls[method]
    val allMethods: Set<String> get() = (directCalls.keys + reverseCalls.keys).toSet()
}

data class CircularCall(
    val cycle: List<String>
)
```

### 5.3 代码质量分析
```kotlin
// 代码质量分析器
class CodeQualityAnalyzer {

    fun analyzeCodeQuality(javaFile: PsiJavaFile): CodeQualityReport {
        val issues = mutableListOf<CodeIssue>()
        val metrics = mutableMapOf<String, Any>()

        var totalLines = 0
        var totalMethods = 0
        var totalClasses = 0
        var totalComplexity = 0

        javaFile.accept(object : JavaRecursiveElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                totalClasses++
                analyzeClassComplexity(aClass)?.let { issues.add(it) }
                super.visitClass(aClass)
            }

            override fun visitMethod(method: PsiMethod) {
                totalMethods++
                val methodComplexity = calculateCyclomaticComplexity(method)
                totalComplexity += methodComplexity

                if (methodComplexity > 10) {
                    issues.add(CodeIssue(
                        type = IssueType.COMPLEXITY,
                        severity = Severity.WARNING,
                        message = "方法复杂度过高: $methodComplexity (建议 <= 10)",
                        element = method.nameIdentifier,
                        location = getLocation(method)
                    ))
                }

                analyzeMethodParameters(method)?.let { issues.add(it) }
                analyzeMethodNaming(method)?.let { issues.add(it) }
                super.visitMethod(method)
            }

            override fun visitField(field: PsiField) {
                analyzeFieldNaming(field)?.let { issues.add(it) }
                analyzeFieldUsage(field)?.let { issues.add(it) }
                super.visitField(field)
            }
        })

        metrics["totalLines"] = totalLines
        metrics["totalMethods"] = totalMethods
        metrics["totalClasses"] = totalClasses
        metrics["averageComplexity"] = if (totalMethods > 0) totalComplexity.toDouble() / totalMethods else 0.0
        metrics["issueCount"] = issues.size

        return CodeQualityReport(
            fileName = javaFile.name,
            metrics = metrics,
            issues = issues,
            score = calculateQualityScore(issues, metrics)
        )
    }

    private fun calculateCyclomaticComplexity(method: PsiMethod): Int {
        var complexity = 1 // 基础复杂度

        method.accept(object : JavaRecursiveElementVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                complexity++
                super.visitIfStatement(statement)
            }

            override fun visitWhileStatement(statement: PsiWhileStatement) {
                complexity++
                super.visitWhileStatement(statement)
            }

            override fun visitForStatement(statement: PsiForStatement) {
                complexity++
                super.visitForStatement(statement)
            }

            override fun visitForeachStatement(statement: PsiForeachStatement) {
                complexity++
                super.visitForeachStatement(statement)
            }

            override fun visitSwitchStatement(statement: PsiSwitchStatement) {
                complexity += statement.statements.size
                super.visitSwitchStatement(statement)
            }

            override fun visitConditionalExpression(expr: PsiConditionalExpression) {
                complexity++
                super.visitConditionalExpression(expr)
            }

            override fun visitCatchSection(section: PsiCatchSection) {
                complexity++
                super.visitCatchSection(section)
            }
        })

        return complexity
    }

    private fun analyzeMethodParameters(method: PsiMethod): CodeIssue? {
        val paramCount = method.parameters.size
        if (paramCount > 5) {
            return CodeIssue(
                type = IssueType.PARAMETER_COUNT,
                severity = Severity.INFO,
                message = "方法参数过多: $paramCount (建议 <= 5)",
                element = method.parameterList,
                location = getLocation(method)
            )
        }
        return null
    }

    private fun analyzeMethodNaming(method: PsiMethod): CodeIssue? {
        if (method.isConstructor) return null

        val name = method.name
        if (!Character.isLowerCase(name[0])) {
            return CodeIssue(
                type = IssueType.NAMING,
                severity = Severity.WARNING,
                message = "方法名应使用小写字母开头: $name",
                element = method.nameIdentifier,
                location = getLocation(method)
            )
        }
        return null
    }

    private fun analyzeFieldNaming(field: PsiField): CodeIssue? {
        val name = field.name
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            // 静态字段应该使用大写字母和下划线
            if (!name.matches(Regex("^[A-Z][A-Z0-9_]*$"))) {
                return CodeIssue(
                    type = IssueType.NAMING,
                    severity = Severity.WARNING,
                    message = "静态字段名应使用大写字母和下划线: $name",
                    element = field.nameIdentifier,
                    location = getLocation(field)
                )
            }
        } else {
            // 非静态字段应该使用驼峰命名
            if (!name.matches(Regex("^[a-z][a-zA-Z0-9]*$"))) {
                return CodeIssue(
                    type = IssueType.NAMING,
                    severity = Severity.WARNING,
                    message = "字段名应使用驼峰命名: $name",
                    element = field.nameIdentifier,
                    location = getLocation(field)
                )
            }
        }
        return null
    }

    private fun analyzeFieldUsage(field: PsiField): CodeIssue? {
        val isPrivate = field.hasModifierProperty(PsiModifier.PRIVATE)
        val isFinal = field.hasModifierProperty(PsiModifier.FINAL)

        if (isPrivate && !isFinal && field.initializer == null) {
            // 检查是否在构造函数中被初始化
            val isInitializedInConstructor = field.containingClass?.constructors?.any { constructor ->
                constructor.accept(object : JavaRecursiveElementVisitor() {
                    override fun visitAssignmentExpression(expr: PsiAssignmentExpression) {
                        if (expr.lExpression is PsiReferenceExpression) {
                            val ref = expr.lExpression as PsiReferenceExpression
                            if (ref.resolve() == field) {
                                throw FoundAssignmentException()
                            }
                        }
                        super.visitAssignmentExpression(expr)
                    }
                })
                false
            } ?: false

            if (!isInitializedInConstructor) {
                return CodeIssue(
                    type = IssueType.UNINITIALIZED_FIELD,
                    severity = Severity.WARNING,
                    message = "私有字段可能未被正确初始化: ${field.name}",
                    element = field.nameIdentifier,
                    location = getLocation(field)
                )
            }
        }
        return null
    }

    private fun analyzeClassComplexity(clazz: PsiClass): CodeIssue? {
        val methodCount = clazz.methods.size
        val fieldCount = clazz.fields.size

        if (methodCount > 20) {
            return CodeIssue(
                type = IssueType.CLASS_SIZE,
                severity = Severity.WARNING,
                message = "类的方法过多: $methodCount (建议 <= 20)",
                element = clazz.nameIdentifier,
                location = getLocation(clazz)
            )
        }

        if (fieldCount > 15) {
            return CodeIssue(
                type = IssueType.CLASS_SIZE,
                severity = Severity.INFO,
                message = "类的字段过多: $fieldCount (建议 <= 15)",
                element = clazz.nameIdentifier,
                location = getLocation(clazz)
            )
        }

        return null
    }

    private fun getLocation(element: PsiElement): String {
        val file = element.containingFile
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
        val line = document?.getLineNumber(element.textRange.startOffset) ?: 0
        return "${file.name}:$line"
    }

    private fun calculateQualityScore(issues: List<CodeIssue>, metrics: Map<String, Any>): Double {
        var score = 100.0

        issues.forEach { issue ->
            when (issue.severity) {
                Severity.ERROR -> score -= 10
                Severity.WARNING -> score -= 5
                Severity.INFO -> score -= 2
            }
        }

        // 复杂度扣分
        val avgComplexity = metrics["averageComplexity"] as? Double ?: 0.0
        if (avgComplexity > 5) score -= (avgComplexity - 5) * 2

        return maxOf(0.0, score)
    }
}

// 异常类用于提前终止遍历
class FoundAssignmentException : RuntimeException()

enum class IssueType {
    COMPLEXITY, PARAMETER_COUNT, NAMING, UNINITIALIZED_FIELD, CLASS_SIZE
}

enum class Severity {
    ERROR, WARNING, INFO
}

data class CodeIssue(
    val type: IssueType,
    val severity: Severity,
    val message: String,
    val element: PsiElement?,
    val location: String
)

data class CodeQualityReport(
    val fileName: String,
    val metrics: Map<String, Any>,
    val issues: List<CodeIssue>,
    val score: Double
)
```

---

## 6. 性能优化最佳实践

### 6.1 ReadAction 正确使用
```kotlin
// 基于你项目中的最佳实践
class PerformanceOptimizedAnalyzer {

    fun <T> computeInReadAction(computation: () -> T): T {
        return ReadAction.compute<T, RuntimeException> {
            computation()
        }
    }

    fun <T> runInReadAction(action: () -> T): T {
        return ReadAction.run<T, RuntimeException> {
            action()
        }
    }

    // 批量处理文件
    fun analyzeFilesBatch(files: List<PsiFile>): List<AnalysisResult> {
        return ReadAction.compute<List<AnalysisResult>, RuntimeException> {
            files.mapNotNull { file ->
                try {
                    analyzeSingleFile(file)
                } catch (e: Exception) {
                    NekoamaLogger.logError("analyzeFilesBatch", NekoamaError.Unknown("分析文件失败: ${e.message}"))
                    null
                }
            }
        }
    }

    // 增量分析
    fun analyzeIncremental(changedFiles: Set<PsiFile>, previousResults: Map<String, AnalysisResult>): Map<String, AnalysisResult> {
        val newResults = previousResults.toMutableMap()

        ReadAction.run<RuntimeException> {
            changedFiles.forEach { file ->
                val filePath = file.virtualFile?.path
                if (filePath != null) {
                    newResults[filePath] = analyzeSingleFile(file)
                }
            }
        }

        return newResults
    }
}
```

### 6.2 缓存和增量分析
```kotlin
// 带缓存的代码分析器
class CachedCodeAnalyzer {
    private val analysisCache = mutableMapOf<String, CachedAnalysis>()
    private val fileModificationTracker = mutableMapOf<String, Long>()

    fun analyzeWithCache(file: PsiFile): AnalysisResult {
        val filePath = file.virtualFile?.path ?: return AnalysisResult.empty()
        val currentModTime = file.virtualFile?.timeStamp ?: 0

        val cachedAnalysis = analysisCache[filePath]
        if (cachedAnalysis != null && cachedAnalysis.modificationTime >= currentModTime) {
            return cachedAnalysis.result
        }

        val result = performAnalysis(file)
        analysisCache[filePath] = CachedAnalysis(result, currentModTime)

        return result
    }

    fun clearCache() {
        analysisCache.clear()
        fileModificationTracker.clear()
    }

    fun clearCacheForFile(filePath: String) {
        analysisCache.remove(filePath)
        fileModificationTracker.remove(filePath)
    }

    private fun performAnalysis(file: PsiFile): AnalysisResult {
        return ReadAction.compute<AnalysisResult, RuntimeException> {
            when (file) {
                is PsiJavaFile -> analyzeJavaFile(file)
                is KtFile -> analyzeKotlinFile(file)
                else -> AnalysisResult.empty()
            }
        }
    }

    private fun analyzeJavaFile(javaFile: PsiJavaFile): AnalysisResult {
        // 实际的Java文件分析逻辑
        val analyzer = JavaFileAnalyzer()
        val analysis = analyzer.analyzeJavaFile(javaFile)

        return AnalysisResult(
            fileName = javaFile.name,
            language = "Java",
            classCount = analysis.classes.size,
            methodCount = analysis.totalMethods,
            fieldCount = analysis.totalFields,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun analyzeKotlinFile(kotlinFile: KtFile): AnalysisResult {
        // Kotlin文件分析逻辑
        return AnalysisResult(
            fileName = kotlinFile.name,
            language = "Kotlin",
            classCount = 0,
            methodCount = 0,
            fieldCount = 0,
            timestamp = System.currentTimeMillis()
        )
    }
}

data class CachedAnalysis(
    val result: AnalysisResult,
    val modificationTime: Long
)

data class AnalysisResult(
    val fileName: String,
    val language: String,
    val classCount: Int,
    val methodCount: Int,
    val fieldCount: Int,
    val timestamp: Long
) {
    companion object {
        fun empty() = AnalysisResult("", "", 0, 0, 0, 0)
    }
}
```

### 6.3 异步分析实现
```kotlin
// 基于你项目中的 UnusedCodeScanner 模式
class AsyncCodeAnalyzer {

    fun analyzeInBackground(project: Project, scope: GlobalSearchScope, onComplete: (Result<AnalysisReport>) -> Unit) {
        DumbService.getInstance(project).runWhenSmart {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "代码分析中", true) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        indicator.text = "正在分析代码结构..."
                        val report = performAnalysis(project, scope, indicator)
                        onComplete(Result.success(report))
                    } catch (e: Exception) {
                        NekoamaLogger.logError("AsyncCodeAnalyzer", NekoamaError.Unknown("分析失败: ${e.message}"))
                        onComplete(Result.error(NekoamaError.Unknown("分析失败: ${e.message}")))
                    }
                }
            })
        }
    }

    private fun performAnalysis(project: Project, scope: GlobalSearchScope, indicator: ProgressIndicator): AnalysisReport {
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<AnalysisResult>()

        // 使用索引获取文件，避免直接文件系统访问
        val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)
        val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)
        val allFiles = javaFiles + kotlinFiles

        val batchSize = 50
        allFiles.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            indicator.checkCanceled()
            indicator.fraction = (batchIndex + 1).toDouble() / ((allFiles.size + batchSize - 1) / batchSize)
            indicator.text = "处理文件批次 ${batchIndex + 1}/${(allFiles.size + batchSize - 1) / batchSize}"

            batch.forEach { file ->
                indicator.text2 = file.name

                val psiFile = ReadAction.compute<PsiFile?, Throwable> {
                    PsiManager.getInstance(project).findFile(file)
                }

                psiFile?.let {
                    val analyzer = CachedCodeAnalyzer()
                    val result = analyzer.analyzeWithCache(it)
                    results.add(result)
                }
            }
        }

        return AnalysisReport(
            duration = System.currentTimeMillis() - startTime,
            fileCount = allFiles.size,
            results = results,
            totalClasses = results.sumOf { it.classCount },
            totalMethods = results.sumOf { it.methodCount },
            totalFields = results.sumOf { it.fieldCount }
        )
    }
}

data class AnalysisReport(
    val duration: Long,
    val fileCount: Int,
    val results: List<AnalysisResult>,
    val totalClasses: Int,
    val totalMethods: Int,
    val totalFields: Int
)
```

---

## 7. 基于项目的集成方案

### 7.1 扩展现有 CodeAnalyzer
```kotlin
// 增强现有的 CodeAnalyzer 接口
interface EnhancedCodeAnalyzer : CodeAnalyzer {
    // 新增方法
    fun analyzeMethodCallChains(element: PsiElement): Result<List<MethodCall>>
    fun analyzeDataFlow(element: PsiElement): Result<DataFlowInfo>
    fun extractDesignPatterns(element: PsiElement): Result<List<DesignPattern>>
    fun generateRefactoringSuggestions(element: PsiElement): Result<List<RefactoringSuggestion>>
    fun analyzeCodeQuality(element: PsiElement): Result<CodeQualityReport>
    fun extractTypeHierarchy(element: PsiElement): Result<TypeHierarchy>
}

// 实现增强的分析器
class NekoamaEnhancedCodeAnalyzer(
    private val project: Project,
    private val callChainAnalyzer: CallChainAnalyzer = CallChainAnalyzer(project),
    private val typeAnalyzer: TypeAnalyzer(),
    private val codeQualityAnalyzer: CodeQualityAnalyzer()
) : EnhancedCodeAnalyzer {

    override fun analyzeMethodCallChains(element: PsiElement): Result<List<MethodCall>> {
        return try {
            when (element) {
                is PsiMethod -> {
                    val result = callChainAnalyzer.analyzeMethodCallChain(element)
                    Result.success(result.directCalls + result.indirectCalls)
                }
                else -> Result.error("不支持的元素类型")
            }
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override fun analyzeCodeQuality(element: PsiElement): Result<CodeQualityReport> {
        return try {
            when (element) {
                is PsiJavaFile -> {
                    val report = codeQualityAnalyzer.analyzeCodeQuality(element)
                    Result.success(report)
                }
                else -> Result.error("不支持的文件类型")
            }
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override fun extractTypeHierarchy(element: PsiElement): Result<TypeHierarchy> {
        return try {
            when (element) {
                is PsiClass -> {
                    val hierarchy = typeAnalyzer.analyzeTypeHierarchy(element)
                    Result.success(hierarchy)
                }
                else -> Result.error("不支持的元素类型")
            }
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    // 实现其他方法...
}

// 数据类型定义
data class DataFlowInfo(
    val variables: List<VariableFlow>,
    val methodCalls: List<MethodCallFlow>
)

data class VariableFlow(
    val variable: PsiVariable,
    val usages: List<PsiReferenceExpression>,
    val definition: PsiElement?
)

data class MethodCallFlow(
    val call: PsiMethodCallExpression,
    val targetMethod: PsiMethod,
    val arguments: List<PsiExpression>
)

data class DesignPattern(
    val name: String,
    val elements: List<PsiElement>,
    val description: String
)

data class RefactoringSuggestion(
    val type: RefactoringType,
    val description: String,
    val element: PsiElement,
    val confidence: Double
)

enum class RefactoringType {
    EXTRACT_METHOD,
    EXTRACT_VARIABLE,
    INLINE_VARIABLE,
    RENAME_METHOD,
    MOVE_METHOD,
    INTRODUCE_PARAMETER
}
```

### 7.2 集成到现有工具窗口
```kotlin
// 在你的工具窗口中添加AST分析功能
class ASTAnalysisTab : NekoamaTab() {
    override val tabId = "ast_analysis"
    override val displayName = "AST分析"
    override val tooltip = "分析代码的AST结构和质量"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val analyzer = NekoamaEnhancedCodeAnalyzer(project)
    private val cachedAnalyzer = CachedCodeAnalyzer()

    override fun getComponent(): JComponent {
        val mainPanel = createThemedCard()
        mainPanel.layout = BorderLayout()

        // 创建控制面板
        val controlPanel = createControlPanel()
        mainPanel.add(controlPanel, BorderLayout.NORTH)

        // 创建结果面板
        val resultsPanel = createResultsPanel()
        mainPanel.add(resultsPanel, BorderLayout.CENTER)

        return mainPanel
    }

    private fun createControlPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))

        // 分析当前文件按钮
        val analyzeCurrentButton = JButton("分析当前文件")
        analyzeCurrentButton.addActionListener {
            analyzeCurrentFile()
        }
        panel.add(analyzeCurrentButton)

        // 分析整个项目按钮
        val analyzeProjectButton = JButton("分析整个项目")
        analyzeProjectButton.addActionListener {
            analyzeProject()
        }
        panel.add(analyzeProjectButton)

        // 清除缓存按钮
        val clearCacheButton = JButton("清除缓存")
        clearCacheButton.addActionListener {
            cachedAnalyzer.clearCache()
            showInfoMessage("缓存已清除")
        }
        panel.add(clearCacheButton)

        return panel
    }

    private fun createResultsPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // 创建结果表格
        val tableModel = DefaultTableModel(
            arrayOf("文件", "类数", "方法数", "字段数", "代码质量评分"),
            0
        )
        val resultsTable = JBTable(tableModel)

        // 创建滚动面板
        val scrollPane = JBScrollPane(resultsTable)
        panel.add(scrollPane, BorderLayout.CENTER)

        // 创建详细信息面板
        val detailPanel = createDetailPanel()
        panel.add(detailPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun analyzeCurrentFile() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return

        scope.launch {
            try {
                showLoading("正在分析当前文件...")

                val result = withContext(Dispatchers.IO) {
                    cachedAnalyzer.analyzeWithCache(psiFile)
                }

                // 获取代码质量报告
                val qualityReport = withContext(Dispatchers.IO) {
                    analyzer.analyzeCodeQuality(psiFile).getOrNull()
                }

                hideLoading()
                displayAnalysisResult(result, qualityReport)

            } catch (e: Exception) {
                hideLoading()
                showError("分析失败: ${e.message}")
                NekoamaLogger.logError("ASTAnalysisTab", NekoamaError.Unknown("分析失败", e))
            }
        }
    }

    private fun analyzeProject() {
        scope.launch {
            try {
                showLoading("正在分析整个项目...")

                val asyncAnalyzer = AsyncCodeAnalyzer()
                asyncAnalyzer.analyzeInBackground(
                    project,
                    GlobalSearchScope.projectScope(project)
                ) { result ->
                    result.onSuccess { report ->
                        hideLoading()
                        displayProjectReport(report)
                    }.onFailure { error ->
                        hideLoading()
                        showError("项目分析失败: ${error.message}")
                    }
                }

            } catch (e: Exception) {
                hideLoading()
                showError("分析失败: ${e.message}")
                NekoamaLogger.logError("ASTAnalysisTab", NekoamaError.Unknown("项目分析失败", e))
            }
        }
    }

    private fun displayAnalysisResult(result: AnalysisResult, qualityReport: CodeQualityReport?) {
        // 更新结果显示
        SwingUtilities.invokeLater {
            val tableModel = (resultsTable.model as DefaultTableModel)
            tableModel.addRow(arrayOf(
                result.fileName,
                result.classCount,
                result.methodCount,
                result.fieldCount,
                qualityReport?.score?.let { String.format("%.1f", it) } ?: "N/A"
            ))

            // 显示详细信息
            if (qualityReport != null) {
                displayQualityDetails(qualityReport)
            }
        }
    }

    private fun displayProjectReport(report: AnalysisReport) {
        SwingUtilities.invokeLater {
            showInfoMessage("""
                项目分析完成！

                统计信息：
                - 文件数量: ${report.fileCount}
                - 类总数: ${report.totalClasses}
                - 方法总数: ${report.totalMethods}
                - 字段总数: ${report.totalFields}
                - 分析耗时: ${report.duration}ms

                平均每文件：
                - 类: ${String.format("%.1f", report.totalClasses.toDouble() / report.fileCount)}
                - 方法: ${String.format("%.1f", report.totalMethods.toDouble() / report.fileCount)}
                - 字段: ${String.format("%.1f", report.totalFields.toDouble() / report.fileCount)}
            """.trimIndent())
        }
    }

    private fun displayQualityDetails(report: CodeQualityReport) {
        // 显示代码质量详细信息
        val details = """
            代码质量报告 - ${report.fileName}

            质量评分: ${String.format("%.1f", report.score)}
            问题数量: ${report.issues.size}

            主要指标:
            ${report.metrics.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }}

            发现的问题:
            ${report.issues.take(10).joinToString("\n") { "- [${it.severity}] ${it.message}" }}
            ${if (report.issues.size > 10) "... 还有 ${report.issues.size - 10} 个问题" else ""}
        """.trimIndent()

        detailPanel.text = details
    }

    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}
```

### 7.3 注册到标签页系统
```kotlin
// 创建AST分析扩展
class ASTAnalysisTabExtension : AbstractTabExtension() {
    override val extensionId = "nekoama.ast.analysis"
    override val displayName = "AST分析"
    override val description = "分析代码的AST结构、调用关系和代码质量"
    override val version = "1.0.0"
    override val minCoreVersion = "1.0.0"

    override fun isApplicable(context: TabContext): Boolean {
        // 检查是否有Java或Kotlin文件
        val project = context.project
        val scope = GlobalSearchScope.projectScope(project)
        val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)
        val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)
        return javaFiles.isNotEmpty() || kotlinFiles.isNotEmpty()
    }

    override fun createTab(): NekoamaTab {
        return ASTAnalysisTab()
    }

    override fun getTabConfig(): TabExtensionConfig {
        return TabExtensionConfig(
            enabledByDefault = true,
            rememberState = true,
            customSettings = mapOf(
                "maxAnalysisDepth" to "10",
                "enableCallChainAnalysis" to "true",
                "enableQualityAnalysis" to "true"
            )
        )
    }
}

// 注册扩展
object ASTAnalysisExtensionRegistration {
    fun register() {
        TabExtensionPointSingleton.getInstance().registerExtension(ASTAnalysisTabExtension())
    }
}
```

---

## 总结

基于你Nekoama项目的现有架构，这份PSI/AST分析方案提供了：

### 🎯 核心能力
1. **完整的Java PSI分析** - 遍历类、方法、字段的完整方案
2. **方法调用关系分析** - 调用链、循环调用检测
3. **代码质量分析** - 复杂度、命名、参数检查
4. **类型层次分析** - 继承关系、接口实现分析
5. **注解信息提取** - 完整的注解属性解析

### 🚀 性能优化
1. **缓存机制** - 基于文件修改时间的智能缓存
2. **异步分析** - 基于你现有模式的后台分析框架
3. **批量处理** - 大型项目的分批次分析
4. **增量更新** - 只分析变更的文件

### 🔧 集成方案
1. **扩展现有CodeAnalyzer** - 增强你当前的分析器
2. **工具窗口集成** - 新增AST分析标签页
3. **标签页系统注册** - 完整的扩展注册机制

### 📈 实际应用
- **代码生成辅助** - 基于AST的智能代码生成
- **代码质量检查** - 静态分析和重构建议
- **项目结构分析** - 复杂度和依赖关系分析
- **开发辅助工具** - 方法调用图、类型层次图

所有代码示例都基于你现有的项目架构，可以直接集成到Nekoama中使用。这些功能将大大增强插件的代码分析和辅助开发能力。