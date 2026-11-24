package com.noop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.ui.ridge.RidgeSegmentedPills

private enum class YourDataTab { SOURCES, FUSED, IMPORTED }

/**
 * B2 Hub4 (owner edit, `YourData.dc.html`): one "Your data" page with the board's three tabs. Sources is the import
 * and Health Connect page, Fused the best-sourced record, Imported the latest imported values. Each tab is the app's
 * existing screen, drawn under this page's title and tab row; no screen's logic moves.
 */
@Composable
fun YourDataScreen(vm: AppViewModel, onOpenInsights: () -> Unit, onOpenLimitations: () -> Unit, fused: @Composable () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(YourDataTab.SOURCES) }
    val labels = mapOf(
        YourDataTab.SOURCES to stringResource(R.string.data_ridge_tab_sources),
        YourDataTab.FUSED to stringResource(R.string.data_ridge_tab_fused),
        YourDataTab.IMPORTED to stringResource(R.string.data_ridge_tab_imported),
    )
    val under: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
            Text(stringResource(R.string.data_ridge_line), style = RidgeType.bodySm, color = Ridge.inkMuted)
            RidgeSegmentedPills(
                items = YourDataTab.entries.toList(),
                selected = tab,
                onSelect = { tab = it },
                label = { labels.getValue(it) },
                fill = true,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
    CompositionLocalProvider(
        LocalRidgeTitleOverride provides stringResource(R.string.hub_your_data_title),
        LocalRidgeUnderTitle provides under,
    ) {
        when (tab) {
            YourDataTab.SOURCES -> DataSourcesScreen(vm, onOpenLimitations)
            YourDataTab.FUSED -> fused()
            YourDataTab.IMPORTED -> LazyScreenScaffold(title = stringResource(R.string.hub_your_data_title), subtitle = null, rowSpacing = 14.dp) {
                item { RidgeImportedTab(vm, onOpenInsights) }
            }
        }
    }
}

/** The "Backup" chip at the right of the Your data header (the board's `nr-chip`). */
@Composable
internal fun YourDataBackupChip(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Ridge.glassFill,
        border = BorderStroke(1.dp, Ridge.glassStroke),
        modifier = Modifier.heightIn(min = 34.dp).clickable(onClick = onClick),
    ) {
        Text(
            stringResource(R.string.data_ridge_backup),
            style = RidgeType.bodySm, color = Ridge.brand,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
