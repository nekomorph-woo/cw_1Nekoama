package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ConnectivityStatus
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig
import com.cw2.nekoama.infrastructure.network.proxy.ProxyConnectionTester
import com.cw2.nekoama.infrastructure.network.proxy.ProxyDetector
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 网络测试服务实现
 *
 * 职责：
 * - 测试 API 连通性
 * - 返回详细的连接状态和响应时间
 * - 生成排查指南（失败时）
 */
@Service(Service.Level.PROJECT)
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

            // 获取设置以填充 endpoint 和 model
            val settings = NekoamaSettings.getInstance()

            ConnectivityStatus(
                isConnected = testResult.success,
                responseTime = testResult.responseTime,
                message = testResult.message,
                endpoint = testUrl,
                model = settings.model,
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
