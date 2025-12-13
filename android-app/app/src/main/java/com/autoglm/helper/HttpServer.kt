package com.autoglm.helper

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.util.UUID

class HttpServer(private val service: AutoGLMAccessibilityService, port: Int = 8080) : NanoHTTPD(port) {
    
    companion object {
        private const val TAG = "AutoGLM-HttpServer"
        private const val PREF_NAME = "app_config"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_AUTH_TOKEN = "auth_token"

        private const val DEFAULT_BASE_URL = "https://api.grsai.com/v1"
        private const val DEFAULT_MODEL = "gpt-4-vision-preview"
        private const val DEFAULT_PROVIDER = "grs"

        @Volatile
        private var inMemoryCommand: JSONObject? = null
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d(TAG, "Request: $method $uri")

        return try {
            when {
                uri == "/status" && method == Method.GET -> handleStatus()
                uri == "/screenshot" && method == Method.GET -> handleScreenshot()
                uri == "/tap" && method == Method.POST -> handleTap(session)
                uri == "/swipe" && method == Method.POST -> handleSwipe(session)
                uri == "/input" && method == Method.POST -> handleInput(session)
                uri == "/config" && method == Method.GET -> handleConfigGet(session)
                uri == "/config" && method == Method.POST -> handleConfigUpdate(session)
                uri == "/command" && method == Method.POST -> handleCommandPost(session)
                uri == "/command" && method == Method.GET -> handleCommandGet(session)
                // 兼容旧接口
                uri == "/pending_command" && method == Method.POST -> handleCommandPost(session)
                uri == "/pending_command" && method == Method.GET -> handleCommandGet(session)
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    """{"error": "Not found"}"""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error": "${e.message}"}"""
            )
        }
    }

    private fun handleStatus(): Response {
        val json = JSONObject()
        json.put("status", "ok")
        json.put("service", "AutoGLM Helper")
        json.put("version", "1.0.0")
        json.put("accessibility_enabled", service.isAccessibilityEnabled())
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString()
        )
    }

    private fun handleScreenshot(): Response {
        val screenshot = service.takeScreenshotBase64()
        
        return if (screenshot != null) {
            val json = JSONObject()
            json.put("success", true)
            json.put("image", screenshot)
            json.put("format", "base64")
            
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                json.toString()
            )
        } else {
            val json = JSONObject()
            json.put("success", false)
            json.put("error", "Failed to take screenshot")
            
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                json.toString()
            )
        }
    }

    private fun handleTap(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        val json = JSONObject(body)
        
        val x = json.getInt("x")
        val y = json.getInt("y")
        
        val success = service.performTap(x, y)
        
        val response = JSONObject()
        response.put("success", success)
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response.toString()
        )
    }

    private fun handleSwipe(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        val json = JSONObject(body)
        
        val x1 = json.getInt("x1")
        val y1 = json.getInt("y1")
        val x2 = json.getInt("x2")
        val y2 = json.getInt("y2")
        val duration = json.optInt("duration", 300)
        
        val success = service.performSwipe(x1, y1, x2, y2, duration)
        
        val response = JSONObject()
        response.put("success", success)
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response.toString()
        )
    }

    private fun handleInput(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        val json = JSONObject(body)
        
        val text = json.getString("text")
        
        val success = service.performInput(text)
        
        val response = JSONObject()
        response.put("success", success)
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response.toString()
        )
    }

    private fun handleConfigGet(session: IHTTPSession): Response {
        if (!isAuthorized(session)) return unauthorized()
        val prefs = service.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        json.put("api_key", prefs.getString(KEY_API_KEY, "") ?: "")
        json.put("base_url", prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL)
        json.put("model", prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)
        json.put("provider", prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER)

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString()
        )
    }

    private fun handleConfigUpdate(session: IHTTPSession): Response {
        if (!isAuthorized(session)) return unauthorized()
        val body = getRequestBody(session)
        val json = JSONObject(body)

        val apiKey = json.optString("api_key", null)
        val baseUrl = json.optString("base_url", null)
        val model = json.optString("model", null)
        val provider = json.optString("provider", null)

        val prefs = service.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        if (apiKey != null) prefs.putString(KEY_API_KEY, apiKey)
        if (baseUrl != null) prefs.putString(KEY_BASE_URL, baseUrl.ifBlank { DEFAULT_BASE_URL })
        if (model != null) prefs.putString(KEY_MODEL, model.ifBlank { DEFAULT_MODEL })
        if (provider != null) prefs.putString(KEY_PROVIDER, provider.ifBlank { DEFAULT_PROVIDER })
        prefs.apply()

        val resp = JSONObject()
        resp.put("success", true)
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            resp.toString()
        )
    }

    private fun handleCommandPost(session: IHTTPSession): Response {
        if (!isAuthorized(session)) return unauthorized()
        val body = getRequestBody(session)
        val json = JSONObject(body)
        val content = json.optString("content", "")
        if (content.isBlank()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                """{"success":false,"error":"content is required"}"""
            )
        }

        val commandJson = JSONObject()
        commandJson.put("id", json.optString("id", ""))
        commandJson.put("title", json.optString("title", ""))
        commandJson.put("content", content)
        commandJson.put("updatedAt", json.optLong("updatedAt", System.currentTimeMillis()))
        inMemoryCommand = commandJson

        val resp = JSONObject()
        resp.put("success", true)
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            resp.toString()
        )
    }

    private fun handleCommandGet(session: IHTTPSession): Response {
        if (!isAuthorized(session)) return unauthorized()
        val params = session.parameters
        val clear = params["clear"]?.firstOrNull()?.toBooleanStrictOrNull() ?: true

        val cmd = inMemoryCommand
        if (cmd == null) {
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                """{"success":false,"message":"empty"}"""
            )
        }

        if (clear) {
            inMemoryCommand = null
        }

        val resp = JSONObject()
        resp.put("success", true)
        resp.put("id", cmd.optString("id", ""))
        resp.put("title", cmd.optString("title", ""))
        resp.put("content", cmd.optString("content", ""))
        resp.put("updatedAt", cmd.optLong("updatedAt", System.currentTimeMillis()))

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            resp.toString()
        )
    }

    private fun getRequestBody(session: IHTTPSession): String {
        val map = HashMap<String, String>()
        session.parseBody(map)
        return map["postData"] ?: ""
    }

    private fun getAuthToken(): String {
        val prefs = service.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var token = prefs.getString(KEY_AUTH_TOKEN, null)
        if (token.isNullOrBlank()) {
            token = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
        }
        return token
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val expected = getAuthToken()
        val provided = session.headers["x-auth-token"] ?: session.parameters["token"]?.firstOrNull()
        return expected.isBlank() || expected == provided
    }

    private fun unauthorized(): Response {
        return newFixedLengthResponse(
            Response.Status.UNAUTHORIZED,
            "application/json",
            """{"error":"unauthorized"}"""
        )
    }
}
