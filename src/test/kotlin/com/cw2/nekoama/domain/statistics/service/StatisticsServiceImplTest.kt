package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ActionType
import com.cw2.nekoama.domain.statistics.model.MonthlyTokenData
import com.cw2.nekoama.domain.statistics.model.TokenStatistics
import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

/**
 * StatisticsServiceImpl 测试
 *
 * ## 当前测试策略
 * 由于 StatisticsServiceImpl 使用服务定位器模式获取 repository
 *（`project.service<StatisticsRepository>()`），而非构造函数注入，
 * 当前测试采用 relaxed mock 验证基本行为。
 *
 * ## 未来改进方向
 * 1. **重构构造函数**: 将 StatisticsRepository 通过构造函数注入，
 *    便于单元测试时注入 mock repository
 * 2. **添加交互验证**: 验证 repository.save/load 方法的实际调用
 * 3. **提高覆盖率**: 添加边界条件和异常场景的测试
 *
 * ## 架构改进示例
 * ```kotlin
 * // 改进前（服务定位器）
 * class StatisticsServiceImpl(private val project: Project) : StatisticsService {
 *     private val repository: StatisticsRepository get() = project.service()
 * }
 *
 * // 改进后（依赖注入）
 * class StatisticsServiceImpl(
 *     private val repository: StatisticsRepository
 * ) : StatisticsService
 * ```
 */
@DisplayName("StatisticsServiceImpl - 统计服务测试")
class StatisticsServiceImplTest {

    private lateinit var service: StatisticsServiceImpl
    private lateinit var mockProject: com.intellij.openapi.project.Project

    @BeforeEach
    fun setUp() {
        // 使用 relaxed mock，让 service() 返回默认实现
        mockProject = mockk(relaxed = true)
        service = StatisticsServiceImpl(mockProject)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    @DisplayName("获取使用统计 - 应该返回非空默认值")
    fun `获取使用统计 - 应该返回非空默认值`() {
        // When
        val result = service.getUsageStatistics()

        // Then - 应该返回默认值（因为 mock repository 返回默认值）
        assertThat(result.namingCount).isGreaterThanOrEqualTo(0)
        assertThat(result.commentCount).isGreaterThanOrEqualTo(0)
        assertThat(result.customGenerateCount).isGreaterThanOrEqualTo(0)
    }

    @Test
    @DisplayName("获取 Token 统计 - 应该返回默认统计对象")
    fun `获取 Token 统计 - 应该返回默认统计对象`() {
        // When
        val result = service.getTokenStatistics()

        // Then
        assertThat(result.totalTokens).isGreaterThanOrEqualTo(0)
        assertThat(result.currentMonthData.totalTokens).isGreaterThanOrEqualTo(0)
    }

    @Test
    @DisplayName("记录功能使用 - 调用不应该抛出异常")
    fun `记录功能使用 - 调用不应该抛出异常`() = runTest {
        // When & Then - 不应该抛出异常
        service.recordUsage(ActionType.NAMING)
        service.recordUsage(ActionType.COMMENT)
        service.recordUsage(ActionType.CUSTOM_GENERATE)
    }

    @Test
    @DisplayName("记录 Token 使用 - 调用不应该抛出异常")
    fun `记录 Token 使用 - 调用不应该抛出异常`() = runTest {
        // When & Then - 不应该抛出异常
        service.recordTokenUsage(TokenUsageData(100, 50, 150))
    }

    @Test
    @DisplayName("环比增长计算 - 有上月数据应该计算增长率")
    fun `环比增长计算 - 有上月数据应该计算增长率`() {
        // Given - 创建带历史数据的 TokenStatistics
        val currentMonth = MonthlyTokenData.currentYearMonth()
        val lastMonth = MonthlyTokenData.lastYearMonth()
        val history = mapOf(
            currentMonth to MonthlyTokenData(currentMonth, totalTokens = 1500),
            lastMonth to MonthlyTokenData(lastMonth, totalTokens = 1000)
        )
        val stats = TokenStatistics(
            totalTokens = 2500,
            currentMonthData = MonthlyTokenData(currentMonth, totalTokens = 1500),
            lastMonthData = MonthlyTokenData(lastMonth, totalTokens = 1000),
            history = history
        )

        // When
        val growth = stats.getMonthOverMonthGrowth()

        // Then - (1500 - 1000) / 1000 * 100 = 50%
        assertThat(growth).isEqualTo(50f)
    }

    @Test
    @DisplayName("环比增长计算 - 无上月数据应该返回负值")
    fun `环比增长计算 - 无上月数据应该返回负值`() {
        // Given - 只有当月数据
        val currentMonth = MonthlyTokenData.currentYearMonth()
        val stats = TokenStatistics(
            totalTokens = 1500,
            currentMonthData = MonthlyTokenData(currentMonth, totalTokens = 1500),
            lastMonthData = null,
            history = mapOf(currentMonth to MonthlyTokenData(currentMonth, totalTokens = 1500))
        )

        // When
        val growth = stats.getMonthOverMonthGrowth()

        // Then - 应该返回负值（相对于100万基准）
        assertThat(growth).isLessThan(0f)
    }

    @Test
    @DisplayName("使用统计 - 总数计算应该正确")
    fun `使用统计 - 总数计算应该正确`() {
        // Given
        val stats = UsageStatistics(
            namingCount = 10,
            commentCount = 20,
            customGenerateCount = 30
        )

        // When
        val total = stats.totalCount

        // Then
        assertThat(total).isEqualTo(60)
    }

    @Test
    @DisplayName("使用统计 - 增量操作应该返回新对象")
    fun `使用统计 - 增量操作应该返回新对象`() {
        // Given
        val stats = UsageStatistics(namingCount = 5)

        // When
        val updated = stats.increment(ActionType.NAMING)

        // Then
        assertThat(updated.namingCount).isEqualTo(6)
        assertThat(stats.namingCount).isEqualTo(5) // 原对象不变
    }

    @Test
    @DisplayName("使用统计 - 百分比计算应该正确")
    fun `使用统计 - 百分比计算应该正确`() {
        // Given
        val stats = UsageStatistics(
            namingCount = 10,
            commentCount = 20,
            customGenerateCount = 30
        )

        // When
        val namingPercent = stats.getPercentage(ActionType.NAMING)

        // Then - 10/60 * 100 ≈ 16.67%
        assertThat(namingPercent).isEqualTo(16.666667f)
    }

    @Test
    @DisplayName("Token 统计 - 格式化大数值应该显示 M 单位")
    fun `Token 统计 - 格式化大数值应该显示 M 单位`() {
        // Given
        val stats = TokenStatistics()

        // When
        val formatted = stats.formatTokenCount(150000)

        // Then
        assertThat(formatted).isEqualTo("0.15M")
    }

    @Test
    @DisplayName("Token 统计 - 格式化小数值应该显示原始数字")
    fun `Token 统计 - 格式化小数值应该显示原始数字`() {
        // Given
        val stats = TokenStatistics()

        // When
        val formatted = stats.formatTokenCount(5000)

        // Then
        assertThat(formatted).isEqualTo("5000")
    }

    @Test
    @DisplayName("Token 统计 - 边界值 100000 应该显示为 M 单位")
    fun `Token 统计 - 边界值 100000 应该显示为 M 单位`() {
        // Given
        val stats = TokenStatistics()

        // When
        val formatted = stats.formatTokenCount(100000)

        // Then
        assertThat(formatted).isEqualTo("0.10M")
    }
}
