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
import org.assertj.core.api.Assertions.assertThat

@DisplayName("NetworkTestServiceImpl - 网络测试服务测试")
class NetworkTestServiceImplTest {

    private lateinit var service: NetworkTestServiceImpl
    private lateinit var mockProject: com.intellij.openapi.project.Project

    @BeforeEach
    fun setUp() {
        mockProject = mockk(relaxed = true)
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
        val mockProxyConfig = mockk<com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig>(relaxed = true)

        every { ProxyDetector.detectSystemProxy(testUrl) } returns mockProxyConfig
        coEvery { ProxyConnectionTester.testProxyConnection(mockProxyConfig, testUrl) } returns
            ProxyConnectionTester.ProxyTestResult(
                success = true,
                responseTime = 100,
                message = "Connection successful"
            )

        // When
        val result = service.testConnectivity(testUrl)

        // Then
        assertThat(result.isConnected).isTrue()
        assertThat(result.responseTime).isEqualTo(100)
        assertThat(result.message).isEqualTo("Connection successful")
    }

    @Test
    @DisplayName("测试连通性 - 失败时应该生成排查指南")
    fun `测试连通性 - 失败时应该生成排查指南`() = runTest {
        // Given
        val testUrl = "https://api.openai.com"
        val mockProxyConfig = mockk<com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig>(relaxed = true)

        every { ProxyDetector.detectSystemProxy(testUrl) } returns mockProxyConfig
        coEvery { ProxyConnectionTester.testProxyConnection(mockProxyConfig, testUrl) } returns
            ProxyConnectionTester.ProxyTestResult(
                success = false,
                responseTime = -1,
                message = "Connection refused",
                statusCode = -1
            )

        // When
        val result = service.testConnectivity(testUrl)

        // Then
        assertThat(result.isConnected).isFalse()
        assertThat(result.troubleshootingGuide).isNotNull()
        assertThat(result.troubleshootingGuide).isNotEmpty()
    }

    @Test
    @DisplayName("测试连通性 - 407 错误应该生成认证指南")
    fun `测试连通性 - 407 错误应该生成认证指南`() = runTest {
        // Given
        val testUrl = "https://api.openai.com"
        val mockProxyConfig = mockk<com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig>(relaxed = true)

        every { ProxyDetector.detectSystemProxy(testUrl) } returns mockProxyConfig
        coEvery { ProxyConnectionTester.testProxyConnection(mockProxyConfig, testUrl) } returns
            ProxyConnectionTester.ProxyTestResult(
                success = false,
                responseTime = -1,
                message = "Proxy Authentication Required",
                statusCode = 407
            )

        // When
        val result = service.testConnectivity(testUrl)

        // Then
        assertThat(result.isConnected).isFalse()
        assertThat(result.troubleshootingGuide).isNotNull()
        assertThat(result.troubleshootingGuide!!.any { it.contains("407") || it.contains("认证") }).isTrue()
    }

    @Test
    @DisplayName("测试连通性 - 超时错误应该生成超时指南")
    fun `测试连通性 - 超时错误应该生成超时指南`() = runTest {
        // Given
        val testUrl = "https://api.openai.com"
        val mockProxyConfig = mockk<com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig>(relaxed = true)

        every { ProxyDetector.detectSystemProxy(testUrl) } returns mockProxyConfig
        coEvery { ProxyConnectionTester.testProxyConnection(mockProxyConfig, testUrl) } returns
            ProxyConnectionTester.ProxyTestResult(
                success = false,
                responseTime = -1,
                message = "Connection timeout",
                statusCode = -1
            )

        // When
        val result = service.testConnectivity(testUrl)

        // Then
        assertThat(result.isConnected).isFalse()
        assertThat(result.troubleshootingGuide).isNotNull()
        assertThat(result.troubleshootingGuide!!.any { it.contains("超时") || it.contains("timeout", ignoreCase = true) }).isTrue()
    }

    @Test
    @DisplayName("测试连通性 - 应该正确传递 endpoint")
    fun `测试连通性 - 应该正确传递 endpoint`() = runTest {
        // Given
        val mockProxyConfig = mockk<com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig>(relaxed = true)
        val testUrl = "https://api.example.com"

        every { ProxyDetector.detectSystemProxy(testUrl) } returns mockProxyConfig
        coEvery { ProxyConnectionTester.testProxyConnection(mockProxyConfig, testUrl) } returns
            ProxyConnectionTester.ProxyTestResult(
                success = true,
                responseTime = 50,
                message = "Connected"
            )

        // When
        val result = service.testConnectivity(testUrl)

        // Then
        assertThat(result.isConnected).isTrue()
        coVerify { ProxyConnectionTester.testProxyConnection(mockProxyConfig, testUrl) }
    }
}
