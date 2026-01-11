# Dashboard Tab Code Review Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复 Dashboard Tab 代码审查中发现的 11 个问题，确保代码符合 DDD 分层架构、i18n 国际化、主题适配等技术规范。

**Architecture:** 优先修复 P0 和 P1 严重问题（DDD 分层违规、i18n 违规、死代码），然后处理 P2 问题（协程生命周期、图标、测试）。

**Tech Stack:** Kotlin 2.1, IntelliJ Platform SDK, JUnit 5, MockK

---

## 问题清单汇总

| Priority | Issue | File | Lines |
|----------|-------|------|-------|
| P0 | DDD 分层违规 - Domain 依赖 Infrastructure | StatisticsServiceImpl.kt | 8 |
| P1 | 死代码 - 未使用的常量 | PropertiesStatisticsRepository.kt | 56-62 |
| P1 | i18n 违规 - 按钮硬编码 | DashboardTab.kt | 158-182 |
| P1 | i18n 违规 - URL 硬编码 | DashboardTab.kt | 211 |
| P1 | i18n 违规 - 错误消息硬编码 | DashboardTab.kt | 312, 314, 357, 390, 399, 426 |
| P1 | 主题适配违规 - 硬编码颜色 | DashboardTab.kt | 374, 376-386 |
| P2 | 协程生命周期未管理 | DashboardTab.kt | 216-218, 299-317 |
| P2 | 图标路径与计划不符 | DashboardTab.kt | 73 |
| P2 | 服务注入模式不一致 | GenerateNamingAction.kt | 108-115 |
| P2 | 测试覆盖率不足 | StatisticsServiceImplTest.kt | 全文 |

---

## Task 1: 修复 P0 - DDD 分层违规

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsServiceImpl.kt:8`

**Step 1: 删除错误的 import 语句**

编辑 `StatisticsServiceImpl.kt`，删除第 8 行：

```kotlin
// 删除此行
import com.cw2.nekoama.infrastructure.statistics.PropertiesStatisticsRepository
```

**Step 2: 验证代码仍可编译**

```bash
./gradlew compileKotlin
```

Expected: 编译成功，无错误

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsServiceImpl.kt
git commit -m "🐛 修复 DDD 分层违规 - 删除 Domain 层对 Infrastructure 的错误依赖"
```

---

## Task 2: 修复 P1 - 清理死代码

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/infrastructure/statistics/PropertiesStatisticsRepository.kt:56-62`

**Step 1: 删除未使用的常量**

编辑 `PropertiesStatisticsRepository.kt`，删除 companion object 中的所有常量：

```kotlin
// 删除整个 companion object 块（第 56-62 行）
companion object {
    private const val KEY_NAMING_COUNT = "nekoama.stats.usage.naming"
    private const val KEY_COMMENT_COUNT = "nekoama.stats.usage.comment"
    private const val KEY_CUSTOM_COUNT = "nekoama.stats.usage.custom"
    private const val KEY_USAGE_LAST_UPDATED = "nekoama.stats.usage.lastUpdated"
    private const val KEY_TOKEN_HISTORY = "nekoama.stats.token.history"
    private const val KEY_TOTAL_TOKENS = "nekoama.stats.token.total"
}
```

**Step 2: 运行测试确认无影响**

```bash
./gradlew test --tests PropertiesStatisticsRepositoryTest
```

Expected: 所有测试通过

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/infrastructure/statistics/PropertiesStatisticsRepository.kt
git commit -m "🧹 清理死代码 - 删除未使用的 PropertiesComponent key 常量"
```

---

## Task 3: 修复 P1 - 添加 i18n 资源 Key

**Files:**
- Modify: `src/main/resources/messages/NekoamaBundle.properties`

**Step 1: 添加缺失的 i18n key**

在 `NekoamaBundle.properties` 末尾添加以下 key：

```properties
# =============================================================================
# DASHBOARD QUICK ACTION BUTTONS
# =============================================================================

dashboard.button.settings=Settings
dashboard.button.settings.tooltip=Open Nekoama Settings
dashboard.button.guide=Guide
dashboard.button.guide.tooltip=Open User Guide
dashboard.button.test.connection=Test Connection
dashboard.button.test.connection.tooltip=Test API Connection
dashboard.guide.url=https://github.com/nekomorph-woo/cw_1Nekoama/blob/master/README.md

# =============================================================================
# DASHBOARD ERROR MESSAGES
# =============================================================================

dashboard.error.service.unavailable=Service not available
dashboard.error.with.detail=Error: {0}
```

**Step 2: 验证资源文件语法**

```bash
./gradlew processResources
```

Expected: 无错误

**Step 3: Commit**

```bash
git add src/main/resources/messages/NekoamaBundle.properties
git commit -m "✨ 添加 Dashboard i18n 资源 - 按钮文案和错误消息"
```

---

## Task 4: 修复 P1 - 替换硬编码按钮文本

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt:158-182`

**Step 1: 替换快捷操作按钮中的硬编码字符串**

将 `createQuickActionsPanel()` 方法中的按钮创建代码修改为：

```kotlin
// 设置按钮
val settingsButton = createQuickActionButton(
    NekoamaBundle.message("dashboard.button.settings"),
    AllIcons.General.Settings,
    NekoamaBundle.message("dashboard.button.settings.tooltip")
) {
    openSettings()
}

// 使用指南按钮
val guideButton = createQuickActionButton(
    NekoamaBundle.message("dashboard.button.guide"),
    AllIcons.Actions.Help,
    NekoamaBundle.message("dashboard.button.guide.tooltip")
) {
    openUserGuide()
}

// 测试连接按钮
val testConnectionButton = createQuickActionButton(
    NekoamaBundle.message("dashboard.button.test.connection"),
    AllIcons.General.Web,
    NekoamaBundle.message("dashboard.button.test.connection.tooltip")
) {
    testConnection()
}
```

**Step 2: 运行 UI 测试（如有）或手动验证**

```bash
./gradlew runIde
```

Expected: Dashboard Tab 快捷操作按钮显示正确的国际化文本

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "✨ Dashboard i18n - 快捷操作按钮使用 Bundle"
```

---

## Task 5: 修复 P1 - URL 使用 i18n

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt:209-212`

**Step 1: 修改 openUserGuide() 方法**

将硬编码的 URL 替换为 i18n：

```kotlin
private fun openUserGuide() {
    val url = NekoamaBundle.message("dashboard.guide.url")
    BrowserUtil.browse(url)
}
```

**Step 2: 验证功能**

在测试 IDE 中点击 Guide 按钮，确认能打开正确 URL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "✨ Dashboard i18n - URL 使用 Bundle 配置"
```

---

## Task 6: 修复 P1 - 错误消息使用 i18n

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**Step 1: 替换硬编码错误消息**

修改以下位置的硬编码错误消息：

```kotlin
// 第 312-314 行 (refreshData 方法)
SwingUtilities.invokeLater {
    networkStatusLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
    tokenStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
    usageStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
}

// 第 357-358 行 (refreshTokenStats 方法)
SwingUtilities.invokeLater {
    tokenStatsLabel.text = NekoamaBundle.message("dashboard.error.service.unavailable")
}

// 第 389-391 行 (refreshTokenStats 方法)
SwingUtilities.invokeLater {
    tokenStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
}

// 第 399-400 行 (refreshUsageStats 方法)
SwingUtilities.invokeLater {
    usageStatsLabel.text = NekoamaBundle.message("dashboard.error.service.unavailable")
}

// 第 425-427 行 (refreshUsageStats 方法)
SwingUtilities.invokeLater {
    usageStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
}
```

**Step 2: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "✨ Dashboard i18n - 错误消息使用 Bundle"
```

---

## Task 7: 修复 P1 - 主题适配（颜色值）

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt:371-386`

**Step 1: 使用 JBColor 替换硬编码颜色**

修改 `refreshTokenStats()` 方法中的颜色计算：

```kotlin
val growth = stats.getMonthOverMonthGrowth()
val growthStr = if (growth != null && growth >= 0) "+%.1f%%".format(growth)
                 else if (growth != null) "%.1f%%".format(growth)
                 else "N/A"

// 使用 JBColor 替代硬编码颜色
val growthColor = if (growth != null && growth >= 0) {
    JBColor(0x00AA00, 0x50C878)  // Light: 深绿, Dark: 亮绿
} else {
    JBColor(0xCC0000, 0xFF6B6B)  // Light: 深红, Dark: 亮红
}

tokenStatsLabel.text = """
    <html>
    <div style='padding: 8px;'>
        <div><b>${NekoamaBundle.message("dashboard.tokens.total")}</b> $totalFormatted</div>
        <div style='margin-top: 4px;'><b>${NekoamaBundle.message("dashboard.tokens.current")}</b> $currentFormatted</div>
        <div style='margin-top: 4px; color: ${String.format("#%06X", 0xFFFFFF and growthColor.rgb)};'>
            <b>${NekoamaBundle.message("dashboard.tokens.growth")}</b> $growthStr
        </div>
    </div>
    </html>
""".trimIndent()
```

**Step 2: 验证主题适配**

在 Light 和 Darcula 主题下测试显示效果

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "🎨 主题适配 - 使用 JBColor 替代硬编码颜色值"
```

---

## Task 8: 修复 P2 - 添加协程生命周期管理

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**Step 1: 添加生命周期管理的 CoroutineScope**

在 `DashboardTab` 类中添加 scope 管理：

```kotlin
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class DashboardTab(
    project: IjProject,
    coordinatorService: TabCoordinatorService
) : BaseTab(project, coordinatorService) {

    // 添加生命周期感知的 scope
    private val tabScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ... 其他代码保持不变 ...

    override fun onDestroy() {
        // 取消所有协程
        tabScope.cancel()
        super.onDestroy()
    }

    private fun testConnection() {
        // 使用 tabScope 替代创建新 scope
        tabScope.launch {
            refreshNetworkStatus()
        }
    }

    private fun refreshData() {
        NekoamaLogger.info("DashboardTab", "Refreshing data...")

        // 使用 tabScope
        tabScope.launch {
            try {
                refreshNetworkStatus()
                refreshTokenStats()
                refreshUsageStats()
            } catch (e: Exception) {
                NekoamaLogger.error("DashboardTab", "Failed to refresh data", mapOf("error" to (e.message ?: "unknown")))
                SwingUtilities.invokeLater {
                    networkStatusLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
                    tokenStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
                    usageStatsLabel.text = NekoamaBundle.message("dashboard.error.with.detail", e.message ?: "")
                }
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "🔧 协程生命周期管理 - 添加 tabScope 并在 onDestroy 时取消"
```

---

## Task 9: 修复 P2 - 修正图标路径

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt:73`

**Step 1: 替换为计划中指定的图标**

修改 metadata 中的 icon：

```kotlin
override val metadata = TabMetadata(
    id = TabMetadata.TabId("dashboard"),
    displayName = NekoamaBundle.message("dashboard.tab.title"),
    icon = AllIcons.General.ToolWindowDashboard  // 使用内置图标
)
```

**Step 2: 删除未使用的 import**

如果 IconLoader 不再被使用，删除其 import：

```kotlin
// 删除此行（如果无其他地方使用）
import com.intellij.openapi.util.IconLoader
```

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "🎨 修正图标 - 使用 AllIcons.General.ToolWindowDashboard"
```

---

## Task 10: 修复 P2 - 改进测试覆盖率

**Files:**
- Modify: `src/test/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsServiceImplTest.kt`

**Step 1: 添加真实的 repository mock 验证**

重写测试以验证实际的 repository 交互：

```kotlin
package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ActionType
import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import com.cw2.nekoama.domain.statistics.repository.StatisticsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("统计服务测试")
class StatisticsServiceImplTest {

    private lateinit var mockRepository: StatisticsRepository
    private lateinit var service: StatisticsService

    @BeforeEach
    fun setup() {
        mockRepository = mockk()
        service = StatisticsServiceImpl(mockRepository)
    }

    @Test
    @DisplayName("记录使用 - 应调用 repository 保存")
    fun `记录使用 - 应调用 repository 保存`() = runTest {
        // Given
        val initialStats = UsageStatistics()
        val updatedStats = initialStats.increment(ActionType.NAMING)
        every { mockRepository.loadUsageStatistics() } returns initialStats
        every { mockRepository.saveUsageStatistics(any()) } returns Unit

        // When
        service.recordUsage(ActionType.NAMING)

        // Then
        verify { mockRepository.loadUsageStatistics() }
        verify { mockRepository.saveUsageStatistics(updatedStats) }
    }

    @Test
    @DisplayName("获取使用统计 - 应返回 repository 数据")
    fun `获取使用统计 - 应返回 repository 数据`() {
        // Given
        val expectedStats = UsageStatistics(namingCount = 5, commentCount = 3)
        every { mockRepository.loadUsageStatistics() } returns expectedStats

        // When
        val result = service.getUsageStatistics()

        // Then
        assertEquals(expectedStats, result)
        verify { mockRepository.loadUsageStatistics() }
    }
}
```

**注意:** 此测试需要修改 `StatisticsServiceImpl` 构造函数以接受 `StatisticsRepository` 而非 `Project`，或者调整测试策略。

**Step 2: 运行测试**

```bash
./gradlew test --tests StatisticsServiceImplTest
```

**Step 3: Commit**

```bash
git add src/test/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsServiceImplTest.kt
git commit -m "🧪 改进测试 - 添加真实的 repository mock 验证"
```

---

## Task 11: 可选 - 统一服务注入模式

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/actions/GenerateNamingAction.kt:108-115`

**Step 1: 评估是否需要修改**

如果 `StatisticsService` 已通过 `@Service` 正确注册，可以简化代码：

```kotlin
// 直接访问服务（移除 nullable）
val service = project.service<StatisticsService>()
service.recordUsage(ActionType.NAMING)
```

如果服务注册可能失败，保持当前的防御性编程模式。

**Step 2: 根据评估结果决定是否修改**

此修改依赖于服务注册的稳定性，建议在确认服务正确注册后再执行。

---

## Definition of Done (交付标准)

1. **功能完整性:**
   - [ ] 所有 P0 问题已修复
   - [ ] 所有 P1 问题已修复
   - [ ] P2 问题已评估并处理

2. **代码质量:**
   - [ ] 所有测试通过: `./gradlew test`
   - [ ] 编译无错误: `./gradlew build`
   - [ ] 无新的 lint 错误

3. **技术规范合规:**
   - [ ] DDD 分层架构正确（Domain 不依赖 Infrastructure）
   - [ ] i18n 规范遵守（无硬编码 UI 字符串）
   - [ ] 主题适配正确（使用 JBColor）
   - [ ] 协程生命周期已管理

4. **文档:**
   - [ ] `agent_docs/memories/active_context.md` 已更新（如需要）
   - [ ] 本修复计划已完成

---

## 优先级说明

**立即执行 (本次 PR):**
- Task 1: P0 DDD 分层违规
- Task 2-7: P1 问题（死代码、i18n、主题适配）

**后续执行 (下一个 PR):**
- Task 8: P2 协程生命周期管理
- Task 9-10: P2 图标和测试

**可选优化:**
- Task 11: 服务注入模式统一

---

**修复计划生成时间:** 2025-01-11
**基于文档:** `agent_docs/code_review/code_review.md`
