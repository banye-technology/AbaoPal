package com.withcareer.screenpal_android.core.skill

import com.fasterxml.jackson.annotation.JsonProperty
import com.withcareer.screenpal_android.data.model.SkillInstructionSet

/**
 * Skill 类型枚举
 */
enum class SkillType {
    TASK,
    APP
}

/**
 * Skill 实体类，映射 YAML 配置文件
 */
data class Skill(
    @JsonProperty("metadata")
    val metadata: SkillMetadata,

    @JsonProperty("device_filter")
    val deviceFilter: DeviceFilter? = null,

    @JsonProperty("instruction")
    val instruction: String = "",

    @JsonProperty("allowed_tools")
    val allowedTools: List<String> = emptyList(),

    @JsonProperty("app_context")
    val appContext: AppContext? = null,

    @JsonProperty("user_path_tree")
    val userPathTree: List<UserPathNode>? = null,

    @JsonProperty("instruction_sets")
    val instructionSets: List<SkillInstructionSet>? = null
)

/**
 * Skill 元数据
 */
data class SkillMetadata(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("type")
    val type: SkillType = SkillType.TASK,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("version")
    val version: String,

    @JsonProperty("author")
    val author: String,

    @JsonProperty("description")
    val description: String,

    @JsonProperty("target_apps")
    val targetApps: List<String>
)

/**
 * 机型过滤配置
 */
data class DeviceFilter(
    @JsonProperty("brands")
    val brands: List<String>? = null,

    @JsonProperty("os_versions")
    val osVersions: List<String>? = null
)

/**
 * 应用上下文配置
 */
data class AppContext(
    @JsonProperty("package")
    val packageName: String,

    @JsonProperty("main_activity")
    val mainActivity: String? = null
)

/**
 * 用户路径节点
 * 代表 App 中的一个可交互区域、按钮或页面状态
 */
data class UserPathNode(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("matcher")
    val matcher: UIMatcher? = null,

    @JsonProperty("action")
    val action: String? = "tap",

    @JsonProperty("included_features")
    val includedFeatures: List<String> = emptyList(),

    @JsonProperty("children")
    val children: List<UserPathNode> = emptyList()
)

/**
 * UI 匹配规则
 */
data class UIMatcher(
    @JsonProperty("text")
    val text: String? = null,

    @JsonProperty("id")
    val id: String? = null,

    @JsonProperty("desc")
    val desc: String? = null
)
