package com.withcareer.screenpal_android.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 用户自定义 Skill 实体
 * 存储用户创建的自定义技能配置
 */
@Entity(tableName = "user_skills")
data class UserSkillEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,                      // 必填：技能名称
    val description: String,               // 必填：技能描述
    val targetApps: String,                // 必填：JSON 对象，包名到应用名的映射 {"com.tencent.mm": "微信"}
    val targetAppNames: String? = null,    // 可选：JSON 对象，包名到应用名的映射 {"com.tencent.mm": "微信"}
    val instruction: String,               // 必填：自然语言指令
    val allowedTools: String = "[]",       // 可选：JSON 数组，允许的工具列表
    val instructionSets: String = "[]",    // 新增：JSON 数组，存储 SkillInstructionSet 列表
    val version: Int = 0,                  // 版本号（由后端控制）
    val author: String = "我",             // 作者名称
    val tags: String = "[]",               // 可选：JSON 数组，标签
    val isPublished: Boolean = false,      // 是否已发布到社区
    val remoteId: String? = null,          // 发布后的远程 ID
    val isImported: Boolean = false,       // 是否从云端导入
    val reviewStatus: String? = null,
    val reviewId: String? = null,
    val reviewMessage: String? = null,
    val reviewUpdatedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
