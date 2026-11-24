package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ChatMessage
import com.example.data.Conversation
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MessagesScreen(
    currentUserId: String,
    conversationsState: UiState<List<Conversation>>,
    selectedConversation: Conversation?,
    messagesState: UiState<List<ChatMessage>>,
    onSelectConversation: (Conversation?) -> Unit,
    onSendMessage: (Conversation, String) -> Unit,
    onBrowseSchoolsClick: () -> Unit
) {
    if (selectedConversation != null) {
        BackHandler { onSelectConversation(null) }
        ConversationThreadView(
            currentUserId = currentUserId,
            conversation = selectedConversation,
            messagesState = messagesState,
            onBack = { onSelectConversation(null) },
            onSend = { text -> onSendMessage(selectedConversation, text) }
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("messages_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Direct conversations between parents and school administrators",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            when (conversationsState) {
                UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = conversationsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val allConversations = conversationsState.data
                    if (allConversations.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Outlined.Forum,
                            title = "No messages yet",
                            subtitle = "Open any school in Discover and tap 'Message Admin' to ask about admissions, fees, curriculum, or campus tours.",
                            actionLabel = "Explore Schools",
                            onAction = onBrowseSchoolsClick
                        )
                    } else {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search conversations...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search conversations"
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear search"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("messages_search_input")
                        )

                        Spacer(Modifier.height(12.dp))

                        val filteredConversations = remember(allConversations, searchQuery) {
                            val q = searchQuery.trim()
                            if (q.isEmpty()) {
                                allConversations
                            } else {
                                allConversations.filter { conv ->
                                    conv.schoolName.contains(q, ignoreCase = true) ||
                                        conv.adminName.contains(q, ignoreCase = true) ||
                                        conv.parentName.contains(q, ignoreCase = true) ||
                                        conv.lastMessage.contains(q, ignoreCase = true)
                                }
                            }
                        }

                        if (filteredConversations.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching conversations for \"$searchQuery\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(filteredConversations, key = { it.id }) { conv ->
                                    val isAdminView = conv.adminUid == currentUserId && conv.parentUid != currentUserId
                                    val counterpartName = if (isAdminView) {
                                        conv.parentName.ifBlank { "Parent" }
                                    } else {
                                        conv.adminName.ifBlank { conv.schoolName }
                                    }
                                    val roleLabel = if (isAdminView) "Parent Inquiry" else "School Admin"
                                    val updatedLabel = conv.updatedAt?.toDate()?.let { timeFormatter.format(it) } ?: "Online"

                                    Surface(
                                        onClick = { onSelectConversation(conv) },
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("conversation_card_${conv.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            GradientInitialsAvatar(
                                                initials = initialsFor(
                                                    conv.schoolName.ifBlank { counterpartName }
                                                ),
                                                size = 46.dp,
                                                showOnlineDot = true
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = conv.schoolName.ifBlank { counterpartName },
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(Modifier.size(8.dp))
                                                    Text(
                                                        text = updatedLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = counterpartName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = roleLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = conv.lastMessage.ifBlank { "Tap to start chatting" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
