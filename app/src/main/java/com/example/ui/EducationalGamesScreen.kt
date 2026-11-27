package com.example.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class GameCategory(val numberedTitle: String, val chipLabel: String) {
    COMPARE_NUMBERS("1. Compare Numbers", "1. Compare Numbers"),
    FRACTIONS("2. Fractions", "2. Fractions")
}

data class EducationalGameItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: GameCategory,
    val simUrl: String,
    val badgeLabel: String
)

object EducationalGameCatalog {
    val games: List<EducationalGameItem> = listOf(
        EducationalGameItem(
            id = "number_compare",
            title = "Compare Numbers",
            subtitle = "Compare numbers with blocks, ten-frames, number lines, and >, <, = symbols.",
            category = GameCategory.COMPARE_NUMBERS,
            simUrl = "https://phet.colorado.edu/sims/html/number-compare/latest/number-compare_en.html",
            badgeLabel = "Interactive Lab"
        ),
        EducationalGameItem(
            id = "fractions_intro",
            title = "Fractions: Intro",
            subtitle = "Explore parts of a whole, build equivalent fractions, and beat matching challenges.",
            category = GameCategory.FRACTIONS,
            simUrl = "https://phet.colorado.edu/sims/html/fractions-intro/latest/fractions-intro_en.html",
            badgeLabel = "Fractions Lab"
        ),
        EducationalGameItem(
            id = "fractions_mixed_numbers",
            title = "Fractions: Mixed Numbers",
            subtitle = "Build mixed numbers and improper fractions across interactive game levels.",
            category = GameCategory.FRACTIONS,
            simUrl = "https://phet.colorado.edu/sims/html/fractions-mixed-numbers/latest/fractions-mixed-numbers_en.html",
            badgeLabel = "Mixed Numbers"
        )
    )

    fun gamesForCategory(category: GameCategory?): List<EducationalGameItem> =
        if (category == null) games else games.filter { it.category == category }

    fun buildResponsiveIframeHtml(game: EducationalGameItem): String = """
        <!doctype html>
        <html lang="en">
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
              html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #0B1220; overflow: hidden; }
              iframe { width: 100%; height: 100%; border: 0; display: block; }
            </style>
          </head>
          <body>
            <iframe src="${game.simUrl}" width="800" height="600" allowfullscreen></iframe>
          </body>
        </html>
    """.trimIndent()
}

@Composable
fun EducationalGamesScreen(
    onBack: () -> Unit,
    enableEmbeddedWebView: Boolean = true
) {
    var selectedCategory by remember { mutableStateOf<GameCategory?>(null) }
    var selectedGameId by remember { mutableStateOf(EducationalGameCatalog.games.first().id) }
    var isFullscreen by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    val visibleGames = remember(selectedCategory) {
        EducationalGameCatalog.gamesForCategory(selectedCategory)
    }

    val activeGame = remember(selectedGameId, visibleGames) {
        visibleGames.firstOrNull { it.id == selectedGameId } ?: visibleGames.first()
    }

    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("educational_games_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1D4ED8), Color(0xFF2563EB), Color(0xFF0EA5E9))
                        )
                    )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.SportsEsports,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Educational Games",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Interactive STEM math labs powered by PhET simulations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // Topic Filter Chips: All, 1. Compare Numbers, 2. Fractions
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All Games (${EducationalGameCatalog.games.size})") },
                        modifier = Modifier.testTag("game_category_all")
                    )
                }
                items(GameCategory.entries) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = category
                            val categoryGames = EducationalGameCatalog.gamesForCategory(category)
                            if (categoryGames.none { it.id == selectedGameId }) {
                                categoryGames.firstOrNull()?.let { selectedGameId = it.id }
                            }
                        },
                        label = { Text(category.chipLabel) },
                        modifier = Modifier.testTag("game_category_${category.name.lowercase()}")
                    )
                }
            }
        }

        // Selectable Interactive Game Cards
        item {
            Text(
                text = selectedCategory?.numberedTitle ?: "Choose a Math Simulation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(visibleGames, key = { it.id }) { game ->
            val isSelected = game.id == activeGame.id
            EducationalGameSelectorCard(
                game = game,
                isSelected = isSelected,
                onClick = { selectedGameId = game.id }
            )
        }

        // Interactive PhET Simulation Stage
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("game_stage_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeGame.category.numberedTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = activeGame.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("game_active_title")
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { reloadNonce++ },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("game_reload_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Reload game"
                                )
                            }
                            IconButton(
                                onClick = { isFullscreen = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("game_fullscreen_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fullscreen,
                                    contentDescription = "Fullscreen mode"
                                )
                            }
                            IconButton(
                                onClick = { runCatching { uriHandler.openUri(activeGame.simUrl) } },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("game_open_external_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open game in browser"
                                )
                            }
                        }
                    }

                    // 4:3 (800x600) interactive container matching the PhET embed dimensions
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0B1220))
                            .testTag("game_webview_container"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (enableEmbeddedWebView) {
                            PhETSimulationWebView(
                                game = activeGame,
                                reloadNonce = reloadNonce,
                                onOpenExternal = { runCatching { uriHandler.openUri(activeGame.simUrl) } },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SportsEsports,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = activeGame.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = activeGame.simUrl,
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = Color(0xFF0B1220),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("game_fullscreen_dialog")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activeGame.category.numberedTitle} • ${activeGame.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { reloadNonce++ },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reload game",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { isFullscreen = false },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("game_exit_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FullscreenExit,
                                contentDescription = "Exit fullscreen",
                                tint = Color.White
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (enableEmbeddedWebView) {
                            PhETSimulationWebView(
                                game = activeGame,
                                reloadNonce = reloadNonce,
                                onOpenExternal = { runCatching { uriHandler.openUri(activeGame.simUrl) } },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EducationalGameSelectorCard(
    game: EducationalGameItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("game_card_${game.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (game.category == GameCategory.COMPARE_NUMBERS) {
                            Icons.Filled.Calculate
                        } else {
                            Icons.Filled.PieChart
                        },
                        contentDescription = null,
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = game.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = game.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = if (isSelected) "Playing" else "Play",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun PhETSimulationWebView(
    game: EducationalGameItem,
    reloadNonce: Int,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember(game.id, reloadNonce) { mutableStateOf(true) }
    var loadError by remember(game.id, reloadNonce) { mutableStateOf<String?>(null) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = false
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUri = request?.url ?: return false
                            val isAllowedPhetHost = targetUri.scheme.equals("https", ignoreCase = true) &&
                                (targetUri.host.equals("phet.colorado.edu", ignoreCase = true) ||
                                    targetUri.host.orEmpty().endsWith(".colorado.edu", ignoreCase = true))
                            return !isAllowedPhetHost
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                loadError = error?.description?.toString()
                                    ?: "Unable to load interactive game. Check internet connectivity."
                            }
                        }
                    }

                    tag = "${game.id}:$reloadNonce"
                    loadUrl(game.simUrl)
                }
            },
            update = { webView ->
                val expectedKey = "${game.id}:$reloadNonce"
                if (webView.tag != expectedKey) {
                    webView.tag = expectedKey
                    loadError = null
                    isLoading = true
                    webView.loadUrl(game.simUrl)
                }
            }
        )

        if (isLoading && loadError == null) {
            Surface(
                color = Color(0xCC0B1220),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Loading ${game.title}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        loadError?.let { err ->
            Surface(
                color = Color(0xF00B1220),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onOpenExternal) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open in Browser", color = Color.White)
                        }
                        Button(onClick = { loadError = null; isLoading = true }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
