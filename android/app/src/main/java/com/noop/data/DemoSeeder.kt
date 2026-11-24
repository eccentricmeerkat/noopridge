package com.noop.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Seeds a comprehensive, self-contained demo dataset so the **dev** build is a full
 * walkthrough of every screen — Today, Sleep, Trends, Workouts, Health, Stress,
 * Insights, Explore, Compare, Apple Health — with no strap and no import required.
 *
 * The caller gates this to `BuildConfig.ENABLE_DEMO`, so the full app never seeds and
 * starts clean. It is a no-op if "my-whoop" already holds daily rows, so it runs at most
 * once and never clobbers real data.
 *
 * Everything here is **synthetic and deterministic** (fixed RNG seed). Nothing is real
 * biometric data. Values are physiologically plausible and internally correlated
 * (recovery ↔ HRV ↔ resting-HR ↔ sleep; strain ↔ workouts; a slow fitness drift over
 * the window) so the charts, trends and insights all read like a real account.
 */
object DemoSeeder {

    private const val WHOOP = "my-whoop"
    private const val APPLE = "apple-health"
    // The NOOP-COMPUTED strap source ("<strap>-noop") the IntelligenceEngine persists its derived weekly
    // scores under (fitness_age / vo2max_est / vitality / body_age). The Health screen + Today "Your cards"
    // + Trends resolve these through the computed UNION (WhoopRepository.metricSeriesComputedUnion), which in
    // the demo (activeStrapId "my-whoop") reads "my-whoop-noop" — so the demo MUST seed them here, not under
    // the imported "my-whoop" source, or those surfaces read empty ("No Data") while the rest of the demo is
    // full. Mirrors the real engine's write target.
    private const val WHOOP_NOOP = "$WHOOP-noop"
    private const val DAYS = 120
    /** Days of demo intraday heart rate (R3c-3), ending now. */
    private const val HR_DAYS = 3

    /** Effort rescale factor: the old 0–21 strain scale → the new 0–100 Effort scale (100/21). */
    private const val STRAIN_SCALE = 100.0 / 21.0

    private val SPORTS = listOf(
        "Running", "Cycling", "Strength", "HIIT", "Swimming", "Yoga", "Walking", "Rowing"
    )

    /** Seed only if the demo (and the user) has no daily history yet. Safe to call on every launch. */
    suspend fun seedIfEmpty(repo: WhoopRepository) {
        if (repo.days(WHOOP).isNotEmpty()) return
        seed(repo)
    }

    /**
     * Demo-only: seed a SECOND paired device (a Polar H10) into the registry so the Devices screen shows
     * the WHOOP (Active) alongside a paired generic strap out of the box — no real hardware needed. The
     * WHOOP `pairedDevice` row itself is created by the v7→v8 migration; this only adds the demo strap, and
     * only if the registry currently holds exactly the WHOOP (so it runs at most once and never clobbers a
     * real pairing). Gated by the caller to `BuildConfig.ENABLE_DEMO`. Status `paired` (not active), so the
     * SourceCoordinator stays dormant on the WHOOP and the existing live flow is untouched.
     */
    suspend fun seedDemoDeviceIfNeeded(registry: DeviceRegistry) {
        val devices = registry.all()
        // Only seed when the registry is the freshly-migrated single-WHOOP state.
        if (devices.size != 1) return
        if (!SourceCoordinatorIsWhoop(devices.first())) return
        val now = System.currentTimeMillis() / 1000
        registry.add(
            PairedDeviceRow(
                id = "demo-polar-h10",
                brand = "Polar",
                model = "H10",
                nickname = null,
                sourceKind = SourceKind.liveBLE.name,
                capabilities = "hr,hrv",
                status = DeviceStatus.paired.name,
                addedAt = now,
                // A plausible "Last seen 3h ago" so the card's last-seen line reads naturally in the demo.
                lastSeenAt = now - 3 * 3600,
            ),
        )
    }

    /** Local WHOOP check, kept here so DemoSeeder (data layer) needn't import the BLE-layer coordinator. */
    private fun SourceCoordinatorIsWhoop(d: PairedDeviceRow): Boolean =
        d.id == "my-whoop" || d.brand.equals("WHOOP", ignoreCase = true)

    private suspend fun seed(repo: WhoopRepository) {
        val rng = Random(0xC0FFEE)
        val zone = ZoneId.systemDefault()
        val startDay = LocalDate.now().minusDays((DAYS - 1).toLong())

        repo.upsertDevice(WHOOP, name = "WHOOP (demo)")

        val daily = ArrayList<DailyMetric>(DAYS)
        val sleeps = ArrayList<SleepSession>(DAYS)
        val series = ArrayList<MetricSeriesRow>(DAYS * 2)
        val apple = ArrayList<AppleDaily>(DAYS)
        val workouts = ArrayList<WorkoutRow>()
        val journal = ArrayList<JournalEntry>()

        var weight = 79.5
        var fitness = 0.0 // slow upward drift: HRV rises, resting-HR falls, VO2max climbs

        for (i in 0 until DAYS) {
            val date = startDay.plusDays(i.toLong())
            val day = date.toString() // ISO yyyy-MM-dd
            val weekend = date.dayOfWeek.value >= 6
            fitness += 0.012

            // --- training load for the day ---
            val trains = if (weekend) rng.nextDouble() < 0.40 else rng.nextDouble() < 0.62
            val nWorkouts = if (!trains) 0 else if (rng.nextDouble() < 0.22) 2 else 1

            // --- sleep architecture ---
            val totalSleep = gauss(rng, 430.0, 35.0).coerceIn(300.0, 540.0)
            val efficiency = gauss(rng, 89.0, 4.0).coerceIn(72.0, 98.0)
            val deep = (totalSleep * gauss(rng, 0.20, 0.03)).coerceIn(35.0, 130.0)
            val rem = (totalSleep * gauss(rng, 0.23, 0.03)).coerceIn(45.0, 150.0)
            val light = (totalSleep - deep - rem).coerceAtLeast(60.0)
            val disturbances = gauss(rng, 6.0, 3.0).coerceIn(0.0, 18.0).toInt()

            // --- autonomic markers ---
            val hrv = (gauss(rng, 78.0 + fitness * 1.5, 12.0) + (if (weekend) 6 else 0) - nWorkouts * 4)
                .coerceIn(28.0, 150.0)
            val rhr = (gauss(rng, 56.0 - fitness * 0.4, 3.0) + nWorkouts * 1.2)
                .coerceIn(42.0, 70.0).toInt()
            val spo2 = gauss(rng, 96.5, 0.8).coerceIn(93.0, 100.0)
            val skinTempDev = gauss(rng, 0.0, 0.25).coerceIn(-1.2, 1.4)
            val resp = gauss(rng, 14.6, 0.9).coerceIn(11.0, 19.0)

            // --- recovery: a function of HRV, sleep quality and resting-HR ---
            val recovery = (
                40 + (hrv - 70) * 0.55 + (efficiency - 85) * 0.6 + (totalSleep - 420) * 0.03 -
                    (rhr - 55) * 1.4 - disturbances * 0.8 + gauss(rng, 0.0, 5.0)
                ).coerceIn(8.0, 99.0)

            // --- strain (Effort): workout-driven, rescaled 0–21 → 0–100 (×100/21) so demo
            // Effort sits on the new scale ---
            val strain = (
                (if (nWorkouts == 0) gauss(rng, 7.5, 1.8)
                else gauss(rng, 13.5, 2.4) + (nWorkouts - 1) * 2.5) * STRAIN_SCALE
                ).coerceIn(3.0 * STRAIN_SCALE, 100.0)

            daily.add(
                DailyMetric(
                    deviceId = WHOOP, day = day,
                    totalSleepMin = round1(totalSleep), efficiency = round1(efficiency),
                    deepMin = round1(deep), remMin = round1(rem), lightMin = round1(light),
                    disturbances = disturbances, restingHr = rhr, avgHrv = round1(hrv),
                    recovery = round1(recovery), strain = round1(strain), exerciseCount = nWorkouts,
                    spo2Pct = round1(spo2), skinTempDevC = round2(skinTempDev), respRateBpm = round1(resp),
                )
            )

            // --- sleep session: previous night ~23:10 → wake ---
            val onset = date.minusDays(1).atTime(23, 10).atZone(zone).toEpochSecond() + rng.nextInt(-1800, 1800)
            val inBedSec = ((totalSleep + totalSleep * (100 - efficiency) / 100) * 60).toLong()
            sleeps.add(
                SleepSession(
                    deviceId = WHOOP, startTs = onset, endTs = onset + inBedSec,
                    efficiency = round1(efficiency), restingHr = rhr, avgHrv = round1(hrv),
                    stagesJSON = timedStagesJson(onset, deep, rem, light, inBedSec / 60.0),
                )
            )

            // --- long-format extras (body composition) under my-whoop ---
            weight += gauss(rng, -0.02, 0.18)
            series.add(MetricSeriesRow(WHOOP, day, "weightKg", round2(weight)))
            series.add(
                MetricSeriesRow(
                    WHOOP, day, "bodyFatPct",
                    round1((18.0 - fitness * 0.2 + gauss(rng, 0.0, 0.4)).coerceIn(10.0, 24.0))
                )
            )
            // Export-verbatim sleep figures (same metricSeries keys the importers write), so
            // the demo Sleep tiles exercise the prefer-imported path.
            val demoNeedMin = (totalSleep + gauss(rng, 25.0, 20.0)).coerceIn(420.0, 560.0)
            series.add(MetricSeriesRow(WHOOP, day, "sleep_performance",
                round1((totalSleep / demoNeedMin * 100.0).coerceAtMost(100.0))))
            series.add(MetricSeriesRow(WHOOP, day, "sleep_consistency",
                round1(gauss(rng, 80.0, 8.0).coerceIn(40.0, 100.0))))
            series.add(MetricSeriesRow(WHOOP, day, "sleep_need_min", round1(demoNeedMin)))
            series.add(MetricSeriesRow(WHOOP, day, "sleep_debt_min",
                round1((demoNeedMin - totalSleep).coerceAtLeast(0.0))))

            // --- Apple Health daily aggregate ---
            val steps = gauss(rng, 8500.0, 2600.0).coerceIn(1200.0, 19000.0).toInt()
            apple.add(
                AppleDaily(
                    deviceId = APPLE, day = day,
                    steps = steps,
                    activeKcal = round1((steps * 0.045 + nWorkouts * 220).coerceIn(120.0, 1400.0)),
                    basalKcal = round1(gauss(rng, 1650.0, 40.0)),
                    vo2max = round1((46 + fitness * 0.3 + gauss(rng, 0.0, 0.5)).coerceIn(38.0, 56.0)),
                    avgHr = gauss(rng, 72.0, 5.0).toInt(),
                    maxHr = gauss(rng, 150.0, 12.0).toInt(),
                    walkingHr = gauss(rng, 108.0, 6.0).toInt(),
                    weightKg = round2(weight),
                )
            )

            // --- workouts on training days ---
            repeat(nWorkouts) { k ->
                val sport = SPORTS[rng.nextInt(SPORTS.size)]
                val durSec = (gauss(rng, 48.0, 16.0).coerceIn(18.0, 110.0) * 60)
                val start = date.atTime(if (weekend) 9 else 18, rng.nextInt(0, 50))
                    .atZone(zone).toEpochSecond() + k * 3600
                val avg = gauss(rng, 138.0, 12.0).toInt()
                val src = if (rng.nextDouble() < 0.7) WHOOP else APPLE
                val distanceSports = setOf("Running", "Cycling", "Walking", "Swimming", "Rowing")
                workouts.add(
                    WorkoutRow(
                        deviceId = src, startTs = start, endTs = start + durSec.toLong(),
                        sport = sport, source = src,
                        durationS = round1(durSec),
                        energyKcal = round1((durSec / 60) * gauss(rng, 9.0, 2.0)),
                        avgHr = avg, maxHr = (avg + gauss(rng, 22.0, 6.0)).toInt(),
                        // strain is already 0–100 (daily Effort), so the per-workout share keeps
                        // the 0–100 scale; bounds rescaled from the old 4–21 (×100/21).
                        strain = round1((strain * gauss(rng, 0.6, 0.1)).coerceIn(4.0 * STRAIN_SCALE, 100.0)),
                        distanceM = if (sport in distanceSports)
                            round1(gauss(rng, 6500.0, 2500.0).coerceAtLeast(500.0)) else null,
                        // Only WHOOP-sourced rows carry zones (matching real imports — Apple Health
                        // rows never do), so the demo Workouts screen showcases the HR Zones card.
                        zonesJSON = if (src == WHOOP) run {
                            val z = listOf(
                                gauss(rng, 15.0, 5.0), gauss(rng, 30.0, 8.0), gauss(rng, 28.0, 8.0),
                                gauss(rng, 15.0, 6.0), gauss(rng, 6.0, 3.0),
                            ).map { it.coerceIn(0.0, 100.0) }
                            """{"zone1":${round1(z[0])},"zone2":${round1(z[1])},"zone3":${round1(z[2])},"zone4":${round1(z[3])},"zone5":${round1(z[4])}}"""
                        } else null,
                        notes = null,
                    )
                )
            }

            // --- journal answers for the recent 40 days ---
            if (i >= DAYS - 40) {
                journal.add(JournalEntry(WHOOP, day, "Any alcohol?", rng.nextDouble() < 0.18))
                journal.add(JournalEntry(WHOOP, day, "Caffeine after 4pm?", rng.nextDouble() < 0.30))
                journal.add(JournalEntry(WHOOP, day, "Felt stressed?", rng.nextDouble() < 0.28))
            }
        }

        // --- weekly Fitness Age + VO2max estimate (the engine stamps these on each week's
        // Saturday; mirror that here so the Fitness Age screen renders in the demo build).
        // Trends from ~42 → ~36 (younger) as the demo "fitness" drift climbs; vo2max ~44 → ~50.
        var fitnessAge = 42.0
        var vo2 = 44.0
        var vitality = 55.0      // weekly Vitality (0–100) trending up as the demo habits improve
        var bodyAgeDemo = 40.0   // Body Age (years) trending down (younger)
        for (i in 0 until DAYS) {
            val date = startDay.plusDays(i.toLong())
            if (date.dayOfWeek.value != 6) continue // 6 = Saturday
            val day = date.toString()
            // Seed under the NOOP-COMPUTED source (WHOOP_NOOP), exactly where the IntelligenceEngine writes
            // these derived weekly scores in the real app — so the Health screen, the Today "Your cards"
            // Fitness age / Vitality cards and Trends (all via the computed union) resolve them in the demo instead
            // of showing "No Data". Trends ~42 → ~34 (younger) for Fitness age; vitality climbs ~55 → ~80.
            series.add(MetricSeriesRow(WHOOP_NOOP, day, "fitness_age",
                round1((fitnessAge + gauss(rng, 0.0, 0.3)).coerceIn(34.0, 44.0))))
            series.add(MetricSeriesRow(WHOOP_NOOP, day, "vo2max_est",
                round1((vo2 + gauss(rng, 0.0, 0.4)).coerceIn(42.0, 52.0))))
            series.add(MetricSeriesRow(WHOOP_NOOP, day, "vitality",
                round1((vitality + gauss(rng, 0.0, 1.0)).coerceIn(40.0, 80.0))))
            series.add(MetricSeriesRow(WHOOP_NOOP, day, "body_age",
                round1((bodyAgeDemo + gauss(rng, 0.0, 0.3)).coerceIn(30.0, 45.0))))
            fitnessAge -= 0.75 // ~6 yr younger across the 8 seeded Saturdays
            vo2 += 0.75
            vitality += 2.0
            bodyAgeDemo -= 0.6
        }

        // --- daily "stress" series (0–3) under my-whoop, EXACTLY as a real WHOOP import derives it
        // (WhoopImporter): z = 0.6·((rhr−rmean)/rsd) − 0.6·((hrv−hmean)/hsd), stress = clamp(1.5 + z, 0, 3).
        // Without this the demo had no "stress" series at all, so the Today "Your cards" Stress card,
        // the Stress screen's stored-series path and Trends all read empty. Seeding it makes the demo
        // match an imported export and fixes Stress everywhere in one place.
        run {
            val rhrAll = daily.mapNotNull { it.restingHr?.toDouble() }
            val hrvAll = daily.mapNotNull { it.avgHrv }
            val (rMean, rSd) = meanStd(rhrAll)
            val (hMean, hSd) = meanStd(hrvAll)
            for (d in daily) {
                val rhr = d.restingHr?.toDouble() ?: continue
                val hrv = d.avgHrv ?: continue
                val z = 0.6 * ((rhr - rMean) / rSd) - 0.6 * ((hrv - hMean) / hSd)
                series.add(MetricSeriesRow(WHOOP, d.day, "stress", round2((1.5 + z).coerceIn(0.0, 3.0))))
            }
        }

        repo.upsertDailyMetrics(daily)
        repo.upsertSleepSessions(sleeps)
        repo.upsertMetricSeries(series)
        repo.upsertAppleDaily(apple)
        if (workouts.isNotEmpty()) repo.upsertWorkouts(workouts)
        if (journal.isNotEmpty()) repo.upsertJournal(journal)
        // R3c-3: intraday heart rate for the last few days, so the Today HR chart (C-05) has a curve to draw.
        // Built AFTER the loop from the sleeps/workouts it produced, with its OWN seeded Random: the main `rng`
        // sequence above is untouched, so every daily value the demo already showed stays byte-identical.
        repo.insertHr(demoHeartRate(sleeps, workouts, zone, startDay))
        // B2-3 (D17): dense heart rate around about a dozen older hard sessions, so Baseline's heart-rate recovery card
        // has sessions to draw. Another own Random, so nothing above changes.
        repo.insertHr(demoRecoveryHeartRate(workouts, System.currentTimeMillis() / 1000))
    }

    /** Hard sessions the demo gives a measurable recovery to (B2-3, D17). */
    private const val HRR_SESSIONS = 12

    /**
     * Demo-only heart rate around up to [HRR_SESSIONS] older strap workouts, spread evenly over the history: 5-second
     * samples for the five minutes before each ends (held near 160-170 bpm, above 70% of any plausible max HR, so
     * [com.noop.analytics.HeartRateRecovery]'s intensity gate passes) and five minutes after it, falling by a 1-minute
     * drop that grows from about 24 to 34 bpm across the sessions, so the trend reads as improving. Workouts inside the
     * last [HR_DAYS] days are skipped: [demoHeartRate] already covers those minutes.
     */
    internal fun demoRecoveryHeartRate(workouts: List<WorkoutRow>, nowSec: Long): List<HrSample> {
        val rng = Random(0x4EC0)
        val cutoff = nowSec - (HR_DAYS + 1) * 86_400L
        val eligible = workouts.filter { it.source == WHOOP && it.endTs < cutoff }.sortedBy { it.endTs }
        if (eligible.isEmpty()) return emptyList()
        val n = minOf(HRR_SESSIONS, eligible.size)
        val picked = (0 until n).map { k -> eligible[if (n == 1) 0 else k * (eligible.size - 1) / (n - 1)] }
        val out = ArrayList<HrSample>()
        picked.forEachIndexed { k, w ->
            val drop1 = 24.0 + 10.0 * (if (n == 1) 0.0 else k / (n - 1.0)) + gauss(rng, 0.0, 1.5)
            val endHr = 160.0 + rng.nextInt(0, 11)
            var t = -300L
            while (t <= 330L) {
                val bpm = if (t <= 0L) endHr + gauss(rng, 0.0, 1.5)
                else endHr - drop1 * (1.0 - kotlin.math.exp(-t / 60.0)) / (1.0 - kotlin.math.exp(-1.0)) + gauss(rng, 0.0, 0.8)
                out.add(HrSample(deviceId = WHOOP, ts = w.endTs + t, bpm = bpm.toInt().coerceIn(40, 200)))
                t += 5L
            }
        }
        return out
    }

    /** Demo-only minute heart rate for the last [HR_DAYS] days up to now: ~52 bpm asleep (inside each seeded
     *  sleep session), a slow 66-84 bpm daytime drift awake, and a moderate ramp during each seeded workout,
     *  capped near 120 so today's live Effort never overtakes the stored value the orbs show. */
    private fun demoHeartRate(
        sleeps: List<SleepSession>,
        workouts: List<WorkoutRow>,
        zone: ZoneId,
        startDay: LocalDate,
    ): List<HrSample> {
        val hr = Random(0xBEA7)
        val now = System.currentTimeMillis() / 1000
        val from = startDay.plusDays((DAYS - HR_DAYS).toLong()).atStartOfDay(zone).toEpochSecond()
        val out = ArrayList<HrSample>()
        var ts = from
        var drift = 74.0
        while (ts <= now) {
            drift = (drift + gauss(hr, 0.0, 0.6)).coerceIn(66.0, 84.0)
            val asleep = sleeps.any { ts >= it.startTs && ts < it.endTs }
            val w = workouts.firstOrNull { ts >= it.startTs && ts < it.endTs }
            val bpm = when {
                asleep -> gauss(hr, 52.0, 2.5)
                w != null -> {
                    val f = ((ts - w.startTs).toDouble() / (w.endTs - w.startTs).coerceAtLeast(1)).coerceIn(0.0, 1.0)
                    val ramp = kotlin.math.sin(f * PI).coerceAtLeast(0.35)
                    (drift + (118.0 - drift) * ramp + gauss(hr, 0.0, 3.0)).coerceAtMost(124.0)
                }
                else -> drift + gauss(hr, 0.0, 3.0)
            }
            out.add(HrSample(deviceId = WHOOP, ts = ts, bpm = bpm.toInt().coerceIn(40, 200)))
            ts += 60
        }
        return out
    }

    // MARK: - helpers

    /** Box–Muller normal sample. */
    private fun gauss(rng: Random, mean: Double, sd: Double): Double {
        val u1 = rng.nextDouble().coerceIn(1e-9, 1.0)
        val u2 = rng.nextDouble()
        return mean + sd * (sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2))
    }

    private fun round1(x: Double) = round(x * 10.0) / 10.0
    private fun round2(x: Double) = round(x * 100.0) / 100.0

    /** Mean + (population) standard deviation of a sample; SD floored so a z-score never divides by zero.
     *  Mirrors WhoopImporter.meanStd, used to derive the demo "stress" series exactly like a real import. */
    private fun meanStd(a: List<Double>): Pair<Double, Double> {
        if (a.isEmpty()) return 0.0 to 1.0
        val m = a.sum() / a.size
        val v = a.sumOf { (it - m) * (it - m) } / a.size
        return m to maxOf(sqrt(v), 0.0001)
    }

    /** A plausible light→deep→rem cycle as a stage-segments array (minutes). Tolerant by design. */
    private fun stagesJson(deep: Double, rem: Double, light: Double, awakeMin: Int): String {
        val arr = JSONArray()
        fun seg(stage: String, min: Double) {
            arr.put(JSONObject().put("stage", stage).put("min", round1(min)))
        }
        seg("light", light * 0.35); seg("deep", deep * 0.6); seg("light", light * 0.30)
        seg("rem", rem * 0.6); seg("deep", deep * 0.4); seg("light", light * 0.35)
        seg("rem", rem * 0.4); seg("awake", awakeMin.toDouble())
        return arr.toString()
    }

    /**
     * The same night with wall-clock times (B2-4a-5), in the shape the on-device stager persists
     * (`[{"start","end","stage"}]`, unix seconds, stage wake|light|deep|rem), so the Sleep stages lanes have something
     * to draw. [inBedMin] less the asleep minutes is the night's awake time, split into two wakes between the cycles and
     * the awake stretch that closes the night.
     * Deterministic and random-free: the demo's other values do not move.
     */
    internal fun timedStagesJson(onsetTs: Long, deep: Double, rem: Double, light: Double, inBedMin: Double): String {
        val awake = (inBedMin - deep - rem - light).coerceAtLeast(0.0)
        val order = listOf(
            "light" to light * 0.35, "deep" to deep * 0.6, "light" to light * 0.30, "wake" to awake * 0.35,
            "rem" to rem * 0.6, "deep" to deep * 0.4, "light" to light * 0.20, "wake" to awake * 0.35,
            "rem" to rem * 0.4, "light" to light * 0.15, "wake" to awake * 0.30,
        )
        val arr = JSONArray()
        var t = onsetTs
        order.forEach { (stage, min) ->
            val end = t + (min * 60).toLong()
            if (end > t) arr.put(JSONObject().put("start", t).put("end", end).put("stage", stage))
            t = end
        }
        return arr.toString()
    }
}
