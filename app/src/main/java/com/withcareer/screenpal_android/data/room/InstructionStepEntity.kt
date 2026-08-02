package com.withcareer.screenpal_android.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "instruction_steps",
    foreignKeys = [
        ForeignKey(
            entity = InstructionSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["setId"])]
)
data class InstructionStepEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val setId: String,
    val orderIndex: Int,
    val actionType: String,
    val actionParams: String, // JSON content
    val description: String,
    val timestamp: Long = 0L,        // 相对录制开始时间（毫秒）
    val delayBefore: Long = 0L       // 与上一步骤的间隔（毫秒）
)
