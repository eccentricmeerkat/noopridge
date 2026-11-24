package com.noop.debug

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noop.ui.NoopTheme
import com.noop.ui.Ridge
import com.noop.ui.RidgeType
import com.noop.ui.ridge.RidgeMountainBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The three seeds side quest #2 compares (step 1: pick one before any page uses it). */
private val SEEDS = listOf(7, 21, 42)

/**
 * Debug-only backdrop lab (side quest #2, step 1): renders [RidgeMountainBackdrop] for three seeds at the screen's
 * full resolution, times each render, shows them side by side, and saves each as a PNG under the app's external
 * files dir (`backdrops/`) so they can be pulled and compared with the reference. Not routed from the app; release
 * builds do not contain it.
 */
class BackdropLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NoopTheme { BackdropLab() } }
    }
}

private data class Render(val seed: Int, val bitmap: Bitmap, val ms: Double, val pngBytes: Long)

@Composable
private fun BackdropLab() {
    val context = LocalContext.current
    var renders by remember { mutableStateOf<List<Render>>(emptyList()) }
    LaunchedEffect(Unit) {
        val dm = context.resources.displayMetrics
        renders = withContext(Dispatchers.Default) {
            val dir = File(context.getExternalFilesDir(null), "backdrops").apply { mkdirs() }
            SEEDS.map { seed ->
                val t0 = System.nanoTime()
                val bmp = RidgeMountainBackdrop.render(dm.widthPixels, dm.heightPixels, seed)
                val ms = (System.nanoTime() - t0) / 1e6
                val f = File(dir, "backdrop-seed$seed-${dm.widthPixels}x${dm.heightPixels}.png")
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Log.i("BackdropLab", "seed=$seed ${dm.widthPixels}x${dm.heightPixels} draw=${"%.1f".format(ms)}ms " +
                    "bitmap=${bmp.byteCount} bytes png=${f.length()} bytes")
                Render(seed, bmp, ms, f.length())
            }
        }
    }
    Column(
        Modifier.fillMaxSize().background(Ridge.bg000).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Ridge backdrops · step 1", style = RidgeType.listTitle, color = Ridge.ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            renders.forEach { r ->
                Column(Modifier.weight(1f)) {
                    Image(
                        r.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(r.bitmap.width / r.bitmap.height.toFloat()),
                    )
                    Text("seed ${r.seed}\n${"%.0f".format(r.ms)} ms\n${r.bitmap.byteCount / 1024} KB in memory", style = RidgeType.bodySm, color = Ridge.inkMuted)
                }
            }
        }
    }
}
