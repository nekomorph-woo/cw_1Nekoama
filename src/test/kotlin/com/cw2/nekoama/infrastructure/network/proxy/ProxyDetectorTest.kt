package com.cw2.nekoama.infrastructure.network.proxy

import io.mockk.every
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 代理检测器测试
 *
 * 验证系统代理检测、环境变量代理检测、评分算法等功能
 */
@DisplayName("代理检测器测试")
class ProxyDetectorTest {

    @BeforeEach
    fun setup() {
        // Mock System.getenv
        mockkStatic(System::class)
    }

    @AfterEach
    fun tearDown() {
        // 清理 Mock
        io.mockk.unmockkAll()
    }

    // ==================== 系统代理检测测试 ====================

    @Nested
    @DisplayName("系统代理检测测试")
    inner class SystemProxyDetectionTests {

        @Test
        @DisplayName("检测系统代理 - 无代理时应该返回直连配置")
        fun `检测系统代理 - 无代理时应该返回直连配置`() {
            // 执行测试（不设置系统代理）
            val config = ProxyDetector.detectSystemProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.DIRECT)
            assertThat(config.host).isNull()
            assertThat(config.port).isEqualTo(0)
        }
    }

    // ==================== 环境变量代理测试 ====================

    @Nested
    @DisplayName("环境变量代理检测测试")
    inner class EnvironmentProxyDetectionTests {

        @Test
        @DisplayName("检测环境变量 - HTTP_PROXY 应该被解析")
        fun `检测环境变量 - HTTP_PROXY 应该被解析`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns "http://proxy.example.com:8080"
            every { System.getenv("HTTPS_PROXY") } returns null
            every { System.getenv("http_proxy") } returns null
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.HTTP)
            assertThat(config.host).isEqualTo("proxy.example.com")
            assertThat(config.port).isEqualTo(8080)
        }

        @Test
        @DisplayName("检测环境变量 - HTTPS_PROXY 应该被解析")
        fun `检测环境变量 - HTTPS_PROXY 应该被解析`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns null
            every { System.getenv("HTTPS_PROXY") } returns "https://proxy.example.com:8443"
            every { System.getenv("http_proxy") } returns null
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.HTTPS)
            assertThat(config.host).isEqualTo("proxy.example.com")
            assertThat(config.port).isEqualTo(8443)
        }

        @Test
        @DisplayName("检测环境变量 - 小写 http_proxy 应该被解析")
        fun `检测环境变量 - 小写 http_proxy 应该被解析`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns null
            every { System.getenv("HTTPS_PROXY") } returns null
            every { System.getenv("http_proxy") } returns "http://proxy.example.com:8080"
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.HTTP)
            assertThat(config.host).isEqualTo("proxy.example.com")
        }

        @Test
        @DisplayName("检测环境变量 - 无代理时应该返回直连")
        fun `检测环境变量 - 无代理时应该返回直连`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns null
            every { System.getenv("HTTPS_PROXY") } returns null
            every { System.getenv("http_proxy") } returns null
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.DIRECT)
        }

        @Test
        @DisplayName("检测环境变量 - HTTPS_PROXY 应该优先于 HTTP_PROXY")
        fun `检测环境变量 - HTTPS_PROXY 应该优先于 HTTP_PROXY`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns "http://proxy1.example.com:8080"
            every { System.getenv("HTTPS_PROXY") } returns "https://proxy2.example.com:8443"
            every { System.getenv("http_proxy") } returns null
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.host).isEqualTo("proxy2.example.com")
            assertThat(config.port).isEqualTo(8443)
        }
    }

    // ==================== 代理字符串解析测试 ====================

    @Nested
    @DisplayName("代理字符串解析测试")
    inner class ProxyStringParsingTests {

        @Test
        @DisplayName("解析代理字符串 - 无环境变量应该返回直连")
        fun `解析代理字符串 - 无环境变量应该返回直连`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns null
            every { System.getenv("HTTPS_PROXY") } returns null
            every { System.getenv("http_proxy") } returns null
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果（无环境变量时返回直连）
            assertThat(config.type).isEqualTo(ProxyType.DIRECT)
        }

        @Test
        @DisplayName("解析代理字符串 - 带 SOCKS5 协议应该成功")
        fun `解析代理字符串 - 带 SOCKS5 协议应该成功`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns null
            every { System.getenv("HTTPS_PROXY") } returns null
            every { System.getenv("http_proxy") } returns null
            every { System.getenv("https_proxy") } returns "socks5://proxy.example.com:1080"

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.SOCKS)
            assertThat(config.host).isEqualTo("proxy.example.com")
            assertThat(config.port).isEqualTo(1080)
        }

        @Test
        @DisplayName("解析代理字符串 - 带认证格式应该成功")
        fun `解析代理字符串 - 带认证格式应该成功`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns "http://user:pass@proxy.example.com:8080"
            every { System.getenv("HTTPS_PROXY") } returns null
            every { System.getenv("http_proxy") } returns null
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.HTTP)
            assertThat(config.host).isEqualTo("proxy.example.com")
            assertThat(config.port).isEqualTo(8080)
            assertThat(config.username).isEqualTo("user")
            assertThat(config.password).isEqualTo("pass")
        }

        @Test
        @DisplayName("解析代理字符串 - 简单 host 端口 格式应该成功")
        fun `解析代理字符串 - 简单 host 端口 格式应该成功`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns null
            every { System.getenv("HTTPS_PROXY") } returns null
            every { System.getenv("http_proxy") } returns "proxy.example.com:8080"
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.HTTP) // 默认 HTTP
            assertThat(config.host).isEqualTo("proxy.example.com")
            assertThat(config.port).isEqualTo(8080)
        }
    }

    // ==================== 代理状态测试 ====================

    @Nested
    @DisplayName("代理状态信息测试")
    inner class ProxyStatusTests {

        @Test
        @DisplayName("获取代理状态 - 直连应该返回正确描述")
        fun `获取代理状态 - 直连应该返回正确描述`() {
            // 准备测试数据
            val config = ProxyConfig.Companion.direct()

            // 执行测试
            val status = ProxyDetector.getProxyStatus(config)

            // 验证结果
            assertThat(status).isEqualTo("直连")
        }

        @Test
        @DisplayName("获取代理状态 - HTTP 代理应该返回正确描述")
        fun `获取代理状态 - HTTP 代理应该返回正确描述`() {
            // 准备测试数据
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                username = null,
                password = null
            )

            // 执行测试
            val status = ProxyDetector.getProxyStatus(config)

            // 验证结果
            assertThat(status).contains("HTTP")
            assertThat(status).contains("proxy.example.com")
            assertThat(status).contains("8080")
            assertThat(status).contains("无认证")
        }

        @Test
        @DisplayName("获取代理状态 - 带认证的代理应该返回正确描述")
        fun `获取代理状态 - 带认证的代理应该返回正确描述`() {
            // 准备测试数据
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                username = "user",
                password = "pass"
            )

            // 执行测试
            val status = ProxyDetector.getProxyStatus(config)

            // 验证结果
            assertThat(status).contains("已认证")
        }
    }

    // ==================== 边界情况测试 ====================

    @Nested
    @DisplayName("边界情况测试")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("解析代理字符串 - 空字符串应该返回直连")
        fun `解析代理字符串 - 空字符串应该返回直连`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns ""
            every { System.getenv("HTTPS_PROXY") } returns null
            every { System.getenv("http_proxy") } returns null
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.DIRECT)
        }

        @Test
        @DisplayName("解析代理字符串 - 无效格式应该返回直连")
        fun `解析代理字符串 - 无效格式应该返回直连`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns "invalid-proxy-string"
            every { System.getenv("HTTPS_PROXY") } returns null
            every { System.getenv("http_proxy") } returns null
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.DIRECT)
        }

        @Test
        @DisplayName("解析代理字符串 - 缺少端口号应该使用默认端口")
        fun `解析代理字符串 - 缺少端口号应该使用默认端口`() {
            // 准备测试数据
            every { System.getenv("HTTP_PROXY") } returns "http://proxy.example.com"
            every { System.getenv("HTTPS_PROXY") } returns null
            every { System.getenv("http_proxy") } returns null
            every { System.getenv("https_proxy") } returns null

            // 执行测试
            val config = ProxyDetector.detectEnvironmentProxy()

            // 验证结果
            assertThat(config.type).isEqualTo(ProxyType.HTTP)
            assertThat(config.host).isEqualTo("proxy.example.com")
            assertThat(config.port).isEqualTo(8080) // 默认端口
        }
    }
}
