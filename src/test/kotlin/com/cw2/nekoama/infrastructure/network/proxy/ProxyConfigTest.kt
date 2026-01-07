package com.cw2.nekoama.infrastructure.network.proxy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 代理配置测试
 *
 * 验证 ProxyConfig 数据类的各种功能
 */
@DisplayName("代理配置测试")
class ProxyConfigTest {

    // ==================== 默认值测试 ====================

    @Nested
    @DisplayName("默认值测试")
    inner class DefaultValueTests {

        @Test
        @DisplayName("默认配置 - 应该是 DIRECT 类型")
        fun `默认配置 - 应该是 DIRECT 类型`() {
            val config = ProxyConfig()

            assertThat(config.type).isEqualTo(ProxyType.DIRECT)
        }

        @Test
        @DisplayName("默认配置 - bypassLocal 应该为 true")
        fun `默认配置 - bypassLocal 应该为 true`() {
            val config = ProxyConfig()

            assertThat(config.bypassLocal).isTrue()
        }

        @Test
        @DisplayName("默认配置 - bypassHosts 应该为空列表")
        fun `默认配置 - bypassHosts 应该为空列表`() {
            val config = ProxyConfig()

            assertThat(config.bypassHosts).isEmpty()
        }
    }

    // ==================== 验证测试 ====================

    @Nested
    @DisplayName("配置验证测试")
    inner class ValidationTests {

        @Test
        @DisplayName("isValid - DIRECT 类型应该始终有效")
        fun `isValid - DIRECT 类型应该始终有效`() {
            val config = ProxyConfig(type = ProxyType.DIRECT)

            assertThat(config.isValid()).isTrue()
        }

        @Test
        @DisplayName("isValid - HTTP 代理带有效配置应该有效")
        fun `isValid - HTTP 代理带有效配置应该有效`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080
            )

            assertThat(config.isValid()).isTrue()
        }

        @Test
        @DisplayName("isValid - 缺少主机应该无效")
        fun `isValid - 缺少主机应该无效`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = null,
                port = 8080
            )

            assertThat(config.isValid()).isFalse()
        }

        @Test
        @DisplayName("isValid - 空主机应该无效")
        fun `isValid - 空主机应该无效`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "   ",
                port = 8080
            )

            assertThat(config.isValid()).isFalse()
        }

        @Test
        @DisplayName("isValid - 端口为 null 应该无效")
        fun `isValid - 端口为 null 应该无效`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = null
            )

            assertThat(config.isValid()).isFalse()
        }

        @Test
        @DisplayName("isValid - 端口超出范围应该无效")
        fun `isValid - 端口超出范围应该无效`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 70000
            )

            assertThat(config.isValid()).isFalse()
        }

        @Test
        @DisplayName("isValid - 端口为 0 应该无效")
        fun `isValid - 端口为 0 应该无效`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 0
            )

            assertThat(config.isValid()).isFalse()
        }

        @Test
        @DisplayName("isValid - HTTPS 代理应该遵循相同规则")
        fun `isValid - HTTPS 代理应该遵循相同规则`() {
            val config = ProxyConfig(
                type = ProxyType.HTTPS,
                host = "secure-proxy.example.com",
                port = 443
            )

            assertThat(config.isValid()).isTrue()
        }

        @Test
        @DisplayName("isValid - SOCKS 代理应该遵循相同规则")
        fun `isValid - SOCKS 代理应该遵循相同规则`() {
            val config = ProxyConfig(
                type = ProxyType.SOCKS,
                host = "socks-proxy.example.com",
                port = 1080
            )

            assertThat(config.isValid()).isTrue()
        }
    }

    // ==================== Java Proxy 转换测试 ====================

    @Nested
    @DisplayName("Java Proxy 转换测试")
    inner class JavaProxyConversionTests {

        @Test
        @DisplayName("toJavaProxy - DIRECT 应该返回 NO_PROXY")
        fun `toJavaProxy - DIRECT 应该返回 NO_PROXY`() {
            val config = ProxyConfig(type = ProxyType.DIRECT)

            val proxy = config.toJavaProxy()

            assertThat(proxy).isEqualTo(java.net.Proxy.NO_PROXY)
        }

        @Test
        @DisplayName("toJavaProxy - HTTP 应该返回 HTTP 代理")
        fun `toJavaProxy - HTTP 应该返回 HTTP 代理`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080
            )

            val proxy = config.toJavaProxy()

            assertThat(proxy.type()).isEqualTo(java.net.Proxy.Type.HTTP)
            assertThat(proxy.address()).isNotNull()
        }

        @Test
        @DisplayName("toJavaProxy - HTTPS 应该返回 HTTP 代理（HTTPS 使用 HTTP 类型）")
        fun `toJavaProxy - HTTPS 应该返回 HTTP 代理`() {
            val config = ProxyConfig(
                type = ProxyType.HTTPS,
                host = "secure-proxy.example.com",
                port = 443
            )

            val proxy = config.toJavaProxy()

            assertThat(proxy.type()).isEqualTo(java.net.Proxy.Type.HTTP)
        }

        @Test
        @DisplayName("toJavaProxy - SOCKS 应该返回 SOCKS 代理")
        fun `toJavaProxy - SOCKS 应该返回 SOCKS 代理`() {
            val config = ProxyConfig(
                type = ProxyType.SOCKS,
                host = "socks.example.com",
                port = 1080
            )

            val proxy = config.toJavaProxy()

            assertThat(proxy.type()).isEqualTo(java.net.Proxy.Type.SOCKS)
        }
    }

    // ==================== 绕过检查测试 ====================

    @Nested
    @DisplayName("绕过检查测试")
    inner class BypassTests {

        @Test
        @DisplayName("shouldBypass - localhost 应该绕过")
        fun `shouldBypass - localhost 应该绕过`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                bypassLocal = true
            )

            assertThat(config.shouldBypass("localhost")).isTrue()
        }

        @Test
        @DisplayName("shouldBypass - 127.0.0.1 应该绕过")
        fun `shouldBypass - 127 端口 0 端口 0 端口 1 应该绕过`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                bypassLocal = true
            )

            assertThat(config.shouldBypass("127.0.0.1")).isTrue()
        }

        @Test
        @DisplayName("shouldBypass - 192.168.x.x 应该绕过")
        fun `shouldBypass - 192 端口 168 x x 应该绕过`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                bypassLocal = true
            )

            assertThat(config.shouldBypass("192.168.1.1")).isTrue()
        }

        @Test
        @DisplayName("shouldBypass - 10.x.x.x 应该绕过")
        fun `shouldBypass - 10 x x x 应该绕过`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                bypassLocal = true
            )

            assertThat(config.shouldBypass("10.0.0.1")).isTrue()
        }

        @Test
        @DisplayName("shouldBypass - 172.16.x.x 应该绕过")
        fun `shouldBypass - 172 端口 16 x x 应该绕过`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                bypassLocal = true
            )

            assertThat(config.shouldBypass("172.16.0.1")).isTrue()
        }

        @Test
        @DisplayName("shouldBypass - .local 域名应该绕过")
        fun `shouldBypass - local 域名应该绕过`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                bypassLocal = true
            )

            assertThat(config.shouldBypass("mymachine.local")).isTrue()
        }

        @Test
        @DisplayName("shouldBypass - bypassLocal 为 false 时不绕过")
        fun `shouldBypass - bypassLocal 为 false 时不绕过`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                bypassLocal = false
            )

            assertThat(config.shouldBypass("localhost")).isFalse()
        }

        @Test
        @DisplayName("shouldBypass - 匹配 bypassHosts 列表")
        fun `shouldBypass - 匹配 bypassHosts 列表`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                bypassHosts = listOf("internal.com", "localhost")
            )

            assertThat(config.shouldBypass("internal.com")).isTrue()
        }

        @Test
        @DisplayName("shouldBypass - 不匹配 bypassHosts 时不绕过")
        fun `shouldBypass - 不匹配 bypassHosts 时不绕过`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                bypassHosts = listOf("internal.com")
            )

            assertThat(config.shouldBypass("example.com")).isFalse()
        }

        @Test
        @DisplayName("shouldBypass - 外部地址不应该绕过")
        fun `shouldBypass - 外部地址不应该绕过`() {
            val config = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                bypassLocal = true
            )

            assertThat(config.shouldBypass("api.example.com")).isFalse()
        }
    }

    // ==================== 伴生对象测试 ====================

    @Nested
    @DisplayName("伴生对象工厂方法测试")
    inner class CompanionObjectTests {

        @Test
        @DisplayName("direct - 应该创建 DIRECT 配置")
        fun `direct - 应该创建 DIRECT 配置`() {
            val config = ProxyConfig.direct()

            assertThat(config.type).isEqualTo(ProxyType.DIRECT)
        }

        @Test
        @DisplayName("http - 应该创建 HTTP 代理配置")
        fun `http - 应该创建 HTTP 代理配置`() {
            val config = ProxyConfig.http("proxy.example.com", 8080)

            assertThat(config.type).isEqualTo(ProxyType.HTTP)
            assertThat(config.host).isEqualTo("proxy.example.com")
            assertThat(config.port).isEqualTo(8080)
        }

        @Test
        @DisplayName("http - 带认证应该正确设置")
        fun `http - 带认证应该正确设置`() {
            val config = ProxyConfig.http(
                "proxy.example.com",
                8080,
                "user",
                "pass"
            )

            assertThat(config.username).isEqualTo("user")
            assertThat(config.password).isEqualTo("pass")
        }

        @Test
        @DisplayName("https - 应该创建 HTTPS 代理配置")
        fun `https - 应该创建 HTTPS 代理配置`() {
            val config = ProxyConfig.https("secure-proxy.example.com", 443)

            assertThat(config.type).isEqualTo(ProxyType.HTTPS)
            assertThat(config.host).isEqualTo("secure-proxy.example.com")
            assertThat(config.port).isEqualTo(443)
        }

        @Test
        @DisplayName("socks - 应该创建 SOCKS 代理配置")
        fun `socks - 应该创建 SOCKS 代理配置`() {
            val config = ProxyConfig.socks("socks.example.com", 1080)

            assertThat(config.type).isEqualTo(ProxyType.SOCKS)
            assertThat(config.host).isEqualTo("socks.example.com")
            assertThat(config.port).isEqualTo(1080)
        }
    }

    // ==================== ProxyType 枚举测试 ====================

    @Nested
    @DisplayName("ProxyType 枚举测试")
    inner class ProxyTypeTests {

        @Test
        @DisplayName("枚举值 - 应该包含所有类型")
        fun `枚举值 - 应该包含所有类型`() {
            assertThat(ProxyType.entries).containsExactly(
                ProxyType.DIRECT,
                ProxyType.HTTP,
                ProxyType.HTTPS,
                ProxyType.SOCKS
            )
        }

        @Test
        @DisplayName("valueOf - DIRECT 应该存在")
        fun `valueOf - DIRECT 应该存在`() {
            assertThat(ProxyType.valueOf("DIRECT")).isEqualTo(ProxyType.DIRECT)
        }

        @Test
        @DisplayName("valueOf - HTTP 应该存在")
        fun `valueOf - HTTP 应该存在`() {
            assertThat(ProxyType.valueOf("HTTP")).isEqualTo(ProxyType.HTTP)
        }

        @Test
        @DisplayName("valueOf - HTTPS 应该存在")
        fun `valueOf - HTTPS 应该存在`() {
            assertThat(ProxyType.valueOf("HTTPS")).isEqualTo(ProxyType.HTTPS)
        }

        @Test
        @DisplayName("valueOf - SOCKS 应该存在")
        fun `valueOf - SOCKS 应该存在`() {
            assertThat(ProxyType.valueOf("SOCKS")).isEqualTo(ProxyType.SOCKS)
        }
    }

    // ==================== 数据类特性测试 ====================

    @Nested
    @DisplayName("数据类特性测试")
    inner class DataClassFeaturesTests {

        @Test
        @DisplayName("equals - 相同配置应该相等")
        fun `equals - 相同配置应该相等`() {
            val config1 = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                username = "user",
                password = "pass"
            )
            val config2 = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080,
                username = "user",
                password = "pass"
            )

            assertThat(config1).isEqualTo(config2)
        }

        @Test
        @DisplayName("equals - 不同类型应该不相等")
        fun `equals - 不同类型应该不相等`() {
            val config1 = ProxyConfig(type = ProxyType.HTTP)
            val config2 = ProxyConfig(type = ProxyType.DIRECT)

            assertThat(config1).isNotEqualTo(config2)
        }

        @Test
        @DisplayName("copy - 应该创建独立副本")
        fun `copy - 应该创建独立副本`() {
            val original = ProxyConfig(
                type = ProxyType.HTTP,
                host = "proxy.example.com",
                port = 8080
            )
            val copied = original.copy(port = 9090)

            assertThat(original.port).isEqualTo(8080)
            assertThat(copied.port).isEqualTo(9090)
        }
    }
}
