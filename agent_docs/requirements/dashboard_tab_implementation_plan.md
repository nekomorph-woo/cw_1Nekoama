# Dashboard Tab 实现计划

## 1. Overview

**目标：** 构建一个功能仪表盘 Tab，提供快捷功能入口、API 网络连通性检测、Token 使用统计和核心功能使用次数统计。

**业务价值：**
- 集中管理用户常用操作（设置、指南、网络检测）
- 提供透明的 Token 消费统计，帮助用户了解使用成本
- 展示功能使用分布，了解插件使用习惯

**范围：**
- [x] In Scope:
  - 移除现有的 WelcomeTab
  - 新增 DashboardTab 替换 WelcomeTab
  - 快捷功能：打开设置面板、显示使用指南（OK Dialog）、手动检测 API 网络连通性
  - 网络状态显示：代理状态、连通性状态、排查指南（仅失败时显示）
  - Token 统计：总计 Token、当月 Token（>10w 显示为 M 单位）、环比上月百分比
  - 功能使用统计：命名建议、注释生成、自定义生成三个功能的进度条（相对于总使用数的百分比）
  - 持久化存储：统计数据跨会话保存

- [ ] Out of Scope:
  - 历史趋势图表
  - 后台自动监控
  - 统计数据清除功能
  - 月度配额限制设置
  - Token 使用速率限制

## 2. Tech Stack

**Backend/Core:**
- **语言：** Kotlin (JVM 21, Kotlin 2.1)
- **架构：** DDD 分层（Domain + Infrastructure + Interfaces）
- **并发：** 协程 + EDT 线程管理（遵循 `edt-threading-rules.md`）

**Frontend/UI:**
- **UI框架：** Swing (JPanel, JButton, JLabel, JProgressBar)
- **主题：** IntelliJ Platform Theme API (遵循 `intellij-theme-adaptation-rules.md`)
- **布局：** BorderLayout, BoxLayout, GridBagLayout
- **国际化：** NekoamaBundle（遵循 `i18n-internationalization-rules.md`）

**Data/Storage:**
- **状态管理：** PersistentStateComponent（IntelliJ 持久化 API）
- **序列化：** JSON（用于 Token 历史数据）

**Testing:**
- **单元测试：** JUnit 5 + MockK
- **测试规则：** 遵循 `kotlin-mockk-testing-rules.md`

## 3. Architecture Design

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    IntelliJ Platform                             │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              interfaces (接口适配层)                       │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  DashboardTab (新增)                                 │  │  │
│  │  │  - 替换 WelcomeTab                                    │  │  │
│  │  │  - UI 组件组装                                        │  │  │
│  │  │  - EDT 线程操作                                       │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  TabFactories (修改)                                 │  │  │
│  │  │  - 移除 WelcomeTab 工厂                              │  │  │
│  │  │  - 注册 DashboardTab 工厂                            │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              ▲                                   │
│                              │ 依赖注入                           │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │            domain (领域模型层)                              │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  statistics/model/ (新增)                            │  │  │
│  │  │  - UsageStatistics: 功能使用统计                      │  │  │
│  │  │  - TokenStatistics: Token 统计                       │  │  │
│  │  │  - MonthlyTokenData: 月度 Token 数据                 │  │  │
│  │  │  - ConnectivityStatus: 连通性状态                    │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  statistics/service/ (新增)                          │  │  │
│  │  │  - StatisticsService: 统计服务接口                   │  │  │
│  │  │  - NetworkTestService: 网络测试服务接口              │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              ▲                                   │
│                              │ 实现接口                           │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │         infrastructure (基础设施层)                         │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  statistics/ (新增)                                   │  │  │
│  │  │  - PropertiesStatisticsRepository: 持久化实现        │  │  │
│  │  │  - TokenUsageInterceptor: Token 拦截器              │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Data Model Layer

**路径：** `src/main/kotlin/com/cw2/nekoama/domain/statistics/model/`

#### `ActionType.kt`
```kotlin
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

#### `UsageStatistics.kt`
```kotlin
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
     * Edge Case 处理：当总次数为 0 时，直接返回 0%（避免除零异常）
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

#### `MonthlyTokenData.kt`
```kotlin
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
            val java.time.YearMonth.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")
            )
        }

        /**
         * 获取上月年月标识
         */
        fun lastYearMonth(): String {
            java.time.YearMonth.now().minusMonths(1).format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")
            )
        }
    }
}
```

#### `TokenStatistics.kt`
```kotlin
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
     *
     * @return 增长率百分比，正数表示增长，负数表示下降。无有效基数时返回 null
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
     * 获取用于显示的基准 Token 数
     *
     * Edge Case 处理：无历史数据时返回默认基准 100 万
     */
    fun getBaselineTokens(): Int {
        return lastMonthData?.totalTokens ?: DEFAULT_BASELINE_TOKENS
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

#### `ConnectivityStatus.kt`
```kotlin
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
    val proxyConfig: com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig? = null,
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

### 3.3 Core Logic Layer

**路径：** `src/main/kotlin/com/cw2/nekoama/domain/statistics/service/`

#### `StatisticsService.kt` (接口)
```kotlin
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

#### `NetworkTestService.kt` (接口)
```kotlin
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

#### `StatisticsServiceImpl.kt` (实现)
```kotlin
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

#### `NetworkTestServiceImpl.kt` (实现)
```kotlin
/**
 * 网络测试服务实现
 */
class NetworkTestServiceImpl(
    private val project: Project
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
        proxyConfig: com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig,
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

### 3.4 UI / Presentation Layer

**路径：** `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/tabs/`

#### `DashboardTab.kt`
```kotlin
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
        icon = AllIcons.General.ToolWindowDashboard
    )

    override val stateType = DashboardTabState::class

    // UI 组件
    private val settingsButton = JButton()
    private val guideButton = JButton()
    private val testConnectionButton = JButton()
    private val connectionStatusLabel = JLabel()
    private val tokenStatsPanel = JPanel()
    private val usageStatsPanel = JPanel()

    // 服务注入（通过依赖注入）
    private val statisticsService: StatisticsService
    private val networkTestService: NetworkTestService

    override fun createComponentImpl(): JComponent {
        // 实现细节...
    }

    override fun onActivated() {
        // 刷新统计数据
        refreshAllData()
    }

    private fun refreshAllData() {
        // 刷新 Token 统计
        // 刷新使用统计
        // 刷新连接状态（从缓存读取，不主动触发检测）
    }
}
```

### 3.5 Infrastructure Layer

**路径：** `src/main/kotlin/com/cw2/nekoama/infrastructure/statistics/`

#### `StatisticsRepository.kt` (接口)
```kotlin
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

#### `PropertiesStatisticsRepository.kt` (实现)
```kotlin
/**
 * 基于 PropertiesComponent 的统计持久化实现
 *
 * 存储策略：
 * 1. 简单计数器（使用次数）→ 直接使用 PropertiesComponent 存储 int
 * 2. 复杂结构（Token 历史）→ JSON 序列化后存入 PropertiesComponent
 */
class PropertiesStatisticsRepository(
    private val project: Project
) : StatisticsRepository {

    private val properties = PropertiesComponent.getInstance(project)

    // JSON 序列化配置
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false  // 紧凑格式节省空间
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

**存储结构说明：**

| 数据类型 | 存储方式 | Key 格式 | 示例值 |
|---------|---------|---------|--------|
| 使用次数 | int | `nekoama.stats.usage.{type}` | `1234` |
| 最后更新时间 | long | `nekoama.stats.usage.lastUpdated` | `1736572800000` |
| Token 总计 | int | `nekoama.stats.token.total` | `1234567` |
| Token 历史 | JSON 字符串 | `nekoama.stats.token.history` | `{"2025-01":{"yearMonth":"2025-01",...}}` |

#### `TokenUsageInterceptor.kt` (OkHttp Interceptor)
```kotlin
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
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
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
    private fun extractTokenUsage(json: String): TokenUsageData? {
        return try {
            val jsonObject = org.json.JSONObject(json)
            val usageObject = jsonObject.optJSONObject("usage") ?: return null

            TokenUsageData(
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

## 4. Implementation Steps (Phasing)

### Phase 1: 领域模型与接口定义（优先级：高）

**目标：** 定义核心接口和数据模型

**任务：**
1. 创建 `domain/statistics/model/` 目录和文件
   - [ ] `ActionType.kt`
   - [ ] `UsageStatistics.kt`
   - [ ] `MonthlyTokenData.kt`
   - [ ] `TokenStatistics.kt`
   - [ ] `ConnectivityStatus.kt`

2. 创建 `domain/statistics/service/` 接口
   - [ ] `StatisticsService.kt`
   - [ ] `NetworkTestService.kt`

**验收标准：**
- 所有接口编译通过
- 数据类具有完整的 KDoc 注释
- 遵循 DDD 分层原则

**测试：**
- 无需测试（仅接口定义）

---

### Phase 2: 基础设施层实现（优先级：高）

**目标：** 实现持久化存储和 Token 拦截

**任务：**
1. 创建 `infrastructure/statistics/` 目录

2. 实现持久化存储
   - [ ] `PropertiesStatisticsRepository.kt`
   - [ ] 单元测试（验证保存/加载/月度切换）

3. 实现 Token 拦截器
   - [ ] `TokenUsageInterceptor.kt`
   - [ ] 修改 `CustomAPIHttpClient` 添加拦截器
   - [ ] 单元测试（模拟 API 响应验证提取逻辑）

**验收标准：**
- 所有单元测试通过
- Token 拦截不影响正常 API 请求

**测试文件：**
- `src/test/kotlin/com/cw2/nekoama/infrastructure/statistics/PropertiesStatisticsRepositoryTest.kt`
- `src/test/kotlin/com/cw2/nekoama/infrastructure/statistics/TokenUsageInterceptorTest.kt`

---

### Phase 3: 服务层实现（优先级：高）

**目标：** 实现业务逻辑编排

**任务：**
1. 实现 `domain/statistics/service/StatisticsServiceImpl.kt`
   - [ ] 记录功能使用
   - [ ] 记录 Token 使用
   - [ ] 计算统计数据
   - [ ] 单元测试

2. 实现 `domain/statistics/service/NetworkTestServiceImpl.kt`
   - [ ] 复用 `ProxyConnectionTester` 进行连通性测试
   - [ ] 生成排查指南
   - [ ] 单元测试

**验收标准：**
- 所有单元测试通过
- 服务线程安全验证

**测试文件：**
- `src/test/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsServiceImplTest.kt`
- `src/test/kotlin/com/cw2/nekoama/domain/statistics/service/NetworkTestServiceImplTest.kt`

---

### Phase 4: UI 层实现（优先级：中）

**目标：** 实现 DashboardTab UI

**任务：**
1. 实现 `DashboardTab.kt`
   - [ ] UI 组件布局（遵循 Swing UI 规则）
   - [ ] 快捷功能按钮实现
   - [ ] 网络状态显示面板
   - [ ] Token 统计显示面板
   - [ ] 使用统计显示面板
   - [ ] 主题适配（深色/浅色）

2. 修改 `TabFactories.kt`
   - [ ] 移除 WelcomeTab 工厂
   - [ ] 添加 DashboardTab 工厂

3. 添加国际化资源
   - [ ] `NekoamaBundle.properties` 中添加所有 UI 文案

**验收标准：**
- UI 组件符合主题适配
- 所有文案使用 NekoamaBundle
- 手动测试验证显示正确

**测试：**
- 手动测试（运行 Plugin）

---

### Phase 5: Action 集成（优先级：中）

**目标：** 在现有 Action 中添加统计记录

**任务：**
1. 修改 `GenerateNamingAction.kt`
   - [ ] 执行成功后记录使用次数

2. 修改 `GenerateCommentAction.kt`
   - [ ] 执行成功后记录使用次数

3. 修改 `CustomGenerateAction.kt`
   - [ ] 执行成功后记录使用次数

**验收标准：**
- Action 执行成功后计数器正确增加
- 统计数据正确持久化

---

### Phase 6: 代码质量与文档（优先级：低）

**目标：** 完善代码质量和文档

**任务：**
1. 代码审查
   - [ ] 确保符合 DDD 分层
   - [ ] 确保线程安全
   - [ ] 确保 EDT 规则遵循

2. 文档完善
   - [ ] 补充 KDoc 注释
   - [ ] 更新 `active_context.md`

**验收标准：**
- 所有 KDoc 完整
- 无 Lint 错误

## 5. Technical Constraints

### 5.1 并发模型
- **UI操作：** 必须在 EDT 线程执行（遵循 `edt-threading-rules.md`）
- **网络测试：** 使用 `Task.Backgroundable` 或协程在后台执行
- **统计记录：** 异步写入，不阻塞主线程

### 5.2 性能要求
- **Tab创建：** 应在 100ms 内完成
- **统计数据加载：** 应在 50ms 内完成
- **网络测试：** 超时时间 10 秒
- **内存占用：** 统计数据不应超过 100KB

### 5.3 兼容性
- **IDE版本：** IntelliJ IDEA 2025.1+ (since-build 251)
- **JDK版本：** JVM 21
- **Kotlin版本：** 2.0.21+

### 5.4 安全性
- **数据验证：** 所有统计数据需要验证范围和合法性
- **异常处理：** 统计记录失败不应影响核心功能
- **资源清理：** Disposable 接口确保资源释放

### 5.5 国际化约束
- **UI文案：** 必须使用 `NekoamaBundle.message()` 获取（遵循 `i18n-internationalization-rules.md`）
- **日志内容：** 可使用中文
- **技术报错：** 可使用中文 + NekoamaError
- **用户报错：** 必须使用英文 + NekoamaBundle

## 6. Testing Strategy

### 6.1 单元测试

**目标类：**
- `PropertiesStatisticsRepository`
- `TokenUsageInterceptor`
- `StatisticsServiceImpl`
- `NetworkTestServiceImpl`

**覆盖目标：**
- 代码覆盖率 > 80%
- 分支覆盖率 > 70%

### 6.2 集成测试

**测试场景：**
- Action 执行 → 统计记录 → 持久化 → UI 显示流程
- API 请求 → Token 拦截 → 统计记录流程
- 月度切换 → Token 数据归档流程

### 6.3 手动/UI验证

**验证项：**
- Dashboard Tab 正常显示
- 快捷按钮功能正常
- 网络测试结果正确显示
- Token 统计数据正确显示
- 使用统计进度条正确计算
- 主题适配正确（深色/浅色）

## 7. Key File Checklist

### Domain层（新增）
- [ ] `domain/statistics/model/ActionType.kt`
- [ ] `domain/statistics/model/UsageStatistics.kt`
- [ ] `domain/statistics/model/MonthlyTokenData.kt`
- [ ] `domain/statistics/model/TokenStatistics.kt`
- [ ] `domain/statistics/model/ConnectivityStatus.kt`
- [ ] `domain/statistics/model/TokenUsageData.kt`
- [ ] `domain/statistics/service/StatisticsService.kt`
- [ ] `domain/statistics/service/NetworkTestService.kt`
- [ ] `domain/statistics/repository/StatisticsRepository.kt`

### Domain层（新增实现）
- [ ] `domain/statistics/service/StatisticsServiceImpl.kt`
- [ ] `domain/statistics/service/NetworkTestServiceImpl.kt`

### Infrastructure层（新增）
- [ ] `infrastructure/statistics/PropertiesStatisticsRepository.kt`
- [ ] `infrastructure/statistics/TokenUsageInterceptor.kt`

### Interfaces层（新增/修改）
- [ ] `interfaces/intellij/toolwindow/tabs/DashboardTab.kt`
- [ ] `interfaces/intellij/toolwindow/tabs/WelcomeTab.kt` (删除)
- [ ] `interfaces/intellij/toolwindow/TabFactories.kt` (修改)

### Interfaces层（修改）
- [ ] `interfaces/intellij/actions/GenerateNamingAction.kt`
- [ ] `interfaces/intellij/actions/GenerateCommentAction.kt`
- [ ] `interfaces/intellij/actions/CustomGenerateAction.kt`

### Infrastructure层（修改）
- [ ] `infrastructure/network/client/CustomAPIHttpClient.kt` (添加拦截器)

### 资源文件（修改）
- [ ] `src/main/resources/messages/NekoamaBundle.properties` (添加 Dashboard 相关文案)

### 测试文件
- [ ] `src/test/kotlin/com/cw2/nekoama/infrastructure/statistics/PropertiesStatisticsRepositoryTest.kt`
- [ ] `src/test/kotlin/com/cw2/nekoama/infrastructure/statistics/TokenUsageInterceptorTest.kt`
- [ ] `src/test/kotlin/com/cw2/nekoama/domain/statistics/service/StatisticsServiceImplTest.kt`
- [ ] `src/test/kotlin/com/cw2/nekoama/domain/statistics/service/NetworkTestServiceImplTest.kt`

## 8. Definition of Done (交付标准)

### 8.1 功能完整性
- [ ] WelcomeTab 已移除
- [ ] Dashboard Tab 正常显示
- [ ] 设置按钮可打开设置面板
- [ ] 指南按钮弹出 OK Dialog（使用 i18n 文案）
- [ ] 检测按钮可触发网络连通性测试
- [ ] 网络状态正确显示（代理、连通性、排查指南）
- [ ] Token 统计正确显示（总计、当月、环比）
- [ ] 使用统计进度条正确显示（相对百分比）
- [ ] 统计数据跨会话持久化

### 8.2 代码质量
- [ ] 所有单元测试通过（100%）
- [ ] 无新的 Lint 错误
- [ ] 符合 DDD 分层规则
- [ ] 符合 EDT 线程规则
- [ ] 符合 Swing UI 规则
- [ ] 符合 i18n 国际化规则

### 8.3 文档完整性
- [ ] 所有公开 API 有 KDoc 注释
- [ ] 关键设计决策有内联注释
- [ ] `active_context.md` 更新

### 8.4 扩展性验证
- [ ] 统计服务接口易于扩展新统计类型
- [ ] Token 拦截器不影响主流程（异步记录 + 异常隔离）
- [ ] 持久化方案可替换

## 9. Edge Cases 处理总结

### 9.1 Token 统计 Edge Cases

| 场景 | 处理方式 |
|------|---------|
| 无上月数据（首次使用） | 使用 1,000,000 Token 作为基准计算环比 |
| 上月数据为 0 | 返回 null，不显示环比 |
| 当月数据为 0 | 正常显示 0，环比基于上月数据计算 |
| Token 数 ≥ 100,000 | 显示为 M 单位（如 1.23M） |

### 9.2 使用次数统计 Edge Cases

| 场景 | 处理方式 |
|------|---------|
| 总次数为 0（从未使用） | 所有功能百分比返回 0% |
| 单个功能为 0 | 该功能进度条显示 0% |
| 除零异常 | 通过 `if (total > 0)` 前置检查避免 |

### 9.3 Token 拦截器 Edge Cases

| 场景 | 处理方式 |
|------|---------|
| API 响应无 usage 字段 | 静默忽略，不记录 |
| JSON 解析失败 | 静默失败，返回原始响应 |
| 统计记录失败 | 捕获异常，仅记录日志 |
| 响应体已被消费 | 重新构建 Response 返回 |

## 10. UI 设计稿

### 10.1 整体布局
```
┌─────────────────────────────────────────────────┐
│  Dashboard                                      │
├─────────────────────────────────────────────────┤
│  [⚙️ Settings]  [📖 Guide]  [🧪 Test Connection]│
├─────────────────────────────────────────────────┤
│  🌐 API Connectivity Status                     │
│  ┌───────────────────────────────────────────┐  │
│  │ Proxy: SOCKS 127.0.0.1:7891              │  │
│  │ Status: ● Connected (23ms)               │  │
│  │ Endpoint: https://api.openai.com         │  │
│  │ Model: gpt-4o-mini                       │  │
│  └───────────────────────────────────────────┘  │
├─────────────────────────────────────────────────┤
│  📊 Token Usage Statistics                     │
│  ┌───────────────────────────────────────────┐  │
│  │ Total: 1.23M tokens                       │  │
│  │ This Month: 234.56K tokens                │  │
│  │ vs Last Month: ▲ 15.3%                   │  │
│  │ ━━━━━━━━━●──────────────────────────────│  │
│  └───────────────────────────────────────────┘  │
├─────────────────────────────────────────────────┤
│  🎯 Feature Usage Statistics                   │
│  ┌───────────────────────────────────────────┐  │
│  │ Naming Suggestions  ███████████░░  45%    │  │
│  │ Comment Generation   ████████░░░░░  35%    │  │
│  │ Custom Generation    ████░░░░░░░░░  20%    │  │
│  │                                           │  │
│  │ Total: 1,234 uses                         │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 9.2 连接失败时的排查指南
```
┌─────────────────────────────────────────────────┐
│  🌐 API Connectivity Status                     │
│  ┌───────────────────────────────────────────┐  │
│  │ Proxy: HTTP 127.0.0.1:10809              │  │
│  │ Status: ● Disconnected                   │  │
│  │                                           │  │
│  │ Troubleshooting Guide:                    │  │
│  │ 1. Check if proxy server is running       │  │
│  │ 2. Verify proxy port in IDEA settings     │  │
│  │ 3. Test proxy authentication              │  │
│  │ 4. Check network connection               │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

## 10. 设计决策记录 (ADR)

### ADR-001: 为什么使用 PropertiesComponent 而非文件存储？
**决策：** 使用 PropertiesComponent 持久化统计数据

**理由：**
1. PropertiesComponent 是 IntelliJ 平台推荐的持久化方案
2. 自动处理跨平台兼容性
3. 支持 IDE 升级时数据迁移
4. 无需处理文件权限和路径问题

**未来：** 如需更复杂的查询或大量数据，可考虑 SQLite

### ADR-002: 为什么 Token 拦截器不阻塞主线程？
**决策：** Token 统计记录使用异步写入

**理由：**
1. Token 统计不是核心功能，失败不应影响 API 调用
2. 统计记录有少量 I/O 延迟
3. 使用协程 GlobalScope 确保即使 Tab 关闭也能完成记录

**风险：** IDE 关闭时可能丢失最后几次统计，但这是可接受的

### ADR-003: 为什么不支持清除统计数据？
**决策：** 不提供"清除统计"功能

**理由：**
1. 用户需求中没有此功能
2. 统计数据应该长期累积以反映真实使用情况
3. 如需重置，用户可手动删除配置文件

**未来：** 如有需求，可添加隐藏的重置功能

### ADR-004: Token 统计为什么使用 100 万作为默认基准？
**决策：** 无历史数据时使用 1,000,000 Token 作为环比计算基准

**理由：**
1. 首次使用时需要显示有意义的百分比
2. 100 万是一个常见的小型项目月度 Token 使用量
3. 百分比计算可以让用户直观了解当前使用量相对于"正常水平"的位置

**影响：**
- 当月使用 50 万 Token 时显示 -50%（低于基准）
- 当月使用 150 万 Token 时显示 +50%（高于基准）

### ADR-005: Token 拦截器为什么不需要开关？
**决策：** Token 拦截器默认始终开启，不提供配置开关

**理由：**
1. Token 统计是基础功能，应该始终工作
2. 拦截器设计为完全异步，不影响主流程
3. 异常处理完善，任何失败都静默处理
4. 简化配置，减少用户困惑

**风险：**
- 如拦截器存在 bug，可能影响所有 API 请求
- 缓解措施：完善的异常处理 + 单元测试

---

**文档版本：** 1.1
**创建日期：** 2025-01-11
**最后更新：** 2025-01-11（添加 Edge Cases 处理）
**状态：** 已审核
