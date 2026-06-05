package com.household.app.feature.assistant

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.household.app.feature.assistant.model.ChatMessage
import com.household.app.feature.assistant.ui.components.*
import com.jugaad.core.llmruntime.LlamaEngine

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isEngineReady by viewModel.isEngineReady.collectAsStateWithLifecycle()
    val modelTier by viewModel.modelTier.collectAsStateWithLifecycle()
    val isSwapping by viewModel.isSwapping.collectAsStateWithLifecycle()
    val pending by viewModel.pendingNavigation.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }

    DisposableEffect(Unit) {
        viewModel.onScreenVisible()
        onDispose { viewModel.onScreenHidden() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090D)) // EliteNavy
    ) {
        // TopBar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                text = "JUGAAD ASSISTANT",
                color = LumePurple,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            )

            Spacer(Modifier.height(4.dp))

            if (isSwapping) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp,
                        color = LumePurple
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Switching model…", color = TextMuted,
                        style = TextStyle(fontSize = 11.sp, letterSpacing = 1.sp))
                }
            } else {
                val (label, chipColor) = when (modelTier) {
                    LlamaEngine.ModelTier.FAST -> "⚡ Fast  LFM2.5" to LumeCyan
                    LlamaEngine.ModelTier.DEEP -> "🧠 Deep  Gemma 4" to LumeAmber
                }
                Row(
                    modifier = Modifier.clickable { viewModel.toggleTier() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(chipColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        color = chipColor,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("tap to switch", color = TextMuted,
                        style = TextStyle(fontSize = 10.sp, letterSpacing = 0.5.sp))
                }
            }
        }

        if (!isEngineReady && messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Model loading…",
                    color = TextMuted,
                    style = TextStyle(fontSize = 14.sp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = scrollState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.role == ChatMessage.Role.USER) {
                        UserBubble(msg.text)
                    } else {
                        AssistantBubble(msg.text, msg.isLoading)
                    }
                }

                if (pending != null) {
                    item(key = "nav_chip") {
                        val label = when (pending) {
                            "wallet"        -> "Open Wallet"
                            "vault"         -> "Open Vault"
                            "subscriptions" -> "Open Subscriptions"
                            "documents"     -> "Open Documents"
                            "config"        -> "Open Settings"
                            else            -> "Open"
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp)) {
                            Text(
                                "→ $label",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LumeCyan.copy(alpha = 0.15f))
                                    .clickable { onNavigate(pending!!); viewModel.clearNavigation() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                color = LumeCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                SuggestedChips(onChipClick = { viewModel.sendMessage(it) })
            }

            InputRow(
                onSend = { viewModel.sendMessage(it) },
                isSending = messages.lastOrNull()?.isLoading == true,
                enabled = isEngineReady && !isSwapping
            )
        }
    }
}

@Composable
fun SuggestedChips(onChipClick: (String) -> Unit) {
    val suggestions = listOf(
        "Monthly spend?" to "What did I spend this month?",
        "Upcoming bills?" to "What bills are due soon?",
        "Subscriptions?" to "Show my active subscriptions",
        "Expiring docs?" to "What documents are expiring soon?"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { (label, prompt) ->
            Text(
                text = label,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(LumePurple.copy(alpha = 0.15f))
                    .clickable { onChipClick(prompt) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = LumePurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun InputRow(
    onSend: (String) -> Unit,
    isSending: Boolean,
    enabled: Boolean
) {
    var inputText by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EliteGlassCard(
            modifier = Modifier.weight(1f),
            glowColor = LumePurple.copy(alpha = 0.12f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text("Ask about your finances…", color = TextMuted, fontSize = 15.sp)
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
                    cursorBrush = SolidColor(LumePurple),
                    singleLine = false,
                    maxLines = 4
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = {
                onSend(inputText)
                inputText = ""
            },
            enabled = enabled && !isSending && inputText.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (enabled && !isSending && inputText.isNotBlank()) LumePurple else TextMuted.copy(alpha = 0.2f)),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
        ) {
            Icon(
                imageVector = Icons.Rounded.Send,
                contentDescription = "Send",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun UserBubble(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            color = LumePurple,
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun AssistantBubble(text: String, isLoading: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        EliteGlassCard(
            modifier = Modifier.widthIn(max = 310.dp),
            glowColor = LumeCyan.copy(alpha = 0.12f)
        ) {
            if (isLoading && text.isEmpty()) {
                TypingIndicator()
            } else {
                Column {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = TextMain,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = LumeCyan,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) { index ->
            val delay = index * 200
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0.2f at delay
                        1f at delay + 300
                        0.2f at delay + 600
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "dot"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(LumePurple.copy(alpha = alpha))
            )
        }
    }
}
