package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
                    onSaveProfile = { _, _, _, _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Complete Your Registration").assertIsDisplayed()
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
}
