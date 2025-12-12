package com.autoglm.helper

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class CommandRepository(context: Context) {

    companion object {
        private const val PREF_NAME = "autoglm_commands"
        private const val KEY_COMMANDS = "commands"
        private const val TAG = "CommandRepository"
    }

    private val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getCommands(): List<Command> {
        val raw = sharedPreferences.getString(KEY_COMMANDS, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        Command(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            content = obj.getString("content"),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse commands", e)
            emptyList()
        }
    }

    fun addCommand(title: String, content: String): List<Command> {
        val current = getCommands()
        val updated = current + Command(title = title, content = content)
        return persist(sorted(updated))
    }

    fun updateCommand(id: String, title: String, content: String): List<Command> {
        val now = System.currentTimeMillis()
        val updated = getCommands().map { command ->
            if (command.id == id) {
                command.copy(title = title, content = content, updatedAt = now)
            } else {
                command
            }
        }
        return persist(sorted(updated))
    }

    fun deleteCommand(id: String): List<Command> {
        val updated = getCommands().filterNot { it.id == id }
        return persist(sorted(updated))
    }

    private fun persist(commands: List<Command>): List<Command> {
        val array = JSONArray()
        commands.forEach { command ->
            val obj = JSONObject()
            obj.put("id", command.id)
            obj.put("title", command.title)
            obj.put("content", command.content)
            obj.put("updatedAt", command.updatedAt)
            array.put(obj)
        }
        sharedPreferences.edit().putString(KEY_COMMANDS, array.toString()).apply()
        return commands
    }

    private fun sorted(commands: List<Command>): List<Command> {
        return commands.sortedByDescending { it.updatedAt }
    }
}
