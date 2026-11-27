package com.noop.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Check for updates": a single call to the project's PUBLIC releases API (GitHub) that reads the latest
 * version and compares it to the installed one. Nothing about the user is sent, and it never installs
 * anything.
 *
 * TWO callers share this, and the distinction matters to anyone auditing what the app does on its own:
 *  - the Hub > About "Check for updates" button (RS-1-1; it was in Settings), which runs only when tapped;
 *  - [UpdateWatch], the #1659 daily check, which runs at most once a day after onboarding and the Terms
 *    gate. It is ON by default and switching it off in Hub > About stops the request entirely.
 *
 * This header previously said the read happened ONLY on a tap. That stopped being true the moment the
 * automatic caller was added, and a false claim here is worse than none: it is the file someone opens to
 * answer "does this app poll?". Documented for real in docs/PRIVACY_SECURITY.md §1.1c.
 *
 * (Android already holds INTERNET for the opt-in AI Coach, so this adds no new capability.)
 */
object UpdateCheck {

    // The Ridge releases repo. The list endpoint, not `/releases/latest`: that one skips pre-releases, and the
    // Ridge betas are published as pre-releases. GitHub returns the list newest first.
    private const val ENDPOINT = "https://api.github.com/repos/eccentricmeerkat/noopridge/releases?per_page=5"

    sealed interface Result {
        data class UpToDate(val version: String) : Result
        data class Available(val version: String, val url: String, val notes: String) : Result
        object Failed : Result
    }

    /**
     * Classify the releases list against [currentVersion]. A repo with no published release (an empty list, or only
     * drafts) has nothing newer than this build, so it is [Result.UpToDate], not a failure. Malformed JSON throws, and
     * [check] turns that into [Result.Failed].
     */
    internal fun classify(body: String, currentVersion: String): Result {
        val list = JSONArray(body)
        val json = (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { !it.optBoolean("draft") }
            ?: return Result.UpToDate(currentVersion)
        val latest = json.getString("tag_name").removePrefix("v")
        val url = json.getString("html_url")
        val notes = cleanNotes(json.optString("body", ""))
        return if (isNewer(latest, currentVersion)) Result.Available(latest, url, notes) else Result.UpToDate(latest)
    }

    /** Fetch the latest release and classify it against [currentVersion]. Never throws — any error
     *  (offline, rate-limited, malformed) resolves to [Result.Failed] so the caller shows a calm
     *  "try again" rather than crashing. */
    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                if (conn.responseCode != 200) return@runCatching Result.Failed
                classify(conn.inputStream.bufferedReader().use { it.readText() }, currentVersion)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(Result.Failed)
    }

    /**
     * True iff [latest] is a strictly newer version than [current]. Compares dot-separated numeric
     * segments left to right — so `1.40 > 1.39` and `1.9 < 1.10`, both of which a plain string compare
     * gets WRONG. Tolerant of a leading "v" and any non-numeric suffix (e.g. the demo flavour's
     * "1.39-demo", or build metadata). Pure + unit-tested.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = segments(latest)
        val b = segments(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun segments(s: String): List<Int> =
        s.trim().removePrefix("v").removePrefix("V")
            .takeWhile { it.isDigit() || it == '.' }   // stop at "-demo" / build metadata
            .split(".")
            .mapNotNull { it.toIntOrNull() }

    /** Turn a GitHub release body into a short, readable "what's new" for an inline preview: drop the
     *  "Downloads"/footer boilerplate, strip the heaviest markdown markers, and cap the length. */
    fun cleanNotes(body: String): String {
        var s = body.substringBefore("Downloads")
        for (marker in listOf("**", "## ", "# ")) s = s.replace(marker, "")
        s = s.trim()
        return if (s.length > 700) s.take(700).trim() + "…" else s
    }
}
