package solutions.laxmi.omnicompiler.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/** Profile picture (grayscale, per the Modernist design system) with an initials tile fallback. */
@Composable
fun Avatar(imageUrl: String?, initials: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    if (imageUrl.isNullOrBlank()) {
        LanguageTile(initials, modifier, selected = true, size = size)
        return
    }
    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = "Profile picture",
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        modifier = modifier.size(size),
        loading = { Box(Modifier.size(size)) { LanguageTile(initials, selected = true, size = size) } },
        error = { LanguageTile(initials, selected = true, size = size) },
    )
}
