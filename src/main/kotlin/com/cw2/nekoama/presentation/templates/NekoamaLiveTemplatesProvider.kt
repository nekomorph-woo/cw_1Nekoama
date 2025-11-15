package com.cw2.nekoama.presentation.templates

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

/*
 * Live Template 提供器
 *
 * 注册 Nekoama 组的 Live Template 模板文件。
 */
class NekoamaLiveTemplatesProvider : DefaultLiveTemplatesProvider {
    override fun getDefaultLiveTemplateFiles(): Array<String> = arrayOf("templates/nekoama")

    override fun getHiddenLiveTemplateFiles(): Array<String> = emptyArray()
}
