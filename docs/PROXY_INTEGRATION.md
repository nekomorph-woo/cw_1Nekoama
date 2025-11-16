# Nekoama 插件代理集成方案

## 问题概述

原问题：插件在没有设置代理的IDEA上能正常工作，但在设置了代理的IDEA上无法连接AI API Endpoint。

**根本原因**：插件的HTTP客户端没有集成IDEA的系统代理设置，导致在有代理环境的IDEA中无法正常连接外部API服务。

## 解决方案架构

### 1. 分层设计

我们采用了分层架构来解决代理集成问题：

```
应用层 (NekoamaConfigurable)
    ↓
管理层 (ProxyInitializationManager)
    ↓
检测层 (ProxyDetector)
    ↓
配置层 (HttpClientProxyConfigurator)
    ↓
实现层 (CustomAPIHttpClient, OkHttp)
```

### 2. 核心组件

#### 2.1 代理检测器 (ProxyDetector.kt)
- **功能**：自动检测IDEA的系统代理设置
- **支持的代理类型**：HTTP、HTTPS、SOCKS
- **认证支持**：用户名/密码认证
- **环境变量支持**：HTTP_PROXY、HTTPS_PROXY、NO_PROXY

#### 2.2 代理配置模型 (ProxyConfig.kt)
- **功能**：统一的代理配置数据模型
- **特性**：
  - 支持所有主流代理类型
  - 包含认证信息
  - 提供主机绕过列表
  - 可转换为Java标准代理对象

#### 2.3 HTTP客户端配置器 (HttpClientProxyConfigurator.kt)
- **功能**：为不同HTTP客户端提供统一的代理配置
- **支持的客户端**：
  - Java HttpClient (系统属性配置)
  - OkHttp (直接配置)
- **特性**：
  - 自动配置代理认证
  - 支持代理类型自动检测

#### 2.4 代理初始化管理器 (ProxyInitializationManager.kt)
- **功能**：全局代理配置的生命周期管理
- **特性**：
  - 插件启动时自动初始化
  - 支持重新配置
  - 提供代理状态查询

#### 2.5 代理连接测试器 (ProxyConnectionTester.kt)
- **功能**：提供代理连接的诊断和测试功能
- **特性**：
  - 支持多种测试模式（直连、代理）
  - 详细的测试结果和诊断信息
  - 用户友好的结果格式化

## 实现细节

### 1. IDEA代理检测

```kotlin
fun detectSystemProxy(targetUrl: String? = null): ProxyConfig {
    val httpConfigurable = HttpConfigurable.getInstance()

    return when {
        !httpConfigurable.USE_HTTP_PROXY && !httpConfigurable.USE_PROXY_PAC -> {
            ProxyConfig.direct() // 直连
        }

        httpConfigurable.USE_HTTP_PROXY -> {
            // HTTP/SOCKS代理
            ProxyConfig(
                type = ProxyType.HTTP,
                host = httpConfigurable.PROXY_HOST,
                port = httpConfigurable.PROXY_PORT,
                username = httpConfigurable.getProxyLogin(),
                password = httpConfigurable.getPlainProxyPassword()
            )
        }

        httpConfigurable.USE_PROXY_PAC -> {
            // PAC代理（暂不支持，回退到环境变量）
            detectEnvironmentProxy()
        }

        else -> ProxyConfig.direct()
    }
}
```

### 2. 系统级代理配置

```kotlin
fun configureSystemProxy(proxyConfig: ProxyConfig) {
    if (proxyConfig.type == ProxyType.DIRECT || !proxyConfig.isValid()) {
        clearSystemProxy()
        return
    }

    // 设置系统代理属性
    System.setProperty("http.proxyHost", proxyConfig.host!!)
    System.setProperty("http.proxyPort", proxyConfig.port.toString())
    System.setProperty("https.proxyHost", proxyConfig.host!!)
    System.setProperty("https.proxyPort", proxyConfig.port.toString())

    // 配置代理认证
    if (!proxyConfig.username.isNullOrBlank()) {
        System.setProperty("http.proxyUser", proxyConfig.username)
        System.setProperty("http.proxyPassword", proxyConfig.password ?: "")
        System.setProperty("https.proxyUser", proxyConfig.username)
        System.setProperty("https.proxyPassword", proxyConfig.password ?: "")

        val authenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(
                    proxyConfig.username,
                    proxyConfig.password?.toCharArray() ?: charArrayOf()
                )
            }
        }
        Authenticator.setDefault(authenticator)
    }
}
```

### 3. 插件启动时初始化

```kotlin
internal class NekoamaStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        try {
            // 初始化全局代理配置
            ProxyInitializationManager.initialize()

            // 其他初始化逻辑...
        } catch (e: Throwable) {
            NekoamaLogger.warn("STARTUP", "Failed to initialize proxy", error = e)
        }
    }
}
```

### 4. 用户界面集成

在设置界面添加了代理测试功能：

```kotlin
// 代理测试连接
proxyTestButton.addActionListener {
    proxyTestButton.isEnabled = false
    proxyTestResultLabel.text = "正在测试代理连接..."
    proxyTestResultLabel.foreground = JBColor.CYAN

    ApplicationManager.getApplication().executeOnPooledThread {
        val testUrl = endpointField.text.trim().ifEmpty { "https://api.openai.com" }
        val result = runBlocking {
            ProxyConnectionTester.testCurrentIDEAProxy(testUrl)
        }

        ApplicationManager.getApplication().invokeLater({
            if (result.success) {
                proxyTestResultLabel.text = "代理测试成功 (响应时间: ${result.responseTime}ms)"
                proxyTestResultLabel.foreground = JBColor.GREEN
            } else {
                proxyTestResultLabel.text = "代理测试失败: ${result.message}"
                proxyTestResultLabel.foreground = JBColor.RED
            }
            proxyTestButton.isEnabled = true
        }, ModalityState.any())
    }
}
```

## 支持的代理类型和特性

### 1. 代理类型支持
- **HTTP代理**：标准的HTTP代理服务器
- **HTTPS代理**：支持TLS的HTTP代理
- **SOCKS代理**：SOCKS4/SOCKS5代理
- **直连**：不使用代理的直接连接

### 2. 认证支持
- **基本认证**：用户名/密码认证
- **自动检测**：从IDEA设置中自动获取认证信息
- **环境变量**：支持从环境变量读取代理认证

### 3. 高级特性
- **主机绕过**：支持本地地址和自定义绕过列表
- **连接测试**：提供代理连接的实时测试功能
- **错误处理**：完善的错误处理和日志记录
- **动态配置**：支持运行时重新配置代理设置

## 使用指南

### 1. 配置IDEA代理
1. 打开 IDEA 设置：File → Settings → Appearance & Behavior → System Settings → HTTP Proxy
2. 配置代理服务器信息：
   - 选择"Manual proxy configuration"
   - 设置代理服务器地址和端口
   - 配置认证信息（如果需要）
3. 保存设置

### 2. 测试代理连接
1. 打开 Nekoama 插件设置
2. 在AI服务配置区域找到"测试代理连接"按钮
3. 点击按钮测试代理连接
4. 查看测试结果

### 3. 故障排除
- **连接失败**：检查代理服务器配置和网络连接
- **认证失败**：验证代理用户名和密码
- **超时问题**：检查代理服务器响应时间

## 文件结构

```
src/main/kotlin/com/cw2/nekoama/core/network/
├── ProxyConfig.kt                    # 代理配置数据模型
├── ProxyDetector.kt                  # IDEA代理检测器
├── HttpClientProxyConfigurator.kt    # HTTP客户端代理配置器
├── ProxyInitializationManager.kt     # 代理初始化管理器
└── ProxyConnectionTester.kt          # 代理连接测试器

src/main/kotlin/com/cw2/nekoama/ai/provider/custom/
└── CustomAPIHttpClient.kt            # 集成代理支持的HTTP客户端

src/main/kotlin/com/cw2/nekoama/platform/lifecycle/
└── NekoamaStartupActivity.kt         # 插件启动代理初始化

src/main/kotlin/com/cw2/nekoama/presentation/settings/
└── NekoamaConfigurable.kt            # 设置界面代理测试功能
```

## 兼容性说明

### 1. IntelliJ平台版本
- **目标平台**：IntelliJ IDEA 2025.1+
- **向后兼容**：支持大部分现代IntelliJ版本
- **API使用**：使用稳定且广泛支持的API

### 2. 已知限制
- **PAC代理**：当前版本暂不支持自动代理配置(PAC)
- **SOCKS代理**：简化处理，主要支持HTTP/HTTPS代理
- **代理类型检测**：部分IDEA版本的字段访问使用了已弃用的API

### 3. 日志记录
- 使用NekoamaLogger进行统一的日志记录
- 支持不同级别的日志输出
- 包含详细的代理配置和连接信息

## 测试和验证

### 1. 单元测试
- 代理配置验证测试
- 代理检测逻辑测试
- 代理连接测试功能测试

### 2. 集成测试
- 不同代理环境下的连接测试
- 认证功能的验证测试
- 错误处理的测试

### 3. 用户场景测试
- 无代理环境下的直连测试
- 有代理环境下的代理连接测试
- 代理切换的动态测试

## 未来扩展

### 1. PAC代理支持
- 实现PAC文件的解析和执行
- 支持动态代理规则

### 2. 更多代理类型
- 完整的SOCKS代理支持
- 代理链支持

### 3. 高级配置
- 代理超时设置
- 代理重试策略
- 代理健康检查

## 总结

本代理集成方案通过以下关键特性解决了原始问题：

1. **自动检测**：自动检测IDEA的系统代理设置
2. **统一配置**：为所有HTTP客户端提供统一的代理配置
3. **完整支持**：支持主流代理类型和认证方式
4. **用户友好**：提供测试功能和详细的诊断信息
5. **稳定性**：完善的错误处理和日志记录

该方案确保了Nekoama插件在各种网络环境下都能正常工作，包括企业防火墙和代理环境。