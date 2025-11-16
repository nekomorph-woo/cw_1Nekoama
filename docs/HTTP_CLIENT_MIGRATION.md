# HTTP客户端迁移文档

## 概述

本文档记录了Nekoama插件从Java HttpClient到OkHttp的完整迁移过程和改进。

## 迁移动机

### 原有问题
- Java HttpClient的拦截器系统功能有限
- 代理配置复杂且不够灵活
- 缺乏内置的重试机制
- 监控和日志功能不足
- 连接池管理不够精细

### OkHttp优势
- **强大的拦截器系统**：支持请求/响应处理、日志记录、监控统计
- **智能重试机制**：内置指数退避策略
- **连接池优化**：更精细的连接管理和复用
- **HTTP/2支持**：提升网络性能
- **丰富的调试功能**：详细的请求追踪和性能分析

## 架构变更

### 1. 异常处理体系更新

#### BaseHttpClient变更
```kotlin
// 原版本（Java HttpClient异常）
} catch (e: java.net.http.HttpTimeoutException) {
    handleTimeoutException("sendRequest", e)

// 新版本（OkHttp异常）
} catch (e: TimeoutException) {
    handleTimeoutException("sendRequest", e, isTimeoutException = true)
} catch (e: SocketTimeoutException) {
    handleTimeoutException("sendRequest", e, isSocketTimeout = true)
} catch (e: ConnectException) {
    handleConnectionException("sendRequest", e)
} catch (e: UnknownHostException) {
    handleHostException("sendRequest", e)
} catch (e: IOException) {
    handleIOException("sendRequest", e)
```

#### 异常类型扩展
- `TimeoutException`: 通用超时异常
- `SocketTimeoutException`: Socket读取超时
- `ConnectException`: 连接失败
- `UnknownHostException`: 主机名解析失败
- `IOException`: IO错误（包含HTTP状态码检测）

### 2. HTTP客户端重构

#### CustomAPIHttpClient新特性
```kotlin
class CustomAPIHttpClient(
    private val config: CustomAPIConfig
) : BaseHttpClient() {

    private val httpClient = createOkHttpClient()
    private val monitoringInterceptor = MonitoringInterceptor()

    // 性能统计API
    fun getHttpStatistics(): MonitoringInterceptor.HttpStatistics
    fun resetHttpStatistics()

    // 连接池管理
    fun getConnectionPoolInfo(): ConnectionPoolInfo
    suspend fun warmupConnectionPool()

    // 配置摘要
    fun getClientConfigurationSummary(): ClientConfigurationSummary
}
```

### 3. 拦截器系统

#### 日志拦截器（LoggingInterceptor）
- 支持多种日志级别：NONE、BASIC、HEADERS、BODY
- 自动过滤敏感信息（认证头、Cookie等）
- 请求ID追踪
- 响应时间记录

```kotlin
enum class LogLevel {
    NONE,    // 不记录日志
    BASIC,   // 记录基本信息（URL、方法、状态码、时间）
    HEADERS, // 记录头部信息
    BODY     // 记录完整请求和响应体（小心敏感信息）
}
```

#### 重试拦截器（RetryInterceptor）
- 指数退避策略
- 可配置重试次数和延迟
- 幂等性检查确保安全重试
- 智能异常和状态码过滤

```kotlin
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 1000,
    private val maxDelayMs: Long = 30000,
    private val retryableExceptions: Set<Class<out IOException>>,
    private val retryableStatusCodes: Set<Int>
)
```

#### 监控拦截器（MonitoringInterceptor）
- 实时性能统计
- 请求计数（成功/失败）
- 响应时间分析（平均、最大、最小）
- 数据传输量统计
- 状态码分布分析

#### 请求头拦截器（HeadersInterceptor）
- 统一请求头管理
- 自动Content-Type设置
- 用户代理标准化
- 请求ID生成

### 4. OkHttp特性优化

#### 连接池配置
```kotlin
builder.connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
```

#### 协议支持
```kotlin
builder.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
```

#### 超时优化
```kotlin
val connectTimeoutMs = (config.timeoutMs / 4).coerceAtLeast(10000L).coerceAtMost(15000L)
val readTimeoutMs = (config.timeoutMs / 2).coerceAtLeast(20000L).coerceAtMost(60000L)
val writeTimeoutMs = (config.timeoutMs / 2).coerceAtLeast(20000L).coerceAtMost(60000L)
val callTimeoutMs = config.timeoutMs
```

#### 连接保持活跃
```kotlin
builder.pingInterval(30, TimeUnit.SECONDS)
builder.retryOnConnectionFailure(true)
```

## 性能改进

### 1. 连接复用
- HTTP/2多路复用支持
- 智能连接池管理
- 连接保持活跃机制

### 2. 内存优化
- 更高效的缓冲区管理
- 自动资源清理
- 优化的序列化流程

### 3. 网络优化
- 连接预热功能
- DNS解析优化
- 请求压缩支持（预留）

## 调试和监控

### 1. 性能指标
```kotlin
data class HttpStatistics(
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val averageResponseTime: Long,
    val minResponseTime: Long,
    val maxResponseTime: Long,
    val totalBytesSent: Long,
    val totalBytesReceived: Long,
    val statusCodeDistribution: Map<Int, Long>
)
```

### 2. 连接池状态
```kotlin
data class ConnectionPoolInfo(
    val idleConnectionCount: Int,
    val connectionCount: Int,
    val maxIdleConnections: Int,
    val keepAliveDurationMs: Long
)
```

### 3. 客户端配置
```kotlin
data class ClientConfigurationSummary(
    val timeoutMs: Long,
    val verifySSL: Boolean,
    val endpointUrl: String,
    val hasAuthHeaders: Boolean,
    val supportsHttp2: Boolean,
    val retryEnabled: Boolean,
    val monitoringEnabled: Boolean,
    val loggingEnabled: Boolean
)
```

## 代理和SSL配置

### 1. 代理支持
- 自动检测IDEA系统代理
- HTTP/HTTPS/SOCKS代理类型支持
- 代理认证自动配置
- 连接失败回退机制

### 2. SSL配置
- 可选的SSL验证禁用（开发环境）
- 自定义TrustManager支持
- 证书验证灵活控制

## 使用示例

### 基本使用
```kotlin
val client = CustomAPIHttpClient(config)

// 发送请求
val result = client.sendRequest(openAIRequest)

// 获取统计信息
val stats = client.getHttpStatistics()
println("成功率: ${stats.successRate}%")
println("平均响应时间: ${stats.averageResponseTime}ms")

// 获取连接池状态
val poolInfo = client.getConnectionPoolInfo()
println("空闲连接: ${poolInfo.idleConnectionCount}")
```

### 配置日志级别
```kotlin
// 在创建客户端时自定义日志级别
val loggingInterceptor = LoggingInterceptor(LoggingInterceptor.LogLevel.HEADERS)
// 然后添加到客户端构建器中
```

## 迁移检查清单

- [x] 更新BaseHttpClient异常处理支持OkHttp
- [x] 完全重写CustomAPIHttpClient使用OkHttp架构
- [x] 迁移代理和SSL配置到OkHttp拦截器系统
- [x] 实现OkHttp拦截器系统（日志、监控、重试）
- [x] 优化现有功能利用OkHttp特性（超时、连接池等）
- [x] 更新测试代码和文档

## 注意事项

### 1. 线程安全
- 所有HTTP客户端操作都是线程安全的
- 监控统计使用同步锁保证线程安全

### 2. 资源管理
- 客户端实现了自动资源管理
- 提供了显式的close()方法用于资源清理

### 3. 向后兼容
- 保持了与原有API的兼容性
- 所有现有调用方式继续有效

## 测试建议

### 1. 单元测试
- 测试异常处理逻辑
- 验证拦截器功能
- 检查配置参数

### 2. 集成测试
- 测试不同网络环境下的表现
- 验证代理配置正确性
- 检查SSL证书处理

### 3. 性能测试
- 对比迁移前后的性能差异
- 测试连接池效果
- 验证并发处理能力

## 总结

通过迁移到OkHttp，Nekoama插件的HTTP客户端获得了以下改进：

1. **更强的功能性**：拦截器系统提供了灵活的请求/响应处理能力
2. **更好的性能**：HTTP/2支持、连接池优化、智能重试机制
3. **更完善的监控**：详细的性能统计和调试信息
4. **更高的可靠性**：更好的错误处理和恢复机制
5. **更易维护**：模块化设计，清晰的职责分离

这次迁移为插件未来的功能扩展奠定了坚实的基础。