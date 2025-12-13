package com.autoglm.helper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var serverStatusText: TextView
    private lateinit var openSettingsButton: Button
    private lateinit var testConnectionButton: Button
    private lateinit var addCommandButton: Button
    private lateinit var commandListView: RecyclerView
    private lateinit var emptyCommandText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var baseUrlInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var providerSpinner: Spinner
    private lateinit var saveConfigButton: Button
    private lateinit var resetConfigButton: Button
    private lateinit var testConfigButton: Button
    private lateinit var refreshCommandButton: Button
    private lateinit var authTokenText: TextView
    private lateinit var regenerateTokenButton: Button
    private lateinit var presetNameInput: EditText
    private lateinit var presetSpinner: Spinner
    private lateinit var savePresetButton: Button
    private lateinit var activatePresetButton: Button
    private lateinit var commandRepository: CommandRepository
    private lateinit var commandAdapter: CommandAdapter
    private val prefs: SharedPreferences by lazy { getSharedPreferences(PREF_NAME, MODE_PRIVATE) }
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val PREF_NAME = "app_config"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_ACTIVE_PRESET = "active_preset"
        private const val KEY_PRESETS = "config_presets"

        private const val DEFAULT_BASE_URL = "https://api.grsai.com/v1"
        private const val DEFAULT_MODEL = "gpt-4-vision-preview"
        private const val DEFAULT_PROVIDER = "grs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        serverStatusText = findViewById(R.id.serverStatusText)
        openSettingsButton = findViewById(R.id.openSettingsButton)
        testConnectionButton = findViewById(R.id.testConnectionButton)
        addCommandButton = findViewById(R.id.addCommandButton)
        commandListView = findViewById(R.id.commandList)
        emptyCommandText = findViewById(R.id.emptyCommandText)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        baseUrlInput = findViewById(R.id.baseUrlInput)
        modelInput = findViewById(R.id.modelInput)
        providerSpinner = findViewById(R.id.providerSpinner)
        saveConfigButton = findViewById(R.id.saveConfigButton)
        resetConfigButton = findViewById(R.id.resetConfigButton)
        testConfigButton = findViewById(R.id.testConfigButton)
        authTokenText = findViewById(R.id.authTokenText)
        regenerateTokenButton = findViewById(R.id.regenerateTokenButton)
        presetNameInput = findViewById(R.id.presetNameInput)
        presetSpinner = findViewById(R.id.presetSpinner)
        savePresetButton = findViewById(R.id.savePresetButton)
        activatePresetButton = findViewById(R.id.activatePresetButton)
        refreshCommandButton = findViewById(R.id.refreshCommandButton)
        commandRepository = CommandRepository(this)
        commandAdapter = CommandAdapter(
            onPublish = { publishCommand(it) },
            onEdit = { showCommandDialog(it) },
            onDelete = { confirmDelete(it) },
            onDetail = { showCommandDetail(it) }
        )

        setupProviderSpinner()
        refreshPresetSpinner()
        loadConfigToUi()
        
        openSettingsButton.setOnClickListener {
            openAccessibilitySettings()
        }
        
        testConnectionButton.setOnClickListener {
            testConnection()
        }

        addCommandButton.setOnClickListener {
            showCommandDialog()
        }

        saveConfigButton.setOnClickListener { saveConfig() }
        resetConfigButton.setOnClickListener { resetConfig() }
        testConfigButton.setOnClickListener { testConfigEndpoint() }
        regenerateTokenButton.setOnClickListener { regenerateToken() }
        savePresetButton.setOnClickListener { saveCurrentToPreset() }
        activatePresetButton.setOnClickListener { activateSelectedPreset() }
        refreshCommandButton.setOnClickListener {
            syncCommandsFromServer(showToast = true)
        }

        commandListView.layoutManager = LinearLayoutManager(this)
        commandListView.adapter = commandAdapter
        refreshCommands()
        
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        syncCommandsFromServer()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun updateStatus() {
        val service = AutoGLMAccessibilityService.getInstance()
        
        if (service != null) {
            statusText.text = getString(R.string.service_running)
            serverStatusText.text = getString(
                R.string.server_status,
                getString(R.string.server_running, AutoGLMAccessibilityService.PORT)
            )
        } else {
            statusText.text = getString(R.string.service_stopped)
            serverStatusText.text = getString(
                R.string.server_status,
                getString(R.string.server_stopped)
            )
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun testConnection() {
        Thread {
            try {
                val url = URL("http://localhost:${AutoGLMAccessibilityService.PORT}/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                try {
                    val responseCode = connection.responseCode
                    connection.inputStream.bufferedReader().use { it.readText() }

                    runOnUiThread {
                        if (responseCode == 200) {
                            Toast.makeText(
                                this,
                                getString(R.string.connection_success),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.connection_failed, "HTTP $responseCode"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.connection_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun setupProviderSpinner() {
        val providers = resources.getStringArray(R.array.provider_options)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            providers
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        providerSpinner.adapter = adapter
    }

    private fun loadConfigToUi() {
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        val provider = prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER
        val authToken = getOrCreateAuthToken()
        val providers = resources.getStringArray(R.array.provider_options)
        val index = providers.indexOf(provider).takeIf { it >= 0 } ?: 0

        apiKeyInput.setText(apiKey)
        baseUrlInput.setText(baseUrl)
        modelInput.setText(model)
        providerSpinner.setSelection(index)
        authTokenText.text = "${getString(R.string.config_token_label)}:\n$authToken"
        val activePreset = prefs.getString(KEY_ACTIVE_PRESET, "") ?: ""
        presetNameInput.setText(activePreset)
        refreshPresetSpinner()
        setSpinnerSelection(presetSpinner, activePreset)
    }

    private fun saveConfig() {
        val apiKey = apiKeyInput.text.toString().trim()
        val baseUrl = baseUrlInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val provider = providerSpinner.selectedItem?.toString()?.trim() ?: DEFAULT_PROVIDER

        if (baseUrl.isBlank() || !isValidUrl(baseUrl)) {
            Toast.makeText(this, getString(R.string.config_base_url_invalid), Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_MODEL, if (model.isBlank()) DEFAULT_MODEL else model)
            .putString(KEY_PROVIDER, provider)
            .apply()

        Toast.makeText(this, getString(R.string.config_save_success), Toast.LENGTH_SHORT).show()
    }

    private fun resetConfig() {
        apiKeyInput.setText("")
        baseUrlInput.setText(DEFAULT_BASE_URL)
        modelInput.setText(DEFAULT_MODEL)
        val providers = resources.getStringArray(R.array.provider_options)
        val index = providers.indexOf(DEFAULT_PROVIDER).takeIf { it >= 0 } ?: 0
        providerSpinner.setSelection(index)

        prefs.edit()
            .putString(KEY_API_KEY, "")
            .putString(KEY_BASE_URL, DEFAULT_BASE_URL)
            .putString(KEY_MODEL, DEFAULT_MODEL)
            .putString(KEY_PROVIDER, DEFAULT_PROVIDER)
            .apply()

        Toast.makeText(this, getString(R.string.config_reset_success), Toast.LENGTH_SHORT).show()
    }

    private fun testConfigEndpoint() {
        val baseUrl = baseUrlInput.text.toString().trim().ifBlank { DEFAULT_BASE_URL }
        Thread {
            try {
                val url = URL(baseUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val code = connection.responseCode
                connection.inputStream?.close()
                connection.disconnect()
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.config_test_success, code),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.config_test_failed, e.message ?: "未知错误"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsed = Uri.parse(url)
            !TextUtils.isEmpty(parsed.scheme) && !TextUtils.isEmpty(parsed.host)
        } catch (e: Exception) {
            false
        }
    }

    private fun refreshCommands(commands: List<Command>? = null) {
        val latest = commands ?: commandRepository.getCommands()
        commandAdapter.submit(latest)
        emptyCommandText.isVisible = latest.isEmpty()
    }

    private fun syncCommandsFromServer(showToast: Boolean = false) {
        Thread {
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
                        commandRepository.upsertCommand(
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
                    runOnUiThread {
                        refreshCommands()
                        if (showToast) Toast.makeText(this, getString(R.string.sync_success), Toast.LENGTH_SHORT).show()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                if (showToast) {
                    runOnUiThread {
                        Toast.makeText(this, getString(R.string.sync_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    private fun publishCommand(command: Command) {
        val service = AutoGLMAccessibilityService.getInstance()
        if (service == null) {
            Toast.makeText(this, getString(R.string.enable_accessibility), Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
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

            runOnUiThread {
                val message = if (success) {
                    getString(R.string.command_enqueue_success, command.title)
                } else {
                    getString(R.string.command_enqueue_failed, command.title)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun regenerateToken() {
        val token = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
        authTokenText.text = "${getString(R.string.config_token_label)}:\n$token"
        Toast.makeText(this, getString(R.string.config_token_regenerated), Toast.LENGTH_SHORT).show()
    }

    private fun saveCurrentToPreset() {
        val name = presetNameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, getString(R.string.preset_name_required), Toast.LENGTH_SHORT).show()
            return
        }
        val presets = loadPresets().toMutableList().filter { it.first != name }.toMutableList()
        presets.add(0, name to collectConfig())
        persistPresets(presets)
        prefs.edit().putString(KEY_ACTIVE_PRESET, name).apply()
        refreshPresetSpinner()
        setSpinnerSelection(presetSpinner, name)
        Toast.makeText(this, getString(R.string.preset_saved, name), Toast.LENGTH_SHORT).show()
    }

    private fun activateSelectedPreset() {
        val name = presetSpinner.selectedItem?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Toast.makeText(this, getString(R.string.preset_name_required), Toast.LENGTH_SHORT).show()
            return
        }
        val presets = loadPresets().toMap()
        val data = presets[name] ?: return
        applyConfig(data)
        prefs.edit().putString(KEY_ACTIVE_PRESET, name).apply()
        presetNameInput.setText(name)
        Toast.makeText(this, getString(R.string.preset_activated, name), Toast.LENGTH_SHORT).show()
    }

    private fun collectConfig(): Map<String, String> {
        val provider = providerSpinner.selectedItem?.toString()?.trim() ?: DEFAULT_PROVIDER
        return mapOf(
            KEY_API_KEY to apiKeyInput.text.toString().trim(),
            KEY_BASE_URL to baseUrlInput.text.toString().trim().ifBlank { DEFAULT_BASE_URL },
            KEY_MODEL to modelInput.text.toString().trim().ifBlank { DEFAULT_MODEL },
            KEY_PROVIDER to provider
        )
    }

    private fun applyConfig(data: Map<String, String>) {
        val apiKey = data[KEY_API_KEY].orEmpty()
        val baseUrl = data[KEY_BASE_URL] ?: DEFAULT_BASE_URL
        val model = data[KEY_MODEL] ?: DEFAULT_MODEL
        val provider = data[KEY_PROVIDER] ?: DEFAULT_PROVIDER
        val providers = resources.getStringArray(R.array.provider_options)
        val index = providers.indexOf(provider).takeIf { it >= 0 } ?: 0

        apiKeyInput.setText(apiKey)
        baseUrlInput.setText(baseUrl)
        modelInput.setText(model)
        providerSpinner.setSelection(index)

        prefs.edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_MODEL, model)
            .putString(KEY_PROVIDER, provider)
            .apply()
    }

    private fun loadPresets(): List<Pair<String, Map<String, String>>> {
        val raw = prefs.getString(KEY_PRESETS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name", "")
                    if (name.isBlank()) continue
                    add(
                        name to mapOf(
                            KEY_API_KEY to obj.optString(KEY_API_KEY, ""),
                            KEY_BASE_URL to obj.optString(KEY_BASE_URL, DEFAULT_BASE_URL),
                            KEY_MODEL to obj.optString(KEY_MODEL, DEFAULT_MODEL),
                            KEY_PROVIDER to obj.optString(KEY_PROVIDER, DEFAULT_PROVIDER)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistPresets(presets: List<Pair<String, Map<String, String>>>) {
        val arr = JSONArray()
        presets.forEach { (name, data) ->
            val obj = JSONObject()
            obj.put("name", name)
            obj.put(KEY_API_KEY, data[KEY_API_KEY].orEmpty())
            obj.put(KEY_BASE_URL, data[KEY_BASE_URL] ?: DEFAULT_BASE_URL)
            obj.put(KEY_MODEL, data[KEY_MODEL] ?: DEFAULT_MODEL)
            obj.put(KEY_PROVIDER, data[KEY_PROVIDER] ?: DEFAULT_PROVIDER)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    private fun refreshPresetSpinner() {
        val presets = loadPresets().map { it.first }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            presets.ifEmpty { listOf(getString(R.string.preset_title)) }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        presetSpinner.adapter = adapter
    }

    private fun setSpinnerSelection(spinner: Spinner, value: String) {
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString() == value) {
                spinner.setSelection(i)
                break
            }
        }
    }

    private fun showCommandDialog(command: Command? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_command, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.commandTitleInput)
        val contentInput = dialogView.findViewById<EditText>(R.id.commandContentInput)

        titleInput.setText(command?.title ?: "")
        contentInput.setText(command?.content ?: "")

        val dialog = AlertDialog.Builder(this)
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
                    Toast.makeText(this, getString(R.string.command_input_required), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val updated = if (command == null) {
                    commandRepository.addCommand(title, content)
                } else {
                    commandRepository.updateCommand(command.id, title, content)
                }
                refreshCommands(updated)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun confirmDelete(command: Command) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_command)
            .setMessage(getString(R.string.delete_command_confirm, command.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                val updated = commandRepository.deleteCommand(command.id)
                refreshCommands(updated)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getOrCreateAuthToken(): String {
        var token = prefs.getString(KEY_AUTH_TOKEN, null)
        if (token.isNullOrBlank()) {
            token = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
        }
        return token
    }

    private fun showCommandDetail(command: Command) {
        Thread {
            val history = commandRepository.getHistory(command.id)
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
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle(command.title)
                    .setMessage(content.toString())
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }.start()
    }
}
