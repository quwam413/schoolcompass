package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_BANNER_DATA_URI_CHARS = 150_000
private const val MAX_BANNER_EDGE_PX = 640

fun formatPageVisitorsLabel(count: Int): String = when (count) {
    1 -> "1 person visited your page"
    else -> "$count people visited your page"
}

suspend fun encodeBannerImageToDataUrl(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val largest = max(width, height).coerceAtLeast(1)
                if (largest > MAX_BANNER_EDGE_PX) {
                    val scale = MAX_BANNER_EDGE_PX.toFloat() / largest.toFloat()
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1)
                    )
                }
                decoder.isMutableRequired = false
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            var sampleSize = 1
            val largest = max(bounds.outWidth, bounds.outHeight)
            while (largest / sampleSize > MAX_BANNER_EDGE_PX * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: error("Unable to read selected image")
            val scaledLargest = max(decoded.width, decoded.height).coerceAtLeast(1)
            if (scaledLargest > MAX_BANNER_EDGE_PX) {
                val scale = MAX_BANNER_EDGE_PX.toFloat() / scaledLargest.toFloat()
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).roundToInt().coerceAtLeast(1),
                    (decoded.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
        }

        val baos = ByteArrayOutputStream()
        var quality = 72
        var dataUrl = ""
        while (quality >= 28) {
            baos.reset()
            rawBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            dataUrl = "data:image/jpeg;base64,$encoded"
            if (dataUrl.length <= MAX_BANNER_DATA_URI_CHARS) {
                return@runCatching dataUrl
            }
            quality -= 12
        }

        // Last-resort further downscale if source image is ultra-detailed
        val smaller = Bitmap.createScaledBitmap(
            rawBitmap,
            (rawBitmap.width * 0.65f).roundToInt().coerceAtLeast(160),
            (rawBitmap.height * 0.65f).roundToInt().coerceAtLeast(120),
            true
        )
        baos.reset()
        smaller.compress(Bitmap.CompressFormat.JPEG, 55, baos)
        val finalUrl = "data:image/jpeg;base64,${Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)}"
        require(finalUrl.length <= MAX_BANNER_DATA_URI_CHARS) {
            "Selected image is too large to sync. Try cropping or using an image URL."
        }
        finalUrl
    }
}

private fun decodeDataImageBitmap(dataUri: String): Bitmap? {
    val commaIdx = dataUri.indexOf(',')
    if (commaIdx <= 0 || commaIdx >= dataUri.length - 1) return null
    val base64Part = dataUri.substring(commaIdx + 1)
    return runCatching {
        val bytes = Base64.decode(base64Part, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

@Composable
fun BannerImage(
    imageUrl: String?,
    @DrawableRes fallbackResId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val trimmed = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    val decodedBitmap = remember(trimmed) {
        if (trimmed != null && trimmed.startsWith("data:image/", ignoreCase = true)) {
            decodeDataImageBitmap(trimmed)
        } else null
    }

    when {
        decodedBitmap != null -> {
            Image(
                bitmap = decodedBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
        trimmed != null && (
            trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("content://", ignoreCase = true)
            ) -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(trimmed)
                    .crossfade(true)
                    .build(),
                placeholder = painterResource(fallbackResId),
                error = painterResource(fallbackResId),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
        else -> {
            Image(
                painter = painterResource(fallbackResId),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
    }
}

@Composable
fun BannerImageEditorSection(
    bannerImageUrl: String?,
    @DrawableRes fallbackResId: Int,
    title: String = "Display Banner Picture",
    subtitle: String = "Shown on your public page and adverts across Skolquik",
    testTagPrefix: String = "banner",
    onBannerChanged: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showUrlField by remember { mutableStateOf(false) }
    var urlInput by remember(bannerImageUrl) {
        mutableStateOf(
            if (bannerImageUrl != null && !bannerImageUrl.startsWith("data:image/")) bannerImageUrl else ""
        )
    }
    var processingError by remember { mutableStateOf<String?>(null) }
    var isEncoding by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isEncoding = true
            processingError = null
            scope.launch {
                val result = encodeBannerImageToDataUrl(context, uri)
                isEncoding = false
                result.onSuccess { encodedUrl ->
                    urlInput = ""
                    onBannerChanged(encodedUrl)
                }.onFailure { err ->
                    processingError = err.localizedMessage ?: "Could not load selected picture."
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag("${testTagPrefix}_preview")
            ) {
                BannerImage(
                    imageUrl = bannerImageUrl,
                    fallbackResId = fallbackResId,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        if (!bannerImageUrl.isNullOrBlank()) "Custom Picture Active" else "Default Banner",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isEncoding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("${testTagPrefix}_pick_photo_button")
                ) {
                    Icon(
                        Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isEncoding) "Optimizing…" else if (bannerImageUrl.isNullOrBlank()) "Choose Picture" else "Change Picture")
                }

                if (!bannerImageUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            urlInput = ""
                            processingError = null
                            onBannerChanged(null)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("${testTagPrefix}_clear_button")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Reset picture",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showUrlField = !showUrlField },
                    modifier = Modifier.testTag("${testTagPrefix}_toggle_url_button")
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showUrlField) "Hide Image URL" else "Or paste an image URL")
                }
            }

            if (showUrlField) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { raw ->
                        urlInput = raw
                        val trimmed = raw.trim()
                        if (trimmed.isEmpty()) {
                            processingError = null
                        } else if (trimmed.startsWith("https://", ignoreCase = true) ||
                            trimmed.startsWith("http://", ignoreCase = true)
                        ) {
                            processingError = null
                            onBannerChanged(trimmed)
                        } else {
                            processingError = "Image URL must start with https://"
                        }
                    },
                    label = { Text("Image URL (https://...)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${testTagPrefix}_url_input")
                )
            }

            processingError?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun PageVisitorsBadge(
    visitorCount: Int,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = if (testTag != null) modifier.testTag(testTag) else modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Rounded.Visibility,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                formatPageVisitorsLabel(visitorCount),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
