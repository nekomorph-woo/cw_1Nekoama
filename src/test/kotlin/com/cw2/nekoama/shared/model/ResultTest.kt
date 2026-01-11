package com.cw2.nekoama.shared.model

import com.cw2.nekoama.shared.exception.NekoamaError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * NekoamaResult 类测试
 *
 * 验证通用结果类型系统的所有功能
 */
@DisplayName("NekoamaResult<T> 通用结果类型测试")
class ResultTest {

    // ==================== 成功状态测试 ====================

    @Nested
    @DisplayName("成功状态测试")
    inner class SuccessTests {

        @Test
        @DisplayName("应该创建成功结果 - 返回正确值")
        fun `应该创建成功结果 - 返回正确值`() {
            val result = NekoamaResult.success("test value")

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("test value")
        }

        @Test
        @DisplayName("应该创建成功结果 - isSuccess 为 true")
        fun `应该创建成功结果 - isSuccess 为 true`() {
            val result = NekoamaResult.success("value")

            assertThat(result.isSuccess).isTrue()
        }

        @Test
        @DisplayName("应该创建成功结果 - isError 为 false")
        fun `应该创建成功结果 - isError 为 false`() {
            val result = NekoamaResult.success("value")

            assertThat(result.isError).isFalse()
        }
    }

    // ==================== 错误状态测试 ====================

    @Nested
    @DisplayName("错误状态测试")
    inner class ErrorTests {

        @Test
        @DisplayName("应该创建错误结果 - 返回错误信息")
        fun `应该创建错误结果 - 返回错误信息`() {
            val error = NekoamaError.Unknown("test error")
            val result = NekoamaResult.error<String>(error)

            assertThat(result.isError).isTrue()
            assertThat(result.errorOrNull()).isEqualTo(error)
        }

        @Test
        @DisplayName("应该创建错误结果 - isError 为 true")
        fun `应该创建错误结果 - isError 为 true`() {
            val error = NekoamaError.Unknown("test error")
            val result = NekoamaResult.error<String>(error)

            assertThat(result.isError).isTrue()
        }

        @Test
        @DisplayName("应该创建错误结果 - isSuccess 为 false")
        fun `应该创建错误结果 - isSuccess 为 false`() {
            val error = NekoamaError.Unknown("test error")
            val result = NekoamaResult.error<String>(error)

            assertThat(result.isSuccess).isFalse()
        }
    }

    // ==================== 高阶函数测试 ====================

    @Nested
    @DisplayName("高阶函数测试")
    inner class HigherOrderFunctionTests {

        @Test
        @DisplayName("map 方法 - 成功时应该转换值")
        fun `map 方法 - 成功时应该转换值`() {
            val result = NekoamaResult.success(5)
            val mapped = result.map { it * 2 }

            assertThat(mapped.getOrNull()).isEqualTo(10)
        }

        @Test
        @DisplayName("map 方法 - 错误时应该保持错误")
        fun `map 方法 - 错误时应该保持错误`() {
            val error = NekoamaError.Unknown("test error")
            val result = NekoamaResult.error<Int>(error)
            val mapped = result.map { it * 2 }

            assertThat(mapped.isError).isTrue()
            assertThat(mapped.errorOrNull()).isEqualTo(error)
        }

        @Test
        @DisplayName("flatMap 方法 - 成功时应该链式调用")
        fun `flatMap 方法 - 成功时应该链式调用`() {
            val result = NekoamaResult.success(5)
            val flatMapped = result.flatMap { value ->
                if (value > 0) NekoamaResult.success(value * 2)
                else NekoamaResult.error(NekoamaError.Unknown("negative value"))
            }

            assertThat(flatMapped.getOrNull()).isEqualTo(10)
        }

        @Test
        @DisplayName("flatMap 方法 - 错误时应该短路")
        fun `flatMap 方法 - 错误时应该短路`() {
            val error = NekoamaError.Unknown("initial error")
            val result = NekoamaResult.error<Int>(error)
            val flatMapped = result.flatMap { value ->
                NekoamaResult.success(value * 2)
            }

            assertThat(flatMapped.isError).isTrue()
            assertThat(flatMapped.errorOrNull()).isEqualTo(error)
        }

        @Test
        @DisplayName("fold 方法 - 成功时调用 onSuccess")
        fun `fold 方法 - 成功时调用 onSuccess`() {
            val result = NekoamaResult.success(42)
            val folded = result.fold(
                onSuccess = { "success: $it" },
                onError = { "error: ${it.message}" }
            )

            assertThat(folded).isEqualTo("success: 42")
        }

        @Test
        @DisplayName("fold 方法 - 错误时调用 onError")
        fun `fold 方法 - 错误时调用 onError`() {
            val error = NekoamaError.Unknown("test error")
            val result = NekoamaResult.error<Int>(error)
            val folded = result.fold(
                onSuccess = { "success: $it" },
                onError = { "error: ${it.message}" }
            )

            assertThat(folded).isEqualTo("error: test error")
        }
    }

    // ==================== 过滤与检查测试 ====================

    @Nested
    @DisplayName("过滤与检查测试")
    inner class FilterAndCheckTests {

        @Test
        @DisplayName("filter 方法 - 条件满足时返回成功")
        fun `filter 方法 - 条件满足时返回成功`() {
            val result = NekoamaResult.success(10)
            val filtered = result.filter(
                predicate = { it > 5 },
                error = NekoamaError.Unknown("value too small")
            )

            assertThat(filtered.isSuccess).isTrue()
        }

        @Test
        @DisplayName("filter 方法 - 条件不满足时返回错误")
        fun `filter 方法 - 条件不满足时返回错误`() {
            val result = NekoamaResult.success(3)
            val filtered = result.filter(
                predicate = { it > 5 },
                error = NekoamaError.Unknown("value too small")
            )

            assertThat(filtered.isError).isTrue()
        }

        @Test
        @DisplayName("getOrNull - 成功时返回值")
        fun `getOrNull - 成功时返回值`() {
            val result = NekoamaResult.success("test")

            assertThat(result.getOrNull()).isEqualTo("test")
        }

        @Test
        @DisplayName("getOrNull - 错误时返回 null")
        fun `getOrNull - 错误时返回 null`() {
            val result = NekoamaResult.error<String>(NekoamaError.Unknown("error"))

            assertThat(result.getOrNull()).isNull()
        }

        @Test
        @DisplayName("errorOrNull - 成功时返回 null")
        fun `errorOrNull - 成功时返回 null`() {
            val result = NekoamaResult.success("test")

            assertThat(result.errorOrNull()).isNull()
        }

        @Test
        @DisplayName("errorOrNull - 错误时返回错误")
        fun `errorOrNull - 错误时返回错误`() {
            val error = NekoamaError.Unknown("error")
            val result = NekoamaResult.error<String>(error)

            assertThat(result.errorOrNull()).isEqualTo(error)
        }
    }

    // ==================== 副作用操作测试 ====================

    @Nested
    @DisplayName("副作用操作测试")
    inner class SideEffectTests {

        @Test
        @DisplayName("onSuccess - 仅在成功时执行")
        fun `onSuccess - 仅在成功时执行`() {
            var executed = false
            val result = NekoamaResult.success("test")

            result.onSuccess { executed = true }

            assertThat(executed).isTrue()
        }

        @Test
        @DisplayName("onSuccess - 错误时不执行")
        fun `onSuccess - 错误时不执行`() {
            var executed = false
            val result = NekoamaResult.error<String>(NekoamaError.Unknown("error"))

            result.onSuccess { executed = true }

            assertThat(executed).isFalse()
        }

        @Test
        @DisplayName("onError - 仅在错误时执行")
        fun `onError - 仅在错误时执行`() {
            var executed = false
            val result = NekoamaResult.error<String>(NekoamaError.Unknown("error"))

            result.onError { executed = true }

            assertThat(executed).isTrue()
        }

        @Test
        @DisplayName("onError - 成功时不执行")
        fun `onError - 成功时不执行`() {
            var executed = false
            val result = NekoamaResult.success("test")

            result.onError { executed = true }

            assertThat(executed).isFalse()
        }

        @Test
        @DisplayName("onSuccess - 应该返回原结果")
        fun `onSuccess - 应该返回原结果`() {
            val result = NekoamaResult.success("test")
            val returned = result.onSuccess { }

            assertThat(returned).isSameAs(result)
        }

        @Test
        @DisplayName("onError - 应该返回原结果")
        fun `onError - 应该返回原结果`() {
            val result = NekoamaResult.success("test")
            val returned = result.onError { }

            assertThat(returned).isSameAs(result)
        }
    }

    // ==================== 伴生对象方法测试 ====================

    @Nested
    @DisplayName("伴生对象方法测试")
    inner class CompanionObjectTests {

        @Test
        @DisplayName("catching - 捕获异常返回错误")
        fun `catching - 捕获异常返回错误`() {
            val result = NekoamaResult.catching {
                throw RuntimeException("test exception")
                "should not reach here"
            }

            assertThat(result.isError).isTrue()
            assertThat(result.errorOrNull()).isInstanceOf(NekoamaError.Unknown::class.java)
        }

        @Test
        @DisplayName("catching - 无异常返回成功")
        fun `catching - 无异常返回成功`() {
            val result = NekoamaResult.catching { "success" }

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("success")
        }

        @Test
        @DisplayName("fromNullable - 非空值返回成功")
        fun `fromNullable - 非空值返回成功`() {
            val result = NekoamaResult.fromNullable("value", NekoamaError.Unknown("null value"))

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("value")
        }

        @Test
        @DisplayName("fromNullable - 空值返回错误")
        fun `fromNullable - 空值返回错误`() {
            val error = NekoamaError.Unknown("null value")
            val result = NekoamaResult.fromNullable<String?>(null, error)

            assertThat(result.isError).isTrue()
            assertThat(result.errorOrNull()).isEqualTo(error)
        }

        @Test
        @DisplayName("combine - 全部成功时返回成功")
        fun `combine - 全部成功时返回成功`() {
            val results = listOf(
                NekoamaResult.success(1),
                NekoamaResult.success(2),
                NekoamaResult.success(3)
            )
            val combined = NekoamaResult.combine(results)

            assertThat(combined.isSuccess).isTrue()
            assertThat(combined.getOrNull()).isEqualTo(listOf(1, 2, 3))
        }

        @Test
        @DisplayName("combine - 任一失败时返回第一个错误")
        fun `combine - 任一失败时返回第一个错误`() {
            val error1 = NekoamaError.Unknown("error 1")
            val error2 = NekoamaError.Unknown("error 2")
            val results = listOf(
                NekoamaResult.success(1),
                NekoamaResult.error<Int>(error1),
                NekoamaResult.error<Int>(error2)
            )
            val combined = NekoamaResult.combine(results)

            assertThat(combined.isError).isTrue()
            assertThat(combined.errorOrNull()).isEqualTo(error1)
        }

        @Test
        @DisplayName("combine 两个结果 - 全部成功时返回成功")
        fun `combine 两个结果 - 全部成功时返回成功`() {
            val result1 = NekoamaResult.success(10)
            val result2 = NekoamaResult.success(20)
            val combined = NekoamaResult.combine(result1, result2) { a, b -> a + b }

            assertThat(combined.isSuccess).isTrue()
            assertThat(combined.getOrNull()).isEqualTo(30)
        }

        @Test
        @DisplayName("combine 两个结果 - 第一个失败时返回错误")
        fun `combine 两个结果 - 第一个失败时返回错误`() {
            val error = NekoamaError.Unknown("error")
            val result1 = NekoamaResult.error<Int>(error)
            val result2 = NekoamaResult.success(20)
            val combined = NekoamaResult.combine(result1, result2) { a, b -> a + b }

            assertThat(combined.isError).isTrue()
            assertThat(combined.errorOrNull()).isEqualTo(error)
        }

        @Test
        @DisplayName("combine 三个结果 - 全部成功时返回成功")
        fun `combine 三个结果 - 全部成功时返回成功`() {
            val result1 = NekoamaResult.success(10)
            val result2 = NekoamaResult.success(20)
            val result3 = NekoamaResult.success(30)
            val combined = NekoamaResult.combine(result1, result2, result3) { a, b, c -> a + b + c }

            assertThat(combined.isSuccess).isTrue()
            assertThat(combined.getOrNull()).isEqualTo(60)
        }

        @Test
        @DisplayName("combine 三个结果 - 中间失败时返回错误")
        fun `combine 三个结果 - 中间失败时返回错误`() {
            val error = NekoamaError.Unknown("error")
            val result1 = NekoamaResult.success(10)
            val result2 = NekoamaResult.error<Int>(error)
            val result3 = NekoamaResult.success(30)
            val combined = NekoamaResult.combine(result1, result2, result3) { a, b, c -> a + b + c }

            assertThat(combined.isError).isTrue()
            assertThat(combined.errorOrNull()).isEqualTo(error)
        }
    }

    // ==================== 扩展函数测试 ====================

    @Nested
    @DisplayName("扩展函数测试")
    inner class ExtensionFunctionTests {

        @Test
        @DisplayName("toResult 扩展 - 非空值返回成功")
        fun `toResult 扩展 - 非空值返回成功`() {
            val result = "value".toResult(NekoamaError.Unknown("null"))

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("value")
        }

        @Test
        @DisplayName("toResult 扩展 - 空值返回错误")
        fun `toResult 扩展 - 空值返回错误`() {
            val error = NekoamaError.Unknown("null")
            val result: NekoamaResult<String?> = null.toResult(error)

            assertThat(result.isError).isTrue()
            assertThat(result.errorOrNull()).isEqualTo(error)
        }

        @Test
        @DisplayName("safeCall 扩展 - 捕获异常返回错误")
        fun `safeCall 扩展 - 捕获异常返回错误`() {
            val result = safeCall<String> {
                throw RuntimeException("test exception")
            }

            assertThat(result.isError).isTrue()
        }

        @Test
        @DisplayName("safeCall 扩展 - 无异常返回成功")
        fun `safeCall 扩展 - 无异常返回成功`() {
            val result = safeCall { "success" }

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("success")
        }
    }
}
