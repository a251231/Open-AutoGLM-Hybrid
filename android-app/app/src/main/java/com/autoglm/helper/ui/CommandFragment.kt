package com.autoglm.helper.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autoglm.helper.AutoGLMAccessibilityService
import com.autoglm.helper.Command
import com.autoglm.helper.CommandAdapter
import com.autoglm.helper.CommandRepository
import com.autoglm.helper.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CommandFragment : Fragment() {

    private lateinit var commandListView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var addButton: Button
    private lateinit var refreshButton: Button
    private lateinit var repository: CommandRepository
    private lateinit var adapter: CommandAdapter
    private val prefs by lazy { requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    companion object {
        private const val PREF_NAME = "app_config"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_PROVIDER = "provider"
        private const val DEFAULT_BASE_URL = "https://api.grsai.com/v1"
        private const val DEFAULT_MODEL = "gpt-4-vision-preview"
        private const val DEFAULT_PROVIDER = "grs"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_commands, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository = CommandRepository(requireContext())
        commandListView = view.findViewById(R.id.commandList)
        emptyView = view.findViewById(R.id.emptyCommandText)
        addButton = view.findViewById(R.id.addCommandButton)
        refreshButton = view.findViewById(R.id.refreshCommandButton)

        adapter = CommandAdapter(
            onPublish = { publishCommand(it) },
            onEdit = { showCommandDialog(it) },
            onDelete = { deleteCommand(it) },
            onDetail = { showDetail(it) }
        )
        commandListView.layoutManager = LinearLayoutManager(requireContext())
        commandListView.adapter = adapter

        addButton.setOnClickListener { showCommandDialog() }
        refreshButton.setOnClickListener { syncCommandsFromServer(showToast = true) }

        lifecycleScope.launch(Dispatchers.IO) {
            val data = repository.getCommands()
            refreshCommandsOnMain(data)
        }
    }

    private suspend fun refreshCommandsOnMain(data: List<Command>) {
        withContext(Dispatchers.Main) {
            adapter.submit(data)
            emptyView.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun deleteCommand(command: Command) {
        lifecycleScope.launch(Dispatchers.IO) {
            val data = repository.deleteCommand(command.id)
            refreshCommandsOnMain(data)
        }
    }

    private fun showDetail(command: Command) {
        lifecycleScope.launch(Dispatchers.IO) {
            val history = repository.getHistory(command.id)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val content = StringBuilder()
            content.append("内容:\n${command.content}\n\n")
            content.append("执行记录:\n")
            if (history.isEmpty()) {
                content.append("暂无历史记录")
            } else {
                history.forEach {
                    val time = sdf.format(Date(it.timestamp))
                    content.append("$time [${it.result}] ${it.message ?: ""}\n")
                }
            }
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(requireContext())
                    .setTitle(command.title)
                    .setMessage(content.toString())
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun showCommandDialog(command: Command? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_command, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.commandTitleInput)
        val contentInput = dialogView.findViewById<EditText>(R.id.commandContentInput)

        titleInput.setText(command?.title ?: "")
        contentInput.setText(command?.content ?: "")

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (command == null) getString(R.string.add_command) else getString(R.string.edit_command))
            .setView(dialogView)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                val content = contentInput.text.toString().trim()

                if (title.isBlank() || content.isBlank()) {
                    Toast.makeText(requireContext(), getString(R.string.command_input_required), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val updated = if (command == null) {
                        repository.addCommand(title, content)
                    } else {
                        repository.updateCommand(command.id, title, content)
                    }
                    refreshCommandsOnMain(updated)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun publishCommand(command: Command) {
        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val url = URL("http://localhost:${AutoGLMAccessibilityService.PORT}/command")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-Auth-Token", getOrCreateAuthToken())
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                val payload = JSONObject()
                payload.put("id", command.id)
                payload.put("title", command.title)
                payload.put("content", command.content)
                payload.put("updatedAt", command.updatedAt)

                connection.outputStream.use { os ->
                    os.write(payload.toString().toByteArray())
                    os.flush()
                }

                val code = connection.responseCode
                connection.inputStream?.close()
                connection.disconnect()
                success = code == 200
            } catch (_: Exception) {
                success = false
            }

            withContext(Dispatchers.Main) {
                val message = if (success) {
                    getString(R.string.command_enqueue_success, command.title)
                } else {
                    getString(R.string.command_enqueue_failed, command.title)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncCommandsFromServer(showToast: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://localhost:${AutoGLMAccessibilityService.PORT}/commands")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("X-Auth-Token", getOrCreateAuthToken())
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(json)
                    val arr = obj.optJSONArray("commands") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        repository.upsertCommand(
                            Command(
                                id = item.optString("id"),
                                title = item.optString("title"),
                                content = item.optString("content"),
                                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                                lastResult = item.optString("lastResult", null),
                                lastRunAt = item.optLong("lastRunAt", 0).let { t -> if (t == 0L) null else t }
                            )
                        )
                    }
                    val latest = repository.getCommands()
                    refreshCommandsOnMain(latest)
                    if (showToast) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), getString(R.string.sync_success), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.sync_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun getOrCreateAuthToken(): String {
        var token = prefs.getString(KEY_AUTH_TOKEN, null)
        if (token.isNullOrBlank()) {
            token = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
        }
        return token
    }
}
