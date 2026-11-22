package com.example.data

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PaymentRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> Flow<T>.logFirestoreErrors(
        operationType: OperationType,
        path: String,
        expectedUid: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    fun observePaymentsByPayer(userId: String): Flow<List<PaymentTransaction>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "payments"
        emitAll(
            db.collection(path)
                .whereEqualTo("payerUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PaymentTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observePaymentsByRecipient(userId: String): Flow<List<PaymentTransaction>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "payments"
        emitAll(
            db.collection(path)
                .whereEqualTo("recipientUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PaymentTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeInquiriesBySender(userId: String): Flow<List<AdvertInquiry>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "advert_inquiries"
        emitAll(
            db.collection(path)
                .whereEqualTo("senderUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AdvertInquiry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeInquiriesByOwner(userId: String): Flow<List<AdvertInquiry>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "advert_inquiries"
        emitAll(
            db.collection(path)
                .whereEqualTo("ownerUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AdvertInquiry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    // --- Bootstrapped Platform Admin Oversight Streams (guarded on elebutequam413@gmail.com) ---

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllEnrollmentsForAdmin(): Flow<List<Enrollment>> = flow {
        val uid = requireUserId()
        val path = "enrollments"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Enrollment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllTutorSessionsForAdmin(): Flow<List<TutorSession>> = flow {
        val uid = requireUserId()
        val path = "tutor_sessions"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TutorSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllInquiriesForAdmin(): Flow<List<AdvertInquiry>> = flow {
        val uid = requireUserId()
        val path = "advert_inquiries"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AdvertInquiry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllPaymentsForAdmin(): Flow<List<PaymentTransaction>> = flow {
        val uid = requireUserId()
        val path = "payments"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PaymentTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createPaymentTransaction(
        payerName: String,
        payerEmail: String,
        recipientUid: String,
        recipientName: String,
        paymentType: String,
        referenceId: String,
        referenceTitle: String,
        amount: Long,
        currency: String,
        paymentMethod: String,
        gatewayProvider: String,
        notes: String? = null
    ): Result<PaymentTransaction> {
        val paymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "payments/$paymentId"
        return try {
            val uid = requireUserId()
            require(paymentType in SchoolConstants.PAYMENT_TYPES) { "Unsupported payment type" }
            require(paymentMethod in SchoolConstants.PAYMENT_METHODS) { "Unsupported payment method" }
            require(gatewayProvider in SchoolConstants.GATEWAY_PROVIDERS) { "Unsupported payment gateway" }
            val safeAmount = amount.coerceIn(1L, 100_000_000L)
            val safeCurrency = currency.takeIf { it in SchoolConstants.FEE_CURRENCIES } ?: "NGN"
            val gatewayRef = generateGatewayReference(gatewayProvider)
            val receiptNo = generateReceiptNumber()
            val status = if (paymentMethod == "Bank Transfer") "PENDING_VERIFICATION" else "PAID"
            val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(300)

            val payload = mapOf(
                "payerUid" to uid,
                "payerName" to payerName.trim().ifEmpty { "Parent" }.take(80),
                "payerEmail" to payerEmail.trim().take(120),
                "recipientUid" to recipientUid.trim().ifEmpty { uid }.take(128),
                "recipientName" to recipientName.trim().ifEmpty { "SchoolCompass Partner" }.take(100),
                "paymentType" to paymentType,
                "referenceId" to referenceId.trim().ifEmpty { paymentId }.take(128),
                "referenceTitle" to referenceTitle.trim().take(140),
                "amount" to safeAmount,
                "currency" to safeCurrency,
                "paymentMethod" to paymentMethod,
                "gatewayProvider" to gatewayProvider,
                "gatewayReference" to gatewayRef,
                "receiptNumber" to receiptNo,
                "status" to status,
                "notes" to cleanNotes,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("payments").document(paymentId).set(payload).await()

            linkPaymentToTarget(paymentType, referenceId, paymentId, status)

            Result.success(
                PaymentTransaction(
                    id = paymentId,
                    payerUid = uid,
                    payerName = payerName.trim().ifEmpty { "Parent" },
                    payerEmail = payerEmail,
                    recipientUid = recipientUid.ifBlank { uid },
                    recipientName = recipientName,
                    paymentType = paymentType,
                    referenceId = referenceId,
                    referenceTitle = referenceTitle,
                    amount = safeAmount,
                    currency = safeCurrency,
                    paymentMethod = paymentMethod,
                    gatewayProvider = gatewayProvider,
                    gatewayReference = gatewayRef,
                    receiptNumber = receiptNo,
                    status = status,
                    notes = cleanNotes
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun linkPaymentToTarget(
        paymentType: String,
        referenceId: String,
        paymentId: String,
        paymentStatus: String
    ) {
        if (referenceId.isBlank()) return
        val collection = when (paymentType) {
            "SCHOOL_APPLICATION_FEE", "TUITION_DEPOSIT" -> "enrollments"
            "ADVERT_CAMPAIGN" -> "adverts"
            "TUTOR_SESSION" -> "tutor_sessions"
            else -> return
        }
        runCatching {
            val docRef = db.collection(collection).document(referenceId)
            if (docRef.get().await().exists()) {
                docRef.update(
                    mapOf(
                        "paymentId" to paymentId,
                        "paymentStatus" to paymentStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    suspend fun updatePaymentStatus(
        payment: PaymentTransaction,
        newStatus: String,
        note: String? = null
    ): Result<Unit> {
        val path = "payments/${payment.id}"
        return try {
            requireUserId()
            require(newStatus in listOf("PAID", "PENDING_VERIFICATION", "REFUNDED", "FAILED")) { "Invalid payment status" }
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(300)
            if (cleanNote != null) updates["notes"] = cleanNote
            db.collection("payments").document(payment.id).update(updates).await()

            if (newStatus == "PAID") {
                linkPaymentToTarget(payment.paymentType, payment.referenceId, payment.id, "PAID")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitAdvertInquiry(
        advert: Advert,
        senderName: String,
        senderEmail: String,
        phone: String?,
        message: String
    ): Result<String> {
        val inquiryId = "inq_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "advert_inquiries/$inquiryId"
        return try {
            val uid = requireUserId()
            val cleanMsg = message.trim().take(600)
            require(cleanMsg.length >= 2) { "Please enter your inquiry message" }
            val cleanPhone = phone?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
            val payload = mapOf(
                "advertId" to advert.id,
                "advertTitle" to advert.title.trim().take(120),
                "ownerUid" to advert.ownerUid,
                "senderUid" to uid,
                "senderName" to senderName.trim().ifEmpty { "Parent" }.take(80),
                "senderEmail" to senderEmail.trim().take(120),
                "phone" to cleanPhone,
                "message" to cleanMsg,
                "status" to "NEW",
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("advert_inquiries").document(inquiryId).set(payload).await()
            Result.success(inquiryId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAdvertInquiryStatus(inquiryId: String, status: String): Result<Unit> {
        val path = "advert_inquiries/$inquiryId"
        return try {
            requireUserId()
            require(status in listOf("NEW", "CONTACTED", "CLOSED")) { "Invalid inquiry status" }
            db.collection("advert_inquiries").document(inquiryId).update("status", status).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    companion object {
        fun generateGatewayReference(provider: String): String {
            val prefix = when (provider) {
                "Paystack" -> "SC-PSK"
                "Flutterwave" -> "SC-FLW"
                "Stripe" -> "SC-STR"
                else -> "SC-BNK"
            }
            val token = UUID.randomUUID().toString().replace("-", "").uppercase().take(8)
            return "$prefix-$token"
        }

        fun generateReceiptNumber(): String {
            val suffix = UUID.randomUUID().toString().replace("-", "").uppercase().take(6)
            return "RCP-2026-$suffix"
        }
    }
}
