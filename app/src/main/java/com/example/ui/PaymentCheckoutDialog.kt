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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.PaymentTransaction
import com.example.data.SchoolConstants
import com.example.data.formatCurrencyAmount
import com.example.ui.theme.StatusAbsentRed
import com.example.ui.theme.StatusLateAmber
import com.example.ui.theme.StatusPresentGreen

data class PaymentSubmissionDraft(
    val paymentMethod: String,
    val gatewayProvider: String,
    val notes: String?
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentCheckoutDialog(
    title: String,
    subtitle: String,
    recipientName: String,
    amount: Long,
    currency: String = "NGN",
    isBusy: Boolean = false,
    allowPayLater: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmPayment: (PaymentSubmissionDraft) -> Unit,
    onPayLater: (() -> Unit)? = null
) {
    var selectedMethod by remember { mutableStateOf("Card Payment") }
    var selectedGateway by remember { mutableStateOf("Paystack") }
    var cardLastFour by remember { mutableStateOf("4242") }
    var cardExpiry by remember { mutableStateOf("08/28") }
    var transferRef by remember { mutableStateOf("") }
    var walletPhone by remember { mutableStateOf("") }

    val formattedTotal = remember(amount, currency) { formatCurrencyAmount(amount, currency) }

    val effectiveGateway = if (selectedMethod == "Bank Transfer") "Direct Bank" else selectedGateway
    val isFormValid = when (selectedMethod) {
        "Card Payment" -> cardLastFour.filter(Char::isDigit).length == 4 && cardExpiry.trim().length >= 4
        "Bank Transfer" -> transferRef.trim().length >= 4
        "Mobile Wallet" -> walletPhone.trim().length >= 6
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = StatusPresentGreen, modifier = Modifier.size(20.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(subtitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("Payable to: $recipientName", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Amount", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = formattedTotal,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = StatusPresentGreen
                            )
                        }
                    }
                }

                Text("Payment Method", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SchoolConstants.PAYMENT_METHODS.forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (method) {
                                        "Card Payment" -> Icons.Filled.CreditCard
                                        "Bank Transfer" -> Icons.Filled.AccountBalance
                                        else -> Icons.Filled.PhoneAndroid
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(method) },
                            modifier = Modifier.testTag("pay_method_${method.replace(" ", "_")}")
                        )
                    }
                }

                if (selectedMethod != "Bank Transfer") {
                    Text("Payment Gateway", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Paystack", "Flutterwave", "Stripe").forEach { gw ->
                            FilterChip(
                                selected = selectedGateway == gw,
                                onClick = { selectedGateway = gw },
                                label = { Text(gw) }
                            )
                        }
                    }
                }

                when (selectedMethod) {
                    "Card Payment" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = cardLastFour,
                                onValueChange = { cardLastFour = it.filter(Char::isDigit).take(4) },
                                label = { Text("Card Last 4 *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("payment_card_last4")
                            )
                            OutlinedTextField(
                                value = cardExpiry,
                                onValueChange = { cardExpiry = it.take(5) },
                                label = { Text("MM/YY *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("payment_card_expiry")
                            )
                        }
                        Text(
                            text = "Processed securely via $selectedGateway • Instant digital receipt issued.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "Bank Transfer" -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("Skolquik Settlement Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text("Bank: Providus / Zenith Escrow • Acct: 1029384756", style = MaterialTheme.typography.bodySmall)
                                Text("Status will be verified by the recipient or Platform Admin.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedTextField(
                            value = transferRef,
                            onValueChange = { transferRef = it },
                            label = { Text("Transfer Reference / Sender Name *") },
                            placeholder = { Text("e.g. NIP-92837412 / Adeola") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("payment_transfer_ref")
                        )
                    }
                    "USSD / Instant Transfer" -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Dial *737*50*${amount}# or *901*${amount}# via $selectedGateway instant USSD gateway.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = walletPhone,
                            onValueChange = { walletPhone = it },
                            label = { Text("Mobile Wallet Phone / Tag *") },
                            placeholder = { Text("+234 800 000 0000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("payment_wallet_phone")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        val sanitizedNote = when (selectedMethod) {
                            "Card Payment" -> "Card ending •••• $cardLastFour ($cardExpiry)"
                            "Bank Transfer" -> "Transfer Ref: ${transferRef.trim()}"
                            "USSD / Instant Transfer" -> "Instant USSD via $selectedGateway"
                            else -> "Wallet: ${walletPhone.trim()}"
                        }
                        onConfirmPayment(
                            PaymentSubmissionDraft(
                                paymentMethod = selectedMethod,
                                gatewayProvider = effectiveGateway,
                                notes = sanitizedNote
                            )
                        )
                    },
                    enabled = !isBusy && isFormValid,
                    modifier = Modifier.testTag("confirm_payment_button")
                ) {
                    Text(if (isBusy) "Processing…" else "Pay $formattedTotal")
                }
                if (allowPayLater && onPayLater != null) {
                    OutlinedButton(
                        onClick = onPayLater,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("pay_later_button")
                    ) {
                        Text("Submit & Pay Later")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PaymentReceiptDialog(
    payment: PaymentTransaction,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusPresentGreen)
                Text("Payment Receipt", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(payment.referenceTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Amount: ${payment.formattedAmount()}", style = MaterialTheme.typography.titleMedium, color = StatusPresentGreen, fontWeight = FontWeight.ExtraBold)
                Text("Receipt #: ${payment.receiptNumber}", style = MaterialTheme.typography.bodySmall)
                Text("Gateway Ref: ${payment.gatewayReference}", style = MaterialTheme.typography.bodySmall)
                Text("Method: ${payment.paymentMethod} (${payment.gatewayProvider})", style = MaterialTheme.typography.bodySmall)
                Text("Recipient: ${payment.recipientName}", style = MaterialTheme.typography.bodySmall)
                Text("Status: ${payment.status}", style = MaterialTheme.typography.labelLarge, color = paymentStatusColor(payment.status))
                if (!payment.notes.isNullOrBlank()) {
                    Text("Details: ${payment.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun PaymentTransactionCard(
    payment: PaymentTransaction,
    isIncoming: Boolean = false,
    showVerifyButton: Boolean = false,
    onVerifyPaid: (() -> Unit)? = null,
    onViewReceipt: () -> Unit = {}
) {
    val stColor = paymentStatusColor(payment.status)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_card_${payment.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(payment.referenceTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isIncoming) "From: ${payment.payerName} (${payment.payerEmail})" else "To: ${payment.recipientName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = payment.formattedAmount(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = stColor
                    )
                    AttributeBadge(
                        text = payment.status,
                        containerColor = stColor.copy(alpha = 0.14f),
                        contentColor = stColor
                    )
                }
            }

            Text(
                text = "${payment.paymentMethod} • ${payment.gatewayProvider} • ${payment.receiptNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewReceipt,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Receipt")
                }
                if (showVerifyButton && payment.status == "PENDING_VERIFICATION" && onVerifyPaid != null) {
                    Button(
                        onClick = onVerifyPaid,
                        modifier = Modifier.weight(1f).testTag("verify_payment_${payment.id}")
                    ) {
                        Text("Confirm Paid")
                    }
                }
            }
        }
    }
}

fun paymentStatusColor(status: String) = when (status) {
    "PAID" -> StatusPresentGreen
    "PENDING_VERIFICATION" -> StatusLateAmber
    else -> StatusAbsentRed
}
