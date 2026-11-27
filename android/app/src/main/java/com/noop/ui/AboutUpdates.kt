package com.noop.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.BuildConfig
import com.noop.R
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.update.UpdateCheck
import com.noop.update.UpdateWatch
import kotlinx.coroutines.launch

/*
 * RS-1-1 item 1 (`About.dc.html`, the Updates section): "Check for updates" moved here from Settings > About, which
 * is gone. Same single user-initiated read of the public releases list (UpdateCheck), the same #1659 daily-check
 * switch, and the same Download row when a newer release exists. The status line never invents a time: with no
 * check on record it says so.
 */

/** What the Updates card's status line says. */
internal sealed interface AboutUpdateStatus {
    object Checking : AboutUpdateStatus
    data class UpToDate(val version: String) : AboutUpdateStatus
    object Failed : AboutUpdateStatus
    data class Available(val version: String, val url: String, val notes: String) : AboutUpdateStatus
    /** No check this visit; the newest check on record (manual or automatic) was at [atMs]. */
    data class LastChecked(val atMs: Long) : AboutUpdateStatus
    object NeverChecked : AboutUpdateStatus
}

/** The card's button label: "Check for updates" before a result, "Checking…" while busy, "Check again" after one. */
internal enum class AboutUpdateButton { CHECK, CHECKING, CHECK_AGAIN }

/**
 * The status for this visit: a check in flight wins, then this visit's result, then the newest check on record
 * ([lastCheckedAtMs], 0 when none). Pure, so every state is tested without a network or a clock.
 */
internal fun aboutUpdateStatus(checking: Boolean, result: UpdateCheck.Result?, lastCheckedAtMs: Long): AboutUpdateStatus = when {
    checking -> AboutUpdateStatus.Checking
    result is UpdateCheck.Result.UpToDate -> AboutUpdateStatus.UpToDate(result.version)
    result is UpdateCheck.Result.Available -> AboutUpdateStatus.Available(result.version, result.url, result.notes)
    result is UpdateCheck.Result.Failed -> AboutUpdateStatus.Failed
    lastCheckedAtMs > 0L -> AboutUpdateStatus.LastChecked(lastCheckedAtMs)
    else -> AboutUpdateStatus.NeverChecked
}

internal fun aboutUpdateButton(status: AboutUpdateStatus): AboutUpdateButton = when (status) {
    AboutUpdateStatus.Checking -> AboutUpdateButton.CHECKING
    is AboutUpdateStatus.LastChecked, AboutUpdateStatus.NeverChecked -> AboutUpdateButton.CHECK
    else -> AboutUpdateButton.CHECK_AGAIN
}

/** Hub > About's Updates card, with the daily-check switch under it (D3). */
@Composable
internal fun AboutUpdatesCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateCheck.Result?>(null) }
    var lastAt by remember { mutableLongStateOf(UpdateWatch.lastCheckedAt(context)) }
    var autoCheck by remember { mutableStateOf(UpdateWatch.isEnabled(context)) }
    val status = aboutUpdateStatus(checking, result, lastAt)
    AboutUpdatesCardContent(
        status = status,
        version = BuildConfig.VERSION_NAME,
        autoCheck = autoCheck,
        onCheck = {
            if (!checking) {
                checking = true
                result = null
                scope.launch {
                    val r = UpdateCheck.check(BuildConfig.VERSION_NAME)
                    val now = System.currentTimeMillis()
                    UpdateWatch.recordManualCheck(context, now)
                    lastAt = now
                    result = r
                    checking = false
                }
            }
        },
        onAutoCheck = {
            autoCheck = it
            UpdateWatch.setEnabled(context, it)
        },
        onDownload = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
            }
        },
    )
}

/** The Updates card's face for one [status] (stateless, so each state has a snapshot). */
@Composable
internal fun AboutUpdatesCardContent(
    status: AboutUpdateStatus,
    version: String,
    autoCheck: Boolean,
    onCheck: () -> Unit,
    onAutoCheck: (Boolean) -> Unit,
    onDownload: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
        GlassCard(
            Modifier.fillMaxWidth(),
            radius = Ridge.radiusLg,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            buildAnnotatedString {
                                append("NOOP ")
                                withStyle(SpanStyle(color = Ridge.brand, fontWeight = FontWeight.Bold)) { append("v$version") }
                            },
                            style = TextStyle(fontFamily = RidgeType.sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
                            color = Ridge.ink,
                        )
                        UpdateStatusLine(status)
                    }
                    UpdatePillButton(
                        label = when (aboutUpdateButton(status)) {
                            AboutUpdateButton.CHECK -> stringResource(R.string.l10n_settings_screen_check_for_updates_736b9062)
                            AboutUpdateButton.CHECKING -> stringResource(R.string.l10n_settings_screen_checking_820d6004)
                            AboutUpdateButton.CHECK_AGAIN -> stringResource(R.string.about_upd_check_again)
                        },
                        enabled = status != AboutUpdateStatus.Checking,
                        onClick = onCheck,
                    )
                }
                (status as? AboutUpdateStatus.Available)?.let { UpdateAvailableRow(it, onDownload) }
            }
        }
        RidgeToggleRow(
            title = stringResource(R.string.l10n_settings_screen_check_automatically_7cd229d2),
            note = stringResource(R.string.l10n_settings_screen_once_a_day_noop_asks_github_5683aad3),
            on = autoCheck,
            onChange = onAutoCheck,
        )
    }
}

/** The muted status line; a mint dot and mint text once this visit's check says the build is current. */
@Composable
private fun UpdateStatusLine(status: AboutUpdateStatus) {
    val upToDate = status is AboutUpdateStatus.UpToDate
    val text = when (status) {
        AboutUpdateStatus.Checking -> stringResource(R.string.l10n_settings_screen_checking_820d6004)
        is AboutUpdateStatus.UpToDate -> stringResource(R.string.about_upd_latest)
        AboutUpdateStatus.Failed -> stringResource(R.string.l10n_settings_screen_couldn_t_check_try_again_b3c885d9)
        is AboutUpdateStatus.Available -> stringResource(R.string.l10n_settings_screen_version_avail_version_is_available_5b401bd4, status.version)
        is AboutUpdateStatus.LastChecked -> stringResource(
            R.string.about_upd_last_checked,
            DateUtils.getRelativeTimeSpanString(status.atMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
        )
        AboutUpdateStatus.NeverChecked -> stringResource(R.string.about_upd_never)
    }
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (upToDate) Box(Modifier.size(8.dp).clip(CircleShape).background(Ridge.brand))
        Text(
            text,
            style = TextStyle(fontFamily = RidgeType.sans, fontSize = 12.sp, lineHeight = 16.sp),
            color = if (upToDate) Ridge.brand else Ridge.inkMuted,
        )
    }
}

/** The board's small secondary pill (36 dp drawn, 48 dp touch target), sentence case as on the board. */
@Composable
private fun UpdatePillButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Ridge.radiusPill)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = 36.dp)
                .clip(shape)
                .background(Ridge.glassFillStrong, shape)
                .border(1.dp, Ridge.inkMuted, shape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(fontFamily = RidgeType.sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp), color = Ridge.ink, maxLines = 1)
        }
    }
}

/** A newer release: its version, a Download straight to the release page, and its trimmed notes. */
@Composable
private fun UpdateAvailableRow(avail: AboutUpdateStatus.Available, onDownload: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ridge.radiusMd))
            .background(Ridge.bg000.copy(alpha = 0.4f))
            .border(1.dp, Ridge.brand.copy(alpha = 0.3f), RoundedCornerShape(Ridge.radiusMd))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(Ridge.space2),
    ) {
        com.noop.ui.ridge.RidgeButton(
            text = stringResource(R.string.l10n_settings_screen_download_a479c9c3),
            onClick = { onDownload(avail.url) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (avail.notes.isNotEmpty()) {
            Text(
                avail.notes,
                style = RidgeType.bodySm, color = Ridge.inkMuted,
                modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

/** The footer's project-home link (moved from Settings > About, D3). */
@Composable
internal fun AboutProjectLink() {
    val context = LocalContext.current
    Column(
        Modifier.clickable(role = Role.Button) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RidgeReleaseNotes.PROJECT_URL)))
            } catch (_: ActivityNotFoundException) {
            }
        }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.l10n_settings_screen_project_home_source_994627c1), style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = Ridge.brand)
        Text(stringResource(R.string.l10n_settings_screen_github_code_releases_issues_and_the_50b41e25), style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp), color = Ridge.inkSubtle)
    }
}

/**
 * The protocol credits and the full medical disclaimer, moved from Settings > About (RS-1-1 item 2) so neither is lost
 * with that card: upstream credits stay intact (AGENTS.md), and the longer disclaimer says what the footer's short
 * line does not.
 */
@Composable
internal fun AboutCredits() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RidgeEyebrow(stringResource(R.string.about_ridge_protocol_credits))
        listOf("my-whoop" to "WHOOP 4.0 protocol", "goose" to "WHOOP 5.0 protocol").forEach { (repo, note) ->
            val a11y = stringResource(R.string.l10n_settings_screen_repo_note_0a694b50, repo, note)
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = a11y },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("›", style = RidgeType.bodySm, color = Ridge.brand)
                Text(repo, style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp), color = Ridge.ink)
                Text(stringResource(R.string.l10n_settings_screen_note_a2481d6c, note), style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = Ridge.inkSubtle)
            }
        }
        Text(stringResource(R.string.l10n_settings_screen_open_source_ble_reverse_engineering_work_40062271), style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp), color = Ridge.inkSubtle)
        Text(
            stringResource(R.string.l10n_settings_screen_noop_is_not_a_medical_device_ab32ef7e),
            style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp), color = Ridge.inkSubtle,
            modifier = Modifier.padding(top = Ridge.space2),
        )
    }
}
