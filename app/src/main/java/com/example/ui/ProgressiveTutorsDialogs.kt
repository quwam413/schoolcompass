package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ProgressiveTutor
import com.example.data.SchoolConstants
import com.example.data.TutorSession
import com.example.data.formatCurrencyAmount
import com.example.ui.theme.StatusPresentGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TutorEditorDialog(
    existingTutor: ProgressiveTutor?,
    defaultName: String,
    defaultLat: Double,
    defaultLng: Double,
    defaultLocationLabel: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProgressiveTutor, Boolean) -> Unit
) {
    var fullName by remember { mutableStateOf(existingTutor?.fullName ?: defaultName) }
    var sex by remember { mutableStateOf(existingTutor?.sex ?: "Female") }
    var headline by remember { mutableStateOf(existingTutor?.headline ?: "") }
    var bio by remember { mutableStateOf(existingTutor?.bio ?: "") }
    var qualifications by remember { mutableStateOf(existingTutor?.qualifications ?: "") }
    var yearsExp by remember { mutableStateOf((existingTutor?.yearsExperience ?: 4L).toString()) }
    var hourlyRate by remember { mutableStateOf((existingTutor?.hourlyRate ?: 7000L).toString()) }
    var currency by remember { mutableStateOf(existingTutor?.currency ?: "NGN") }
    var meetingPlatform by remember { mutableStateOf(existingTutor?.meetingPlatform ?: "Google Meet") }
    var availability by remember { mutableStateOf(existingTutor?.availability ?: "Mon–Fri • 4:00 PM – 8:00 PM") }
    var selectedPreset by remember {
        mutableStateOf(
            SchoolConstants.PRESET_LOCATIONS.firstOrNull { it.label == existingTutor?.locationLabel }
        )
    }
    var locationLabel by remember { mutableStateOf(existingTutor?.locationLabel ?: defaultLocationLabel) }
    var cambridgeSelected by remember {
        mutableStateOf(existingTutor?.cambridgeSubjects?.toSet() ?: setOf("Cambridge IGCSE Mathematics"))
    }
    var nigerianSelected by remember {
        mutableStateOf(existingTutor?.nigerianSubjects?.toSet() ?: setOf("WAEC / NECO Mathematics"))
    }
    var bannerImageUrl by remember(existingTutor) {
        mutableStateOf(existingTutor?.bannerImageUrl)
    }

    val hasAnySubject = cambridgeSelected.isNotEmpty() || nigerianSelected.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingTutor == null) "Offer Online Tutoring Session" else "Edit Tutoring Profile",
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
                BannerImageEditorSection(
                    bannerImageUrl = bannerImageUrl,
                    fallbackResId = R.drawable.img_portal_academic,
                    title = "Tutor Display Banner Picture",
                    subtitle = "Shown on your tutor card and Home adverts across Skolquik",
                    testTagPrefix = "tutor_banner",
                    onBannerChanged = { bannerImageUrl = it }
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Teacher Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tutor_name_input")
                )

                Text("Sex *", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SchoolConstants.TUTOR_SEX_OPTIONS.forEach { opt ->
                        FilterChip(
                            selected = sex == opt,
                            onClick = { sex = opt },
                            label = { Text(opt) }
                        )
                    }
                }

                OutlinedTextField(
                    value = headline,
                    onValueChange = { headline = it },
                    label = { Text("Session Headline (e.g., Cambridge Physics & WAEC Maths) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tutor_headline_input")
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Teaching Approach & Exam Prep Bio") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Cambridge Subjects", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SchoolConstants.CAMBRIDGE_SUBJECTS.forEach { subj ->
                        val active = subj in cambridgeSelected
                        FilterChip(
                            selected = active,
                            onClick = {
                                cambridgeSelected = if (active) cambridgeSelected - subj else cambridgeSelected + subj
                            },
                            label = { Text(subj.removePrefix("Cambridge ")) }
                        )
                    }
                }

                Text("Nigerian Subjects", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SchoolConstants.NIGERIAN_SUBJECTS.forEach { subj ->
                        val active = subj in nigerianSelected
                        FilterChip(
                            selected = active,
                            onClick = {
                                nigerianSelected = if (active) nigerianSelected - subj else nigerianSelected + subj
                            },
                            label = { Text(subj) }
                        )
                    }
                }

                OutlinedTextField(
                    value = qualifications,
                    onValueChange = { qualifications = it },
                    label = { Text("Qualifications (e.g., M.Sc., TRCN, IGCSE Certified)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = yearsExp,
                        onValueChange = { yearsExp = it.filter(Char::isDigit) },
                        label = { Text("Years Exp") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hourlyRate,
                        onValueChange = { hourlyRate = it.filter(Char::isDigit) },
                        label = { Text("Rate / hr") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Currency & Platform", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SchoolConstants.FEE_CURRENCIES.forEach { curr ->
                        FilterChip(
                            selected = currency == curr,
                            onClick = { currency = curr },
                            label = { Text(curr) }
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SchoolConstants.MEETING_PLATFORMS.forEach { plat ->
                        FilterChip(
                            selected = meetingPlatform == plat,
                            onClick = { meetingPlatform = plat },
                            label = { Text(plat) }
                        )
                    }
                }

                OutlinedTextField(
                    value = availability,
                    onValueChange = { availability = it },
                    label = { Text("Online Availability Schedule") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Teacher Base Location (for client distance)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SchoolConstants.PRESET_LOCATIONS.forEach { preset ->
                        FilterChip(
                            selected = locationLabel == preset.label,
                            onClick = {
                                selectedPreset = preset
                                locationLabel = preset.label
                            },
                            label = { Text(preset.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = locationLabel,
                    onValueChange = { locationLabel = it },
                    label = { Text("Neighborhood / City") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = selectedPreset?.lat ?: existingTutor?.latitude ?: defaultLat
                    val lng = selectedPreset?.lng ?: existingTutor?.longitude ?: defaultLng
                    val combined = (cambridgeSelected + nigerianSelected).toList()
                    val tracks = buildList {
                        if (cambridgeSelected.isNotEmpty()) add("Cambridge")
                        if (nigerianSelected.isNotEmpty()) add("Nigerian")
                    }
                    onSave(
                        ProgressiveTutor(
                            id = existingTutor?.id ?: "",
                            teacherUid = existingTutor?.teacherUid ?: "",
                            fullName = fullName,
                            sex = sex,
                            headline = headline,
                            bio = bio,
                            curriculumTracks = tracks,
                            cambridgeSubjects = cambridgeSelected.toList(),
                            nigerianSubjects = nigerianSelected.toList(),
                            allSubjects = combined,
                            qualifications = qualifications.ifBlank { "Certified Online Educator" },
                            yearsExperience = yearsExp.toLongOrNull() ?: 3L,
                            hourlyRate = hourlyRate.toLongOrNull() ?: 7000L,
                            currency = currency,
                            meetingPlatform = meetingPlatform,
                            availability = availability,
                            locationLabel = locationLabel,
                            latitude = lat,
                            longitude = lng,
                            rating = existingTutor?.rating ?: 4.8,
                            onlineActive = true,
                            bannerImageUrl = bannerImageUrl
                        ),
                        existingTutor == null
                    )
                },
                enabled = !isBusy && fullName.trim().length >= 2 && headline.trim().length >= 2 && hasAnySubject,
                modifier = Modifier.testTag("save_tutor_profile_button")
            ) {
                Text(if (isBusy) "Saving…" else "Publish Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookTutorSessionDialog(
    tutor: ProgressiveTutor,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onBook: (
        studentName: String,
        curriculumTrack: String,
        subject: String,
        schedule: String,
        notes: String?,
        hours: Int,
        paymentDraft: PaymentSubmissionDraft?
    ) -> Unit
) {
    var studentName by remember { mutableStateOf("") }
    val availableTracks = remember(tutor) {
        buildList {
            if (tutor.cambridgeSubjects.isNotEmpty()) add("Cambridge")
            if (tutor.nigerianSubjects.isNotEmpty()) add("Nigerian")
            if (tutor.cambridgeSubjects.isNotEmpty() && tutor.nigerianSubjects.isNotEmpty()) add("Cambridge & Nigerian")
            if (isEmpty()) add("Cambridge & Nigerian")
        }
    }
    var selectedTrack by remember { mutableStateOf(availableTracks.last()) }
    var selectedSubject by remember { mutableStateOf(tutor.allSubjects.firstOrNull() ?: "Mathematics") }
    var selectedHours by remember { mutableIntStateOf(1) }
    var preferredSchedule by remember { mutableStateOf(tutor.availability.ifBlank { "Weekdays 5:00 PM" }) }
    var notes by remember { mutableStateOf("") }
    var showCheckout by remember { mutableStateOf(false) }

    val totalAmount = (tutor.hourlyRate * selectedHours).coerceAtLeast(1L)

    if (showCheckout) {
        PaymentCheckoutDialog(
            title = "Tutoring Session Checkout",
            subtitle = "${tutor.fullName} • $selectedSubject (${selectedHours}h)",
            recipientName = tutor.fullName,
            amount = totalAmount,
            currency = tutor.currency,
            isBusy = isBusy,
            allowPayLater = true,
            onDismiss = { showCheckout = false },
            onConfirmPayment = { draft ->
                showCheckout = false
                onBook(studentName, selectedTrack, selectedSubject, preferredSchedule, notes.ifBlank { null }, selectedHours, draft)
            },
            onPayLater = {
                showCheckout = false
                onBook(studentName, selectedTrack, selectedSubject, preferredSchedule, notes.ifBlank { null }, selectedHours, null)
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Book Online Session with ${tutor.fullName}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${tutor.formattedHourlyRate()} • via ${tutor.meetingPlatform}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it },
                        label = { Text("Student Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("book_tutor_student_input")
                    )
                    Text("Session Duration", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 4).forEach { hrs ->
                            FilterChip(
                                selected = selectedHours == hrs,
                                onClick = { selectedHours = hrs },
                                label = { Text("${hrs} hr${if (hrs > 1) "s" else ""}") }
                            )
                        }
                    }
                    Text(
                        text = "Session Fee: ${formatCurrencyAmount(totalAmount, tutor.currency)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusPresentGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Curriculum Track", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableTracks.forEach { track ->
                            FilterChip(
                                selected = selectedTrack == track,
                                onClick = { selectedTrack = track },
                                label = { Text(track) }
                            )
                        }
                    }
                    Text("Select Subject *", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tutor.allSubjects.forEach { subj ->
                            FilterChip(
                                selected = selectedSubject == subj,
                                onClick = { selectedSubject = subj },
                                label = { Text(subj) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = preferredSchedule,
                        onValueChange = { preferredSchedule = it },
                        label = { Text("Preferred Day & Time *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Learning Goals / Exam Target (IGCSE, WAEC, etc.)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCheckout = true },
                    enabled = !isBusy && studentName.isNotBlank() && selectedSubject.isNotBlank() && preferredSchedule.isNotBlank(),
                    modifier = Modifier.testTag("confirm_book_tutor_button")
                ) {
                    Text(if (isBusy) "Booking…" else "Continue to Payment")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            onBook(studentName, selectedTrack, selectedSubject, preferredSchedule, notes.ifBlank { null }, selectedHours, null)
                        },
                        enabled = !isBusy && studentName.isNotBlank() && selectedSubject.isNotBlank() && preferredSchedule.isNotBlank()
                    ) {
                        Text("Pay Later")
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun ConfirmTutorSessionDialog(
    session: TutorSession,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (meetingLink: String?) -> Unit
) {
    var meetingLink by remember { mutableStateOf(session.meetingLink ?: "https://meet.google.com/new") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Online Tutoring Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${session.studentName} • ${session.subject} (${session.curriculumTrack})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Requested slot: ${session.preferredSchedule}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = meetingLink,
                    onValueChange = { meetingLink = it },
                    label = { Text("Live Video Class Link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(meetingLink.ifBlank { null }) },
                enabled = !isBusy
            ) {
                Text("Confirm & Share Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
