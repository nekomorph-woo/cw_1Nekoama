package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PropertiesStatisticsRepositoryUsageTest : BasePlatformTestCase() {

    private lateinit var repository: PropertiesStatisticsRepository
    private lateinit var statisticsData: StatisticsData

    @BeforeEach
    override fun setUp() {
        super.setUp()
        repository = PropertiesStatisticsRepository(project)
        statisticsData = project.getService(StatisticsData::class.java)
    }

    override fun tearDown() {
        // Clear state
        statisticsData.namingCount = 0
        statisticsData.commentCount = 0
        statisticsData.customGenerateCount = 0
        statisticsData.lastUpdated = 0L
        super.tearDown()
    }

    @org.junit.Test
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
        assertThat(loaded.namingCount).isEqualTo(10)
        assertThat(loaded.commentCount).isEqualTo(20)
        assertThat(loaded.customGenerateCount).isEqualTo(30)
        assertThat(loaded.lastUpdated).isEqualTo(123456789L)
    }

    @org.junit.Test
    fun `加载空统计 - 应该返回默认值`() {
        // Given: No previous data

        // When
        val loaded = repository.loadUsageStatistics()

        // Then
        assertThat(loaded.namingCount).isEqualTo(0)
        assertThat(loaded.commentCount).isEqualTo(0)
        assertThat(loaded.customGenerateCount).isEqualTo(0)
    }

    @org.junit.Test
    fun `增量保存 - 应该正确累加`() {
        // Given
        val stats1 = UsageStatistics(namingCount = 5)
        repository.saveUsageStatistics(stats1)

        // When
        val stats2 = UsageStatistics(namingCount = 10)
        repository.saveUsageStatistics(stats2)
        val loaded = repository.loadUsageStatistics()

        // Then
        assertThat(loaded.namingCount).isEqualTo(10)
    }
}
