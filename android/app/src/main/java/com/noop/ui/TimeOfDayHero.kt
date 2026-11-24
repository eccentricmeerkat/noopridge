package com.noop.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.noop.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/**
 * Which of the four Today hero photographs to show (BS-x2-1 item 3.1), by the DEVICE clock:
 * morning 06:00–10:59, afternoon 11:00–15:59, evening 16:00–20:59, night 21:00–05:59.
 * The background follows the clock, not the day on screen, so a past day keeps the same photograph.
 */
enum class HeroSlot(@DrawableRes val drawable: Int, val topScrimAlpha: Float) {
    // [topScrimAlpha]: how dark the soft scrim over the photograph's top third is, so the greeting reads over
    // the brightest sky (morning) without dimming the already dark slots.
    Morning(R.drawable.ridge_hero_morning, 0.50f),
    Afternoon(R.drawable.ridge_hero_afternoon, 0.42f),
    Evening(R.drawable.ridge_hero_evening, 0.22f),
    Night(R.drawable.ridge_hero_night, 0.06f),
}

/**
 * The Today greeting string for a slot, so the words and the photograph always agree. The night photograph
 * keeps "Good evening": "Good night" reads as a goodbye or an instruction to go to sleep.
 */
@androidx.annotation.StringRes
fun heroGreetingRes(slot: HeroSlot): Int = when (slot) {
    HeroSlot.Morning -> R.string.today_greeting_morning
    HeroSlot.Afternoon -> R.string.today_greeting_afternoon
    HeroSlot.Evening, HeroSlot.Night -> R.string.today_greeting_evening
}

/** The slot for a local hour of day (0–23). */
fun heroSlotForHour(hour: Int): HeroSlot = when (hour) {
    in 6..10 -> HeroSlot.Morning
    in 11..15 -> HeroSlot.Afternoon
    in 16..20 -> HeroSlot.Evening
    else -> HeroSlot.Night
}

/** The slot at [instant] in [zone]. */
fun heroSlotAt(instant: Instant, zone: ZoneId): HeroSlot =
    heroSlotForHour(ZonedDateTime.ofInstant(instant, zone).hour)

/** Milliseconds until the next hour boundary at which the slot can change (06, 11, 16, 21), never under a second. */
fun millisToNextHeroBoundary(instant: Instant, zone: ZoneId): Long {
    val now = ZonedDateTime.ofInstant(instant, zone)
    val boundaries = listOf(6, 11, 16, 21)
    val next = boundaries.map { now.toLocalDate().atTime(it, 0).atZone(zone) }.firstOrNull { it.isAfter(now) }
        ?: now.toLocalDate().plusDays(1).atTime(6, 0).atZone(zone)
    return (next.toInstant().toEpochMilli() - instant.toEpochMilli()).coerceAtLeast(1_000L)
}

/** The slot for the device clock now; changes when the hour boundary passes while the screen is open. */
@Composable
fun rememberHeroSlot(): State<HeroSlot> {
    val slot = remember { mutableStateOf(heroSlotAt(Instant.now(), ZoneId.systemDefault())) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            slot.value = heroSlotAt(now, zone)
            // Re-check at the boundary, and at least every 5 minutes so a changed clock or zone is picked up.
            delay(millisToNextHeroBoundary(now, zone).coerceAtMost(5L * 60_000L) + 500L)
        }
    }
    return slot
}
