package com.withcareer.screenpal_android.core.skill

import android.content.Context
import android.os.Build
import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.IOException

/**
 * Skill 加载器
 * 负责从 assets 目录加载 Skill 配置，并根据设备信息进行过滤
 */
class SkillLoader(private val context: Context) {

    private val mapper = ObjectMapper(YAMLFactory()).apply {
        registerModule(KotlinModule.Builder().build())
    }

    /**
     * 加载所有适用于当前设备的 Skill
     * @return Skill 列表
     */
    fun loadAllSkills(): List<Skill> {
        val skills = mutableListOf<Skill>()
        try {
            // 递归遍历 assets/skills 目录
            val assetManager = context.assets
            val skillFiles = listAssetFiles("skills")

            for (filePath in skillFiles) {
                try {
                    val inputStream = assetManager.open(filePath)
                    val skill = mapper.readValue(inputStream, Skill::class.java)

                    if (isDeviceMatch(skill)) {
                        skills.add(skill)
                        Log.d("SkillLoader", "Skill loaded")
                    } else {
                        Log.d("SkillLoader", "Skipped skill due to device mismatch")
                    }
                } catch (e: Exception) {
                    Log.e("SkillLoader", "Failed to load skill asset", e)
                }
            }
        } catch (e: IOException) {
            Log.e("SkillLoader", "Failed to list assets", e)
        }
        return skills
    }

    private fun listAssetFiles(path: String): List<String> {
        val files = mutableListOf<String>()
        try {
            val list = context.assets.list(path) ?: return emptyList()
            if (list.isNotEmpty()) {
                for (file in list) {
                    val fullPath = if (path.isEmpty()) file else "$path/$file"
                    if (file.contains(".")) {
                         // 简单判断，如果是文件
                        if (file.endsWith(".yaml") || file.endsWith(".yml")) {
                            files.add(fullPath)
                        }
                    } else {
                        // 可能是目录，递归
                        files.addAll(listAssetFiles(fullPath))
                    }
                }
            }
        } catch (e: IOException) {
            // ignore
        }
        return files
    }

    private fun isDeviceMatch(skill: Skill): Boolean {
        val filter = skill.deviceFilter ?: return true // 无过滤器则默认匹配

        // 1. 品牌匹配
        if (!filter.brands.isNullOrEmpty()) {
            val currentBrand = Build.BRAND
            val matchBrand = filter.brands.any { it.equals(currentBrand, ignoreCase = true) }
            if (!matchBrand) return false
        }

        // 2. 系统版本匹配 (支持 ">=30", "33", "<=28")
        if (!filter.osVersions.isNullOrEmpty()) {
            val currentSdk = Build.VERSION.SDK_INT
            val matchVersion = filter.osVersions.any { checkVersion(currentSdk, it) }
            if (!matchVersion) return false
        }

        return true
    }

    private fun checkVersion(current: Int, rule: String): Boolean {
        return try {
            when {
                rule.startsWith(">=") -> current >= rule.substring(2).toInt()
                rule.startsWith("<=") -> current <= rule.substring(2).toInt()
                rule.startsWith(">") -> current > rule.substring(1).toInt()
                rule.startsWith("<") -> current < rule.substring(1).toInt()
                else -> current == rule.toInt()
            }
        } catch (e: NumberFormatException) {
            false
        }
    }
}
