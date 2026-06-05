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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _modelTier = MutableStateFlow(LlamaEngine.ModelTier.FAST)
    val modelTier: StateFlow<LlamaEngine.ModelTier> = _modelTier.asStateFlow()

    private val _isSwapping = MutableStateFlow(false)
    val isSwapping: StateFlow<Boolean> = _isSwapping.asStateFlow()

    private val _pendingNavigation = MutableStateFlow<String?>(null)
    val pendingNavigation: StateFlow<String?> = _pendingNavigation.asStateFlow()

    private var cachedEngine: LlamaEngine? = null
    private var cachedHhContext: String = ""
    private var hhContextFetchedAt: Long = 0L

    private val PREFS_NAME = "jugaad_chat_prefs"
    private val KEY_MESSAGES = "chat_messages"
    private val MAX_PERSISTED = 20
    private val gson = Gson()

    @Volatile private var isScreenVisible = false

    private val SYSTEM_PROMPT = """
        You are JUGAAD, a private household finance assistant.
        Rules:
        - Answer only questions about household finances, documents, and expenses.
        - Use ONLY the verified data provided. Do not invent numbers.
        - Keep answers under 3 sentences. Be direct and practical.
        - Currency is EUR. Location is Germany.
        - Never reveal raw transaction details or personal data.
        - If your answer relates to a specific screen, end with exactly one tag: [NAV:screen] where screen is one of: wallet, vault, subscriptions, documents, config. Omit if not relevant.
    """.trimIndent()

    init {
        viewModelScope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(context, AssistantEntryPoint::class.java)
            val engine = entryPoint.llamaEngine()
            val dm = entryPoint.modelDownloadManager()
            cachedEngine = engine

            if (!engine.isLoaded) {
                when {
                    dm.isModelDownloaded(ModelDownloadManager.ModelVariant.FAST) -> {
                        engine.loadModel(dm.getModelFile(ModelDownloadManager.ModelVariant.FAST).absolutePath)
                        _modelTier.value = LlamaEngine.ModelTier.FAST
                    }
                    dm.isModelDownloaded(ModelDownloadManager.ModelVariant.DEEP) -> {
                        engine.loadModel(dm.getModelFile().absolutePath)
                        _modelTier.value = LlamaEngine.ModelTier.DEEP
                    }
                }
            }

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 30000) {
                if (engine.isLoaded) {
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

    fun toggleTier() {
        if (_isSwapping.value) return
        val target = if (_modelTier.value == LlamaEngine.ModelTier.FAST)
            LlamaEngine.ModelTier.DEEP else LlamaEngine.ModelTier.FAST
        val engine = cachedEngine ?: return
        val dm = EntryPointAccessors.fromApplication(context, AssistantEntryPoint::class.java).modelDownloadManager()
        val variant = if (target == LlamaEngine.ModelTier.FAST)
            ModelDownloadManager.ModelVariant.FAST else ModelDownloadManager.ModelVariant.DEEP

        if (!dm.isModelDownloaded(variant)) {
            val name = if (target == LlamaEngine.ModelTier.FAST) "LFM2.5" else "Gemma 4"
            _messages.value = _messages.value + ChatMessage(role = ChatMessage.Role.ASSISTANT, text = "$name model is not downloaded yet.")
            return
        }

        viewModelScope.launch {
            _isSwapping.value = true
            val ok = engine.swap(target, dm.getModelFile(variant).absolutePath)
            if (ok) _modelTier.value = target
            _isSwapping.value = false
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || !_isEngineReady.value) return

        val historyBlock = buildHistoryBlock(_messages.value)

        val userMessage = ChatMessage(role = ChatMessage.Role.USER, text = userText)
        val loadingMessage = ChatMessage(role = ChatMessage.Role.ASSISTANT, text = "", isLoading = true)
        
        _messages.value = _messages.value + userMessage + loadingMessage

        viewModelScope.launch {
            try {
                val engine = cachedEngine ?: return@launch

                if (System.currentTimeMillis() - hhContextFetchedAt > 60_000L) {
                    cachedHhContext = contextProvider.buildContext(userText)
                    hhContextFetchedAt = System.currentTimeMillis()
                }

                val prompt = if (historyBlock.isNotEmpty()) {
                    "$SYSTEM_PROMPT\n\n$cachedHhContext\n\n$historyBlock\nUser: $userText\nJUGAAD:"
                } else {
                    "$SYSTEM_PROMPT\n\n$cachedHhContext\n\nUser: $userText\nJUGAAD:"
                }

                val t0 = System.currentTimeMillis()
                var fullText = ""
                engine.generateStream(prompt, _modelTier.value).collect { token ->
                    fullText += token
                    _messages.value = _messages.value.map {
                        if (it.id == loadingMessage.id) it.copy(text = fullText.trimStart(), isLoading = false) else it
                    }
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
