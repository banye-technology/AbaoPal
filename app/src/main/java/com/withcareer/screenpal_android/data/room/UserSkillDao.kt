package com.withcareer.screenpal_android.data.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 用户自定义 Skill 数据访问对象
 */
@Dao
interface UserSkillDao {
    /**
     * 获取所有用户 Skill（按更新时间倒序）
     */
    @Query("SELECT * FROM user_skills ORDER BY updatedAt DESC")
    fun getAllSkills(): Flow<List<UserSkillEntity>>

    /**
     * 根据 ID 获取单个 Skill
     */
    @Query("SELECT * FROM user_skills WHERE id = :id")
    suspend fun getSkillById(id: String): UserSkillEntity?

    /**
     * 插入或替换 Skill
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: UserSkillEntity)

    /**
     * 更新 Skill
     */
    @Update
    suspend fun updateSkill(skill: UserSkillEntity)

    /**
     * 删除 Skill
     */
    @Delete
    suspend fun deleteSkill(skill: UserSkillEntity)

    /**
     * 根据 ID 删除 Skill
     */
    @Query("DELETE FROM user_skills WHERE id = :id")
    suspend fun deleteSkillById(id: String)

    /**
     * 根据目标应用包名查询 Skill（模糊匹配）
     */
    @Query("SELECT * FROM user_skills WHERE targetApps LIKE '%' || :packageName || '%'")
    suspend fun getSkillsForApp(packageName: String): List<UserSkillEntity>

    /**
     * 获取所有 Skill（非 Flow，用于一次性查询）
     */
    @Query("SELECT * FROM user_skills ORDER BY updatedAt DESC")
    suspend fun getAllSkillsList(): List<UserSkillEntity>

    /**
     * 根据 remoteId 获取 Skill
     */
    @Query("SELECT * FROM user_skills WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getSkillByRemoteId(remoteId: String): UserSkillEntity?
}
