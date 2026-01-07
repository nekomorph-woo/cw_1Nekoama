package com.cw2.nekoama.mock

/**
 * 测试数据管理工具
 *
 * 使用混合方式管理测试数据：
 * - 简单数据：使用 Kotlin 常量
 * - 复杂数据：从资源文件加载
 */
object TestData {

    // ==================== 简单 JSON 响应数据（常量） ====================

    /**
     * 简单的命名建议响应
     */
    val simpleNamingResponse = """
        {
          "suggestions": [
            {
              "name": "testName",
              "description": "测试描述",
              "score": 0.9
            }
          ]
        }
    """.trimIndent()

    /**
     * 简单的注释建议响应
     */
    val simpleCommentResponse = """
        {
          "content": "测试注释内容"
        }
    """.trimIndent()

    /**
     * 简单的错误响应
     */
    val simpleErrorResponse = """
        {
          "error": {
            "message": "测试错误",
            "type": "test_error"
          }
        }
    """.trimIndent()

    // ==================== 简单代码片段（常量） ====================

    /**
     * 简单的 Kotlin 函数
     */
    val simpleKotlinFunction = """
        fun test() = true
    """.trimIndent()

    /**
     * 简单的 Kotlin 类
     */
    val simpleKotlinClass = """
        class TestClass {
            fun test() = true
        }
    """.trimIndent()

    /**
     * 简单的 Java 方法
     */
    val simpleJavaMethod = """
        public void test() {
            // method body
        }
    """.trimIndent()

    // ==================== 资源文件加载工具 ====================

    /**
     * 从资源文件加载 JSON 数据
     *
     * @param filename 文件名（相对于 test-data/openai-responses/ 目录）
     * @return JSON 字符串
     * @throws IllegalArgumentException 如果文件不存在
     */
    fun loadJson(filename: String): String {
        return javaClass.getResource("/test-data/openai-responses/$filename")
            ?.readText()
            ?: throw IllegalArgumentException("测试文件不存在: /test-data/openai-responses/$filename")
    }

    /**
     * 从资源文件加载代码示例
     *
     * @param filename 文件名（相对于 test-data/code-samples/ 目录）
     * @return 代码字符串
     * @throws IllegalArgumentException 如果文件不存在
     */
    fun loadCodeSample(filename: String): String {
        return javaClass.getResource("/test-data/code-samples/$filename")
            ?.readText()
            ?: throw IllegalArgumentException("代码示例不存在: /test-data/code-samples/$filename")
    }

    /**
     * 从资源文件加载代理配置
     *
     * @param filename 文件名（相对于 test-data/proxy-configs/ 目录）
     * @return 配置字符串
     * @throws IllegalArgumentException 如果文件不存在
     */
    fun loadProxyConfig(filename: String): String {
        return javaClass.getResource("/test-data/proxy-configs/$filename")
            ?.readText()
            ?: throw IllegalArgumentException("代理配置不存在: /test-data/proxy-configs/$filename")
    }

    // ==================== 代理配置测试数据（常量） ====================

    /**
     * HTTP 代理配置
     */
    val httpProxyConfig = """
        {
          "type": "HTTP",
          "host": "127.0.0.1",
          "port": 8080,
          "username": null,
          "password": null
        }
    """.trimIndent()

    /**
     * SOCKS 代理配置
     */
    val socksProxyConfig = """
        {
          "type": "SOCKS",
          "host": "127.0.0.1",
          "port": 1080,
          "username": null,
          "password": null
        }
    """.trimIndent()

    /**
     * 带认证的 HTTP 代理配置
     */
    val authenticatedHttpProxyConfig = """
        {
          "type": "HTTP",
          "host": "proxy.example.com",
          "port": 3128,
          "username": "testuser",
          "password": "testpass"
        }
    """.trimIndent()
}
