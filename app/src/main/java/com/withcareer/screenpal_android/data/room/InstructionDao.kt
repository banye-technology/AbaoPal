package com.withcareer.screenpal_android.data.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 指令集与步骤的数据库访问接口
 */
@Dao
interface InstructionDao {
    /**
     * 获取全部指令集（按创建时间倒序）
     */
    @Query("SELECT * FROM instruction_sets ORDER BY createdAt DESC")
    fun getAllSets(): Flow<List<InstructionSetEntity>>

    /**
     * 根据指令集 ID 获取指令集
     */
    @Query("SELECT * FROM instruction_sets WHERE id = :id")
    suspend fun getSetById(id: String): InstructionSetEntity?

    /**
     * 根据标题精确匹配获取指令集（忽略大小写）
     */
    @Query("SELECT * FROM instruction_sets WHERE title = :title COLLATE NOCASE LIMIT 1")
    suspend fun getSetByTitle(title: String): InstructionSetEntity?

    /**
     * 根据标题关键字模糊匹配获取指令集（忽略大小写）
     */
    @Query("SELECT * FROM instruction_sets WHERE title LIKE '%' || :keyword || '%' ORDER BY createdAt DESC LIMIT 1")
    suspend fun findSetByTitleKeyword(keyword: String): InstructionSetEntity?

    /**
     * 获取指令集下的所有步骤（按顺序）
     */
    @Query("SELECT * FROM instruction_steps WHERE setId = :setId ORDER BY orderIndex ASC")
    suspend fun getStepsBySetId(setId: String): List<InstructionStepEntity>

    /**
     * 保存或更新指令集
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: InstructionSetEntity)

    /**
     * 保存或更新指令集步骤
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<InstructionStepEntity>)

    /**
     * 删除指令集下所有步骤
     */
    @Query("DELETE FROM instruction_steps WHERE setId = :setId")
    suspend fun deleteStepsBySetId(setId: String)

    /**
     * 创建或更新指令集并覆盖步骤
     */
    @Transaction
    suspend fun createInstructionSet(set: InstructionSetEntity, steps: List<InstructionStepEntity>) {
        insertSet(set)
        deleteStepsBySetId(set.id) // 先删除旧步骤，确保更新后步骤不重复
        insertSteps(steps)
    }

    /**
     * 删除指令集
     */
    @Query("DELETE FROM instruction_sets WHERE id = :id")
    suspend fun deleteSet(id: String)
}
