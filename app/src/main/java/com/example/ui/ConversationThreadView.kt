package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.data.Conversation
import com.example.ui.theme.StatusPresentGreen
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ConversationThreadView(
    currentUserId: String,
    conversation: Conversation,
    messagesState: UiState<List<ChatMessage>>,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val isAdminView = conversation.adminUid == currentUserId && conversation.parentUid != currentUserId
    val counterpartName = if (isAdminView) {
        conversation.parentName.ifBlank { "Parent" }
    } else {
        conversation.adminName.ifBlank { conversation.schoolName }
    }
    val headerTitle = conversation.schoolName.ifBlank { counterpartName }
    val headerSubtitle = if (counterpartName.equals(headerTitle, ignoreCase = true)) {
        "Online"
    } else {
        "Online • $counterpartName"
    }

    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun submitDraft() {
        val trimmed = draft.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
            draft = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("chat_thread_view"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top Chat Header
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to messages"
                                )
                            }
                            GradientInitialsAvatar(
                                initials = initialsFor(headerTitle),
                                size = 40.dp,
                                showOnlineDot = true,
                                modifier = Modifier.testTag("chat_header_avatar")
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = headerTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = headerSubtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                // 2. Scrollable Message Feed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    when (messagesState) {
                        UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        is UiState.Error -> Text(
                            text = messagesState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                        is UiState.Success -> {
                            if (messages.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GradientInitialsAvatar(
                                        initials = initialsFor(headerTitle),
                                        size = 56.dp,
                                        showOnlineDot = true
                                    )
                                    Text(
                                        text = "Start a conversation with $headerTitle",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Ask about admissions, fees, curriculum, or book a campus visit.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("chat_message_list")
                                ) {
                                    items(messages, key = { it.id }) { msg ->
                                        val isMine = msg.senderUid == currentUserId
                                        val formattedTime = msg.createdAt?.toDate()?.let {
                                            timeFormatter.format(it)
                                        }
                                        val timestampText = when {
                                            isMine && formattedTime != null -> "$formattedTime • Sent"
                                            isMine -> "Sending…"
                                            formattedTime != null -> formattedTime
                                            else -> "Just now"
                                        }

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(
                                                    topStart = 18.dp,
                                                    topEnd = 18.dp,
                                                    bottomStart = if (isMine) 18.dp else 4.dp,
                                                    bottomEnd = if (isMine) 4.dp else 18.dp
                                                ),
                                                color = if (isMine) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                },
                                                border = if (!isMine) {
                                                    BorderStroke(
                                                        0.5.dp,
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                    )
                                                } else {
                                                    null
                                                },
                                                modifier = Modifier
                                                    .widthIn(max = 312.dp)
                                                    .testTag("chat_message_bubble_${msg.id}")
                                            ) {
                                                Text(
                                                    text = msg.text,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 15.sp,
                                                        lineHeight = 21.sp
                                                    ),
                                                    color = if (isMine) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                                )
                                            }

                                            Text(
                                                text = timestampText,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = if (isMine) TextAlign.End else TextAlign.Start,
                                                modifier = Modifier
                                                    .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                                                    .testTag("chat_message_timestamp_${msg.id}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Bottom Input Composer
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                ) {
                    Column {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                placeholder = {
                                    Text(
                                        text = "Message...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submitDraft() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_message_input")
                            )

                            val canSend = draft.isNotBlank()
                            val buttonScale by animateFloatAsState(
                                targetValue = if (canSend) 1f else 0.94f,
                                label = "send_button_scale"
                            )

                            FilledIconButton(
                                onClick = { submitDraft() },
                                enabled = canSend,
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .scale(buttonScale)
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GradientInitialsAvatar(
    initials: String,
    size: Dp,
    showOnlineDot: Boolean,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = if (size >= 48.dp) 18.sp else 14.sp
            )
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(StatusPresentGreen)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

internal fun initialsFor(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "SQ"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}
