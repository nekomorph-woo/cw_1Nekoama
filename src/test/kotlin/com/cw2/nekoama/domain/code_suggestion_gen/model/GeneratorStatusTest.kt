package com.cw2.nekoama.domain.code_suggestion_gen.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 生成器状态测试
 *
 * 验证 GeneratorStatus 数据类的属性和默认值
 */
@DisplayName("生成器状态测试")
class GeneratorStatusTest {

    // ==================== 基本属性测试 ====================

    @Nested
    @DisplayName("基本属性测试")
    inner class BasicPropertiesTests {

        @Test
        @DisplayName("创建状态 - 应该包含所有必需属性")
        fun `创建状态 - 应该包含所有必需属性`() {
            val beforeTime = System.currentTimeMillis()
            val status = GeneratorStatus(
                available = true
            )
            val afterTime = System.currentTimeMillis()

            assertThat(status.available).isTrue()
            assertThat(status.latencyMs).isNull()
            assertThat(status.quotaRemaining).isNull()
            assertThat(status.quotaTotal).isNull()
            assertThat(status.lastError).isNull()
            assertThat(status.lastCheckTime).isBetween(beforeTime, afterTime)
        }

        @Test
        @DisplayName("创建状态 - available 为 false 应该正确设置")
        fun `创建状态 - available 为 false 应该正确设置`() {
            val status = GeneratorStatus(
                available = false
            )

            assertThat(status.available).isFalse()
        }
    }

    // ==================== 可选属性测试 ====================

    @Nested
    @DisplayName("可选属性测试")
    inner class OptionalPropertiesTests {

        @Test
        @DisplayName("latencyMs - 应该能够设置延迟时间")
        fun `latencyMs - 应该能够设置延迟时间`() {
            val status = GeneratorStatus(
                available = true,
                latencyMs = 150
            )

            assertThat(status.latencyMs).isEqualTo(150)
        }

        @Test
        @DisplayName("quotaRemaining - 应该能够设置剩余配额")
        fun `quotaRemaining - 应该能够设置剩余配额`() {
            val status = GeneratorStatus(
                available = true,
                quotaRemaining = 80
            )

            assertThat(status.quotaRemaining).isEqualTo(80)
        }

        @Test
        @DisplayName("quotaTotal - 应该能够设置总配额")
        fun `quotaTotal - 应该能够设置总配额`() {
            val status = GeneratorStatus(
                available = true,
                quotaTotal = 100
            )

            assertThat(status.quotaTotal).isEqualTo(100)
        }

        @Test
        @DisplayName("lastError - 应该能够设置错误信息")
        fun `lastError - 应该能够设置错误信息`() {
            val status = GeneratorStatus(
                available = false,
                lastError = "网络连接超时"
            )

            assertThat(status.lastError).isEqualTo("网络连接超时")
        }

        @Test
        @DisplayName("lastCheckTime - 应该能够自定义时间戳")
        fun `lastCheckTime - 应该能够自定义时间戳`() {
            val customTime = 1234567890L
            val status = GeneratorStatus(
                available = true,
                lastCheckTime = customTime
            )

            assertThat(status.lastCheckTime).isEqualTo(customTime)
        }
    }

    // ==================== 组合属性测试 ====================

    @Nested
    @DisplayName("组合属性测试")
    inner class CombinedPropertiesTests {

        @Test
        @DisplayName("完整状态 - 应该包含所有属性")
        fun `完整状态 - 应该包含所有属性`() {
            val customTime = System.currentTimeMillis()
            val status = GeneratorStatus(
                available = true,
                latencyMs = 200,
                quotaRemaining = 75,
                quotaTotal = 100,
                lastError = null,
                lastCheckTime = customTime
            )

            assertThat(status.available).isTrue()
            assertThat(status.latencyMs).isEqualTo(200)
            assertThat(status.quotaRemaining).isEqualTo(75)
            assertThat(status.quotaTotal).isEqualTo(100)
            assertThat(status.lastError).isNull()
            assertThat(status.lastCheckTime).isEqualTo(customTime)
        }

        @Test
        @DisplayName("错误状态 - 应该包含错误信息")
        fun `错误状态 - 应该包含错误信息`() {
            val status = GeneratorStatus(
                available = false,
                latencyMs = null,
                quotaRemaining = null,
                quotaTotal = null,
                lastError = "API 调用失败：401 Unauthorized"
            )

            assertThat(status.available).isFalse()
            assertThat(status.lastError).isEqualTo("API 调用失败：401 Unauthorized")
        }
    }

    // ==================== 配额计算测试 ====================

    @Nested
    @DisplayName("配额计算测试")
    inner class QuotaCalculationTests {

        @Test
        @DisplayName("配额使用率 - 应该能够计算")
        fun `配额使用率 - 应该能够计算`() {
            val status = GeneratorStatus(
                available = true,
                quotaRemaining = 30,
                quotaTotal = 100
            )

            val usedPercentage = status.quotaTotal?.let { total ->
                status.quotaRemaining?.let { remaining ->
                    ((total - remaining).toDouble() / total * 100).toInt()
                }
            }

            assertThat(usedPercentage).isEqualTo(70)
        }

        @Test
        @DisplayName("配额使用率 - 缺少配额信息应该返回 null")
        fun `配额使用率 - 缺少配额信息应该返回 null`() {
            val status = GeneratorStatus(
                available = true,
                quotaRemaining = null,
                quotaTotal = null
            )

            val usedPercentage = status.quotaTotal?.let { total ->
                status.quotaRemaining?.let { remaining ->
                    ((total - remaining).toDouble() / total * 100).toInt()
                }
            }

            assertThat(usedPercentage).isNull()
        }

        @Test
        @DisplayName("配额使用率 - 只有 total 没有 remaining 应该返回 null")
        fun `配额使用率 - 只有 total 没有 remaining 应该返回 null`() {
            val status = GeneratorStatus(
                available = true,
                quotaRemaining = null,
                quotaTotal = 100
            )

            val usedPercentage = status.quotaTotal?.let { total ->
                status.quotaRemaining?.let { remaining ->
                    ((total - remaining).toDouble() / total * 100).toInt()
                }
            }

            assertThat(usedPercentage).isNull()
        }
    }

    // ==================== 数据类特性测试 ====================

    @Nested
    @DisplayName("数据类特性测试")
    inner class DataClassFeaturesTests {

        @Test
        @DisplayName("equals - 相同属性应该相等")
        fun `equals - 相同属性应该相等`() {
            val time = System.currentTimeMillis()
            val status1 = GeneratorStatus(
                available = true,
                latencyMs = 100,
                quotaRemaining = 50,
                quotaTotal = 100,
                lastError = null,
                lastCheckTime = time
            )
            val status2 = GeneratorStatus(
                available = true,
                latencyMs = 100,
                quotaRemaining = 50,
                quotaTotal = 100,
                lastError = null,
                lastCheckTime = time
            )

            assertThat(status1).isEqualTo(status2)
            assertThat(status1.hashCode()).isEqualTo(status2.hashCode())
        }

        @Test
        @DisplayName("equals - 不同属性应该不相等")
        fun `equals - 不同属性应该不相等`() {
            val status1 = GeneratorStatus(
                available = true,
                latencyMs = 100
            )
            val status2 = GeneratorStatus(
                available = false,
                latencyMs = 100
            )

            assertThat(status1).isNotEqualTo(status2)
        }

        @Test
        @DisplayName("copy - 应该创建独立副本")
        fun `copy - 应该创建独立副本`() {
            val original = GeneratorStatus(
                available = true,
                latencyMs = 100,
                quotaRemaining = 50
            )
            val copied = original.copy(
                available = false
            )

            assertThat(original.available).isTrue()
            assertThat(copied.available).isFalse()
            assertThat(copied.latencyMs).isEqualTo(original.latencyMs)
            assertThat(copied.quotaRemaining).isEqualTo(original.quotaRemaining)
        }
    }

    // ==================== 状态判断测试 ====================

    @Nested
    @DisplayName("状态判断测试")
    inner class StatusJudgmentTests {

        @Test
        @DisplayName("isHealthy - 可用且无错误应该为健康")
        fun `isHealthy - 可用且无错误应该为健康`() {
            val status = GeneratorStatus(
                available = true,
                lastError = null
            )

            val isHealthy = status.available && status.lastError == null

            assertThat(isHealthy).isTrue()
        }

        @Test
        @DisplayName("isHealthy - 不可用应该为不健康")
        fun `isHealthy - 不可用应该为不健康`() {
            val status = GeneratorStatus(
                available = false,
                lastError = null
            )

            val isHealthy = status.available && status.lastError == null

            assertThat(isHealthy).isFalse()
        }

        @Test
        @DisplayName("isHealthy - 有错误应该为不健康")
        fun `isHealthy - 有错误应该为不健康`() {
            val status = GeneratorStatus(
                available = true,
                lastError = "连接失败"
            )

            val isHealthy = status.available && status.lastError == null

            assertThat(isHealthy).isFalse()
        }

        @Test
        @DisplayName("isSlow - 延迟超过 1 秒应该为慢")
        fun `isSlow - 延迟超过 1 秒应该为慢`() {
            val status = GeneratorStatus(
                available = true,
                latencyMs = 1500
            )

            val isSlow = (status.latencyMs ?: 0) > 1000

            assertThat(isSlow).isTrue()
        }

        @Test
        @DisplayName("isSlow - 延迟低于 1 秒应该为正常")
        fun `isSlow - 延迟低于 1 秒应该为正常`() {
            val status = GeneratorStatus(
                available = true,
                latencyMs = 500
            )

            val isSlow = (status.latencyMs ?: 0) > 1000

            assertThat(isSlow).isFalse()
        }

        @Test
        @DisplayName("isSlow - 无延迟数据应该为正常")
        fun `isSlow - 无延迟数据应该为正常`() {
            val status = GeneratorStatus(
                available = true,
                latencyMs = null
            )

            val isSlow = (status.latencyMs ?: 0) > 1000

            assertThat(isSlow).isFalse()
        }

        @Test
        @DisplayName("isQuotaLow - 剩余配额低于 20% 应该为低")
        fun `isQuotaLow - 剩余配额低于 20 百分比 应该为低`() {
            val status = GeneratorStatus(
                available = true,
                quotaRemaining = 15,
                quotaTotal = 100
            )

            val isQuotaLow = status.quotaTotal?.let { total ->
                status.quotaRemaining?.let { remaining ->
                    (remaining.toDouble() / total) < 0.2
                }
            } ?: false

            assertThat(isQuotaLow).isTrue()
        }

        @Test
        @DisplayName("isQuotaLow - 剩余配额高于 20% 应该为正常")
        fun `isQuotaLow - 剩余配额高于 20 百分比 应该为正常`() {
            val status = GeneratorStatus(
                available = true,
                quotaRemaining = 30,
                quotaTotal = 100
            )

            val isQuotaLow = status.quotaTotal?.let { total ->
                status.quotaRemaining?.let { remaining ->
                    (remaining.toDouble() / total) < 0.2
                }
            } ?: false

            assertThat(isQuotaLow).isFalse()
        }
    }
}
