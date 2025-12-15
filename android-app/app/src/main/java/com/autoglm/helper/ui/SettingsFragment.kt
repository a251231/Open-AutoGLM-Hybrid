package com.autoglm.helper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.autoglm.helper.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class SettingsFragment : Fragment() {

    companion object {
        private const val PREF_NAME = "app_config"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_ACTIVE_PRESET = "active_preset"
        private const val KEY_PRESETS = "config_presets"
        private const val KEY_HELPER_URL = "helper_url"

        private const val DEFAULT_BASE_URL = "https://api.grsai.com/v1"
        private const val DEFAULT_MODEL = "gpt-4-vision-preview"
        private const val DEFAULT_PROVIDER = "grs"
        private const val DEFAULT_HELPER_URL = "http://127.0.0.1:18080"
    }

    private val prefs by lazy { requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    private lateinit var apiKeyInput: EditText
    private lateinit var baseUrlInput: EditText
    private lateinit var helperUrlInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var providerSpinner: Spinner
    private lateinit var authTokenText: TextView
    private lateinit var regenerateTokenButton: Button
    private lateinit var copyTokenButton: Button
    private lateinit var saveConfigButton: Button
    private lateinit var resetConfigButton: Button
    private lateinit var testConfigButton: Button
    private lateinit var presetNameInput: EditText
    private lateinit var presetSpinner: Spinner
    private lateinit var savePresetButton: Button
    private lateinit var activatePresetButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        apiKeyInput = view.findViewById(R.id.apiKeyInput)
        baseUrlInput = view.findViewById(R.id.baseUrlInput)
        helperUrlInput = view.findViewById(R.id.helperUrlInput)
        modelInput = view.findViewById(R.id.modelInput)
        providerSpinner = view.findViewById(R.id.providerSpinner)
        authTokenText = view.findViewById(R.id.authTokenText)
        regenerateTokenButton = view.findViewById(R.id.regenerateTokenButton)
        copyTokenButton = view.findViewById(R.id.copyTokenButton)
        saveConfigButton = view.findViewById(R.id.saveConfigButton)
        resetConfigButton = view.findViewById(R.id.resetConfigButton)
        testConfigButton = view.findViewById(R.id.testConfigButton)
        presetNameInput = view.findViewById(R.id.presetNameInput)
        presetSpinner = view.findViewById(R.id.presetSpinner)
        savePresetButton = view.findViewById(R.id.savePresetButton)
        activatePresetButton = view.findViewById(R.id.activatePresetButton)

        setupProviderSpinner()
        refreshPresetSpinner()
        loadConfigToUi()

        saveConfigButton.setOnClickListener { saveConfig() }
        resetConfigButton.setOnClickListener { resetConfig() }
        testConfigButton.setOnClickListener { testConfigEndpoint() }
        regenerateTokenButton.setOnClickListener { regenerateToken() }
        copyTokenButton.setOnClickListener { copyToken() }
        savePresetButton.setOnClickListener { saveCurrentToPreset() }
        activatePresetButton.setOnClickListener { activateSelectedPreset() }
    }

    private fun setupProviderSpinner() {
        val providers = resources.getStringArray(R.array.provider_options)
        val adapter = ArrayAdapter(
            requireContext(),
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
        val helperUrl = prefs.getString(KEY_HELPER_URL, DEFAULT_HELPER_URL) ?: DEFAULT_HELPER_URL
        val authToken = getOrCreateAuthToken()
        val providers = resources.getStringArray(R.array.provider_options)
        val index = providers.indexOf(provider).takeIf { it >= 0 } ?: 0

        apiKeyInput.setText(apiKey)
        baseUrlInput.setText(baseUrl)
        helperUrlInput.setText(helperUrl)
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
        val helperUrl = helperUrlInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val provider = providerSpinner.selectedItem?.toString()?.trim() ?: DEFAULT_PROVIDER

        if (baseUrl.isBlank() || !isValidUrl(baseUrl)) {
            Toast.makeText(requireContext(), getString(R.string.config_base_url_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        if (helperUrl.isBlank() || !isValidUrl(helperUrl)) {
            Toast.makeText(requireContext(), getString(R.string.config_helper_url_invalid), Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_HELPER_URL, helperUrl)
            .putString(KEY_MODEL, if (model.isBlank()) DEFAULT_MODEL else model)
            .putString(KEY_PROVIDER, provider)
            .apply()

        Toast.makeText(requireContext(), getString(R.string.config_save_success), Toast.LENGTH_SHORT).show()
    }

    private fun resetConfig() {
        apiKeyInput.setText("")
        baseUrlInput.setText(DEFAULT_BASE_URL)
        helperUrlInput.setText(DEFAULT_HELPER_URL)
        modelInput.setText(DEFAULT_MODEL)
        val providers = resources.getStringArray(R.array.provider_options)
        val index = providers.indexOf(DEFAULT_PROVIDER).takeIf { it >= 0 } ?: 0
        providerSpinner.setSelection(index)

        prefs.edit()
            .putString(KEY_API_KEY, "")
            .putString(KEY_BASE_URL, DEFAULT_BASE_URL)
            .putString(KEY_HELPER_URL, DEFAULT_HELPER_URL)
            .putString(KEY_MODEL, DEFAULT_MODEL)
            .putString(KEY_PROVIDER, DEFAULT_PROVIDER)
            .apply()

        Toast.makeText(requireContext(), getString(R.string.config_reset_success), Toast.LENGTH_SHORT).show()
    }

    private fun testConfigEndpoint() {
        val baseUrl = baseUrlInput.text.toString().trim().ifBlank { DEFAULT_BASE_URL }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(baseUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val code = connection.responseCode
                connection.inputStream?.close()
                connection.disconnect()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.config_test_success, code),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.config_test_failed, e.message ?: "未知错误"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun regenerateToken() {
        val token = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
        authTokenText.text = "${getString(R.string.config_token_label)}:\n$token"
        Toast.makeText(requireContext(), getString(R.string.config_token_regenerated), Toast.LENGTH_SHORT).show()
    }

    private fun copyToken() {
        val token = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        if (token.isBlank()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AUTOGLM_AUTH_TOKEN", token))
        Toast.makeText(requireContext(), getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
    }

    private fun saveCurrentToPreset() {
        val name = presetNameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.preset_name_required), Toast.LENGTH_SHORT).show()
            return
        }
        val presets = loadPresets().toMutableList().filter { it.first != name }.toMutableList()
        presets.add(0, name to collectConfig())
        persistPresets(presets)
        prefs.edit().putString(KEY_ACTIVE_PRESET, name).apply()
        refreshPresetSpinner()
        setSpinnerSelection(presetSpinner, name)
        Toast.makeText(requireContext(), getString(R.string.preset_saved, name), Toast.LENGTH_SHORT).show()
    }

    private fun activateSelectedPreset() {
        val name = presetSpinner.selectedItem?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.preset_name_required), Toast.LENGTH_SHORT).show()
            return
        }
        val presets = loadPresets().toMap()
        val data = presets[name] ?: return
        applyConfig(data)
        prefs.edit().putString(KEY_ACTIVE_PRESET, name).apply()
        presetNameInput.setText(name)
        Toast.makeText(requireContext(), getString(R.string.preset_activated, name), Toast.LENGTH_SHORT).show()
    }

    private fun collectConfig(): Map<String, String> {
        val provider = providerSpinner.selectedItem?.toString()?.trim() ?: DEFAULT_PROVIDER
        return mapOf(
            KEY_API_KEY to apiKeyInput.text.toString().trim(),
            KEY_BASE_URL to baseUrlInput.text.toString().trim().ifBlank { DEFAULT_BASE_URL },
            KEY_HELPER_URL to helperUrlInput.text.toString().trim().ifBlank { DEFAULT_HELPER_URL },
            KEY_MODEL to modelInput.text.toString().trim().ifBlank { DEFAULT_MODEL },
            KEY_PROVIDER to provider
        )
    }

    private fun applyConfig(data: Map<String, String>) {
        val apiKey = data[KEY_API_KEY].orEmpty()
        val baseUrl = data[KEY_BASE_URL] ?: DEFAULT_BASE_URL
        val helperUrl = data[KEY_HELPER_URL] ?: DEFAULT_HELPER_URL
        val model = data[KEY_MODEL] ?: DEFAULT_MODEL
        val provider = data[KEY_PROVIDER] ?: DEFAULT_PROVIDER
        val providers = resources.getStringArray(R.array.provider_options)
        val index = providers.indexOf(provider).takeIf { it >= 0 } ?: 0

        apiKeyInput.setText(apiKey)
        baseUrlInput.setText(baseUrl)
        helperUrlInput.setText(helperUrl)
        modelInput.setText(model)
        providerSpinner.setSelection(index)

        prefs.edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_HELPER_URL, helperUrl)
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
                            KEY_HELPER_URL to obj.optString(KEY_HELPER_URL, DEFAULT_HELPER_URL),
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
            val obj = org.json.JSONObject()
            obj.put("name", name)
            obj.put(KEY_API_KEY, data[KEY_API_KEY].orEmpty())
            obj.put(KEY_BASE_URL, data[KEY_BASE_URL] ?: DEFAULT_BASE_URL)
            obj.put(KEY_HELPER_URL, data[KEY_HELPER_URL] ?: DEFAULT_HELPER_URL)
            obj.put(KEY_MODEL, data[KEY_MODEL] ?: DEFAULT_MODEL)
            obj.put(KEY_PROVIDER, data[KEY_PROVIDER] ?: DEFAULT_PROVIDER)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    private fun refreshPresetSpinner() {
        val presets = loadPresets().map { it.first }
        val adapter = ArrayAdapter(
            requireContext(),
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

    private fun getOrCreateAuthToken(): String {
        var token = prefs.getString(KEY_AUTH_TOKEN, null)
        if (token.isNullOrBlank()) {
            token = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
        }
        return token
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsed = Uri.parse(url)
            !TextUtils.isEmpty(parsed.scheme) && !TextUtils.isEmpty(parsed.host)
        } catch (e: Exception) {
            false
        }
    }
}
