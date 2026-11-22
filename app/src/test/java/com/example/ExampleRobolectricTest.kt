package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.example.ui.theme.DeepPurplePrimary
import com.example.ui.theme.LightPurplePrimaryContainer
import com.example.ui.theme.SchoolCompassTheme
import org.junit.Assert.assertEquals
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
        assertEquals("SchoolCompass", appName)
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
    fun `purple theme exposes deep and light purple tokens and balanced ai model id`() {
        var capturedPrimary = androidx.compose.ui.graphics.Color.Unspecified
        var capturedPrimaryContainer = androidx.compose.ui.graphics.Color.Unspecified

        composeTestRule.setContent {
            SchoolCompassTheme(darkTheme = false, dynamicColor = false) {
                capturedPrimary = MaterialTheme.colorScheme.primary
                capturedPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(DeepPurplePrimary, capturedPrimary)
        assertEquals(LightPurplePrimaryContainer, capturedPrimaryContainer)
        assertEquals("gemini-3.8-flash", AiAdvisorModelOption.BALANCED.modelId)
        assertEquals("gemini-3.5-flash-lite", AiAdvisorModelOption.LITE.modelId)
        assertEquals("gemini-4-argon", AiAdvisorModelOption.DEEP.modelId)
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
}
