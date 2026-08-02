package com.withcareer.screenpal_android.data.sqllite

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.sqrt

/**
 * 本地指令集向量存储 (SQLite 实现)
 * 用于存储 Instruction Set 的 Embedding 并支持相似度搜索
 */
class InstructionVectorStore(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "instruction_vectors.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "instructions"
        private const val COLUMN_ID = "id"
        private const val COLUMN_EMBEDDING = "embedding" // JSON String of List<Double>
        private const val COLUMN_METADATA = "metadata"   // JSON String or Description
    }

    private val gson = Gson()

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_EMBEDDING TEXT,
                $COLUMN_METADATA TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /**
     * 保存或更新 Instruction Embedding
     */
    fun saveEmbedding(id: String, embedding: List<Double>, metadata: String = "") {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ID, id)
            put(COLUMN_EMBEDDING, gson.toJson(embedding))
            put(COLUMN_METADATA, metadata)
        }
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * 删除 Instruction Embedding
     */
    fun deleteEmbedding(id: String) {
        val db = writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id))
    }

    /**
     * 获取所有已存储的 ID
     */
    fun getAllIds(): List<String> {
        val ids = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, arrayOf(COLUMN_ID), null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                ids.add(it.getString(0))
            }
        }
        return ids
    }

    /**
     * 搜索最相似的 Instructions
     */
    fun search(queryVector: List<Double>, limit: Int = 5, threshold: Double = 0.4): List<SearchResult> {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, arrayOf(COLUMN_ID, COLUMN_EMBEDDING), null, null, null, null, null)

        val candidates = mutableListOf<SearchResult>()
        Log.d("InstructionVectorStore", "Searching in ${cursor.count} instructions")

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val embeddingJson = it.getString(1)

                try {
                    if (embeddingJson.isNullOrBlank()) {
                        continue
                    }
                    val type = object : TypeToken<List<Double>>() {}.type
                    val vector: List<Double>? = gson.fromJson(embeddingJson, type)
                    if (vector.isNullOrEmpty()) {
                        continue
                    }

                    val similarity = cosineSimilarity(queryVector, vector)
                    Log.d("InstructionVectorStore", "Instruction similarity calculated")
                    if (similarity >= threshold) {
                        candidates.add(SearchResult(id, similarity))
                    }
                } catch (e: Exception) {
                    Log.e("InstructionVectorStore", "Error parsing instruction embedding", e)
                }
            }
        }

        return candidates.sortedByDescending { it.score }.take(limit)
    }

    /**
     * 计算余弦相似度
     */
    private fun cosineSimilarity(v1: List<Double>, v2: List<Double>): Double {
        if (v1.size != v2.size) return 0.0

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }

        if (normA == 0.0 || normB == 0.0) return 0.0
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}
