package com.noop.ingest

import androidx.health.connect.client.HealthConnectClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BS-x2-3 item 17: the Health Connect card's state and its empty-import messages. */
class HealthConnectStateTest {
    private val ok = HealthConnectClient.SDK_AVAILABLE

    @Test fun notInstalledAndUpdateAreTold() {
        assertEquals(HcState.NOT_INSTALLED, HealthConnectState.of(HealthConnectClient.SDK_UNAVAILABLE, 3, true, false))
        assertEquals(HcState.UPDATE_REQUIRED, HealthConnectState.of(HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED, 3, true, false))
    }

    @Test fun noGrantMeansPermissionNeeded() {
        assertEquals(HcState.PERMISSION_NEEDED, HealthConnectState.of(ok, 0, false, false))
    }

    @Test fun grantedButNothingImportedIsStillConnected() {
        val s = HealthConnectState.of(ok, 2, false, false)
        assertEquals(HcState.CONNECTED_NO_DATA, s)
        assertTrue("never 'Not connected' after a grant", HealthConnectState.isConnected(s))
    }

    @Test fun importedRowsAreConnectedEvenIfTheGrantReadFailed() {
        assertEquals(HcState.CONNECTED, HealthConnectState.of(ok, 0, true, false))
    }

    @Test fun importingWinsOverEverythingElseWhenAvailable() {
        assertEquals(HcState.IMPORTING, HealthConnectState.of(ok, 0, false, true))
        assertEquals(HcState.NOT_INSTALLED, HealthConnectState.of(HealthConnectClient.SDK_UNAVAILABLE, 0, false, true))
    }

    @Test fun permissionNeededIsNotConnected() = assertFalse(HealthConnectState.isConnected(HcState.PERMISSION_NEEDED))

    @Test fun anEmptyHealthConnectAndARefusedReadReadDifferently() {
        assertEquals("No records found for the categories you chose.", HealthConnectState.emptyImportMessage(0, emptyList()))
        val refused = HealthConnectState.emptyImportMessage(0, listOf("StepsRecord: SecurityException no permission"))
        assertTrue(refused.startsWith("Health Connect refused the read"))
        assertTrue(refused.contains("SecurityException"))
    }

    @Test fun diagnosticsLineCarriesCountsAndFailuresOnly() {
        val line = HealthConnectState.diagnosticsLine(2, 9, linkedMapOf("StepsRecord" to 0, "SleepSessionRecord" to 12), listOf("X: boom"))
        assertEquals("granted=2/9 records=StepsRecord=0,SleepSessionRecord=12 failures=X: boom", line)
    }
}
