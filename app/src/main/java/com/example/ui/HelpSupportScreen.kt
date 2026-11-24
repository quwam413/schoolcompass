package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SchoolConstants

private data class SupportFaqItem(
    val question: String,
    val answer: String
)

private val SKOLQUIK_FAQS = listOf(
    SupportFaqItem(
        question = "How do Verified Online school results work?",
        answer = "Skolquik queries Google Search & Google Maps grounding in real time so already-verified schools appear immediately with official websites, addresses, and Maps links—no waiting for a school to register first."
    ),
    SupportFaqItem(
        question = "How do I reload nearby schools on the home screen?",
        answer = "Pull down (drawdown) anywhere at the top of the Discover home feed to refresh nearby OpenStreetMap, Google Places, and Google Search & Maps grounded listings."
    ),
    SupportFaqItem(
        question = "Can schools or parents add extra details to an online-verified school?",
        answer = "Yes. Open any school card or detail sheet and tap 'Submit More Details' to contribute tuition brackets, admission windows, boarding options, or curriculum courses without blocking anyone from browsing."
    ),
    SupportFaqItem(
        question = "How does the Sunrise & Sunset theme work?",
        answer = "Open Settings and privacy from the sidebar and select 'Sunrise & Sunset'. Skolquik calculates local solar sunrise and sunset times for your search area, keeping a crisp white theme by day and switching dark automatically after sunset."
    ),
    SupportFaqItem(
        question = "How can a school claim an existing online profile?",
        answer = "Tap 'Claim School' inside a verified school's sheet to link your administrator account in the School Hub, broadcast announcements, and manage student admissions directly."
    )
)

@Composable
fun HelpSupportScreen(
    onOpenAiAdvisor: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var expandedIndex by remember { mutableStateOf<Int?>(0) }

    val visibleFaqs = remember(searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) SKOLQUIK_FAQS
        else SKOLQUIK_FAQS.filter {
            it.question.contains(q, ignoreCase = true) || it.answer.contains(q, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("help_support_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Help & Support",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Search answers on school verification, enrollments, progressive tutors, or reach Skolquik support.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenAiAdvisor,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("help_open_ai_button")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Ask Skolquik AI", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:${SchoolConstants.PLATFORM_ADMIN_EMAIL}")
                                    putExtra(Intent.EXTRA_SUBJECT, "Skolquik Support Request")
                                }
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("help_email_support_button")
                        ) {
                            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Email Support", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search help articles…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("help_search_input")
            )
        }

        itemsIndexed(visibleFaqs) { index, faq ->
            val expanded = expandedIndex == index
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { expandedIndex = if (expanded) null else index }
                    .testTag("help_faq_item_$index")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = faq.question,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (expanded) {
                        Text(
                            text = faq.answer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Customize Theme & Privacy Settings")
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
