package com.noop.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Final review: a screen that shows the live heart rate must ask the strap to stream it. The strap only sends live HR
 * while something wants the realtime stream (`AppViewModel.requestRealtimeHr`, ref-counted). The Ridge Vital signs
 * rebuild dropped the request and the card then only moved when another screen was streaming. This pins the pairing:
 * a file that reads the smoothed live bpm (`displayHr(`, `vm.bpm`, `viewModel.bpm`) must also request the stream.
 */
class LiveHrScreensRequestStreamTest {

    private val uiDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/noop/ui").takeIf { d -> d.isDirectory } ?: File(it, "src/main/java/com/noop/ui") }
        .first { it.isDirectory }

    /** The helper that defines `displayHr` is not a screen. */
    private val notScreens = setOf("HealthLiveHrLogic.kt")

    @Test
    fun everyScreenThatShowsLiveHrRequestsTheStream() {
        val shows = Regex("""displayHr\(|\bvm\.bpm\b|\bviewModel\.bpm\b""")
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in notScreens }
            .filter { shows.containsMatchIn(it.readText()) }
            .filterNot { it.readText().contains("requestRealtimeHr()") }
            .map { it.name }.toList()
        assertTrue("these show live HR but never call requestRealtimeHr(): $offenders", offenders.isEmpty())
    }
}
