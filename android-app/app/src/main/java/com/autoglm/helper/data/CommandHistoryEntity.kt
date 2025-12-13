package com.autoglm.helper.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val commandId: String,
    val contentSnapshot: String,
    val result: String,
    val message: String? = null,
    val source: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
