package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ConnectivityStatus

/**
 * 网络测试服务接口
 */
interface NetworkTestService {
    /**
     * 测试 API 连通性
     *
     * @param endpoint API 端点，为空时使用设置中的配置
     * @return 连通性状态
     */
    suspend fun testConnectivity(endpoint: String? = null): ConnectivityStatus
}
