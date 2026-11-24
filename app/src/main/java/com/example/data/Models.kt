package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object SchoolConstants {
    val CURRICULA = listOf(
        "Nigerian",
        "British",
        "Nigerian & British",
        "IB",
        "American",
        "Montessori",
        "STEM"
    )

    val STUDENT_MIX_OPTIONS = listOf("Mixed", "Boys Only", "Girls Only")
    val BOARDING_OPTIONS = listOf("Day", "Boarding", "Day & Boarding", "Not Specified")
    val FEE_CURRENCIES = listOf("NGN", "USD", "GBP", "EUR")
    val FEE_PERIODS = listOf("Per Term", "Per Year")
    val TUITION_TIERS = listOf("Public / Free", "$", "$$", "$$$")
    val GRADE_LEVELS = listOf("Creche / Pre-K", "Primary", "Junior Secondary", "Senior Secondary", "Sixth Form")
    val TERTIARY_PROGRAMMES = listOf("NCE Programme", "ND / HND Programme", "Undergraduate", "Postgraduate")
    val ALL_PROGRAMMES = GRADE_LEVELS + TERTIARY_PROGRAMMES

    val INSTITUTION_TYPES = listOf(
        "Primary & Secondary",
        "University",
        "Polytechnic",
        "College of Education",
        "Technical / Vocational"
    )

    val COURSES_OFFERED_OPTIONS = listOf(
        "Computer Science",
        "Medicine & Surgery",
        "Civil Engineering",
        "Electrical Electronics Eng.",
        "Education & Pedagogy",
        "Early Childhood Education",
        "Law",
        "Accounting & Finance",
        "Business Administration",
        "Nursing & Health Sciences",
        "Mass Communication",
        "Agricultural Technology",
        "Sciences & Biotechnology",
        "Arts & Humanities"
    )

    val EXTRACURRICULAR_ACTIVITIES = listOf(
        "Robotics",
        "Debate & MUN",
        "Coding Club",
        "Orchestra & Music",
        "Visual Arts",
        "Drama & Theatre",
        "Chess Club",
        "STEM Olympiad",
        "Environmental Club",
        "Entrepreneurship"
    )

    val SPORTS_FOCUS_OPTIONS = listOf(
        "Football (Soccer)",
        "Basketball",
        "Athletics & Track",
        "Swimming",
        "Lawn Tennis",
        "Table Tennis",
        "Badminton",
        "Cricket",
        "Volleyball",
        "Martial Arts"
    )

    val ANNOUNCEMENT_CATEGORIES = listOf("Urgent", "Event", "Academic", "Admissions")

    val TUTOR_CURRICULUM_TRACKS = listOf("Cambridge", "Nigerian", "Cambridge & Nigerian")
    val TUTOR_SEX_OPTIONS = listOf("Female", "Male")
    val MEETING_PLATFORMS = listOf("Google Meet", "Zoom", "Microsoft Teams", "Skolquik Live")

    val CAMBRIDGE_SUBJECTS = listOf(
        "Cambridge IGCSE Mathematics",
        "Cambridge Physics",
        "Cambridge Chemistry",
        "Cambridge Biology",
        "Cambridge English Language",
        "Cambridge Computer Science",
        "Cambridge Economics",
        "Cambridge Accounting"
    )

    val NIGERIAN_SUBJECTS = listOf(
        "WAEC / NECO Mathematics",
        "English Studies",
        "Further Mathematics",
        "Civic Education",
        "Nigerian History & Govt",
        "Basic Science & Tech",
        "Agricultural Science",
        "Yoruba / Igbo / Hausa"
    )

    val ADVERT_CATEGORIES = listOf(
        "School Admissions",
        "Progressive Tutors",
        "Exam Prep",
        "Sports & Camps",
        "Educational Resources"
    )

    val ADVERT_CURRICULA = listOf("All", "Nigerian", "British", "Cambridge", "Nigerian & British")

    const val PLATFORM_ADMIN_EMAIL = "elebutequam413@gmail.com"
    const val DEFAULT_APPLICATION_FEE_NGN = 15_000L

    val PAYMENT_TYPES = listOf(
        "SCHOOL_APPLICATION_FEE",
        "TUITION_DEPOSIT",
        "ADVERT_CAMPAIGN",
        "TUTOR_SESSION"
    )

    val PAYMENT_METHODS = listOf(
        "Card Payment",
        "Bank Transfer",
        "USSD / Instant Transfer",
        "Mobile Wallet"
    )

    val GATEWAY_PROVIDERS = listOf(
        "Paystack",
        "Flutterwave",
        "Stripe",
        "Direct Bank"
    )

    data class AdvertPlan(
        val tier: String,
        val priceLabel: String,
        val amountNgn: Long,
        val durationDays: Int,
        val summary: String
    )

    val ADVERT_PLANS = listOf(
        AdvertPlan("Starter Boost", "₦15,000 / 7 days", 15_000L, 7, "Directory badge + regional feed card"),
        AdvertPlan("Featured Spotlight", "₦35,000 / 14 days", 35_000L, 14, "Top carousel spotlight + map priority"),
        AdvertPlan("District Hero", "₦75,000 / 30 days", 75_000L, 30, "Pinned banner across Discover & Tutors")
    )

    data class PresetLocation(val label: String, val lat: Double, val lng: Double)

    val PRESET_LOCATIONS = listOf(
        PresetLocation("Lekki Phase 1, Lagos", 6.4474, 3.4737),
        PresetLocation("Ikorodu, Lagos", 6.6194, 3.5105),
        PresetLocation("Victoria Island, Lagos", 6.4281, 3.4219),
        PresetLocation("Ikeja GRA, Lagos", 6.5833, 3.3500),
        PresetLocation("Maitama, Abuja", 9.0882, 7.4934),
        PresetLocation("Central London, UK", 51.5074, -0.1278)
    )
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "parent",
    val displayName: String = "",
    val email: String = "",
    val phone: String? = null,
    val homeAddress: String? = null,
    val homeLat: Double = 6.4474,
    val homeLng: Double = 3.4737,
    val searchRadiusKm: Long = 15L,
    val childSummary: String? = null,
    val favoriteSchoolIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class School(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminName: String = "",
    val name: String = "",
    val description: String = "",
    val address: String = "",
    val latitude: Double = 6.4474,
    val longitude: Double = 3.4737,
    val curriculum: String = "Nigerian & British",
    val institutionType: String = "Primary & Secondary",
    val faithBased: Boolean = false,
    val faithTradition: String = "Secular",
    val specialSchool: Boolean = false,
    val specialNeedsSupport: String = "General Education",
    val studentMix: String = "Mixed",
    val gradeLevels: List<String> = emptyList(),
    val coursesOffered: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val sportsFocus: List<String> = emptyList(),
    val feeMin: Long = 0L,
    val feeMax: Long = 0L,
    val feeCurrency: String = "NGN",
    val feePeriod: String = "Per Term",
    val rankingScore: Double = 85.0,
    val districtRank: Long = 10L,
    val rating: Double = 4.5,
    val tuitionTier: String = "$$",
    val capacity: Long = 500L,
    val enrolledCount: Long = 0L,
    val admissionsOpen: Boolean = true,
    val contactPhone: String? = null,
    val externalPlaceId: String? = null,
    val websiteUrl: String? = null,
    val verificationStatus: String = "UNVERIFIED",
    val verificationSource: String? = null,
    val sourceCitations: List<String> = emptyList(),
    val googleMapsPlaceRef: String? = null,
    val googleMapsUrl: String? = null,
    val admissionWindow: String? = null,
    val boardingOption: String? = null,
    val photoUrls: List<String> = emptyList(),
    val extraNotes: String? = null,
    val lastEnrichedByName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun hasPlatformAdmin(): Boolean = adminUid.isNotBlank()

    fun isVerifiedOnline(): Boolean =
        verificationStatus in listOf("VERIFIED", "VERIFIED_ONLINE", "INTERNET_MATCHED")

    fun verificationBadgeLabel(): String = when (verificationStatus) {
        "VERIFIED" -> "Verified (Admin & Online)"
        "VERIFIED_ONLINE" -> "Verified Online"
        "INTERNET_MATCHED" -> "Verified Online"
        else -> "Pending Verification"
    }

    fun parsedCitations(): List<GroundingSourceLink> {
        val list = mutableListOf<GroundingSourceLink>()
        val seenUris = mutableSetOf<String>()
        sourceCitations.forEach { raw ->
            val parts = raw.split(" | ", limit = 2)
            val uri = if (parts.size == 2) parts[1].trim() else raw.trim()
            val titleRaw = if (parts.size == 2) parts[0].trim() else uri
            val isMaps = titleRaw.startsWith("[Google Maps]", ignoreCase = true) ||
                uri.contains("google.com/maps", ignoreCase = true) ||
                uri.contains("maps.google", ignoreCase = true)
            val sourceType = if (isMaps) "Google Maps" else "Google Search"
            val cleanTitle = titleRaw
                .removePrefix("[Google Maps]")
                .removePrefix("[Google Search]")
                .removePrefix("[Web]")
                .trim()
                .ifEmpty { if (isMaps) "Google Maps Place" else "Online Source" }
            if ((uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) &&
                seenUris.add(uri)
            ) {
                list.add(
                    GroundingSourceLink(
                        title = cleanTitle.take(80),
                        uri = uri.take(300),
                        sourceType = sourceType,
                        placeId = if (isMaps) googleMapsPlaceRef else null
                    )
                )
            }
        }
        val mapsLink = googleMapsUrl?.trim()
        if (!mapsLink.isNullOrBlank() &&
            (mapsLink.startsWith("http://", ignoreCase = true) || mapsLink.startsWith("https://", ignoreCase = true)) &&
            seenUris.add(mapsLink)
        ) {
            list.add(
                GroundingSourceLink(
                    title = googleMapsPlaceRef?.let { "Google Maps ($it)" } ?: "Google Maps Listing",
                    uri = mapsLink.take(300),
                    sourceType = "Google Maps",
                    placeId = googleMapsPlaceRef
                )
            )
        }
        val site = websiteUrl?.trim()
        if (!site.isNullOrBlank() &&
            (site.startsWith("http://", ignoreCase = true) || site.startsWith("https://", ignoreCase = true)) &&
            seenUris.add(site)
        ) {
            list.add(
                GroundingSourceLink(
                    title = "Official Website",
                    uri = site.take(300),
                    sourceType = "Official Site"
                )
            )
        }
        return list
    }

    fun primarySourceSummary(): String {
        val rawSource = verificationSource?.trim().orEmpty()
        val ref = googleMapsPlaceRef?.trim().orEmpty()
        return when {
            rawSource.isNotBlank() && ref.isNotBlank() -> "$rawSource • Maps Ref"
            rawSource.isNotBlank() -> rawSource
            ref.isNotBlank() -> "Google Maps ($ref)"
            isVerifiedOnline() -> "Google Search & Maps Grounding"
            else -> "Pending verification"
        }
    }

    fun formattedFeeRange(): String {
        if (!hasPlatformAdmin() && feeMin == 0L && feeMax == 0L) return "Fees not added yet"
        if (feeMin == 0L && feeMax == 0L) return "Public / Free"
        val symbol = when (feeCurrency) {
            "NGN" -> "₦"
            "USD" -> "$"
            "GBP" -> "£"
            "EUR" -> "€"
            else -> "$feeCurrency "
        }
        return "$symbol${formatCompactAmount(feeMin)} – $symbol${formatCompactAmount(feeMax)} / ${if (feePeriod == "Per Term") "term" else "yr"}"
    }
}

data class GroundingSourceLink(
    val title: String,
    val uri: String,
    val sourceType: String,
    val placeId: String? = null
) {
    fun toCitationString(): String = "[$sourceType] ${title.trim().take(80)} | ${uri.trim().take(240)}"
}

fun formatCompactAmount(amount: Long): String = when {
    amount >= 1_000_000L -> {
        val m = amount / 1_000_000.0
        if (m % 1.0 == 0.0) "${m.toLong()}M" else String.format("%.1fM", m)
    }
    amount >= 1_000L -> "${amount / 1_000L}k"
    else -> amount.toString()
}

fun formatCurrencyAmount(amount: Long, currency: String): String {
    val symbol = when (currency) {
        "NGN" -> "₦"
        "USD" -> "$"
        "GBP" -> "£"
        "EUR" -> "€"
        else -> "$currency "
    }
    return "$symbol${java.text.NumberFormat.getIntegerInstance().format(amount)}"
}

@Keep
@IgnoreExtraProperties
data class Enrollment(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val adminUid: String = "",
    val parentUid: String = "",
    val parentName: String = "",
    val studentName: String = "",
    val gradeApplying: String = "",
    val notes: String? = null,
    val status: String = "PENDING",
    val adminResponse: String? = null,
    val paymentId: String? = null,
    val paymentStatus: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudentRecord(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val adminUid: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val studentName: String = "",
    val gradeLevel: String = "",
    val attendanceRate: Double = 100.0,
    val presentDays: Long = 0L,
    val absentDays: Long = 0L,
    val tardyDays: Long = 0L,
    val gpa: Double = 0.0,
    val overallAverage: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AttendanceLog(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val adminUid: String = "",
    val dateLabel: String = "",
    val status: String = "PRESENT",
    val note: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SubjectGrade(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val adminUid: String = "",
    val subject: String = "",
    val score: Double = 0.0,
    val letterGrade: String = "A",
    val term: String = "Fall 2026",
    val teacherRemark: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val parentUid: String = "",
    val parentName: String = "",
    val adminUid: String = "",
    val adminName: String = "",
    val memberUids: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastSenderUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "parent",
    val text: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val adminUid: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "Event",
    val eventDate: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class SortOption(val label: String) {
    NEAREST("Nearest"),
    HIGHEST_RANKED("Top Ranked"),
    TOP_RATED("Highest Rated"),
    LOWEST_FEE("Lowest Fees")
}

data class SchoolSearchFilter(
    val query: String = "",
    val centerLat: Double = 6.4474,
    val centerLng: Double = 3.4737,
    val centerLabel: String = "Lekki Phase 1, Lagos",
    val radiusKm: Int = 15,
    val limitByRadius: Boolean = true,
    val lockToNigeria: Boolean = true,
    val showNigeriaStates: Boolean = true,
    val userGpsLat: Double? = null,
    val userGpsLng: Double? = null,
    val institutionType: String? = null,
    val curriculum: String? = null,
    val faithFilter: FaithFilter = FaithFilter.ALL,
    val specialSchoolFilter: SpecialFilter = SpecialFilter.ALL,
    val studentMix: String? = null,
    val selectedProgrammes: Set<String> = emptySet(),
    val selectedCourses: Set<String> = emptySet(),
    val selectedActivities: Set<String> = emptySet(),
    val selectedSports: Set<String> = emptySet(),
    val minRankingScore: Double = 0.0,
    val maxFeeBudget: Long? = null,
    val onlyFavorites: Boolean = false,
    val sortBy: SortOption = SortOption.NEAREST
)

enum class FaithFilter(val label: String) {
    ALL("All Faiths"),
    FAITH_BASED("Faith-Based"),
    SECULAR("Secular")
}

enum class SpecialFilter(val label: String) {
    ALL("All Programs"),
    SPECIAL_ONLY("Special Needs"),
    GENERAL_ONLY("General Ed")
}

data class SchoolWithDistance(
    val school: School,
    val distanceKm: Double
)

@Keep
@IgnoreExtraProperties
data class Advert(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val advertiserName: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "School Admissions",
    val curriculumFocus: String = "All",
    val planTier: String = "Featured Spotlight",
    val planPriceLabel: String = "₦35,000 / 14 days",
    val durationDays: Long = 14L,
    val targetLocation: String = "Lekki Phase 1, Lagos",
    val latitude: Double = 6.4474,
    val longitude: Double = 3.4737,
    val ctaLabel: String = "Learn More",
    val contactInfo: String? = null,
    val status: String = "ACTIVE",
    val paymentId: String? = null,
    val paymentStatus: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdvertInquiry(
    @DocumentId val id: String = "",
    val advertId: String = "",
    val advertTitle: String = "",
    val ownerUid: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderEmail: String = "",
    val phone: String? = null,
    val message: String = "",
    val status: String = "NEW",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class AdvertWithDistance(
    val advert: Advert,
    val distanceKm: Double
)

@Keep
@IgnoreExtraProperties
data class ProgressiveTutor(
    @DocumentId val id: String = "",
    val teacherUid: String = "",
    val fullName: String = "",
    val sex: String = "Female",
    val headline: String = "",
    val bio: String = "",
    val curriculumTracks: List<String> = emptyList(),
    val cambridgeSubjects: List<String> = emptyList(),
    val nigerianSubjects: List<String> = emptyList(),
    val allSubjects: List<String> = emptyList(),
    val qualifications: String = "",
    val yearsExperience: Long = 3L,
    val hourlyRate: Long = 6000L,
    val currency: String = "NGN",
    val meetingPlatform: String = "Google Meet",
    val availability: String = "",
    val locationLabel: String = "Lekki Phase 1, Lagos",
    val latitude: Double = 6.4474,
    val longitude: Double = 3.4737,
    val rating: Double = 4.8,
    val onlineActive: Boolean = true,
    val verificationStatus: String = "REGISTERED",
    val verificationSource: String? = null,
    val websiteUrl: String? = null,
    val googleMapsUrl: String? = null,
    val contactPhone: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isVerifiedOnlineOnly(): Boolean = teacherUid.isBlank() || verificationStatus == "VERIFIED_ONLINE"

    fun formattedHourlyRate(): String {
        if (hourlyRate <= 0L) return "Contact for rates"
        val symbol = when (currency) {
            "NGN" -> "₦"
            "USD" -> "$"
            "GBP" -> "£"
            "EUR" -> "€"
            else -> "$currency "
        }
        return "$symbol${formatCompactAmount(hourlyRate)} / hr"
    }
}

@Keep
@IgnoreExtraProperties
data class TutorSession(
    @DocumentId val id: String = "",
    val tutorId: String = "",
    val tutorName: String = "",
    val teacherUid: String = "",
    val clientUid: String = "",
    val clientName: String = "",
    val studentName: String = "",
    val curriculumTrack: String = "Cambridge & Nigerian",
    val subject: String = "",
    val preferredSchedule: String = "",
    val notes: String? = null,
    val meetingLink: String? = null,
    val status: String = "REQUESTED",
    val paymentId: String? = null,
    val paymentStatus: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentTransaction(
    @DocumentId val id: String = "",
    val payerUid: String = "",
    val payerName: String = "",
    val payerEmail: String = "",
    val recipientUid: String = "",
    val recipientName: String = "",
    val paymentType: String = "SCHOOL_APPLICATION_FEE",
    val referenceId: String = "",
    val referenceTitle: String = "",
    val amount: Long = 0L,
    val currency: String = "NGN",
    val paymentMethod: String = "Card Payment",
    val gatewayProvider: String = "Paystack",
    val gatewayReference: String = "",
    val receiptNumber: String = "",
    val status: String = "PAID",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun formattedAmount(): String = formatCurrencyAmount(amount, currency)
}

data class TutorWithDistance(
    val tutor: ProgressiveTutor,
    val distanceKm: Double
)

data class TutorSearchFilter(
    val query: String = "",
    val sexFilter: String? = null,
    val curriculumFilter: String = "All",
    val selectedSubjects: Set<String> = emptySet(),
    val clientLat: Double = 6.4474,
    val clientLng: Double = 3.4737,
    val clientLabel: String = "Lekki Phase 1, Lagos",
    val maxDistanceKm: Int = 50,
    val limitByDistance: Boolean = false
)

fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

enum class AiAdvisorModelOption(val modelId: String, val label: String, val subtitle: String) {
    BALANCED("gemini-3.8-flash", "Gemini 3.8 Flash", "Default • Fast & thorough"),
    LITE("gemini-3.5-flash-lite", "Flash-Lite", "Ultra-fast responses"),
    DEEP("gemini-4-argon", "Gemini 4 Argon", "Complex reasoning (Blaze)")
}

data class AiAdvisorMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val groundedArea: String? = null,
    val groundedSchoolCount: Int = 0,
    val modelLabel: String? = null,
    val isStreaming: Boolean = false,
    val groundingLinks: List<GroundingSourceLink> = emptyList()
)

