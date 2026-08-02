package com.withcareer.screenpal_android.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.withcareer.screenpal_android.data.room.UserSkillDao
import com.withcareer.screenpal_android.data.room.UserSkillEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户自定义 Skill 仓库
 * 仅负责本地数据库操作（社区云端功能已移除）
 */
class UserSkillRepository(
    private val dao: UserSkillDao,
    private val context: Context
) {
    private val verboseLog: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        private const val TAG = "UserSkillRepository"
    }

    // ==================== 本地数据库操作 ====================

    /**
     * 获取所有用户 Skill（Flow，实时更新）
     */
    fun getAllSkills(): Flow<List<UserSkillEntity>> = dao.getAllSkills()

    /**
     * 获取所有用户 Skill（列表，一次性查询）
     */
    suspend fun getAllSkillsList(): List<UserSkillEntity> = dao.getAllSkillsList()

    /**
     * 根据 ID 获取 Skill
     */
    suspend fun getSkillById(id: String): UserSkillEntity? = dao.getSkillById(id)

    /**
     * 根据 remoteId 获取 Skill
     */
    suspend fun getSkillByRemoteId(remoteId: String): UserSkillEntity? = dao.getSkillByRemoteId(remoteId)

    /**
     * 插入或更新 Skill
     */
    suspend fun insertSkill(skill: UserSkillEntity) = dao.insertSkill(skill)

    /**
     * 更新 Skill
     */
    suspend fun updateSkill(skill: UserSkillEntity) = dao.updateSkill(skill)

    /**
     * 删除 Skill
     */
    suspend fun deleteSkill(id: String) = dao.deleteSkillById(id)

    /**
     * 根据应用包名查询 Skill
     */
    suspend fun getSkillsForApp(packageName: String): List<UserSkillEntity> {
        return dao.getSkillsForApp(packageName)
    }
}
