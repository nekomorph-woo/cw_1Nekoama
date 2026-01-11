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
import org.assertj.core.api.Assertions.assertThat

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
    @DisplayName("记录 COMMENT 使用 - 应该正确增加计数")
    fun `记录 COMMENT 使用 - 应该正确增加计数`() = runTest {
        // Given
        val initialStats = UsageStatistics(commentCount = 3)
        every { mockRepository.loadUsageStatistics() } returns initialStats
        every { mockRepository.saveUsageStatistics(any()) } just Runs

        // When
        service.recordUsage(ActionType.COMMENT)

        // Then
        verify { mockRepository.saveUsageStatistics(match { it.commentCount == 4 }) }
    }

    @Test
    @DisplayName("记录 CUSTOM_GENERATE 使用 - 应该正确增加计数")
    fun `记录 CUSTOM_GENERATE 使用 - 应该正确增加计数`() = runTest {
        // Given
        val initialStats = UsageStatistics(customGenerateCount = 7)
        every { mockRepository.loadUsageStatistics() } returns initialStats
        every { mockRepository.saveUsageStatistics(any()) } just Runs

        // When
        service.recordUsage(ActionType.CUSTOM_GENERATE)

        // Then
        verify { mockRepository.saveUsageStatistics(match { it.customGenerateCount == 8 }) }
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
        assertThat(result.namingCount).isEqualTo(10)
        assertThat(result.commentCount).isEqualTo(20)
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
    @DisplayName("记录 Token 使用 - 首次记录应该创建当月数据")
    fun `记录 Token 使用 - 首次记录应该创建当月数据`() = runTest {
        // Given
        val currentMonth = MonthlyTokenData.currentYearMonth()
        every { mockRepository.loadTokenHistory() } returns emptyMap()
        every { mockRepository.getTotalTokens() } returns 0
        every { mockRepository.saveTokenHistory(any()) } just Runs
        every { mockRepository.saveTotalTokens(any()) } just Runs

        // When
        service.recordTokenUsage(TokenUsageData(100, 50, 150))

        // Then
        verify { mockRepository.saveTokenHistory(match {
            it.containsKey(currentMonth) && it[currentMonth]?.totalTokens == 150
        }) }
        verify { mockRepository.saveTotalTokens(150) }
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
        assertThat(result.totalTokens).isEqualTo(2500)
        assertThat(result.currentMonthData.totalTokens).isEqualTo(1500)
        assertThat(result.lastMonthData?.totalTokens).isEqualTo(1000)
        val growth = result.getMonthOverMonthGrowth()
        // (1500 - 1000) / 1000 * 100 = 50%
        assertThat(growth).isEqualTo(50f)
    }

    @Test
    @DisplayName("获取 Token 统计 - 无上月数据应该使用默认基准")
    fun `获取 Token 统计 - 无上月数据应该使用默认基准`() {
        // Given
        val currentMonth = MonthlyTokenData.currentYearMonth()
        val history = mapOf(
            currentMonth to MonthlyTokenData(currentMonth, totalTokens = 1500)
        )
        every { mockRepository.loadTokenHistory() } returns history
        every { mockRepository.getTotalTokens() } returns 1500

        // When
        val result = service.getTokenStatistics()

        // Then
        assertThat(result.totalTokens).isEqualTo(1500)
        assertThat(result.lastMonthData).isNull()
        val growth = result.getMonthOverMonthGrowth()
        // (1500 - 1000000) / 1000000 * 100 = -98.5%
        assertThat(growth).isLessThan(0f)
    }

    @Test
    @DisplayName("获取 Token 统计 - 空历史应该返回默认值")
    fun `获取 Token 统计 - 空历史应该返回默认值`() {
        // Given
        every { mockRepository.loadTokenHistory() } returns emptyMap()
        every { mockRepository.getTotalTokens() } returns 0

        // When
        val result = service.getTokenStatistics()

        // Then
        assertThat(result.totalTokens).isEqualTo(0)
        assertThat(result.currentMonthData.totalTokens).isEqualTo(0)
        assertThat(result.lastMonthData).isNull()
    }
}
