package com.withcareer.screenpal_android.core.action.handler

import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import com.google.gson.JsonObject

/**
 * 动作处理接口
 * 定义了所有 UI 动作的统一执行规范
 *
 */
interface ActionHandler {
    /**
     * 获取该处理器支持的动作类型（如 "Tap", "Scroll", "Type"）
     * @return 动作类型字符串
     */
    fun getActionType(): String

    /**
     * 执行动作
     *
     * @param service 无障碍服务实例，提供 UI 交互能力
     * @param params 动作参数 JSON 对象
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 执行结果
     * @throws Exception 如果执行过程中发生错误
     */
    suspend fun execute(service: ScreenPalAccessibilityService, params: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult
}
