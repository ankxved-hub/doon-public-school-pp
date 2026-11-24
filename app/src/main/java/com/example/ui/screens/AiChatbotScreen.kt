package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SchoolUser
import com.example.ui.theme.*
import com.example.ui.viewmodel.AiChatMessage
import com.example.ui.viewmodel.AiTutorPersona
import com.example.ui.viewmodel.AiTutorUiState
import com.example.ui.viewmodel.GeminiChatModel

/**
 * Multi-turn Gemini AI Chatbot accessible across Student, Teacher, and Admin portals.
 * Maintains conversation history via Firebase AI Logic startChat(), streams replies in real time,
 * and lets users switch system instruction role personas and model speed/depth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatbotScreen(
    currentUser: SchoolUser,
    messages: List<AiChatMessage>,
    tutorState: AiTutorUiState,
    selectedPersona: AiTutorPersona,
    selectedModel: GeminiChatModel,
    onSelectPersona: (AiTutorPersona) -> Unit,
    onSelectModel: (GeminiChatModel) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isBusy = tutorState is AiTutorUiState.Loading || tutorState is AiTutorUiState.Streaming

    val quickPrompts = remember(currentUser.role, selectedPersona) {
        when (selectedPersona) {
            AiTutorPersona.DPT_COACH -> listOf(
                "Create a 5-question MCQ practice test on Quadratic Equations",
                "Design a 20-mark Daily Practice Test for Science Electricity",
                "Give quick revision points for CBSE Social Science History",
            )
            AiTutorPersona.CIRCULAR_WRITER -> listOf(
                "Draft a Principal circular announcing a PTM this Saturday",
                "Write a crisp staff message reminding teachers about DPT deadlines",
                "Draft a winter uniform and school timing notice for parents",
            )
            AiTutorPersona.CRAFT_MENTOR -> listOf(
                "Suggest 3 low-cost Science Exhibition working models for Class VIII",
                "How to build a cardboard solar system diorama step-by-step?",
                "Give eco-friendly Best Out of Waste craft ideas",
            )
            AiTutorPersona.STUDY_TUTOR -> listOf(
                "Explain Newton's 3 laws of motion with simple daily examples",
                "Help me solve linear equations in two variables step-by-step",
                "Summarize photosynthesis for a 5-mark board exam answer",
            )
        }
    }

    LaunchedEffect(messages.size, (tutorState as? AiTutorUiState.Streaming)?.partialText?.length) {
        val targetIndex = (messages.size + if (isBusy) 1 else 0).coerceAtLeast(1) - 1
        listState.animateScrollToItem(targetIndex)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_chatbot_screen"),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDeep,
                    titleContentColor = Color.White,
                    navigationIconContentColor = GoldAmber,
                    actionIconContentColor = GoldAmber,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp).testTag("ai_chat_back_btn"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close AI Assistant")
                    }
                },
                title = {
                    Column {
                        Text("Doon AI Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${selectedPersona.label} • ${selectedModel.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAmber,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onClearChat,
                        enabled = !isBusy,
                        modifier = Modifier.size(48.dp).testTag("ai_chat_clear_btn"),
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear Chat History")
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(1500) },
                        placeholder = { Text("Ask anything about studies, tests, or circulars...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_chat_input"),
                    )
                    FilledIconButton(
                        onClick = {
                            val text = draft.trim()
                            if (text.isNotEmpty() && !isBusy) {
                                draft = ""
                                onSendMessage(text)
                            }
                        },
                        enabled = draft.isNotBlank() && !isBusy,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = NavyDeep,
                            contentColor = GoldAmber,
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("ai_chat_send_btn"),
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = GoldAmber,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Message")
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Role Persona & Model Selector Strip
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Text("Role:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AiTutorPersona.entries.forEach { persona ->
                            FilterChip(
                                selected = selectedPersona == persona,
                                onClick = { onSelectPersona(persona) },
                                label = { Text(persona.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("ai_persona_${persona.name.lowercase()}"),
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Text("Model:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        GeminiChatModel.entries.forEach { model ->
                            FilterChip(
                                selected = selectedModel == model,
                                onClick = { onSelectModel(model) },
                                leadingIcon = {
                                    Icon(
                                        when (model) {
                                            GeminiChatModel.FAST -> Icons.Filled.Bolt
                                            GeminiChatModel.GENERAL -> Icons.Filled.AutoAwesome
                                            GeminiChatModel.DEEP -> Icons.Filled.Psychology
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                label = { Text(model.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("ai_model_${model.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            // Scrollable Chat Thread
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(messages) { idx, msg ->
                    AiChatBubble(message = msg, isStreaming = false, tagIndex = idx)
                }

                if (messages.size <= 1 && !isBusy) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Try asking:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            quickPrompts.forEach { prompt ->
                                OutlinedCard(
                                    onClick = { onSendMessage(prompt) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GoldAmberDark, modifier = Modifier.size(16.dp))
                                        Text(prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }

                when (tutorState) {
                    is AiTutorUiState.Loading -> {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 6.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NavyDeep)
                                Text(
                                    "Gemini (${selectedModel.label}) is thinking...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    is AiTutorUiState.Streaming -> {
                        item {
                            AiChatBubble(
                                message = AiChatMessage(isUser = false, text = tutorState.partialText),
                                isStreaming = true,
                                tagIndex = messages.size,
                            )
                        }
                    }
                    is AiTutorUiState.Error -> {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = RoseContainer),
                                border = BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().testTag("ai_chat_error_card"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = "Error", tint = RoseError)
                                    Text(
                                        tutorState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RoseError,
                                    )
                                }
                            }
                        }
                    }
                    AiTutorUiState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun AiChatBubble(
    message: AiChatMessage,
    isStreaming: Boolean,
    tagIndex: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_chat_msg_$tagIndex"),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp,
            ),
            color = if (message.isUser) NavyDeep else MaterialTheme.colorScheme.surface,
            border = if (message.isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = if (message.isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 310.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!message.isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GoldAmberDark, modifier = Modifier.size(14.dp))
                        Text(
                            if (isStreaming) "Gemini • typing..." else "Gemini Assistant",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
