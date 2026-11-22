package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Advert
import com.example.data.AdvertInquiry
import com.example.data.Enrollment
import com.example.data.PaymentTransaction
import com.example.data.ProgressiveTutor
import com.example.data.School
import com.example.data.TutorSession
import com.example.data.UserProfile
import com.example.data.formatCurrencyAmount
import com.example.ui.theme.StatusLateAmber
import com.example.ui.theme.StatusPresentGreen

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.platformOversightItems(
    selectedCategory: Int,
    onSelectCategory: (Int) -> Unit,
    users: List<UserProfile>,
    schools: List<School>,
    tutors: List<ProgressiveTutor>,
    enrollments: List<Enrollment>,
    tutorSessions: List<TutorSession>,
    adverts: List<Advert>,
    inquiries: List<AdvertInquiry>,
    payments: List<PaymentTransaction>,
    isBusy: Boolean,
    onVerifySchool: (School) -> Unit,
    onConfirmPaymentPaid: (PaymentTransaction) -> Unit,
    onViewReceipt: (PaymentTransaction) -> Unit
) {
    item {
        val paidByCurrency = payments.filter { it.status == "PAID" }
            .groupBy { it.currency }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val revenueSummary = if (paidByCurrency.isEmpty()) {
            "No verified payments yet"
        } else {
            paidByCurrency.entries.joinToString(" • ") { (curr, amt) -> formatCurrencyAmount(amt, curr) }
        }
        val pendingCount = payments.count { it.status == "PENDING_VERIFICATION" }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("platform_oversight_summary_card")
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
                        Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Column {
                        Text(
                            text = "Platform Super-Admin Oversight",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Real-time visibility across every registration, booking, advert & payment",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OversightKpiChip("Users", users.size.toString())
                    OversightKpiChip("Schools", schools.size.toString())
                    OversightKpiChip("Tutors", tutors.size.toString())
                    OversightKpiChip("Enrollments", enrollments.size.toString())
                    OversightKpiChip("Sessions", tutorSessions.size.toString())
                    OversightKpiChip("Adverts", adverts.size.toString())
                    OversightKpiChip("Inquiries", inquiries.size.toString())
                    OversightKpiChip("Payments", payments.size.toString())
                }

                Text(
                    text = "Verified Revenue: $revenueSummary" +
                        if (pendingCount > 0) " • $pendingCount transfer(s) pending confirmation" else "",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = StatusPresentGreen
                )
            }
        }
    }

    item {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Users (${users.size})",
                "Schools (${schools.size})",
                "Tutors (${tutors.size})",
                "Enrollments (${enrollments.size})",
                "Sessions (${tutorSessions.size})",
                "Adverts & Leads (${adverts.size + inquiries.size})",
                "Payments (${payments.size})"
            ).forEachIndexed { idx, label ->
                FilterChip(
                    selected = selectedCategory == idx,
                    onClick = { onSelectCategory(idx) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("oversight_cat_$idx")
                )
            }
        }
    }

    when (selectedCategory) {
        0 -> {
            if (users.isEmpty()) {
                item { OversightEmpty("No registered users found yet.") }
            } else {
                items(users, key = { "ov_usr_${it.id}" }) { u ->
                    OversightItemCard(
                        title = u.displayName.ifBlank { u.email },
                        subtitle = "${u.email} • Role: ${u.role.uppercase()}",
                        detail = "Location: ${u.homeAddress ?: "Not set"} • Radius: ${u.searchRadiusKm} km" +
                            if (!u.phone.isNullOrBlank()) " • ${u.phone}" else "",
                        badgeText = u.role.uppercase(),
                        badgeIsPositive = u.role == "admin"
                    )
                }
            }
        }

        1 -> {
            if (schools.isEmpty()) {
                item { OversightEmpty("No schools registered yet.") }
            } else {
                items(schools, key = { "ov_sch_${it.id}" }) { s ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp,
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${s.institutionType} • ${s.curriculum} • ${s.formattedFeeRange()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                AttributeBadge(
                                    text = s.verificationBadgeLabel(),
                                    containerColor = if (s.verificationStatus == "VERIFIED") StatusPresentGreen.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (s.verificationStatus == "VERIFIED") StatusPresentGreen
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Admin: ${s.adminName} • ${s.address}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { onVerifySchool(s) },
                                enabled = !isBusy
                            ) {
                                Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Verify on Internet")
                            }
                        }
                    }
                }
            }
        }

        2 -> {
            if (tutors.isEmpty()) {
                item { OversightEmpty("No Progressive Tutors registered yet.") }
            } else {
                items(tutors, key = { "ov_tut_${it.id}" }) { t ->
                    OversightItemCard(
                        title = "${t.fullName} (${t.sex})",
                        subtitle = "${t.headline} • ${t.formattedHourlyRate()}",
                        detail = "${t.locationLabel} • Tracks: ${t.curriculumTracks.joinToString("/")} • ${t.meetingPlatform}",
                        badgeText = if (t.onlineActive) "ACTIVE" else "INACTIVE",
                        badgeIsPositive = t.onlineActive
                    )
                }
            }
        }

        3 -> {
            if (enrollments.isEmpty()) {
                item { OversightEmpty("No enrollments submitted yet.") }
            } else {
                items(enrollments, key = { "ov_enr_${it.id}" }) { enr ->
                    val payLabel = enr.paymentStatus ?: "UNPAID"
                    OversightItemCard(
                        title = "${enr.studentName} → ${enr.schoolName}",
                        subtitle = "Parent: ${enr.parentName} • Grade/Prog: ${enr.gradeApplying}",
                        detail = "Application: ${enr.status} • Fee: $payLabel",
                        badgeText = payLabel,
                        badgeIsPositive = payLabel == "PAID"
                    )
                }
            }
        }

        4 -> {
            if (tutorSessions.isEmpty()) {
                item { OversightEmpty("No tutor sessions booked yet.") }
            } else {
                items(tutorSessions, key = { "ov_ses_${it.id}" }) { ses ->
                    val payLabel = ses.paymentStatus ?: "UNPAID"
                    OversightItemCard(
                        title = "${ses.studentName} • ${ses.subject}",
                        subtitle = "Tutor: ${ses.tutorName} • Client: ${ses.clientName}",
                        detail = "Schedule: ${ses.preferredSchedule} • Session: ${ses.status} • Payment: $payLabel",
                        badgeText = ses.status,
                        badgeIsPositive = ses.status == "CONFIRMED" || ses.status == "COMPLETED"
                    )
                }
            }
        }

        5 -> {
            if (adverts.isEmpty() && inquiries.isEmpty()) {
                item { OversightEmpty("No adverts or campaign inquiries yet.") }
            } else {
                if (adverts.isNotEmpty()) {
                    item {
                        Text("All Advert Campaigns (${adverts.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(adverts, key = { "ov_adv_${it.id}" }) { ad ->
                        OversightItemCard(
                            title = ad.title,
                            subtitle = "${ad.advertiserName} • ${ad.planTier} (${ad.planPriceLabel})",
                            detail = "${ad.category} • ${ad.targetLocation} • Status: ${ad.status} • Fee: ${ad.paymentStatus ?: "N/A"}",
                            badgeText = ad.status,
                            badgeIsPositive = ad.status == "ACTIVE"
                        )
                    }
                }
                if (inquiries.isNotEmpty()) {
                    item {
                        Text("All Advert Inquiries (${inquiries.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(inquiries, key = { "ov_inq_${it.id}" }) { inq ->
                        OversightItemCard(
                            title = "${inq.senderName} → ${inq.advertTitle}",
                            subtitle = inq.senderEmail + if (!inq.phone.isNullOrBlank()) " • ${inq.phone}" else "",
                            detail = inq.message,
                            badgeText = inq.status,
                            badgeIsPositive = inq.status == "CONTACTED"
                        )
                    }
                }
            }
        }

        else -> {
            if (payments.isEmpty()) {
                item { OversightEmpty("No payment transactions recorded yet.") }
            } else {
                items(payments, key = { "ov_pay_${it.id}" }) { pay ->
                    PaymentTransactionCard(
                        payment = pay,
                        isIncoming = true,
                        showVerifyButton = true,
                        onVerifyPaid = { onConfirmPaymentPaid(pay) },
                        onViewReceipt = { onViewReceipt(pay) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OversightKpiChip(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OversightItemCard(
    title: String,
    subtitle: String,
    detail: String,
    badgeText: String,
    badgeIsPositive: Boolean
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                AttributeBadge(
                    text = badgeText,
                    containerColor = if (badgeIsPositive) StatusPresentGreen.copy(alpha = 0.15f) else StatusLateAmber.copy(alpha = 0.15f),
                    contentColor = if (badgeIsPositive) StatusPresentGreen else StatusLateAmber
                )
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OversightEmpty(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
