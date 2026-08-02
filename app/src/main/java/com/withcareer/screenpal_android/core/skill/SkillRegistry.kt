package com.withcareer.screenpal_android.core.skill

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.data.sqllite.SkillVectorStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Skill 注册中心
 * 管理所有已加载的 Skill，并提供查询功能
 *
 * 升级为三维度架构：
 * 1. Task-Oriented Skills (内置): 从 assets 加载，通过向量检索访问
 * 2. User-Defined Skills (用户): 从数据库加载，优先级高于内置 Skills
 * 3. App-Specific Skills: 仅通过应用上下文直接访问 (getAppSkills)
 *
 */
class SkillRegistry(private val context: Context) {

    private val skillLoader = SkillLoader(context)
    private val vectorStore = SkillVectorStore(context)
    private val gson = Gson()

    // 内置任务型 Skills (列表)
    private var taskSkills: List<Skill> = emptyList()

    // 用户自定义 Skills (列表)
    private var userSkills: List<Skill> = emptyList()

    // 应用型 Skills (Map: PackageName -> List<Skill>)
    private var appSkillsMap: Map<String, List<Skill>> = emptyMap()

    private var isLoaded = false

    // 用于追踪用户 Skills 是否首次加载完成
    private val firstLoadDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()

    /**
     * 等待用户 Skills 加载完成
     * @param timeoutMillis 超时时间，默认 3 秒
     */
    suspend fun waitForUserSkillsLoad(timeoutMillis: Long = 3000) {
        if (firstLoadDeferred.isCompleted) return

        try {
            kotlinx.coroutines.withTimeout(timeoutMillis) {
                firstLoadDeferred.await()
            }
        } catch (e: Exception) {
            Log.w("SkillRegistry", "Wait for user skills load timeout/error", e)
        }
    }

    /**
     * 初始化并加载 Skills
     */
    @Synchronized
    fun initialize() {
        if (isLoaded) return
        Log.i("SkillRegistry", "Initializing SkillRegistry...")
        val allSkills = skillLoader.loadAllSkills()

        // 分离 Task 和 App Skills
        taskSkills = allSkills.filter { it.metadata.type == SkillType.TASK }

        val appSkills = allSkills.filter { it.metadata.type == SkillType.APP }
        val map = mutableMapOf<String, MutableList<Skill>>()
        for (skill in appSkills) {
            for (app in skill.metadata.targetApps) {
                map.computeIfAbsent(app) { mutableListOf() }.add(skill)
            }
        }
        appSkillsMap = map
        isLoaded = true

        Log.i("SkillRegistry", "Initialized: ${taskSkills.size} Task skills, ${appSkills.size} App skills, ${userSkills.size} User skills")
    }

    /**
     * 加载用户自定义 Skills
     * 从数据库异步加载
     */
    suspend fun loadUserSkills() {
        try {
            val app = context.applicationContext as? ScreenPalApplication
            if (app == null) {
                Log.w("SkillRegistry", "Cannot load user skills: Application context not available")
                return
            }

            val userSkillDao = app.database.userSkillDao()
            val entities = userSkillDao.getAllSkills().first()

            userSkills = entities.map { entity ->
                // 解析按键集
                val instructionSets = try {
                    if (entity.instructionSets.isNotBlank() && entity.instructionSets != "[]") {
                        val type = object : com.google.gson.reflect.TypeToken<List<com.withcareer.screenpal_android.data.model.SkillInstructionSet>>() {}.type
                        gson.fromJson<List<com.withcareer.screenpal_android.data.model.SkillInstructionSet>>(entity.instructionSets, type)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w("SkillRegistry", "Failed to parse instruction sets for user skill", e)
                    null
                }

                val skill = Skill(
                    metadata = SkillMetadata(
                        id = "user.${entity.id}",
                        type = SkillType.TASK,
                        name = entity.name,
                        version = entity.version.toString(), // 将 Int 转换为 String
                        author = entity.author,
                        description = entity.description,
                        targetApps = parseTargetAppsToPackageList(entity.targetApps)
                    ),
                    instruction = entity.instruction,
                    allowedTools = parseJsonArray(entity.allowedTools),
                    instructionSets = instructionSets
                )
                Log.i("SkillRegistry", "User skill loaded: instructionLength=${skill.instruction.length}, instructionSets=${instructionSets?.size ?: 0}")
                skill
            }

            Log.i("SkillRegistry", "Loaded ${userSkills.size} user-defined skills")
        } catch (e: Exception) {
            Log.e("SkillRegistry", "Failed to load user skills", e)
            userSkills = emptyList()
        } finally {
            // 无论成功失败，都标记为加载完成，避免无限等待
            if (!firstLoadDeferred.isCompleted) {
                firstLoadDeferred.complete(Unit)
            }
        }
    }

    /**
     * 刷新用户 Skills（当用户新增/修改/删除 Skill 时调用）
     * 同时自动为新增的 skill 生成 embedding
     */
    suspend fun refreshUserSkills() {
        loadUserSkills()

        // 自动为新增的 skills 生成 embeddings
        try {
            val app = context.applicationContext as? com.withcareer.screenpal_android.ScreenPalApplication
            if (app != null) {
                val prefsRepo = com.withcareer.screenpal_android.data.preference.PreferencesRepository(context)
                val apiKey = prefsRepo.getAsrApiKeySync()

                if (!apiKey.isNullOrBlank()) {
                    val modelClient = ModelClient(com.withcareer.screenpal_android.config.AppConfig.BAILIAN_BASE_URL)

                    // 检查是否有新的用户 skills 需要生成 embedding
                    val storedIds = vectorStore.getAllSkillIds().toSet()
                    val missingUserSkills = userSkills.filter { it.metadata.id !in storedIds }

                    if (missingUserSkills.isNotEmpty()) {
                        Log.i("SkillRegistry", "Auto-generating embeddings for ${missingUserSkills.size} new user skills")
                        for (skill in missingUserSkills) {
                            Log.i("SkillRegistry", "Generating user-skill embedding")
                            generateEmbeddingForSkill(skill, modelClient, apiKey)
                        }
                        Log.i("SkillRegistry", "Embedding generation completed for new skills")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SkillRegistry", "Failed to auto-generate embeddings for new skills", e)
        }
    }

    /**
     * 解析 JSON 数组字符串
     */
    private fun parseJsonArray(json: String): List<String> {
        return try {
            gson.fromJson(json, Array<String>::class.java).toList()
        } catch (e: Exception) {
            emptyList<String>()
        }
    }

    /**
     * 解析 targetApps JSON 对象，返回包名列表（用于运行时匹配）
     * 兼容旧格式：如果解析为数组，则返回数组内容
     */
    private fun parseTargetAppsToPackageList(json: String): List<String> {
        return try {
            if (json.isBlank() || json == "[]" || json == "{}") return emptyList()
            // 尝试解析为对象
            val map = gson.fromJson(json, Map::class.java) as? Map<*, *>
            if (map != null && map.isNotEmpty()) {
                // 是对象格式，提取 keys
                map.keys.map { it.toString() }
            } else {
                // 兼容旧格式：尝试解析为数组
                gson.fromJson(json, Array<String>::class.java).toList()
            }
        } catch (e: Exception) {
            Log.w("SkillRegistry", "Failed to parse target-app package list", e)
            emptyList()
        }
    }

    /**
     * 检查向量库是否有数据
     */
    fun isVectorStoreReady(): Boolean {
        return vectorStore.getAllSkillIds().isNotEmpty()
    }

    /**
     * 确保 Task Skills 都有 Embedding
     * 注意：仅处理 SkillType.TASK，App Skill 不参与向量索引
     * @param modelClient 模型客户端
     * @param apiKey API Key
     */
    suspend fun ensureEmbeddings(modelClient: ModelClient, apiKey: String) {
        if (!isLoaded) initialize()

        val storedIds = vectorStore.getAllSkillIds().toSet()

        Log.d("SkillRegistry", "Checking embeddings for ${userSkills.size} user skills")

        // 检查内置 Task Skills
        val missingTaskSkills = taskSkills.filter { it.metadata.id !in storedIds }

        // 检查用户自定义 Skills
        val missingUserSkills = userSkills.filter { it.metadata.id !in storedIds }

        val totalMissing = missingTaskSkills.size + missingUserSkills.size

        if (totalMissing > 0) {
            Log.i("SkillRegistry", "Generating embeddings for $totalMissing skills (${missingTaskSkills.size} builtin, ${missingUserSkills.size} user)...")

            // 为内置 Skills 生成 Embedding
            for (skill in missingTaskSkills) {
                Log.i("SkillRegistry", "Generating built-in skill embedding")
                generateEmbeddingForSkill(skill, modelClient, apiKey)
            }

            // 为用户 Skills 生成 Embedding
            for (skill in missingUserSkills) {
                Log.i("SkillRegistry", "Generating user-skill embedding")
                generateEmbeddingForSkill(skill, modelClient, apiKey)
            }

            Log.i("SkillRegistry", "Embedding generation completed for all $totalMissing skills")
        } else {
            Log.i("SkillRegistry", "All skills already have embeddings (${storedIds.size} total)")
        }
    }

    /**
     * 为单个 Skill 生成 Embedding
     */
    private suspend fun generateEmbeddingForSkill(skill: Skill, modelClient: ModelClient, apiKey: String) {
        val sb = StringBuilder()
        sb.append(skill.metadata.name).append(": ").append(skill.metadata.description)

        if (skill.instruction.isNotBlank()) {
            sb.append("\nInstruction:\n")
            sb.append(skill.instruction)
        }

        val text = sb.toString()

        try {
            val embedding = modelClient.createEmbedding(text, AppConfig.DEFAULT_EMBEDDING_MODEL, apiKey)
            if (embedding.isNotEmpty()) {
                vectorStore.saveSkillEmbedding(skill.metadata.id, embedding, skill.metadata.description)
                Log.d("SkillRegistry", "Skill embedding saved")
            }
            // Avoid rate limit
            delay(200)
        } catch (e: Exception) {
            Log.e("SkillRegistry", "Failed to generate skill embedding", e)
        }
    }

    /**
     * 语义搜索 Task Skills（含用户自定义 Skills）
     * 用户 Skills 优先，当 target_apps 冲突时，过滤掉内置 Skills
     * @param query 用户查询
     * @param modelClient 模型客户端
     * @param apiKey API Key
     * @param limit 返回数量限制
     */
    suspend fun searchSkills(query: String, modelClient: ModelClient, apiKey: String, limit: Int = 5): List<Skill> {
        if (!isLoaded) initialize()

        Log.d("SkillRegistry", "Semantic query received: length=${query.length}")
        Log.d("SkillRegistry", "Available user skill count: ${userSkills.size}")
        Log.d("SkillRegistry", "Total indexed skills: ${userSkills.size} user skills + ${taskSkills.size} task skills = ${userSkills.size + taskSkills.size}")

        // 1. Generate query embedding
        val queryEmbedding = modelClient.createEmbedding(query, AppConfig.DEFAULT_EMBEDDING_MODEL, apiKey)
        if (queryEmbedding.isEmpty()) {
            Log.w("SkillRegistry", "Failed to generate query embedding, fallback to all skills")
            // 优先返回用户 Skills
            return userSkills + taskSkills
        }

        // 2. Search in Vector Store (Task Skills and User Skills are indexed)
        val results = vectorStore.search(queryEmbedding, limit * 2) // 获取更多结果以便过滤
        val resultIds = results.map { it.id }

        Log.i("SkillRegistry", "Semantic search result count: ${results.size}")

        // Debug: Log vector search details
        Log.d("SkillRegistry", "Vector search returned ${results.size} results (limit was ${limit * 2})")
        if (results.size < userSkills.size + taskSkills.size) {
            val totalSkills = userSkills.size + taskSkills.size
            Log.w("SkillRegistry", "Some skills were filtered out by vector search. Total indexed skills: $totalSkills, returned: ${results.size}")

            // Show which user skills were NOT returned
            val missingUserSkills = userSkills.filter { it.metadata.id !in resultIds }
            if (missingUserSkills.isNotEmpty()) {
                Log.w("SkillRegistry", "Missing user skill count: ${missingUserSkills.size}")
            }
        }

        // 3. 先从用户 Skills 中匹配
        val matchedUserSkills = userSkills.filter { it.metadata.id in resultIds }
        if (matchedUserSkills.isNotEmpty()) {
            Log.i("SkillRegistry", "Matched user skill count: ${matchedUserSkills.size}")
        }

        // 4. 获取用户 Skills 覆盖的应用列表
        val coveredApps = matchedUserSkills.flatMap { it.metadata.targetApps }.toSet()
        if (coveredApps.isNotEmpty()) {
            Log.i("SkillRegistry", "User-skill covered app count: ${coveredApps.size}")
        }

        // 5. 从内置 Skills 中匹配，但排除已被用户 Skill 覆盖的应用
        val matchedTaskSkillsBeforeFilter = taskSkills.filter { it.metadata.id in resultIds }
        val matchedTaskSkills = matchedTaskSkillsBeforeFilter
            .filterNot { skill ->
                skill.metadata.targetApps.any { app -> app in coveredApps }
            }

        val filteredCount = matchedTaskSkillsBeforeFilter.size - matchedTaskSkills.size
        if (filteredCount > 0) {
            Log.i("SkillRegistry", "Filtered out $filteredCount builtin skills due to user skill conflicts")
        }

        // 6. 合并结果：用户 Skills 在前
        val combinedSkills = matchedUserSkills + matchedTaskSkills
        Log.i("SkillRegistry", "Final matched skill count: ${combinedSkills.size}")

        // 7. 按向量搜索结果排序并限制数量
        return combinedSkills
            .sortedBy { resultIds.indexOf(it.metadata.id) }
            .take(limit)
    }

    /**
     * 获取指定应用的 App Skills (直接加载)
     * @param packageName 应用包名
     * @return App Skill 列表
     */
    fun getAppSkills(packageName: String): List<Skill> {
        if (!isLoaded) initialize()
        return appSkillsMap[packageName] ?: emptyList()
    }

    /**
     * 兼容旧接口：获取指定应用的 Skills
     * 这里我们只返回 App Skills，因为 Task Skills 应该通过 Search 获取
     */
    fun getSkillsForApp(packageName: String): List<Skill> {
        return getAppSkills(packageName)
    }

    /**
     * 获取所有 Task Skills（含用户 Skills）
     */
    fun getAllTaskSkills(): List<Skill> {
        if (!isLoaded) initialize()
        return userSkills + taskSkills
    }

    /**
     * 获取所有已加载的 Skills (User + Task + App)
     */
    fun getAllSkills(): List<Skill> {
        if (!isLoaded) initialize()
        return userSkills + taskSkills + appSkillsMap.values.flatten().distinct()
    }

    /**
     * 获取用户自定义 Skills
     */
    fun getUserSkills(): List<Skill> {
        return userSkills
    }

    /**
     * 获取指定应用可用的用户自定义 Skills
     */
    fun getUserSkillsForApp(packageName: String): List<Skill> {
        if (!isLoaded) initialize()
        if (packageName.isBlank()) return emptyList()
        return userSkills.filter { skill ->
            val targets = skill.metadata.targetApps
            targets.isEmpty() || targets.contains("*") || targets.contains(packageName)
        }
    }

    /**
     * 获取 Task Skills 的摘要信息
     * 用于 Router 快速筛选
     */
    fun getSkillSummaries(): List<String> {
        if (!isLoaded) initialize()
        return taskSkills.map {
            "${it.metadata.id}: ${it.metadata.description}"
        }
    }

    /**
     * 获取指定 ID 列表的 Skills
     */
    fun getSkillsByIds(ids: List<String>): List<Skill> {
        if (!isLoaded) initialize()
        val all = getAllSkills()
        return all.filter { it.metadata.id in ids }
    }

    /**
     * 获取全局通用 Skills
     * 默认返回所有 Task Skills 中 target_app 包含 "*" 的
     */
    fun getGlobalSkills(): List<Skill> {
        if (!isLoaded) initialize()
        return taskSkills.filter { it.metadata.targetApps.contains("*") }
    }
}
