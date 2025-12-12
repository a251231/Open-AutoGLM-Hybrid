package com.autoglm.helper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
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
    private lateinit var commandRepository: CommandRepository
    private lateinit var commandAdapter: CommandAdapter
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
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
        commandRepository = CommandRepository(this)
        commandAdapter = CommandAdapter(
            onPublish = { publishCommand(it) },
            onEdit = { showCommandDialog(it) },
            onDelete = { confirmDelete(it) }
        )
        
        openSettingsButton.setOnClickListener {
            openAccessibilitySettings()
        }
        
        testConnectionButton.setOnClickListener {
            testConnection()
        }

        addCommandButton.setOnClickListener {
            showCommandDialog()
        }

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
