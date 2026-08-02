package com.withcareer.screenpal_android.data.model

/**
 * 大模型配置档案
 * 用户可保存多套模型配置，在执行任务时切换使用
 *
 * @param id 配置唯一 ID
 * @param name 配置名称（用户可读）
 * @param provider 服务商（dashscope / volcengine / siliconflow）
 * @param baseUrl 自定义 Base URL（空则使用服务商默认地址）
 * @param modelName 模型名称（空则使用服务商默认多模态模型）
 * @param apiKey API Key
 */
data class ModelProfile(
    val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = ""
)
