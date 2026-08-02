package com.withcareer.screenpal_android.ui_v2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.core.mcp.ToolRegistry
import com.withcareer.screenpal_android.core.skill.Skill
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.data.room.UserSkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class V2AbilityItemKind {
    SKILL,
    MCP
}

enum class V2AbilityFilter {
    ALL,
    SKILL,
    MCP,
    BUILTIN_SKILLS,
    MY_SKILLS
}

data class V2AbilityItem(
    val key: String,
    val kind: V2AbilityItemKind,
    val id: String,
    val title: String,
    val description: String,
    val meta: String? = null,
    val useText: String,
    val isEnabled: Boolean,
    val isUserSkill: Boolean = false,
    val userSkillId: String? = null
)

data class V2AbilityCenterUiState(
    val query: String = "",
    val filter: V2AbilityFilter = V2AbilityFilter.SKILL,
    val items: List<V2AbilityItem> = emptyList()
)

class V2AbilityCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ScreenPalApplication
    private val userSkillDao = app.database.userSkillDao()
    private val skillRegistry = app.skillRegistry
    private val preferencesRepository = PreferencesRepository(application)

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(V2AbilityFilter.SKILL)

    private val builtinSkillItems: List<V2AbilityItem> = run {
        skillRegistry.initialize()
        skillRegistry.getAllTaskSkills()
            .filterNot { it.metadata.id.startsWith("user.") }
            .map(::toBuiltinSkillItem)
    }

    private val mcpToolItems: List<V2AbilityItem> = ToolRegistry.getTools(listOf("*"))
        .map { tool ->
            V2AbilityItem(
                key = "mcp_${tool.name}",
                kind = V2AbilityItemKind.MCP,
                id = tool.name,
                title = tool.name,
                description = tool.description,
                meta = "工具",
                useText = tool.usageFormat,
                isEnabled = true
            )
        }

    val uiState: StateFlow<V2AbilityCenterUiState> = combine(
        query,
        filter,
        userSkillDao.getAllSkills(),
        preferencesRepository.disabledSkillIds,
        preferencesRepository.disabledMcpTools
    ) { q, f, userSkills, disabledSkillIds, disabledMcpTools ->
        val userSkillItems = userSkills.map { toUserSkillItem(it, disabledSkillIds) }
        val builtinSkillItems = builtinSkillItems.map { it.copy(isEnabled = it.id !in disabledSkillIds) }
        val mcpItems = mcpToolItems.map { it.copy(isEnabled = it.id !in disabledMcpTools) }

        val merged = (builtinSkillItems + userSkillItems + mcpItems)
            .sortedWith(
                compareBy<V2AbilityItem> { item ->
                    when (item.kind) {
                        V2AbilityItemKind.SKILL -> 0
                        V2AbilityItemKind.MCP -> 1
                    }
                }.thenBy { it.title }
            )

        val filteredByType = when (f) {
            V2AbilityFilter.ALL -> merged
            V2AbilityFilter.SKILL -> merged.filter { it.kind == V2AbilityItemKind.SKILL }
            V2AbilityFilter.MCP -> merged.filter { it.kind == V2AbilityItemKind.MCP }
            V2AbilityFilter.BUILTIN_SKILLS -> merged.filter { it.kind == V2AbilityItemKind.SKILL && !it.isUserSkill }
            V2AbilityFilter.MY_SKILLS -> merged.filter { it.kind == V2AbilityItemKind.SKILL && it.isUserSkill }
        }

        val qClean = q.trim()
        val filtered = if (qClean.isBlank()) {
            filteredByType
        } else {
            val lower = qClean.lowercase()
            filteredByType.filter {
                it.title.lowercase().contains(lower) || it.description.lowercase().contains(lower)
            }
        }

        V2AbilityCenterUiState(
            query = q,
            filter = f,
            items = filtered
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), V2AbilityCenterUiState())

    fun updateQuery(value: String) {
        query.update { value }
    }

    fun updateFilter(value: V2AbilityFilter) {
        filter.update { value }
    }

    fun setSkillEnabled(skillId: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.setSkillEnabled(skillId, enabled)
        }
    }

    fun setMcpEnabled(toolName: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.setMcpToolEnabled(toolName, enabled)
        }
    }

    private fun toBuiltinSkillItem(skill: Skill): V2AbilityItem {
        return V2AbilityItem(
            key = "skill_builtin_${skill.metadata.id}",
            kind = V2AbilityItemKind.SKILL,
            id = skill.metadata.id,
            title = skill.metadata.name,
            description = skill.metadata.description,
            meta = "内置 · v${skill.metadata.version}",
            useText = skill.metadata.name,
            isEnabled = true,
            isUserSkill = false
        )
    }

    private fun toUserSkillItem(entity: UserSkillEntity, disabledSkillIds: Set<String>): V2AbilityItem {
        val skillId = "user.${entity.id}"
        val meta = buildString {
            if (entity.isImported) append("已下载") else append("我创建的")
            append(" · v${entity.version}")
        }
        return V2AbilityItem(
            key = "skill_user_${entity.id}",
            kind = V2AbilityItemKind.SKILL,
            id = skillId,
            title = entity.name,
            description = entity.description,
            meta = meta,
            useText = entity.name,
            isEnabled = skillId !in disabledSkillIds,
            isUserSkill = true,
            userSkillId = entity.id
        )
    }
}
