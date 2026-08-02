package com.withcareer.screenpal_android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.withcareer.screenpal_android.core.skill.SkillRegistry
import com.withcareer.screenpal_android.data.repository.InstructionRepository
import com.withcareer.screenpal_android.data.room.InstructionSetEntity
import com.withcareer.screenpal_android.data.repository.UserSkillRepository
import com.withcareer.screenpal_android.data.room.UserSkillEntity
import com.withcareer.screenpal_android.data.model.SkillInstructionSet
import com.withcareer.screenpal_android.data.model.InstructionStep
import com.withcareer.screenpal_android.recording.RecordingResult
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 用户自定义 Skill 的 UI 模型
 * 预处理了 JSON 解析逻辑，避免在渲染层进行耗时操作
 */
data class UserSkillUiModel(
    val id: String,
    val name: String,
    val description: String,
    val targetAppNames: List<String>,
    val tags: List<String>,
    val isPublished: Boolean,
    val isImported: Boolean,
    val remoteId: String?,
    val reviewStatus: String?,
    val reviewMessage: String?,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 用户自定义 Skill ViewModel
 * 管理本地 Skill 的创建、编辑、删除和分享
 */
class UserSkillViewModel(
    private val repository: UserSkillRepository,
    private val skillRegistry: SkillRegistry,
    private val instructionRepository: InstructionRepository
) : ViewModel() {

    companion object {
        const val NEW_SKILL_ID = "new_skill_pending"
    }

    private val gson = Gson()

    // 临时存储录制的步骤（用于导航传递）
    private val _pendingRecordedSteps = MutableStateFlow<List<InstructionStep>?>(null)
    val pendingRecordedSteps: StateFlow<List<InstructionStep>?> = _pendingRecordedSteps.asStateFlow()

    // 临时存储录制结果（用于完整编辑页：时间轴/实时编辑/截图）
    private val _pendingRecordingResult = MutableStateFlow<RecordingResult?>(null)
    val pendingRecordingResult: StateFlow<RecordingResult?> = _pendingRecordingResult.asStateFlow()

    // 临时存储待编辑的按键集（用于从列表进入全屏编辑）
    private val _pendingEditingInstructionSet = MutableStateFlow<SkillInstructionSet?>(null)
    val pendingEditingInstructionSet: StateFlow<SkillInstructionSet?> = _pendingEditingInstructionSet.asStateFlow()

    data class SavedInstructionSetUpdate(
        val skillId: String,
        val instructionSet: SkillInstructionSet
    )

    private val _lastSavedInstructionSet = MutableStateFlow<SavedInstructionSetUpdate?>(null)
    val lastSavedInstructionSet: StateFlow<SavedInstructionSetUpdate?> = _lastSavedInstructionSet.asStateFlow()

    // 当前正在编辑的技能ID（用于按键集保存）
    private val _currentEditingSkillId = MutableStateFlow<String?>(null)
    val currentEditingSkillId: StateFlow<String?> = _currentEditingSkillId.asStateFlow()

    // Skill 列表（实时更新并预处理为 UI 模型）
    val skills: StateFlow<List<UserSkillUiModel>> = repository.getAllSkills()
        .map { entities ->
            withContext(Dispatchers.Default) {
                entities.map { entity ->
                    UserSkillUiModel(
                        id = entity.id,
                        name = entity.name,
                        description = entity.description,
                        targetAppNames = parseTargetAppNames(entity.targetApps),
                        tags = parseJsonArray(entity.tags),
                        isPublished = entity.isPublished,
                        isImported = entity.isImported,
                        remoteId = entity.remoteId,
                        reviewStatus = entity.reviewStatus,
                        reviewMessage = entity.reviewMessage,
                        version = entity.version,
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val instructionSets: StateFlow<List<InstructionSetEntity>> =
        instructionRepository.allInstructionSets.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun parseTargetAppNames(json: String): List<String> {
        return try {
            val map = gson.fromJson(json, Map::class.java) as? Map<String, String>
            if (map != null && map.isNotEmpty()) {
                map.filter { (key, value) -> value != key && value.isNotBlank() }.values.toList()
            } else {
                // 旧格式是数组，可能直接存的包名，如果包含点号则过滤
                val list = gson.fromJson(json, Array<String>::class.java).toList()
                list.filter { !it.contains(".") }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseJsonArray(json: String): List<String> {
        return try {
            gson.fromJson(json, Array<String>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 解析 instructionSets JSON 字符串为 SkillInstructionSet 列表
     */
    fun parseInstructionSets(json: String): List<SkillInstructionSet> {
        return try {
            if (json.isBlank() || json == "[]") {
                emptyList()
            } else {
                val type = object : TypeToken<List<SkillInstructionSet>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("UserSkillViewModel", "Failed to parse instruction sets", e)
            emptyList()
        }
    }

    /**
     * 序列化 SkillInstructionSet 列表为 JSON 字符串
     */
    fun serializeInstructionSets(sets: List<SkillInstructionSet>): String {
        return try {
            if (sets.isEmpty()) {
                "[]"
            } else {
                gson.toJson(sets)
            }
        } catch (e: Exception) {
            Log.e("UserSkillViewModel", "Failed to serialize instructionSets", e)
            "[]"
        }
    }

    /**
     * 获取指定按键集的步骤列表
     */
    suspend fun getInstructionSetSteps(setId: String): List<com.withcareer.screenpal_android.data.room.InstructionStepEntity> {
        return instructionRepository.getSteps(setId)
    }

    /**
     * 保存 Skill（新建或更新）
     */
    fun saveSkill(skill: UserSkillEntity) {
        viewModelScope.launch {
            try {
                repository.insertSkill(skill)
                // 刷新 SkillRegistry
                skillRegistry.refreshUserSkills()
            } catch (e: Exception) {
                // 错误处理
            }
        }
    }

    /**
     * 根据 ID 获取 Skill
     */
    suspend fun getSkillById(id: String): UserSkillEntity? {
        return repository.getSkillById(id)
    }

    /**
     * 删除 Skill
     */
    fun deleteSkill(id: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.deleteSkill(id)
                // 刷新 SkillRegistry
                skillRegistry.refreshUserSkills()
                onSuccess?.invoke()
            } catch (e: Exception) {
                // 错误处理
            }
        }
    }

    /**
     * 设置当前正在编辑的技能ID
     */
    fun setCurrentEditingSkillId(skillId: String?) {
        _currentEditingSkillId.value = skillId
    }

    /**
     * 保存录制的步骤（用于导航传递）
     */
    fun setPendingRecordedSteps(steps: List<InstructionStep>?) {
        _pendingRecordedSteps.value = steps
    }

    fun setPendingRecordingResult(result: RecordingResult?) {
        _pendingRecordingResult.value = result
    }

    fun setPendingEditingInstructionSet(set: SkillInstructionSet?) {
        _pendingEditingInstructionSet.value = set
    }

    fun setLastSavedInstructionSet(skillId: String, instructionSet: SkillInstructionSet) {
        _lastSavedInstructionSet.value = SavedInstructionSetUpdate(skillId, instructionSet)
    }

    /**
     * 清空录制的步骤
     */
    fun clearPendingRecordedSteps() {
        _pendingRecordedSteps.value = null
    }

    fun clearPendingRecordingResult() {
        _pendingRecordingResult.value = null
    }

    fun clearPendingEditingInstructionSet() {
        _pendingEditingInstructionSet.value = null
    }

    fun clearLastSavedInstructionSet() {
        _lastSavedInstructionSet.value = null
    }

    /**
     * 添加按键集到指定技能
     */
    suspend fun addInstructionSetToSkill(skillId: String, instructionSet: SkillInstructionSet): Boolean {
        return try {
            val skill = repository.getSkillById(skillId) ?: return false

            // 解析现有按键集
            val existingSets = parseInstructionSets(skill.instructionSets)
            val updatedSets = existingSets + instructionSet

            // 序列化并更新
            val updatedSkill = skill.copy(
                instructionSets = serializeInstructionSets(updatedSets),
                updatedAt = System.currentTimeMillis()
            )

            repository.updateSkill(updatedSkill)
            skillRegistry.loadUserSkills() // 重新加载技能
            true
        } catch (e: Exception) {
            Log.e("UserSkillViewModel", "添加按键集失败", e)
            false
        }
    }

    /**
     * 新增或更新按键集（按ID覆盖）
     */
    suspend fun upsertInstructionSetToSkill(skillId: String, instructionSet: SkillInstructionSet): Boolean {
        if (skillId == NEW_SKILL_ID) {
            // 新建技能时的临时处理，不写入数据库，只通过 Flow 通知 UI 更新
            setLastSavedInstructionSet(skillId, instructionSet)
            return true
        }

        return try {
            val skill = repository.getSkillById(skillId) ?: return false
            val existingSets = parseInstructionSets(skill.instructionSets)
            val updatedSets = existingSets.map { existing ->
                if (existing.id == instructionSet.id) instructionSet else existing
            }.let { list ->
                if (list.any { it.id == instructionSet.id }) list else list + instructionSet
            }

            val updatedSkill = skill.copy(
                instructionSets = serializeInstructionSets(updatedSets),
                updatedAt = System.currentTimeMillis()
            )
            repository.updateSkill(updatedSkill)
            skillRegistry.loadUserSkills()
            true
        } catch (e: Exception) {
            Log.e("UserSkillViewModel", "更新按键集失败", e)
            false
        }
    }

    /**
     * 删除指定按键集
     */
    suspend fun removeInstructionSetFromSkill(skillId: String, instructionSetId: String): Boolean {
        if (skillId == NEW_SKILL_ID) {
            // 新建技能时的临时处理，UI层自己维护列表，这里只需要返回成功
            return true
        }

        return try {
            val skill = repository.getSkillById(skillId) ?: return false
            val existingSets = parseInstructionSets(skill.instructionSets)
            val updatedSets = existingSets.filterNot { it.id == instructionSetId }

            val updatedSkill = skill.copy(
                instructionSets = serializeInstructionSets(updatedSets),
                updatedAt = System.currentTimeMillis()
            )
            repository.updateSkill(updatedSkill)
            skillRegistry.loadUserSkills()
            true
        } catch (e: Exception) {
            Log.e("UserSkillViewModel", "删除按键集失败", e)
            false
        }
    }
}

/**
 * UserSkillViewModel 的 Factory
 */
class UserSkillViewModelFactory(
    private val repository: UserSkillRepository,
    private val skillRegistry: SkillRegistry,
    private val instructionRepository: InstructionRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserSkillViewModel::class.java)) {
            return UserSkillViewModel(repository, skillRegistry, instructionRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
