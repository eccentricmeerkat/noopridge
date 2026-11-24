package com.noop.ingest

import androidx.health.connect.client.HealthConnectClient

/** What the Health Connect card should say, from what Android reports and what NOOP has imported (BS-x2-3 item 17). */
internal enum class HcState {
    /** Health Connect is not installed (or cannot run on this device). */
    NOT_INSTALLED,

    /** Health Connect needs an update from Google Play before it can be used. */
    UPDATE_REQUIRED,

    /** Installed, but none of the selected read permissions is granted. */
    PERMISSION_NEEDED,

    /** An import is running now. */
    IMPORTING,

    /** Permission granted, but nothing has been imported yet (or the last import found nothing). */
    CONNECTED_NO_DATA,

    /** Permission granted and rows imported. */
    CONNECTED,
}

internal object HealthConnectState {

    /**
     * The card's state. Granted permission alone means "Connected": the card must never say "Not connected" after a
     * permission was granted, even when the last import found no records.
     */
    fun of(sdkStatus: Int, grantedSelected: Int, hasImportedRows: Boolean, importing: Boolean): HcState = when {
        sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcState.UPDATE_REQUIRED
        sdkStatus != HealthConnectClient.SDK_AVAILABLE -> HcState.NOT_INSTALLED
        importing -> HcState.IMPORTING
        grantedSelected <= 0 && !hasImportedRows -> HcState.PERMISSION_NEEDED
        hasImportedRows -> HcState.CONNECTED
        else -> HcState.CONNECTED_NO_DATA
    }

    /** Whether the card shows "Connected". */
    fun isConnected(state: HcState): Boolean = state == HcState.CONNECTED || state == HcState.CONNECTED_NO_DATA ||
        state == HcState.IMPORTING

    /**
     * The message for an import that wrote nothing. A read that THREW is not "no data": say so, with the first reason,
     * so a tester can tell a refused read from an empty Health Connect.
     */
    fun emptyImportMessage(recordsRead: Int, failures: List<String>): String = when {
        recordsRead == 0 && failures.isNotEmpty() ->
            "Health Connect refused the read (${failures.first()}). Open Health Connect and check NOOP's permissions."
        recordsRead == 0 -> "No records found for the categories you chose."
        else -> "Records were found but none could be used for the days NOOP covers."
    }

    /** One log line: permission count, records per type, failures. No health values. */
    fun diagnosticsLine(granted: Int, requested: Int, recordsByType: Map<String, Int>, failures: List<String>): String =
        "granted=$granted/$requested records=" + recordsByType.entries.joinToString(",") { "${it.key}=${it.value}" } +
            (if (failures.isEmpty()) "" else " failures=" + failures.joinToString(" | "))
}
