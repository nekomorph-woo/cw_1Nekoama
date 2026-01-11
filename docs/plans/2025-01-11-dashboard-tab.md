# Dashboard Tab Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace WelcomeTab with DashboardTab providing shortcuts, network connectivity status, token usage statistics, and feature usage statistics with persistent storage.

**Architecture:** DDD layered architecture with Domain (models/services), Infrastructure (persistence/interceptors), and Interfaces (UI). TDD for core logic, VDD for UI. Data persisted via PropertiesComponent, token tracking via OkHttp interceptor.

**Tech Stack:** Kotlin 2.1 (JVM 21), IntelliJ Platform SDK 2025.1+, kotlinx.serialization, Swing UI, JUnit 5 + MockK

---

## Phase 1: Domain Models (Data Layer)

### Task 1.1: Create ActionType Enum

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/statistics/model/ActionType.kt`

**Step 1: Create the enum file**

```kotlin
package com.cw2.nekoama.domain.statistics.model

/**
 * 功能类型枚举
 */
enum class ActionType {
    /** 命名建议 */
    NAMING,

    /** 注释生成 */
    COMMENT,

    /** 自定义生成 */
    CUSTOM_GENERATE
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/model/ActionType.kt
git commit -m "feat(stats): add ActionType enum"
```

---

### Task 1.2: Create UsageStatistics Data Class

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/statistics/model/UsageStatistics.kt`

**Step 1: Create the UsageStatistics data class**

```kotlin
package com.cw2.nekoama.domain.statistics.model

/**
 * 功能使用统计数据
 *
 * @property namingCount 命名建议使用次数
 * @property commentCount 注释生成使用次数
 * @property customGenerateCount 自定义生成使用次数
 * @property lastUpdated 最后更新时间戳
 */
data class UsageStatistics(
    val namingCount: Int = 0,
    val commentCount: Int = 0,
    val customGenerateCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * 获取总使用次数
     */
    val totalCount: Int
        get() = namingCount + commentCount + customGenerateCount

    /**
     * 获取指定功能的使用次数
     */
    fun getCount(actionType: ActionType): Int = when (actionType) {
        ActionType.NAMING -> namingCount
        ActionType.COMMENT -> commentCount
        ActionType.CUSTOM_GENERATE -> customGenerateCount
    }

    /**
     * 获取指定功能占总数的百分比
     *
     * Edge Case: 当总次数为 0 时返回 0%（避免除零）
     */
    fun getPercentage(actionType: ActionType): Float {
        val total = totalCount
        return if (total > 0) {
            getCount(actionType).toFloat() / total * 100
        } else {
            0f
        }
    }

    /**
     * 增加指定功能的使用次数
     */
    fun increment(actionType: ActionType): UsageStatistics = when (actionType) {
        ActionType.NAMING -> copy(namingCount = namingCount + 1)
        ActionType.COMMENT -> copy(commentCount = commentCount + 1)
        ActionType.CUSTOM_GENERATE -> copy(customGenerateCount = customGenerateCount + 1)
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/model/UsageStatistics.kt
git commit -m "feat(stats): add UsageStatistics data class"
```

---

### Task 1.3: Create MonthlyTokenData Data Class

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/statistics/model/MonthlyTokenData.kt`

**Step 1: Create the MonthlyTokenData data class**

```kotlin
package com.cw2.nekoama.domain.statistics.model

import kotlinx.serialization.Serializable

/**
 * 月度 Token 数据
 *
 * @property yearMonth 年月标识（格式：YYYY-MM，如 "2025-01"）
 * @property totalTokens 总 Token 数
 * @property promptTokens 输入 Token 数
 * @property completionTokens 输出 Token 数
 */
@Serializable
data class MonthlyTokenData(
    val yearMonth: String,
    val totalTokens: Int = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
) {
    companion object {
        /**
         * 获取当前年月标识
         */
        fun currentYearMonth(): String {
            return java.time.YearMonth.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")
            )
        }

        /**
         * 获取上月年月标识
         */
        fun lastYearMonth(): String {
            return java.time.YearMonth.now().minusMonths(1).format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")
            )
        }
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/model/MonthlyTokenData.kt
git commit -m "feat(stats): add MonthlyTokenData with serialization"
```

---

### Task 1.4: Create TokenStatistics Data Class

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/statistics/model/TokenStatistics.kt`

**Step 1: Create the TokenStatistics data class**

```kotlin
package com.cw2.nekoama.domain.statistics.model

/**
 * Token 统计数据
 *
 * @property totalTokens 累计总 Token 数（所有月份）
 * @property currentMonthData 当月数据
 * @property lastMonthData 上月数据
 * @property history 历史月度数据
 */
data class TokenStatistics(
    val totalTokens: Int = 0,
    val currentMonthData: MonthlyTokenData = MonthlyTokenData(MonthlyTokenData.currentYearMonth()),
    val lastMonthData: MonthlyTokenData? = null,
    val history: Map<String, MonthlyTokenData> = emptyMap()
) {
    companion object {
        /**
         * 默认基准 Token 数（当无历史数据时使用）
         */
        private const val DEFAULT_BASELINE_TOKENS = 1_000_000
    }

    /**
     * 获取环比增长率（相对于上月）
     *
     * Edge Case 处理：
     * - 上月无数据且当月有数据：使用 100 万作为基准计算
     * - 上月无数据且当月无数据：返回 null
     * - 上月有数据但为 0：返回 null（避免除零）
     */
    fun getMonthOverMonthGrowth(): Float? {
        val currentMonthTokens = currentMonthData.totalTokens
        val lastMonthTokens = lastMonthData?.totalTokens

        return when {
            // 上月有数据且大于 0：正常计算环比
            lastMonthTokens != null && lastMonthTokens > 0 -> {
                ((currentMonthTokens - lastMonthTokens).toFloat() / lastMonthTokens) * 100
            }
            // 上月无数据且当月有数据：使用 100 万作为基准
            lastMonthTokens == null && currentMonthTokens > 0 -> {
                ((currentMonthTokens - DEFAULT_BASELINE_TOKENS).toFloat() / DEFAULT_BASELINE_TOKENS) * 100
            }
            // 其他情况：返回 null
            else -> null
        }
    }

    /**
     * 格式化 Token 数量（>10w 显示为 M 单位）
     */
    fun formatTokenCount(count: Int): String {
        return when {
            count >= 100_000 -> {
                val millions = count.toFloat() / 1_000_000
                String.format("%.2fM", millions)
            }
            else -> count.toString()
        }
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/model/TokenStatistics.kt
git commit -m "feat(stats): add TokenStatistics with growth calculation"
```

---

### Task 1.5: Create ConnectivityStatus Data Class

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/statistics/model/ConnectivityStatus.kt`

**Step 1: Create the ConnectivityStatus data class**

```kotlin
package com.cw2.nekoama.domain.statistics.model

import com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig

/**
 * API 连通性状态
 *
 * @property isConnected 是否连通
 * @property responseTime 响应时间（毫秒）
 * @property message 状态消息
 * @property proxyConfig 代理配置信息
 * @property troubleshootingGuide 排查指南（仅失败时）
 */
data class ConnectivityStatus(
    val isConnected: Boolean,
    val responseTime: Long = -1,
    val message: String,
    val proxyConfig: ProxyConfig? = null,
    val troubleshootingGuide: List<String>? = null
) {
    /**
     * 获取状态描述
     */
    fun getStatusDescription(): String {
        return if (isConnected) {
            "Connected (${responseTime}ms)"
        } else {
            "Disconnected"
        }
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/model/ConnectivityStatus.kt
git commit -m "feat(stats): add ConnectivityStatus model"
```

---

## Phase 2: Domain Service Interfaces

### Task 2.1: Create StatisticsService Interface

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsService.kt`

**Step 1: Create the StatisticsService interface**

```kotlin
package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ActionType
import com.cw2.nekoama.domain.statistics.model.TokenStatistics
import com.cw2.nekoama.domain.statistics.model.UsageStatistics

/**
 * 统计服务接口
 */
interface StatisticsService {
    /**
     * 记录功能使用
     */
    suspend fun recordUsage(actionType: ActionType)

    /**
     * 记录 Token 使用
     */
    suspend fun recordTokenUsage(usage: TokenUsageData)

    /**
     * 获取功能使用统计
     */
    fun getUsageStatistics(): UsageStatistics

    /**
     * 获取 Token 统计
     */
    fun getTokenStatistics(): TokenStatistics
}

/**
 * Token 使用数据
 */
data class TokenUsageData(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsService.kt
git commit -m "feat(stats): add StatisticsService interface"
```

---

### Task 2.2: Create NetworkTestService Interface

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/statistics/service/NetworkTestService.kt`

**Step 1: Create the NetworkTestService interface**

```kotlin
package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ConnectivityStatus

/**
 * 网络测试服务接口
 */
interface NetworkTestService {
    /**
     * 测试 API 连通性
     *
     * @param endpoint API 端点，为空时使用设置中的配置
     * @return 连通性状态
     */
    suspend fun testConnectivity(endpoint: String? = null): ConnectivityStatus
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/service/NetworkTestService.kt
git commit -m "feat(stats): add NetworkTestService interface"
```

---

### Task 2.3: Create StatisticsRepository Interface

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/statistics/repository/StatisticsRepository.kt`

**Step 1: Create the StatisticsRepository interface**

```kotlin
package com.cw2.nekoama.domain.statistics.repository

import com.cw2.nekoama.domain.statistics.model.MonthlyTokenData
import com.cw2.nekoama.domain.statistics.model.UsageStatistics

/**
 * 统计数据持久化仓库接口
 */
interface StatisticsRepository {
    // ========== 使用次数统计 ==========

    /**
     * 保存使用统计数据
     */
    fun saveUsageStatistics(statistics: UsageStatistics)

    /**
     * 加载使用统计数据
     */
    fun loadUsageStatistics(): UsageStatistics

    // ========== Token 统计 ==========

    /**
     * 保存 Token 历史数据
     */
    fun saveTokenHistory(history: Map<String, MonthlyTokenData>)

    /**
     * 加载 Token 历史数据
     */
    fun loadTokenHistory(): Map<String, MonthlyTokenData>

    /**
     * 获取累计总 Token 数
     */
    fun getTotalTokens(): Int

    /**
     * 保存累计总 Token 数
     */
    fun saveTotalTokens(total: Int)
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/repository/StatisticsRepository.kt
git commit -m "feat(stats): add StatisticsRepository interface"
```

---

## Phase 3: Infrastructure Layer (TDD)

### Task 3.1: Write PropertiesStatisticsRepository Test - Usage Statistics

**Files:**
- Create: `src/test/kotlin/com/cw2/nekoama/infrastructure/statistics/PropertiesStatisticsRepositoryTest.kt`

**Step 1: Create test file with usage statistics tests**

```kotlin
package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.model.ActionType
import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import com.intellij.testFramework.TestDataLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("PropertiesStatisticsRepository - 使用统计测试")
class PropertiesStatisticsRepositoryUsageTest {

    private lateinit var repository: PropertiesStatisticsRepository
    private lateinit var mockProject: com.intellij.openapi.project.Project

    @BeforeEach
    fun setUp() {
        // Setup will be implemented after creating the class
    }

    @AfterEach
    fun tearDown() {
        // Cleanup will be implemented after creating the class
    }

    @Test
    @DisplayName("保存使用统计 - 应该正确保存所有计数器")
    fun `保存使用统计 - 应该正确保存所有计数器`() {
        // Given
        val stats = UsageStatistics(
            namingCount = 10,
            commentCount = 20,
            customGenerateCount = 30,
            lastUpdated = 123456789L
        )

        // When
        repository.saveUsageStatistics(stats)
        val loaded = repository.loadUsageStatistics()

        // Then
        assertEquals(10, loaded.namingCount)
        assertEquals(20, loaded.commentCount)
        assertEquals(30, loaded.customGenerateCount)
        assertEquals(123456789L, loaded.lastUpdated)
    }

    @Test
    @DisplayName("加载空统计 - 应该返回默认值")
    fun `加载空统计 - 应该返回默认值`() {
        // Given: No previous data

        // When
        val loaded = repository.loadUsageStatistics()

        // Then
        assertEquals(0, loaded.namingCount)
        assertEquals(0, loaded.commentCount)
        assertEquals(0, loaded.customGenerateCount)
    }

    @Test
    @DisplayName("增量保存 - 应该正确累加")
    fun `增量保存 - 应该正确累加`() {
        // Given
        val stats1 = UsageStatistics(namingCount = 5)
        repository.saveUsageStatistics(stats1)

        // When
        val stats2 = UsageStatistics(namingCount = 10)
        repository.saveUsageStatistics(stats2)
        val loaded = repository.loadUsageStatistics()

        // Then
        assertEquals(10, loaded.namingCount)
    }
}
```

**Step 2: Verify test compilation fails**

Run: `./gradlew compileTestKotlin`
Expected: FAIL with "PropertiesStatisticsRepository not found"

**Step 3: Commit**

```bash
git add src/test/kotlin/com/cw2/nekoama/infrastructure/statistics/PropertiesStatisticsRepositoryTest.kt
git commit -m "test(stats): add usage statistics tests"
```

---

### Task 3.2: Implement PropertiesStatisticsRepository - Usage Statistics

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/infrastructure/statistics/PropertiesStatisticsRepository.kt`
- Modify: `src/test/kotlin/com/cw2/nekoama/infrastructure/statistics/PropertiesStatisticsRepositoryTest.kt`

**Step 1: Create the repository implementation**

```kotlin
package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.model.MonthlyTokenData
import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import com.cw2.nekoama.domain.statistics.repository.StatisticsRepository
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * 基于 PropertiesComponent 的统计持久化实现
 *
 * 存储策略：
 * 1. 简单计数器（使用次数）→ 直接使用 PropertiesComponent 存储 int
 * 2. 复杂结构（Token 历史）→ JSON 序列化后存入 PropertiesComponent
 */
@Service(Service.Level.PROJECT)
class PropertiesStatisticsRepository(
    private val project: Project
) : StatisticsRepository {

    private val properties = com.intellij.openapi.components.PropertiesComponent.getInstance(project)

    // JSON 序列化配置
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = false
    }

    companion object {
        // 使用次数存储 Key
        private const val KEY_NAMING_COUNT = "nekoama.stats.usage.naming"
        private const val KEY_COMMENT_COUNT = "nekoama.stats.usage.comment"
        private const val KEY_CUSTOM_COUNT = "nekoama.stats.usage.custom"
        private const val KEY_USAGE_LAST_UPDATED = "nekoama.stats.usage.lastUpdated"

        // Token 统计存储 Key
        private const val KEY_TOKEN_HISTORY = "nekoama.stats.token.history"
        private const val KEY_TOTAL_TOKENS = "nekoama.stats.token.total"

        /**
         * 获取项目实例
         */
        fun getInstance(project: Project): PropertiesStatisticsRepository {
            return project.service()
        }
    }

    // ========== 使用次数统计（直接存储 int） ==========

    override fun saveUsageStatistics(statistics: UsageStatistics) {
        properties.setValue(KEY_NAMING_COUNT, statistics.namingCount, 0)
        properties.setValue(KEY_COMMENT_COUNT, statistics.commentCount, 0)
        properties.setValue(KEY_CUSTOM_COUNT, statistics.customGenerateCount, 0)
        properties.setValue(KEY_USAGE_LAST_UPDATED, statistics.lastUpdated, 0L)
    }

    override fun loadUsageStatistics(): UsageStatistics {
        return UsageStatistics(
            namingCount = properties.getInt(KEY_NAMING_COUNT, 0),
            commentCount = properties.getInt(KEY_COMMENT_COUNT, 0),
            customGenerateCount = properties.getInt(KEY_CUSTOM_COUNT, 0),
            lastUpdated = properties.getLong(KEY_USAGE_LAST_UPDATED, 0L)
        )
    }

    // ========== Token 统计（JSON 序列化） ==========

    override fun saveTokenHistory(history: Map<String, MonthlyTokenData>) {
        try {
            val jsonStr = json.encodeToString(history)
            properties.setValue(KEY_TOKEN_HISTORY, jsonStr)
        } catch (e: Exception) {
            NekoamaLogger.error("StatisticsRepository", "保存 Token 历史失败: ${e.message}")
        }
    }

    override fun loadTokenHistory(): Map<String, MonthlyTokenData> {
        val jsonStr = properties.getValue(KEY_TOKEN_HISTORY)
        return if (jsonStr.isNullOrBlank()) {
            emptyMap()
        } else {
            try {
                json.decodeFromString<Map<String, MonthlyTokenData>>(jsonStr)
            } catch (e: Exception) {
                NekoamaLogger.error("StatisticsRepository", "加载 Token 历史失败: ${e.message}")
                emptyMap()
            }
        }
    }

    override fun getTotalTokens(): Int {
        return properties.getInt(KEY_TOTAL_TOKENS, 0)
    }

    override fun saveTotalTokens(total: Int) {
        properties.setValue(KEY_TOTAL_TOKENS, total, 0)
    }
}
```

**Step 2: Update test setup**

```kotlin
package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import com.intellij.testFramework.TestDataLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@DisplayName("PropertiesStatisticsRepository - 使用统计测试")
class PropertiesStatisticsRepositoryUsageTest : BasePlatformTestCase() {

    private lateinit var repository: PropertiesStatisticsRepository

    override fun setUp() {
        super.setUp()
        repository = PropertiesStatisticsRepository(project)
    }

    override fun tearDown() {
        // Clear properties
        val properties = com.intellij.openapi.components.PropertiesComponent.getInstance(project)
        properties.unsetValue("nekoama.stats.usage.naming")
        properties.unsetValue("nekoama.stats.usage.comment")
        properties.unsetValue("nekoama.stats.usage.custom")
        properties.unsetValue("nekoama.stats.usage.lastUpdated")
        super.tearDown()
    }

    @Test
    @DisplayName("保存使用统计 - 应该正确保存所有计数器")
    fun `保存使用统计 - 应该正确保存所有计数器`() {
        // Given
        val stats = UsageStatistics(
            namingCount = 10,
            commentCount = 20,
            customGenerateCount = 30,
            lastUpdated = 123456789L
        )

        // When
        repository.saveUsageStatistics(stats)
        val loaded = repository.loadUsageStatistics()

        // Then
        assertEquals(10, loaded.namingCount)
        assertEquals(20, loaded.commentCount)
        assertEquals(30, loaded.customGenerateCount)
        assertEquals(123456789L, loaded.lastUpdated)
    }

    @Test
    @DisplayName("加载空统计 - 应该返回默认值")
    fun `加载空统计 - 应该返回默认值`() {
        // Given: No previous data

        // When
        val loaded = repository.loadUsageStatistics()

        // Then
        assertEquals(0, loaded.namingCount)
        assertEquals(0, loaded.commentCount)
        assertEquals(0, loaded.customGenerateCount)
    }

    @Test
    @DisplayName("增量保存 - 应该正确累加")
    fun `增量保存 - 应该正确累加`() {
        // Given
        val stats1 = UsageStatistics(namingCount = 5)
        repository.saveUsageStatistics(stats1)

        // When
        val stats2 = UsageStatistics(namingCount = 10)
        repository.saveUsageStatistics(stats2)
        val loaded = repository.loadUsageStatistics()

        // Then
        assertEquals(10, loaded.namingCount)
    }
}
```

**Step 3: Run tests to verify they pass**

Run: `./gradlew test --tests PropertiesStatisticsRepositoryUsageTest`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/infrastructure/statistics/PropertiesStatisticsRepository.kt
git add src/test/kotlin/com/cw2/nekoama/infrastructure/statistics/PropertiesStatisticsRepositoryTest.kt
git commit -m "feat(stats): implement PropertiesStatisticsRepository usage statistics"
```

---

### Task 3.3: Write TokenUsageInterceptor Test

**Files:**
- Create: `src/test/kotlin/com/cw2/nekoama/infrastructure/statistics/TokenUsageInterceptorTest.kt`

**Step 1: Create test file**

```kotlin
package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.service.StatisticsService
import com.cw2.nekoama.domain.statistics.service.TokenUsageData
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("TokenUsageInterceptor - Token 拦截测试")
class TokenUsageInterceptorTest {

    private lateinit var interceptor: TokenUsageInterceptor
    private lateinit var mockStatisticsService: StatisticsService
    private lateinit var mockChain: Interceptor.Chain

    @BeforeEach
    fun setUp() {
        mockStatisticsService = mockk()
        interceptor = TokenUsageInterceptor(mockStatisticsService)
        mockChain = mockk()

        // Setup GlobalScope test dispatcher
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        Dispatchers.setMain(Dispatchers.Default)
    }

    @Test
    @DisplayName("拦截成功响应 - 应该提取 usage 数据并记录")
    fun `拦截成功响应 - 应该提取 usage 数据并记录`() = runTest {
        // Given
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .build()

        val responseBody = """
        {
            "id": "chatcmpl-123",
            "object": "chat.completion",
            "usage": {
                "prompt_tokens": 100,
                "completion_tokens": 50,
                "total_tokens": 150
            }
        }
        """.trimIndent()

        val response = Response.Builder()
            .request(request)
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .body(ResponseBody.create("application/json".toMediaType(), responseBody))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response
        coEvery { mockStatisticsService.recordTokenUsage(any()) } just Runs

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertNotNull(result)
        assertEquals(200, result.code)

        // Verify async call was made
        coVerify(timeout = 1000) { mockStatisticsService.recordTokenUsage(TokenUsageData(100, 50, 150)) }
    }

    @Test
    @DisplayName("拦截无 usage 字段响应 - 应该静默忽略")
    fun `拦截无 usage 字段响应 - 应该静默忽略`() = runTest {
        // Given
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .build()

        val responseBody = """
        {
            "id": "chatcmpl-123",
            "object": "chat.completion"
        }
        """.trimIndent()

        val response = Response.Builder()
            .request(request)
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .body(ResponseBody.create("application/json".toMediaType(), responseBody))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertNotNull(result)
        coVerify(exactly = 0) { mockStatisticsService.recordTokenUsage(any()) }
    }

    @Test
    @DisplayName("拦截失败响应 - 应该直接返回不记录")
    fun `拦截失败响应 - 应该直接返回不记录`() = runTest {
        // Given
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .build()

        val response = Response.Builder()
            .request(request)
            .code(401)
            .protocol(Protocol.HTTP_1_1)
            .message("Unauthorized")
            .body(ResponseBody.create("application/json".toMediaType(), "{}"))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertEquals(401, result.code)
        coVerify(exactly = 0) { mockStatisticsService.recordTokenUsage(any()) }
    }

    @Test
    @DisplayName("JSON 解析失败 - 应该返回原始响应")
    fun `JSON 解析失败 - 应该返回原始响应`() = runTest {
        // Given
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .build()

        val response = Response.Builder()
            .request(request)
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .body(ResponseBody.create("application/json".toMediaType(), "invalid json"))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertNotNull(result)
        assertEquals(200, result.code)
    }
}
```

**Step 2: Verify test compilation fails**

Run: `./gradlew compileTestKotlin`
Expected: FAIL with "TokenUsageInterceptor not found"

**Step 3: Commit**

```bash
git add src/test/kotlin/com/cw2/nekoama/infrastructure/statistics/TokenUsageInterceptorTest.kt
git commit -m "test(stats): add TokenUsageInterceptor tests"
```

---

### Task 3.4: Implement TokenUsageInterceptor

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/infrastructure/statistics/TokenUsageInterceptor.kt`

**Step 1: Create the interceptor implementation**

```kotlin
package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.service.StatisticsService
import com.cw2.nekoama.shared.logging.NekoamaLogger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * Token 使用拦截器
 *
 * 职责：
 * - 拦截 OpenAI 兼容的 API 响应
 * - 提取 response.body.usage 字段
 * - 异步记录到统计服务
 * - 唯一的异常处理：不影响主流程（静默失败）
 *
 * 注意：
 * - 无需容错配置
 * - 无需可控开关
 * - 默认始终开启
 */
class TokenUsageInterceptor(
    private val statisticsService: StatisticsService
) : Interceptor {

    companion object {
        private const val LOG_TAG = "TokenUsageInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // 只处理成功的响应
        if (!response.isSuccessful) {
            return response
        }

        // 提取响应体
        val responseBody = response.body ?: return response

        return try {
            val rawJson = responseBody.string()

            // 提取 usage 数据
            val tokenUsage = extractTokenUsage(rawJson)

            if (tokenUsage != null) {
                // 异步记录，不阻塞请求，不影响主流程
                GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        statisticsService.recordTokenUsage(tokenUsage)
                    } catch (e: Exception) {
                        // 静默失败，仅记录日志
                        NekoamaLogger.debug(LOG_TAG, "记录 Token 使用失败: ${e.message}")
                    }
                }
            }

            // 重新构建响应（因为 string() 只能调用一次）
            response.newBuilder()
                .body(ResponseBody.create(responseBody.contentType(), rawJson))
                .build()

        } catch (e: Exception) {
            // 任何异常都不影响主流程，返回原始响应
            NekoamaLogger.debug(LOG_TAG, "拦截器处理失败，返回原始响应: ${e.message}")
            response
        }
    }

    /**
     * 从 OpenAI 兼容响应中提取 usage 数据
     *
     * OpenAI API 响应格式：
     * {
     *   "usage": {
     *     "prompt_tokens": 100,
     *     "completion_tokens": 50,
     *     "total_tokens": 150
     *   }
     * }
     */
    private fun extractTokenUsage(json: String): com.cw2.nekoama.domain.statistics.service.TokenUsageData? {
        return try {
            val jsonObject = org.json.JSONObject(json)
            val usageObject = jsonObject.optJSONObject("usage") ?: return null

            com.cw2.nekoama.domain.statistics.service.TokenUsageData(
                promptTokens = usageObject.optInt("prompt_tokens", 0),
                completionTokens = usageObject.optInt("completion_tokens", 0),
                totalTokens = usageObject.optInt("total_tokens", 0)
            )
        } catch (e: Exception) {
            // 静默失败，返回 null
            null
        }
    }
}
```

**Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests TokenUsageInterceptorTest`
Expected: PASS

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/infrastructure/statistics/TokenUsageInterceptor.kt
git commit -m "feat(stats): implement TokenUsageInterceptor"
```

---

## Phase 4: Domain Service Implementation

### Task 4.1: Implement StatisticsService

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsServiceImpl.kt`
- Create: `src/test/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsServiceImplTest.kt`

**Step 1: Write failing tests first**

```kotlin
package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ActionType
import com.cw2.nekoama.domain.statistics.model.MonthlyTokenData
import com.cw2.nekoama.domain.statistics.model.TokenStatistics
import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import com.cw2.nekoama.domain.statistics.repository.StatisticsRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("StatisticsServiceImpl - 统计服务测试")
class StatisticsServiceImplTest {

    private lateinit var service: StatisticsServiceImpl
    private lateinit var mockRepository: StatisticsRepository

    @BeforeEach
    fun setUp() {
        mockRepository = mockk()
        service = StatisticsServiceImpl(mockRepository)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    @DisplayName("记录功能使用 - 应该调用 repository 保存")
    fun `记录功能使用 - 应该调用 repository 保存`() = runTest {
        // Given
        val initialStats = UsageStatistics(namingCount = 5)
        every { mockRepository.loadUsageStatistics() } returns initialStats
        every { mockRepository.saveUsageStatistics(any()) } just Runs

        // When
        service.recordUsage(ActionType.NAMING)

        // Then
        verify { mockRepository.saveUsageStatistics(match { it.namingCount == 6 }) }
    }

    @Test
    @DisplayName("获取使用统计 - 应该返回 repository 数据")
    fun `获取使用统计 - 应该返回 repository 数据`() {
        // Given
        val stats = UsageStatistics(namingCount = 10, commentCount = 20)
        every { mockRepository.loadUsageStatistics() } returns stats

        // When
        val result = service.getUsageStatistics()

        // Then
        assertEquals(10, result.namingCount)
        assertEquals(20, result.commentCount)
    }

    @Test
    @DisplayName("记录 Token 使用 - 应该累加到当月数据")
    fun `记录 Token 使用 - 应该累加到当月数据`() = runTest {
        // Given
        val currentMonth = MonthlyTokenData.currentYearMonth()
        val existingHistory = mapOf(
            currentMonth to MonthlyTokenData(currentMonth, totalTokens = 1000)
        )
        every { mockRepository.loadTokenHistory() } returns existingHistory
        every { mockRepository.getTotalTokens() } returns 1000
        every { mockRepository.saveTokenHistory(any()) } just Runs
        every { mockRepository.saveTotalTokens(any()) } just Runs

        // When
        service.recordTokenUsage(TokenUsageData(100, 50, 150))

        // Then
        verify { mockRepository.saveTokenHistory(match {
            it[currentMonth]?.totalTokens == 1150
        }) }
        verify { mockRepository.saveTotalTokens(1150) }
    }

    @Test
    @DisplayName("获取 Token 统计 - 应该包含环比数据")
    fun `获取 Token 统计 - 应该包含环比数据`() {
        // Given
        val currentMonth = MonthlyTokenData.currentYearMonth()
        val lastMonth = MonthlyTokenData.lastYearMonth()
        val history = mapOf(
            currentMonth to MonthlyTokenData(currentMonth, totalTokens = 1500),
            lastMonth to MonthlyTokenData(lastMonth, totalTokens = 1000)
        )
        every { mockRepository.loadTokenHistory() } returns history
        every { mockRepository.getTotalTokens() } returns 2500

        // When
        val result = service.getTokenStatistics()

        // Then
        assertEquals(2500, result.totalTokens)
        assertEquals(1500, result.currentMonthData.totalTokens)
        assertEquals(1000, result.lastMonthData?.totalTokens)
        val growth = result.getMonthOverMonthGrowth()
        // (1500 - 1000) / 1000 * 100 = 50%
        assertEquals(50f, growth)
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests StatisticsServiceImplTest`
Expected: FAIL with "StatisticsServiceImpl not found"

**Step 3: Commit test**

```bash
git add src/test/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsServiceImplTest.kt
git commit -m "test(stats): add StatisticsServiceImpl tests"
```

**Step 4: Implement StatisticsServiceImpl**

```kotlin
package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ActionType
import com.cw2.nekoama.domain.statistics.model.MonthlyTokenData
import com.cw2.nekoama.domain.statistics.model.TokenStatistics
import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import com.cw2.nekoama.domain.statistics.repository.StatisticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统计服务实现
 */
class StatisticsServiceImpl(
    private val repository: StatisticsRepository
) : StatisticsService {

    private val currentMonth = MonthlyTokenData.currentYearMonth()

    // ========== 使用次数统计 ==========

    override suspend fun recordUsage(actionType: ActionType) {
        withContext(Dispatchers.IO) {
            val currentStats = repository.loadUsageStatistics()
            val updatedStats = currentStats.increment(actionType)
            repository.saveUsageStatistics(updatedStats)
        }
    }

    override fun getUsageStatistics(): UsageStatistics {
        return repository.loadUsageStatistics()
    }

    // ========== Token 统计 ==========

    override suspend fun recordTokenUsage(usage: TokenUsageData) {
        withContext(Dispatchers.IO) {
            // 1. 加载当前历史
            val history = repository.loadTokenHistory().toMutableMap()

            // 2. 获取或创建当月数据
            val currentMonthData = history.getOrPut(currentMonth) {
                MonthlyTokenData(yearMonth = currentMonth)
            }

            // 3. 累加当月数据
            val updatedMonthData = currentMonthData.copy(
                totalTokens = currentMonthData.totalTokens + usage.totalTokens,
                promptTokens = currentMonthData.promptTokens + usage.promptTokens,
                completionTokens = currentMonthData.completionTokens + usage.completionTokens
            )
            history[currentMonth] = updatedMonthData

            // 4. 更新总计
            val totalTokens = repository.getTotalTokens() + usage.totalTokens

            // 5. 保存
            repository.saveTokenHistory(history)
            repository.saveTotalTokens(totalTokens)
        }
    }

    override fun getTokenStatistics(): TokenStatistics {
        val history = repository.loadTokenHistory()
        val currentMonthData = history[currentMonth] ?: MonthlyTokenData(currentMonth)
        val lastMonthKey = MonthlyTokenData.lastYearMonth()
        val lastMonthData = history[lastMonthKey]

        return TokenStatistics(
            totalTokens = repository.getTotalTokens(),
            currentMonthData = currentMonthData,
            lastMonthData = lastMonthData,
            history = history
        )
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests StatisticsServiceImplTest`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsServiceImpl.kt
git commit -m "feat(stats): implement StatisticsServiceImpl"
```

---

### Task 4.2: Implement NetworkTestService

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/statistics/service/NetworkTestServiceImpl.kt`
- Create: `src/test/kotlin/com/cw2/nekoama/domain/statistics/service/NetworkTestServiceImplTest.kt`

**Step 1: Write failing tests first**

```kotlin
package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ConnectivityStatus
import com.cw2.nekoama.infrastructure.network.proxy.ProxyConnectionTester
import com.cw2.nekoama.infrastructure.network.proxy.ProxyDetector
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("NetworkTestServiceImpl - 网络测试服务测试")
class NetworkTestServiceImplTest {

    private lateinit var service: NetworkTestServiceImpl
    private lateinit var mockProject: com.intellij.openapi.project.Project
    private lateinit var mockProxyConfig: com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig

    @BeforeEach
    fun setUp() {
        mockProject = mockk(relaxed = true)
        mockProxyConfig = mockk(relaxed = true)
        mockkObject(ProxyDetector)
        mockkObject(ProxyConnectionTester)
        service = NetworkTestServiceImpl(mockProject)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("测试连通性 - 成功时应该返回 Connected 状态")
    fun `测试连通性 - 成功时应该返回 Connected 状态`() = runTest {
        // Given
        val testUrl = "https://api.openai.com"
        every { ProxyDetector.detectSystemProxy(testUrl) } returns mockProxyConfig
        every { ProxyConnectionTester.testProxyConnection(mockProxyConfig, testUrl) } returns
            ProxyConnectionTester.ProxyTestResult(
                success = true,
                responseTime = 100,
                message = "Connection successful"
            )

        // When
        val result = service.testConnectivity(testUrl)

        // Then
        assertTrue(result.isConnected)
        assertEquals(100, result.responseTime)
        assertEquals("Connection successful", result.message)
    }

    @Test
    @DisplayName("测试连通性 - 失败时应该生成排查指南")
    fun `测试连通性 - 失败时应该生成排查指南`() = runTest {
        // Given
        val testUrl = "https://api.openai.com"
        every { ProxyDetector.detectSystemProxy(testUrl) } returns mockProxyConfig
        every { ProxyConnectionTester.testProxyConnection(mockProxyConfig, testUrl) } returns
            ProxyConnectionTester.ProxyTestResult(
                success = false,
                responseTime = -1,
                message = "Connection refused",
                statusCode = null
            )

        // When
        val result = service.testConnectivity(testUrl)

        // Then
        assertFalse(result.isConnected)
        assertNotNull(result.troubleshootingGuide)
        assertTrue(result.troubleshootingGuide!!.isNotEmpty())
    }

    @Test
    @DisplayName("测试连通性 - 407 错误应该生成认证指南")
    fun `测试连通性 - 407 错误应该生成认证指南`() = runTest {
        // Given
        val testUrl = "https://api.openai.com"
        every { ProxyDetector.detectSystemProxy(testUrl) } returns mockProxyConfig
        every { ProxyConnectionTester.testProxyConnection(mockProxyConfig, testUrl) } returns
            ProxyConnectionTester.ProxyTestResult(
                success = false,
                responseTime = -1,
                message = "Proxy Authentication Required",
                statusCode = 407
            )

        // When
        val result = service.testConnectivity(testUrl)

        // Then
        assertFalse(result.isConnected)
        assertNotNull(result.troubleshootingGuide)
        assertTrue(result.troubleshootingGuide!!.any { it.contains("407") || it.contains("认证") })
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests NetworkTestServiceImplTest`
Expected: FAIL with "NetworkTestServiceImpl not found"

**Step 3: Commit test**

```bash
git add src/test/kotlin/com/cw2/nekoama/domain/statistics/service/NetworkTestServiceImplTest.kt
git commit -m "test(stats): add NetworkTestServiceImpl tests"
```

**Step 4: Implement NetworkTestServiceImpl**

```kotlin
package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ConnectivityStatus
import com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig
import com.cw2.nekoama.infrastructure.network.proxy.ProxyConnectionTester
import com.cw2.nekoama.infrastructure.network.proxy.ProxyDetector
import com.cw2.nekoama.domain.settings.NekoamaSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 网络测试服务实现
 */
class NetworkTestServiceImpl(
    private val project: com.intellij.openapi.project.Project
) : NetworkTestService {

    override suspend fun testConnectivity(endpoint: String?): ConnectivityStatus {
        return withContext(Dispatchers.IO) {
            val testUrl = endpoint ?: NekoamaSettings.getInstance().apiEndpoint
                .ifEmpty { "https://api.openai.com" }

            // 检测代理配置
            val proxyConfig = ProxyDetector.detectSystemProxy(testUrl)

            // 执行连接测试
            val testResult = ProxyConnectionTester.testProxyConnection(proxyConfig, testUrl)

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
                troubleshootingGuide = troubleshootingGuide
            )
        }
    }

    private fun generateTroubleshootingGuide(
        proxyConfig: ProxyConfig,
        testResult: ProxyConnectionTester.ProxyTestResult
    ): List<String> {
        val guide = mutableListOf<String>()

        // 基于错误类型生成指南
        when {
            testResult.statusCode == 407 -> {
                guide.add("代理认证失败（407）")
                guide.add("1. 检查代理用户名和密码是否正确")
                guide.add("2. 确认代理服务器支持认证")
            }
            testResult.message.contains("timeout", ignoreCase = true) -> {
                guide.add("连接超时")
                guide.add("1. 检查网络连接是否正常")
                guide.add("2. 确认代理服务器是否运行")
                guide.add("3. 尝试增加超时时间")
            }
            testResult.message.contains("Connection refused", ignoreCase = true) -> {
                guide.add("连接被拒绝")
                guide.add("1. 确认代理服务器是否启动")
                guide.add("2. 检查代理端口是否正确")
            }
            else -> {
                guide.add("连接失败")
                guide.add("1. 检查网络连接")
                guide.add("2. 验证代理配置")
                guide.add("3. 查看 IDE 日志获取详细信息")
            }
        }

        return guide
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests NetworkTestServiceImplTest`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/statistics/service/NetworkTestServiceImpl.kt
git commit -m "feat(stats): implement NetworkTestServiceImpl"
```

---

## Phase 5: Integration with HTTP Client

### Task 5.1: Register TokenUsageInterceptor in CustomAPIHttpClient

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/infrastructure/network/client/CustomAPIHttpClient.kt`

**Step 1: Read the existing file**

Check the file to understand current structure and where to add the interceptor.

**Step 2: Add the interceptor to the OkHttp client**

```kotlin
// In the OkHttpClient.Builder configuration, add:
.addInterceptor(TokenUsageInterceptor(statisticsService))
```

**Step 3: Update constructor to inject StatisticsService**

```kotlin
class CustomAPIHttpClient(
    private val project: Project,
    private val statisticsService: StatisticsService = // Get from service
) {
    // ...
}
```

**Step 4: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/infrastructure/network/client/CustomAPIHttpClient.kt
git commit -m "feat(stats): add TokenUsageInterceptor to HTTP client"
```

---

## Phase 6: UI Layer - Dashboard Tab (VDD)

### Task 6.1: Create Base Infrastructure for Dashboard Tab

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**Step 1: Create basic DashboardTab structure**

```kotlin
package com.cw2.nekoama.interfaces.intellij.toolwindow.tabs

import com.cw2.nekoama.domain.statistics.service.NetworkTestService
import com.cw2.nekoama.domain.statistics.service.StatisticsService
import com.cw2.nekoama.domain.toolwindow.service.TabCoordinatorService
import com.cw2.nekoama.domain.toolwindow.model.TabMetadata
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.NekoamaIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

/**
 * 仪表盘 Tab
 *
 * 替换原有的 WelcomeTab，提供：
 * - 快捷功能按钮（设置、指南、检测连接）
 * - 网络状态显示
 * - Token 统计显示
 * - 功能使用统计显示
 */
class DashboardTab(
    project: Project,
    coordinatorService: TabCoordinatorService
) : BaseTab(project, coordinatorService) {

    override val metadata = TabMetadata(
        id = TabMetadata.TabId("dashboard"),
        displayName = NekoamaBundle.message("dashboard.tab.title"),
        icon = NekoamaIcons.DASHBOARD // Need to add this icon
    )

    override val stateType = DashboardTabState::class

    // UI Components
    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val toolbarPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT))
    private val settingsButton = JButton()
    private val guideButton = JButton()
    private val testConnectionButton = JButton()

    // Services (will be injected)
    private val statisticsService: StatisticsService
    private val networkTestService: NetworkTestService

    init {
        services = // Get from project service
        statisticsService = // Get from service
        networkTestService = // Get from service

        initComponents()
        layoutComponents()
    }

    private fun initComponents() {
        // Toolbar buttons
        settingsButton.text = NekoamaBundle.message("dashboard.button.settings")
        settingsButton.addActionListener { openSettings() }

        guideButton.text = NekoamaBundle.message("dashboard.button.guide")
        guideButton.addActionListener { showGuide() }

        testConnectionButton.text = NekoamaBundle.message("dashboard.button.testConnection")
        testConnectionButton.addActionListener { testConnection() }
    }

    private fun layoutComponents() {
        // Toolbar
        toolbarPanel.add(settingsButton)
        toolbarPanel.add(guideButton)
        toolbarPanel.add(testConnectionButton)

        mainPanel.add(toolbarPanel, BorderLayout.NORTH)

        // Placeholder for content
        val placeholder = JBLabel("Dashboard Content - Under Construction")
        placeholder.horizontalAlignment = SwingConstants.CENTER
        mainPanel.add(placeholder, BorderLayout.CENTER)
    }

    override fun createComponentImpl(): JComponent {
        return mainPanel
    }

    override fun onActivated() {
        refreshAllData()
    }

    private fun refreshAllData() {
        // Will be implemented in subsequent tasks
    }

    private fun openSettings() {
        // Open settings
        com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            NekoamaBundle.message("settings.name")
        )
    }

    private fun showGuide() {
        JOptionPane.showMessageDialog(
            mainPanel,
            NekoamaBundle.message("dashboard.guide.content"),
            NekoamaBundle.message("dashboard.guide.title"),
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun testConnection() {
        // Will be implemented in subsequent tasks
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (after fixing imports and dependencies)

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "feat(ui): create basic DashboardTab structure"
```

---

### Task 6.2: Create DashboardTabState

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/domain/toolwindow/model/DashboardTabState.kt`

**Step 1: Create the state class**

```kotlin
package com.cw2.nekoama.domain.toolwindow.model

import kotlinx.serialization.Serializable

/**
 * Dashboard Tab 状态
 */
@Serializable
data class DashboardTabState(
    val lastRefreshTime: Long = 0L,
    val autoRefreshEnabled: Boolean = true
) : TabState
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/domain/toolwindow/model/DashboardTabState.kt
git commit -m "feat(domain): add DashboardTabState"
```

---

### Task 6.3: Update TabFactories to Use DashboardTab

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/TabFactories.kt`

**Step 1: Read the existing file**

Check the file to understand current tab factory registration.

**Step 2: Replace WelcomeTab with DashboardTab**

```kotlin
// Remove WelcomeTab factory
// Add DashboardTab factory
registerTabFactory(DashboardTab::class.java) { project, coordinator ->
    DashboardTab(project, coordinator)
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/TabFactories.kt
git commit -m "refactor(ui): replace WelcomeTab with DashboardTab"
```

---

### Task 6.4: Add i18n Messages for Dashboard Tab

**Files:**
- Modify: `src/main/resources/messages/NekoamaBundle.properties`

**Step 1: Add all dashboard-related messages**

```properties
# Dashboard Tab
dashboard.tab.title=Dashboard
dashboard.button.settings=Settings
dashboard.button.guide=User Guide
dashboard.button.testConnection=Test Connection
dashboard.guide.title=Nekoama User Guide
dashboard.guide.content=<html><h2>Welcome to Nekoama!</h2>\
<p>Nekoama is an AI-powered code assistant that helps you:</p>\
<ul>\
<li><b>Naming Suggestions:</b> Right-click on code to generate better names</li>\
<li><b>Comment Generation:</b> Automatically generate code comments</li>\
<li><b>Custom Generation:</b> Generate code based on your custom prompts</li>\
</ul>\
<p><b>Quick Start:</b></p>\
<ol>\
<li>Configure your API key in Settings</li>\
<li>Test your connection using the "Test Connection" button</li>\
<li>Right-click on code to use Nekoama features</li>\
</ol></html>

# Network Status
dashboard.network.title=API Connectivity Status
dashboard.network.proxy=Proxy
dashboard.network.status=Status
dashboard.network.endpoint=Endpoint
dashboard.network.model=Model
dashboard.network.status.connected=Connected ({0}ms)
dashboard.network.status.disconnected=Disconnected
dashboard.network.troubleshooting=Troubleshooting Guide

# Token Statistics
dashboard.token.title=Token Usage Statistics
dashboard.token.total=Total
dashboard.token.thisMonth=This Month
dashboard.token.vsLastMonth=vs Last Month
dashboard.token.noData=No data available

# Usage Statistics
dashboard.usage.title=Feature Usage Statistics
dashboard.usage.naming=Naming Suggestions
dashboard.usage.comment=Comment Generation
dashboard.usage.custom=Custom Generation
dashboard.usage.total=Total: {0} uses
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/resources/messages/NekoamaBundle.properties
git commit -m "feat(i18n): add Dashboard Tab messages"
```

---

### Task 6.5: Implement Network Status Panel

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**Step 1: Create NetworkStatusPanel inner class**

```kotlin
private inner class NetworkStatusPanel : JBPanel<JBPanel<*>>(BorderLayout()) {
    private val titleLabel = JBLabel().apply {
        text = NekoamaBundle.message("dashboard.network.title")
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        border = JBUI.Borders.empty(10, 10, 5, 10)
    }

    private val contentPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(10)
    }

    private val proxyLabel = JBLabel()
    private val statusLabel = JBLabel()
    private val endpointLabel = JBLabel()
    private val troubleshootingPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isVisible = false
    }

    init {
        add(titleLabel, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)

        contentPanel.add(createInfoRow(NekoamaBundle.message("dashboard.network.proxy"), proxyLabel))
        contentPanel.add(createInfoRow(NekoamaBundle.message("dashboard.network.status"), statusLabel))
        contentPanel.add(createInfoRow(NekoamaBundle.message("dashboard.network.endpoint"), endpointLabel))
        contentPanel.add(troubleshootingPanel)
    }

    fun updateStatus(status: ConnectivityStatus) {
        proxyLabel.text = status.proxyConfig?.toString() ?: "None"
        statusLabel.text = if (status.isConnected) {
            NekoamaBundle.message("dashboard.network.status.connected", status.responseTime)
        } else {
            NekoamaBundle.message("dashboard.network.status.disconnected")
        }
        statusLabel.foreground = if (status.isConnected) {
            JBColor.GREEN
        } else {
            JBColor.RED
        }

        // Show troubleshooting guide only when disconnected
        if (!status.isConnected && status.troubleshootingGuide != null) {
            troubleshootingPanel.isVisible = true
            troubleshootingPanel.removeAll()
            status.troubleshootingGuide.forEach { step ->
                troubleshootingPanel.add(JBLabel("• $step"))
            }
        } else {
            troubleshootingPanel.isVisible = false
        }

        repaint()
    }

    private fun createInfoRow(label: String, valueLabel: JBLabel): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.border = JBUI.Borders.empty(2)
        panel.add(JBLabel("$label: "))
        panel.add(valueLabel)
        return panel
    }
}
```

**Step 2: Add to DashboardTab**

```kotlin
private val networkStatusPanel = NetworkStatusPanel()

// In layoutComponents():
mainPanel.add(networkStatusPanel, BorderLayout.NORTH)
```

**Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "feat(ui): add network status panel to Dashboard Tab"
```

---

### Task 6.6: Implement Token Statistics Panel

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**Step 1: Create TokenStatisticsPanel inner class**

```kotlin
private inner class TokenStatisticsPanel : JBPanel<JBPanel<*>>(BorderLayout()) {
    private val titleLabel = JBLabel().apply {
        text = NekoamaBundle.message("dashboard.token.title")
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        border = JBUI.Borders.empty(10, 10, 5, 10)
    }

    private val contentPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(10)
    }

    private val totalLabel = JBLabel()
    private val thisMonthLabel = JBLabel()
    private val vsLastMonthLabel = JBLabel()

    init {
        add(titleLabel, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)

        contentPanel.add(createStatRow(NekoamaBundle.message("dashboard.token.total"), totalLabel))
        contentPanel.add(createStatRow(NekoamaBundle.message("dashboard.token.thisMonth"), thisMonthLabel))
        contentPanel.add(createStatRow(NekoamaBundle.message("dashboard.token.vsLastMonth"), vsLastMonthLabel))
    }

    fun updateStatistics(statistics: TokenStatistics) {
        totalLabel.text = statistics.formatTokenCount(statistics.totalTokens) + " tokens"
        thisMonthLabel.text = statistics.formatTokenCount(statistics.currentMonthData.totalTokens) + " tokens"

        val growth = statistics.getMonthOverMonthGrowth()
        vsLastMonthLabel.text = if (growth != null) {
            val arrow = if (growth >= 0) "▲" else "▼"
            val color = if (growth >= 0) JBColor.GREEN else JBColor.RED
            vsLastMonthLabel.foreground = color
            "$arrow ${String.format("%.1f", growth)}%"
        } else {
            vsLastMonthLabel.foreground = UIUtil.getLabelForeground()
            NekoamaBundle.message("dashboard.token.noData")
        }

        repaint()
    }

    private fun createStatRow(label: String, valueLabel: JBLabel): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.border = JBUI.Borders.empty(2)
        panel.add(JBLabel("$label: "))
        panel.add(valueLabel)
        return panel
    }
}
```

**Step 2: Add to DashboardTab and wire refresh logic**

```kotlin
private val tokenStatsPanel = TokenStatisticsPanel()

// In refreshAllData():
tokenStatsPanel.updateStatistics(statisticsService.getTokenStatistics())

// In layoutComponents():
mainPanel.add(tokenStatsPanel, BorderLayout.CENTER)
```

**Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "feat(ui): add token statistics panel to Dashboard Tab"
```

---

### Task 6.7: Implement Usage Statistics Panel

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt`

**Step 1: Create UsageStatisticsPanel inner class**

```kotlin
private inner class UsageStatisticsPanel : JBPanel<JBPanel<*>>(BorderLayout()) {
    private val titleLabel = JBLabel().apply {
        text = NekoamaBundle.message("dashboard.usage.title")
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        border = JBUI.Borders.empty(10, 10, 5, 10)
    }

    private val contentPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(10)
    }

    private val namingProgressBar = JProgressBar(0, 100)
    private val commentProgressBar = JProgressBar(0, 100)
    private val customProgressBar = JProgressBar(0, 100)
    private val totalLabel = JBLabel()

    init {
        add(titleLabel, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)

        contentPanel.add(createProgressRow(NekoamaBundle.message("dashboard.usage.naming"), namingProgressBar))
        contentPanel.add(createProgressRow(NekoamaBundle.message("dashboard.usage.comment"), commentProgressBar))
        contentPanel.add(createProgressRow(NekoamaBundle.message("dashboard.usage.custom"), customProgressBar))
        contentPanel.add(totalLabel)
    }

    fun updateStatistics(statistics: UsageStatistics) {
        namingProgressBar.value = statistics.getPercentage(ActionType.NAMING).toInt()
        commentProgressBar.value = statistics.getPercentage(ActionType.COMMENT).toInt()
        customProgressBar.value = statistics.getPercentage(ActionType.CUSTOM_GENERATE).toInt()

        totalLabel.text = NekoamaBundle.message("dashboard.usage.total", statistics.totalCount)

        repaint()
    }

    private fun createProgressRow(label: String, progressBar: JProgressBar): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.border = JBUI.Borders.empty(2)
        panel.add(JBLabel(label))
        panel.add(progressBar)
        val percentLabel = JBLabel("0%")
        progressBar.addChangeListener {
            percentLabel.text = "${progressBar.value}%"
        }
        panel.add(percentLabel)
        return panel
    }
}
```

**Step 2: Add to DashboardTab and wire refresh logic**

```kotlin
private val usageStatsPanel = UsageStatisticsPanel()

// In refreshAllData():
usageStatsPanel.updateStatistics(statisticsService.getUsageStatistics())

// In layoutComponents(): Use a scroll pane or split pane to organize all panels
```

**Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/DashboardTab.kt
git commit -m "feat(ui): add usage statistics panel to Dashboard Tab"
```

---

## Phase 7: Action Integration

### Task 7.1: Add Statistics Recording to GenerateNamingAction

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/actions/GenerateNamingAction.kt`

**Step 1: Read the existing action file**

Understand the current flow and where to inject statistics recording.

**Step 2: Add statistics recording after successful execution**

```kotlin
// In the actionPerformed or success callback:
project.service<StatisticsService>().let { service ->
    CoroutineScope(Dispatchers.IO).launch {
        service.recordUsage(ActionType.NAMING)
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/actions/GenerateNamingAction.kt
git commit -m "feat(stats): record usage in GenerateNamingAction"
```

---

### Task 7.2: Add Statistics Recording to GenerateCommentAction

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/actions/GenerateCommentAction.kt`

**Step 1: Add statistics recording after successful execution**

```kotlin
// In the actionPerformed or success callback:
project.service<StatisticsService>().let { service ->
    CoroutineScope(Dispatchers.IO).launch {
        service.recordUsage(ActionType.COMMENT)
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/actions/GenerateCommentAction.kt
git commit -m "feat(stats): record usage in GenerateCommentAction"
```

---

### Task 7.3: Add Statistics Recording to CustomGenerateAction

**Files:**
- Modify: `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/actions/CustomGenerateAction.kt`

**Step 1: Add statistics recording after successful execution**

```kotlin
// In the actionPerformed or success callback:
project.service<StatisticsService>().let { service ->
    CoroutineScope(Dispatchers.IO).launch {
        service.recordUsage(ActionType.CUSTOM_GENERATE)
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/interfaces/intellij/actions/CustomGenerateAction.kt
git commit -m "feat(stats): record usage in CustomGenerateAction"
```

---

## Phase 8: Service Registration

### Task 8.1: Register Statistics Services

**Files:**
- Create: `src/main/kotlin/com/cw2/nekoama/infrastructure/statistics/StatisticsServiceRegistration.kt`

**Step 1: Create service registration**

```kotlin
package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.service.NetworkTestService
import com.cw2.nekoama.domain.statistics.service.NetworkTestServiceImpl
import com.cw2.nekoama.domain.statistics.service.StatisticsService
import com.cw2.nekoama.domain.statistics.service.StatisticsServiceImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * 统计相关服务注册
 */
@Service(Service.Level.PROJECT)
class StatisticsServiceRegistration(
    private val project: Project
) {
    init {
        // Services are registered via @Service annotations
        // This class can be used for initialization logic
    }

    companion object {
        fun getStatisticsService(project: Project): StatisticsService {
            return project.getService(StatisticsServiceImpl::class.java)
        }

        fun getNetworkTestService(project: Project): NetworkTestService {
            return project.getService(NetworkTestServiceImpl::class.java)
        }
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/com/cw2/nekoama/infrastructure/statistics/StatisticsServiceRegistration.kt
git commit -m "feat(infra): register statistics services"
```

---

## Phase 9: Icon and Visual Polish

### Task 9.1: Add Dashboard Icon

**Files:**
- Modify: `src/main/resources/icons/NekoamaIcons.kt` or create new icon resource

**Step 1: Add dashboard icon constant**

```kotlin
val DASHBOARD = load("/icons/dashboard.svg")
```

**Step 2: Add icon SVG file to resources**

Create `src/main/resources/icons/dashboard.svg`

**Step 3: Commit**

```bash
git add src/main/resources/icons/dashboard.svg
git add src/main/resources/icons/NekoamaIcons.kt
git commit -m "feat(ui): add dashboard icon"
```

---

## Phase 10: Final Testing and Integration

### Task 10.1: Run all tests

**Step 1: Run full test suite**

```bash
./gradlew test
```

Expected: All tests pass

**Step 2: Verify plugin builds**

```bash
./gradlew buildPlugin
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git commit -m "test(stats): all tests passing"
```

---

### Task 10.2: Manual Testing Checklist

**Step 1: Create manual testing document**

```markdown
# Dashboard Tab - Manual Testing Checklist

## UI Display Tests
- [ ] Dashboard Tab appears in Tool Window
- [ ] Toolbar buttons display correctly
- [ ] Network status panel shows initial state
- [ ] Token statistics panel displays correctly
- [ ] Usage statistics progress bars render

## Functionality Tests
- [ ] Settings button opens configuration
- [ ] Guide button shows help dialog
- [ ] Test Connection button triggers network test
- [ ] Statistics update when tab is activated

## Data Persistence Tests
- [ ] Statistics persist across IDE restarts
- [ ] Usage counts increment correctly
- [ ] Token history records properly

## Theme Tests
- [ ] Light theme displays correctly
- [ ] Dark theme displays correctly
- [ ] Colors adapt to theme changes

## Edge Case Tests
- [ ] Zero usage statistics handled
- [ ] Network failure shows troubleshooting
- [ ] Missing history data shows default values
```

**Step 2: Run manual tests in development IDE**

**Step 3: Document any issues found**

---

### Task 10.3: Update Documentation

**Files:**
- Modify: `agent_docs/memories/active_context.md`

**Step 1: Update active context**

Add Dashboard Tab to the current features list and update architecture notes.

**Step 2: Commit**

```bash
git add agent_docs/memories/active_context.md
git commit -m "docs(stats): update active context with Dashboard Tab"
```

---

## Definition of Done Checklist

- [ ] All unit tests pass (100%)
- [ ] Plugin builds successfully
- [ ] Manual UI testing completed
- [ ] WelcomeTab removed from codebase
- [ ] DashboardTab fully functional
- [ ] Statistics persist across sessions
- [ ] i18n messages complete
- [ ] Theme adaptation verified
- [ ] Actions integrated with statistics
- [ ] Token interceptor working
- [ ] Documentation updated

---

**Plan created:** 2025-01-11
**Estimated Tasks:** 40+
**Estimated Implementation Time:** 3-4 days
**Test Coverage Target:** >80%
