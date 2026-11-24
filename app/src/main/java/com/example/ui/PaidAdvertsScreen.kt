package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Advert
import com.example.data.AdvertInquiry
import com.example.data.AdvertWithDistance
import com.example.data.SchoolConstants
import com.example.ui.theme.StarGold
import com.example.ui.theme.StatusLateAmber
import com.example.ui.theme.StatusPresentGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaidAdvertsScreen(
    currentUserId: String,
    defaultAdvertiserName: String,
    defaultEmail: String = "",
    defaultLat: Double,
    defaultLng: Double,
    advertsUiState: UiState<List<Advert>>,
    sortedAdverts: List<AdvertWithDistance>,
    receivedInquiriesState: UiState<List<AdvertInquiry>> = UiState.Success(emptyList()),
    isBusy: Boolean,
    onSaveAdvert: (Advert, Boolean, PaymentSubmissionDraft?) -> Unit,
    onPayExistingAdvert: (Advert, PaymentSubmissionDraft) -> Unit = { _, _ -> },
    onUpdateAdvertStatus: (String, String) -> Unit,
    onDeleteAdvert: (String) -> Unit,
    onSubmitInquiry: (Advert, String, String, String?, String, () -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateInquiryStatus: (String, String) -> Unit = { _, _ -> },
    onNavigateToSchools: () -> Unit,
    onNavigateToTutors: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var sectionIndex by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingAdvert by remember { mutableStateOf<Advert?>(null) }
    var inquiryAdvert by remember { mutableStateOf<Advert?>(null) }
    var payingAdvert by remember { mutableStateOf<Advert?>(null) }

    val activeFeed = remember(sortedAdverts, selectedCategory) {
        sortedAdverts.filter {
            it.advert.status == "ACTIVE" &&
                (selectedCategory == null || it.advert.category == selectedCategory)
        }
    }
    val myCampaigns = remember(sortedAdverts, currentUserId) {
        sortedAdverts.filter { it.advert.ownerUid == currentUserId }
    }
    val incomingInquiries = (receivedInquiriesState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Publish CTA
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Discover"
                                )
                            }
                            Column {
                                Text(
                                    text = "Paid Adverts & Promotions",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Reach parents & students for admissions, camps & tutoring",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Icon(
                            Icons.Filled.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Button(
                        onClick = {
                            editingAdvert = null
                            showEditor = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_advert_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Launch Paid Advert Campaign")
                    }
                }
            }
        }

        // Ad Packages Preview Strip
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(SchoolConstants.ADVERT_PLANS) { plan ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(plan.tier, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(plan.priceLabel, style = MaterialTheme.typography.titleSmall, color = StatusPresentGreen, fontWeight = FontWeight.Bold)
                            Text(plan.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // View Switcher
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Sponsored Feed (${activeFeed.size})", "My Campaigns (${myCampaigns.size})").forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = sectionIndex == idx,
                        onClick = { sectionIndex = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
                        modifier = Modifier.testTag("adverts_section_$idx")
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (sectionIndex == 0) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All Adverts") }
                        )
                    }
                    items(SchoolConstants.ADVERT_CATEGORIES) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            when (advertsUiState) {
                UiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> item { ErrorBanner(advertsUiState.message) }
                is UiState.Success -> {
                    if (activeFeed.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Campaign,
                                title = "No live sponsored campaigns yet",
                                subtitle = "Verified schools appear live in Discover via Google Search & Maps. Launch a real campaign to spotlight open admissions here.",
                                actionLabel = "Browse Verified Schools",
                                onAction = onNavigateToSchools
                            )
                        }
                    } else {
                        items(activeFeed, key = { it.advert.id }) { item ->
                            SponsoredAdvertCard(
                                item = item,
                                onCtaClick = {
                                    when (item.advert.category) {
                                        "Progressive Tutors" -> onNavigateToTutors()
                                        else -> inquiryAdvert = item.advert
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            if (myCampaigns.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Campaign,
                        title = "You have no paid advert campaigns",
                        subtitle = "Create a sponsored advert to spotlight your school, tutoring program, or event.",
                        actionLabel = "Create Campaign",
                        onAction = {
                            editingAdvert = null
                            showEditor = true
                        }
                    )
                }
            } else {
                items(myCampaigns, key = { "my_${it.advert.id}" }) { item ->
                    val ad = item.advert
                    val payStatus = ad.paymentStatus ?: "UNPAID"
                    val payColor = paymentStatusColor(payStatus)
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
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
                                Text(ad.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AttributeBadge(
                                        text = payStatus,
                                        containerColor = payColor.copy(alpha = 0.14f),
                                        contentColor = payColor
                                    )
                                    AttributeBadge(
                                        text = ad.status,
                                        containerColor = if (ad.status == "ACTIVE") StatusPresentGreen.copy(alpha = 0.16f) else StatusLateAmber.copy(alpha = 0.16f),
                                        contentColor = if (ad.status == "ACTIVE") StatusPresentGreen else StatusLateAmber
                                    )
                                }
                            }
                            Text("${ad.planTier} • ${ad.planPriceLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(ad.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (payStatus == "UNPAID") {
                                Button(
                                    onClick = { payingAdvert = ad },
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Pay Campaign Fee")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val next = if (ad.status == "ACTIVE") "PAUSED" else "ACTIVE"
                                        onUpdateAdvertStatus(ad.id, next)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (ad.status == "ACTIVE") Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(if (ad.status == "ACTIVE") "Pause" else "Resume")
                                }
                                OutlinedButton(
                                    onClick = {
                                        editingAdvert = ad
                                        showEditor = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Edit")
                                }
                                IconButton(onClick = { onDeleteAdvert(ad.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete advert", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Incoming Advert Inquiries (${incomingInquiries.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (incomingInquiries.isEmpty()) {
                item {
                    Text(
                        text = "Parent inquiries submitted on your sponsored adverts appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(incomingInquiries, key = { "adv_inq_${it.id}" }) { inq ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(inq.advertTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                AttributeBadge(
                                    text = inq.status,
                                    containerColor = if (inq.status == "CONTACTED") StatusPresentGreen.copy(alpha = 0.14f) else StatusLateAmber.copy(alpha = 0.14f),
                                    contentColor = if (inq.status == "CONTACTED") StatusPresentGreen else StatusLateAmber
                                )
                            }
                            Text(
                                text = "From: ${inq.senderName} (${inq.senderEmail})" +
                                    if (!inq.phone.isNullOrBlank()) " • ${inq.phone}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(inq.message, style = MaterialTheme.typography.bodySmall)
                            if (inq.status == "NEW") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onUpdateInquiryStatus(inq.id, "CONTACTED") },
                                        enabled = !isBusy,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Mark Contacted")
                                    }
                                    OutlinedButton(
                                        onClick = { onUpdateInquiryStatus(inq.id, "CLOSED") },
                                        enabled = !isBusy,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Close")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showEditor) {
        AdvertEditorDialog(
            existingAdvert = editingAdvert,
            defaultAdvertiserName = defaultAdvertiserName,
            defaultLat = defaultLat,
            defaultLng = defaultLng,
            isBusy = isBusy,
            onDismiss = { showEditor = false },
            onSave = { advert, isNew, draft ->
                onSaveAdvert(advert, isNew, draft)
                showEditor = false
            }
        )
    }

    payingAdvert?.let { ad ->
        val matchedPlan = SchoolConstants.ADVERT_PLANS.firstOrNull { it.tier == ad.planTier }
            ?: SchoolConstants.ADVERT_PLANS[1]
        PaymentCheckoutDialog(
            title = "Pay Advert Campaign",
            subtitle = "${matchedPlan.tier} • ${ad.title}",
            recipientName = "Skolquik Paid Adverts",
            amount = matchedPlan.amountNgn,
            currency = "NGN",
            isBusy = isBusy,
            allowPayLater = false,
            onDismiss = { payingAdvert = null },
            onConfirmPayment = { draft ->
                onPayExistingAdvert(ad, draft)
                payingAdvert = null
            }
        )
    }

    inquiryAdvert?.let { ad ->
        AdvertInquiryDialog(
            advert = ad,
            defaultName = defaultAdvertiserName,
            defaultEmail = defaultEmail,
            isBusy = isBusy,
            onDismiss = { inquiryAdvert = null },
            onBrowseSchools = {
                inquiryAdvert = null
                onNavigateToSchools()
            },
            onSubmit = { senderName, senderEmail, phone, message ->
                onSubmitInquiry(ad, senderName, senderEmail, phone, message) {
                    inquiryAdvert = null
                }
            }
        )
    }
}

@Composable
private fun AdvertInquiryDialog(
    advert: Advert,
    defaultName: String,
    defaultEmail: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onBrowseSchools: () -> Unit,
    onSubmit: (String, String, String?, String) -> Unit
) {
    var senderName by remember { mutableStateOf(defaultName) }
    var senderEmail by remember { mutableStateOf(defaultEmail) }
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Hello, I would like more details about \"${advert.title}\".") }

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
                Text(advert.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(advert.body, style = MaterialTheme.typography.bodySmall)
                if (!advert.contactInfo.isNullOrBlank()) {
                    Text(
                        text = "Direct Contact: ${advert.contactInfo}",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusPresentGreen
                    )
                }
                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("Your Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("inquiry_name_input")
                )
                OutlinedTextField(
                    value = senderEmail,
                    onValueChange = { senderEmail = it },
                    label = { Text("Your Email *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("inquiry_email_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Inquiry Message *") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("inquiry_message_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(senderName, senderEmail, phone.ifBlank { null }, message)
                },
                enabled = !isBusy && senderName.isNotBlank() && senderEmail.isNotBlank() && message.trim().length >= 2,
                modifier = Modifier.testTag("submit_advert_inquiry_button")
            ) {
                Text(if (isBusy) "Sending…" else "Send Inquiry")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onBrowseSchools) { Text("Browse Schools") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SponsoredAdvertCard(
    item: AdvertWithDistance,
    onCtaClick: () -> Unit
) {
    val ad = item.advert
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advert_card_${ad.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AttributeBadge(
                    text = "Sponsored",
                    containerColor = StarGold.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
                AttributeBadge(
                    text = ad.planTier,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                AttributeBadge(
                    text = ad.category,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (ad.curriculumFocus != "All") {
                    AttributeBadge(
                        text = ad.curriculumFocus,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Text(ad.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("By ${ad.advertiserName}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(ad.body, style = MaterialTheme.typography.bodyMedium)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${ad.targetLocation} • ${String.format("%.1f", item.distanceKm)} km away",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onCtaClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(ad.ctaLabel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvertEditorDialog(
    existingAdvert: Advert?,
    defaultAdvertiserName: String,
    defaultLat: Double,
    defaultLng: Double,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Advert, Boolean, PaymentSubmissionDraft?) -> Unit
) {
    var advertiserName by remember { mutableStateOf(existingAdvert?.advertiserName ?: defaultAdvertiserName) }
    var title by remember { mutableStateOf(existingAdvert?.title ?: "") }
    var body by remember { mutableStateOf(existingAdvert?.body ?: "") }
    var category by remember { mutableStateOf(existingAdvert?.category ?: SchoolConstants.ADVERT_CATEGORIES.first()) }
    var curriculumFocus by remember { mutableStateOf(existingAdvert?.curriculumFocus ?: "All") }
    var selectedPlan by remember {
        mutableStateOf(
            SchoolConstants.ADVERT_PLANS.firstOrNull { it.tier == existingAdvert?.planTier }
                ?: SchoolConstants.ADVERT_PLANS[1]
        )
    }
    var selectedPreset by remember {
        mutableStateOf(SchoolConstants.PRESET_LOCATIONS.firstOrNull { it.label == existingAdvert?.targetLocation })
    }
    var targetLocation by remember { mutableStateOf(existingAdvert?.targetLocation ?: "Lekki Phase 1, Lagos") }
    var ctaLabel by remember { mutableStateOf(existingAdvert?.ctaLabel ?: "Inquire Now") }
    var contactInfo by remember { mutableStateOf(existingAdvert?.contactInfo ?: "") }
    var showCheckout by remember { mutableStateOf(false) }

    fun buildAdvert(): Advert {
        val lat = selectedPreset?.lat ?: existingAdvert?.latitude ?: defaultLat
        val lng = selectedPreset?.lng ?: existingAdvert?.longitude ?: defaultLng
        return Advert(
            id = existingAdvert?.id ?: "",
            ownerUid = existingAdvert?.ownerUid ?: "",
            advertiserName = advertiserName,
            title = title,
            body = body,
            category = category,
            curriculumFocus = curriculumFocus,
            planTier = selectedPlan.tier,
            planPriceLabel = selectedPlan.priceLabel,
            durationDays = selectedPlan.durationDays.toLong(),
            targetLocation = targetLocation,
            latitude = lat,
            longitude = lng,
            ctaLabel = ctaLabel,
            contactInfo = contactInfo.ifBlank { null },
            status = existingAdvert?.status ?: "ACTIVE",
            paymentId = existingAdvert?.paymentId,
            paymentStatus = existingAdvert?.paymentStatus
        )
    }

    if (showCheckout && existingAdvert == null) {
        PaymentCheckoutDialog(
            title = "Paid Advert Checkout",
            subtitle = "${selectedPlan.tier} • ${title.ifBlank { advertiserName }}",
            recipientName = "Skolquik Paid Adverts",
            amount = selectedPlan.amountNgn,
            currency = "NGN",
            isBusy = isBusy,
            allowPayLater = true,
            onDismiss = { showCheckout = false },
            onConfirmPayment = { draft ->
                showCheckout = false
                onSave(buildAdvert(), true, draft)
            },
            onPayLater = {
                showCheckout = false
                onSave(buildAdvert(), true, null)
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = if (existingAdvert == null) "Publish Paid Advert" else "Edit Advert Campaign",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Select Promotion Tier", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SchoolConstants.ADVERT_PLANS.forEach { plan ->
                            FilterChip(
                                selected = selectedPlan.tier == plan.tier,
                                onClick = { selectedPlan = plan },
                                label = { Text("${plan.tier} • ${plan.priceLabel}") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OutlinedTextField(
                        value = advertiserName,
                        onValueChange = { advertiserName = it },
                        label = { Text("School / Tutor / Organization Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("advert_org_input")
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Campaign Headline *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("advert_title_input")
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Advert Copy & Offer Details *") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("advert_body_input")
                    )

                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SchoolConstants.ADVERT_CATEGORIES.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }

                    Text("Curriculum Focus", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SchoolConstants.ADVERT_CURRICULA.forEach { curr ->
                            FilterChip(
                                selected = curriculumFocus == curr,
                                onClick = { curriculumFocus = curr },
                                label = { Text(curr) }
                            )
                        }
                    }

                    Text("Target Area", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SchoolConstants.PRESET_LOCATIONS.forEach { preset ->
                            FilterChip(
                                selected = targetLocation == preset.label,
                                onClick = {
                                    selectedPreset = preset
                                    targetLocation = preset.label
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = ctaLabel,
                        onValueChange = { ctaLabel = it },
                        label = { Text("Button Label (e.g., Apply Now)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contactInfo,
                        onValueChange = { contactInfo = it },
                        label = { Text("Contact Email or Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (existingAdvert == null) {
                            showCheckout = true
                        } else {
                            onSave(buildAdvert(), false, null)
                        }
                    },
                    enabled = !isBusy && advertiserName.isNotBlank() && title.trim().length >= 2 && body.trim().length >= 4,
                    modifier = Modifier.testTag("save_advert_button")
                ) {
                    Text(
                        if (isBusy) "Saving…"
                        else if (existingAdvert == null) "Proceed to Checkout"
                        else "Save Edits"
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}
