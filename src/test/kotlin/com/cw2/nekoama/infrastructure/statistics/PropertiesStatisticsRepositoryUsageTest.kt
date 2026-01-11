package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertEquals

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

    @org.junit.jupiter.api.Test
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

    @org.junit.jupiter.api.Test
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

    @org.junit.jupiter.api.Test
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
