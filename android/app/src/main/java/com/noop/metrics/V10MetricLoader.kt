package com.noop.metrics

import com.noop.NoopApplication
import com.noop.analytics.Baselines
import com.noop.analytics.IntelligenceEngine
import com.noop.ui.NoopPrefs
import com.noop.ui.ProfileStore
import com.noop.ui.loadCaloriesByDay
import com.noop.ui.loadSleepTabSessions
import com.noop.ui.logicalDayNow
import com.noop.ui.napSleepMinutesByDay
import com.noop.ui.bedWakeByWakeDay
import com.noop.ui.stepsForDay
import kotlinx.coroutines.flow.first

/**
 * Gathers [V10MetricInputs] the way the v10 screens gather them (R1, J-01): each input comes from the read the
 * screen that shows it makes, through the same function where one exists, so the registry and the screen cannot
 * read different rows.
 *
 * | Input | Read the same way as |
 * |---|---|
 * | days | AppViewModel.recentDays (`recentDaysMergedFlow`, the app's device id) |
 * | restByDay | Today's Rest tile (`resolvedSeries("sleep_performance")`, imported wins) |
 * | storedStress | Today's stress (`metricSeries("stress")`, clamped 0–3) |
 * | hrvBaselineEpoch | Today (`NoopPrefs`, `Baselines.hrvBaselineEpochKey`) |
 * | age, maxHrOverride | `ProfileStore` (Settings), override only when set |
 * | restNeedHours | `IntelligenceEngine.routineRestNeedHours` (the routine scoring run's read) |
 * | importedNeedMin / importedDebtMin | the Sleep tab's export-verbatim series |
 * | napSleepMinByDay, bedWakeByDay | the Sleep tab (`loadSleepTabSessions` + `napSleepMinutesByDay`, `mainSleepSpan`) |
 * | caloriesByDay | Today (`loadCaloriesByDay`) |
 * | computedSeries | Health (`metricSeriesComputedUnion` for the weekly keys) |
 * | importedStepsByDay / stepsEstimateByDay | Today's Steps tile (`stepsForDay`; `resolvedSeries("steps_est")`) |
 *
 * Each read fails soft to empty, as on the screens. [today] is the logical day Today shows (04:00 roll).
 */
internal suspend fun loadV10MetricInputs(app: NoopApplication): V10MetricInputs {
    val repo = app.repository
    val strap = app.sourceCoordinator.activeDeviceId.value ?: app.activeDeviceId
    val prefs = NoopPrefs.of(app)
    val profile = ProfileStore.from(app)
    suspend fun stored(key: String) = runCatching {
        repo.metricSeries("my-whoop", key, "0000-00-00", "9999-99-99")
    }.getOrDefault(emptyList()).associate { it.day to it.value }

    val apple = runCatching { repo.appleDaily("apple-health", "0000-01-01", "9999-12-31") }.getOrDefault(emptyList())
    val healthConnect = runCatching { repo.appleDaily("health-connect", "0000-01-01", "9999-12-31") }.getOrDefault(emptyList())
    val importedSteps = (apple + healthConnect).map { it.day }.toSet()
        .mapNotNull { day -> stepsForDay(apple, healthConnect, day)?.let { day to it } }.toMap()

    val sleeps = runCatching { loadSleepTabSessions(repo, strap) }.getOrDefault(emptyList())
    val midsleep = runCatching { repo.habitualMidsleepSec(strap) }.getOrNull()

    return V10MetricInputs(
        days = runCatching { repo.recentDaysMergedFlow(app.activeDeviceId).first() }.getOrDefault(emptyList()),
        restByDay = runCatching {
            repo.resolvedSeries("sleep_performance", "my-whoop", "0000-00-00", "9999-99-99", strapDeviceId = strap)
                .values.associate { it.first to it.second }
        }.getOrDefault(emptyMap()),
        storedStress = runCatching {
            repo.metricSeries("my-whoop", "stress", "0000-01-01", "9999-12-31").associate { it.day to it.value.coerceIn(0.0, 3.0) }
        }.getOrDefault(emptyMap()),
        today = logicalDayNow(),
        hrvBaselineEpoch = prefs.getLong(Baselines.hrvBaselineEpochKey, 0L).toDouble(),
        // R9a V2-7 (#27): null when no age was ever entered and the change is on; the engines keep profile.age.
        age = profile.knownAge,
        maxHrOverride = profile.hrMaxOverride.takeIf { it > 0 }?.toDouble(),
        restNeedHours = runCatching { IntelligenceEngine.routineRestNeedHours(repo, profile.age.toDouble()) }.getOrNull(),
        importedNeedMin = stored("sleep_need_min"),
        importedDebtMin = stored("sleep_debt_min"),
        napSleepMinByDay = runCatching { napSleepMinutesByDay(sleeps, midsleep) }.getOrDefault(emptyMap()),
        // RS-3c: the Sleep score's bed and wake times, one bridged main night per local wake day.
        bedWakeByDay = runCatching { bedWakeByWakeDay(sleeps, midsleep, java.time.ZoneId.systemDefault()) }.getOrDefault(emptyMap()),
        caloriesByDay = runCatching { loadCaloriesByDay(repo, strap) }.getOrDefault(emptyMap()),
        importedStepsByDay = importedSteps,
        stepsEstimateByDay = runCatching {
            repo.resolvedSeries("steps_est", "my-whoop", "0000-00-00", "9999-99-99", strapDeviceId = strap)
                .values.associate { it.first to it.second }
        }.getOrDefault(emptyMap()),
        // J-06 (R9b): why Blood Oxygen can be blank, read as Today reads them.
        spo2DeviceUnsupported = prefs.getString("noop.selectedWhoopModel", null) == com.noop.ble.WhoopModel.WHOOP4.name,
        spo2EstimateOn = NoopPrefs.spo2CandidateDisplay(app) || prefs.getString("noop.selectedWhoopModel", null) == null,
        computedSeries = V10MetricRegistry.WEEKLY_KEYS.values.associateWith { key ->
            runCatching {
                repo.metricSeriesComputedUnion(strap, key, "0000-01-01", "9999-12-31").associate { it.day to it.value }
            }.getOrDefault(emptyMap())
        },
    )
}
