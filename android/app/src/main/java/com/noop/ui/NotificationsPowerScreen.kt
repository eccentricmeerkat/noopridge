package com.noop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.ui.ridge.RidgeSegmentedPills

private enum class NpTab { NOTIFICATIONS, POWER }

/**
 * B2 Hub5b (`Notifications.dc.html`): one page with the board's two tabs. The header and its one-line subtitle follow the
 * tab. Notifications and Power saving are the app's existing screens, hosted under the tabs; no setting moves.
 */
@Composable
fun NotificationsPowerScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(NpTab.NOTIFICATIONS) }
    val labels = mapOf(
        NpTab.NOTIFICATIONS to stringResource(R.string.np_ridge_tab_notifications),
        NpTab.POWER to stringResource(R.string.np_ridge_tab_power),
    )
    val title = stringResource(if (tab == NpTab.NOTIFICATIONS) R.string.np_ridge_tab_notifications else R.string.np_ridge_tab_power)
    val sub = stringResource(if (tab == NpTab.NOTIFICATIONS) R.string.np_ridge_sub_notifications else R.string.np_ridge_sub_power)
    val under: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
            Text(sub, style = RidgeType.bodySm, color = Ridge.inkMuted)
            RidgeSegmentedPills(
                items = NpTab.entries.toList(), selected = tab, onSelect = { tab = it },
                label = { labels.getValue(it) }, fill = true, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
    CompositionLocalProvider(LocalRidgeTitleOverride provides title, LocalRidgeUnderTitle provides under) {
        when (tab) {
            NpTab.NOTIFICATIONS -> NotificationsSettingsScreen(vm)
            NpTab.POWER -> PowerSavingScreen(vm)
        }
    }
}
