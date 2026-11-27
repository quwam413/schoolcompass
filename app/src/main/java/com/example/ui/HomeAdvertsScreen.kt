package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CastForEducation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Advert
import com.example.data.AdvertWithDistance
import com.example.data.GoogleNewsArticle
import com.example.data.GoogleNewsTopic
import com.example.data.PixelWeatherSnapshot
import com.example.data.ProgressiveTutor
import com.example.data.School
import com.example.data.SchoolWithDistance
import com.example.data.TutorWithDistance
import com.example.ui.theme.SkolquikAmberCheck
import com.example.ui.theme.StarGold
import com.example.ui.theme.StatusPresentGreen

enum class HomeAdvertKind(val badgeLabel: String) {
    SCHOOL("School"),
    TUTOR("Progressive Tutor")
}

data class HomeAdvertItem(
    val id: String,
    val kind: HomeAdvertKind,
    val isSponsored: Boolean,
    val title: String,
    val advertiserName: String,
    val description: String,
    val locationLabel: String,
    val metaBadge: String,
    val ctaLabel: String,
    val bannerImageUrl: String? = null,
    val advert: Advert? = null,
    val schoolItem: SchoolWithDistance? = null,
    val tutorItem: TutorWithDistance? = null
)

private val HOME_FILTER_CATEGORIES = listOf("All", "Schools", "Progressive Tutors", "Sponsored")

internal fun buildHomeAdvertFeed(
    activeAdverts: List<AdvertWithDistance>,
    schools: List<SchoolWithDistance>,
    tutors: List<TutorWithDistance>,
    selectedCategory: String
): List<HomeAdvertItem> {
    val paidSchoolAds = mutableListOf<HomeAdvertItem>()
    val paidTutorAds = mutableListOf<HomeAdvertItem>()

    activeAdverts.forEach { awd ->
        val ad = awd.advert
        val isTutorAd = ad.category.equals("Progressive Tutors", ignoreCase = true) ||
            ad.advertiserName.contains("tutor", ignoreCase = true)
        val entry = HomeAdvertItem(
            id = "ad_${ad.id}",
            kind = if (isTutorAd) HomeAdvertKind.TUTOR else HomeAdvertKind.SCHOOL,
            isSponsored = true,
            title = ad.title,
            advertiserName = ad.advertiserName,
            description = ad.body,
            locationLabel = "${ad.targetLocation} • ${String.format("%.1f km", awd.distanceKm)}",
            metaBadge = ad.planTier,
            ctaLabel = ad.ctaLabel.ifBlank { if (isTutorAd) "Book Tutor" else "Inquire Now" },
            bannerImageUrl = ad.bannerImageUrl,
            advert = ad
        )
        if (isTutorAd) paidTutorAds.add(entry) else paidSchoolAds.add(entry)
    }

    val schoolSpotlights = schools.take(10).map { swd ->
        val s = swd.school
        HomeAdvertItem(
            id = "school_${s.id}",
            kind = HomeAdvertKind.SCHOOL,
            isSponsored = false,
            title = "${s.name} • ${if (s.admissionsOpen) "Admissions Open" else s.curriculum}",
            advertiserName = s.curriculum,
            description = s.description.ifBlank {
                "${s.institutionType} in ${s.address}. ${s.formattedFeeRange()}."
            },
            locationLabel = "${s.address.ifBlank { "Nearby" }} • ${String.format("%.1f km", swd.distanceKm)}",
            metaBadge = s.formattedFeeRange(),
            ctaLabel = if (s.admissionsOpen) "Apply / Explore" else "View School",
            bannerImageUrl = s.bannerImageUrl,
            schoolItem = swd
        )
    }

    val tutorSpotlights = tutors.take(10).map { twd ->
        val t = twd.tutor
        val subjectsPreview = (t.cambridgeSubjects + t.nigerianSubjects + t.allSubjects)
            .distinct()
            .take(2)
            .joinToString(", ")
        HomeAdvertItem(
            id = "tutor_${t.id}",
            kind = HomeAdvertKind.TUTOR,
            isSponsored = false,
            title = "${t.fullName} — ${t.headline.ifBlank { t.curriculumTracks.joinToString(" & ").ifBlank { "Progressive Tutor" } }}",
            advertiserName = "Progressive Tutor • ${t.meetingPlatform}",
            description = t.bio.ifBlank {
                subjectsPreview.ifBlank { "${t.yearsExperience} yrs experience • Cambridge & Nigerian curriculum coaching." }
            },
            locationLabel = "${t.locationLabel} • ${String.format("%.1f km", twd.distanceKm)}",
            metaBadge = t.formattedHourlyRate(),
            ctaLabel = "Book Session",
            bannerImageUrl = t.bannerImageUrl,
            tutorItem = twd
        )
    }

    val combinedSchools = paidSchoolAds + schoolSpotlights
    val combinedTutors = paidTutorAds + tutorSpotlights

    val rawList = when (selectedCategory) {
        "Schools" -> combinedSchools
        "Progressive Tutors" -> combinedTutors
        "Sponsored" -> paidSchoolAds + paidTutorAds
        else -> buildList {
            val maxSize = maxOf(combinedSchools.size, combinedTutors.size)
            for (i in 0 until maxSize) {
                if (i < combinedSchools.size) add(combinedSchools[i])
                if (i < combinedTutors.size) add(combinedTutors[i])
            }
        }
    }
    return rawList.distinctBy { it.id }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAdvertsScreen(
    activeAdverts: List<AdvertWithDistance>,
    schools: List<SchoolWithDistance>,
    tutors: List<TutorWithDistance>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isBusy: Boolean,
    defaultUserName: String,
    defaultUserEmail: String,
    onRefreshHome: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenTutors: () -> Unit,
    onOpenPaidAdverts: () -> Unit,
    onSchoolVisited: (School) -> Unit = {},
    onTutorVisited: (ProgressiveTutor) -> Unit = {},
    onSendAdvertInquiry: (Advert, String, String, String?, String, () -> Unit) -> Unit,
    weatherState: UiState<PixelWeatherSnapshot> = UiState.Loading,
    locationLabel: String = "Lekki Phase 1, Lagos",
    googleNewsState: UiState<List<GoogleNewsArticle>> = UiState.Loading,
    selectedNewsTopic: GoogleNewsTopic? = null,
    onSelectNewsTopic: (GoogleNewsTopic?) -> Unit = {},
    onRefreshNews: () -> Unit = {},
    onRefreshWeather: () -> Unit = {},
    onUseGpsWeather: (Double, Double, String) -> Unit = { _, _, _ -> }
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var inspectedItem by remember { mutableStateOf<HomeAdvertItem?>(null) }
    var inquiryAdvert by remember { mutableStateOf<Advert?>(null) }

    fun openHomeAdvertItem(item: HomeAdvertItem) {
        item.schoolItem?.school?.let(onSchoolVisited)
        item.tutorItem?.tutor?.let(onTutorVisited)
        inspectedItem = item
    }

    val allItems = remember(activeAdverts, schools, tutors, selectedCategory) {
        buildHomeAdvertFeed(
            activeAdverts = activeAdverts,
            schools = schools,
            tutors = tutors,
            selectedCategory = selectedCategory
        )
    }

    val spotlightItems = remember(allItems) { allItems.take(4) }
    val feedItems = remember(allItems) { allItems.drop(4) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefreshHome,
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_adverts_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_feed_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // YouTube-style top filter pills row
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_filter_pills")
                ) {
                    items(HOME_FILTER_CATEGORIES) { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = if (isSelected) null else BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedCategory = category }
                                .testTag("home_pill_${category.lowercase().replace(" ", "_")}")
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.background
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenPaidAdverts() }
                                .testTag("home_post_advert_pill")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Post Ad",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Google Pixel Weather Widget for user's location
            item(key = "home_pixel_weather") {
                PixelWeatherWidgetCard(
                    weatherState = weatherState,
                    locationLabel = locationLabel,
                    onRefreshWeather = onRefreshWeather,
                    onUseGpsLocation = onUseGpsWeather
                )
            }

            if (isLoading && allItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (allItems.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        EmptyStateCard(
                            icon = Icons.Filled.Campaign,
                            title = if (selectedCategory == "Sponsored") {
                                "No active paid adverts yet"
                            } else {
                                "Syncing School & Tutor Adverts"
                            },
                            subtitle = "Schools and progressive tutors can promote open admissions and coaching sessions right here.",
                            actionLabel = "Launch Advert",
                            onAction = onOpenPaidAdverts
                        )
                    }
                }
            } else {
                // Shorts-style 2-column Spotlight Adverts grid
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .testTag("home_spotlight_grid"),
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
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Campaign,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(5.dp)
                                            .size(16.dp)
                                    )
                                }
                                Text(
                                    text = "Featured Adverts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            TextButton(onClick = onOpenPaidAdverts) {
                                Text("Manage Ads")
                            }
                        }

                        spotlightItems.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { item ->
                                    SpotlightPortraitCard(
                                        item = item,
                                        onClick = { openHomeAdvertItem(item) },
                                        onOpenDiscover = onOpenDiscover,
                                        onOpenTutors = onOpenTutors,
                                        onOpenPaidAdverts = onOpenPaidAdverts,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Google News (Education, Science & Technology, Innovation)
            item(key = "home_google_news") {
                GoogleNewsHomepageSection(
                    newsState = googleNewsState,
                    selectedTopic = selectedNewsTopic,
                    onSelectTopic = onSelectNewsTopic,
                    onRefreshNews = onRefreshNews
                )
            }

            if (allItems.isNotEmpty() && !isLoading) {
                if (feedItems.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // YouTube-style Widescreen Feed Cards for Schools & Progressive Tutors
                items(feedItems, key = { "feed_${it.id}" }) { item ->
                    YouTubeAdvertFeedCard(
                        item = item,
                        onClick = { openHomeAdvertItem(item) },
                        onOpenDiscover = onOpenDiscover,
                        onOpenTutors = onOpenTutors,
                        onOpenPaidAdverts = onOpenPaidAdverts
                    )
                }
            }
        }
    }

    inspectedItem?.let { item ->
        HomeAdvertDetailDialog(
            item = item,
            onDismiss = { inspectedItem = null },
            onPrimaryAction = {
                inspectedItem = null
                when {
                    item.advert != null -> inquiryAdvert = item.advert
                    item.kind == HomeAdvertKind.SCHOOL -> onOpenDiscover()
                    else -> onOpenTutors()
                }
            },
            onExploreMore = {
                inspectedItem = null
                if (item.kind == HomeAdvertKind.SCHOOL) onOpenDiscover() else onOpenTutors()
            }
        )
    }

    inquiryAdvert?.let { ad ->
        HomeQuickInquiryDialog(
            advert = ad,
            defaultName = defaultUserName,
            defaultEmail = defaultUserEmail,
            isBusy = isBusy,
            onDismiss = { inquiryAdvert = null },
            onSubmit = { name, email, phone, message ->
                onSendAdvertInquiry(ad, name, email, phone, message) {
                    inquiryAdvert = null
                }
            }
        )
    }
}

@Composable
private fun SpotlightPortraitCard(
    item: HomeAdvertItem,
    onClick: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenTutors: () -> Unit,
    onOpenPaidAdverts: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isSchool = item.kind == HomeAdvertKind.SCHOOL
    val backdropRes = if (isSchool) R.drawable.img_campus_hero else R.drawable.img_portal_academic
    val tintBrush = if (isSchool) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF0A244D).copy(alpha = 0.35f),
                Color(0xFF071326).copy(alpha = 0.88f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFF28194A).copy(alpha = 0.35f),
                Color(0xFF140B29).copy(alpha = 0.88f)
            )
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .height(228.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("home_spotlight_card_${item.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BannerImage(
                imageUrl = item.bannerImageUrl,
                fallbackResId = backdropRes,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tintBrush)
            )

            // Top badge & 3-dot overflow menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(start = 8.dp, top = 6.dp, end = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.isSponsored) StarGold else Color.Black.copy(alpha = 0.56f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                item.isSponsored -> Icons.Filled.Star
                                isSchool -> Icons.Filled.School
                                else -> Icons.Filled.CastForEducation
                            },
                            contentDescription = null,
                            tint = if (item.isSponsored) Color.Black else Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (isSchool) "SCHOOL" else "TUTOR",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isSponsored) Color.Black else Color.White
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Advert options",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("View Details") },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isSchool) "Browse Schools" else "Browse Tutors") },
                            onClick = {
                                menuExpanded = false
                                if (isSchool) onOpenDiscover() else onOpenTutors()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Post Advert") },
                            onClick = {
                                menuExpanded = false
                                onOpenPaidAdverts()
                            }
                        )
                    }
                }
            }

            // Bottom overlay title + fee/rate badge
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.metaBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun YouTubeAdvertFeedCard(
    item: HomeAdvertItem,
    onClick: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenTutors: () -> Unit,
    onOpenPaidAdverts: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isSchool = item.kind == HomeAdvertKind.SCHOOL
    val backdropRes = if (isSchool) R.drawable.img_campus_hero else R.drawable.img_portal_academic

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag("home_feed_card_${item.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 16:9 YouTube Thumbnail Media Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
        ) {
            BannerImage(
                imageUrl = item.bannerImageUrl,
                fallbackResId = backdropRes,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            // Top-left category pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (item.isSponsored) SkolquikAmberCheck else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = if (item.isSponsored) {
                        "SPONSORED • ${item.kind.badgeLabel.uppercase()}"
                    } else {
                        item.kind.badgeLabel.uppercase()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }

            // Center-bottom headline preview inside banner
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = item.metaBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.94f)
                    ) {
                        Text(
                            text = item.ctaLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // YouTube-style metadata row below thumbnail
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSchool) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSchool) Icons.Filled.School else Icons.Filled.CastForEducation,
                        contentDescription = null,
                        tint = if (isSchool) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.advertiserName} • ${item.locationLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(item.ctaLabel) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isSchool) "Open in Discover" else "Open Progressive Tutors") },
                        onClick = {
                            menuExpanded = false
                            if (isSchool) onOpenDiscover() else onOpenTutors()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Promote Your Own Ad") },
                        onClick = {
                            menuExpanded = false
                            onOpenPaidAdverts()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeAdvertDetailDialog(
    item: HomeAdvertItem,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
    onExploreMore: () -> Unit
) {
    val isSchool = item.kind == HomeAdvertKind.SCHOOL
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isSchool) Icons.Filled.School else Icons.Filled.CastForEducation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.kind.badgeLabel,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!item.bannerImageUrl.isNullOrBlank()) {
                    BannerImage(
                        imageUrl = item.bannerImageUrl,
                        fallbackResId = if (isSchool) R.drawable.img_campus_hero else R.drawable.img_portal_academic,
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = StatusPresentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${item.advertiserName} • ${item.metaBadge}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = item.locationLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(item.description, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(onClick = onPrimaryAction) {
                Text(item.ctaLabel)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onExploreMore) {
                    Text(if (isSchool) "Discover Map" else "All Tutors")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun HomeQuickInquiryDialog(
    advert: Advert,
    defaultName: String,
    defaultEmail: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String?, String) -> Unit
) {
    var senderName by remember { mutableStateOf(defaultName) }
    var senderEmail by remember { mutableStateOf(defaultEmail) }
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Hello, I saw your advert \"${advert.title}\" on Skolquik Home.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inquire • ${advert.advertiserName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("Your Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = senderEmail,
                    onValueChange = { senderEmail = it },
                    label = { Text("Your Email *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message *") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(senderName, senderEmail, phone.ifBlank { null }, message) },
                enabled = !isBusy && senderName.isNotBlank() && senderEmail.isNotBlank() && message.trim().length >= 2
            ) {
                Text(if (isBusy) "Sending…" else "Send Inquiry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
