package com.household.app.feature.assistant

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.household.app.feature.assistant.model.ChatMessage
import com.jugaad.core.llmruntime.LlamaEngine
import com.jugaad.core.llmruntime.ModelDownloadManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt
import javax.inject.Inject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AssistantEntryPoint {
    fun llamaEngine(): LlamaEngine
    fun modelDownloadManager(): ModelDownloadManager
}

@HiltViewModel
class AssistantViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contextProvider: HouseholdContextProvider
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    private val _modelTier = MutableStateFlow(LlamaEngine.ModelTier.DEEP)
    val modelTier: StateFlow<LlamaEngine.ModelTier> = _modelTier.asStateFlow()

    private val _isSwapping = MutableStateFlow(false)
    val isSwapping: StateFlow<Boolean> = _isSwapping.asStateFlow()

    private val _inferenceBackend = MutableStateFlow(InferenceBackend.LOCAL)
    val inferenceBackend: StateFlow<InferenceBackend> = _inferenceBackend.asStateFlow()

    private val _pendingNavigation = MutableStateFlow<String?>(null)
    val pendingNavigation: StateFlow<String?> = _pendingNavigation.asStateFlow()

    private var cachedEngine: LlamaEngine? = null
    private var cachedHhContext: String = ""
    private var hhContextFetchedAt: Long = 0L

    private val PREFS_NAME = "jugaad_chat_prefs"
    private val KEY_MESSAGES = "chat_messages"
    private val KEY_INFERENCE_BACKEND = "inference_backend"
    private val MAX_PERSISTED = 20
    private val gson = Gson()

    @Volatile private var isScreenVisible = false

    private val SYSTEM_PROMPT = """
        Finance AI. Rules: use only provided data, max 3 sentences, no preamble, EUR/Germany.
        Optional tag at end: [NAV:wallet|vault|subscriptions|documents|config]
        If exact DB data is needed, output exactly one tool call in this format and nothing else:
        [SQL: SELECT ...]
        Allowed SQL is read-only single statement starting with SELECT or WITH.
    """.trimIndent()

    private val OLLAMA_BASE_URL = "http://192.168.2.66:11434"
    private val OLLAMA_TEST_MODEL = "qwen2.5-coder:1.5b-base"
    private val SQL_MAX_STEPS = 3
    private val SQL_MAX_ROWS = 200
    private val SQL_RESULT_MAX_CHARS = 120_000
    private val SQL_MARKER = Regex("""\[SQL:\s*(.*?)]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    enum class InferenceBackend {
        LOCAL,
        OLLAMA
    }

    init {
        viewModelScope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(context, AssistantEntryPoint::class.java)
            val engine = entryPoint.llamaEngine()
            val dm = entryPoint.modelDownloadManager()
            cachedEngine = engine
            dm.deleteLegacyFastModelIfPresent()

            if (!engine.isLoaded && dm.isModelDownloaded(ModelDownloadManager.ModelVariant.DEEP)) {
                engine.loadModel(
                    dm.getModelFile(ModelDownloadManager.ModelVariant.DEEP).absolutePath,
                    LlamaEngine.ModelTier.DEEP
                )
            }

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 30000) {
                if (engine.isLoaded) {
                    // Sync tier from engine singleton, which may already be loaded.
                    _modelTier.value = engine.currentTier
                    val backendPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_INFERENCE_BACKEND, InferenceBackend.LOCAL.name)
                    _inferenceBackend.value = runCatching {
                        InferenceBackend.valueOf(backendPref ?: InferenceBackend.LOCAL.name)
                    }.getOrDefault(InferenceBackend.LOCAL)
                    _isEngineReady.value = true
                    val persisted = loadMessages()
                    if (persisted.isNotEmpty()) _messages.value = persisted
                    break
                }
                delay(500)
            }
        }
    }

    fun onScreenVisible() { isScreenVisible = true }
    fun onScreenHidden() { isScreenVisible = false }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || !_isEngineReady.value) return

        val trimmedInput = userText.trim()
        if (trimmedInput.equals("/ollama", ignoreCase = true)
            || trimmedInput.equals("/ollama test", ignoreCase = true)
            || trimmedInput.equals("test ollama", ignoreCase = true)
        ) {
            sendOllamaProbe(trimmedInput)
            return
        }

        if (_inferenceBackend.value == InferenceBackend.OLLAMA) {
            sendMessageViaOllama(trimmedInput)
            return
        }

        val historyBlock = buildHistoryBlock(_messages.value)

        val userMessage = ChatMessage(role = ChatMessage.Role.USER, text = userText)
        val loadingMessage = ChatMessage(role = ChatMessage.Role.ASSISTANT, text = "", isLoading = true)
        
        _messages.value = _messages.value + userMessage + loadingMessage

        viewModelScope.launch {
            try {
                val engine = cachedEngine ?: return@launch

                if (System.currentTimeMillis() - hhContextFetchedAt > 60_000L) {
                    cachedHhContext = contextProvider.buildContext(userText, includeFullDb = false)
                    hhContextFetchedAt = System.currentTimeMillis()
                }

                // Suffix "Here:" forces the model to skip polite preamble and answer immediately
                val prompt = if (historyBlock.isNotEmpty()) {
                    "$SYSTEM_PROMPT\n\n$cachedHhContext\n\n${historyBlock}User: $userText\nJUGAAD: Here:"
                } else {
                    "$SYSTEM_PROMPT\n\n$cachedHhContext\n\nUser: $userText\nJUGAAD: Here:"
                }

                val t0 = System.currentTimeMillis()
                val fullText = resolveWithSqlToolLoop(prompt) { p ->
                    engine.generate(p, LlamaEngine.ModelTier.DEEP)
                }
                _messages.value = _messages.value.map {
                    if (it.id == loadingMessage.id) it.copy(text = fullText.trimStart(), isLoading = false) else it
                }
                Log.d("AssistantVM", "response time: ${System.currentTimeMillis() - t0}ms")

                val navRegex = Regex("""\[NAV:(\w+)]\s*$""")
                val match = navRegex.find(fullText)
                if (match != null) {
                    val route = match.groupValues[1]
                    val cleanText = fullText.substring(0, match.range.first).trimEnd()
                    _messages.value = _messages.value.map {
                        if (it.id == loadingMessage.id) it.copy(text = cleanText) else it
                    }
                    _pendingNavigation.value = route
                }

                saveMessages(_messages.value)
                if (!isScreenVisible) postResponseNotification(fullText)

            } catch (e: Exception) {
                Log.e("AssistantVM", "Generation failed", e)
                _messages.value = _messages.value.map {
                    if (it.id == loadingMessage.id) it.copy(text = "Sorry, something went wrong.", isLoading = false) else it
                }
            }
        }
    }

    fun toggleInferenceBackend() {
        val next = if (_inferenceBackend.value == InferenceBackend.LOCAL) {
            InferenceBackend.OLLAMA
        } else {
            InferenceBackend.LOCAL
        }
        _inferenceBackend.value = next
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_INFERENCE_BACKEND, next.name)
            .apply()

        val info = if (next == InferenceBackend.OLLAMA) {
            "Switched to Remote Ollama responses."
        } else {
            "Switched to Local Gemma responses."
        }
        _messages.value = _messages.value + ChatMessage(role = ChatMessage.Role.ASSISTANT, text = info)
        saveMessages(_messages.value)
    }

    private fun sendMessageViaOllama(userText: String) {
        val historyBlock = buildHistoryBlock(_messages.value)
        val userMessage = ChatMessage(role = ChatMessage.Role.USER, text = userText)
        val loadingMessage = ChatMessage(role = ChatMessage.Role.ASSISTANT, text = "", isLoading = true)
        _messages.value = _messages.value + userMessage + loadingMessage

        viewModelScope.launch {
            try {
                if (System.currentTimeMillis() - hhContextFetchedAt > 60_000L) {
                    cachedHhContext = contextProvider.buildContext(userText, includeFullDb = false)
                    hhContextFetchedAt = System.currentTimeMillis()
                }

                val prompt = if (historyBlock.isNotEmpty()) {
                    "$SYSTEM_PROMPT\n\n$cachedHhContext\n\n${historyBlock}User: $userText\nJUGAAD: Here:"
                } else {
                    "$SYSTEM_PROMPT\n\n$cachedHhContext\n\nUser: $userText\nJUGAAD: Here:"
                }

                val reply = resolveWithSqlToolLoop(prompt) { p -> requestOllamaCompletion(p) }
                _messages.value = _messages.value.map {
                    if (it.id == loadingMessage.id) it.copy(text = reply, isLoading = false) else it
                }

                val navRegex = Regex("""\[NAV:(\w+)]\s*$""")
                val match = navRegex.find(reply)
                if (match != null) {
                    val route = match.groupValues[1]
                    val cleanText = reply.substring(0, match.range.first).trimEnd()
                    _messages.value = _messages.value.map {
                        if (it.id == loadingMessage.id) it.copy(text = cleanText) else it
                    }
                    _pendingNavigation.value = route
                }

                saveMessages(_messages.value)
                if (!isScreenVisible) postResponseNotification(reply)
            } catch (e: Exception) {
                _messages.value = _messages.value.map {
                    if (it.id == loadingMessage.id) it.copy(text = "Ollama request failed: ${e.message ?: "unknown error"}", isLoading = false) else it
                }
            }
        }
    }

    private suspend fun resolveWithSqlToolLoop(
        basePrompt: String,
        complete: suspend (String) -> String
    ): String {
        var prompt = basePrompt
        repeat(SQL_MAX_STEPS) {
            val reply = complete(prompt).trim()
            val sql = extractSqlCall(reply)
            if (sql == null) return reply

            if (!isSafeReadOnlySql(sql)) {
                prompt = buildSqlFeedbackPrompt(
                    basePrompt = basePrompt,
                    sql = sql,
                    resultJson = "{\"error\":\"blocked_query_only_select_with_allowed\"}",
                    previousReply = reply
                )
                return@repeat
            }

            val result = contextProvider.executeReadOnlySql(
                query = sql,
                maxRows = SQL_MAX_ROWS,
                maxChars = SQL_RESULT_MAX_CHARS
            )
            prompt = buildSqlFeedbackPrompt(
                basePrompt = basePrompt,
                sql = sql,
                resultJson = result,
                previousReply = reply
            )
        }
        return "I need more than $SQL_MAX_STEPS SQL steps for this. Please narrow the request."
    }

    private fun buildSqlFeedbackPrompt(
        basePrompt: String,
        sql: String,
        resultJson: String,
        previousReply: String
    ): String {
        return """
$basePrompt

Previous model output:
$previousReply

SQL_TOOL_EXECUTION
QUERY:
$sql
RESULT_JSON:
$resultJson
END_SQL_TOOL_EXECUTION

If more DB data is needed, output exactly one new [SQL: ...] and nothing else.
If sufficient, provide final answer now (max 3 sentences, no preamble).
""".trimIndent()
    }

    private fun extractSqlCall(reply: String): String? {
        val match = SQL_MARKER.find(reply) ?: return null
        val sql = match.groupValues.getOrNull(1)?.trim().orEmpty()
        return sql.ifEmpty { null }
    }

    private fun isSafeReadOnlySql(sql: String): Boolean {
        val q = sql.trim()
        if (q.contains(";")) return false
        val lower = q.lowercase()
        if (!(lower.startsWith("select") || lower.startsWith("with"))) return false
        val blocked = Regex("""\b(insert|update|delete|drop|alter|create|replace|truncate|attach|detach|pragma|vacuum|reindex)\b""")
        return !blocked.containsMatchIn(lower)
    }

    private fun sendOllamaProbe(userText: String) {
        val userMessage = ChatMessage(role = ChatMessage.Role.USER, text = userText)
        val loadingMessage = ChatMessage(role = ChatMessage.Role.ASSISTANT, text = "", isLoading = true)
        _messages.value = _messages.value + userMessage + loadingMessage

        viewModelScope.launch {
            val resultText = runCatching { probeOllama() }
                .getOrElse { "Ollama check failed: ${it.message ?: "unknown error"}" }

            _messages.value = _messages.value.map {
                if (it.id == loadingMessage.id) it.copy(text = resultText, isLoading = false) else it
            }
            saveMessages(_messages.value)
        }
    }

    private suspend fun probeOllama(): String = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val tagsJson = httpGetJson("$OLLAMA_BASE_URL/api/tags", 10_000, 20_000)
        val tags = gson.fromJson(tagsJson, OllamaTagsResponse::class.java)
        val models = tags.models.orEmpty()
        if (models.isEmpty()) {
            return@withContext "Ollama reachable at $OLLAMA_BASE_URL, but no models are installed."
        }

        val preferred = models.firstOrNull { it.name == OLLAMA_TEST_MODEL }
        val chosen = preferred ?: models.firstOrNull { it.capabilities.orEmpty().contains("completion") } ?: models.first()

        val body = gson.toJson(
            OllamaGenerateRequest(
                model = chosen.name,
                prompt = "Reply with exactly: OLLAMA_APP_OK",
                stream = false
            )
        )
        val generateJson = httpPostJson("$OLLAMA_BASE_URL/api/generate", body, 10_000, 30_000)
        val generated = gson.fromJson(generateJson, OllamaGenerateResponse::class.java)
        val elapsedMs = (System.currentTimeMillis() - startedAt)

        val reply = generated.response?.trim().orEmpty().ifEmpty { "<empty>" }
        val totalMs = ((generated.total_duration ?: 0L) / 1_000_000.0).roundToInt()

        return@withContext if (reply.contains("OLLAMA_APP_OK")) {
            "Ollama connected. Model: ${chosen.name}. Reply: $reply. App time: ${elapsedMs}ms. Server total: ${totalMs}ms."
        } else {
            "Ollama connected, but test reply differed. Model: ${chosen.name}. Reply: $reply."
        }
    }

    private suspend fun requestOllamaCompletion(prompt: String): String = withContext(Dispatchers.IO) {
        val tagsJson = httpGetJson("$OLLAMA_BASE_URL/api/tags", 10_000, 20_000)
        val tags = gson.fromJson(tagsJson, OllamaTagsResponse::class.java)
        val models = tags.models.orEmpty()
        if (models.isEmpty()) {
            throw IllegalStateException("No Ollama models found on $OLLAMA_BASE_URL")
        }
        val chosen = models.firstOrNull { it.name == OLLAMA_TEST_MODEL }
            ?: models.firstOrNull { it.capabilities.orEmpty().contains("completion") }
            ?: models.first()

        val body = gson.toJson(
            OllamaGenerateRequest(
                model = chosen.name,
                prompt = prompt,
                stream = false
            )
        )
        val generateJson = httpPostJson("$OLLAMA_BASE_URL/api/generate", body, 10_000, 60_000)
        val generated = gson.fromJson(generateJson, OllamaGenerateResponse::class.java)
        generated.response?.trim().orEmpty().ifEmpty {
            throw IllegalStateException("Ollama returned empty response")
        }
    }

    private fun httpGetJson(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
        }
        return conn.useAndReadJson()
    }

    private fun httpPostJson(url: String, jsonBody: String, connectTimeoutMs: Int, readTimeoutMs: Int): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { out ->
            out.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
        }
        return conn.useAndReadJson()
    }

    private fun HttpURLConnection.useAndReadJson(): String {
        return try {
            val code = responseCode
            val stream = if (code in 200..299) inputStream else errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code ${responseMessage ?: ""} ${text.take(180)}".trim())
            }
            text
        } finally {
            disconnect()
        }
    }

    private data class OllamaTagsResponse(
        val models: List<OllamaModel>? = null
    )

    private data class OllamaModel(
        val name: String,
        val capabilities: List<String>? = null
    )

    private data class OllamaGenerateRequest(
        val model: String,
        val prompt: String,
        val stream: Boolean
    )

    private data class OllamaGenerateResponse(
        val response: String? = null,
        val total_duration: Long? = null
    )

    private fun buildHistoryBlock(messages: List<ChatMessage>): String {
        val lastTurns = messages.filter { !it.isLoading }.takeLast(4)
        if (lastTurns.isEmpty()) return ""

        val sb = StringBuilder()
        lastTurns.forEach { msg ->
            val role = if (msg.role == ChatMessage.Role.USER) "User" else "JUGAAD"
            sb.append("$role: ${msg.text}\n")
        }

        var block = sb.toString()
        while (block.length > 400) {
            val firstNewline = block.indexOf('\n')
            if (firstNewline == -1) break
            block = block.substring(firstNewline + 1)
        }
        return block
    }

    private fun saveMessages(messages: List<ChatMessage>) {
        val json = gson.toJson(messages.filter { !it.isLoading }.takeLast(MAX_PERSISTED))
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MESSAGES, json).apply()
    }

    private fun loadMessages(): List<ChatMessage> {
        return try {
            val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MESSAGES, null) ?: return emptyList()
            gson.fromJson(json, Array<ChatMessage>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearNavigation() { _pendingNavigation.value = null }

    fun clearHistory() {
        _messages.value = emptyList()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun postResponseNotification(preview: String) {
        val nm = androidx.core.app.NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val channelId = "jugaad_assistant"
        val mgr = context.getSystemService(android.app.NotificationManager::class.java)
        if (mgr != null && mgr.getNotificationChannel(channelId) == null) {
            mgr.createNotificationChannel(
                android.app.NotificationChannel(
                    channelId, "JUGAAD Assistant",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Response-ready alerts" }
            )
        }
        val intent = try {
            android.content.Intent(context, Class.forName("com.household.app.MainActivity")).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_assistant", true)
            }
        } catch (e: Exception) { null } ?: return
        val pi = android.app.PendingIntent.getActivity(context, 9001, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        nm.notify(9001,
            androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("JUGAAD Assistant")
                .setContentText(preview.trimStart().take(80))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
    }
}
