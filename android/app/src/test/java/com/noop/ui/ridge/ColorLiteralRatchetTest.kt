package com.noop.ui.ridge

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B-01 "no hard-coded colours in new code": a per-file ratchet on `Color(0x…)` literals under `ui/`, in the
 * style of `Tools/doc_comment_lint.py`. The baseline (`ridge/color_literal_baseline.txt`) is today's count per
 * file; a file may go down, never up, and a new file starts at zero. The token files are exempt: they are
 * where literals belong.
 *
 * Re-record after deliberately removing literals:
 *   NOOP_COLOR_BASELINE_RECORD=1 ./gradlew testFullDebugUnitTest --tests com.noop.ui.ridge.ColorLiteralRatchetTest
 */
class ColorLiteralRatchetTest {

    private val exempt = setOf("RidgeTheme.kt", "PaletteTokens.kt")
    private val literal = Regex("""\bColor\(0x[0-9A-Fa-f]{6,8}""")

    private fun uiDir(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/noop/ui").takeIf { d -> d.isDirectory } ?: File(it, "src/main/java/com/noop/ui") }
        .first { it.isDirectory }

    private fun counts(): Map<String, Int> = uiDir().walkTopDown()
        .filter { it.isFile && it.extension == "kt" && it.name !in exempt }
        .associate { it.relativeTo(uiDir()).path to literal.findAll(it.readText()).count() }
        .filterValues { it > 0 }

    @Test
    fun noUiFileGainsAColourLiteral() {
        val current = counts()
        val res = File(javaClass.classLoader!!.getResource("ridge/color_literal_baseline.txt")!!.toURI())
        if (System.getenv("NOOP_COLOR_BASELINE_RECORD") == "1") {
            val src = generateSequence(File("").absoluteFile) { it.parentFile }
                .map { File(it, "app/src/test/resources/ridge/color_literal_baseline.txt") }
                .firstOrNull { it.parentFile.isDirectory } ?: res
            src.writeText(current.toSortedMap().entries.joinToString("\n", postfix = "\n") { "${it.key}\t${it.value}" })
            return
        }
        val baseline = res.readLines().filter { it.isNotBlank() }
            .associate { it.substringBefore('\t') to it.substringAfter('\t').toInt() }
        val over = current.filter { (f, n) -> n > (baseline[f] ?: 0) }
            .map { (f, n) -> "$f: $n (baseline ${baseline[f] ?: 0})" }
        assertTrue("New Color(0x…) literals in ui/ — use Ridge / Palette tokens instead: $over", over.isEmpty())
    }
}
