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
import java.net.HttpURLConnection
import java.net.URL

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
        commandRepository = CommandRepository(this)
        commandAdapter = CommandAdapter(
            onPublish = { publishCommand(it) },
            onEdit = { showCommandDialog(it) },
            onDelete = { confirmDelete(it) }
        )

        setupProviderSpinner()
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

        commandListView.layoutManager = LinearLayoutManager(this)
        commandListView.adapter = commandAdapter
        refreshCommands()
        
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
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
        val providers = resources.getStringArray(R.array.provider_options)
        val index = providers.indexOf(provider).takeIf { it >= 0 } ?: 0

        apiKeyInput.setText(apiKey)
        baseUrlInput.setText(baseUrl)
        modelInput.setText(model)
        providerSpinner.setSelection(index)
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

    private fun publishCommand(command: Command) {
        val service = AutoGLMAccessibilityService.getInstance()
        if (service == null) {
            Toast.makeText(this, getString(R.string.enable_accessibility), Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            val success = service.performInput(command.content)
            runOnUiThread {
                val message = if (success) {
                    getString(R.string.command_publish_success, command.title)
                } else {
                    getString(R.string.command_publish_failed, command.title)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
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
}
