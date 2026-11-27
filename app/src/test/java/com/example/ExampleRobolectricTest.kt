package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.example.data.AiAdvisorModelOption
import com.example.data.Announcement
import com.example.data.PaymentRepository
import com.example.data.PaymentTransaction
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.SchoolWithDistance
import com.example.data.calculateDistanceKm
import com.example.ui.MercatorProjection
import com.example.ui.SchoolDetailView
import com.example.ui.UiState
import com.example.ui.theme.HighlightHue
import com.example.ui.theme.PureWhiteBackground
import com.example.ui.theme.SchoolCompassTheme
import com.example.ui.theme.SolarThemeCalculator
import com.example.ui.theme.ThemeAppearanceMode
import com.example.ui.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Skolquik", appName)
        assertEquals("SEARCH SMART, ENROLL FAST.", context.getString(R.string.app_tagline))
    }

    @Test
    fun `formatted fee range displays currency and period`() {
        val school = School(
            name = "Test Dual Academy",
            curriculum = "Nigerian & British",
            feeMin = 450_000L,
            feeMax = 1_200_000L,
            feeCurrency = "NGN",
            feePeriod = "Per Term",
            sportsFocus = listOf("Football (Soccer)", "Swimming")
        )
        assertEquals("₦450k – ₦1.2M / term", school.formattedFeeRange())
    }

    @Test
    fun `haversine distance calculates accurate kilometer radius`() {
        val dist = calculateDistanceKm(6.4474, 3.4737, 6.4281, 3.4219)
        assertTrue("Distance should be between 5 and 7 km", dist in 5.0..7.0)
    }

    @Test
    fun `map zoom scales inversely with selected radius`() {
        val nearZoom = com.example.ui.zoomForRadiusKm(2)
        val midZoom = com.example.ui.zoomForRadiusKm(15)
        val wideZoom = com.example.ui.zoomForRadiusKm(50)
        assertTrue("Zoom should decrease as radius widens", nearZoom > midZoom && midZoom > wideZoom)
    }

    @Test
    fun `progressive tutor formats hourly rate cleanly`() {
        val tutor = com.example.data.ProgressiveTutor(
            fullName = "Mrs. Funke Adeyemi",
            sex = "Female",
            cambridgeSubjects = listOf("Cambridge IGCSE Mathematics"),
            nigerianSubjects = listOf("WAEC / NECO Mathematics"),
            hourlyRate = 8500L,
            currency = "NGN"
        )
        assertEquals("₦8k / hr", tutor.formattedHourlyRate())
        assertTrue(tutor.cambridgeSubjects.isNotEmpty() && tutor.nigerianSubjects.isNotEmpty())
    }

    @Test
    fun `parseOverpassSchoolsJson parses unregistered geolocated schools cleanly`() {
        val sampleJson = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 123456,
                  "lat": 6.4475,
                  "lon": 3.4738,
                  "tags": {
                    "amenity": "school",
                    "name": "Greensprings Lekki Campus",
                    "addr:street": "Admiralty Way",
                    "addr:city": "Lagos",
                    "website": "greensprings.edu.ng",
                    "phone": "+234-800-111-2222"
                  }
                },
                {
                  "type": "way",
                  "id": 987654,
                  "center": { "lat": 6.4281, "lon": 3.4219 },
                  "tags": {
                    "amenity": "college",
                    "name": "St. Gregory Faith College",
                    "religion": "christian"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = com.example.data.ExternalSchoolService.parseOverpassSchoolsJson(sampleJson)
        assertEquals(2, parsed.size)
        val first = parsed[0]
        assertEquals("", first.adminUid)
        assertEquals("node_123456", first.externalPlaceId)
        assertEquals("https://greensprings.edu.ng", first.websiteUrl)
        assertEquals("INTERNET_MATCHED", first.verificationStatus)
        assertEquals("Admiralty Way, Lagos", first.address)

        val second = parsed[1]
        assertEquals("way_987654", second.externalPlaceId)
        assertTrue(second.faithBased)
        assertEquals("Fees not added yet", second.formattedFeeRange())
    }

    @Test
    fun `mergeRegisteredAndExternalSchools deduplicates claimed schools`() {
        val external1 = School(
            id = "osm_node_123456",
            adminUid = "",
            name = "Greensprings Lekki Campus",
            latitude = 6.4475,
            longitude = 3.4738,
            externalPlaceId = "node_123456",
            verificationStatus = "INTERNET_MATCHED"
        )
        val external2 = School(
            id = "osm_way_987654",
            adminUid = "",
            name = "Corona School Victoria Island",
            latitude = 6.4281,
            longitude = 3.4219,
            externalPlaceId = "way_987654",
            verificationStatus = "INTERNET_MATCHED"
        )
        val claimedRegistered = School(
            id = com.example.data.ExternalSchoolService.claimSchoolDocId("node_123456"),
            adminUid = "admin_123",
            name = "Greensprings Lekki Campus",
            latitude = 6.4475,
            longitude = 3.4738,
            externalPlaceId = "node_123456",
            verificationStatus = "VERIFIED",
            feeMin = 500_000L,
            feeMax = 900_000L
        )

        val merged = com.example.data.ExternalSchoolService.mergeRegisteredAndExternalSchools(
            registered = listOf(claimedRegistered),
            external = listOf(external1, external2)
        )
        assertEquals(2, merged.size)
        assertEquals("ext_node_123456", merged[0].id)
        assertEquals("VERIFIED", merged[0].verificationStatus)
        assertEquals("osm_way_987654", merged[1].id)
    }

    @Test
    fun `parseOverpassSchoolsJson classifies universities polytechnics and colleges of education with courses`() {
        val sampleJson = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 111001,
                  "center": { "lat": 6.5158, "lon": 3.3898 },
                  "tags": {
                    "amenity": "university",
                    "name": "Lagos Coastal University",
                    "courses": "Computer Science; Medicine & Surgery; Law"
                  }
                },
                {
                  "type": "node",
                  "id": 111002,
                  "lat": 6.5220,
                  "lon": 3.3770,
                  "tags": {
                    "amenity": "college",
                    "name": "Yaba Federal Polytechnic",
                    "department": "Civil Engineering, Electrical Electronics Eng."
                  }
                },
                {
                  "type": "node",
                  "id": 111003,
                  "lat": 6.5190,
                  "lon": 3.3810,
                  "tags": {
                    "amenity": "college",
                    "name": "Adeniran College of Education"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = com.example.data.ExternalSchoolService.parseOverpassSchoolsJson(sampleJson)
        assertEquals(3, parsed.size)

        val uni = parsed[0]
        assertEquals("University", uni.institutionType)
        assertTrue(uni.gradeLevels.contains("Undergraduate"))
        assertTrue(uni.coursesOffered.contains("Computer Science"))
        assertTrue(uni.coursesOffered.contains("Medicine & Surgery"))

        val poly = parsed[1]
        assertEquals("Polytechnic", poly.institutionType)
        assertTrue(poly.gradeLevels.contains("ND / HND Programme"))
        assertTrue(poly.coursesOffered.contains("Civil Engineering"))

        val coe = parsed[2]
        assertEquals("College of Education", coe.institutionType)
        assertTrue(coe.gradeLevels.contains("NCE Programme"))
        assertTrue(coe.coursesOffered.contains("Education & Pedagogy"))
    }

    @Test
    fun `mercator projection converts lat lng to world pixels and back accurately`() {
        val lat = 6.4474
        val lng = 3.4737
        val zoom = 13
        val (wx, wy) = MercatorProjection.latLngToWorldPx(lat, lng, zoom)
        val (backLat, backLng) = MercatorProjection.worldPxToLatLng(wx, wy, zoom)
        assertEquals(lat, backLat, 0.0005)
        assertEquals(lng, backLng, 0.0005)
        val mpp = MercatorProjection.metersPerPixel(lat, zoom)
        assertTrue("Meters per pixel at zoom 13 should be positive and ~19m", mpp in 15.0..22.0)
    }

    @Test
    fun `payment transaction formatting and receipt generation are consistent`() {
        val tx = PaymentTransaction(
            amount = 35_000L,
            currency = "NGN",
            paymentMethod = "Card Payment",
            gatewayProvider = "Paystack"
        )
        assertEquals("₦35,000", tx.formattedAmount())

        val receipt = PaymentRepository.generateReceiptNumber()
        assertTrue(receipt.startsWith("RCP-2026-"))
        assertEquals(15, receipt.length)

        val ref = PaymentRepository.generateGatewayReference("Paystack")
        assertTrue(ref.startsWith("SC-PSK-"))
        assertTrue(SchoolConstants.PAYMENT_METHODS.contains("Bank Transfer"))
    }

    @Test
    fun `google maps geocoding and places parsers extract valid coordinates and schools`() {
        val geocodeJson = """
            {
              "status": "OK",
              "results": [
                {
                  "formatted_address": "Ikeja GRA, Lagos, Nigeria",
                  "geometry": { "location": { "lat": 6.5833, "lng": 3.3500 } }
                }
              ]
            }
        """.trimIndent()
        val geocoded = com.example.data.GoogleMapsPlatformService.parseGoogleGeocodingJson(geocodeJson)
        assertEquals("Ikeja GRA, Lagos, Nigeria", geocoded?.formattedAddress)
        assertEquals(6.5833, geocoded?.latitude ?: 0.0, 0.0001)
        assertEquals(3.3500, geocoded?.longitude ?: 0.0, 0.0001)

        val placesJson = """
            {
              "status": "OK",
              "results": [
                {
                  "place_id": "ChIJabc123",
                  "name": "British International School Victoria Island",
                  "vicinity": "Landbridge Avenue, Victoria Island, Lagos",
                  "rating": 4.7,
                  "types": ["school", "point_of_interest"],
                  "geometry": { "location": { "lat": 6.4321, "lng": 3.4455 } }
                },
                {
                  "place_id": "ChIJuni456",
                  "name": "Pan-Atlantic University",
                  "vicinity": "Ibeju-Lekki, Lagos",
                  "rating": 4.8,
                  "types": ["university", "school"],
                  "geometry": { "location": { "lat": 6.4892, "lng": 3.8547 } }
                }
              ]
            }
        """.trimIndent()
        val places = com.example.data.GoogleMapsPlatformService.parseGooglePlacesSchoolsJson(placesJson)
        assertEquals(2, places.size)
        assertEquals("gplace_ChIJabc123", places[0].externalPlaceId)
        assertEquals("British", places[0].curriculum)
        assertEquals("University", places[1].institutionType)
        assertEquals("Google Maps Places API", places[1].verificationSource)
        assertTrue(places[1].coursesOffered.contains("Computer Science"))
    }

    @Test
    fun `school detail view renders firestore courses, program details, and announcements`() {
        val fallbackSchool = School(
            id = "uni_lagos_test",
            adminUid = "admin_uid_1",
            adminName = "Prof. Adebayo",
            name = "Coastline University Lagos",
            address = "Akoka, Lagos"
        )
        val firestoreSchool = fallbackSchool.copy(
            institutionType = "University",
            curriculum = "Nigerian & British",
            gradeLevels = listOf("Undergraduate", "Postgraduate"),
            coursesOffered = listOf("Computer Science", "Civil Engineering", "Medicine & Surgery"),
            verificationStatus = "VERIFIED",
            feeMin = 850_000L,
            feeMax = 1_500_000L
        )
        val announcements = listOf(
            Announcement(
                id = "ann_1",
                schoolId = "uni_lagos_test",
                schoolName = "Coastline University Lagos",
                adminUid = "admin_uid_1",
                title = "Post-UTME Screening Opens",
                body = "Engineering and Medical Sciences screening holds this Saturday.",
                category = "Admissions"
            )
        )

        composeTestRule.setContent {
            MaterialTheme {
                SchoolDetailView(
                    fallbackSchoolItem = SchoolWithDistance(fallbackSchool, 3.2),
                    firestoreDetailState = UiState.Success(firestoreSchool),
                    announcementsState = UiState.Success(announcements),
                    onMessageAdmin = {},
                    onSubmitEnrollment = { _, _, _, _, _ -> },
                    onClaimSchool = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("school_detail_view").assertIsDisplayed()
        composeTestRule.onNodeWithText("Live Firestore Profile Synced").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undergraduate").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Postgraduate").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Computer Science").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Civil Engineering").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Post-UTME Screening Opens").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `school detail view renders favorite heart button and invokes toggle callback`() {
        val sampleSchool = School(
            id = "fav_test_school",
            adminUid = "admin_1",
            name = "Amethyst Heritage College",
            address = "Victoria Island, Lagos"
        )
        var toggledSchool: School? = null
        var isFavState = false

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                SchoolDetailView(
                    fallbackSchoolItem = SchoolWithDistance(sampleSchool, 1.4),
                    firestoreDetailState = UiState.Success(sampleSchool),
                    isFavorite = isFavState,
                    onToggleFavorite = {
                        toggledSchool = it
                        isFavState = !isFavState
                    },
                    onMessageAdmin = {},
                    onSubmitEnrollment = { _, _, _, _, _ -> },
                    onClaimSchool = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_favorite_button")
            .assertIsDisplayed()
            .assertContentDescriptionContains("Save Amethyst Heritage College to favorites")
            .performClick()

        composeTestRule.waitForIdle()
        assertEquals("fav_test_school", toggledSchool?.id)
        assertTrue(isFavState)
    }

    @Test
    fun `minimalist white theme and customizable highlight hues preserve white canvas and model ids`() {
        var capturedBackground = androidx.compose.ui.graphics.Color.Unspecified
        var capturedPrimary = androidx.compose.ui.graphics.Color.Unspecified

        composeTestRule.setContent {
            SchoolCompassTheme(
                darkTheme = false,
                highlightHue = HighlightHue.EMERALD_MINT,
                dynamicColor = false
            ) {
                capturedBackground = MaterialTheme.colorScheme.background
                capturedPrimary = MaterialTheme.colorScheme.primary
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(PureWhiteBackground, capturedBackground)
        assertEquals(HighlightHue.EMERALD_MINT.lightPrimary, capturedPrimary)
        assertEquals("gemini-3.8-flash", AiAdvisorModelOption.BALANCED.modelId)
        assertEquals("gemini-3.5-flash-lite", AiAdvisorModelOption.LITE.modelId)
        assertEquals("gemini-4-argon", AiAdvisorModelOption.DEEP.modelId)
    }

    @Test
    fun `solar theme calculator switches between day and night accurately around sunrise and sunset`() {
        val noonCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Africa/Lagos")).apply {
            set(2026, java.util.Calendar.JUNE, 15, 12, 30, 0)
        }
        val midnightCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Africa/Lagos")).apply {
            set(2026, java.util.Calendar.JUNE, 15, 22, 30, 0)
        }
        val noonSchedule = SolarThemeCalculator.calculateSchedule(6.4474, 3.4737, noonCal)
        val nightSchedule = SolarThemeCalculator.calculateSchedule(6.4474, 3.4737, midnightCal)
        assertFalse("Solar schedule at 12:30 should be daytime", noonSchedule.isCurrentlyNight)
        assertTrue("Solar schedule at 22:30 should be nighttime", nightSchedule.isCurrentlyNight)
        assertTrue(noonSchedule.sunriseMinutesFromMidnight in 300..450)
        assertTrue(noonSchedule.sunsetMinutesFromMidnight in 1050..1200)
    }

    @Test
    fun `leaflet osm map template and ikorodu preset configure openstreetmap cleanly`() {
        val ikorodu = SchoolConstants.PRESET_LOCATIONS.firstOrNull { it.label == "Ikorodu, Lagos" }
        assertTrue("Ikorodu, Lagos preset should be present", ikorodu != null)
        assertEquals(6.6194, ikorodu!!.lat, 0.0001)
        assertEquals(3.5105, ikorodu.lng, 0.0001)

        val html = com.example.ui.buildLeafletHtmlDocument(
            initialLat = ikorodu.lat,
            initialLng = ikorodu.lng,
            initialZoom = 12,
            initialLabel = ikorodu.label,
            primaryHex = "#4A148C"
        )
        assertTrue(html.contains("https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"))
        assertTrue(html.contains("https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"))
        assertTrue(html.contains("https://tile.openstreetmap.org/{z}/{x}/{y}.png"))
        assertTrue(html.contains("6.619400"))
        assertTrue(html.contains("3.510500"))
        assertTrue(html.contains("© OpenStreetMap contributors"))

        val payloadJson = com.example.ui.buildLeafletMapPayloadJson(
            filter = com.example.data.SchoolSearchFilter(
                centerLat = ikorodu.lat,
                centerLng = ikorodu.lng,
                centerLabel = ikorodu.label,
                radiusKm = 12
            ),
            filteredSchools = listOf(
                SchoolWithDistance(
                    school = School(
                        id = "iko_1",
                        adminUid = "admin_iko",
                        name = "Ikorodu Grammar \"Model\" College",
                        latitude = 6.6210,
                        longitude = 3.5150,
                        curriculum = "Nigerian & British"
                    ),
                    distanceKm = 0.6
                )
            ),
            primaryHex = "#4A148C"
        )
        val parsed = org.json.JSONObject(payloadJson)
        assertEquals(6.6194, parsed.getDouble("centerLat"), 0.0001)
        assertEquals(3.5105, parsed.getDouble("centerLng"), 0.0001)
        assertEquals(1, parsed.getJSONArray("schools").length())
        assertEquals("Ikorodu Grammar \"Model\" College", parsed.getJSONArray("schools").getJSONObject(0).getString("name"))
        assertTrue(html.contains("L.latLngBounds([4.0, 2.5], [14.0, 14.9])"))
        assertTrue(html.contains("https://www.geoboundaries.org/api/current/gbOpen/NGA/ADM1/"))
        assertTrue(com.example.data.GoogleMapsPlatformService.isInsideNigeriaBounds(6.6194, 3.5105))
        assertTrue(!com.example.data.GoogleMapsPlatformService.isInsideNigeriaBounds(51.5074, -0.1278))
    }

    @Test
    fun `radius filter card updates kilometer radius and triggers current location centering`() {
        var currentFilter = com.example.data.SchoolSearchFilter(
            centerLat = 6.6194,
            centerLng = 3.5105,
            centerLabel = "Current GPS",
            radiusKm = 15,
            limitByRadius = true
        )
        var gpsRequested = false

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.RadiusFilterCard(
                    filter = currentFilter,
                    matchCount = 4,
                    isLocatingUser = false,
                    onRequestGpsCenter = { gpsRequested = true },
                    onUpdateFilter = { reducer -> currentFilter = reducer(currentFilter) }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("map_radius_filter_component").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 visible markers within 15 km of Current GPS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("radius_chip_25km").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(25, currentFilter.radiusKm)
        assertTrue(currentFilter.limitByRadius)

        composeTestRule.onNodeWithTag("radius_use_my_location_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(gpsRequested)
    }

    @Test
    fun `skolquik opening logo and launcher icon drawables load cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logoDrawable = context.getDrawable(R.drawable.img_skolquik_opening_logo)
        val iconDrawable = context.getDrawable(R.drawable.img_launcher_foreground)
        assertTrue("Opening logo drawable should exist", logoDrawable != null && logoDrawable.intrinsicWidth > 0)
        assertTrue("Launcher foreground drawable should exist", iconDrawable != null && iconDrawable.intrinsicWidth > 0)
    }

    @Test
    fun `authScreen renders skolquik opening logo and sign in actions without crashing`() {
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.auth.AuthScreen(onAuthSuccess = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("opening_brand_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_sign_in_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `discoverScreen renders with pull to refresh and without CameraUpdateFactory crash`() {
        val verifiedSchool = School(
            id = "disc_1",
            adminUid = "",
            name = "Corona School Victoria Island",
            address = "Victoria Island, Lagos",
            latitude = 6.4281,
            longitude = 3.4219,
            verificationStatus = "VERIFIED_ONLINE"
        )
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.DiscoverScreen(
                    schoolsUiState = UiState.Success(listOf(verifiedSchool)),
                    filteredSchools = listOf(SchoolWithDistance(verifiedSchool, 0.8)),
                    filter = com.example.data.SchoolSearchFilter(
                        centerLat = 6.6194,
                        centerLng = 3.5105,
                        centerLabel = "Ikorodu, Lagos",
                        radiusKm = 15
                    ),
                    isBusy = false,
                    isRefreshingHome = false,
                    onPullToRefreshHome = {},
                    onUpdateFilter = {},
                    onResetFilters = {},
                    onMessageAdmin = {},
                    onSubmitEnrollment = { _, _, _, _, _ -> },
                    onRegisterSchoolClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("discover_pull_to_refresh").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_advert_grid_4x2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("discover_search_input").assertIsDisplayed()
    }

    @Test
    fun `mainActivity launches onCreate and renders initial content without crashing`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
        try {
            controller.setup()
            val activity = controller.get()
            assertTrue("MainActivity should not finish during launch", !activity.isFinishing)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `registrationPortalScreen renders after first sign in without crashing`() {
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.RegistrationPortalScreen(
                    existingProfile = null,
                    defaultName = "Quam Elebute",
                    defaultEmail = SchoolConstants.PLATFORM_ADMIN_EMAIL,
                    isBusy = false,
                    onSaveProfile = { _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Complete Your Registration").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_banner_pick_photo_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_registration_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `adminHubScreen renders super admin oversight without crashing`() {
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.AdminHubScreen(
                    currentUserId = "admin_super",
                    adminName = "Quam Elebute",
                    defaultLat = 6.4474,
                    defaultLng = 3.4737,
                    isPlatformAdmin = true,
                    initialOpenOversight = true,
                    schoolsState = UiState.Success(emptyList()),
                    adminEnrollmentsState = UiState.Success(emptyList()),
                    adminStudentsState = UiState.Success(emptyList()),
                    selectedAdminStudentId = null,
                    adminAttendanceState = UiState.Success(emptyList()),
                    adminGradesState = UiState.Success(emptyList()),
                    isBusy = false,
                    onSaveSchool = { _, _ -> },
                    onUpdateEnrollmentStatus = { _, _, _ -> },
                    onSelectAdminStudent = {},
                    onLogAttendance = { _, _, _, _ -> },
                    onAddGrade = { _, _, _, _, _ -> },
                    onPublishAnnouncement = { _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("platform_oversight_summary_card").assertIsDisplayed()
    }

    @Test
    fun `parseGroundedSchoolsResponse extracts verified online schools with search and maps citations`() {
        val rawJson = """
            ```json
            [
              {
                "name": "Atlantic Hall School",
                "address": "Poka, Epe, Lagos",
                "latitude": 6.6012,
                "longitude": 3.9721,
                "institutionType": "Primary & Secondary",
                "curriculum": "Nigerian & British",
                "gradeLevels": ["Junior Secondary", "Senior Secondary"],
                "coursesOffered": ["Sciences & Biotechnology", "Cambridge IGCSE"],
                "programHighlights": "Full boarding co-educational secondary school with IGCSE and WAEC tracks.",
                "websiteUrl": "https://atlantic-hall.net",
                "contactPhone": "+234-800-555-0199",
                "boardingOption": "Boarding",
                "admissionWindow": "Jan - Apr 2026",
                "sourceTitle": "Atlantic Hall Official Portal",
                "sourceUrl": "https://atlantic-hall.net/admissions",
                "googleMapsPlaceRef": "places/ChIJA12345"
              }
            ]
            ```
        """.trimIndent()

        val groundingLinks = listOf(
            com.example.data.GroundingSourceLink(
                title = "Atlantic Hall School on Google Maps",
                uri = "https://www.google.com/maps/place/?q=place_id:ChIJA12345",
                sourceType = "Google Maps",
                placeId = "places/ChIJA12345"
            ),
            com.example.data.GroundingSourceLink(
                title = "Atlantic Hall Admissions Overview",
                uri = "https://atlantic-hall.net",
                sourceType = "Google Search"
            )
        )

        val parsed = com.example.data.OnlineSchoolGroundingService.parseGroundedSchoolsResponse(
            rawText = rawJson,
            groundingLinks = groundingLinks,
            fallbackLat = 6.4474,
            fallbackLng = 3.4737,
            fallbackArea = "Lagos"
        )

        assertEquals(1, parsed.size)
        val school = parsed.first()
        assertEquals("", school.adminUid)
        assertEquals("gmaps_ChIJA12345", school.id)
        assertEquals("VERIFIED_ONLINE", school.verificationStatus)
        assertTrue(school.isVerifiedOnline())
        assertEquals("Verified Online", school.verificationBadgeLabel())
        assertEquals("Boarding", school.boardingOption)
        assertEquals("Jan - Apr 2026", school.admissionWindow)
        assertEquals("places/ChIJA12345", school.googleMapsPlaceRef)
        assertTrue(school.parsedCitations().any { it.sourceType == "Google Maps" })
        assertTrue(school.parsedCitations().any { it.uri == "https://atlantic-hall.net/admissions" })
    }

    @Test
    fun `unclaimed verified online school exposes immediate inquiry and submit details without gate`() {
        val onlineSchool = School(
            id = "gmaps_ChIJOnline1",
            adminUid = "",
            name = "Corona Secondary School Agbara",
            address = "Agbara Estate, Ogun",
            latitude = 6.5050,
            longitude = 3.1020,
            verificationStatus = "VERIFIED_ONLINE",
            verificationSource = "Google Search & Maps Grounding",
            googleMapsPlaceRef = "places/ChIJOnline1",
            googleMapsUrl = "https://www.google.com/maps/place/?q=place_id:ChIJOnline1",
            sourceCitations = listOf("[Google Search] Corona Schools | https://coronaschools.org"),
            boardingOption = "Day & Boarding",
            admissionWindow = "Rolling Admissions 2026"
        )
        var extraDetailsTriggered = false

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                SchoolDetailView(
                    fallbackSchoolItem = SchoolWithDistance(onlineSchool, 4.5),
                    firestoreDetailState = UiState.Success(null),
                    onMessageAdmin = {},
                    onSubmitEnrollment = { _, _, _, _, _ -> },
                    onClaimSchool = {},
                    onSubmitExtraDetails = { extraDetailsTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_verification_card").assertIsDisplayed()
        val verifiedOnlineNodes = composeTestRule.onAllNodesWithText("Verified Online", useUnmergedTree = true)
        assertTrue(verifiedOnlineNodes.fetchSemanticsNodes().isNotEmpty())
        verifiedOnlineNodes[0].assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_citation_chip_0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_apply_enroll_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_claim_school_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_submit_details_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(extraDetailsTriggered)
    }

    @Test
    fun `buildTenAdvertSlides builds slides strictly from live adverts and real Google grounded schools`() {
        val liveAd = com.example.data.Advert(
            id = "ad_live_1",
            ownerUid = "owner_1",
            advertiserName = "Grange Admissions",
            title = "2026 Scholarship Entrance",
            body = "Apply early for Year 7 scholarships",
            planTier = "District Hero",
            status = "ACTIVE"
        )
        val realSchools = (1..9).map { idx ->
            SchoolWithDistance(
                school = School(
                    id = "real_$idx",
                    adminUid = "",
                    name = "Verified School $idx",
                    address = "Lagos",
                    verificationStatus = "VERIFIED_ONLINE"
                ),
                distanceKm = idx.toDouble()
            )
        }
        val slides = com.example.ui.buildTenAdvertSlides(
            activeAdverts = listOf(com.example.data.AdvertWithDistance(liveAd, 1.4)),
            realSchools = realSchools
        )
        assertEquals(10, slides.size)
        assertTrue(slides.first().isLiveSponsored)
        assertEquals("HERO AD", slides.first().badge)
        assertEquals("2026 Scholarship Entrance", slides.first().title)
        assertEquals("Verified School 1", slides[1].title)
        assertEquals("VERIFIED ONLINE", slides[1].badge)
        assertTrue(slides[1].schoolItem != null)
    }

    @Test
    fun `discoverScreen 4x2 single-advert carousel supports right-to-left user swipe across real school slides`() {
        val schoolOne = School(
            id = "disc_carousel_1",
            adminUid = "",
            name = "Corona School Lekki",
            address = "Lekki, Lagos",
            latitude = 6.4474,
            longitude = 3.4737,
            verificationStatus = "VERIFIED_ONLINE"
        )
        val schoolTwo = School(
            id = "disc_carousel_2",
            adminUid = "",
            name = "British International School",
            address = "Oniru, Lagos",
            latitude = 6.4320,
            longitude = 3.4490,
            verificationStatus = "VERIFIED_ONLINE"
        )
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.DiscoverScreen(
                    schoolsUiState = UiState.Success(listOf(schoolOne, schoolTwo)),
                    filteredSchools = listOf(
                        SchoolWithDistance(schoolOne, 0.8),
                        SchoolWithDistance(schoolTwo, 2.3)
                    ),
                    filter = com.example.data.SchoolSearchFilter(
                        centerLat = 6.4474,
                        centerLng = 3.4737,
                        centerLabel = "Lekki Phase 1, Lagos",
                        radiusKm = 15
                    ),
                    isBusy = false,
                    onUpdateFilter = {},
                    onResetFilters = {},
                    onMessageAdmin = {},
                    onSubmitEnrollment = { _, _, _, _, _ -> },
                    onRegisterSchoolClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_advert_grid_4x2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("advert_slide_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("advert_carousel_pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("advert_slide_1").assertIsDisplayed()
    }

    @Test
    fun `minimalist sidebar drawer includes Dashboard, Saved, Skolquik AI, Settings and privacy, and Help and support`() {
        val activeTabState = androidx.compose.runtime.mutableStateOf(com.example.ui.MainTab.DISCOVER)
        val snackbarHostState = androidx.compose.material3.SnackbarHostState()

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.SkolquikMinimalShell(
                    selectedTab = activeTabState.value,
                    displayName = "Amina Bello",
                    userEmail = "amina@example.com",
                    userRole = "parent",
                    isPlatformAdmin = false,
                    favoriteCount = 2,
                    snackbarHostState = snackbarHostState,
                    onSelectTab = { activeTabState.value = it },
                    onOpenProfile = {},
                    onSignOut = {}
                ) {
                    androidx.compose.material3.Text("Current Tab: ${activeTabState.value.name}")
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_sidebar_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sidebar_drawer_sheet").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_dashboard").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_saved").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_ai_advisor").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_settings").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_help").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.MainTab.SETTINGS, activeTabState.value)
    }

    @Test
    fun `settingsPrivacyScreen lets user pick Sunrise Sunset theme and custom highlight hue`() {
        val currentSettingsState = androidx.compose.runtime.mutableStateOf(ThemeSettings())
        composeTestRule.setContent {
            val currentSettings = currentSettingsState.value
            SchoolCompassTheme(darkTheme = false, highlightHue = currentSettings.highlightHue, dynamicColor = false) {
                com.example.ui.SettingsPrivacyScreen(
                    themeSettings = currentSettings,
                    latitude = 6.4474,
                    longitude = 3.4737,
                    locationLabel = "Lekki Phase 1, Lagos",
                    displayName = "Amina Bello",
                    userEmail = "amina@example.com",
                    onSelectAppearanceMode = {
                        currentSettingsState.value = currentSettingsState.value.copy(appearanceMode = it)
                    },
                    onSelectHighlightHue = {
                        currentSettingsState.value = currentSettingsState.value.copy(highlightHue = it)
                    },
                    onToggleAutoOnlineDiscovery = {},
                    onTogglePreciseLocation = {},
                    onOpenProfile = {},
                    onSignOut = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_privacy_screen")
            .performScrollToNode(hasTestTag("theme_mode_SUNRISE_SUNSET"))
        composeTestRule.onNodeWithTag("theme_mode_SUNRISE_SUNSET").performClick()
        composeTestRule.onNodeWithTag("settings_privacy_screen")
            .performScrollToNode(hasTestTag("theme_hue_CORAL_ROSE"))
        composeTestRule.onNodeWithTag("theme_hue_CORAL_ROSE").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ThemeAppearanceMode.SUNRISE_SUNSET, currentSettingsState.value.appearanceMode)
        assertEquals(HighlightHue.CORAL_ROSE, currentSettingsState.value.highlightHue)
    }

    @Test
    fun `dashboard saved and help screens render cleanly and parseGroundedTutorsResponse extracts verified providers`() {
        val rawTutorsJson = """
            [
              {
                "name": "PrepClass Africa",
                "headline": "WAEC, IGCSE & JAMB Home & Online Tutors",
                "bio": "Verified tutoring network across Lagos and Abuja.",
                "address": "Yaba, Lagos",
                "latitude": 6.5095,
                "longitude": 3.3711,
                "curriculumTracks": ["Cambridge", "Nigerian"],
                "cambridgeSubjects": ["Cambridge IGCSE Mathematics"],
                "nigerianSubjects": ["WAEC / NECO Mathematics"],
                "qualifications": "Certified Exam Tutors",
                "websiteUrl": "https://prepclass.com.ng"
              }
            ]
        """.trimIndent()
        val parsedTutors = com.example.data.OnlineSchoolGroundingService.parseGroundedTutorsResponse(
            rawText = rawTutorsJson,
            groundingLinks = emptyList(),
            fallbackLat = 6.4474,
            fallbackLng = 3.4737,
            fallbackArea = "Lagos"
        )
        assertEquals(1, parsedTutors.size)
        assertEquals("VERIFIED_ONLINE", parsedTutors.first().verificationStatus)
        assertEquals("https://prepclass.com.ng", parsedTutors.first().websiteUrl)

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.DashboardScreen(
                    displayName = "Amina Bello",
                    userRole = "parent",
                    filter = com.example.data.SchoolSearchFilter(),
                    nearbySchools = emptyList(),
                    savedSchoolsCount = 0,
                    enrollmentsState = UiState.Success(emptyList()),
                    conversationCount = 0,
                    onNavigateTab = {},
                    onRefreshOnline = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_stat_nearby").assertIsDisplayed()
    }

    @Test
    fun `messagesScreen displays modern chat header bubbles timestamps and sends messages`() {
        val sampleConv = com.example.data.Conversation(
            id = "conv_1",
            schoolId = "school_1",
            schoolName = "Greenhills Academy",
            parentUid = "user_me",
            parentName = "Amina Bello",
            adminUid = "admin_jd",
            adminName = "Jordan Davis",
            memberUids = listOf("user_me", "admin_jd"),
            lastMessage = "Hey! Are we still on for the campus tour?"
        )
        val selectedConvState = androidx.compose.runtime.mutableStateOf<com.example.data.Conversation?>(null)
        val sentMessages = mutableListOf<String>()
        val messagesListState = androidx.compose.runtime.mutableStateOf(
            listOf(
                com.example.data.ChatMessage(
                    id = "m1",
                    senderUid = "admin_jd",
                    senderName = "Jordan Davis",
                    senderRole = "admin",
                    text = "Hey! Are we still on for the admissions tour at 3?"
                ),
                com.example.data.ChatMessage(
                    id = "m2",
                    senderUid = "user_me",
                    senderName = "Amina Bello",
                    senderRole = "parent",
                    text = "Yes! Wrapping up our questions now."
                )
            )
        )

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = true, dynamicColor = false) {
                com.example.ui.MessagesScreen(
                    currentUserId = "user_me",
                    conversationsState = UiState.Success(listOf(sampleConv)),
                    selectedConversation = selectedConvState.value,
                    messagesState = UiState.Success(messagesListState.value),
                    onSelectConversation = { selectedConvState.value = it },
                    onSendMessage = { _, text ->
                        sentMessages.add(text)
                        messagesListState.value = messagesListState.value + com.example.data.ChatMessage(
                            id = "m3",
                            senderUid = "user_me",
                            senderName = "Amina Bello",
                            senderRole = "parent",
                            text = text
                        )
                    },
                    onBrowseSchoolsClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("messages_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conversation_card_conv_1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_thread_view").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_header_avatar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Online • Jordan Davis").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_message_bubble_m1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_message_bubble_m2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_message_timestamp_m2").assertIsDisplayed()

        composeTestRule.onNodeWithTag("chat_message_input").performTextInput("See you at reception!")
        composeTestRule.onNodeWithTag("chat_send_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("See you at reception!"), sentMessages)
        composeTestRule.onNodeWithText("See you at reception!").assertIsDisplayed()
    }

    @Test
    fun `cloudSqlSyncClient validates urls parses health and round trips school json and renders sync card`() {
        assertEquals(
            "https://ais-pre-2twucpr4pjla5edur3vinr-571905150412.europe-west1.run.app",
            com.example.data.CloudSqlSyncClient.normalizeSafeBaseUrl(
                "https://ais-pre-2twucpr4pjla5edur3vinr-571905150412.europe-west1.run.app/"
            )
        )
        assertEquals(null, com.example.data.CloudSqlSyncClient.normalizeSafeBaseUrl("http://insecure.example.com"))
        assertEquals(null, com.example.data.CloudSqlSyncClient.normalizeSafeBaseUrl("javascript:alert(1)"))

        val okHealth = com.example.data.CloudSqlSyncClient.parseHealthResponse(
            """{"ok":true,"engine":"PostgreSQL","schoolsCount":42}""",
            200
        )
        assertTrue(okHealth.isConnected)
        assertEquals("PostgreSQL", okHealth.engine)
        assertEquals(42, okHealth.syncedSchoolsCount)

        val missingRoute = com.example.data.CloudSqlSyncClient.parseHealthResponse("Not Found", 404)
        assertFalse(missingRoute.isConnected)
        assertTrue(missingRoute.message.contains("404"))

        val htmlFallback = com.example.data.CloudSqlSyncClient.parseHealthResponse("<!doctype html><html></html>", 200)
        assertFalse(htmlFallback.isConnected)

        val school = School(
            id = "sql_school_1",
            name = "Corona Secondary School",
            address = "Agbara, Ogun State",
            latitude = 6.502,
            longitude = 3.108,
            curriculum = "Nigerian & British",
            verificationStatus = "VERIFIED_ONLINE",
            websiteUrl = "https://coronaschools.org"
        )
        val payload = com.example.data.CloudSqlSyncClient.schoolToJson(school)
        val parsed = com.example.data.CloudSqlSyncClient.parseSqlSchoolsJson("""{"schools":[$payload]}""")
        assertEquals(1, parsed.size)
        assertEquals("sql_school_1", parsed.first().id)
        assertEquals("Corona Secondary School", parsed.first().name)
        assertEquals("https://coronaschools.org", parsed.first().websiteUrl)

        var syncClicked = false
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.SettingsPrivacyScreen(
                    themeSettings = ThemeSettings(),
                    latitude = 6.4474,
                    longitude = 3.4737,
                    locationLabel = "Lekki Phase 1, Lagos",
                    displayName = "Amina Bello",
                    userEmail = "amina@example.com",
                    cloudSqlStatus = okHealth,
                    isTestingCloudSql = false,
                    onSelectAppearanceMode = {},
                    onSelectHighlightHue = {},
                    onToggleAutoOnlineDiscovery = {},
                    onTogglePreciseLocation = {},
                    onVerifyAndSyncCloudSql = { syncClicked = true },
                    onOpenProfile = {},
                    onSignOut = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_privacy_screen")
            .performScrollToNode(hasTestTag("sync_cloud_sql_button"))
        composeTestRule.onNodeWithTag("cloud_sql_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_cloud_sql_button").performClick()
        assertTrue(syncClicked)
    }

    @Test
    fun `platformAdminScreen restricts non-admins and shows individuals schools visits and admin delegation controls`() {
        val overview = com.example.data.parseAdminOverviewJson(
            """{"totalVisits":148,"androidVisits":92,"webVisits":56,"totalUsersCount":3,"individualsCount":2,"schoolAccountsCount":1,"schoolsCount":1}"""
        )
        assertEquals(148L, overview.totalVisits)
        assertEquals(92L, overview.androidVisits)
        assertEquals(56L, overview.webVisits)

        val grantedUids = mutableListOf<String>()
        val revokedUids = mutableListOf<String>()
        val parentUser = com.example.data.UserProfile(
            id = "u_ind_1",
            userId = "u_ind_1",
            role = "parent",
            displayName = "Amina Individual",
            email = "amina@example.com",
            homeAddress = "Lekki, Lagos"
        )
        val schoolAdminUser = com.example.data.UserProfile(
            id = "u_sch_1",
            userId = "u_sch_1",
            role = "admin",
            displayName = "Greenhills Admissions",
            email = "admissions@greenhills.edu.ng",
            homeAddress = "Victoria Island, Lagos"
        )
        val delegated = com.example.data.PlatformAdminRecord(
            id = "u_sch_1",
            userId = "u_sch_1",
            email = "admissions@greenhills.edu.ng",
            displayName = "Greenhills Admissions",
            grantedByUid = "owner_1",
            grantedByEmail = SchoolConstants.PLATFORM_ADMIN_EMAIL
        )

        val isAdminState = androidx.compose.runtime.mutableStateOf(false)
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.PlatformAdminScreen(
                    isPlatformAdmin = isAdminState.value,
                    isBootstrappedOwner = true,
                    currentUserEmail = SchoolConstants.PLATFORM_ADMIN_EMAIL,
                    allUsersState = UiState.Success(listOf(parentUser, schoolAdminUser)),
                    schoolsState = UiState.Success(
                        listOf(
                            School(
                                id = "sch_1",
                                adminUid = "u_sch_1",
                                adminName = "Greenhills Admissions",
                                name = "Greenhills Academy",
                                address = "Victoria Island, Lagos"
                            )
                        )
                    ),
                    platformAdminsState = UiState.Success(listOf(delegated)),
                    visitStatsState = UiState.Success(
                        com.example.data.AppVisitStats(
                            id = "visits_summary",
                            totalVisits = 148L,
                            androidVisits = 92L,
                            webVisits = 56L
                        )
                    ),
                    visitLogsState = UiState.Success(
                        listOf(
                            com.example.data.AppVisitLog(
                                id = "vis_1",
                                userId = "u_ind_1",
                                userEmail = "amina@example.com",
                                displayName = "Amina Individual",
                                role = "parent",
                                platform = "web"
                            )
                        )
                    ),
                    sqlAdminOverview = overview,
                    onGrantAdmin = { grantedUids.add(it.userId) },
                    onRevokeAdmin = { uid, _, _ -> revokedUids.add(uid) },
                    onVerifySchool = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("platform_admin_restricted").assertIsDisplayed()

        isAdminState.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("platform_admin_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_total_visits_value").assertIsDisplayed()
        composeTestRule.onNodeWithText("148").assertIsDisplayed()

        composeTestRule.onNodeWithTag("platform_admin_screen")
            .performScrollToNode(hasTestTag("user_make_admin_u_ind_1"))
        composeTestRule.onNodeWithTag("user_make_admin_u_ind_1").performClick()
        assertEquals(listOf("u_ind_1"), grantedUids)

        composeTestRule.onNodeWithTag("platform_admin_screen")
            .performScrollToNode(hasTestTag("admin_segment_1"))
        composeTestRule.onNodeWithTag("admin_segment_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("platform_admin_screen")
            .performScrollToNode(hasTestTag("user_revoke_admin_u_sch_1"))
        composeTestRule.onNodeWithTag("user_revoke_admin_u_sch_1").performClick()
        assertEquals(listOf("u_sch_1"), revokedUids)
    }

    @Test
    fun `sidebar hides Platform Admin for non-admins and shows it for authorized admins`() {
        val isAdminState = androidx.compose.runtime.mutableStateOf(false)
        val snackbarHostState = androidx.compose.material3.SnackbarHostState()
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.SkolquikMinimalShell(
                    selectedTab = com.example.ui.MainTab.DISCOVER,
                    displayName = "User",
                    userEmail = "user@example.com",
                    userRole = "parent",
                    isPlatformAdmin = isAdminState.value,
                    favoriteCount = 0,
                    snackbarHostState = snackbarHostState,
                    onSelectTab = {},
                    onOpenProfile = {},
                    onSignOut = {}
                ) {
                    androidx.compose.material3.Text("Body")
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_sidebar_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodes(hasTestTag("nav_platform_admin")).fetchSemanticsNodes().isEmpty())

        isAdminState.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_platform_admin").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `home bottom bar displays Home first followed by Discover, SkolQuik AI, Saved, and Search keys`() {
        val activeTabState = androidx.compose.runtime.mutableStateOf(com.example.ui.MainTab.HOME)
        val searchModeState = androidx.compose.runtime.mutableStateOf(false)
        var searchClickCount = 0
        var notificationsClickCount = 0
        val snackbarHostState = androidx.compose.material3.SnackbarHostState()

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.SkolquikMinimalShell(
                    selectedTab = activeTabState.value,
                    displayName = "Amina Bello",
                    userEmail = "amina@example.com",
                    userRole = "parent",
                    isPlatformAdmin = false,
                    favoriteCount = 3,
                    snackbarHostState = snackbarHostState,
                    isSearchActive = searchModeState.value,
                    onSearchClick = {
                        searchClickCount++
                        searchModeState.value = true
                        activeTabState.value = com.example.ui.MainTab.DISCOVER
                    },
                    notificationCount = 4,
                    onNotificationsClick = { notificationsClickCount++ },
                    onSelectTab = { tab ->
                        searchModeState.value = false
                        activeTabState.value = tab
                    },
                    onOpenProfile = {},
                    onSignOut = {}
                ) {
                    androidx.compose.material3.Text("Body")
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_bottom_bar").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodes(hasTestTag("open_ai_advisor_button")).fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("top_bar_notifications_button").assertIsDisplayed().performClick()
        assertEquals(1, notificationsClickCount)
        composeTestRule.onNodeWithTag("top_bar_search_button").assertIsDisplayed()

        val homeBounds = composeTestRule.onNodeWithTag("bottom_nav_home")
            .assertIsDisplayed()
            .assertTextContains("Home")
            .fetchSemanticsNode().boundsInRoot
        val discoverBounds = composeTestRule.onNodeWithTag("bottom_nav_discover")
            .assertIsDisplayed()
            .assertTextContains("Discover")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(homeBounds.left < discoverBounds.left)

        composeTestRule.onNodeWithTag("bottom_nav_ai").assertIsDisplayed().assertTextContains("SkolQuik AI")
        composeTestRule.onNodeWithTag("bottom_nav_saved").assertIsDisplayed().assertTextContains("Saved")
        composeTestRule.onNodeWithTag("bottom_nav_search").assertIsDisplayed().assertTextContains("Search")

        composeTestRule.onNodeWithTag("bottom_nav_discover").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.MainTab.DISCOVER, activeTabState.value)

        composeTestRule.onNodeWithTag("bottom_nav_ai").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.MainTab.AI_ADVISOR, activeTabState.value)

        composeTestRule.onNodeWithTag("bottom_nav_saved").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.MainTab.SAVED, activeTabState.value)

        composeTestRule.onNodeWithTag("bottom_nav_search").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, searchClickCount)
        assertEquals(com.example.ui.MainTab.DISCOVER, activeTabState.value)
        assertTrue(searchModeState.value)

        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.MainTab.HOME, activeTabState.value)
        assertTrue(!searchModeState.value)
    }

    @Test
    fun `home adverts screen showcases adverts for both schools and progressive tutors`() {
        val sampleAdverts = listOf(
            com.example.data.AdvertWithDistance(
                advert = com.example.data.Advert(
                    id = "ad_school_1",
                    advertiserName = "Crestview Academy",
                    title = "2026 STEM Scholarship Admissions",
                    body = "Apply now for Junior & Senior Secondary entrance exams.",
                    category = "School Admissions",
                    targetLocation = "Lekki Phase 1, Lagos",
                    ctaLabel = "Apply Now",
                    status = "ACTIVE"
                ),
                distanceKm = 1.4
            ),
            com.example.data.AdvertWithDistance(
                advert = com.example.data.Advert(
                    id = "ad_tutor_1",
                    advertiserName = "The Progressive Tutors",
                    title = "IGCSE & WAEC Intensive Coaching",
                    body = "1-on-1 online & in-person Mathematics and Physics sessions.",
                    category = "Progressive Tutors",
                    targetLocation = "Victoria Island, Lagos",
                    ctaLabel = "Book Tutor",
                    status = "ACTIVE"
                ),
                distanceKm = 2.8
            )
        )
        val sampleSchools = listOf(
            SchoolWithDistance(
                school = School(
                    id = "sch_home_1",
                    name = "Horizon British School",
                    curriculum = "British",
                    address = "Ikoyi, Lagos",
                    admissionsOpen = true
                ),
                distanceKm = 2.1
            )
        )
        val sampleTutors = listOf(
            com.example.data.TutorWithDistance(
                tutor = com.example.data.ProgressiveTutor(
                    id = "tut_home_1",
                    fullName = "Chinedu Okafor",
                    headline = "Cambridge Physics & Further Maths Mentor",
                    hourlyRate = 8000L,
                    locationLabel = "Ikeja GRA, Lagos"
                ),
                distanceKm = 3.5
            )
        )

        var discoverOpened = false
        var tutorsOpened = false

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.HomeAdvertsScreen(
                    activeAdverts = sampleAdverts,
                    schools = sampleSchools,
                    tutors = sampleTutors,
                    isLoading = false,
                    isRefreshing = false,
                    isBusy = false,
                    defaultUserName = "Amina Bello",
                    defaultUserEmail = "amina@example.com",
                    onRefreshHome = {},
                    onOpenDiscover = { discoverOpened = true },
                    onOpenTutors = { tutorsOpened = true },
                    onOpenPaidAdverts = {},
                    onSendAdvertInquiry = { _, _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_adverts_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_filter_pills").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_spotlight_grid").assertIsDisplayed()
        composeTestRule.onNodeWithText("2026 STEM Scholarship Admissions", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("IGCSE & WAEC Intensive Coaching", substring = true).assertIsDisplayed()

        // Filter to Progressive Tutors only
        composeTestRule.onNodeWithTag("home_pill_progressive_tutors").performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            composeTestRule.onAllNodesWithText("2026 STEM Scholarship Admissions", substring = true)
                .fetchSemanticsNodes().isEmpty()
        )
        composeTestRule.onNodeWithText("IGCSE & WAEC Intensive Coaching", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Chinedu Okafor", substring = true).assertIsDisplayed()
    }

    @Test
    fun `dashboard screen displays user display banner and edit picture button`() {
        var editProfileTriggered = false
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.DashboardScreen(
                    displayName = "Amina Bello",
                    userRole = "parent",
                    filter = com.example.data.SchoolSearchFilter(),
                    nearbySchools = emptyList(),
                    savedSchoolsCount = 2,
                    enrollmentsState = UiState.Success(emptyList()),
                    conversationCount = 1,
                    bannerImageUrl = "https://example.com/user-banner.jpg",
                    onEditProfile = { editProfileTriggered = true },
                    onNavigateTab = {},
                    onRefreshOnline = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_user_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_edit_banner_button").assertIsDisplayed().performClick()
        assertTrue(editProfileTriggered)
    }

    @Test
    fun `school editor, advert editor, and tutor editor expose banner picture picker and URL controls`() {
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.BannerImageEditorSection(
                    bannerImageUrl = null,
                    fallbackResId = R.drawable.img_campus_hero,
                    testTagPrefix = "test_banner",
                    onBannerChanged = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("test_banner_preview").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_banner_pick_photo_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_banner_toggle_url_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("test_banner_url_input").assertIsDisplayed()
    }

    @Test
    fun `registered school admins see visitor counts in admin hub and owned school detail view`() {
        val ownedSchool = School(
            id = "sch_owned_1",
            adminUid = "admin_uid_1",
            adminName = "Dr. Bello",
            name = "Lagoon Crest Academy",
            address = "Lekki Phase 1, Lagos",
            curriculum = "Nigerian & British",
            bannerImageUrl = "https://example.com/lagoon-banner.jpg"
        )
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.AdminHubScreen(
                    currentUserId = "admin_uid_1",
                    adminName = "Dr. Bello",
                    defaultLat = 6.4474,
                    defaultLng = 3.4737,
                    isPlatformAdmin = false,
                    schoolsState = UiState.Success(listOf(ownedSchool)),
                    adminEnrollmentsState = UiState.Success(emptyList()),
                    adminStudentsState = UiState.Success(emptyList()),
                    selectedAdminStudentId = null,
                    adminAttendanceState = UiState.Success(emptyList()),
                    adminGradesState = UiState.Success(emptyList()),
                    schoolVisitorCounts = mapOf("sch_owned_1" to 4),
                    isBusy = false,
                    onSaveSchool = { _, _ -> },
                    onUpdateEnrollmentStatus = { _, _, _ -> },
                    onSelectAdminStudent = {},
                    onLogAttendance = { _, _, _, _ -> },
                    onAddGrade = { _, _, _, _, _ -> },
                    onPublishAnnouncement = { _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_total_school_visits").assertIsDisplayed()
        composeTestRule.onNode(
            androidx.compose.ui.test.hasAnyAncestor(hasTestTag("admin_total_school_visits")) and
                androidx.compose.ui.test.hasText("4 people visited your page", substring = true),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("school_card_banner_sch_owned_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("school_visits_badge_sch_owned_1")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(
            androidx.compose.ui.test.hasAnyAncestor(hasTestTag("school_visits_badge_sch_owned_1")) and
                androidx.compose.ui.test.hasText("4 people visited your page", substring = true),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun `school detail view shows visitor badge only to owning school admin`() {
        val school = School(
            id = "sch_detail_visits",
            adminUid = "owner_1",
            adminName = "Principal",
            name = "Horizon British College",
            address = "Ikoyi, Lagos",
            bannerImageUrl = "https://example.com/horizon.jpg"
        )
        val isOwnerState = androidx.compose.runtime.mutableStateOf(false)
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                SchoolDetailView(
                    fallbackSchoolItem = SchoolWithDistance(school, 1.2),
                    firestoreDetailState = UiState.Success(school),
                    isOwnedByCurrentUser = isOwnerState.value,
                    pageVisitorCount = 1,
                    onMessageAdmin = {},
                    onSubmitEnrollment = { _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("school_detail_banner").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodes(hasTestTag("school_detail_visits_badge"))
                .fetchSemanticsNodes().isEmpty()
        )

        isOwnerState.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("school_detail_visits_badge").assertIsDisplayed()
        composeTestRule.onNode(
            androidx.compose.ui.test.hasAnyAncestor(hasTestTag("school_detail_visits_badge")) and
                androidx.compose.ui.test.hasText("1 person visited your page", substring = true),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun `progressive tutors screen shows visitor summary and card badge for registered tutors`() {
        val ownedTutor = com.example.data.ProgressiveTutor(
            id = "tut_owned_1",
            teacherUid = "tutor_owner_uid",
            fullName = "Mrs. Ada Obi",
            headline = "Cambridge & WAEC Chemistry Tutor",
            hourlyRate = 9000L,
            locationLabel = "Victoria Island, Lagos",
            bannerImageUrl = "https://example.com/ada-banner.jpg"
        )
        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.ProgressiveTutorsScreen(
                    currentUserId = "tutor_owner_uid",
                    defaultTeacherName = "Mrs. Ada Obi",
                    tutorsUiState = UiState.Success(listOf(ownedTutor)),
                    filteredTutors = listOf(com.example.data.TutorWithDistance(ownedTutor, 1.0)),
                    filter = com.example.data.TutorSearchFilter(),
                    clientSessionsState = UiState.Success(emptyList()),
                    teacherSessionsState = UiState.Success(emptyList()),
                    tutorVisitorCounts = mapOf("tut_owned_1" to 7),
                    isBusy = false,
                    onUpdateFilter = {},
                    onResetFilters = {},
                    onSaveTutorProfile = { _, _ -> },
                    onBookSession = { _, _, _, _, _, _, _, _ -> },
                    onUpdateSessionStatus = { _, _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tutor_owner_visits_summary").assertIsDisplayed()
        composeTestRule.onNode(
            androidx.compose.ui.test.hasAnyAncestor(hasTestTag("tutor_owner_visits_summary")) and
                androidx.compose.ui.test.hasText("7 people visited your page", substring = true),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun `buildHomeAdvertFeed preserves bannerImageUrl for adverts, schools, and tutors`() {
        val adWithBanner = com.example.data.AdvertWithDistance(
            advert = com.example.data.Advert(
                id = "ad_banner_1",
                advertiserName = "Crestview Academy",
                title = "Open Admissions",
                body = "Join our STEM showcase",
                category = "School Admissions",
                bannerImageUrl = "https://example.com/ad-banner.jpg"
            ),
            distanceKm = 1.0
        )
        val schoolWithBanner = SchoolWithDistance(
            school = School(
                id = "sch_banner_1",
                name = "Royal Oaks School",
                curriculum = "British",
                address = "Lekki, Lagos",
                bannerImageUrl = "https://example.com/school-banner.jpg"
            ),
            distanceKm = 2.0
        )
        val tutorWithBanner = com.example.data.TutorWithDistance(
            tutor = com.example.data.ProgressiveTutor(
                id = "tut_banner_1",
                fullName = "Mr. Tunde Balogun",
                headline = "IGCSE Maths",
                bannerImageUrl = "https://example.com/tutor-banner.jpg"
            ),
            distanceKm = 3.0
        )

        val feed = com.example.ui.buildHomeAdvertFeed(
            activeAdverts = listOf(adWithBanner),
            schools = listOf(schoolWithBanner),
            tutors = listOf(tutorWithBanner),
            selectedCategory = "All"
        )

        assertEquals(
            "https://example.com/ad-banner.jpg",
            feed.first { it.id == "ad_ad_banner_1" }.bannerImageUrl
        )
        assertEquals(
            "https://example.com/school-banner.jpg",
            feed.first { it.id == "school_sch_banner_1" }.bannerImageUrl
        )
        assertEquals(
            "https://example.com/tutor-banner.jpg",
            feed.first { it.id == "tutor_tut_banner_1" }.bannerImageUrl
        )
    }

    @Test
    fun `homeWidgetsService parses Pixel Weather JSON and Google News RSS XML for Education, Science Tech, Innovation`() {
        val weatherJson = """
            {
              "current": {
                "temperature_2m": 29.4,
                "apparent_temperature": 32.1,
                "weather_code": 0,
                "is_day": 1,
                "wind_speed_10m": 14.6,
                "relative_humidity_2m": 72
              },
              "daily": {
                "time": ["2026-11-27", "2026-11-28", "2026-11-29"],
                "temperature_2m_max": [31.2, 30.8, 29.9],
                "temperature_2m_min": [24.1, 23.7, 23.4],
                "weather_code": [0, 2, 61]
              }
            }
        """.trimIndent()

        val weather = com.example.data.HomeWidgetsService.parseOpenMeteoWeatherJson(
            jsonBody = weatherJson,
            locationLabel = "Lekki Phase 1, Lagos",
            latitude = 6.4474,
            longitude = 3.4737
        )
        assertEquals("Lekki Phase 1, Lagos", weather.locationLabel)
        assertEquals(29, weather.temperatureC)
        assertEquals(32, weather.apparentTempC)
        assertEquals(31, weather.highTempC)
        assertEquals(24, weather.lowTempC)
        assertEquals(72, weather.humidityPct)
        assertEquals(15, weather.windKmh)
        assertEquals("Sunny", weather.conditionLabel)
        assertEquals(3, weather.dailyForecast.size)
        assertEquals("Today", weather.dailyForecast.first().dayLabel)

        val educationRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <item>
                  <title>Global STEM Scholarship Portal Opens - EdTech Times</title>
                  <link>https://example.org/news/stem-scholarship</link>
                  <pubDate>Fri, 27 Nov 2026 08:00:00 GMT</pubDate>
                  <source>EdTech Times</source>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val sciTechRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <item>
                  <title>Quantum Battery Prototype Hits Lab Milestone - Science Daily</title>
                  <link>https://example.org/news/quantum-battery</link>
                  <pubDate>Fri, 27 Nov 2026 07:30:00 GMT</pubDate>
                  <source>Science Daily</source>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val innovationRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <item>
                  <title>Adaptive Classroom Robotics Expands Across Africa - Innovation Weekly</title>
                  <link>https://example.org/news/classroom-robotics</link>
                  <pubDate>Fri, 27 Nov 2026 06:15:00 GMT</pubDate>
                  <source>Innovation Weekly</source>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val eduArticles = com.example.data.HomeWidgetsService.parseGoogleNewsRssXml(
            educationRss,
            com.example.data.GoogleNewsTopic.EDUCATION
        )
        val sciArticles = com.example.data.HomeWidgetsService.parseGoogleNewsRssXml(
            sciTechRss,
            com.example.data.GoogleNewsTopic.SCIENCE_TECH
        )
        val innoArticles = com.example.data.HomeWidgetsService.parseGoogleNewsRssXml(
            innovationRss,
            com.example.data.GoogleNewsTopic.INNOVATION
        )

        assertEquals(1, eduArticles.size)
        assertEquals("Global STEM Scholarship Portal Opens", eduArticles.first().title)
        assertEquals("EdTech Times", eduArticles.first().source)
        assertEquals(com.example.data.GoogleNewsTopic.EDUCATION, eduArticles.first().topic)

        assertEquals(1, sciArticles.size)
        assertEquals(com.example.data.GoogleNewsTopic.SCIENCE_TECH, sciArticles.first().topic)

        assertEquals(1, innoArticles.size)
        assertEquals(com.example.data.GoogleNewsTopic.INNOVATION, innoArticles.first().topic)
    }

    @Test
    fun `home adverts screen renders Pixel Weather widget and Google News topics`() {
        val sampleWeather = com.example.data.PixelWeatherSnapshot(
            locationLabel = "Victoria Island, Lagos",
            latitude = 6.4281,
            longitude = 3.4219,
            temperatureC = 28,
            apparentTempC = 30,
            highTempC = 31,
            lowTempC = 24,
            humidityPct = 68,
            windKmh = 12,
            weatherCode = 2,
            isDay = true,
            conditionLabel = "Partly Cloudy",
            dailyForecast = listOf(
                com.example.data.DailyForecastItem("Today", 2, 24, 31),
                com.example.data.DailyForecastItem("Sat", 0, 25, 32),
                com.example.data.DailyForecastItem("Sun", 61, 23, 29)
            )
        )
        val sampleArticles = listOf(
            com.example.data.GoogleNewsArticle(
                id = "n_edu",
                title = "Cambridge & WAEC Digital Mock Prep Launches",
                source = "Education Desk",
                publishedAt = "1h ago",
                url = "https://news.google.com/edu",
                topic = com.example.data.GoogleNewsTopic.EDUCATION
            ),
            com.example.data.GoogleNewsArticle(
                id = "n_sci",
                title = "Next-Gen Solar Microgrids Power Rural Campuses",
                source = "Science & Tech Monitor",
                publishedAt = "2h ago",
                url = "https://news.google.com/scitech",
                topic = com.example.data.GoogleNewsTopic.SCIENCE_TECH
            ),
            com.example.data.GoogleNewsArticle(
                id = "n_inno",
                title = "Student Innovation Challenge Awards Robotics Grants",
                source = "Tech Innovators",
                publishedAt = "3h ago",
                url = "https://news.google.com/innovation",
                topic = com.example.data.GoogleNewsTopic.INNOVATION
            )
        )
        val selectedTopicState = androidx.compose.runtime.mutableStateOf<com.example.data.GoogleNewsTopic?>(null)
        var refreshWeatherClicked = false

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.HomeAdvertsScreen(
                    activeAdverts = emptyList(),
                    schools = emptyList(),
                    tutors = emptyList(),
                    isLoading = false,
                    isRefreshing = false,
                    isBusy = false,
                    defaultUserName = "Amina Bello",
                    defaultUserEmail = "amina@example.com",
                    onRefreshHome = {},
                    onOpenDiscover = {},
                    onOpenTutors = {},
                    onOpenPaidAdverts = {},
                    onSendAdvertInquiry = { _, _, _, _, _, _ -> },
                    weatherState = UiState.Success(sampleWeather),
                    locationLabel = "Victoria Island, Lagos",
                    googleNewsState = UiState.Success(sampleArticles),
                    selectedNewsTopic = selectedTopicState.value,
                    onSelectNewsTopic = { selectedTopicState.value = it },
                    onRefreshWeather = { refreshWeatherClicked = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pixel_weather_widget").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pixel_weather_location").assertTextContains("Victoria Island, Lagos")
        composeTestRule.onNodeWithTag("pixel_weather_temp").assertTextContains("28°")
        composeTestRule.onNodeWithTag("pixel_weather_gps_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pixel_weather_refresh_button").performClick()
        assertTrue(refreshWeatherClicked)

        composeTestRule.onNodeWithTag("home_feed_list").performScrollToNode(hasTestTag("google_news_section"))
        composeTestRule.onNodeWithTag("google_news_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("news_topic_education").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.GoogleNewsTopic.EDUCATION, selectedTopicState.value)
        composeTestRule.onNodeWithTag("google_news_card_n_edu").assertIsDisplayed()

        composeTestRule.onNodeWithTag("news_topic_science_tech").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.GoogleNewsTopic.SCIENCE_TECH, selectedTopicState.value)
        composeTestRule.onNodeWithTag("google_news_card_n_sci").assertIsDisplayed()

        composeTestRule.onNodeWithTag("news_topic_innovation").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.GoogleNewsTopic.INNOVATION, selectedTopicState.value)
        composeTestRule.onNodeWithTag("google_news_card_n_inno").assertIsDisplayed()
    }

    @Test
    fun `radio controller and online radio screen handle skip forward, skip backward, and pause`() {
        val controller = com.example.radio.RadioPlaybackController(enableAudioHardware = false)
        assertEquals(com.example.radio.RadioStatus.IDLE, controller.state.value.status)
        assertEquals(0, controller.state.value.currentIndex)

        controller.togglePlayPause()
        assertEquals(com.example.radio.RadioStatus.PLAYING, controller.state.value.status)

        controller.skipForward()
        assertEquals(1, controller.state.value.currentIndex)
        assertEquals(com.example.radio.RadioStatus.PLAYING, controller.state.value.status)

        controller.pause()
        assertEquals(com.example.radio.RadioStatus.PAUSED, controller.state.value.status)

        controller.skipBackward()
        assertEquals(0, controller.state.value.currentIndex)

        controller.skipBackward()
        assertEquals(com.example.radio.DefaultRadioStations.lastIndex, controller.state.value.currentIndex)

        composeTestRule.setContent {
            val state = controller.state.collectAsState()
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.OnlineRadioScreen(
                    radioState = state.value,
                    onTogglePlayPause = controller::togglePlayPause,
                    onPause = controller::pause,
                    onSkipForward = controller::skipForward,
                    onSkipBackward = controller::skipBackward,
                    onSelectStation = controller::selectAndPlay,
                    onAddCustomStation = controller::addAndPlayCustomStation,
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("online_radio_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("radio_skip_forward_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, controller.state.value.currentIndex)

        composeTestRule.onNodeWithTag("radio_skip_forward_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, controller.state.value.currentIndex)
        composeTestRule.onNodeWithTag("radio_current_station_title")
            .assertTextContains(com.example.radio.DefaultRadioStations[1].name)

        composeTestRule.onNodeWithTag("radio_skip_backward_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, controller.state.value.currentIndex)

        composeTestRule.onNodeWithTag("radio_explicit_pause_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.radio.RadioStatus.PAUSED, controller.state.value.status)
        composeTestRule.onNodeWithTag("radio_status_label").assertTextContains("PAUSED")
    }

    @Test
    fun `sidebar contains Online Radio and Emergency Call 112 phone link`() {
        val selectedTab = androidx.compose.runtime.mutableStateOf(com.example.ui.MainTab.HOME)
        var emergencyDialTriggered = false
        val snackbarHostState = androidx.compose.material3.SnackbarHostState()

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.SkolquikMinimalShell(
                    selectedTab = selectedTab.value,
                    displayName = "Amina Bello",
                    userEmail = "amina@example.com",
                    userRole = "parent",
                    isPlatformAdmin = false,
                    favoriteCount = 0,
                    snackbarHostState = snackbarHostState,
                    onSelectTab = { selectedTab.value = it },
                    onOpenProfile = {},
                    onSignOut = {},
                    onEmergencyCall = { emergencyDialTriggered = true }
                ) {
                    androidx.compose.material3.Text("Home Body")
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_sidebar_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_radio").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.MainTab.RADIO, selectedTab.value)

        composeTestRule.onNodeWithTag("open_sidebar_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sidebar_emergency_call_112")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(emergencyDialTriggered)
    }

    @Test
    fun `sidebar displays Educational Games entry and EducationalGamesScreen renders Compare Numbers and Fractions sims`() {
        val allGames = com.example.ui.EducationalGameCatalog.games
        assertEquals(3, allGames.size)
        val compareNumbers = allGames.filter { it.category == com.example.ui.GameCategory.COMPARE_NUMBERS }
        val fractions = allGames.filter { it.category == com.example.ui.GameCategory.FRACTIONS }
        assertEquals(1, compareNumbers.size)
        assertEquals(
            "https://phet.colorado.edu/sims/html/number-compare/latest/number-compare_en.html",
            compareNumbers.first().simUrl
        )
        assertEquals(2, fractions.size)
        assertEquals(
            "https://phet.colorado.edu/sims/html/fractions-intro/latest/fractions-intro_en.html",
            fractions[0].simUrl
        )
        assertEquals(
            "https://phet.colorado.edu/sims/html/fractions-mixed-numbers/latest/fractions-mixed-numbers_en.html",
            fractions[1].simUrl
        )

        val selectedTab = androidx.compose.runtime.mutableStateOf(com.example.ui.MainTab.HOME)
        val snackbarHostState = androidx.compose.material3.SnackbarHostState()

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.SkolquikMinimalShell(
                    selectedTab = selectedTab.value,
                    displayName = "Amina Bello",
                    userEmail = "amina@example.com",
                    userRole = "parent",
                    isPlatformAdmin = false,
                    favoriteCount = 0,
                    snackbarHostState = snackbarHostState,
                    onSelectTab = { selectedTab.value = it },
                    onOpenProfile = {},
                    onSignOut = {}
                ) {
                    if (selectedTab.value == com.example.ui.MainTab.GAMES) {
                        com.example.ui.EducationalGamesScreen(
                            onBack = { selectedTab.value = com.example.ui.MainTab.HOME },
                            enableEmbeddedWebView = false
                        )
                    } else {
                        androidx.compose.material3.Text("Home Body")
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_sidebar_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_games")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Educational Games", substring = true)
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.MainTab.GAMES, selectedTab.value)

        composeTestRule.onNodeWithTag("educational_games_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("game_category_compare_numbers").assertIsDisplayed()
        composeTestRule.onNodeWithTag("game_category_fractions").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("educational_games_screen")
            .performScrollToNode(hasTestTag("game_card_fractions_intro"))
        composeTestRule.onNodeWithTag("game_card_fractions_intro").assertIsDisplayed()

        composeTestRule.onNodeWithTag("educational_games_screen")
            .performScrollToNode(hasTestTag("game_card_fractions_mixed_numbers"))
        composeTestRule.onNodeWithTag("game_card_fractions_mixed_numbers").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("educational_games_screen")
            .performScrollToNode(hasTestTag("game_stage_card"))
        composeTestRule.onNodeWithTag("game_active_title").assertTextContains("Fractions: Mixed Numbers")
        composeTestRule.onNodeWithTag("game_fullscreen_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("game_fullscreen_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("game_exit_fullscreen_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("educational_games_screen")
            .performScrollToNode(hasTestTag("game_category_compare_numbers"))
        composeTestRule.onNodeWithTag("game_category_compare_numbers").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("educational_games_screen")
            .performScrollToNode(hasTestTag("game_card_number_compare"))
        composeTestRule.onNodeWithTag("game_card_number_compare").assertIsDisplayed()

        composeTestRule.onNodeWithTag("educational_games_screen")
            .performScrollToNode(hasTestTag("game_stage_card"))
        composeTestRule.onNodeWithTag("game_active_title").assertTextContains("Compare Numbers")
    }

    @Test
    fun `worldwide reverse geocoder formats global GPS locations into human readable place words`() {
        val lagosJson = """
            {
              "lat": "6.4474",
              "lon": "3.4737",
              "address": {
                "suburb": "Lekki Phase 1",
                "city": "Lagos",
                "state": "Lagos State",
                "country": "Nigeria"
              }
            }
        """.trimIndent()
        val londonJson = """
            {
              "lat": "51.5074",
              "lon": "-0.1278",
              "address": {
                "neighbourhood": "Westminster",
                "city": "London",
                "country": "United Kingdom"
              }
            }
        """.trimIndent()
        val tokyoJson = """
            {
              "lat": "35.6595",
              "lon": "139.7004",
              "address": {
                "city_district": "Shibuya",
                "city": "Tokyo",
                "country": "Japan"
              }
            }
        """.trimIndent()

        val lagos = com.example.data.GoogleMapsPlatformService.parseNominatimReverseGeocodingJson(lagosJson)
        val london = com.example.data.GoogleMapsPlatformService.parseNominatimReverseGeocodingJson(londonJson)
        val tokyo = com.example.data.GoogleMapsPlatformService.parseNominatimReverseGeocodingJson(tokyoJson)

        assertEquals("Lekki Phase 1, Lagos, Lagos State", lagos?.formattedAddress)
        assertEquals("Westminster, London, United Kingdom", london?.formattedAddress)
        assertEquals("Shibuya, Tokyo, Japan", tokyo?.formattedAddress)
    }

    @Test
    fun `nigeria news topic parses Nigerian RSS feeds and infers Channels TV, Premium Times, and Punch publishers`() {
        val nigeriaRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <item>
                  <title>FG Expands STEM Laboratories Across Federal Unity Colleges</title>
                  <link>https://www.channelstv.com/2026/11/27/stem-labs/</link>
                  <pubDate>Fri, 27 Nov 2026 09:00:00 GMT</pubDate>
                </item>
                <item>
                  <title>WAEC Announces Digital CBT Pilot in Lagos and Abuja - Premium Times</title>
                  <link>https://www.premiumtimesng.com/news/waec-cbt-pilot</link>
                  <pubDate>Fri, 27 Nov 2026 08:30:00 GMT</pubDate>
                  <source>Premium Times</source>
                </item>
                <item>
                  <title>National Innovation Hub Opens Youth Robotics Grants</title>
                  <link>https://punchng.com/innovation-youth-robotics/</link>
                  <pubDate>Fri, 27 Nov 2026 07:45:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = com.example.data.HomeWidgetsService.parseGoogleNewsRssXml(
            xml = nigeriaRss,
            topic = com.example.data.GoogleNewsTopic.NIGERIA
        )
        assertEquals(3, parsed.size)
        assertEquals("Channels TV", parsed[0].source)
        assertEquals("Premium Times", parsed[1].source)
        assertEquals("The Punch", parsed[2].source)
        assertEquals(com.example.data.GoogleNewsTopic.NIGERIA, parsed[0].topic)
    }

    @Test
    fun `online radio screen filters Nigeria stations and preserves playback controls`() {
        val nigeriaStations = com.example.radio.DefaultRadioStations.filter { it.country == "Nigeria" }
        assertTrue(nigeriaStations.any { it.name.contains("Nigeria Info") })
        assertTrue(nigeriaStations.any { it.name.contains("Cool FM") })
        assertTrue(nigeriaStations.any { it.name.contains("Wazobia FM") })

        val controller = com.example.radio.RadioPlaybackController(enableAudioHardware = false)
        composeTestRule.setContent {
            val state = controller.state.collectAsState()
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.OnlineRadioScreen(
                    radioState = state.value,
                    onTogglePlayPause = controller::togglePlayPause,
                    onPause = controller::pause,
                    onSkipForward = controller::skipForward,
                    onSkipBackward = controller::skipBackward,
                    onSelectStation = controller::selectAndPlay,
                    onAddCustomStation = controller::addAndPlayCustomStation,
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("online_radio_screen")
            .performScrollToNode(hasTestTag("radio_filter_nigeria"))
        composeTestRule.onNodeWithTag("radio_filter_nigeria").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("online_radio_screen")
            .performScrollToNode(hasTestTag("radio_station_ng_info_993"))
        composeTestRule.onNodeWithTag("radio_station_ng_info_993").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodes(hasTestTag("radio_station_somafm_groovesalad"))
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun `sidebar hides School Admin from Individuals and hides Platform Admin from non-admins`() {
        val roleState = androidx.compose.runtime.mutableStateOf("parent")
        val isAdminState = androidx.compose.runtime.mutableStateOf(false)
        val snackbarHostState = androidx.compose.material3.SnackbarHostState()

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.SkolquikMinimalShell(
                    selectedTab = com.example.ui.MainTab.HOME,
                    displayName = "Tunde Individual",
                    userEmail = "tunde@example.com",
                    userRole = roleState.value,
                    isPlatformAdmin = isAdminState.value,
                    favoriteCount = 0,
                    snackbarHostState = snackbarHostState,
                    onSelectTab = {},
                    onOpenProfile = {},
                    onSignOut = {}
                ) {
                    androidx.compose.material3.Text("Body")
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_sidebar_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodes(hasTestTag("nav_admin")).fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodes(hasTestTag("nav_platform_admin")).fetchSemanticsNodes().isEmpty())

        roleState.value = "admin"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_admin").performScrollTo().assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodes(hasTestTag("nav_platform_admin")).fetchSemanticsNodes().isEmpty())

        isAdminState.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_platform_admin").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `notifications dialog supports dismissing individual items and clearing all notifications`() {
        val items = androidx.compose.runtime.mutableStateOf(
            listOf(
                com.example.data.Announcement(
                    id = "ann_1",
                    schoolName = "Crestview Academy",
                    title = "Open Day Saturday",
                    body = "Campus tours start at 10am.",
                    category = "Events"
                ),
                com.example.data.Announcement(
                    id = "ann_2",
                    schoolName = "Lagoon High",
                    title = "Scholarship Exam",
                    body = "Registration closes Friday.",
                    category = "Admissions"
                )
            )
        )

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                NotificationsAlertDialog(
                    notifications = items.value,
                    onDismissNotification = { id -> items.value = items.value.filterNot { it.id == id } },
                    onClearAll = { items.value = emptyList() },
                    onOpenPortal = {},
                    onClose = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("notification_item_ann_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notification_item_ann_2").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dismiss_notification_ann_1").performClick()
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodes(hasTestTag("notification_item_ann_1")).fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("notification_item_ann_2").assertIsDisplayed()

        composeTestRule.onNodeWithTag("clear_notifications_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("notifications_empty_text").assertIsDisplayed()
    }

    @Test
    fun `platformAdminScreen shows full individual and school signup details and deletes violating users`() {
        val deletedLog = mutableListOf<Pair<String, String>>()
        val parentUser = com.example.data.UserProfile(
            id = "u_ind_1",
            userId = "u_ind_1",
            role = "parent",
            displayName = "Amina Individual",
            email = "amina@example.com",
            phone = "+2348011122233",
            homeAddress = "Lekki Phase 1, Lagos",
            homeLat = 6.4474,
            homeLng = 3.4737,
            searchRadiusKm = 15L,
            childSummary = "Primary 5 & JSS 1 student"
        )
        val schoolAdminUser = com.example.data.UserProfile(
            id = "u_sch_1",
            userId = "u_sch_1",
            role = "admin",
            displayName = "Greenhills Admissions",
            email = "admissions@greenhills.edu.ng",
            phone = "+2348099988877",
            homeAddress = "Victoria Island, Lagos",
            homeLat = 6.4281,
            homeLng = 3.4219,
            searchRadiusKm = 25L
        )
        val linkedSchool = School(
            id = "sch_1",
            adminUid = "u_sch_1",
            adminName = "Greenhills Admissions",
            name = "Greenhills Academy",
            address = "Victoria Island, Lagos",
            curriculum = "British & Nigerian",
            contactPhone = "+2348099988877",
            capacity = 600L,
            enrolledCount = 410L
        )

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                com.example.ui.PlatformAdminScreen(
                    isPlatformAdmin = true,
                    isBootstrappedOwner = true,
                    currentUserEmail = SchoolConstants.PLATFORM_ADMIN_EMAIL,
                    currentUserId = "owner_uid",
                    allUsersState = UiState.Success(listOf(parentUser, schoolAdminUser)),
                    schoolsState = UiState.Success(listOf(linkedSchool)),
                    platformAdminsState = UiState.Success(emptyList()),
                    visitStatsState = UiState.Success(null),
                    visitLogsState = UiState.Success(emptyList()),
                    onGrantAdmin = {},
                    onRevokeAdmin = { _, _, _ -> },
                    onDeleteUser = { u, reason -> deletedLog.add(u.userId to reason) },
                    onVerifySchool = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("platform_admin_screen")
            .performScrollToNode(hasTestTag("admin_user_card_u_ind_1"))
        composeTestRule.onNodeWithText("Phone: +2348011122233", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Child / Student Info: Primary 5 & JSS 1 student", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("platform_admin_screen")
            .performScrollToNode(hasTestTag("delete_user_button_u_ind_1"))
        composeTestRule.onNodeWithTag("delete_user_button_u_ind_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("delete_user_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_user_reason_input").performTextReplacement("Spam listings posted")
        composeTestRule.onNodeWithTag("confirm_delete_user_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("u_ind_1" to "Spam listings posted"), deletedLog)

        composeTestRule.onNodeWithTag("platform_admin_screen")
            .performScrollToNode(hasTestTag("admin_segment_1"))
        composeTestRule.onNodeWithTag("admin_segment_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("platform_admin_screen")
            .performScrollToNode(hasTestTag("admin_user_card_u_sch_1"))
        composeTestRule.onNodeWithText("Linked Schools (1)", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Greenhills Academy", substring = true).assertIsDisplayed()
    }
}
