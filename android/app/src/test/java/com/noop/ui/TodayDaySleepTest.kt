package com.noop.ui

import com.noop.data.SleepSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * R9b (owner, on device): a night NOOP detected itself is stored under the computed "-noop" id. Today's Activities and
 * HR band read only the strap/import union, so the Sleep tab showed last night while Today listed no sleep.
 */
class TodayDaySleepTest {

    private val zone = ZoneId.of("Asia/Dubai")
    private fun ts(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toEpochSecond()
    private fun session(id: String, s: Long, e: Long) = SleepSession(deviceId = id, startTs = s, endTs = e)

    private fun withZone(block: () -> Unit) {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        try { block() } finally { TimeZone.setDefault(old) }
    }

    @Test
    fun anOnDeviceNightIsTodaysSleep() = withZone {
        val dayStart = ts(14, 0, 0)
        val now = ts(14, 15, 33)
        val onDevice = session("my-whoop-noop", ts(14, 2, 7), ts(14, 6, 17))
        val olderImport = session("my-whoop", ts(12, 22, 0), ts(13, 7, 0))
        val blocks = daySleepBlocks(listOf(olderImport), listOf(onDevice), dayStart, now)
        assertEquals(listOf(onDevice), blocks)
    }

    @Test
    fun anImportedNightStillWinsItsDay() = withZone {
        val dayStart = ts(14, 0, 0)
        val now = ts(14, 15, 0)
        val imported = session("my-whoop", ts(13, 23, 0), ts(14, 7, 0))
        val onDevice = session("my-whoop-noop", ts(13, 23, 5), ts(14, 6, 55))
        assertEquals(listOf(imported), daySleepBlocks(listOf(imported), listOf(onDevice), dayStart, now))
    }
}
