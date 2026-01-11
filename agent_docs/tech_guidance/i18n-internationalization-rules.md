# i18n Internationalization Rules

## 1. Axioms (不可违背的公理)
- **Status**: Stable
- **Core Principle**: 用户可见的 UI 文案必须通过 `NekoamaBundle` 从资源文件获取，禁止硬编码；报错信息可使用简体中文

## 2. Mapping Rules (规则映射)

| Category | ❌ Forbidden (Strict Ban) | ✅ Required (Pattern) |
| :--- | :--- | :--- |
| UI 文案 (按钮、标签、菜单等) | `"Submit"`, `"取消"`, `"Generating..."` | `NekoamaBundle.message("action.submit")` |
| 用户提示信息 | `"Please enter your name"` | `NekoamaBundle.message("prompt.enter.name")` |
| Settings 描述 | `description="Enable the feature"` | `NekoamaBundle.message("settings.enable.feature")` |
| 技术报错 (throw/exception) | `throw Exception("网络错误")` | `throw NekoamaError.NetworkError("网络错误")` |
| 用户友好报错 (Notification) | `"连接超时，请重试"` | `NekoamaBundle.message("error.user.connection.timeout")` |
| 日志信息 (logger) | `logger.error("处理失败")` | `logger.error("处理失败: ${error.message}")` |

## 3. Critical Snippets (核心代码范式)

### 3.1 定义资源 key

```properties
# src/main/resources/messages/NekoamaBundle.properties

# 简单文案
action.submit=Submit
action.cancel=Cancel

# 带参数文案 (使用 {0}, {1}, ... 占位)
notification.greeting=Hello, {0}!
error.api.failed=API request failed: {0}

# 用户友好报错 (英文)
error.user.connection.timeout=Network connection timeout, please check network connection and retry
error.user.invalid.api.key=Invalid API key, please check key configuration
```

### 3.2 使用 NekoamaBundle 获取文案

```kotlin
import com.cw2.nekoama.shared.i18n.NekoamaBundle

// ✅ 简单文案
val buttonText = NekoamaBundle.message("action.submit")

// ✅ 带参数文案
val greeting = NekoamaBundle.message("notification.greeting", userName)
val errorMsg = NekoamaBundle.message("error.api.failed", statusCode)

// ❌ 禁止硬编码 UI 文案
val badText = "Submit"  // 禁止

// ❌ 禁止在 UI 代码中直接使用中文文案
val badText2 = "提交"  // 禁止
```

### 3.3 报错信息区分

```kotlin
// ✅ 技术报错使用 Result<NekoamaError>，描述可用中文
fun process(): Result<Data> {
    return when {
        invalidInput -> Result.error(
            NekoamaError.ValidationError.InvalidConfiguration("配置无效：endpoint 为空")
        )
        else -> Result.success(data)
    }
}

// ✅ 用户友好报错使用 NekoamaBundle (英文)
fun showErrorNotification() {
    NekoamaNotifier.showError(
        NekoamaBundle.message("error.user.connection.timeout")
    )
}

// ✅ 日志可以使用中文
logger.error("处理失败: ${error.message}")
```

### 3.4 Action 中使用 i18n

```kotlin
class GenerateNamingAction : DumbAwareAction() {
    override fun getActionText(): String {
        // ✅ 动态获取文案
        return NekoamaBundle.message("action.naming.text")
    }

    override fun actionPerformed(e: AnActionEvent) {
        // ✅ 用户提示使用 Bundle
        project?.let {
            NekoamaNotifier.showInfo(
                it,
                NekoamaBundle.message("progress.generatingNaming")
            )
        }
    }
}
```

### 3.5 Settings 中使用 i18n

```kotlin
class NekoamaConfigurable : Configurable {
    override fun getDisplayName(): String {
        // ✅ 设置页面标题
        return NekoamaBundle.message("settings.title")
    }
}

// Config 中使用 @Nls 注解
data class AIConfig(
    @Nls(capitalization = Nls.Capitalization.Title)
    val displayName: String = NekoamaBundle.message("settings.ai.section")
)
```

### 3.6 资源文件组织规范

```properties
# =============================================================================
# 命名规范
# =============================================================================

# 按功能区域分组，使用点号分隔
# 格式: <area>.<subcategory>.<item>

# Action 相关
action.<name>.text=Display Text
action.<name>.description=Description

# Settings 相关
settings.<section>.<item>=Label
settings.<section>.<item>.hint=Hint text

# Error 相关
error.<type>.<specific>=Error message
error.user.<specific>=User-friendly error message

# Progress 相关
progress.<action>=Progress text

# 按钮通用
button.<action>=Label
```

## 4. Verification (如何验证)

### Code Review 检查点

- [ ] **无硬编码 UI 文案**: 搜索代码中的字符串字面量（除正则、日志、测试外）
- [ ] **NekoamaBundle.message**: 所有用户可见文案都通过 Bundle 获取
- [ ] **资源文件存在**: 使用的 key 在 `NekoamaBundle.properties` 中有定义
- [ ] **报错信息正确分类**: 技术报错用中文 + NekoamaError，用户报错用英文 + NekoamaBundle
- [ ] **参数占位符**: 带参数的 key 使用 {0}, {1} 格式

### 自动化检查 (可选)

```bash
# 搜索可能硬编码的 UI 字符串 (英文字符串长度 > 3 且不在特定上下文)
rg '"[A-Z][a-zA-Z\s]{3,}"' --type kotlin

# 搜索可能硬编码的中文字符串 (排除注释和文档)
rg '("[^"]*[\u4e00-\u9fa5]+[^"]*")' --type kotlin
```

### 常见违规模式

```kotlin
// ❌ 硬编码英文字符串
button.text = "Submit"
JLabel("Configuration")

// ❌ 硬编码中文字符串在 UI 中
JLabel("配置")

// ✅ 正确使用
button.text = NekoamaBundle.message("button.submit")
JLabel(NekoamaBundle.message("settings.title"))
```

## 5. 特殊场景处理

### 5.1 动态文案组合

```kotlin
// ✅ 使用带参数的 key
NekoamaBundle.message("settings.depth.value", depth, min, max)
// settings.depth.value={0} ({1}-{2})

// ❌ 禁止字符串拼接
"Depth: $depth ($min-$max)"  // 禁止
```

### 5.2 复数形式处理

```kotlin
// ✅ 根据数量选择不同 key
val message = when (count) {
    1 -> NekoamaBundle.message("notification.single.item")
    else -> NekoamaBundle.message("notification.multiple.items", count)
}
```

### 5.3 第三方库回调文案

```kotlin
// ✅ 即使是第三方库回调，也要通过 Bundle
someLibrary.onError { error ->
    NekoamaNotifier.showError(
        NekoamaBundle.message("error.user.operation.failed", error.message)
    )
}
```

## 6. 例外情况

以下情况可以**不使用** NekoamaBundle：
1. **日志内容** (Logger.info/error/debug) - 可使用中文
2. **单元测试** - 测试代码内硬编码无限制
3. **正则表达式模式** - 技术字符串
4. **技术常量** (如 "UTF-8", "application/json")
5. **NekoamaError 报错描述** - 可使用中文 (仅限异常类内部)
