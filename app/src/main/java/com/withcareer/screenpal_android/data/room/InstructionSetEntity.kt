package com.withcareer.screenpal_android.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "instruction_sets")
data class InstructionSetEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val authorId: String = "self",
    val authorName: String = "Me",
    val isPublished: Boolean = false,
    val remoteId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceInfo: String = "",
    val executionCount: Int = 0,
    val totalDuration: Long = 0L
)
