package com.withcareer.screenpal_android.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.data.model.InstructionStep
import com.withcareer.screenpal_android.data.room.UserSkillEntity
import com.withcareer.screenpal_android.data.room.InstructionSetEntity
import com.withcareer.screenpal_android.data.room.InstructionStepEntity
import com.withcareer.screenpal_android.data.model.SkillInstructionSet
import com.withcareer.screenpal_android.recording.RecordingService
import com.withcareer.screenpal_android.ui.component.AppSelectorDialog
import com.withcareer.screenpal_android.ui.component.InstructionSetManagerSection
import com.withcareer.screenpal_android.ui.viewmodel.UserSkillViewModel
import com.withcareer.screenpal_android.ui_v2.component.V2ScreenTopBar
import com.withcareer.screenpal_android.util.AccessibilityServiceHelper
import com.withcareer.screenpal_android.util.DeviceUtils
import com.withcareer.screenpal_android.util.OperationCheckFailure
import com.withcareer.screenpal_android.util.OperationGuard
import com.withcareer.screenpal_android.util.ToastUtils
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 用户 Skill 创建/编辑界面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserSkillEditScreen(
    skillId: String? = null,
    viewModel: UserSkillViewModel,
    onBack: () -> Unit,
    onNavigateToInstructionSetEdit: () -> Unit = {} // 导航到按键集编辑页面的回调
) {
    val gson = remember { Gson() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val operationGuard = remember(context) { OperationGuard(context) }

    // 工具分类数据类
    data class ToolCategory(
        val name: String,
        val tools: List<String>,
        val defaultExpanded: Boolean = false
    )

    // 工具分类列表
    val toolCategories = listOf(
        ToolCategory(
            name = stringResource(R.string.tool_category_basic),
            tools = listOf("Tap", "Wait", "Type", "Swipe", "Scroll", "Back", "Home"),
            defaultExpanded = true
        ),
        ToolCategory(
            name = stringResource(R.string.tool_category_launch),
            tools = listOf("Launch")
        ),
        ToolCategory(
            name = stringResource(R.string.tool_category_gesture),
            tools = listOf("LongPress", "DoubleTap", "TapAndSwipe", "ZoomIn", "ZoomOut")
        ),
        ToolCategory(
            name = stringResource(R.string.tool_category_system),
            tools = listOf("Recents", "Notifications", "QuickSettings", "LockScreen", "Power", "SplitScreen", "Screenshot")
        ),
        ToolCategory(
            name = stringResource(R.string.tool_category_input),
            tools = listOf("Enter", "Copy", "Cut", "Paste")
        ),
        ToolCategory(
            name = stringResource(R.string.tool_category_assistant),
            tools = listOf("WebSearch", "RecordNote", "Interact", "Take_over", "Replan", "RunInstructionSet")
        ),
        ToolCategory(
            name = stringResource(R.string.tool_category_finish),
            tools = listOf("Finish")
        )
    )

    // 所有工具列表（扁平化，用于存储）
    val allTools = toolCategories.flatMap { it.tools }

    // 工具名到中文的映射
    val toolNameMap = mapOf(
        // 基础操作
        "Tap" to stringResource(R.string.tool_name_tap),
        "Wait" to stringResource(R.string.tool_name_wait),
        "Type" to stringResource(R.string.tool_name_type),
        "Swipe" to stringResource(R.string.tool_name_swipe),
        "Scroll" to stringResource(R.string.tool_name_scroll),
        "Back" to stringResource(R.string.tool_name_back),
        "Home" to stringResource(R.string.tool_name_home),
        // 启动
        "Launch" to stringResource(R.string.tool_name_launch),
        // 手势操作
        "LongPress" to stringResource(R.string.tool_name_long_press),
        "DoubleTap" to stringResource(R.string.tool_name_double_tap),
        "TapAndSwipe" to stringResource(R.string.tool_name_tap_and_swipe),
        "ZoomIn" to stringResource(R.string.tool_name_zoom_in),
        "ZoomOut" to stringResource(R.string.tool_name_zoom_out),
        // 系统操作
        "Recents" to stringResource(R.string.tool_name_recents),
        "Notifications" to stringResource(R.string.tool_name_notifications),
        "QuickSettings" to stringResource(R.string.tool_name_quick_settings),
        "LockScreen" to stringResource(R.string.tool_name_lock_screen),
        "Power" to stringResource(R.string.tool_name_power_menu),
        "SplitScreen" to stringResource(R.string.tool_name_split_screen),
        "Screenshot" to stringResource(R.string.tool_name_screenshot),
        // 输入控制
        "Enter" to stringResource(R.string.tool_name_enter),
        "Copy" to stringResource(R.string.tool_name_copy),
        "Cut" to stringResource(R.string.tool_name_cut),
        "Paste" to stringResource(R.string.tool_name_paste),
        // 助手功能
        "WebSearch" to stringResource(R.string.tool_name_web_search),
        "RecordNote" to stringResource(R.string.tool_name_record_note),
        "Interact" to stringResource(R.string.tool_name_interact),
        "Take_over" to stringResource(R.string.tool_name_take_over),
        "Replan" to stringResource(R.string.tool_name_replan),
        "RunInstructionSet" to stringResource(R.string.tool_name_run_instruction_set),
        // 完成
        "Finish" to stringResource(R.string.tool_name_finish)
    )

    // 分类展开状态
    val expandedCategories = remember {
        mutableStateMapOf<String, Boolean>().apply {
            toolCategories.forEach { category ->
                put(category.name, category.defaultExpanded)
            }
        }
    }

    // 包名到应用名的映射（优先使用保存的，如果不存在则查询）
    // 使用 rememberSaveable 确保 Activity 重建时不丢失
    val appNameMapSaver = remember {
        Saver<Map<String, String>, String>(
            save = { map -> gson.toJson(map) },
            restore = { json ->
                try {
                    gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type) as Map<String, String>
                } catch (e: Exception) {
                    emptyMap()
                }
            }
        )
    }
    var packageToAppNameMap by rememberSaveable(skillId, stateSaver = appNameMapSaver) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var savedAppNameMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // 表单状态
    var name by rememberSaveable(skillId) { mutableStateOf("") }
    var description by rememberSaveable(skillId) { mutableStateOf("") }
    var targetAppsInput by rememberSaveable(skillId) { mutableStateOf("") }
    var instructionValue by rememberSaveable(skillId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var isInstructionPreview by rememberSaveable(skillId) { mutableStateOf(false) }
    var selectedTools by rememberSaveable(skillId) { mutableStateOf<List<String>>(allTools) } // 默认全选
    val instructionSetsSaver = remember(viewModel) {
        Saver<List<SkillInstructionSet>, String>(
            save = { sets -> viewModel.serializeInstructionSets(sets) },
            restore = { json -> viewModel.parseInstructionSets(json) }
        )
    }
    var skillInstructionSets by rememberSaveable(skillId, stateSaver = instructionSetsSaver) {
        mutableStateOf<List<SkillInstructionSet>>(emptyList())
    }
    var isLoading by remember { mutableStateOf(true) }
    var existingSkill by remember { mutableStateOf<UserSkillEntity?>(null) }
    var loadedSkillId by rememberSaveable { mutableStateOf<String?>(null) }

    // 按键集编辑对话框已移除，改为全屏编辑页

    // 监听录制完成广播
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == RecordingService.ACTION_RECORDING_COMPLETED) {
                    // 获取录制来源
                    val source = intent.getStringExtra(RecordingService.EXTRA_RECORDING_SOURCE)
                        ?: RecordingService.SOURCE_NORMAL

                    Log.d("UserSkillEditScreen", "Recording completion broadcast received")

                    // 只处理来自技能编辑器的录制
                    if (source != RecordingService.SOURCE_SKILL_EDITOR) {
                        Log.d("UserSkillEditScreen", "Ignored recording from unrelated source")
                        return
                    }

                    // 获取录制结果
                    val result = RecordingService.lastRecordingResult
                    Log.d("UserSkillEditScreen", "Recording result received: actions=${result?.actions?.size ?: 0}")

                    if (result != null && result.actions.isNotEmpty()) {
                        // 转换为 InstructionStep 列表（RecordedAction 使用 paramsJson，无 description）
                        val steps = result.actions.mapIndexed { index, action ->
                            InstructionStep(
                                orderIndex = index,
                                actionType = action.actionType,
                                actionParams = action.paramsJson,
                                description = action.actionType,
                                delayBefore = action.delayBefore
                            )
                        }

                        Log.d("UserSkillEditScreen", "转换步骤完成: steps=${steps.size}")

                        // 保存录制的步骤和完整录制结果到ViewModel
                        viewModel.setPendingRecordedSteps(steps)
                        viewModel.setPendingRecordingResult(result)
                        Log.d("UserSkillEditScreen", "步骤已保存到ViewModel")

                        // 导航到按键集编辑页面
                        Log.d("UserSkillEditScreen", "Preparing editor navigation")
                        onNavigateToInstructionSetEdit()
                        Log.d("UserSkillEditScreen", "导航回调已调用")

                        context?.let { ToastUtils.show(it, context.getString(R.string.recording_completed_edit_instruction_set), Toast.LENGTH_SHORT) }
                    } else {
                        Log.e("UserSkillEditScreen", "录制结果为空或没有操作")
                        context?.let { ToastUtils.show(it, context.getString(R.string.recording_failed_or_empty), Toast.LENGTH_SHORT) }
                    }
                }
            }
        }

        val filter = IntentFilter(RecordingService.ACTION_RECORDING_COMPLETED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // 忽略异常
            }
        }
    }

    // 加载现有 Skill 数据（编辑模式）
    LaunchedEffect(skillId) {
        if (skillId != null && loadedSkillId != skillId) {
            val skill = viewModel.getSkillById(skillId)
            if (skill != null) {
                existingSkill = skill
                name = skill.name
                description = skill.description
                // 解析 targetApps：支持对象格式和旧数组格式
                    val (targetApps, targetAppsMapFromField) = try {
                        val map = gson.fromJson(skill.targetApps, Map::class.java) as? Map<*, *>
                        if (map != null && map.isNotEmpty()) {
                            // 对象格式
                            val normalizedMap = map.mapKeys { it.key.toString() }.mapValues { it.value.toString() }
                            normalizedMap.keys.toList() to normalizedMap
                        } else {
                            // 兼容旧格式：数组格式
                            val packages = gson.fromJson(skill.targetApps, Array<String>::class.java).toList()
                            packages to packages.associateWith { it }
                        }
                } catch (e: Exception) {
                        emptyList<String>() to emptyMap<String, String>()
                }
                targetAppsInput = targetApps.joinToString("\n")
                instructionValue = TextFieldValue(skill.instruction)
                selectedTools = try {
                    val tools = gson.fromJson(skill.allowedTools, Array<String>::class.java).toList()
                    // 如果技能中没有设置工具或为空，则默认全选
                    if (tools.isEmpty()) allTools else tools
                } catch (e: Exception) {
                    // 解析失败时默认全选
                    allTools
                }

                // 加载保存的应用名映射
                if (!skill.targetAppNames.isNullOrBlank()) {
                    try {
                        val savedMap = gson.fromJson(
                            skill.targetAppNames,
                            object : TypeToken<Map<String, String>>() {}.type
                        ) as Map<String, String>
                        savedAppNameMap = savedMap
                    } catch (e: Exception) {
                        // 解析失败，忽略
                    }
                }
                // 合并：优先使用保存的映射，其次使用 targetApps 中的值；缺失则回退包名
                packageToAppNameMap = savedAppNameMap + targetAppsMapFromField.filterKeys { it !in savedAppNameMap }

                // 解析按键集数据
                skillInstructionSets = viewModel.parseInstructionSets(skill.instructionSets)
                loadedSkillId = skillId
            }
        } else if (skillId == null) {
            loadedSkillId = null
        }
        isLoading = false
    }

    // 异步加载缺失的应用名（在页面打开后执行，不阻塞UI）
    LaunchedEffect(packageToAppNameMap, targetAppsInput) {
        val targetApps = targetAppsInput.lines().filter { it.isNotBlank() && it != "*" }
        if (targetApps.isEmpty()) return@LaunchedEffect

        // 找出需要加载应用名的包名（应用名为空或等于包名）
        val packagesNeedingNames = targetApps.filter { pkg ->
            val name = packageToAppNameMap[pkg]
            name.isNullOrBlank() || name == pkg
        }

        if (packagesNeedingNames.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val systemAppNames = DeviceUtils.getInstalledAppsInfo(context)
                    .associate { it.packageName to it.label }

                // 更新映射，只更新缺失或无效的应用名
                val newMappings = packagesNeedingNames.mapNotNull { pkg ->
                    val systemName = systemAppNames[pkg]
                    if (!systemName.isNullOrBlank()) {
                        pkg to systemName
                    } else {
                        null
                    }
                }

                if (newMappings.isNotEmpty()) {
                    packageToAppNameMap = packageToAppNameMap + newMappings
                }
            }
        }
    }

    // 表单验证
    val isFormValid = name.isNotBlank() &&
            description.isNotBlank() &&
            targetAppsInput.isNotBlank() &&
            instructionValue.text.isNotBlank()

    var hasAttemptedSave by remember { mutableStateOf(false) }
    val showErrors = hasAttemptedSave

    val instructionSets by viewModel.instructionSets.collectAsState()
    val lastSavedInstructionSetUpdate by viewModel.lastSavedInstructionSet.collectAsState()
    val instructionTitles = remember(instructionSets) {
        instructionSets.map { it.title }.distinct().sorted()
    }
    var mentionMenuExpanded by remember { mutableStateOf(false) }
    var activeMentionAtIndex by remember { mutableStateOf<Int?>(null) }

    // 可用的录制列表（用于导入）
    var availableRecordings by remember { mutableStateOf<List<Pair<InstructionSetEntity, List<InstructionStepEntity>>>>(emptyList()) }

    LaunchedEffect(lastSavedInstructionSetUpdate) {
        val update = lastSavedInstructionSetUpdate ?: return@LaunchedEffect
        // 如果是当前编辑的技能，或者是新建技能时的临时ID，都进行更新
        if (skillId == update.skillId || (skillId == null && update.skillId == UserSkillViewModel.NEW_SKILL_ID)) {
            val exists = skillInstructionSets.any { it.id == update.instructionSet.id }
            skillInstructionSets = if (exists) {
                skillInstructionSets.map { set ->
                    if (set.id == update.instructionSet.id) update.instructionSet else set
                }
            } else {
                skillInstructionSets + update.instructionSet
            }
            viewModel.clearLastSavedInstructionSet()
        }
    }

    // 加载可用录制
    LaunchedEffect(instructionSets) {
        withContext(Dispatchers.IO) {
            val recordings = instructionSets
                .filter { it.description == "录制生成" }
                .mapNotNull { entity ->
                    val steps = viewModel.getInstructionSetSteps(entity.id)
                    if (steps.isNotEmpty()) entity to steps else null
                }
            availableRecordings = recordings
        }
    }

    Scaffold(
        topBar = {
            V2ScreenTopBar(
                title = if (skillId == null) stringResource(R.string.skill_create_title) else stringResource(R.string.skill_edit_title),
                onBack = onBack
            )
        },
        bottomBar = {
            if (!isLoading) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                hasAttemptedSave = true
                                if (!isFormValid) {
                                    ToastUtils.show(context, context.getString(R.string.please_complete_required_fields), Toast.LENGTH_SHORT)
                                    return@Button
                                }
                                // 保存 Skill
                                val targetApps = targetAppsInput
                                    .split("\n")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }

                                // 构建应用名映射（只保存当前选择的应用）
                                val appNameMap = targetApps.associateWith { packageName ->
                                    packageToAppNameMap[packageName] ?: packageName
                                }

                                // 保存为 JSON 对象格式：{"package": "appName"}
                                val targetAppsJson = gson.toJson(appNameMap)

                                // 序列化按键集
                                val instructionSetsJson = viewModel.serializeInstructionSets(skillInstructionSets)

                                val skill = if (skillId != null) {
                                    // 编辑：基于原 Skill copy，避免把 isPublished/remoteId/isImported/author 等字段重置为默认值
                                    (existingSkill ?: UserSkillEntity(
                                        id = skillId,
                                        name = name,
                                        description = description,
                                        targetApps = targetAppsJson,
                                        instruction = instructionValue.text
                                    )).copy(
                                        name = name,
                                        description = description,
                                        targetApps = targetAppsJson,
                                        targetAppNames = gson.toJson(appNameMap),
                                        instruction = instructionValue.text,
                                        allowedTools = gson.toJson(allTools),
                                        instructionSets = instructionSetsJson,
                                        tags = "[]",
                                        updatedAt = System.currentTimeMillis()
                                    )
                                } else {
                                    // 新建
                                    UserSkillEntity(
                                        id = UUID.randomUUID().toString(),
                                        name = name,
                                        description = description,
                                        targetApps = targetAppsJson,
                                        targetAppNames = gson.toJson(appNameMap),
                                        instruction = instructionValue.text,
                                        allowedTools = gson.toJson(allTools),
                                        instructionSets = instructionSetsJson,
                                        tags = "[]",
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                                viewModel.saveSkill(skill)
                                onBack()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 技能名称
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.skill_name_label)) },
                        placeholder = { Text(stringResource(R.string.skill_name_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = showErrors && name.isBlank(),
                        shape = RoundedCornerShape(18.dp)
                    )
                }

                // 2. 技能描述
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.skill_description_label)) },
                        placeholder = { Text(stringResource(R.string.skill_description_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        isError = showErrors && description.isBlank(),
                        shape = RoundedCornerShape(18.dp)
                    )
                }

                // 3. 目标应用
                item {
                    var showAppSelector by remember { mutableStateOf(false) }
                    val selectedApps = remember(targetAppsInput) {
                        targetAppsInput.lines().filter { it.isNotBlank() }
                    }
                    val hasWildcard = selectedApps.contains("*")
                    val maxApps = 5

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.target_apps_label),
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (!hasWildcard && selectedApps.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.target_apps_count_format, selectedApps.size, maxApps),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedApps.size >= maxApps) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 使用 FlowRow 显示已选应用标签
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // "应用于所有"选项
                            if (hasWildcard) {
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        // 移除通配符
                                        targetAppsInput = ""
                                    },
                                    label = {
                                        Text(
                                            stringResource(R.string.app_apply_all_label),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.delete),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            } else {
                                // 已选应用的标签
                                selectedApps.forEach { packageName ->
                                    val appName = packageToAppNameMap[packageName]
                                    val displayText = if (!appName.isNullOrBlank() && appName != packageName) {
                                        appName
                                    } else {
                                        packageName
                                    }
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            // 点击标签本身移除该应用
                                            val newList = selectedApps.filter { it != packageName }
                                            targetAppsInput = newList.joinToString("\n")
                                        },
                                        label = {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                Text(
                                                    displayText,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.delete),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }

                                // "应用于所有"按钮（仅当没有选择具体应用时显示）
                                if (selectedApps.isEmpty()) {
                                    InputChip(
                                        selected = false,
                                        onClick = {
                                            targetAppsInput = "*"
                                        },
                                        label = { Text(stringResource(R.string.app_apply_all_label)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Apps,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }

                                // 添加应用按钮（仅当未达到上限且没有通配符时显示）
                                if (selectedApps.size < maxApps) {
                                    InputChip(
                                        selected = false,
                                        onClick = { showAppSelector = true },
                                        label = { Text(stringResource(R.string.add_app_label)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // 提示信息
                        Spacer(modifier = Modifier.height(4.dp))
                        if (targetAppsInput.isBlank()) {
                            Text(
                                stringResource(R.string.target_apps_hint_select_one),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (showErrors) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        } else if (hasWildcard) {
                            Text(
                                stringResource(R.string.target_apps_hint_all),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (selectedApps.size >= maxApps) {
                            Text(
                                stringResource(R.string.target_apps_limit_reached_format, maxApps),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                stringResource(R.string.target_apps_limit_format, maxApps),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                            // 应用选择器对话框
                    if (showAppSelector) {
                        AppSelectorDialog(
                            preSelectedPackages = selectedApps,
                            onDismiss = { showAppSelector = false },
                            onConfirm = { selectedPackages, appNames ->
                                // 限制最多选择 5 个
                                val limitedPackages = selectedPackages.take(maxApps)
                                targetAppsInput = limitedPackages.joinToString("\n")

                                // 更新应用名映射
                                packageToAppNameMap = packageToAppNameMap + appNames

                                showAppSelector = false
                            }
                        )
                    }
                }

                // 4. 详细指令
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.skill_detail_instruction_label),
                                style = MaterialTheme.typography.labelLarge
                            )

                            // Markdown 预览切换
                            TextButton(
                                onClick = { isInstructionPreview = !isInstructionPreview },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isInstructionPreview) Icons.Default.Edit else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (isInstructionPreview) stringResource(R.string.edit) else stringResource(R.string.preview),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isInstructionPreview) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 200.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            ) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    if (instructionValue.text.isBlank()) {
                                        Text(
                                            stringResource(R.string.instruction_preview_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        MarkdownText(
                                            markdown = instructionValue.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }
                            }
                        } else {
                            val filteredMentions = remember(instructionTitles) {
                                instructionTitles.take(8)
                            }
                            val menuExpanded = mentionMenuExpanded && filteredMentions.isNotEmpty()

                            Box {
                                OutlinedTextField(
                                    value = instructionValue,
                                    onValueChange = { newValue ->
                                        val oldValue = instructionValue
                                        instructionValue = newValue
                                        val selectionEnd = newValue.selection.end
                                            .coerceIn(0, newValue.text.length)
                                        val justTypedAt = newValue.text.length == oldValue.text.length + 1 &&
                                                selectionEnd > 0 &&
                                                newValue.text[selectionEnd - 1] == '@'
                                        if (justTypedAt) {
                                            activeMentionAtIndex = selectionEnd - 1
                                            mentionMenuExpanded = true
                                        } else {
                                            mentionMenuExpanded = false
                                            activeMentionAtIndex = null
                                        }
                                    },
                                    placeholder = { Text(stringResource(R.string.skill_instruction_placeholder_long)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 200.dp),
                                    minLines = 8,
                                    isError = showErrors && instructionValue.text.isBlank(),
                                    shape = RoundedCornerShape(18.dp)
                                )

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { mentionMenuExpanded = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    filteredMentions.forEach { title ->
                                        DropdownMenuItem(
                                            text = { Text(title) },
                                            onClick = {
                                                val atIndex = activeMentionAtIndex
                                                if (atIndex != null) {
                                                    val newText = instructionValue.text.replaceRange(
                                                        atIndex + 1,
                                                        atIndex + 1,
                                                        title
                                                    )
                                                    val newCursor = atIndex + 1 + title.length
                                                    instructionValue = instructionValue.copy(
                                                        text = newText,
                                                        selection = TextRange(newCursor, newCursor)
                                                    )
                                                }
                                                mentionMenuExpanded = false
                                                activeMentionAtIndex = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. 按键集管理
                item {
                    InstructionSetManagerSection(
                        instructionSets = skillInstructionSets,
                        onAdd = {
                            coroutineScope.launch {
                                val result = operationGuard.checkWithResult(
                                    requireApiKey = false,
                                    requireAccessibility = true,
                                    requireOverlay = true
                                )
                                if (!result.passed) {
                                    when (result.failure) {
                                        OperationCheckFailure.ACCESSIBILITY_NOT_READY -> {
                                            AccessibilityServiceHelper.goToAccessibilitySettings(context)
                                        }
                                        OperationCheckFailure.OVERLAY_DENIED -> {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        }
                                        else -> {}
                                    }
                                    return@launch
                                }
                                viewModel.setPendingEditingInstructionSet(null)
                                viewModel.setPendingRecordedSteps(null)
                                viewModel.setPendingRecordingResult(null)
                                RecordingService.start(context, RecordingService.SOURCE_SKILL_EDITOR)
                                ToastUtils.show(
                                    context,
                                    context.getString(R.string.recording_control_started),
                                    Toast.LENGTH_LONG
                                )
                            }
                        },
                        onEdit = { set ->
                            viewModel.setPendingEditingInstructionSet(set)
                            viewModel.setPendingRecordedSteps(null)
                            viewModel.setPendingRecordingResult(null)
                            onNavigateToInstructionSetEdit()
                        },
                        onDelete = { set ->
                            val previous = skillInstructionSets
                            skillInstructionSets = skillInstructionSets.filterNot { it.id == set.id }

                            // 只有在编辑已有技能时才需要调用 ViewModel 删除
                            if (skillId != null) {
                                coroutineScope.launch {
                                    val success = viewModel.removeInstructionSetFromSkill(
                                        skillId = skillId,
                                        instructionSetId = set.id
                                    )
                                    if (!success) {
                                        skillInstructionSets = previous
                                        ToastUtils.show(context, context.getString(R.string.delete_instruction_set_failed), Toast.LENGTH_SHORT)
                                    } else {
                                        ToastUtils.show(context, context.getString(R.string.instruction_set_deleted), Toast.LENGTH_SHORT)
                                    }
                                }
                            } else {
                                // 新建技能模式下，本地删除即可
                                ToastUtils.show(context, context.getString(R.string.instruction_set_deleted), Toast.LENGTH_SHORT)
                            }
                        }
                    )
                }

                // 底部留空，防止被固定底栏遮挡
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

}
