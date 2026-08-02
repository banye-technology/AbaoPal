package com.withcareer.screenpal_android.core.agent.component

import android.app.Application
import android.util.Log
import com.withcareer.screenpal_android.config.AiProvider
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.config.LlmModel
import com.withcareer.screenpal_android.core.action.ActionExecutor
import com.withcareer.screenpal_android.core.agent.framework.AgentBus
import com.withcareer.screenpal_android.core.agent.framework.BaseAgent
import com.withcareer.screenpal_android.core.agent.impl.ActionExecutorAgent
import com.withcareer.screenpal_android.core.agent.impl.DirectorAgent
import com.withcareer.screenpal_android.core.agent.impl.ExecutorAgentWrapper
import com.withcareer.screenpal_android.core.agent.impl.PlannerAgentWrapper
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.core.llm.ModelConfigResolver
import com.withcareer.screenpal_android.core.skill.Skill
import com.withcareer.screenpal_android.core.skill.SkillRegistry
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates the lifecycle and configuration of agents (Planner, Executor, Director).
 */
class AgentOrchestrator(
    private val application: Application,
    private val preferencesRepository: PreferencesRepository,
    private val skillRegistry: SkillRegistry,
    private val scope: CoroutineScope
) {
    var agentBus: AgentBus? = null
        private set

    private val agents = mutableListOf<BaseAgent>()

    /**
     * Initializes and starts all agents for a new task.
     */
    suspend fun initializeAgents(
        userInput: String,
        skipPlanning: Boolean
    ) {
        val startMs = android.os.SystemClock.elapsedRealtime()
        // Ensure clean state
        cleanupAgents()

        val configStart = android.os.SystemClock.elapsedRealtime()

        // 解析当前生效的执行模型配置（自定义配置优先，其次默认配置）
        val execConfig = ModelConfigResolver.resolveExecutor(application)
        if (execConfig == null) {
            throw IllegalStateException("未配置 API Key，请先在设置中填写")
        }

        // 1. Infrastructure Client (Always Aliyun Bailian)
        val infraBaseUrl = AppConfig.BAILIAN_BASE_URL
        val infraClient = ModelClient(infraBaseUrl)
        val infraApiKey = preferencesRepository.getAsrApiKeySync()
        if (infraApiKey.isNullOrBlank()) {
            throw IllegalStateException("未配置 API Key，请先在设置中填写")
        }

        // 2. Execution Client（来自用户配置）
        val execClient = if (execConfig.baseUrl == infraBaseUrl) infraClient else ModelClient(execConfig.baseUrl)
        val execApiKey = execConfig.apiKey
        val executorModel = execConfig.modelName
        Log.d("AgentOrchestrator", "Using resolved model configuration")

        // 向量模型使用基础设施配置（阿里云）
        val embeddingClient = infraClient
        val embeddingApiKey = infraApiKey

        val useMaxPlannerModel = userInput.length > 25
        val plannerModel: String
        val plannerClient: ModelClient
        val plannerApiKey: String
        if (useMaxPlannerModel) {
            Log.i("AgentOrchestrator", "Long input detected. Using QWEN3_MAX for planning.")
            plannerModel = LlmModel.QWEN3_MAX.value
            plannerClient = infraClient
            plannerApiKey = infraApiKey
        } else {
            Log.i("AgentOrchestrator", "Short input detected; using executor configuration for planning")
            plannerModel = executorModel
            plannerClient = execClient
            plannerApiKey = execApiKey
        }

        val maxSteps = preferencesRepository.getMaxExecutionStepsSync()
        val maxContextRounds = preferencesRepository.getMaxContextRoundsSync()
        Log.d("AgentOrchestrator", "Timing config load: ${android.os.SystemClock.elapsedRealtime() - configStart}ms")

        // Skills Logic
        val plannerSkillIds: List<String>?
        val canUseEmbedding = embeddingApiKey.isNotBlank()
        val vectorCheckStart = android.os.SystemClock.elapsedRealtime()
        val isVectorReady = if (canUseEmbedding) withContext(Dispatchers.IO) { skillRegistry.isVectorStoreReady() } else false
        Log.d("AgentOrchestrator", "Timing vector ready check: ${android.os.SystemClock.elapsedRealtime() - vectorCheckStart}ms")

        if (!canUseEmbedding) {
            Log.w("AgentOrchestrator", "Embedding API Key is empty. Skipping skills retrieval and embeddings.")
            plannerSkillIds = null
        } else if (!isVectorReady) {
            Log.i("AgentOrchestrator", "Cold start: Loading all skills and generating embeddings in background")
            scope.launch(Dispatchers.IO) {
                skillRegistry.ensureEmbeddings(embeddingClient, embeddingApiKey)
            }
            plannerSkillIds = null
        } else {
             val searchStart = android.os.SystemClock.elapsedRealtime()
             val relevantSkills = withContext(Dispatchers.IO) {
                 skillRegistry.searchSkills(userInput, embeddingClient, embeddingApiKey, limit = 10)
             }
             Log.d("AgentOrchestrator", "Timing skill search: ${android.os.SystemClock.elapsedRealtime() - searchStart}ms")
             if (relevantSkills.isNotEmpty()) {
                 val skillIds = relevantSkills.map { it.metadata.id }.toMutableList()

                 // 自动添加相关的 App Skills
                 // 收集所有 Task Skills 的 targetApps
                 val targetApps = relevantSkills.flatMap { it.metadata.targetApps }.distinct()

                 // 为每个 targetApp 获取对应的 App Skills
                 val relatedAppSkills = mutableListOf<Skill>()
                 targetApps.forEach { packageName ->
                     val appSkills = skillRegistry.getAppSkills(packageName)
                     relatedAppSkills.addAll(appSkills)
                     if (appSkills.isNotEmpty()) {
                         Log.d("AgentOrchestrator", "Found ${appSkills.size} app skills")
                     }
                 }

                 // 添加 App Skills 的 IDs
                 val appSkillIds = relatedAppSkills.map { it.metadata.id }
                 skillIds.addAll(appSkillIds)

                 // Planner 和 Executor 使用相同的筛选结果
                 plannerSkillIds = skillIds.distinct()

                 Log.i("AgentOrchestrator", "Semantic search selected skill count: ${relevantSkills.size}")
                 Log.i("AgentOrchestrator", "Auto-added app skill count: ${appSkillIds.size}")
                 Log.i("AgentOrchestrator", "Final planner skill count: ${plannerSkillIds.size}")
             } else {
                 Log.w("AgentOrchestrator", "Semantic Search returned no skills, fallback to all.")
                 plannerSkillIds = null
             }
        }

        withContext(Dispatchers.Main) {
            val createStart = android.os.SystemClock.elapsedRealtime()
            val bus = AgentBus()
            agentBus = bus

            if (!skipPlanning) {
                val planner = PlannerAgentWrapper(
                    application, plannerClient, plannerApiKey, plannerModel, bus, scope,
                    skipPlanning = false,
                    allowedSkillIds = plannerSkillIds
                )
                agents.add(planner)
            }

            val executor = ExecutorAgentWrapper(
                context = application,
                skillRegistry = skillRegistry,
                modelClient = execClient,
                apiKey = execApiKey,
                modelName = executorModel,
                bus = bus,
                scope = scope,
                maxContextRounds = maxContextRounds,
                getCurrentApp = { ScreenPalAccessibilityService.getInstance()?.getActivePackageName() }
            )

            // Create a dedicated ActionExecutor for the agents
            val service = ScreenPalAccessibilityService.getInstance()
            if (service != null) {
                Log.d("AgentOrchestrator", "Creating ActionExecutor and DirectorAgent")
                val actionExecutorInstance = ActionExecutor(
                    service,
                    execClient,
                    { execApiKey },
                    executorModel
                )
                val actionRunner = ActionExecutorAgent(actionExecutorInstance, application, bus, scope)
                val director = DirectorAgent(service, bus, scope, maxSteps)

                agents.addAll(listOf(executor, actionRunner, director))
                agents.forEach { it.start() }
                Log.d("AgentOrchestrator", "All agents started: ${agents.size} agents")
            } else {
                Log.e("AgentOrchestrator", "Accessibility Service not found when initializing agents")
            }
            Log.d("AgentOrchestrator", "Timing agent creation/start: ${android.os.SystemClock.elapsedRealtime() - createStart}ms")
        }
        Log.d("AgentOrchestrator", "Timing initializeAgents total: ${android.os.SystemClock.elapsedRealtime() - startMs}ms")
    }

    fun cleanupAgents() {
        val agentsCopy = ArrayList(agents)
        agentsCopy.forEach {
            try { it.stop() } catch (e: Exception) { Log.e("AgentOrchestrator", "Error stopping agent", e) }
        }
        agents.clear()
        agentBus = null
    }
}
