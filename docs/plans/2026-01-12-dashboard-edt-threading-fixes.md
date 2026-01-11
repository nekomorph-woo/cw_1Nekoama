# Dashboard Tab EDT 线程修复实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**目标:** 修复 Dashboard Tab 中的 EDT 线程违规问题，确保网络操作在后台线程执行，UI 更新在 EDT 线程执行。

**架构:** 使用 `ProgressManager` + `Task.Backgroundable` 替代直接协程启动，遵循 IntelliJ 平台的 EDT 线程规则。

**技术栈:**
- IntelliJ Platform SDK (ProgressManager, Task.Backgroundable)
- Kotlin Coroutines (Dispatchers.IO)
- `IntellijTaskManager` 工具类
- ApplicationManager.invokeLater (替代 SwingUtilities.invokeLater)

**参考文档:**
- `agent_docs/tech_guidance/edt-threading-rules.md`
- `agent_docs/tech_guidance/intellij-swing-ui-rules.md`
- `agent_docs/code_review/code_review.md`

---

## 前置准备

### Step 0: 验证当前问题

**文件:** `DashboardTab.kt:218-223`

**当前错误代码:**
```kotlin
private fun testConnection() {
    // 使用 tabScope 在后台协程中执行
    tabScope.launch {
        refreshNetworkStatus()
    }
}
```

**验证命令:**
```bash
# 运行插件并点击 Test Connection 按钮
# 预期看到: SlowOperations are prohibited on EDT 警告
```

---

## Task 1: 修复 testConnection() 方法 - EDT 线程违规 (P0/Critical)

**文件:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt:218-223`

**Step 1: 导入必要的类**

在文件顶部添加导入：
```kotlin
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.application.Application
import kotlinx.coroutines.withTimeout
```

**Step 2: 重写 testConnection() 方法**

将 `testConnection()` 方法从第 218 行替换为：

```kotlin
private fun testConnection() {
    val service = networkTestService ?: run {
        // Service 不可用时直接更新 UI（已在 EDT 中）
        networkStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
        networkStatusLabel.foreground = JBColor.RED
        return
    }

    // 更新为测试中状态
    networkStatusLabel.text = NekoamaBundle.message("dashboard.status.testing")
    networkStatusLabel.foreground = JBColor.GRAY

    ProgressManager.getInstance().run(object : Task.Backgroundable(
        project,
        NekoamaBundle.message("dashboard.progress.testing.connection"),
        true  // 可取消
    ) {
        private var testResult: com.cw2.nekoama.domain.statistics.model.ConnectivityStatus? = null
        private var testError: Throwable? = null

        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = true
            indicator.text = NekoamaBundle.message("dashboard.progress.testing.connection")

            try {
                // 使用 runBlocking 在后台线程中执行挂起函数
                testResult = kotlinx.coroutines.runBlocking {
                    withTimeout(15_000) {  // 15 秒超时
                        service.testConnectivity(null)
                    }
                }
            } catch (e: Exception) {
                testError = e
            }
        }

        override fun onSuccess() {
            // onSuccess 自动在 EDT 上执行
            if (testError != null) {
                networkStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
                networkStatusLabel.foreground = JBColor.RED
                NekoamaLogger.error("DashboardTab", "Test connection failed", mapOf("error" to (testError?.message ?: "unknown")))
            } else if (testResult != null) {
                val status = testResult!!
                if (status.isConnected) {
                    val timeStr = if (status.responseTime > 0) {
                        " (${status.responseTime}ms)"
                    } else {
                        ""
                    }
                    networkStatusLabel.text = NekoamaBundle.message("dashboard.status.connected", timeStr)
                    networkStatusLabel.foreground = JBColor.GREEN
                } else {
                    networkStatusLabel.text = status.message
                    networkStatusLabel.foreground = JBColor.RED
                }
            }
        }

        override fun onThrowable(error: Throwable) {
            // 错误处理（在 EDT 上）
            networkStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
            networkStatusLabel.foreground = JBColor.RED
            NekoamaLogger.error("DashboardTab", "Test connection error", mapOf("error" to (error.message ?: "unknown")))
        }
    })
}
```

**Step 3: 添加 i18n 消息键**

编辑 `src/main/resources/messages/NekoamaBundle.properties`，添加：

```properties
dashboard.status.testing=Testing...
dashboard.progress.testing.connection=Testing API Connection
```

**Step 4: 验证修复**

```bash
# 重新构建插件
./gradlew buildPlugin

# 在 IDE 中运行插件
# 点击 Test Connection 按钮
# 预期: 不再出现 SlowOperations 警告
# 预期: 网络状态正确更新
```

**Step 5: 提交**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git add src/main/resources/messages/NekoamaBundle.properties
git commit -m "fix: 修复 testConnection EDT 线程违规

- 使用 ProgressManager + Task.Backgroundable 替代直接协程启动
- 添加 15 秒超时控制
- onSuccess 回调自动在 EDT 上执行，无需手动切换
- 添加测试中状态显示"
```

---

## Task 2: 替换 SwingUtilities.invokeLater 为 EdtExecutor (P1/Important)

**文件:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**受影响行数:** 315, 327, 335, 350, 360, 370, 399, 408, 418, 435, 444

**Step 1: 添加 EdtExecutor 导入**

```kotlin
import com.intellij.openapi.application.Application
```

**Step 2: 移除 SwingUtilities 导入**

删除或注释掉：
```kotlin
// import javax.swing.SwingUtilities
```

**Step 3: 批量替换所有 SwingUtilities.invokeLater**

将文件中所有 `SwingUtilities.invokeLater` 替换为 `ApplicationManager.getApplication().invokeLater`

| 原始行 | 代码位置 | 替换内容 |
|-------|---------|---------|
| 315 | refreshData() catch block | `ApplicationManager.getApplication().invokeLater {` |
| 327 | refreshNetworkStatus() | `ApplicationManager.getApplication().invokeLater {` |
| 335 | refreshNetworkStatus() | `ApplicationManager.getApplication().invokeLater {` |
| 350 | refreshNetworkStatus() | `ApplicationManager.getApplication().invokeLater {` |
| 360 | refreshTokenStats() | `ApplicationManager.getApplication().invokeLater {` |
| 370 | refreshTokenStats() | `ApplicationManager.getApplication().invokeLater {` |
| 399 | refreshTokenStats() | `ApplicationManager.getApplication().invokeLater {` |
| 408 | refreshUsageStats() | `ApplicationManager.getApplication().invokeLater {` |
| 418 | refreshUsageStats() | `ApplicationManager.getApplication().invokeLater {` |
| 435 | refreshUsageStats() | `ApplicationManager.getApplication().invokeLater {` |
| 444 | onActivated() | `ApplicationManager.getApplication().invokeLater {` |

**Step 4: 验证替换**

```bash
# 确认没有遗漏的 SwingUtilities.invokeLater
grep -n "SwingUtilities.invokeLater" src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt

# 预期: 无匹配结果
```

**Step 5: 手动测试 UI 更新**

```bash
# 重新构建插件
./gradlew buildPlugin

# 测试场景:
# 1. 打开 Dashboard Tab - 确认数据正确加载
# 2. 点击 Refresh 按钮 - 确认所有面板刷新
# 3. 点击 Test Connection - 确认网络状态更新
# 4. 切换主题（亮/暗）- 确认 UI 渲染正常
```

**Step 6: 提交**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "refactor: 使用 EdtExecutor 替代过时的 SwingUtilities.invokeLater

- 11 处 SwingUtilities.invokeLater 替换为 ApplicationManager.getApplication().invokeLater
- 符合 Swing UI Rules (agent_docs/tech_guidance/intellij-swing-ui-rules.md line 16)"
```

---

## Task 3: 为 refreshNetworkStatus 添加超时控制 (P1/Important)

**文件:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt:324-355`

**Step 1: 添加 withTimeout 导入**

```kotlin
import kotlinx.coroutines.withTimeout
```

**Step 2: 修改 refreshNetworkStatus() 方法**

将 `service.testConnectivity(null)` 调用包装在 `withTimeout` 中：

```kotlin
private suspend fun refreshNetworkStatus() {
    val service = networkTestService
    if (service == null) {
        ApplicationManager.getApplication().invokeLater {
            networkStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
        }
        return
    }

    try {
        // 添加 15 秒超时控制
        val status = withTimeout(15_000) {
            service.testConnectivity(null)
        }
        ApplicationManager.getApplication().invokeLater {
            if (status.isConnected) {
                val timeStr = if (status.responseTime > 0) {
                    " (${status.responseTime}ms)"
                } else {
                    ""
                }
                networkStatusLabel.text = NekoamaBundle.message("dashboard.status.connected", timeStr)
                networkStatusLabel.foreground = JBColor.GREEN
            } else {
                networkStatusLabel.text = status.message
                networkStatusLabel.foreground = JBColor.RED
            }
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        ApplicationManager.getApplication().invokeLater {
            networkStatusLabel.text = NekoamaBundle.message("dashboard.error.timeout")
            networkStatusLabel.foreground = JBColor.RED
        }
    } catch (e: Exception) {
        ApplicationManager.getApplication().invokeLater {
            networkStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
            networkStatusLabel.foreground = JBColor.RED
        }
    }
}
```

**Step 3: 添加超时错误消息**

编辑 `src/main/resources/messages/NekoamaBundle.properties`，添加：

```properties
dashboard.error.timeout=Connection timeout
```

**Step 4: 验证超时控制**

```bash
# 重新构建插件
./gradlew buildPlugin

# 测试场景: 使用无效的 API endpoint 触发超时
# 1. 修改设置中的 API endpoint 为无效地址 (如 http://invalid.local)
# 2. 点击 Test Connection
# 3. 预期: 15 秒后显示 "Connection timeout"
```

**Step 5: 提交**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git add src/main/resources/messages/NekoamaBundle.properties
git commit -m "fix: 添加网络测试超时控制

- refreshNetworkStatus 添加 15 秒超时
- 捕获 TimeoutCancellationException 并显示友好错误
- 符合 Dashboard Plan 要求 (line 979: 超时时间 10 秒)"
```

---

## Task 4: 清理未使用的协程 Scope (可选/建议)

**文件:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**Step 1: 评估 tabScope 使用**

检查 `tabScope` 是否还被其他方法使用：

```bash
grep -n "tabScope.launch" src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
```

**Step 2: 决定是否移除 tabScope**

如果 `testConnection()` 已经改为使用 `ProgressManager`，而其他方法（`refreshData()`, `refreshNetworkStatus()` 等）是挂起函数（`suspend fun`），不需要 `tabScope.launch`。

**情况 A: 如果 refreshData() 仍然使用 tabScope.launch**

保留 `tabScope`，但将 `Dispatchers.IO` 改为 `Dispatchers.Default`：

```kotlin
private val tabScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

**情况 B: 如果没有其他地方使用 tabScope.launch**

移除以下内容：

```kotlin
// 删除导入
// import kotlinx.coroutines.CoroutineScope
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.SupervisorJob
// import kotlinx.coroutines.cancel
// import kotlinx.coroutines.launch

// 删除字段
// private val tabScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// 删除 onDestroy() 中的 cancel 调用
// override fun onDestroy() {
//     tabScope.cancel()
// }
```

**Step 3: 提交**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "refactor: 移除未使用的 tabScope

- testConnection 已改用 ProgressManager
- refreshData 使用后台协程但不需要显式 Scope
- 简化生命周期管理"
```

---

## 验证步骤

### 最终验证清单

**文件:** `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**Step 1: 代码规范检查**

```bash
# 确认没有 SlowOperations 违规
grep -n "tabScope.launch" src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
# 预期: testConnection() 中不再使用

# 确认没有过时的 SwingUtilities.invokeLater
grep -n "SwingUtilities.invokeLater" src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
# 预期: 无匹配结果

# 确认使用了 EdtExecutor
grep -c "ApplicationManager.getApplication().invokeLater" src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
# 预期: 11 处
```

**Step 2: 构建测试**

```bash
./gradlew buildPlugin
```

**Step 3: 运行时测试**

在 IntelliJ IDEA 中运行插件并验证：

1. **基础功能:**
   - [ ] Dashboard Tab 正常显示
   - [ ] 快捷操作按钮可点击
   - [ ] 主题切换正常（亮/暗）

2. **网络连接测试:**
   - [ ] 点击 "Test Connection" 按钮不触发 SlowOperations 警告
   - [ ] 测试成功显示绿色状态和响应时间
   - [ ] 测试失败显示红色状态和错误消息
   - [ ] 测试超时（15秒）显示超时消息

3. **数据刷新:**
   - [ ] 点击 "Refresh" 按钮所有面板数据正确更新
   - [ ] Token 统计显示正确
   - [ ] Usage 统计显示正确

4. **日志验证:**
   - [ ] 没有异常堆栈输出到 IDEA 日志
   - [ ] NekoamaLogger 正确记录操作

**Step 4: 性能验证**

```bash
# 使用 IDEA Profiler 检查:
# - EDT 阻塞时间 < 50ms
# - 网络操作在后台线程执行
# - UI 更新在 EDT 线程执行
```

---

## 附录: 错误处理参考

### EDT 线程规则速查表

| 操作 | 正确方式 | 错误方式 |
|-----|---------|---------|
| UI 更新 | `ApplicationManager.getApplication().invokeLater { }` | 直接从后台线程更新 |
| 长时间运行 | `ProgressManager.getInstance().run(Task.Backgroundable)` | `launch { }` 在 EDT 上 |
| PSI 读取 | `ReadAction.compute { }` | 直接读取 |
| 协程启动 | 在 Task.Backgroundable.run() 内使用 `runBlocking` | `launch { }` 在 ActionListener 内 |

### 测试用例参考

**场景: 网络测试超时**

```kotlin
// 模拟超时场景
val invalidEndpoint = "http://invalid.local"
// 修改 API endpoint 设置
// 点击 Test Connection
// 预期: 15 秒后显示超时消息
```

**场景: 网络测试成功**

```kotlin
// 使用有效的 API endpoint
// 点击 Test Connection
// 预期: 显示 "Connected (XXXms)" 绿色状态
```

---

**预计总工作量:** 约 4-6 小时

**依赖项:**
- 无外部依赖
- 不需要修改其他模块

**风险:**
- 低风险：主要是重构现有代码
- 测试覆盖：需要手动测试 UI 场景

**完成后:**
- 请求代码评审进行二次验证
- 更新 `agent_docs/code_review/code_review.md` 标记问题已修复
