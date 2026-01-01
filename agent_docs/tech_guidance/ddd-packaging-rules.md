# DDD 分包架构技术约束

## 1. Axioms (不可违背的公理)
- **Status**: Stable
- **Core Principle**: 领域层不依赖基础设施层，依赖方向始终从外向内（Interfaces → Domain ← Infrastructure）

## 2. Mapping Rules (规则映射)

| Category | ❌ Forbidden (Strict Ban) | ✅ Required (Pattern) |
| :--- | :--- | :--- |
| **依赖方向** | `domain` 依赖 `infrastructure` | `infrastructure` 依赖 `domain` |
| **外部 API** | domain 层直接使用 PSI/OkHttp 等 | 在 domain/model 定义接口，infrastructure 实现 |
| **配置类** | 放在 domain 层 | 放在 infrastructure 层（如 `CustomGeneratorConfig`） |
| **业务语言** | 技术命名（`AIProvider`, `OpenAIClient`） | 业务语言（`CodeSuggestionGenerator`, `OpenAIGenerator`） |
| **Service 职责** | domain/service 包含基础设施实现细节 | domain/service 只做业务编排，依赖注入接口 |

## 3. Critical Snippets (核心代码范式)

### 3.1 防腐层接口（Domain Model 层）

```kotlin
// ✅ 正确：在 domain/model 定义接口，参数可以是外部类型（PsiElement）
package com.cw2.nekoama.domain.code_suggestion_gen.model

interface CodeElementAnalyzer {
    fun analyzeMethod(method: PsiElement): Result<MethodContext>
    fun analyzeClass(clazz: PsiElement): Result<ClassContext>
    fun detectLanguage(element: PsiElement): ProgrammingLanguage
    fun getProjectMetadata(): ProjectMetadata
}
```

### 3.2 基础设施实现（Infrastructure 层）

```kotlin
// ✅ 正确：infrastructure 实现接口，处理 PSI 等外部依赖
package com.cw2.nekoama.infrastructure.code_suggestion_gen.code_analysis

class UniversalCodeElementAnalyzer(
    private val project: Project
) : CodeElementAnalyzer {

    private val javaAnalyzer = JavaCodeAnalyzer(project)
    private val kotlinAnalyzer = KotlinCodeAnalyzer(project)

    override fun analyzeMethod(method: PsiElement): Result<MethodContext> {
        return when (detectLanguage(method)) {
            ProgrammingLanguage.JAVA -> javaAnalyzer.analyzeJavaMethod(method as PsiMethod)
            ProgrammingLanguage.KOTLIN -> kotlinAnalyzer.analyzeKotlinFunction(method as KtFunction)
            else -> Result.error(...)
        }
    }
}
```

### 3.3 应用服务编排（Domain Service 层）

```kotlin
// ✅ 正确：domain/service 通过依赖注入使用接口
package com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis

class CodeAnalysisService(
    private val analyzer: CodeElementAnalyzer  // 依赖接口，不依赖具体实现
) {
    fun analyzeMethod(method: PsiElement): Result<MethodContext> {
        return analyzer.analyzeMethod(method)
    }

    fun buildCodeAnalysisReport(element: PsiElement): AnalysisReport {
        val language = analyzer.detectLanguage(element)
        val metadata = analyzer.getProjectMetadata()
        // 业务编排逻辑
        return AnalysisReport(language, metadata)
    }
}
```

### 3.4 Action 层使用（Interfaces 层）

```kotlin
// ✅ 正确：interfaces 层组装依赖关系
package com.cw2.nekoama.interfaces.intellij.actions

class GenerateCommentAction : BaseAction() {
    private fun buildCodeContext(project: Project, element: PsiElement): CodeContext? {
        val codeAnalysisService = CodeAnalysisService(
            UniversalCodeElementAnalyzer(project)  // 注入具体实现
        )
        val language = codeAnalysisService.detectLanguage(element)
        val context = codeAnalysisService.analyzeMethod(element)
        // ...
    }
}
```

### 3.5 配置类归属（Infrastructure 层）

```kotlin
// ✅ 正确：配置类放在 infrastructure 层
package com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config

data class CustomGeneratorConfig(
    val generatorName: String,
    val apiUrl: String,
    val apiKey: String,
    val timeoutMs: Long
) : GeneratorConfig  // 实现定义在 domain 的配置接口
```

### 3.6 ❌ 错误示例

```kotlin
// ❌ 错误：domain 层不应依赖 PSI
package com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis

class UniversalCodeAnalyzer(
    private val project: Project  // ❌ Project 来自 infrastructure
) : CodeAnalyzer {
    fun analyzeMethod(method: PsiMethod): Result<MethodContext> {  // ❌ PsiMethod 来自 infrastructure
        // ...
    }
}

// ❌ 错误：domain 层不应包含配置类
package com.cw2.nekoama.domain.code_suggestion_gen.model.config

data class CustomAPIConfig(  // ❌ 配置类应在 infrastructure
    val apiUrl: String,
    val apiKey: String
)
```

## 4. Layer Responsibilities (各层职责)

| Layer | 职责 | 示例 |
| :--- | :--- | :--- |
| **domain/model** | 定义领域模型、防腐层接口 | `CodeSuggestionGenerator`, `CodeElementAnalyzer` |
| **domain/service** | 业务流程编排，依赖注入接口 | `CodeAnalysisService`, `CodeSuggestionService` |
| **infrastructure/** | 实现防腐层接口，处理外部依赖 | `OpenAIGenerator`, `UniversalCodeElementAnalyzer` |
| **infrastructure/**/model/config | 配置类（实现 domain 的配置接口） | `CustomGeneratorConfig` |
| **interfaces/** | IntelliJ 平台集成，组装依赖关系 | `GenerateCommentAction`, `NekoamaSettings` |

## 5. Naming Conventions (命名规范)

| Type | ❌ Avoid | ✅ Use |
| :--- | :--- | :--- |
| **Generator** | `AIProvider`, `OpenAIClient` | `CodeSuggestionGenerator`, `OpenAIGenerator` |
| **Config** | `CustomAPIConfig` | `CustomGeneratorConfig` |
| **Status** | `AIProviderStatus` | `GeneratorStatus` |
| **Analyzer** | `CodeAnalyzer` (domain 层) | `CodeElementAnalyzer` (接口), `UniversalCodeElementAnalyzer` (实现) |
| **Service Method** | `createAIProvider()`, `getProjectInfo()` | `createCodeSuggestionGenerator()`, `getProjectMetadata()` |

## 6. Verification (如何验证)

* **依赖检查**: 搜索 `import` 语句，确认 `domain` 包中没有引用 `infrastructure` 的类
* **接口定义**: 所有外部 API（PSI、OkHttp 等）的使用都应该通过 domain/model 中的接口隔离
* **配置位置**: 配置类应该在 `infrastructure/.../model/config/` 下
* **命名检查**: 类名应使用业务语言而非技术术语

## 7. Decision Record (决策记录)

### 为什么接口参数可以使用外部类型（如 PsiElement）？
**决策**: 允许接口方法参数使用 `PsiElement` 等外部类型
**原因**:
- 强行抽象会增加复杂度，且无法完全隔离 IntelliJ PSI API
- 依赖倒置的核心是"领域定义接口，基础设施实现"，参数类型可以是共同依赖的类型
- 关键是返回值应该是领域类型（如 `MethodContext`），而不是外部类型

### 为什么配置类在 infrastructure 层？
**决策**: 配置类放在 `infrastructure/.../model/config/`
**原因**:
- 配置是基础设施的实现细节（API endpoint、timeout 等）
- domain 层只定义配置接口（`GeneratorConfig`），不关心具体字段
- 符合"依赖倒置"原则：infrastructure 依赖 domain 的配置接口

### 为什么要区分 CodeElementAnalyzer 和 CodeAnalysisService？
**决策**: 分离接口（`CodeElementAnalyzer`）和编排服务（`CodeAnalysisService`）
**原因**:
- `CodeElementAnalyzer` 是防腐层接口，定义"需要什么能力"
- `CodeAnalysisService` 是应用服务，负责"业务流程编排"
- 清晰的职责分离便于测试和维护
