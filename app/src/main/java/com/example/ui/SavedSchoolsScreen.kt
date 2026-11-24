package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Announcement
import com.example.data.School
import com.example.data.SchoolWithDistance

@Composable
fun SavedSchoolsScreen(
    favoriteSchools: List<SchoolWithDistance>,
    favoriteSchoolIds: Set<String>,
    selectedSchoolDetailState: UiState<School?> = UiState.Success(null),
    selectedSchoolAnnouncementsState: UiState<List<Announcement>> = UiState.Success(emptyList()),
    onSelectSchoolForDetail: (School?) -> Unit = {},
    onToggleFavoriteSchool: (School) -> Unit,
    onMessageAdmin: (School) -> Unit,
    onSubmitEnrollment: (School, String, String, String?, PaymentSubmissionDraft?) -> Unit,
    onClaimSchool: (School) -> Unit,
    onSubmitExtraDetails: (
        School, Long, Long, String, String, Boolean, String?, String?,
        List<String>, String?, List<String>, List<String>, String?, String?
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onBrowseDiscover: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var selectedItem by remember { mutableStateOf<SchoolWithDistance?>(null) }
    var enrichingSchool by remember { mutableStateOf<School?>(null) }

    LaunchedEffect(selectedItem?.school?.id) {
        onSelectSchoolForDetail(selectedItem?.school)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("saved_schools_screen"),
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
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Saved Shortlist (${favoriteSchools.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your bookmarked schools synced to your account across devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (favoriteSchools.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Bookmark,
                    title = "No saved schools yet",
                    subtitle = "Tap the heart icon on any verified school in Discover to shortlist and compare it here.",
                    actionLabel = "Discover Verified Schools",
                    onAction = onBrowseDiscover
                )
            }
        } else {
            items(favoriteSchools, key = { it.school.id }) { item ->
                SchoolResultCard(
                    item = item,
                    isFavorite = item.school.id in favoriteSchoolIds,
                    onToggleFavorite = onToggleFavoriteSchool,
                    onSubmitExtraDetails = { enrichingSchool = it },
                    onClaimSchool = onClaimSchool,
                    onClick = { selectedItem = item }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    selectedItem?.let { item ->
        val detailSchool = (selectedSchoolDetailState as? UiState.Success)?.data ?: item.school
        val isFav = (item.school.id in favoriteSchoolIds) || (detailSchool.id in favoriteSchoolIds)
        SchoolDetailBottomSheet(
            item = item,
            firestoreDetailState = selectedSchoolDetailState,
            announcementsState = selectedSchoolAnnouncementsState,
            isFavorite = isFav,
            onToggleFavorite = onToggleFavoriteSchool,
            onDismiss = { selectedItem = null },
            onMessageAdmin = { school ->
                selectedItem = null
                onMessageAdmin(school)
            },
            onSubmitEnrollment = onSubmitEnrollment,
            onClaimSchool = { school ->
                selectedItem = null
                onClaimSchool(school)
            },
            onSubmitExtraDetails = { school ->
                enrichingSchool = school
            }
        )
    }

    enrichingSchool?.let { targetSchool ->
        SubmitSchoolDetailsDialog(
            school = targetSchool,
            onDismiss = { enrichingSchool = null },
            onSubmit = { feeMin, feeMax, curr, period, open, window, boarding, photos, notes, courses, grades, site, phone ->
                onSubmitExtraDetails(
                    targetSchool, feeMin, feeMax, curr, period, open,
                    window, boarding, photos, notes, courses, grades, site, phone
                )
                enrichingSchool = null
            }
        )
    }
}
