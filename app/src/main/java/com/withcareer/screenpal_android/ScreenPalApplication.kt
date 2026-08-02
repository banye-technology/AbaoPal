package com.withcareer.screenpal_android

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.data.preference.TaskRepository
import com.withcareer.screenpal_android.data.preference.ScheduledTaskRepository
import com.withcareer.screenpal_android.overlay.FloatingAssistantService
import com.withcareer.screenpal_android.core.agent.AgentRuntime
import com.withcareer.screenpal_android.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.withcareer.screenpal_android.core.skill.SkillRegistry
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.data.repository.InstructionRepository
import com.withcareer.screenpal_android.data.sqllite.InstructionVectorStore
import androidx.room.Room
import com.withcareer.screenpal_android.data.room.AppDatabase

class ScreenPalApplication : Application() {
    companion object {
        lateinit var instance: ScreenPalApplication
            private set
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "screenpal-database"
        )
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9
        )
        .build()
    }

    private val instructionVectorStore: InstructionVectorStore by lazy {
        InstructionVectorStore(this)
    }

    val instructionRepository: InstructionRepository by lazy {
        InstructionRepository(database.instructionDao(), instructionVectorStore)
    }

    val agentRuntime: AgentRuntime by lazy { AgentRuntime(this) }
    val taskRepository: TaskRepository by lazy { TaskRepository(this) }
    val scheduledTaskRepository: ScheduledTaskRepository by lazy { ScheduledTaskRepository(this) }
    val skillRegistry: SkillRegistry by lazy { SkillRegistry(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 预加载 Skills 并检查 Embeddings
        CoroutineScope(Dispatchers.IO).launch {
            try {
                skillRegistry.initialize()
                // 加载用户自定义 Skills
                skillRegistry.loadUserSkills()

                val repo = PreferencesRepository(this@ScreenPalApplication)
                // Embedding 生成始终使用阿里云 API (基础设施层)
                val infraApiKey = repo.getAsrApiKeySync()

                // 仅当配置了阿里云 API Key 时才预热 Embeddings
                if (!infraApiKey.isNullOrBlank()) {
                    val baseUrl = AppConfig.BAILIAN_BASE_URL
                    val modelClient = ModelClient(baseUrl)
                    android.util.Log.i("ScreenPalApp", "Generating embeddings for skills and instructions...")
                    // 为内置 Skills 和用户 Skills 生成 Embeddings
                    skillRegistry.ensureEmbeddings(modelClient, infraApiKey)

                    // 确保本地指令集也有 Embeddings
                    instructionRepository.ensureEmbeddings(modelClient, infraApiKey)
                    android.util.Log.i("ScreenPalApp", "Embeddings generation completed")
                } else {
                    android.util.Log.w("ScreenPalApp", "ASR API Key not configured, skipping embedding generation")
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreenPalApp", "Failed to preload skills/instructions", e)
            }
        }

        // 启动悬浮助手服务
        CoroutineScope(Dispatchers.Default).launch {
            val repo = PreferencesRepository(this@ScreenPalApplication)
            val enabled = repo.getFloatingAssistantEnabledSync()
            if (!enabled) return@launch
            if (!Settings.canDrawOverlays(this@ScreenPalApplication)) return@launch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    this@ScreenPalApplication,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return@launch
            }
            FloatingAssistantService.start(this@ScreenPalApplication)
        }
    }
}
