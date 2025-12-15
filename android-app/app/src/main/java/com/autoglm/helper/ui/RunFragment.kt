package com.autoglm.helper.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.autoglm.helper.AutoGLMAccessibilityService
import com.autoglm.helper.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RunFragment : Fragment() {

    companion object {
        private const val PREF_NAME = "app_config"
        private const val KEY_HELPER_URL = "helper_url"
        private const val DEFAULT_HELPER_URL = "http://127.0.0.1:18080"
    }

    private val prefs by lazy { requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    private lateinit var agentStatusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var tipText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_run, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        agentStatusText = view.findViewById(R.id.agentStatusText)
        startButton = view.findViewById(R.id.agentStartButton)
        stopButton = view.findViewById(R.id.agentStopButton)
        tipText = view.findViewById(R.id.runTipText)
        tipText.text = getString(R.string.run_tip_termux)

        startButton.setOnClickListener { callAgent("/agent/start") }
        stopButton.setOnClickListener { callAgent("/agent/stop") }

        refreshAgentStatus()
    }

    private fun refreshAgentStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("${helperBaseUrl()}/agent/status")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                val code = conn.responseCode
                val body = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code == 200) {
                    val obj = JSONObject(body)
                    val running = obj.optBoolean("running", false)
                    withContext(Dispatchers.Main) {
                        agentStatusText.text = getString(
                            R.string.agent_status_label,
                            if (running) "运行中" else "已停止"
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun callAgent(path: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("${helperBaseUrl()}$path")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                val code = conn.responseCode
                conn.inputStream?.close()
                conn.disconnect()
                withContext(Dispatchers.Main) {
                    if (code == 200) {
                        refreshAgentStatus()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.agent_call_failed, "HTTP $code"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.agent_call_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun helperBaseUrl(): String {
        val raw = prefs.getString(KEY_HELPER_URL, DEFAULT_HELPER_URL) ?: DEFAULT_HELPER_URL
        return raw.trimEnd('/')
    }
}
