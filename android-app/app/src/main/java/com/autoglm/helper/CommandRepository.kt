package com.autoglm.helper

import android.content.Context
import com.autoglm.helper.data.AppDatabase
import com.autoglm.helper.data.CommandEntity
import com.autoglm.helper.data.CommandHistoryEntity
import java.util.UUID

class CommandRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val commandDao = db.commandDao()
    private val historyDao = db.commandHistoryDao()

    fun getCommands(): List<Command> {
        return commandDao.getAll().map { it.toDomain() }
    }

    fun addCommand(title: String, content: String): List<Command> {
        val now = System.currentTimeMillis()
        val command = Command(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            updatedAt = now
        )
        commandDao.insert(CommandEntity.fromDomain(command))
        return getCommands()
    }

    fun updateCommand(id: String, title: String, content: String): List<Command> {
        val now = System.currentTimeMillis()
        val entity = CommandEntity(id = id, title = title, content = content, updatedAt = now)
        commandDao.insert(entity)
        return getCommands()
    }

    fun deleteCommand(id: String): List<Command> {
        commandDao.delete(id)
        return getCommands()
    }

    fun upsertCommand(command: Command) {
        val existing = commandDao.getById(command.id)
        val merged = Command(
            id = command.id,
            title = if (command.title.isNotBlank()) command.title else existing?.title ?: command.title,
            content = if (command.content.isNotBlank()) command.content else existing?.content ?: command.content,
            updatedAt = command.updatedAt,
            lastResult = command.lastResult ?: existing?.lastResult,
            lastRunAt = command.lastRunAt ?: existing?.lastRunAt
        )
        commandDao.insert(CommandEntity.fromDomain(merged))
    }

    fun addHistory(commandId: String, contentSnapshot: String, result: String, message: String?, source: String?) {
        val entity = CommandHistoryEntity(
            commandId = commandId,
            contentSnapshot = contentSnapshot,
            result = result,
            message = message,
            source = source
        )
        historyDao.insert(entity)
    }

    fun getHistory(commandId: String): List<CommandHistoryEntity> {
        return historyDao.historyForCommand(commandId)
    }
}
