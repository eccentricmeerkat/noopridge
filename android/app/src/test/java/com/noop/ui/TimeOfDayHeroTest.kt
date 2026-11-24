package com.noop.ui

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** BS-x2-1 item 3.1: the device-clock hour to hero-photograph mapping. */
class TimeOfDayHeroTest {
    private val london = ZoneId.of("Europe/London")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun slot(h: Int, m: Int, zone: ZoneId = london) =
        heroSlotAt(ZonedDateTime.of(2026, 11, 21, h, m, 0, 0, zone).toInstant(), zone)

    @Test fun boundaries() {
        assertEquals(HeroSlot.Night, slot(5, 59))
        assertEquals(HeroSlot.Morning, slot(6, 0))
        assertEquals(HeroSlot.Morning, slot(10, 59))
        assertEquals(HeroSlot.Afternoon, slot(11, 0))
        assertEquals(HeroSlot.Afternoon, slot(15, 59))
        assertEquals(HeroSlot.Evening, slot(16, 0))
        assertEquals(HeroSlot.Evening, slot(20, 59))
        assertEquals(HeroSlot.Night, slot(21, 0))
        assertEquals(HeroSlot.Night, slot(0, 0))
        assertEquals(HeroSlot.Night, slot(23, 59))
    }

    @Test fun sameInstantFollowsTheDeviceZone() {
        val noonLondon = ZonedDateTime.of(2026, 11, 21, 12, 0, 0, 0, london).toInstant() // 21:00 in Tokyo
        assertEquals(HeroSlot.Afternoon, heroSlotAt(noonLondon, london))
        assertEquals(HeroSlot.Night, heroSlotAt(noonLondon, tokyo))
    }

    @Test fun nextBoundary() {
        val at = { h: Int, m: Int -> ZonedDateTime.of(2026, 11, 21, h, m, 0, 0, london).toInstant() }
        assertEquals(60L * 60_000L, millisToNextHeroBoundary(at(10, 0), london)) // 10:00 -> 11:00
        assertEquals(9L * 3_600_000L - 60_000L, millisToNextHeroBoundary(at(21, 1), london)) // 21:01 -> 06:00 next day
    }

    @Test fun greetingAgreesWithThePhotograph() {
        assertEquals(com.noop.R.string.today_greeting_morning, heroGreetingRes(slot(5, 59).let { heroSlotForHour(6) }))
        // 05:30 shows the night photograph, so it must not say "Good morning".
        assertEquals(com.noop.R.string.today_greeting_evening, heroGreetingRes(slot(5, 30)))
        assertEquals(com.noop.R.string.today_greeting_morning, heroGreetingRes(slot(6, 0)))
        assertEquals(com.noop.R.string.today_greeting_afternoon, heroGreetingRes(slot(13, 0)))
        assertEquals(com.noop.R.string.today_greeting_evening, heroGreetingRes(slot(18, 0)))
        // Night keeps "Good evening", never "Good night".
        assertEquals(com.noop.R.string.today_greeting_evening, heroGreetingRes(slot(23, 0)))
    }
}
