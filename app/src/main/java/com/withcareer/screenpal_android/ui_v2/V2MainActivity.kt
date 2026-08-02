package com.withcareer.screenpal_android.ui_v2

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.withcareer.screenpal_android.overlay.FloatingAssistantService
import com.withcareer.screenpal_android.ui.viewmodel.SettingsViewModel

import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import com.withcareer.screenpal_android.util.ToastUtils
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.ui.screen.UserSkillEditScreen
import com.withcareer.screenpal_android.ui.theme.Typography
import com.withcareer.screenpal_android.ui.theme.Shapes
import com.withcareer.screenpal_android.ui.viewmodel.UserSkillViewModel
import com.withcareer.screenpal_android.ui.viewmodel.UserSkillViewModelFactory
import com.withcareer.screenpal_android.ui_v2.screen.V2AbilityDetailRoute
import com.withcareer.screenpal_android.ui_v2.screen.V2AbilityCenterRoute
import com.withcareer.screenpal_android.ui_v2.screen.V2InstructionSetEditRoute
import com.withcareer.screenpal_android.ui_v2.screen.V2ChatRoute
import com.withcareer.screenpal_android.ui_v2.screen.V2PermissionGuideRoute
import com.withcareer.screenpal_android.ui_v2.screen.V2QuickCommandsRoute
import com.withcareer.screenpal_android.ui_v2.screen.V2MySkillsRoute
import com.withcareer.screenpal_android.ui_v2.screen.V2SearchRoute
import com.withcareer.screenpal_android.ui_v2.screen.V2ScheduledTaskEditRoute
import com.withcareer.screenpal_android.ui_v2.screen.V2ScheduledTaskListRoute
import com.withcareer.screenpal_android.ui_v2.screen.V2SettingsRoute
import com.withcareer.screenpal_android.ui_v2.util.ProvideV2WindowInsets

private val V2LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E6BD6),
    secondary = Color(0xFF1557B0),
    tertiary = Color(0xFF2E7BEA),
    background = Color(0xFFEFF6FF),
    surface = Color(0xFFEFF6FF)
)

private val V2DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8CCBFF),
    secondary = Color(0xFF64B5F6),
    tertiary = Color(0xFFAEC7FF),
    background = Color(0xFF0E1723),
    surface = Color(0xFF0E1723)
)

class V2MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            V2Theme {
                ProvideV2WindowInsets {
                    V2MainScreen()
                }
            }
        }
    }
}

private const val ABILITY_CENTER_PREFILL_KEY = "ability_center_prefill_key"

@Composable
private fun V2Theme(
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) V2DarkColorScheme else V2LightColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
private fun V2MainScreen() {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. Permission Check
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        settingsViewModel.checkRecordAudioPermission()
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // 2. Service Management (Accessibility & Floating Assistant)
    // 监听生命周期，在 Resume 时刷新无障碍服务状态并尝试启动悬浮球
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsViewModel.checkAccessibilityService()

                val shouldStartService = (settingsState.isFloatingAssistantEnabled ||
                    (settingsState.isWakeupEnabled && settingsState.apiKey.isNotBlank())) &&
                    Settings.canDrawOverlays(context)

                if (shouldStartService) {
                    FloatingAssistantService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 当设置加载完成且为开启状态时，自动启动服务（处理冷启动场景）
    LaunchedEffect(settingsState) {
        val shouldStartService = (settingsState.isFloatingAssistantEnabled ||
            (settingsState.isWakeupEnabled && settingsState.apiKey.isNotBlank())) &&
            Settings.canDrawOverlays(context)

        if (shouldStartService) {
            FloatingAssistantService.start(context)
        }
    }

    V2AppNavHost()
}

@Composable
private fun V2AppNavHost() {
    val navController = rememberNavController()
    val backStackEntryState = navController.currentBackStackEntryAsState()
    val context = LocalContext.current
    val preferencesRepository = remember { PreferencesRepository(context) }
    val scope = rememberCoroutineScope()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = if (preferencesRepository.getPermissionGuideShownSync()) "chat" else "permission_guide"
    }

    val resolvedStart = startDestination
    if (resolvedStart == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("加载中...")
        }
        return
    }

    // 在NavHost级别创建共享的UserSkillViewModel，供所有技能相关路由使用
    val application = context.applicationContext as ScreenPalApplication
    val sharedUserSkillViewModel: UserSkillViewModel = remember(application) {
        val userSkillRepository = com.withcareer.screenpal_android.data.repository.UserSkillRepository(
            dao = application.database.userSkillDao(),
            context = context
        )
        androidx.lifecycle.ViewModelProvider(
            context as V2MainActivity,
            UserSkillViewModelFactory(
                repository = userSkillRepository,
                skillRegistry = application.skillRegistry,
                instructionRepository = application.instructionRepository
            )
        )[UserSkillViewModel::class.java]
    }

    NavHost(
        navController = navController,
        startDestination = resolvedStart,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(260)
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(260)
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(260)
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(260)
            )
        }
    ) {
        composable("permission_guide") {
            V2PermissionGuideRoute(
                onDone = {
                    scope.launch {
                        preferencesRepository.savePermissionGuideShown(true)
                        navController.navigate("chat") {
                            popUpTo("permission_guide") { inclusive = true }
                        }
                    }
                },
                onSkip = {
                    scope.launch {
                        preferencesRepository.savePermissionGuideShown(true)
                        navController.navigate("chat") {
                            popUpTo("permission_guide") { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("chat") {
            val backStackEntry = backStackEntryState.value
            val prefillText = backStackEntry?.savedStateHandle?.get<String>(ABILITY_CENTER_PREFILL_KEY)
            V2ChatRoute(
                onOpenSettings = { navController.navigate("settings") },
                onOpenScheduledTasks = { navController.navigate("scheduled_tasks") },
                onOpenSearch = { navController.navigate("search") },
                onOpenAbilityCenter = { navController.navigate("my_skills") },
                prefillText = prefillText,
                onConsumePrefillText = {
                    backStackEntry?.savedStateHandle?.remove<String>(ABILITY_CENTER_PREFILL_KEY)
                }
            )
        }

        composable("ability_center") {
            V2AbilityCenterRoute(
                onBack = { navController.popBackStack() },
                onCreateSkill = { navController.navigate("user_skill/edit") },
                onEditSkill = { skillId -> navController.navigate("user_skill/edit?skillId=$skillId") },
                onOpenDetail = { key -> navController.navigate("ability_center/detail/$key") }
            )
        }

        composable("my_skills") {
            V2MySkillsRoute(
                onBack = { navController.popBackStack() },
                onCreateSkill = { navController.navigate("user_skill/edit") },
                onEditSkill = { skillId -> navController.navigate("user_skill/edit?skillId=$skillId") }
            )
        }

        composable(
            route = "ability_center/detail/{key}",
            arguments = listOf(navArgument("key") { type = NavType.StringType })
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString("key") ?: return@composable
            V2AbilityDetailRoute(
                key = key,
                onBack = { navController.popBackStack() },
                onUse = { text ->
                    runCatching {
                        navController.getBackStackEntry("chat").savedStateHandle.set(ABILITY_CENTER_PREFILL_KEY, text)
                    }
                    navController.popBackStack("chat", false)
                },
                onEditSkill = { skillId -> navController.navigate("user_skill/edit?skillId=$skillId") }
            )
        }

        composable("quick_commands") {
            V2QuickCommandsRoute(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "user_skill/edit?skillId={skillId}",
            arguments = listOf(
                navArgument("skillId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val skillId = backStackEntry.arguments?.getString("skillId")
            UserSkillEditScreen(
                    skillId = skillId,
                    viewModel = sharedUserSkillViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToInstructionSetEdit = {
                        android.util.Log.d("V2MainActivity", "Navigating to instruction-set editor")
                        // 传递技能ID参数（只在编辑现有技能时）
                        val targetId = skillId ?: UserSkillViewModel.NEW_SKILL_ID
                        android.util.Log.d("V2MainActivity", "Instruction-set edit route selected")
                        navController.navigate("instructionSetEdit/$targetId")
                        android.util.Log.d("V2MainActivity", "Navigation command executed")
                    }
                )
        }

        // 按键集编辑页面
        composable(
            route = "instructionSetEdit/{skillId}",
            arguments = listOf(navArgument("skillId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pendingSteps by sharedUserSkillViewModel.pendingRecordedSteps.collectAsStateWithLifecycle()
            val pendingRecordingResult by sharedUserSkillViewModel.pendingRecordingResult.collectAsStateWithLifecycle()
            val pendingEditingInstructionSet by sharedUserSkillViewModel.pendingEditingInstructionSet.collectAsStateWithLifecycle()
            val skillIdFromNav = backStackEntry.arguments?.getString("skillId")
            val coroutineScope = rememberCoroutineScope()

            android.util.Log.d(
                "V2MainActivity",
                "instructionSetEdit route loaded: pendingStepCount=${pendingSteps?.size ?: 0}, " +
                    "recordingActionCount=${pendingRecordingResult?.actions?.size ?: 0}, " +
                    "hasSkillId=${skillIdFromNav != null}, hasEditingSet=${pendingEditingInstructionSet != null}"
            )

            if ((pendingSteps != null || pendingRecordingResult != null || pendingEditingInstructionSet != null) && skillIdFromNav != null) {
                android.util.Log.d("V2MainActivity", "Showing V2InstructionSetEditRoute")
                V2InstructionSetEditRoute(
                    initialSteps = pendingSteps ?: pendingEditingInstructionSet?.steps ?: emptyList(),
                    recordingResult = pendingRecordingResult,
                    instructionSet = pendingEditingInstructionSet,
                    onSave = { instructionSet ->
                        // 保存按键集到技能
                        coroutineScope.launch {
                            val success = sharedUserSkillViewModel.upsertInstructionSetToSkill(
                                skillId = skillIdFromNav,
                                instructionSet = instructionSet
                            )
                            if (success) {
                                ToastUtils.show(
                                    context,
                                    "按键集已添加",
                                    Toast.LENGTH_SHORT
                                )
                                sharedUserSkillViewModel.setLastSavedInstructionSet(
                                    skillId = skillIdFromNav,
                                    instructionSet = instructionSet
                                )
                                sharedUserSkillViewModel.clearPendingRecordedSteps()
                                sharedUserSkillViewModel.clearPendingRecordingResult()
                                sharedUserSkillViewModel.clearPendingEditingInstructionSet()
                                navController.popBackStack()
                            } else {
                                ToastUtils.show(
                                    context,
                                    "添加按键集失败",
                                    Toast.LENGTH_SHORT
                                )
                            }
                        }
                    },
                    onBack = {
                        sharedUserSkillViewModel.clearPendingRecordedSteps()
                        sharedUserSkillViewModel.clearPendingRecordingResult()
                        sharedUserSkillViewModel.clearPendingEditingInstructionSet()
                        navController.popBackStack()
                    }
                )
            } else {
                // 如果没有待处理的步骤或skillId，返回上一页
                android.util.Log.w("V2MainActivity", "Missing editor data: pendingSteps=${pendingSteps?.size ?: 0}, hasSkillId=${skillIdFromNav != null}; popping back")
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        composable("search") {
            V2SearchRoute(onBack = { navController.popBackStack() })
        }

        composable("settings") {
            V2SettingsRoute(
                onBack = { navController.popBackStack() }
            )
        }

        composable("scheduled_tasks") {
            V2ScheduledTaskListRoute(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate("scheduled_tasks/edit") },
                onEdit = { taskId -> navController.navigate("scheduled_tasks/edit?taskId=$taskId") }
            )
        }

        composable(
            route = "scheduled_tasks/edit?taskId={taskId}",
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")
            V2ScheduledTaskEditRoute(
                taskId = taskId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
