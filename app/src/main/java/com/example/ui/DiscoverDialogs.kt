package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Announcement
import com.example.data.FaithFilter
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.SchoolSearchFilter
import com.example.data.SchoolWithDistance
import com.example.data.SortOption
import com.example.data.SpecialFilter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchoolFilterBottomSheet(
    filter: SchoolSearchFilter,
    onUpdateFilter: ((SchoolSearchFilter) -> SchoolSearchFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filter Schools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onReset, modifier = Modifier.testTag("reset_filters_button")) {
                    Text("Reset All")
                }
            }

            // Saved Favorites Only
            FilterChip(
                selected = filter.onlyFavorites,
                onClick = { onUpdateFilter { it.copy(onlyFavorites = !it.onlyFavorites) } },
                label = { Text("Saved Favorites Only") },
                modifier = Modifier.testTag("sheet_favorites_only_chip")
            )

            // Radius Slider
            Text("Search Perimeter: ${filter.radiusKm} km around ${filter.centerLabel}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = filter.radiusKm.toFloat(),
                onValueChange = { km -> onUpdateFilter { it.copy(radiusKm = km.roundToInt().coerceIn(2, 50)) } },
                valueRange = 2f..50f
            )

            // Institution Type (Primary & Secondary, University, Polytechnic, College of Education)
            Text("Institution Type", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filter.institutionType == null,
                    onClick = { onUpdateFilter { it.copy(institutionType = null) } },
                    label = { Text("All") }
                )
                SchoolConstants.INSTITUTION_TYPES.forEach { instType ->
                    FilterChip(
                        selected = filter.institutionType == instType,
                        onClick = {
                            onUpdateFilter { it.copy(institutionType = if (it.institutionType == instType) null else instType) }
                        },
                        label = { Text(instType) }
                    )
                }
            }

            // Programmes Run
            Text("Programmes Run", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.ALL_PROGRAMMES.forEach { prog ->
                    val active = prog in filter.selectedProgrammes
                    FilterChip(
                        selected = active,
                        onClick = {
                            onUpdateFilter { cur ->
                                val next = if (active) cur.selectedProgrammes - prog else cur.selectedProgrammes + prog
                                cur.copy(selectedProgrammes = next)
                            }
                        },
                        label = { Text(prog) }
                    )
                }
            }

            // Courses Offered
            Text("Courses Offered", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.COURSES_OFFERED_OPTIONS.forEach { course ->
                    val active = course in filter.selectedCourses
                    FilterChip(
                        selected = active,
                        onClick = {
                            onUpdateFilter { cur ->
                                val next = if (active) cur.selectedCourses - course else cur.selectedCourses + course
                                cur.copy(selectedCourses = next)
                            }
                        },
                        label = { Text(course) }
                    )
                }
            }

            // Curriculum (Nigerian, British, Nigerian & British, IB, American, etc.)
            Text("Curriculum", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filter.curriculum == null,
                    onClick = { onUpdateFilter { it.copy(curriculum = null) } },
                    label = { Text("All") }
                )
                SchoolConstants.CURRICULA.forEach { curr ->
                    FilterChip(
                        selected = filter.curriculum == curr,
                        onClick = {
                            onUpdateFilter { it.copy(curriculum = if (it.curriculum == curr) null else curr) }
                        },
                        label = { Text(curr) }
                    )
                }
            }

            // Faith Affiliation
            Text("Faith Affiliation", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FaithFilter.entries.forEach { faith ->
                    FilterChip(
                        selected = filter.faithFilter == faith,
                        onClick = { onUpdateFilter { it.copy(faithFilter = faith) } },
                        label = { Text(faith.label) }
                    )
                }
            }

            // Special Needs School
            Text("Special Education", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecialFilter.entries.forEach { sp ->
                    FilterChip(
                        selected = filter.specialSchoolFilter == sp,
                        onClick = { onUpdateFilter { it.copy(specialSchoolFilter = sp) } },
                        label = { Text(sp.label) }
                    )
                }
            }

            // Student Mix
            Text("Student Mix", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter.studentMix == null,
                    onClick = { onUpdateFilter { it.copy(studentMix = null) } },
                    label = { Text("Any") }
                )
                SchoolConstants.STUDENT_MIX_OPTIONS.forEach { mix ->
                    FilterChip(
                        selected = filter.studentMix == mix,
                        onClick = {
                            onUpdateFilter { it.copy(studentMix = if (it.studentMix == mix) null else mix) }
                        },
                        label = { Text(mix) }
                    )
                }
            }

            // Max Fee Range Filter
            Text("School Fee Ceiling", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val budgets = listOf(
                    null to "Any Fee",
                    0L to "Public / Free",
                    500_000L to "Up to 500k",
                    1_000_000L to "Up to 1M",
                    2_000_000L to "Up to 2M"
                )
                budgets.forEach { (limit, label) ->
                    FilterChip(
                        selected = filter.maxFeeBudget == limit,
                        onClick = { onUpdateFilter { it.copy(maxFeeBudget = limit) } },
                        label = { Text(label) }
                    )
                }
            }

            // Minimum Ranking Score
            Text("Minimum Ranking Score", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.0 to "All", 80.0 to "80+ Score", 90.0 to "90+ Score", 95.0 to "Top 95+").forEach { (score, label) ->
                    FilterChip(
                        selected = filter.minRankingScore == score,
                        onClick = { onUpdateFilter { it.copy(minRankingScore = score) } },
                        label = { Text(label) }
                    )
                }
            }

            // Sports Focused On
            Text("Sports Focus", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.SPORTS_FOCUS_OPTIONS.forEach { sport ->
                    val active = sport in filter.selectedSports
                    FilterChip(
                        selected = active,
                        onClick = {
                            onUpdateFilter { cur ->
                                val next = if (active) cur.selectedSports - sport else cur.selectedSports + sport
                                cur.copy(selectedSports = next)
                            }
                        },
                        label = { Text(sport) }
                    )
                }
            }

            // Extracurricular Activities
            Text("Extracurricular Activities", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.EXTRACURRICULAR_ACTIVITIES.forEach { act ->
                    val active = act in filter.selectedActivities
                    FilterChip(
                        selected = active,
                        onClick = {
                            onUpdateFilter { cur ->
                                val next = if (active) cur.selectedActivities - act else cur.selectedActivities + act
                                cur.copy(selectedActivities = next)
                            }
                        },
                        label = { Text(act) }
                    )
                }
            }

            // Sort By
            Text("Sort Results By", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SortOption.entries.forEach { opt ->
                    FilterChip(
                        selected = filter.sortBy == opt,
                        onClick = { onUpdateFilter { it.copy(sortBy = opt) } },
                        label = { Text(opt.label) }
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(bottom = 8.dp)
            ) {
                Text("Apply Filters")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolDetailBottomSheet(
    item: SchoolWithDistance,
    firestoreDetailState: UiState<School?> = UiState.Success(null),
    announcementsState: UiState<List<Announcement>> = UiState.Success(emptyList()),
    isFavorite: Boolean = false,
    onToggleFavorite: (School) -> Unit = {},
    onDismiss: () -> Unit,
    onMessageAdmin: (School) -> Unit,
    onSubmitEnrollment: (School, String, String, String?, PaymentSubmissionDraft?) -> Unit,
    onClaimSchool: (School) -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SchoolDetailView(
            fallbackSchoolItem = item,
            firestoreDetailState = firestoreDetailState,
            announcementsState = announcementsState,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onMessageAdmin = onMessageAdmin,
            onSubmitEnrollment = onSubmitEnrollment,
            onClaimSchool = onClaimSchool
        )
    }
}
