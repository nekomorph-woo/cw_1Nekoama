package com.cw2.nekoama.mock

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk

/**
 * Project Mock 工厂
 *
 * 提供创建 Project Mock 的工厂方法，用于测试中需要 Project 实例的场景
 */
object ProjectMock {

    /**
     * 创建 Mock Project
     *
     * @param name 项目名称
     * @param baseDir 项目根目录
     * @return Mock 的 Project
     */
    fun mockProject(
        name: String = "TestProject",
        baseDir: VirtualFile? = null
    ): Project {
        val mockProject = mockk<Project>(relaxed = true)

        every { mockProject.name } returns name

        // 模拟基础目录
        if (baseDir != null) {
            every { mockProject.basePath } returns baseDir.path
        } else {
            every { mockProject.basePath } returns "/test/project/path"
        }

        return mockProject
    }
}
