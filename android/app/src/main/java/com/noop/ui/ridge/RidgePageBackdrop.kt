package com.noop.ui.ridge

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.noop.ui.LocalPageScrollPx
import com.noop.ui.Ridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The seed the owner picked at side quest #2 step 1 (2026-11-13). */
const val RIDGE_BACKDROP_SEED = 42

/**
 * One rendered backdrop per process, keyed by pixel size: every page shares it, so a tab switch never redraws it.
 * Rendering takes 60–140 ms, so it runs off the main thread; [prewarm] starts it at launch.
 */
object RidgeBackdropCache {
    private val lock = Mutex()
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    private var key: Pair<Int, Int>? = null

    suspend fun prewarm(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        lock.withLock {
            if (key == widthPx to heightPx && bitmap != null) return
            val img = withContext(Dispatchers.Default) {
                RidgeMountainBackdrop.render(widthPx, heightPx, RIDGE_BACKDROP_SEED).asImageBitmap()
            }
            key = widthPx to heightPx
            bitmap = img
        }
    }
}

/**
 * The Ridge page backdrop (side quest #2): the generated mountains over the whole viewport, bled up behind the
 * status bar, fading into bg-100 by half height, so a page's own ground and the backdrop never meet at a seam.
 * Until the first render lands it is plain bg-100. Static and decorative, hidden from TalkBack. It rises with the page's
 * scroll, 1:1 (SQ3-2); the scaffold's navy canvas stays beneath.
 */
@Composable
fun RidgePageBackdrop() {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    // The full screen, status bar included, since the scaffold bleeds the slot up behind it.
    val heightDp = config.screenHeightDp.dp + 64.dp
    val wPx = with(density) { config.screenWidthDp.dp.roundToPx() }
    val hPx = with(density) { heightDp.roundToPx() }
    LaunchedEffect(wPx, hPx) { RidgeBackdropCache.prewarm(wPx, hPx) }
    // SQ3-2: the page's own scroll, so the mountains rise with the content, exactly 1:1, and leave the navy canvas
    // beneath. Read inside graphicsLayer: a layer move per frame, no recomposition.
    val scrollPx = LocalPageScrollPx.current
    Box(
        Modifier.fillMaxWidth().height(heightDp).graphicsLayer { translationY = -scrollPx() }
            .background(Ridge.bg100).clearAndSetSemantics {},
    ) {
        RidgeBackdropCache.bitmap?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxWidth().height(heightDp))
        }
    }
}
