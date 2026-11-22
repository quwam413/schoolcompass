package com.example.data

import android.content.Context
import com.example.R
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
import kotlin.math.roundToInt

class SchoolRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

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

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .logFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    fun observeSchools(): Flow<List<School>> = flow {
        val uid = requireUserId()
        val path = "schools"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.districtRank }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeSchoolById(schoolId: String): Flow<School?> = flow {
        val uid = requireUserId()
        val trimmedId = schoolId.trim()
        require(trimmedId.matches(Regex("^[a-zA-Z0-9_-]{1,128}$"))) {
            "Invalid school ID"
        }
        val path = "schools/$trimmedId"
        emitAll(
            db.collection("schools").document(trimmedId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .logFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    fun observeParentEnrollments(userId: String): Flow<List<Enrollment>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "enrollments"
        emitAll(
            db.collection(path)
                .whereEqualTo("parentUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Enrollment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAdminEnrollments(userId: String): Flow<List<Enrollment>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "enrollments"
        emitAll(
            db.collection(path)
                .whereEqualTo("adminUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Enrollment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeParentStudents(userId: String): Flow<List<StudentRecord>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "students"
        emitAll(
            db.collection(path)
                .whereEqualTo("parentUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.studentName }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAdminStudents(userId: String): Flow<List<StudentRecord>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "students"
        emitAll(
            db.collection(path)
                .whereEqualTo("adminUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.studentName }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeStudentAttendance(studentId: String, isAdmin: Boolean): Flow<List<AttendanceLog>> = flow {
        val uid = requireUserId()
        val path = "students/$studentId/attendance"
        val filterField = if (isAdmin) "adminUid" else "parentUid"
        emitAll(
            db.collection("students").document(studentId).collection("attendance")
                .whereEqualTo(filterField, uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AttendanceLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeStudentGrades(studentId: String, isAdmin: Boolean): Flow<List<SubjectGrade>> = flow {
        val uid = requireUserId()
        val path = "students/$studentId/grades"
        val filterField = if (isAdmin) "adminUid" else "parentUid"
        emitAll(
            db.collection("students").document(studentId).collection("grades")
                .whereEqualTo(filterField, uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SubjectGrade::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.subject }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "conversations"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAnnouncements(): Flow<List<Announcement>> = flow {
        val uid = requireUserId()
        val path = "announcements"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeSchoolAnnouncements(schoolId: String): Flow<List<Announcement>> = flow {
        val uid = requireUserId()
        val trimmedId = schoolId.trim()
        require(trimmedId.matches(Regex("^[a-zA-Z0-9_-]{1,128}$"))) {
            "Invalid school ID"
        }
        val path = "announcements"
        emitAll(
            db.collection(path)
                .whereEqualTo("schoolId", trimmedId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun saveUserProfile(
        existing: UserProfile?,
        role: String,
        displayName: String,
        email: String,
        phone: String?,
        homeAddress: String?,
        homeLat: Double,
        homeLng: Double,
        searchRadiusKm: Int,
        childSummary: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            require(role in listOf("parent", "admin")) { "Role must be parent or admin" }
            val cleanName = displayName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Display name is required" }
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path

            val cleanPhone = phone?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
            val cleanAddr = homeAddress?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
            val cleanChild = childSummary?.trim()?.takeIf { it.isNotEmpty() }?.take(200)

            if (existing == null) {
                val payload = mapOf(
                    "userId" to uid,
                    "role" to role,
                    "displayName" to cleanName,
                    "email" to email.trim().take(120),
                    "phone" to cleanPhone,
                    "homeAddress" to cleanAddr,
                    "homeLat" to homeLat.coerceIn(-90.0, 90.0),
                    "homeLng" to homeLng.coerceIn(-180.0, 180.0),
                    "searchRadiusKm" to searchRadiusKm.coerceIn(1, 100),
                    "childSummary" to cleanChild,
                    "favoriteSchoolIds" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "role" to role,
                    "displayName" to cleanName,
                    "homeLat" to homeLat.coerceIn(-90.0, 90.0),
                    "homeLng" to homeLng.coerceIn(-180.0, 180.0),
                    "searchRadiusKm" to searchRadiusKm.coerceIn(1, 100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanPhone != null) updates["phone"] = cleanPhone
                if (cleanAddr != null) updates["homeAddress"] = cleanAddr
                if (cleanChild != null) updates["childSummary"] = cleanChild
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleFavoriteSchool(schoolId: String, currentlyFavorite: Boolean): Result<Boolean> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanSchoolId = schoolId.trim()
            require(cleanSchoolId.isNotEmpty() && cleanSchoolId.length <= 128) {
                "Invalid school identifier"
            }
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val arrayMutation = if (currentlyFavorite) {
                FieldValue.arrayRemove(cleanSchoolId)
            } else {
                FieldValue.arrayUnion(cleanSchoolId)
            }
            docRef.update(
                mapOf(
                    "favoriteSchoolIds" to arrayMutation,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(!currentlyFavorite)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createOrUpdateSchool(school: School, isNew: Boolean): Result<String> {
        val cleanExternalId = school.externalPlaceId?.trim()?.takeIf { it.isNotEmpty() }?.take(128)
        val isClaimingUnregistered = isNew || school.id.isBlank() || school.id.startsWith("osm_") || school.adminUid.isBlank()
        val schoolId = when {
            isClaimingUnregistered && cleanExternalId != null -> ExternalSchoolService.claimSchoolDocId(cleanExternalId)
            isClaimingUnregistered -> "sch_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            else -> school.id
        }
        val docPath = "schools/$schoolId"
        return try {
            val uid = requireUserId()
            val cleanName = school.name.trim().take(100)
            require(cleanName.length >= 2) { "School name must be at least 2 characters" }
            val cleanAddress = school.address.trim().take(200)
            require(cleanAddress.length >= 2) { "Address must be at least 2 characters" }
            val safeFeeMin = school.feeMin.coerceIn(0L, 50_000_000L)
            val safeFeeMax = school.feeMax.coerceAtLeast(safeFeeMin).coerceAtMost(100_000_000L)
            val safeCapacity = school.capacity.coerceIn(1L, 10_000L)
            val safeEnrolled = school.enrolledCount.coerceIn(0L, safeCapacity)
            val safePhone = school.contactPhone?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
            val safeWebsite = ExternalSchoolService.normalizeWebsiteUrl(school.websiteUrl)?.take(200)
            val safeVerifStatus = school.verificationStatus.takeIf {
                it in listOf("VERIFIED", "INTERNET_MATCHED", "UNVERIFIED")
            } ?: "UNVERIFIED"
            val safeVerifSource = school.verificationSource?.trim()?.takeIf { it.isNotEmpty() }?.take(160)

            val docRef = db.collection("schools").document(schoolId)
            val existingSnap = if (isClaimingUnregistered && cleanExternalId != null) {
                docRef.get().await()
            } else null

            val shouldCreate = isClaimingUnregistered && (existingSnap == null || !existingSnap.exists())
            if (!shouldCreate && existingSnap != null && existingSnap.exists()) {
                val existingOwner = existingSnap.getString("adminUid")
                if (!existingOwner.isNullOrBlank() && existingOwner != uid) {
                    throw IllegalStateException("This existing school has already been claimed by another school administrator.")
                }
            }

            if (shouldCreate) {
                val payload = mapOf(
                    "adminUid" to uid,
                    "adminName" to school.adminName.trim().ifEmpty { "School Admin" }.take(80),
                    "name" to cleanName,
                    "description" to school.description.trim().take(800),
                    "address" to cleanAddress,
                    "latitude" to school.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to school.longitude.coerceIn(-180.0, 180.0),
                    "curriculum" to school.curriculum.takeIf { it in SchoolConstants.CURRICULA }.orEmpty().ifEmpty { "Nigerian & British" },
                    "institutionType" to school.institutionType.takeIf { it in SchoolConstants.INSTITUTION_TYPES }.orEmpty().ifEmpty { "Primary & Secondary" },
                    "faithBased" to school.faithBased,
                    "faithTradition" to school.faithTradition.trim().ifEmpty { if (school.faithBased) "Christian" else "Secular" }.take(60),
                    "specialSchool" to school.specialSchool,
                    "specialNeedsSupport" to school.specialNeedsSupport.trim().ifEmpty { if (school.specialSchool) "Dedicated Special Needs School" else "General Education" }.take(120),
                    "studentMix" to school.studentMix.takeIf { it in SchoolConstants.STUDENT_MIX_OPTIONS }.orEmpty().ifEmpty { "Mixed" },
                    "gradeLevels" to school.gradeLevels.distinct().take(10),
                    "coursesOffered" to school.coursesOffered.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(25),
                    "activities" to school.activities.take(15),
                    "sportsFocus" to school.sportsFocus.take(12),
                    "feeMin" to safeFeeMin,
                    "feeMax" to safeFeeMax,
                    "feeCurrency" to school.feeCurrency.takeIf { it in SchoolConstants.FEE_CURRENCIES }.orEmpty().ifEmpty { "NGN" },
                    "feePeriod" to school.feePeriod.takeIf { it in SchoolConstants.FEE_PERIODS }.orEmpty().ifEmpty { "Per Term" },
                    "rankingScore" to school.rankingScore.coerceIn(0.0, 100.0),
                    "districtRank" to school.districtRank.coerceIn(1L, 9999L),
                    "rating" to school.rating.coerceIn(1.0, 5.0),
                    "tuitionTier" to school.tuitionTier.takeIf { it in SchoolConstants.TUITION_TIERS }.orEmpty().ifEmpty { "$$" },
                    "capacity" to safeCapacity,
                    "enrolledCount" to safeEnrolled,
                    "admissionsOpen" to school.admissionsOpen,
                    "contactPhone" to safePhone,
                    "externalPlaceId" to cleanExternalId,
                    "websiteUrl" to safeWebsite,
                    "verificationStatus" to safeVerifStatus,
                    "verificationSource" to safeVerifSource,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "adminName" to school.adminName.trim().ifEmpty { "School Admin" }.take(80),
                    "name" to cleanName,
                    "description" to school.description.trim().take(800),
                    "address" to cleanAddress,
                    "latitude" to school.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to school.longitude.coerceIn(-180.0, 180.0),
                    "curriculum" to school.curriculum.takeIf { it in SchoolConstants.CURRICULA }.orEmpty().ifEmpty { "Nigerian & British" },
                    "institutionType" to school.institutionType.takeIf { it in SchoolConstants.INSTITUTION_TYPES }.orEmpty().ifEmpty { "Primary & Secondary" },
                    "faithBased" to school.faithBased,
                    "faithTradition" to school.faithTradition.trim().ifEmpty { if (school.faithBased) "Christian" else "Secular" }.take(60),
                    "specialSchool" to school.specialSchool,
                    "specialNeedsSupport" to school.specialNeedsSupport.trim().ifEmpty { if (school.specialSchool) "Dedicated Special Needs School" else "General Education" }.take(120),
                    "studentMix" to school.studentMix.takeIf { it in SchoolConstants.STUDENT_MIX_OPTIONS }.orEmpty().ifEmpty { "Mixed" },
                    "gradeLevels" to school.gradeLevels.distinct().take(10),
                    "coursesOffered" to school.coursesOffered.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(25),
                    "activities" to school.activities.take(15),
                    "sportsFocus" to school.sportsFocus.take(12),
                    "feeMin" to safeFeeMin,
                    "feeMax" to safeFeeMax,
                    "feeCurrency" to school.feeCurrency.takeIf { it in SchoolConstants.FEE_CURRENCIES }.orEmpty().ifEmpty { "NGN" },
                    "feePeriod" to school.feePeriod.takeIf { it in SchoolConstants.FEE_PERIODS }.orEmpty().ifEmpty { "Per Term" },
                    "rankingScore" to school.rankingScore.coerceIn(0.0, 100.0),
                    "districtRank" to school.districtRank.coerceIn(1L, 9999L),
                    "rating" to school.rating.coerceIn(1.0, 5.0),
                    "tuitionTier" to school.tuitionTier.takeIf { it in SchoolConstants.TUITION_TIERS }.orEmpty().ifEmpty { "$$" },
                    "capacity" to safeCapacity,
                    "enrolledCount" to safeEnrolled,
                    "admissionsOpen" to school.admissionsOpen,
                    "verificationStatus" to safeVerifStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safePhone != null) updates["contactPhone"] = safePhone
                if (cleanExternalId != null) updates["externalPlaceId"] = cleanExternalId
                if (safeWebsite != null) updates["websiteUrl"] = safeWebsite
                if (safeVerifSource != null) updates["verificationSource"] = safeVerifSource
                docRef.update(updates).await()
            }
            Result.success(schoolId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSchoolVerification(
        schoolId: String,
        result: SchoolVerificationResult
    ): Result<Unit> {
        val docPath = "schools/$schoolId"
        return try {
            requireUserId()
            val safeStatus = result.status.takeIf {
                it in listOf("VERIFIED", "INTERNET_MATCHED", "UNVERIFIED")
            } ?: "UNVERIFIED"
            val updates = mutableMapOf<String, Any>(
                "verificationStatus" to safeStatus,
                "verificationSource" to result.source.trim().take(160),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            result.discoveredWebsite?.let { updates["websiteUrl"] = it.take(200) }
            result.matchedExternalId?.let { updates["externalPlaceId"] = it.take(128) }
            db.collection("schools").document(schoolId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitEnrollment(
        school: School,
        parentName: String,
        studentName: String,
        gradeApplying: String,
        notes: String?,
        paymentId: String? = null,
        paymentStatus: String? = null
    ): Result<String> {
        val enrollmentId = "enr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docPath = "enrollments/$enrollmentId"
        return try {
            val uid = requireUserId()
            val cleanStudent = studentName.trim().take(80)
            require(cleanStudent.isNotEmpty()) { "Student name is required" }
            val cleanGrade = gradeApplying.trim().take(30)
            require(cleanGrade.isNotEmpty()) { "Grade level is required" }
            val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(400)
            val safePayId = paymentId?.trim()?.takeIf { it.isNotEmpty() }?.take(128)
            val safePayStatus = paymentStatus?.takeIf { it in listOf("UNPAID", "PAID", "PENDING_VERIFICATION") }

            val payload = mapOf(
                "schoolId" to school.id,
                "schoolName" to school.name.take(100),
                "adminUid" to school.adminUid,
                "parentUid" to uid,
                "parentName" to parentName.trim().ifEmpty { "Parent" }.take(80),
                "studentName" to cleanStudent,
                "gradeApplying" to cleanGrade,
                "notes" to cleanNotes,
                "status" to "PENDING",
                "paymentId" to safePayId,
                "paymentStatus" to safePayStatus,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("enrollments").document(enrollmentId).set(payload).await()
            Result.success(enrollmentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateEnrollmentStatus(
        enrollment: Enrollment,
        newStatus: String,
        adminResponse: String?
    ): Result<Unit> {
        val docPath = "enrollments/${enrollment.id}"
        return try {
            val uid = requireUserId()
            require(enrollment.adminUid == uid) { "Only the school administrator can update this enrollment" }
            require(newStatus in listOf("APPROVED", "WAITLISTED", "DECLINED")) { "Invalid enrollment status" }

            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanResponse = adminResponse?.trim()?.takeIf { it.isNotEmpty() }?.take(300)
            if (cleanResponse != null) {
                updates["adminResponse"] = cleanResponse
            }
            db.collection("enrollments").document(enrollment.id).update(updates).await()

            if (newStatus == "APPROVED") {
                val studentId = "stu_${enrollment.id}"
                val studentRef = db.collection("students").document(studentId)
                val studentSnap = studentRef.get().await()
                if (!studentSnap.exists()) {
                    val studentPayload = mapOf(
                        "parentUid" to enrollment.parentUid,
                        "adminUid" to uid,
                        "schoolId" to enrollment.schoolId,
                        "schoolName" to enrollment.schoolName,
                        "studentName" to enrollment.studentName,
                        "gradeLevel" to enrollment.gradeApplying,
                        "attendanceRate" to 100.0,
                        "presentDays" to 1,
                        "absentDays" to 0,
                        "tardyDays" to 0,
                        "gpa" to 3.8,
                        "overallAverage" to 91.0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    studentRef.set(studentPayload).await()

                    val logId = "att_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                    studentRef.collection("attendance").document(logId).set(
                        mapOf(
                            "parentUid" to enrollment.parentUid,
                            "adminUid" to uid,
                            "dateLabel" to "Orientation Day",
                            "status" to "PRESENT",
                            "note" to "Enrolled and checked in",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()

                    val gradeId = "grd_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                    studentRef.collection("grades").document(gradeId).set(
                        mapOf(
                            "parentUid" to enrollment.parentUid,
                            "adminUid" to uid,
                            "subject" to "Placement Assessment",
                            "score" to 91.0,
                            "letterGrade" to "A",
                            "term" to "Term 1 2026",
                            "teacherRemark" to "Strong academic readiness",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                db.collection("schools").document(enrollment.schoolId).update(
                    mapOf(
                        "enrolledCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun logStudentAttendance(
        student: StudentRecord,
        dateLabel: String,
        status: String,
        note: String?
    ): Result<Unit> {
        val logId = "att_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "students/${student.id}/attendance/$logId"
        return try {
            val uid = requireUserId()
            require(student.adminUid == uid) { "Only the school administrator can log attendance" }
            require(status in listOf("PRESENT", "ABSENT", "TARDY", "EXCUSED")) { "Invalid attendance status" }
            val cleanDate = dateLabel.trim().take(40)
            require(cleanDate.isNotEmpty()) { "Date label is required" }

            val payload = mapOf(
                "parentUid" to student.parentUid,
                "adminUid" to uid,
                "dateLabel" to cleanDate,
                "status" to status,
                "note" to note?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("students").document(student.id)
                .collection("attendance").document(logId)
                .set(payload).await()

            val newPresent = student.presentDays + if (status == "PRESENT" || status == "EXCUSED") 1 else 0
            val newAbsent = student.absentDays + if (status == "ABSENT") 1 else 0
            val newTardy = student.tardyDays + if (status == "TARDY") 1 else 0
            val total = (newPresent + newAbsent + newTardy).coerceAtLeast(1L)
            val newRate = (((newPresent + newTardy * 0.5) / total.toDouble()) * 100.0).coerceIn(0.0, 100.0)

            db.collection("students").document(student.id).update(
                mapOf(
                    "presentDays" to newPresent,
                    "absentDays" to newAbsent,
                    "tardyDays" to newTardy,
                    "attendanceRate" to (newRate * 10.0).roundToInt() / 10.0,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addStudentGrade(
        student: StudentRecord,
        subject: String,
        score: Double,
        term: String,
        teacherRemark: String?
    ): Result<Unit> {
        val gradeId = "grd_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "students/${student.id}/grades/$gradeId"
        return try {
            val uid = requireUserId()
            require(student.adminUid == uid) { "Only the school administrator can record grades" }
            val cleanSubject = subject.trim().take(60)
            require(cleanSubject.isNotEmpty()) { "Subject is required" }
            val cleanTerm = term.trim().take(40)
            require(cleanTerm.isNotEmpty()) { "Term is required" }
            val clampedScore = score.coerceIn(0.0, 100.0)
            val letter = when {
                clampedScore >= 90 -> "A+"
                clampedScore >= 80 -> "A"
                clampedScore >= 75 -> "B+"
                clampedScore >= 70 -> "B"
                clampedScore >= 60 -> "C"
                clampedScore >= 50 -> "D"
                else -> "F"
            }

            val payload = mapOf(
                "parentUid" to student.parentUid,
                "adminUid" to uid,
                "subject" to cleanSubject,
                "score" to clampedScore,
                "letterGrade" to letter,
                "term" to cleanTerm,
                "teacherRemark" to teacherRemark?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val studentRef = db.collection("students").document(student.id)
            studentRef.collection("grades").document(gradeId).set(payload).await()

            val allGrades = studentRef.collection("grades")
                .whereEqualTo("adminUid", uid)
                .get().await()
                .toObjects(SubjectGrade::class.java)
            val avg = if (allGrades.isNotEmpty()) {
                allGrades.map { it.score }.average().coerceIn(0.0, 100.0)
            } else clampedScore
            val gpa = ((avg / 100.0) * 4.0).coerceIn(0.0, 4.0)

            studentRef.update(
                mapOf(
                    "overallAverage" to (avg * 10.0).roundToInt() / 10.0,
                    "gpa" to (gpa * 100.0).roundToInt() / 100.0,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun getOrCreateConversation(
        school: School,
        parentName: String
    ): Result<Conversation> {
        val uid = requireUserId()
        val convId = "${school.id}_$uid".take(128)
        val docPath = "conversations/$convId"
        return try {
            val docRef = db.collection("conversations").document(convId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val conv = existing.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (conv != null) return Result.success(conv)
            }
            val memberUids = listOf(uid, school.adminUid).distinct()
            // Note: rules require memberUids.size == 2 and parentUid in memberUids and adminUid in memberUids
            val finalMembers = if (memberUids.size == 2) memberUids else listOf(uid, school.adminUid)
            val payload = mapOf(
                "schoolId" to school.id,
                "schoolName" to school.name.take(100),
                "parentUid" to uid,
                "parentName" to parentName.trim().ifEmpty { "Parent" }.take(80),
                "adminUid" to school.adminUid,
                "adminName" to school.adminName.trim().ifEmpty { "School Admin" }.take(80),
                "memberUids" to finalMembers,
                "lastMessage" to "Conversation started",
                "lastSenderUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val created = docRef.get().await()
                .toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: Conversation(
                    id = convId,
                    schoolId = school.id,
                    schoolName = school.name,
                    parentUid = uid,
                    parentName = parentName,
                    adminUid = school.adminUid,
                    adminName = school.adminName,
                    memberUids = finalMembers,
                    lastMessage = "Conversation started",
                    lastSenderUid = uid
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversation: Conversation,
        senderName: String,
        senderRole: String,
        text: String
    ): Result<Unit> {
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "conversations/${conversation.id}/messages/$msgId"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(1000)
            require(cleanText.isNotEmpty()) { "Message cannot be empty" }
            val convRef = db.collection("conversations").document(conversation.id)
            val msgPayload = mapOf(
                "senderUid" to uid,
                "senderName" to senderName.trim().ifEmpty { "User" }.take(80),
                "senderRole" to if (senderRole == "admin") "admin" else "parent",
                "text" to cleanText,
                "memberUids" to conversation.memberUids,
                "createdAt" to FieldValue.serverTimestamp()
            )
            convRef.collection("messages").document(msgId).set(msgPayload).await()
            convRef.update(
                mapOf(
                    "lastMessage" to cleanText.take(500),
                    "lastSenderUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun publishAnnouncement(
        school: School,
        title: String,
        body: String,
        category: String,
        eventDate: String?
    ): Result<Unit> {
        val annId = "ann_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "announcements/$annId"
        return try {
            val uid = requireUserId()
            require(school.adminUid == uid) { "Only the school administrator can publish announcements" }
            val cleanTitle = title.trim().take(120)
            val cleanBody = body.trim().take(1000)
            require(cleanTitle.length >= 2 && cleanBody.length >= 2) { "Title and message are required" }
            val safeCategory = category.takeIf { it in SchoolConstants.ANNOUNCEMENT_CATEGORIES } ?: "Event"
            val cleanDate = eventDate?.trim()?.takeIf { it.isNotEmpty() }?.take(60)

            val payload = mapOf(
                "schoolId" to school.id,
                "schoolName" to school.name.take(100),
                "adminUid" to uid,
                "title" to cleanTitle,
                "body" to cleanBody,
                "category" to safeCategory,
                "eventDate" to cleanDate,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("announcements").document(annId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun seedSampleDirectoryAround(
        centerLat: Double,
        centerLng: Double,
        adminName: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val samples = listOf(
                School(
                    id = "sch_sample_greensprings_${uid.take(6)}",
                    adminUid = uid,
                    adminName = adminName,
                    name = "Greensprings Dual Heritage School",
                    description = "Blended Nigerian & British curriculum with Olympiad robotics, orchestra, and competitive swimming & football.",
                    address = "24 Admiralty Crescent",
                    latitude = centerLat + 0.012,
                    longitude = centerLng + 0.015,
                    curriculum = "Nigerian & British",
                    faithBased = false,
                    faithTradition = "Secular",
                    specialSchool = false,
                    specialNeedsSupport = "Inclusive Learning Support",
                    studentMix = "Mixed",
                    gradeLevels = listOf("Creche / Pre-K", "Primary", "Junior Secondary", "Senior Secondary"),
                    activities = listOf("Robotics", "Debate & MUN", "Orchestra & Music", "Coding Club"),
                    sportsFocus = listOf("Football (Soccer)", "Swimming", "Athletics & Track"),
                    feeMin = 550_000L,
                    feeMax = 1_150_000L,
                    feeCurrency = "NGN",
                    feePeriod = "Per Term",
                    rankingScore = 96.4,
                    districtRank = 1L,
                    rating = 4.9,
                    tuitionTier = "$$$",
                    capacity = 850L,
                    enrolledCount = 720L,
                    admissionsOpen = true,
                    contactPhone = "+234-800-111-2020"
                ),
                School(
                    id = "sch_sample_crescent_${uid.take(6)}",
                    adminUid = uid,
                    adminName = adminName,
                    name = "St. Jude Faith & STEM College",
                    description = "Faith-based Nigerian curriculum school fostering moral leadership, STEM clubs, and basketball excellence.",
                    address = "18 Heritage Boulevard",
                    latitude = centerLat - 0.018,
                    longitude = centerLng + 0.009,
                    curriculum = "Nigerian",
                    faithBased = true,
                    faithTradition = "Christian",
                    specialSchool = false,
                    specialNeedsSupport = "General Education",
                    studentMix = "Mixed",
                    gradeLevels = listOf("Primary", "Junior Secondary", "Senior Secondary"),
                    activities = listOf("STEM Olympiad", "Chess Club", "Drama & Theatre"),
                    sportsFocus = listOf("Basketball", "Football (Soccer)", "Table Tennis"),
                    feeMin = 220_000L,
                    feeMax = 450_000L,
                    feeCurrency = "NGN",
                    feePeriod = "Per Term",
                    rankingScore = 91.2,
                    districtRank = 3L,
                    rating = 4.7,
                    tuitionTier = "$",
                    capacity = 600L,
                    enrolledCount = 510L,
                    admissionsOpen = true,
                    contactPhone = "+234-800-222-3030"
                ),
                School(
                    id = "sch_sample_kensington_${uid.take(6)}",
                    adminUid = uid,
                    adminName = adminName,
                    name = "Kensington British Grammar",
                    description = "Full Cambridge IGCSE & A-Level British curriculum with lawn tennis academy and Model UN.",
                    address = "7 Victoria Gardens",
                    latitude = centerLat + 0.032,
                    longitude = centerLng - 0.025,
                    curriculum = "British",
                    faithBased = false,
                    faithTradition = "Secular",
                    specialSchool = false,
                    specialNeedsSupport = "General Education",
                    studentMix = "Girls Only",
                    gradeLevels = listOf("Primary", "Junior Secondary", "Senior Secondary", "Sixth Form"),
                    activities = listOf("Debate & MUN", "Visual Arts", "Orchestra & Music"),
                    sportsFocus = listOf("Lawn Tennis", "Swimming", "Badminton"),
                    feeMin = 900_000L,
                    feeMax = 1_850_000L,
                    feeCurrency = "NGN",
                    feePeriod = "Per Term",
                    rankingScore = 94.8,
                    districtRank = 2L,
                    rating = 4.8,
                    tuitionTier = "$$$",
                    capacity = 420L,
                    enrolledCount = 385L,
                    admissionsOpen = true,
                    contactPhone = "+234-800-333-4040"
                ),
                School(
                    id = "sch_sample_brightstar_${uid.take(6)}",
                    adminUid = uid,
                    adminName = adminName,
                    name = "BrightStar Special Needs Academy",
                    description = "Dedicated special education school offering individualized therapy, sensory classrooms, and adaptive athletics.",
                    address = "41 Willow Lane",
                    latitude = centerLat - 0.009,
                    longitude = centerLng - 0.019,
                    curriculum = "Nigerian & British",
                    faithBased = true,
                    faithTradition = "Interfaith",
                    specialSchool = true,
                    specialNeedsSupport = "Special Education (Autism, Speech & Sensory)",
                    studentMix = "Mixed",
                    gradeLevels = listOf("Creche / Pre-K", "Primary", "Junior Secondary"),
                    activities = listOf("Visual Arts", "Orchestra & Music", "Environmental Club"),
                    sportsFocus = listOf("Swimming", "Athletics & Track", "Table Tennis"),
                    feeMin = 380_000L,
                    feeMax = 680_000L,
                    feeCurrency = "NGN",
                    feePeriod = "Per Term",
                    rankingScore = 92.6,
                    districtRank = 4L,
                    rating = 4.9,
                    tuitionTier = "$$",
                    capacity = 180L,
                    enrolledCount = 142L,
                    admissionsOpen = true,
                    contactPhone = "+234-800-444-5050"
                ),
                School(
                    id = "sch_sample_university_${uid.take(6)}",
                    adminUid = uid,
                    adminName = adminName,
                    name = "Meridian Coastal University",
                    description = "Accredited Nigerian & British partner university offering NUC/international undergraduate and postgraduate degrees with varsity athletics.",
                    address = "102 University Expressway",
                    latitude = centerLat + 0.024,
                    longitude = centerLng + 0.028,
                    curriculum = "Nigerian & British",
                    institutionType = "University",
                    faithBased = false,
                    faithTradition = "Secular",
                    specialSchool = false,
                    specialNeedsSupport = "Inclusive Disability & Academic Support Unit",
                    studentMix = "Mixed",
                    gradeLevels = listOf("Undergraduate", "Postgraduate"),
                    coursesOffered = listOf("Computer Science", "Medicine & Surgery", "Law", "Accounting & Finance", "Nursing & Health Sciences", "Mass Communication"),
                    activities = listOf("Robotics", "Debate & MUN", "Entrepreneurship"),
                    sportsFocus = listOf("Football (Soccer)", "Basketball", "Athletics & Track"),
                    feeMin = 850_000L,
                    feeMax = 2_400_000L,
                    feeCurrency = "NGN",
                    feePeriod = "Per Year",
                    rankingScore = 95.8,
                    districtRank = 2L,
                    rating = 4.9,
                    tuitionTier = "$$$",
                    capacity = 4200L,
                    enrolledCount = 3650L,
                    admissionsOpen = true,
                    contactPhone = "+234-800-555-6060",
                    verificationStatus = "INTERNET_MATCHED",
                    verificationSource = "Accredited Higher Education Listing"
                ),
                School(
                    id = "sch_sample_polytechnic_${uid.take(6)}",
                    adminUid = uid,
                    adminName = adminName,
                    name = "Atlantic Technology Polytechnic",
                    description = "NBTE-aligned polytechnic running practical National Diploma (ND) and Higher National Diploma (HND) engineering & tech programmes.",
                    address = "15 Innovation Drive",
                    latitude = centerLat - 0.027,
                    longitude = centerLng + 0.021,
                    curriculum = "Nigerian",
                    institutionType = "Polytechnic",
                    faithBased = false,
                    faithTradition = "Secular",
                    specialSchool = false,
                    specialNeedsSupport = "General Education",
                    studentMix = "Mixed",
                    gradeLevels = listOf("ND / HND Programme"),
                    coursesOffered = listOf("Civil Engineering", "Electrical Electronics Eng.", "Computer Science", "Business Administration", "Agricultural Technology"),
                    activities = listOf("Coding Club", "Entrepreneurship", "STEM Olympiad"),
                    sportsFocus = listOf("Football (Soccer)", "Volleyball", "Table Tennis"),
                    feeMin = 180_000L,
                    feeMax = 380_000L,
                    feeCurrency = "NGN",
                    feePeriod = "Per Year",
                    rankingScore = 89.5,
                    districtRank = 5L,
                    rating = 4.6,
                    tuitionTier = "$",
                    capacity = 2500L,
                    enrolledCount = 2120L,
                    admissionsOpen = true,
                    contactPhone = "+234-800-666-7070",
                    verificationStatus = "INTERNET_MATCHED",
                    verificationSource = "Public Technical & Polytechnic Registry"
                ),
                School(
                    id = "sch_sample_coe_${uid.take(6)}",
                    adminUid = uid,
                    adminName = adminName,
                    name = "Heritage College of Education",
                    description = "Teacher-training college of education offering NCE and B.Ed programmes in pedagogy, early childhood, and STEM teaching.",
                    address = "88 Scholars Avenue",
                    latitude = centerLat + 0.014,
                    longitude = centerLng - 0.031,
                    curriculum = "Nigerian",
                    institutionType = "College of Education",
                    faithBased = false,
                    faithTradition = "Secular",
                    specialSchool = false,
                    specialNeedsSupport = "Special Education Teacher Training",
                    studentMix = "Mixed",
                    gradeLevels = listOf("NCE Programme", "Undergraduate"),
                    coursesOffered = listOf("Education & Pedagogy", "Early Childhood Education", "Sciences & Biotechnology", "Arts & Humanities"),
                    activities = listOf("Debate & MUN", "Drama & Theatre", "Environmental Club"),
                    sportsFocus = listOf("Athletics & Track", "Badminton", "Football (Soccer)"),
                    feeMin = 95_000L,
                    feeMax = 210_000L,
                    feeCurrency = "NGN",
                    feePeriod = "Per Year",
                    rankingScore = 88.0,
                    districtRank = 6L,
                    rating = 4.6,
                    tuitionTier = "$",
                    capacity = 1800L,
                    enrolledCount = 1490L,
                    admissionsOpen = true,
                    contactPhone = "+234-800-777-8080",
                    verificationStatus = "INTERNET_MATCHED",
                    verificationSource = "Teacher Education Directory Listing"
                )
            )

            samples.forEach { sample ->
                val ref = db.collection("schools").document(sample.id)
                if (!ref.get().await().exists()) {
                    val payload = mapOf(
                        "adminUid" to uid,
                        "adminName" to sample.adminName,
                        "name" to sample.name,
                        "description" to sample.description,
                        "address" to sample.address,
                        "latitude" to sample.latitude,
                        "longitude" to sample.longitude,
                        "curriculum" to sample.curriculum,
                        "institutionType" to sample.institutionType,
                        "faithBased" to sample.faithBased,
                        "faithTradition" to sample.faithTradition,
                        "specialSchool" to sample.specialSchool,
                        "specialNeedsSupport" to sample.specialNeedsSupport,
                        "studentMix" to sample.studentMix,
                        "gradeLevels" to sample.gradeLevels,
                        "coursesOffered" to sample.coursesOffered,
                        "activities" to sample.activities,
                        "sportsFocus" to sample.sportsFocus,
                        "feeMin" to sample.feeMin,
                        "feeMax" to sample.feeMax,
                        "feeCurrency" to sample.feeCurrency,
                        "feePeriod" to sample.feePeriod,
                        "rankingScore" to sample.rankingScore,
                        "districtRank" to sample.districtRank,
                        "rating" to sample.rating,
                        "tuitionTier" to sample.tuitionTier,
                        "capacity" to sample.capacity,
                        "enrolledCount" to sample.enrolledCount,
                        "admissionsOpen" to sample.admissionsOpen,
                        "contactPhone" to sample.contactPhone,
                        "verificationStatus" to sample.verificationStatus,
                        "verificationSource" to sample.verificationSource,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    ref.set(payload).await()
                }
            }

            val firstSample = samples.first()
            val annId = "ann_sample_${uid.take(8)}"
            val annRef = db.collection("announcements").document(annId)
            if (!annRef.get().await().exists()) {
                annRef.set(
                    mapOf(
                        "schoolId" to firstSample.id,
                        "schoolName" to firstSample.name,
                        "adminUid" to uid,
                        "title" to "Admissions Open House & Sports Showcase",
                        "body" to "Tour our dual Nigerian & British classrooms, robotics lab, and Olympic pool this Saturday.",
                        "category" to "Event",
                        "eventDate" to "Saturday • 10:00 AM",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "schools")
            Result.failure(e)
        }
    }

    fun observeAdverts(): Flow<List<Advert>> = flow {
        val uid = requireUserId()
        val path = "adverts"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Advert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createOrUpdateAdvert(advert: Advert, isNew: Boolean): Result<String> {
        val advertId = if (isNew || advert.id.isBlank()) {
            "adv_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        } else {
            advert.id
        }
        val docPath = "adverts/$advertId"
        return try {
            val uid = requireUserId()
            val cleanAdvertiser = advert.advertiserName.trim().take(80)
            require(cleanAdvertiser.isNotEmpty()) { "Advertiser name is required" }
            val cleanTitle = advert.title.trim().take(100)
            require(cleanTitle.length >= 2) { "Advert headline must be at least 2 characters" }
            val cleanBody = advert.body.trim().take(600)
            require(cleanBody.length >= 4) { "Advert description must be at least 4 characters" }
            val cleanLocation = advert.targetLocation.trim().take(120)
            require(cleanLocation.length >= 2) { "Target location is required" }
            val safeCategory = advert.category.takeIf { it in SchoolConstants.ADVERT_CATEGORIES } ?: "School Admissions"
            val safeCurr = advert.curriculumFocus.takeIf { it in SchoolConstants.ADVERT_CURRICULA } ?: "All"
            val matchedPlan = SchoolConstants.ADVERT_PLANS.firstOrNull { it.tier == advert.planTier }
                ?: SchoolConstants.ADVERT_PLANS[1]
            val cleanCta = advert.ctaLabel.trim().ifEmpty { "Learn More" }.take(40)
            val cleanContact = advert.contactInfo?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
            val safeStatus = advert.status.takeIf { it in listOf("ACTIVE", "PAUSED", "ENDED") } ?: "ACTIVE"
            val safePayId = advert.paymentId?.trim()?.takeIf { it.isNotEmpty() }?.take(128)
            val safePayStatus = advert.paymentStatus?.takeIf { it in listOf("UNPAID", "PAID", "PENDING_VERIFICATION") }

            val docRef = db.collection("adverts").document(advertId)
            if (isNew || advert.id.isBlank()) {
                val payload = mapOf(
                    "ownerUid" to uid,
                    "advertiserName" to cleanAdvertiser,
                    "title" to cleanTitle,
                    "body" to cleanBody,
                    "category" to safeCategory,
                    "curriculumFocus" to safeCurr,
                    "planTier" to matchedPlan.tier,
                    "planPriceLabel" to matchedPlan.priceLabel,
                    "durationDays" to matchedPlan.durationDays.toLong(),
                    "targetLocation" to cleanLocation,
                    "latitude" to advert.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to advert.longitude.coerceIn(-180.0, 180.0),
                    "ctaLabel" to cleanCta,
                    "contactInfo" to cleanContact,
                    "status" to safeStatus,
                    "paymentId" to safePayId,
                    "paymentStatus" to safePayStatus,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "advertiserName" to cleanAdvertiser,
                    "title" to cleanTitle,
                    "body" to cleanBody,
                    "category" to safeCategory,
                    "curriculumFocus" to safeCurr,
                    "planTier" to matchedPlan.tier,
                    "planPriceLabel" to matchedPlan.priceLabel,
                    "durationDays" to matchedPlan.durationDays.toLong(),
                    "targetLocation" to cleanLocation,
                    "latitude" to advert.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to advert.longitude.coerceIn(-180.0, 180.0),
                    "ctaLabel" to cleanCta,
                    "status" to safeStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanContact != null) updates["contactInfo"] = cleanContact
                if (safePayId != null) updates["paymentId"] = safePayId
                if (safePayStatus != null) updates["paymentStatus"] = safePayStatus
                docRef.update(updates).await()
            }
            Result.success(advertId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAdvertStatus(advertId: String, newStatus: String): Result<Unit> {
        val docPath = "adverts/$advertId"
        return try {
            requireUserId()
            require(newStatus in listOf("ACTIVE", "PAUSED", "ENDED")) { "Invalid advert status" }
            db.collection("adverts").document(advertId).update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAdvert(advertId: String): Result<Unit> {
        val docPath = "adverts/$advertId"
        return try {
            requireUserId()
            db.collection("adverts").document(advertId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedSampleAdvertsAround(centerLat: Double, centerLng: Double): Result<Unit> {
        return try {
            val uid = requireUserId()
            val samples = listOf(
                Advert(
                    id = "adv_sample_hero_${uid.take(6)}",
                    advertiserName = "Greensprings Dual Heritage",
                    title = "2026 Entrance Exam & Merit Scholarships",
                    body = "Register for Nigerian & British Dual Curriculum entry assessments. Up to 50% STEM & Varsity Sports scholarships available.",
                    category = "School Admissions",
                    curriculumFocus = "Nigerian & British",
                    planTier = "District Hero",
                    planPriceLabel = "₦75,000 / 30 days",
                    durationDays = 30L,
                    targetLocation = "Lekki & Victoria Island",
                    latitude = centerLat + 0.01,
                    longitude = centerLng + 0.01,
                    ctaLabel = "Inquire Admissions",
                    contactInfo = "admissions@greensprings-demo.edu.ng",
                    status = "ACTIVE"
                ),
                Advert(
                    id = "adv_sample_tutor_${uid.take(6)}",
                    advertiserName = "Progressive Tutors Collective",
                    title = "IGCSE & WAEC Intensive Online Bootcamps",
                    body = "Small-group and 1-on-1 live online tutoring in Cambridge Physics, Further Maths, and English Studies with certified examiners.",
                    category = "Progressive Tutors",
                    curriculumFocus = "Cambridge",
                    planTier = "Featured Spotlight",
                    planPriceLabel = "₦35,000 / 14 days",
                    durationDays = 14L,
                    targetLocation = "Lagos & Remote",
                    latitude = centerLat - 0.015,
                    longitude = centerLng + 0.012,
                    ctaLabel = "Book Online Trial",
                    contactInfo = "tutors@schoolcompass-demo.edu.ng",
                    status = "ACTIVE"
                ),
                Advert(
                    id = "adv_sample_sports_${uid.take(6)}",
                    advertiserName = "Kensington Swim & Tennis Academy",
                    title = "Summer Youth Aquatics & Coding Camp",
                    body = "Weekend lawn tennis, Olympic swimming coaching, and robotics camp open to Primary & Secondary students.",
                    category = "Sports & Camps",
                    curriculumFocus = "British",
                    planTier = "Starter Boost",
                    planPriceLabel = "₦15,000 / 7 days",
                    durationDays = 7L,
                    targetLocation = "Ikoyi & Victoria Island",
                    latitude = centerLat + 0.025,
                    longitude = centerLng - 0.02,
                    ctaLabel = "Reserve Spot",
                    contactInfo = "camps@kensington-demo.edu.ng",
                    status = "ACTIVE"
                )
            )

            samples.forEach { sample ->
                val ref = db.collection("adverts").document(sample.id)
                if (!ref.get().await().exists()) {
                    val payload = mapOf(
                        "ownerUid" to uid,
                        "advertiserName" to sample.advertiserName,
                        "title" to sample.title,
                        "body" to sample.body,
                        "category" to sample.category,
                        "curriculumFocus" to sample.curriculumFocus,
                        "planTier" to sample.planTier,
                        "planPriceLabel" to sample.planPriceLabel,
                        "durationDays" to sample.durationDays,
                        "targetLocation" to sample.targetLocation,
                        "latitude" to sample.latitude,
                        "longitude" to sample.longitude,
                        "ctaLabel" to sample.ctaLabel,
                        "contactInfo" to sample.contactInfo,
                        "status" to sample.status,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    ref.set(payload).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "adverts")
            Result.failure(e)
        }
    }

    fun observeTutors(): Flow<List<ProgressiveTutor>> = flow {
        val uid = requireUserId()
        val path = "tutors"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ProgressiveTutor::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.rating }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createOrUpdateTutor(tutor: ProgressiveTutor, isNew: Boolean): Result<String> {
        val tutorId = if (isNew || tutor.id.isBlank()) {
            "tut_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        } else {
            tutor.id
        }
        val docPath = "tutors/$tutorId"
        return try {
            val uid = requireUserId()
            val cleanName = tutor.fullName.trim().take(80)
            require(cleanName.length >= 2) { "Teacher name must be at least 2 characters" }
            val safeSex = tutor.sex.takeIf { it in SchoolConstants.TUTOR_SEX_OPTIONS } ?: "Female"
            val cleanHeadline = tutor.headline.trim().take(120)
            require(cleanHeadline.length >= 2) { "Headline is required" }
            val cleanCambridge = tutor.cambridgeSubjects.distinct().take(15)
            val cleanNigerian = tutor.nigerianSubjects.distinct().take(15)
            val combinedSubjects = (cleanCambridge + cleanNigerian + tutor.allSubjects).distinct().take(30)
            require(combinedSubjects.isNotEmpty()) { "Select at least one Cambridge or Nigerian subject" }
            val tracks = buildList {
                if (cleanCambridge.isNotEmpty()) add("Cambridge")
                if (cleanNigerian.isNotEmpty()) add("Nigerian")
                if (isEmpty()) addAll(tutor.curriculumTracks)
                if (isEmpty()) add("Cambridge")
            }.distinct().take(4)

            val docRef = db.collection("tutors").document(tutorId)
            if (isNew || tutor.id.isBlank()) {
                val payload = mapOf(
                    "teacherUid" to uid,
                    "fullName" to cleanName,
                    "sex" to safeSex,
                    "headline" to cleanHeadline,
                    "bio" to tutor.bio.trim().take(600),
                    "curriculumTracks" to tracks,
                    "cambridgeSubjects" to cleanCambridge,
                    "nigerianSubjects" to cleanNigerian,
                    "allSubjects" to combinedSubjects,
                    "qualifications" to tutor.qualifications.trim().ifEmpty { "Certified Educator" }.take(120),
                    "yearsExperience" to tutor.yearsExperience.coerceIn(0L, 60L),
                    "hourlyRate" to tutor.hourlyRate.coerceIn(0L, 500_000L),
                    "currency" to tutor.currency.takeIf { it in SchoolConstants.FEE_CURRENCIES }.orEmpty().ifEmpty { "NGN" },
                    "meetingPlatform" to tutor.meetingPlatform.trim().ifEmpty { "Google Meet" }.take(50),
                    "availability" to tutor.availability.trim().ifEmpty { "Weekdays 4 PM – 8 PM" }.take(120),
                    "locationLabel" to tutor.locationLabel.trim().ifEmpty { "Lagos" }.take(120),
                    "latitude" to tutor.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to tutor.longitude.coerceIn(-180.0, 180.0),
                    "rating" to tutor.rating.coerceIn(1.0, 5.0),
                    "onlineActive" to tutor.onlineActive,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "fullName" to cleanName,
                    "sex" to safeSex,
                    "headline" to cleanHeadline,
                    "bio" to tutor.bio.trim().take(600),
                    "curriculumTracks" to tracks,
                    "cambridgeSubjects" to cleanCambridge,
                    "nigerianSubjects" to cleanNigerian,
                    "allSubjects" to combinedSubjects,
                    "qualifications" to tutor.qualifications.trim().ifEmpty { "Certified Educator" }.take(120),
                    "yearsExperience" to tutor.yearsExperience.coerceIn(0L, 60L),
                    "hourlyRate" to tutor.hourlyRate.coerceIn(0L, 500_000L),
                    "currency" to tutor.currency.takeIf { it in SchoolConstants.FEE_CURRENCIES }.orEmpty().ifEmpty { "NGN" },
                    "meetingPlatform" to tutor.meetingPlatform.trim().ifEmpty { "Google Meet" }.take(50),
                    "availability" to tutor.availability.trim().ifEmpty { "Weekdays 4 PM – 8 PM" }.take(120),
                    "locationLabel" to tutor.locationLabel.trim().ifEmpty { "Lagos" }.take(120),
                    "latitude" to tutor.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to tutor.longitude.coerceIn(-180.0, 180.0),
                    "rating" to tutor.rating.coerceIn(1.0, 5.0),
                    "onlineActive" to tutor.onlineActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(tutorId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeClientTutorSessions(userId: String): Flow<List<TutorSession>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "tutor_sessions"
        emitAll(
            db.collection(path)
                .whereEqualTo("clientUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TutorSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeTeacherTutorSessions(userId: String): Flow<List<TutorSession>> = flow {
        val uid = requireUserId()
        if (uid != userId) throw IllegalStateException("User session mismatch")
        val path = "tutor_sessions"
        emitAll(
            db.collection(path)
                .whereEqualTo("teacherUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TutorSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun bookTutorSession(
        tutor: ProgressiveTutor,
        clientName: String,
        studentName: String,
        curriculumTrack: String,
        subject: String,
        preferredSchedule: String,
        notes: String?,
        paymentId: String? = null,
        paymentStatus: String? = null
    ): Result<String> {
        val sessionId = "ses_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "tutor_sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val cleanStudent = studentName.trim().take(80)
            require(cleanStudent.isNotEmpty()) { "Student name is required" }
            val cleanSubject = subject.trim().take(80)
            require(cleanSubject.length >= 2) { "Subject is required" }
            val cleanSchedule = preferredSchedule.trim().take(100)
            require(cleanSchedule.length >= 2) { "Preferred schedule is required" }
            val safeTrack = curriculumTrack.takeIf { it in SchoolConstants.TUTOR_CURRICULUM_TRACKS }
                ?: "Cambridge & Nigerian"
            val safePayId = paymentId?.trim()?.takeIf { it.isNotEmpty() }?.take(128)
            val safePayStatus = paymentStatus?.takeIf { it in listOf("UNPAID", "PAID", "PENDING_VERIFICATION") }

            val payload = mapOf(
                "tutorId" to tutor.id,
                "tutorName" to tutor.fullName.take(80),
                "teacherUid" to tutor.teacherUid,
                "clientUid" to uid,
                "clientName" to clientName.trim().ifEmpty { "Parent" }.take(80),
                "studentName" to cleanStudent,
                "curriculumTrack" to safeTrack,
                "subject" to cleanSubject,
                "preferredSchedule" to cleanSchedule,
                "notes" to notes?.trim()?.takeIf { it.isNotEmpty() }?.take(400),
                "status" to "REQUESTED",
                "paymentId" to safePayId,
                "paymentStatus" to safePayStatus,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("tutor_sessions").document(sessionId).set(payload).await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTutorSessionStatus(
        session: TutorSession,
        newStatus: String,
        meetingLink: String?
    ): Result<Unit> {
        val path = "tutor_sessions/${session.id}"
        return try {
            val uid = requireUserId()
            require(newStatus in listOf("CONFIRMED", "COMPLETED", "CANCELLED")) { "Invalid session status" }
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanLink = meetingLink?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
            if (cleanLink != null && session.teacherUid == uid) {
                updates["meetingLink"] = cleanLink
            }
            db.collection("tutor_sessions").document(session.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun seedSampleTutorsAround(centerLat: Double, centerLng: Double): Result<Unit> {
        return try {
            val uid = requireUserId()
            val samples = listOf(
                ProgressiveTutor(
                    id = "tut_sample_funke_${uid.take(6)}",
                    teacherUid = uid,
                    fullName = "Mrs. Funke Adeyemi",
                    sex = "Female",
                    headline = "Cambridge IGCSE & WAEC Dual Mathematics Tutor",
                    bio = "12 years preparing students for Cambridge Checkpoint/IGCSE and Nigerian WAEC/NECO Distinctions in interactive online classrooms.",
                    curriculumTracks = listOf("Cambridge", "Nigerian"),
                    cambridgeSubjects = listOf("Cambridge IGCSE Mathematics", "Cambridge Physics"),
                    nigerianSubjects = listOf("WAEC / NECO Mathematics", "Further Mathematics"),
                    allSubjects = listOf("Cambridge IGCSE Mathematics", "Cambridge Physics", "WAEC / NECO Mathematics", "Further Mathematics"),
                    qualifications = "M.Sc. Mathematics • Cambridge Certified & TRCN",
                    yearsExperience = 12L,
                    hourlyRate = 8500L,
                    currency = "NGN",
                    meetingPlatform = "Google Meet",
                    availability = "Mon–Sat • 4:00 PM – 8:30 PM",
                    locationLabel = "Lekki Phase 1",
                    latitude = centerLat + 0.011,
                    longitude = centerLng + 0.014,
                    rating = 4.9,
                    onlineActive = true
                ),
                ProgressiveTutor(
                    id = "tut_sample_chidi_${uid.take(6)}",
                    teacherUid = uid,
                    fullName = "Mr. Chidi Okafor",
                    sex = "Male",
                    headline = "Cambridge Computer Science & Nigerian STEM Mentor",
                    bio = "Software engineer & examiner helping secondary students master Python, algorithms, Cambridge CS, and Basic Science & Tech.",
                    curriculumTracks = listOf("Cambridge", "Nigerian"),
                    cambridgeSubjects = listOf("Cambridge Computer Science", "Cambridge Chemistry"),
                    nigerianSubjects = listOf("Basic Science & Tech", "WAEC / NECO Mathematics"),
                    allSubjects = listOf("Cambridge Computer Science", "Cambridge Chemistry", "Basic Science & Tech", "WAEC / NECO Mathematics"),
                    qualifications = "B.Eng. Computer Engineering • Microsoft Educator",
                    yearsExperience = 8L,
                    hourlyRate = 7500L,
                    currency = "NGN",
                    meetingPlatform = "Zoom",
                    availability = "Tue–Sun • 3:30 PM – 9:00 PM",
                    locationLabel = "Victoria Island",
                    latitude = centerLat - 0.019,
                    longitude = centerLng - 0.011,
                    rating = 4.8,
                    onlineActive = true
                ),
                ProgressiveTutor(
                    id = "tut_sample_aisha_${uid.take(6)}",
                    teacherUid = uid,
                    fullName = "Dr. Aisha Bello",
                    sex = "Female",
                    headline = "Cambridge Biology & Nigerian Agricultural Science",
                    bio = "PhD educator offering structured 1-on-1 lab simulations, past-question drills, and exam strategy sessions online.",
                    curriculumTracks = listOf("Cambridge", "Nigerian"),
                    cambridgeSubjects = listOf("Cambridge Biology", "Cambridge Chemistry"),
                    nigerianSubjects = listOf("Agricultural Science", "Basic Science & Tech"),
                    allSubjects = listOf("Cambridge Biology", "Cambridge Chemistry", "Agricultural Science", "Basic Science & Tech"),
                    qualifications = "Ph.D. Biological Sciences • IGCSE Examiner",
                    yearsExperience = 10L,
                    hourlyRate = 9000L,
                    currency = "NGN",
                    meetingPlatform = "Google Meet",
                    availability = "Mon–Fri • 5:00 PM – 8:00 PM",
                    locationLabel = "Ikeja GRA",
                    latitude = centerLat + 0.045,
                    longitude = centerLng - 0.032,
                    rating = 4.9,
                    onlineActive = true
                ),
                ProgressiveTutor(
                    id = "tut_sample_tunde_${uid.take(6)}",
                    teacherUid = uid,
                    fullName = "Mr. Tunde Balogun",
                    sex = "Male",
                    headline = "English Studies, Civic Education & Cambridge Economics",
                    bio = "Dedicated humanities & essay coaching specialist for Nigerian BECE/WAEC and Cambridge IGCSE English & Economics.",
                    curriculumTracks = listOf("Cambridge", "Nigerian"),
                    cambridgeSubjects = listOf("Cambridge English Language", "Cambridge Economics", "Cambridge Accounting"),
                    nigerianSubjects = listOf("English Studies", "Civic Education", "Nigerian History & Govt"),
                    allSubjects = listOf("Cambridge English Language", "Cambridge Economics", "Cambridge Accounting", "English Studies", "Civic Education", "Nigerian History & Govt"),
                    qualifications = "M.A. English Education • IELTS & IGCSE Coach",
                    yearsExperience = 7L,
                    hourlyRate = 6500L,
                    currency = "NGN",
                    meetingPlatform = "SchoolCompass Live",
                    availability = "Daily • 2:00 PM – 7:00 PM",
                    locationLabel = "Surulere / Ikoyi",
                    latitude = centerLat - 0.034,
                    longitude = centerLng + 0.029,
                    rating = 4.7,
                    onlineActive = true
                )
            )

            samples.forEach { sample ->
                val ref = db.collection("tutors").document(sample.id)
                if (!ref.get().await().exists()) {
                    val payload = mapOf(
                        "teacherUid" to uid,
                        "fullName" to sample.fullName,
                        "sex" to sample.sex,
                        "headline" to sample.headline,
                        "bio" to sample.bio,
                        "curriculumTracks" to sample.curriculumTracks,
                        "cambridgeSubjects" to sample.cambridgeSubjects,
                        "nigerianSubjects" to sample.nigerianSubjects,
                        "allSubjects" to sample.allSubjects,
                        "qualifications" to sample.qualifications,
                        "yearsExperience" to sample.yearsExperience,
                        "hourlyRate" to sample.hourlyRate,
                        "currency" to sample.currency,
                        "meetingPlatform" to sample.meetingPlatform,
                        "availability" to sample.availability,
                        "locationLabel" to sample.locationLabel,
                        "latitude" to sample.latitude,
                        "longitude" to sample.longitude,
                        "rating" to sample.rating,
                        "onlineActive" to sample.onlineActive,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    ref.set(payload).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "tutors")
            Result.failure(e)
        }
    }
}
