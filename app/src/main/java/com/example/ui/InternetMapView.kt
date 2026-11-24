package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.SchoolSearchFilter
import com.example.data.SchoolWithDistance
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh

object MercatorProjection {
    private const val TILE_SIZE = 256.0

    fun latLngToWorldPx(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
        val mapSize = TILE_SIZE * 2.0.pow(zoom)
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val x = ((lng + 180.0) / 360.0) * mapSize
        val sinLat = sin(Math.toRadians(clampedLat))
        val y = (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)) * mapSize
        return x to y
    }

    fun worldPxToLatLng(worldX: Double, worldY: Double, zoom: Int): Pair<Double, Double> {
        val mapSize = TILE_SIZE * 2.0.pow(zoom)
        val wrappedX = ((worldX % mapSize) + mapSize) % mapSize
        val lng = (wrappedX / mapSize) * 360.0 - 180.0
        val n = PI - (2.0 * PI * worldY.coerceIn(0.0, mapSize)) / mapSize
        val lat = Math.toDegrees(atan(sinh(n)))
        return lat.coerceIn(-85.05112878, 85.05112878) to lng
    }

    fun metersPerPixel(lat: Double, zoom: Int): Double {
        val clampedLat = lat.coerceIn(-85.0, 85.0)
        return (156543.03392 * cos(Math.toRadians(clampedLat)) / 2.0.pow(zoom)).coerceAtLeast(0.05)
    }
}

internal fun buildLeafletMapPayloadJson(
    filter: SchoolSearchFilter,
    filteredSchools: List<SchoolWithDistance>,
    primaryHex: String
): String {
    val zoom = zoomForRadiusKm(filter.radiusKm).roundToInt().coerceIn(6, 17)
    val markers = JSONArray()
    filteredSchools.forEach { item ->
        val s = item.school
        val isRegistered = s.hasPlatformAdmin()
        val pinColorHex = when {
            !isRegistered -> "#E59A24"
            s.institutionType == "University" -> "#0891B2"
            s.institutionType in listOf("Polytechnic", "College of Education", "Technical / Vocational") -> "#C026D3"
            s.specialSchool -> "#7C3AED"
            s.curriculum == "Nigerian & British" -> "#2E7D6B"
            s.curriculum == "British" -> "#2563EB"
            s.curriculum == "Nigerian" -> "#EA580C"
            else -> primaryHex
        }
        markers.put(
            JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("lat", s.latitude)
                put("lng", s.longitude)
                put("institutionType", s.institutionType)
                put("curriculum", s.curriculum)
                put("fees", s.formattedFeeRange())
                put("distanceKm", String.format(Locale.US, "%.1f", item.distanceKm))
                put("registered", isRegistered)
                put("color", pinColorHex)
            }
        )
    }
    return JSONObject().apply {
        put("centerLat", filter.centerLat)
        put("centerLng", filter.centerLng)
        put("centerLabel", filter.centerLabel)
        put("radiusKm", filter.radiusKm)
        put("limitByRadius", filter.limitByRadius)
        put("lockToNigeria", filter.lockToNigeria)
        put("showNigeriaStates", filter.showNigeriaStates)
        put("zoom", zoom)
        put("primaryColor", primaryHex)
        put("schools", markers)
    }.toString()
}

internal fun buildLeafletHtmlDocument(
    initialLat: Double,
    initialLng: Double,
    initialZoom: Int,
    initialLabel: String,
    primaryHex: String,
    lockToNigeria: Boolean = true
): String {
    val safeLat = String.format(Locale.US, "%.6f", initialLat)
    val safeLng = String.format(Locale.US, "%.6f", initialLng)
    val escapedLabelJson = JSONObject.quote(initialLabel)
    val escapedColorJson = JSONObject.quote(primaryHex)
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes">
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <style>
            html, body, #map {
              height: 100%;
              width: 100%;
              margin: 0;
              padding: 0;
              background: #F5EEFF;
              font-family: system-ui, -apple-system, sans-serif;
            }
            .sc-popup-title {
              font-weight: 700;
              font-size: 13px;
              color: #2B0059;
              margin-bottom: 3px;
            }
            .sc-popup-meta {
              font-size: 11px;
              color: #4A3B5E;
              margin-bottom: 6px;
              line-height: 1.35;
            }
            .sc-popup-btn {
              display: inline-block;
              background: $primaryHex;
              color: #FFFFFF;
              border: none;
              border-radius: 8px;
              padding: 6px 10px;
              font-size: 11px;
              font-weight: 600;
              cursor: pointer;
            }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            (function() {
              function escapeHtml(text) {
                if (text === null || text === undefined) return '';
                return String(text)
                  .replace(/&/g, '&amp;')
                  .replace(/</g, '&lt;')
                  .replace(/>/g, '&gt;')
                  .replace(/"/g, '&quot;')
                  .replace(/'/g, '&#39;');
              }

              var nigeriaBounds = L.latLngBounds([4.0, 2.5], [14.0, 14.9]);
              var map = L.map('map', {
                zoomControl: false,
                attributionControl: false,
                minZoom: ${if (lockToNigeria) 6 else 2}
              }).setView([$safeLat, $safeLng], $initialZoom);

              if (${if (lockToNigeria) "true" else "false"}) {
                map.setMaxBounds(nigeriaBounds.pad(0.2));
              }

              var osmTiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '© OpenStreetMap contributors'
              }).addTo(map);

              var fallbackAdded = false;
              osmTiles.on('tileerror', function() {
                if (!fallbackAdded) {
                  fallbackAdded = true;
                  L.tileLayer('https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png', {
                    maxZoom: 19
                  }).addTo(map);
                }
              });

              // State boundaries (geoBoundaries, open data for Nigeria ADM1)
              var stateBoundaryLayer = null;
              var statesRequested = false;
              var wantStatesVisible = true;

              function ensureNigeriaStatesLoaded() {
                if (statesRequested) {
                  if (stateBoundaryLayer) {
                    if (wantStatesVisible && !map.hasLayer(stateBoundaryLayer)) {
                      stateBoundaryLayer.addTo(map);
                      stateBoundaryLayer.bringToBack();
                    } else if (!wantStatesVisible && map.hasLayer(stateBoundaryLayer)) {
                      map.removeLayer(stateBoundaryLayer);
                    }
                  }
                  return;
                }
                statesRequested = true;
                fetch('https://www.geoboundaries.org/api/current/gbOpen/NGA/ADM1/')
                  .then(function(r) { return r.json(); })
                  .then(function(meta) {
                    if (!meta || !meta.gjDownloadURL) throw new Error('No GeoJSON URL');
                    return fetch(meta.gjDownloadURL);
                  })
                  .then(function(r) { return r.json(); })
                  .then(function(geo) {
                    stateBoundaryLayer = L.geoJSON(geo, {
                      style: { color: '#008751', weight: 1.5, fillOpacity: 0.05 },
                      onEachFeature: function(f, layer) {
                        var stateName = (f && f.properties && f.properties.shapeName) ? f.properties.shapeName : 'State';
                        layer.bindTooltip(stateName, { sticky: true });
                        layer.on('mouseover', function() { layer.setStyle({ fillOpacity: 0.22 }); });
                        layer.on('mouseout', function() { layer.setStyle({ fillOpacity: 0.05 }); });
                      }
                    });
                    if (wantStatesVisible) {
                      stateBoundaryLayer.addTo(map);
                      stateBoundaryLayer.bringToBack();
                    }
                  })
                  .catch(function(err) {
                    console.warn('Nigeria boundaries failed to load', err);
                  });
              }

              ensureNigeriaStatesLoaded();

              var centerMarker = L.marker([$safeLat, $safeLng]).addTo(map)
                .bindPopup('<div class="sc-popup-title">' + escapeHtml($escapedLabelJson) + '</div><div class="sc-popup-meta">Active search center</div>');

              var radiusCircle = L.circle([$safeLat, $safeLng], {
                radius: 15000,
                color: $escapedColorJson,
                weight: 2,
                dashArray: '8, 6',
                fillColor: $escapedColorJson,
                fillOpacity: 0.14
              }).addTo(map);

              var schoolLayer = L.layerGroup().addTo(map);
              var lastCenterKey = '';
              var lastZoom = $initialZoom;

              map.on('click', function(e) {
                if (window.SchoolCompassBridge && e && e.latlng) {
                  window.SchoolCompassBridge.onPinLocation(e.latlng.lat, e.latlng.lng);
                }
              });

              window.selectSchoolFromPopup = function(schoolId) {
                if (window.SchoolCompassBridge) {
                  window.SchoolCompassBridge.onSelectSchool(String(schoolId));
                }
              };

              window.zoomMap = function(delta) {
                var target = Math.max(map.getMinZoom() || 4, Math.min(18, map.getZoom() + delta));
                map.setZoom(target);
              };

              window.fitNigeriaOverview = function() {
                map.fitBounds(nigeriaBounds, { padding: [12, 12] });
              };

              window.updateSchoolCompassMap = function(payload) {
                if (!payload) return;
                var lat = Number(payload.centerLat);
                var lng = Number(payload.centerLng);
                var radMeters = Number(payload.radiusKm || 15) * 1000;
                var limitByRadius = payload.limitByRadius !== false;
                var lockNg = payload.lockToNigeria !== false;
                wantStatesVisible = payload.showNigeriaStates !== false;
                var z = Number(payload.zoom || 12);
                var primary = payload.primaryColor || '#4A148C';

                if (lockNg) {
                  map.setMinZoom(6);
                  map.setMaxBounds(nigeriaBounds.pad(0.2));
                } else {
                  map.setMinZoom(2);
                  map.setMaxBounds(null);
                }
                ensureNigeriaStatesLoaded();

                var centerKey = lat.toFixed(4) + ',' + lng.toFixed(4);
                if (centerKey !== lastCenterKey || z !== lastZoom) {
                  map.setView([lat, lng], z, { animate: true });
                  lastCenterKey = centerKey;
                  lastZoom = z;
                }

                centerMarker.setLatLng([lat, lng]);
                centerMarker.setPopupContent(
                  '<div class="sc-popup-title">' + escapeHtml(payload.centerLabel) + '</div>' +
                  '<div class="sc-popup-meta">' +
                  (limitByRadius ? (escapeHtml(payload.radiusKm) + ' km search radius') : 'All distances visible') +
                  '</div>'
                );

                if (limitByRadius) {
                  if (!map.hasLayer(radiusCircle)) radiusCircle.addTo(map);
                  radiusCircle.setLatLng([lat, lng]);
                  radiusCircle.setRadius(radMeters);
                  radiusCircle.setStyle({ color: primary, fillColor: primary });
                } else {
                  if (map.hasLayer(radiusCircle)) map.removeLayer(radiusCircle);
                }

                schoolLayer.clearLayers();
                var list = payload.schools || [];
                for (var i = 0; i < list.length; i++) {
                  (function(s) {
                    var marker = L.circleMarker([s.lat, s.lng], {
                      radius: 8,
                      color: '#FFFFFF',
                      weight: 2.5,
                      fillColor: s.color || primary,
                      fillOpacity: 0.95
                    });
                    var actionLabel = s.registered ? 'Open Details' : 'View & Act';
                    var popupEl = document.createElement('div');
                    popupEl.innerHTML =
                      '<div class="sc-popup-title">' + escapeHtml(s.name) + '</div>' +
                      '<div class="sc-popup-meta">' +
                        escapeHtml(s.institutionType) + ' • ' + escapeHtml(s.curriculum) + '<br>' +
                        escapeHtml(s.fees) + ' • ' + escapeHtml(s.distanceKm) + ' km away' +
                      '</div>';
                    var btn = document.createElement('button');
                    btn.className = 'sc-popup-btn';
                    btn.textContent = actionLabel;
                    btn.onclick = function() {
                      window.selectSchoolFromPopup(s.id);
                    };
                    popupEl.appendChild(btn);
                    marker.bindPopup(popupEl);
                    marker.addTo(schoolLayer);
                  })(list[i]);
                }
              };
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}

private class LeafletBridge(
    private val mainHandler: Handler,
    private val onPinLocation: (Double, Double) -> Unit,
    private val onSelectSchoolId: (String) -> Unit
) {
    @JavascriptInterface
    fun onPinLocation(lat: Double, lng: Double) {
        if (!lat.isNaN() && !lng.isNaN() && lat in -90.0..90.0 && lng in -180.0..180.0) {
            mainHandler.post { onPinLocation.invoke(lat, lng) }
        }
    }

    @JavascriptInterface
    fun onSelectSchool(schoolId: String) {
        mainHandler.post { onSelectSchoolId.invoke(schoolId) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun InternetStreetMapView(
    filter: SchoolSearchFilter,
    filteredSchools: List<SchoolWithDistance>,
    isNetworkOnline: Boolean,
    fitNigeriaOverviewTick: Int = 0,
    onPinLocation: (Double, Double) -> Unit,
    onSelectSchool: (SchoolWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryHex = remember(primaryColor) {
        String.format(Locale.US, "#%06X", 0xFFFFFF and primaryColor.toArgb())
    }
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val latestSchools by rememberUpdatedState(filteredSchools)
    val latestOnPin by rememberUpdatedState(onPinLocation)
    val latestOnSelectSchool by rememberUpdatedState(onSelectSchool)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }
    var webViewUnavailable by remember { mutableStateOf(false) }

    val payloadJson = remember(filter, filteredSchools, primaryHex) {
        buildLeafletMapPayloadJson(
            filter = filter,
            filteredSchools = filteredSchools,
            primaryHex = primaryHex
        )
    }
    val latestPayloadJson by rememberUpdatedState(payloadJson)

    LaunchedEffect(pageLoaded, payloadJson) {
        if (pageLoaded) {
            runCatching {
                webViewRef?.evaluateJavascript("window.updateSchoolCompassMap($payloadJson);", null)
            }
        }
    }

    LaunchedEffect(fitNigeriaOverviewTick) {
        if (fitNigeriaOverviewTick > 0 && pageLoaded) {
            runCatching {
                webViewRef?.evaluateJavascript("window.fitNigeriaOverview();", null)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef?.let { wv ->
                    wv.stopLoading()
                    wv.removeJavascriptInterface("SchoolCompassBridge")
                    wv.destroy()
                }
            }
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
            .testTag("internet_map_view")
    ) {
        if (!webViewUnavailable) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    try {
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(AndroidColor.parseColor("#F5EEFF"))
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                allowFileAccess = false
                                allowContentAccess = false
                                setSupportZoom(true)
                                builtInZoomControls = false
                                displayZoomControls = false
                                userAgentString = "SchoolCompassAndroid/1.0 (LeafletOSM)"
                            }

                            // Prevent parent LazyColumn from stealing vertical map pan gestures
                            setOnTouchListener { v, event ->
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        v.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                                false
                            }

                            val bridge = LeafletBridge(
                                mainHandler = mainHandler,
                                onPinLocation = { lat, lng -> latestOnPin(lat, lng) },
                                onSelectSchoolId = { id ->
                                    latestSchools.firstOrNull { it.school.id == id }?.let { match ->
                                        latestOnSelectSchool(match)
                                    }
                                }
                            )
                            addJavascriptInterface(bridge, "SchoolCompassBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    pageLoaded = true
                                    runCatching {
                                        view?.evaluateJavascript("window.updateSchoolCompassMap($latestPayloadJson);", null)
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                }
                            }

                            val initialZoom = zoomForRadiusKm(filter.radiusKm).roundToInt().coerceIn(6, 17)
                            val html = buildLeafletHtmlDocument(
                                initialLat = filter.centerLat,
                                initialLng = filter.centerLng,
                                initialZoom = initialZoom,
                                initialLabel = filter.centerLabel,
                                primaryHex = primaryHex,
                                lockToNigeria = filter.lockToNigeria
                            )
                            loadDataWithBaseURL(
                                "https://schoolcompass.app/",
                                html,
                                "text/html",
                                "UTF-8",
                                null
                            )
                            webViewRef = this
                        }
                    } catch (_: Throwable) {
                        webViewUnavailable = true
                        android.view.View(ctx)
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${filter.centerLabel} (${String.format(Locale.US, "%.3f", filter.centerLat)}, ${String.format(Locale.US, "%.3f", filter.centerLng)})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${filteredSchools.size} schools matched within ${filter.radiusKm} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Zoom + / - Controls (bottom right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledTonalIconButton(
                onClick = { runCatching { webViewRef?.evaluateJavascript("window.zoomMap(1);", null) } },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("map_zoom_in")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom in")
            }
            FilledTonalIconButton(
                onClick = { runCatching { webViewRef?.evaluateJavascript("window.zoomMap(-1);", null) } },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("map_zoom_out")
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
            }
        }

        // Attribution / Offline pill (bottom left)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = if (isNetworkOnline) "© OpenStreetMap • geoBoundaries" else "Offline • Reconnect for tiles",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
