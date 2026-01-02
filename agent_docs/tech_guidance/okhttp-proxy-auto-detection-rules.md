# OkHttp Proxy Auto-Detection Technical Rules

## 1. Axioms (不可违背的公理)
- **Status**: Stable
- **Core Principle**: All HTTP requests must respect IDEA's proxy configuration automatically

## 2. Mapping Rules (规则映射)
| Category | ❌ Forbidden (Strict Ban) | ✅ Required (Pattern) |
| :--- | :--- | :--- |
| Proxy Detection | Manual proxy configuration | `HttpConfigurable.getInstance()` |
| OkHttp Setup | `new OkHttpClient()` | `HttpClientProxyConfigurator.configureOkHttpClientProxy()` |
| System Properties | Hardcoded proxy settings | `ProxyDetector.detectSystemProxy()` |
| Proxy Auth | Direct header manipulation | `okhttp3.Authenticator` with `Credentials.basic()` |
| Error Handling | Silent proxy failures | Fallback to `ProxyConfig.direct()` |

## 3. Critical Snippets (核心代码范式)
```kotlin
// Good Pattern - Automatic proxy detection
val proxyConfig = ProxyDetector.detectSystemProxy()
val builder = OkHttpClient.Builder()
HttpClientProxyConfigurator.configureOkHttpClientProxy(builder, proxyConfig)
val client = builder.build()

// Good Pattern - Proxy authentication with authenticator
val authenticator = okhttp3.Authenticator { _, response ->
    val credential = okhttp3.Credentials.basic(username, password)
    response.request.newBuilder()
        .header("Proxy-Authorization", credential)
        .build()
}
builder.proxyAuthenticator(authenticator)

// Good Pattern - IDEA proxy detection
val httpConfigurable = HttpConfigurable.getInstance()
when {
    !httpConfigurable.USE_HTTP_PROXY && !httpConfigurable.USE_PROXY_PAC -> ProxyConfig.direct()
    httpConfigurable.USE_HTTP_PROXY -> detectProxyType(httpConfigurable)
    else -> detectEnvironmentProxy()
}
```

## 4. Verification (如何验证)
* Check: Proxy auto-detection works with IDEA HTTP/HTTPS/SOCKS proxy settings
* Check: Proxy authentication credentials are applied automatically when configured
* Check: Fallback to direct connection when proxy detection fails
* Check: Environment variable proxy support (HTTP_PROXY, HTTPS_PROXY)
* Check: Proxy bypass rules for localhost and local networks