# Dashboard Network Status Enhancement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 增强网络状态面板，显示 Proxy 配置、API Endpoint、Model 名称和排查指南

**Architecture:** 遵循 DDD 分层架构，从 Domain 层扩展模型开始，到 Infrastructure 层实现数据填充，最后在 Interfaces 层完成 UI 展示

**Tech Stack:** Kotlin 2.1, JVM 21, IntelliJ Platform SDK, Swing UI

---

## Overview

当前 DashboardTab 的网络状态面板仅显示简单的连接状态文本，需要扩展为显示完整信息：
- Proxy 配置（代理类型和地址）
- API Endpoint
- Model 名称
- 连接失败时的排查指南

---

## Task 1: Domain Layer - 扩展 ConnectivityStatus 模型

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/domain/statistics/model/ConnectivityStatus.kt`

**Step 1: 添加 endpoint 和 model 字段**

在 `ConnectivityStatus` data class 中添加两个必填字段：

```kotlin
/**
 * API 连通性状态
 *
 * @property isConnected 是否连通
 * @property responseTime 响应时间（毫秒）
 * @property message 状态消息
 * @property proxyConfig 代理配置信息
 * @property troubleshootingGuide 排查指南（仅失败时）
 * @property endpoint API 端点（如 https://api.openai.com）
 * @property model 模型名称（如 gpt-4o-mini）
 */
data class ConnectivityStatus(
    val isConnected: Boolean,
    val responseTime: Long = -1,
    val message: String,
    val proxyConfig: ProxyConfig? = null,
    val troubleshootingGuide: List<String>? = null,
    val endpoint: String,
    val model: String
) {
    // ... existing code
}
```

**Step 2: 编译验证**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESS

**Step 3: 提交**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/model/ConnectivityStatus.kt
git commit -m "feat(domain): add endpoint and model fields to ConnectivityStatus"
```

---

## Task 2: Infrastructure Layer - 修改 NetworkTestServiceImpl 填充新字段

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/domain/statistics/service/NetworkTestServiceImpl.kt`

**Step 1: 修改 testConnectivity 方法填充 endpoint 和 model**

在 `testConnectivity` 方法中，从 `NekoamaSettings` 读取配置并传递给 `ConnectivityStatus`：

```kotlin
override suspend fun testConnectivity(endpoint: String?): ConnectivityStatus {
    return withContext(Dispatchers.IO) {
        val settings = NekoamaSettings.getInstance()

        // 确定 endpoint（优先使用参数，fallback 到设置）
        val finalEndpoint = endpoint
            ?: settings.apiEndpoint
                .ifEmpty { "https://api.openai.com" }

        // 获取 model（从设置中读取）
        val finalModel = settings.model

        // 检测代理配置
        val proxyConfig = ProxyDetector.detectSystemProxy(finalEndpoint)

        // 执行连接测试
        val testResult = ProxyConnectionTester.testProxyConnection(proxyConfig, finalEndpoint)

        // 生成排查指南（仅失败时）
        val troubleshootingGuide = if (!testResult.success) {
            generateTroubleshootingGuide(proxyConfig, testResult)
        } else {
            null
        }

        ConnectivityStatus(
            isConnected = testResult.success,
            responseTime = testResult.responseTime,
            message = testResult.message,
            proxyConfig = proxyConfig,
            troubleshootingGuide = troubleshootingGuide,
            endpoint = finalEndpoint,
            model = finalModel
        )
    }
}
```

**Step 2: 编译验证**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESS

**Step 3: 提交**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/service/NetworkTestServiceImpl.kt
git commit -m "feat(infrastructure): populate endpoint and model in NetworkTestServiceImpl"
```

---

## Task 3: I18N Layer - 添加网络面板国际化文案

**Files:**
- Modify: `src/main/resources/messages/NekoamaBundle.properties`

**Step 1: 在 DASHBOARD TAB 部分添加新的文案 key**

在 `dashboard.error.timeout` 后添加：

```properties
# =============================================================================
# DASHBOARD NETWORK STATUS PANEL
# =============================================================================

dashboard.network.proxy=Proxy: {0}
dashboard.network.endpoint=Endpoint: {0}
dashboard.network.model=Model: {0}
dashboard.network.status.prefix=Status:
dashboard.network.proxy.direct=Direct (no proxy)
dashboard.network.troubleshooting.title=Troubleshooting Guide:
```

**Step 2: 验证属性文件语法**

Run: `./gradlew processResources`
Expected: BUILD SUCCESS

**Step 3: 提交**

```bash
git add src/main/resources/messages/NekoamaBundle.properties
git commit -m "feat(i18n): add network status panel i18n keys"
```

---

## Task 4: UI Layer - 重构网络状态面板布局

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**Step 1: 添加新的 UI 组件引用**

在类的成员变量区域（约第 90-98 行）添加：

```kotlin
// UI 组件引用
private lateinit var mainPanel: JPanel
private lateinit var quickActionsPanel: JPanel
private lateinit var networkStatusPanel: JPanel

// 网络状态面板的各个子组件
private lateinit var proxyLabel: JBLabel
private lateinit var endpointLabel: JBLabel
private lateinit var modelLabel: JBLabel
private lateinit var connectionStatusLabel: JBLabel
private lateinit var troubleshootingPanel: JPanel
private lateinit var troubleshootingLabel: JBLabel

private lateinit var tokenStatsPanel: JPanel
private lateinit var tokenStatsLabel: JBLabel
private lateinit var usageStatsPanel: JPanel
private lateinit var usageStatsLabel: JBLabel
```

**Step 2: 重构 createNetworkStatusPanel 方法**

替换整个 `createNetworkStatusPanel` 方法（约第 284-303 行）：

```kotlin
private fun createNetworkStatusPanel(): JPanel {
    return JPanel().apply {
        layout = BorderLayout(8, 8)
        background = TabThemeManager.getTabBackgroundColor()
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(TabThemeManager.getBorderColor()),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        )

        // 标题
        val titleLabel = JBLabel(NekoamaBundle.message("dashboard.section.network")).apply {
            font = font.deriveFont(Font.BOLD, 14f)
        }
        add(titleLabel, BorderLayout.NORTH)

        // 内容面板（多行信息）
        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = TabThemeManager.getTabBackgroundColor()
            alignmentX = Component.LEFT_ALIGNMENT

            // Proxy 配置行
            proxyLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
                foreground = JBColor.GRAY
            }
            add(proxyLabel)

            // API Endpoint 行
            endpointLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
                foreground = JBColor.GRAY
            }
            add(endpointLabel)

            // Model 行
            modelLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
                foreground = JBColor.GRAY
            }
            add(modelLabel)

            // 连接状态行
            connectionStatusLabel = JBLabel(NekoamaBundle.message("dashboard.status.loading")).apply {
                foreground = JBColor.GRAY
            }
            add(connectionStatusLabel)

            // 排查指南面板（默认隐藏，CardLayout 控制显示/隐藏）
            troubleshootingPanel = JPanel().apply {
                layout = CardLayout()
                background = TabThemeManager.getTabBackgroundColor()
                alignmentX = Component.LEFT_ALIGNMENT
                isVisible = false  // 默认隐藏

                // 空白卡片
                val emptyCard = JPanel()
                emptyCard.background = TabThemeManager.getTabBackgroundColor()

                // 排查指南卡片
                troubleshootingLabel = JBLabel().apply {
                    foreground = JBColor.RED
                }

                add(emptyCard, "empty")
                add(troubleshootingLabel, "guide")
            }
            add(troubleshootingPanel)
        }

        add(contentPanel, BorderLayout.CENTER)
    }
}
```

**Step 3: 添加辅助方法 formatProxyConfig**

在 `createSpacer` 方法后添加：

```kotlin
/**
 * 格式化代理配置为显示字符串
 */
private fun formatProxyConfig(proxyConfig: com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig?): String {
    if (proxyConfig == null) {
        return NekoamaBundle.message("dashboard.network.proxy.direct")
    }

    val type = when (proxyConfig.type) {
        com.cw2.nekoama.infrastructure.network.proxy.ProxyType.HTTP -> "HTTP"
        com.cw2.nekoama.infrastructure.network.proxy.ProxyType.SOCKS -> "SOCKS"
        com.cw2.nekoama.infrastructure.network.proxy.ProxyType.DIRECT -> "Direct"
    }

    val host = proxyConfig.host ?: "unknown"
    val port = proxyConfig.port ?: 0

    return if (proxyConfig.type == com.cw2.nekoama.infrastructure.network.proxy.ProxyType.DIRECT) {
        NekoamaBundle.message("dashboard.network.proxy.direct")
    } else {
        NekoamaBundle.message("dashboard.network.proxy", "$type $host:$port")
    }
}
```

**Step 4: 添加辅助方法 updateTroubleshootingGuide**

在 `formatProxyConfig` 方法后添加：

```kotlin
/**
 * 更新排查指南面板
 */
private fun updateTroubleshootingGuide(guide: List<String>?) {
    if (guide.isNullOrEmpty()) {
        troubleshootingPanel.isVisible = false
        return
    }

    troubleshootingPanel.isVisible = true
    val cardLayout = troubleshootingPanel.layout as CardLayout
    cardLayout.show(troubleshootingPanel, "guide")

    val html = buildString {
        append("<html><div style='padding: 8px;'>")
        append("<b>${NekoamaBundle.message("dashboard.network.troubleshooting.title")}</b><br>")
        guide.forEach { step ->
            append(step).append("<br>")
        }
        append("</div></html>")
    }

    troubleshootingLabel.text = html
}
```

**Step 5: 修改 refreshNetworkStatus 方法更新所有行**

替换 `refreshNetworkStatus` 方法的内容（约第 383-433 行）：

```kotlin
private suspend fun refreshNetworkStatus() {
    val service = networkTestService
    if (service == null) {
        NekoamaLogger.error("DashboardTab", "NetworkTestService is not available")
        ApplicationManager.getApplication().invokeLater {
            connectionStatusLabel.text = NekoamaBundle.message("dashboard.status.disconnected")
            connectionStatusLabel.foreground = JBColor.RED
        }
        return
    }

    try {
        val status = kotlinx.coroutines.withTimeout(15_000) {
            service.testConnectivity(null)
        }
        ApplicationManager.getApplication().invokeLater {
            // 1. 更新代理配置行
            proxyLabel.text = formatProxyConfig(status.proxyConfig)
            proxyLabel.foreground = TabThemeManager.getTabTextColor()

            // 2. 更新端点行
            endpointLabel.text = NekoamaBundle.message("dashboard.network.endpoint", status.endpoint)
            endpointLabel.foreground = TabThemeManager.getTabTextColor()

            // 3. 更新模型行
            modelLabel.text = NekoamaBundle.message("dashboard.network.model", status.model)
            modelLabel.foreground = TabThemeManager.getTabTextColor()

            // 4. 更新连接状态行
            if (status.isConnected) {
                val timeStr = if (status.responseTime > 0) {
                    " (${status.responseTime}ms)"
                } else {
                    ""
                }
                connectionStatusLabel.text = NekoamaBundle.message("dashboard.network.status.prefix") +
                    " " + NekoamaBundle.message("dashboard.status.connected", timeStr)
                connectionStatusLabel.foreground = JBColor.GREEN
            } else {
                connectionStatusLabel.text = NekoamaBundle.message("dashboard.network.status.prefix") +
                    " " + NekoamaBundle.message("dashboard.status.disconnected")
                connectionStatusLabel.foreground = JBColor.RED
            }

            // 5. 更新排查指南
            updateTroubleshootingGuide(status.troubleshootingGuide)
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        NekoamaLogger.error("DashboardTab", "Network test timeout after 15 seconds")
        ApplicationManager.getApplication().invokeLater {
            connectionStatusLabel.text = NekoamaBundle.message("dashboard.network.status.prefix") +
                " " + NekoamaBundle.message("dashboard.error.timeout")
            connectionStatusLabel.foreground = JBColor.RED
            troubleshootingPanel.isVisible = false
        }
    } catch (e: Exception) {
        NekoamaLogger.error("DashboardTab", "Network status refresh failed",
            mapOf(
                "error_class" to e.javaClass.simpleName,
                "error_message" to (e.message ?: "null")
            )
        )
        ApplicationManager.getApplication().invokeLater {
            connectionStatusLabel.text = NekoamaBundle.message("dashboard.network.status.prefix") +
                " Error: ${e.javaClass.simpleName}"
            connectionStatusLabel.foreground = JBColor.RED
            troubleshootingPanel.isVisible = false
        }
    }
}
```

**Step 6: 编译验证**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESS

**Step 7: 提交**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "feat(ui): refactor network status panel with detailed information display"
```

---

## Task 5: 同步更新 testConnection 方法

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**Step 1: 修改 testConnection 方法中的 onSuccess 回调**

在 `testConnection` 方法（约第 224-282 行）中，替换 `onSuccess` 回调内容：

```kotlin
override fun onSuccess() {
    if (testResult != null) {
        val status = testResult!!

        // 使用与 refreshNetworkStatus 相同的更新逻辑
        proxyLabel.text = formatProxyConfig(status.proxyConfig)
        proxyLabel.foreground = TabThemeManager.getTabTextColor()

        endpointLabel.text = NekoamaBundle.message("dashboard.network.endpoint", status.endpoint)
        endpointLabel.foreground = TabThemeManager.getTabTextColor()

        modelLabel.text = NekoamaBundle.message("dashboard.network.model", status.model)
        modelLabel.foreground = TabThemeManager.getTabTextColor()

        if (status.isConnected) {
            val timeStr = if (status.responseTime > 0) {
                " (${status.responseTime}ms)"
            } else {
                ""
            }
            connectionStatusLabel.text = NekoamaBundle.message("dashboard.network.status.prefix") +
                " " + NekoamaBundle.message("dashboard.status.connected", timeStr)
            connectionStatusLabel.foreground = JBColor.GREEN
        } else {
            connectionStatusLabel.text = NekoamaBundle.message("dashboard.network.status.prefix") +
                " " + status.message
            connectionStatusLabel.foreground = JBColor.RED
        }

        updateTroubleshootingGuide(status.troubleshootingGuide)
    }
}
```

**Step 2: 编译验证**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESS

**Step 3: 提交**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "feat(ui): update testConnection method to use new network panel layout"
```

---

## Task 6: 手动测试验证

**Step 1: 构建插件**

Run: `./gradlew buildPlugin`

**Step 2: 在测试 IDE 中运行**

Run: `./gradlew runIde`

**Step 3: 验证清单**

在 Dashboard Tab 中验证以下内容：

| 验证项 | 预期结果 |
|--------|----------|
| Proxy 配置显示 | 显示代理类型和地址，或 "Direct (no proxy)" |
| Endpoint 显示 | 显示 API 端点 URL |
| Model 显示 | 显示模型名称（如 gpt-4o-mini） |
| 连接成功状态 | 显示绿色 "Status: Connected (XXms)" |
| 连接失败状态 | 显示红色 "Status: Disconnected" |
| 排查指南显示 | 连接失败时显示排查指南步骤 |
| 主题适配 | 深色/浅色主题下文本颜色正确 |

**Step 4: 提交**

如果测试通过，创建最终提交：

```bash
git add -A
git commit -m "test: manual verification of network status panel enhancement"
```

---

## Definition of Done

- [ ] ConnectivityStatus 包含 endpoint 和 model 字段
- [ ] NetworkTestServiceImpl 从 NekoamaSettings 读取并传递配置
- [ ] DashboardTab 显示完整的网络状态信息
- [ ] 所有文案使用 NekoamaBundle.message()
- [ ] 手动测试验证通过
- [ ] 代码符合 DDD 分层规则
- [ ] 代码符合 EDT 线程规则

---

## 技术参考

- DDD 分层规则: `agent_docs/tech_guidance/ddd-packaging-rules.md`
- EDT 线程规则: `agent_docs/tech_guidance/edt-threading-rules.md`
- Swing UI 规则: `agent_docs/tech_guidance/intellij-swing-ui-rules.md`
- i18n 国际化规则: `agent_docs/tech_guidance/i18n-internationalization-rules.md`
