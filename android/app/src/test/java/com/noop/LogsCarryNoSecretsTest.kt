package com.noop

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B2-Rb security audit S4: no log call writes an API key, bearer token or auth header. A source scan, in the style of
 * `PoseStillCoverageTest`: a line that logs (android.util.Log, or the strap-log `log(` / `logLine(` helpers) must not
 * name a secret. It does not prove the values are never logged by another route, but it stops the obvious mistake.
 */
class LogsCarryNoSecretsTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/noop").takeIf { d -> d.isDirectory } ?: File(it, "src/main/java/com/noop") }
        .first { it.isDirectory }

    private val logCall = Regex("""\b(Log\.[dwiev]\(|log\(|logLine\(|appendLine\()""")
    private val secret = Regex("""(?i)\b(apiKey|bearerToken|bearer |authHeader|authorization|customAuthHeader|installKey)\b""")

    @Test
    fun noLogLineNamesASecret() {
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, line) ->
                val code = line.substringBefore("//")
                logCall.containsMatchIn(code) && secret.containsMatchIn(code)
            }.map { (i, line) -> "${f.name}:${i + 1}: ${line.trim()}" }
        }.toList()
        assertTrue("log lines that name a secret: $offenders", offenders.isEmpty())
    }
}
