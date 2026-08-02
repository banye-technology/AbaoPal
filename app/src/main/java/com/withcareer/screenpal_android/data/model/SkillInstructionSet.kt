package com.withcareer.screenpal_android.data.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/**
 * 技能内嵌的按键集数据模型
 * 用于在技能中定义可执行的操作序列
 */
data class SkillInstructionSet(
    @JsonProperty("id")
    val id: String = UUID.randomUUID().toString(),

    @JsonProperty("name")
    val name: String,                    // 按键集名称（必填）

    @JsonProperty("description")
    val description: String,             // 按键集描述（必填，供Agent选择使用）

    @JsonProperty("steps")
    val steps: List<InstructionStep>,    // 操作步骤列表
    @JsonProperty("created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 按键集中的单个操作步骤
 */
data class InstructionStep(
    @JsonProperty("order_index")
    val orderIndex: Int,

    @JsonProperty("action_type")
    val actionType: String,              // tap, swipe, longPress, type, scroll, multiTap等

    @JsonProperty("action_params")
    val actionParams: String,            // JSON格式参数

    @JsonProperty("description")
    val description: String,             // 步骤描述

    @JsonProperty("delay_before")
    val delayBefore: Long = 0L          // 执行前延迟(ms)
)
