package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Streetview
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Announcement
import com.example.data.GoogleMapsPlatformService
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.SchoolWithDistance
import com.example.data.formatCurrencyAmount
import com.example.ui.theme.FavoriteHeartColor
import com.example.ui.theme.StarGold
import com.example.ui.theme.StatusPresentGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolDetailView(
    fallbackSchoolItem: SchoolWithDistance,
    firestoreDetailState: UiState<School?>,
    announcementsState: UiState<List<Announcement>> = UiState.Success(emptyList()),
    isFavorite: Boolean = false,
    isOwnedByCurrentUser: Boolean = false,
    pageVisitorCount: Int = 0,
    onToggleFavorite: (School) -> Unit = {},
    onMessageAdmin: (School) -> Unit,
    onSubmitEnrollment: (School, String, String, String?, PaymentSubmissionDraft?) -> Unit,
    onClaimSchool: (School) -> Unit = {},
    onSubmitExtraDetails: (School) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val firestoreSchool = (firestoreDetailState as? UiState.Success)?.data
    val school = firestoreSchool ?: fallbackSchoolItem.school
    val isRegistered = school.hasPlatformAdmin()
    val isSyncedWithFirestore = firestoreSchool != null
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showEnrollDialog by remember { mutableStateOf(false) }
    var courseFilterQuery by remember(school.id) { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("school_detail_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BannerImage(
            imageUrl = school.bannerImageUrl,
            fallbackResId = R.drawable.img_campus_hero,
            contentDescription = "${school.name} banner",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .testTag("school_detail_banner")
        )

        if (isOwnedByCurrentUser) {
            PageVisitorsBadge(
                visitorCount = pageVisitorCount,
                testTag = "school_detail_visits_badge"
            )
        }

        // Real-time Firestore Sync Indicator
        when (firestoreDetailState) {
            UiState.Loading -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Fetching live school record & courses from Firestore…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            is UiState.Error -> {
                ErrorBanner("Live Firestore sync unavailable: ${firestoreDetailState.message}")
            }
            is UiState.Success -> {
                if (isSyncedWithFirestore) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = StatusPresentGreen.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = StatusPresentGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Live Firestore Profile Synced",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusPresentGreen
                            )
                        }
                    }
                }
            }
        }

        // School Title, Location & Rank Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = school.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "${school.address} • ${String.format("%.1f", fallbackSchoolItem.distanceKm)} km away",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when {
                        isRegistered -> MaterialTheme.colorScheme.primaryContainer
                        school.isVerifiedOnline() -> StatusPresentGreen.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Text(
                        text = when {
                            isRegistered -> "#${school.districtRank} District"
                            school.isVerifiedOnline() -> "Verified Online"
                            else -> "Online Listing"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isRegistered -> MaterialTheme.colorScheme.onPrimaryContainer
                            school.isVerifiedOnline() -> StatusPresentGreen
                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                IconButton(
                    onClick = { onToggleFavorite(school) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("detail_favorite_button")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove ${school.name} from favorites" else "Save ${school.name} to favorites",
                        tint = if (isFavorite) FavoriteHeartColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Google Maps Platform Navigation & Street View Intents
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    GoogleMapsPlatformService.openGoogleMapsDirections(
                        context = context,
                        lat = school.latitude,
                        lng = school.longitude,
                        label = school.name
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_directions_button")
            ) {
                Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Directions")
            }
            OutlinedButton(
                onClick = {
                    GoogleMapsPlatformService.openGoogleStreetView(
                        context = context,
                        lat = school.latitude,
                        lng = school.longitude
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_streetview_button")
            ) {
                Icon(Icons.Filled.Streetview, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Street View")
            }
        }

        // Verification, Online Citations & Admissions Contact Card
        val detailCitations = remember(school.sourceCitations, school.googleMapsUrl, school.websiteUrl) {
            school.parsedCitations()
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (school.isVerifiedOnline()) {
                StatusPresentGreen.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("detail_verification_card")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (school.isVerifiedOnline()) Icons.Filled.Verified else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (school.isVerifiedOnline()) StatusPresentGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = school.verificationBadgeLabel(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Source: ${school.primarySourceSummary()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!school.googleMapsPlaceRef.isNullOrBlank()) {
                    Text(
                        text = "Google Maps Place Ref: ${school.googleMapsPlaceRef}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (school.adminName.isNotBlank()) {
                    Text(
                        text = "Managed by: ${school.adminName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!school.boardingOption.isNullOrBlank() && school.boardingOption != "Not Specified") {
                    Text(
                        text = "Boarding / Day: ${school.boardingOption}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!school.admissionWindow.isNullOrBlank()) {
                    Text(
                        text = "Admission Window: ${school.admissionWindow}",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusPresentGreen
                    )
                }
                if (!school.contactPhone.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = school.contactPhone,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (!school.websiteUrl.isNullOrBlank()) {
                    TextButton(
                        onClick = { runCatching { uriHandler.openUri(school.websiteUrl) } },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(school.websiteUrl)
                    }
                }
                if (detailCitations.isNotEmpty()) {
                    Text(
                        text = "Verified Online Sources & Citations (${detailCitations.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        detailCitations.forEachIndexed { idx, link ->
                            AssistChip(
                                onClick = { runCatching { uriHandler.openUri(link.uri) } },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (link.sourceType == "Google Maps") Icons.Filled.Map else Icons.Filled.Public,
                                        contentDescription = "Open ${link.sourceType} citation",
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text("${link.sourceType}: ${link.title}") },
                                modifier = Modifier.testTag("detail_citation_chip_$idx")
                            )
                        }
                    }
                }
                if (!school.extraNotes.isNullOrBlank()) {
                    Text(
                        text = "Extra Notes: ${school.extraNotes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!school.lastEnrichedByName.isNullOrBlank()) {
                    Text(
                        text = "Last enriched by: ${school.lastEnrichedByName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusPresentGreen
                    )
                }
                if (school.photoUrls.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        school.photoUrls.forEachIndexed { idx, url ->
                            AssistChip(
                                onClick = { runCatching { uriHandler.openUri(url) } },
                                label = { Text("Photo ${idx + 1}") }
                            )
                        }
                    }
                }
            }
        }

        // Fee Range, Rating & Seat Capacity Card
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = StatusPresentGreen, modifier = Modifier.size(18.dp))
                        Text("Tuition & Fees", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = school.formattedFeeRange(),
                        style = MaterialTheme.typography.titleSmall,
                        color = StatusPresentGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(18.dp))
                        Text("Rating ${school.rating}/5.0 • Score ${school.rankingScore.toInt()}/100", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = if (school.admissionsOpen) "Admissions Open" else "Closed",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (school.admissionsOpen) StatusPresentGreen else MaterialTheme.colorScheme.error
                    )
                }
                val fillRatio = if (school.capacity > 0L) {
                    (school.enrolledCount.toFloat() / school.capacity.toFloat()).coerceIn(0f, 1f)
                } else 0f
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Campus Enrollment Capacity",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${school.enrolledCount}/${school.capacity} seats",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { fillRatio },
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }
            }
        }

        if (school.description.isNotBlank()) {
            Text(school.description, style = MaterialTheme.typography.bodyMedium)
        }

        // Program Details Section
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("detail_program_details_section")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Program Details & Academic Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AttributeBadge(
                        text = school.institutionType,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    AttributeBadge(
                        text = "${school.curriculum} Curriculum",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    AttributeBadge(
                        text = if (school.faithBased) "Faith-Based: ${school.faithTradition}" else "Secular",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    AttributeBadge(
                        text = if (school.specialSchool) "Special Education: ${school.specialNeedsSupport}" else school.specialNeedsSupport,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    AttributeBadge(
                        text = school.studentMix,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (school.gradeLevels.isNotEmpty()) {
                    Text(
                        text = "Programmes & Academic Levels Run (${school.gradeLevels.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        school.gradeLevels.forEach { prog ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prog,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = programDescriptionFor(prog, school.curriculum),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    AttributeBadge(
                                        text = if (prog in SchoolConstants.TERTIARY_PROGRAMMES) "Tertiary" else "K-12",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Program levels have not been published yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Course Offerings Section
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("detail_course_offerings_section")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Course Offerings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "${school.coursesOffered.size} listed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (school.coursesOffered.size > 4) {
                    OutlinedTextField(
                        value = courseFilterQuery,
                        onValueChange = { courseFilterQuery = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        placeholder = { Text("Filter courses offered…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val filteredCourses = remember(school.coursesOffered, courseFilterQuery) {
                    if (courseFilterQuery.isBlank()) school.coursesOffered
                    else school.coursesOffered.filter { it.contains(courseFilterQuery.trim(), ignoreCase = true) }
                }

                if (filteredCourses.isEmpty()) {
                    Text(
                        text = if (school.coursesOffered.isEmpty()) {
                            "No specific courses or departments listed yet for this ${school.institutionType.lowercase()}."
                        } else {
                            "No courses match \"$courseFilterQuery\"."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredCourses.forEach { course ->
                            AttributeBadge(
                                text = course,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Sports Focus & Extracurricular Activities
        if (school.sportsFocus.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.SportsSoccer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text("Sports Focus", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                school.sportsFocus.forEach { sport ->
                    AttributeBadge(
                        text = sport,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (school.activities.isNotEmpty()) {
            Text("Extracurricular Activities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                school.activities.forEach { act ->
                    AttributeBadge(
                        text = act,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Live School Announcements from Firestore
        val schoolAnnouncements = (announcementsState as? UiState.Success)?.data.orEmpty()
        if (schoolAnnouncements.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_announcements_section")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("School Announcements", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    schoolAnnouncements.take(3).forEach { ann ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(ann.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(ann.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(ann.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!ann.eventDate.isNullOrBlank()) {
                                    Text("Date: ${ann.eventDate}", style = MaterialTheme.typography.labelSmall, color = StatusPresentGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Immediate Enrollment Inquiry + Optional Enrichment / Claim Actions (No Registration Gate)
        if (!isRegistered) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Verified from online sources and ready to bookmark, compare, or submit an enrollment inquiry now. Schools or parents can also optionally enrich tuition brackets, admission windows, boarding options, or photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Button(
                onClick = { showEnrollDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("detail_apply_enroll_button")
            ) {
                Text("Submit Enrollment Inquiry")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onSubmitExtraDetails(school) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("detail_submit_details_button")
                ) {
                    Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Submit More Details")
                }
                OutlinedButton(
                    onClick = { onClaimSchool(school) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("detail_claim_school_button")
                ) {
                    Text("Claim Profile")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onMessageAdmin(school) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("detail_message_admin_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Message Admin")
                }
                Button(
                    onClick = { showEnrollDialog = true },
                    enabled = school.admissionsOpen,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("detail_apply_enroll_button")
                ) {
                    Text(if (school.admissionsOpen) "Apply / Enroll" else "Admissions Closed")
                }
            }
            OutlinedButton(
                onClick = { onSubmitExtraDetails(school) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .height(48.dp)
                    .testTag("detail_submit_details_button")
            ) {
                Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Submit More Details")
            }
        }
    }

    if (showEnrollDialog) {
        EnrollmentApplyDialog(
            school = school,
            onDismiss = { showEnrollDialog = false },
            onSubmit = { studentName, grade, notes, paymentDraft ->
                onSubmitEnrollment(school, studentName, grade, notes, paymentDraft)
                showEnrollDialog = false
            }
        )
    }
}

private fun programDescriptionFor(programme: String, curriculum: String): String = when (programme) {
    "Creche / Pre-K" -> "Early years & foundation Montessori / play-based learning ($curriculum)"
    "Primary" -> "Core literacy, numeracy & STEM foundation ($curriculum)"
    "Junior Secondary" -> "BECE / Checkpoint middle school pathway ($curriculum)"
    "Senior Secondary" -> "WAEC, NECO, IGCSE & college prep track ($curriculum)"
    "Sixth Form" -> "Cambridge A-Levels & International Baccalaureate prep ($curriculum)"
    "NCE Programme" -> "3-year Nigeria Certificate in Education teacher training"
    "ND / HND Programme" -> "NBTE-accredited National & Higher National Diploma tracks"
    "Undergraduate" -> "Bachelor's degree faculties & honours programmes ($curriculum)"
    "Postgraduate" -> "Master's, PGD & doctoral research degrees"
    else -> "$curriculum academic programme"
}

@Composable
private fun EnrollmentApplyDialog(
    school: School,
    onDismiss: () -> Unit,
    onSubmit: (studentName: String, gradeApplying: String, notes: String?, paymentDraft: PaymentSubmissionDraft?) -> Unit
) {
    var studentName by remember { mutableStateOf("") }
    var gradeApplying by remember { mutableStateOf(school.gradeLevels.firstOrNull() ?: "Primary") }
    var notes by remember { mutableStateOf("") }
    var showCheckout by remember { mutableStateOf(false) }

    val appFeeAmount = remember(school) {
        when {
            school.feeMin <= 0L -> SchoolConstants.DEFAULT_APPLICATION_FEE_NGN
            else -> (school.feeMin / 20L).coerceIn(5_000L, 50_000L)
        }
    }

    val isRegistered = school.hasPlatformAdmin()
    if (showCheckout && isRegistered) {
        PaymentCheckoutDialog(
            title = "Application Fee Checkout",
            subtitle = "Enrollment Application • $studentName ($gradeApplying)",
            recipientName = school.name,
            amount = appFeeAmount,
            currency = school.feeCurrency,
            allowPayLater = true,
            onDismiss = { showCheckout = false },
            onConfirmPayment = { draft ->
                showCheckout = false
                onSubmit(studentName, gradeApplying, notes.ifBlank { null }, draft)
            },
            onPayLater = {
                showCheckout = false
                onSubmit(studentName, gradeApplying, notes.ifBlank { null }, null)
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (isRegistered) "Apply to ${school.name}" else "Enrollment Inquiry • ${school.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it },
                        label = { Text("Student Full Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("enroll_student_name_input")
                    )
                    OutlinedTextField(
                        value = gradeApplying,
                        onValueChange = { gradeApplying = it },
                        label = { Text("Grade / Programme Applying For *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("enroll_grade_input")
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (courses, activities, special support, sports)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (isRegistered) {
                            "Optional application processing fee: ${formatCurrencyAmount(appFeeAmount, school.feeCurrency)}"
                        } else {
                            "Your enrollment inquiry is saved to your Skolquik portal immediately. When the school's admin joins Skolquik, they can respond directly."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isRegistered) showCheckout = true
                        else onSubmit(studentName, gradeApplying, notes.ifBlank { null }, null)
                    },
                    enabled = studentName.isNotBlank() && gradeApplying.isNotBlank(),
                    modifier = Modifier.testTag("enroll_submit_button")
                ) {
                    Text(if (isRegistered) "Continue to Application Fee" else "Submit Inquiry")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isRegistered) {
                        TextButton(
                            onClick = { onSubmit(studentName, gradeApplying, notes.ifBlank { null }, null) },
                            enabled = studentName.isNotBlank() && gradeApplying.isNotBlank()
                        ) {
                            Text("Submit Free")
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        )
    }
}
