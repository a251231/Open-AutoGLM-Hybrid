package com.autoglm.helper.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autoglm.helper.Command
import java.util.UUID

@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastResult: String? = null,
    val lastRunAt: Long? = null
) {
    fun toDomain(): Command = Command(
        id = id,
        title = title,
        content = content,
        updatedAt = updatedAt,
        lastResult = lastResult,
        lastRunAt = lastRunAt
    )

    companion object {
        fun fromDomain(command: Command): CommandEntity = CommandEntity(
            id = command.id,
            title = command.title,
            content = command.content,
            updatedAt = command.updatedAt,
            lastResult = command.lastResult,
            lastRunAt = command.lastRunAt
        )
    }
}
