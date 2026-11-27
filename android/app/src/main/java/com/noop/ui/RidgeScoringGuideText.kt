package com.noop.ui

// RS-3g: the words of "How your numbers work", one entry per score, in the page's three parts: what it measures, how it's
// captured and what it means. Each statement of method is checked against the code that does it, so change them together.
// The layout follows ScoringGuide.dc.html; where the board's words and the app's calculation differed, the app wins.
// English only (owner, 27 Nov 2026).

/** Everything the page shows for one score. [bands] is empty where the app has no threshold of its own to quote. */
internal class GuideContent(
    val score: GuideScore,
    val title: String,
    val scale: String,
    val intro: String,
    val measures: String,
    val captured: String,
    val means: String,
    val note: String,
    val inputs: List<String>,
    val liquid: RidgeLiquid,
    val bands: List<GuideBand> = emptyList(),
)

internal const val GUIDE_CONFIDENCE =
    "A Calibrating tag means NOOP doesn’t have enough data yet, or the number isn’t final, so it can still move a lot. " +
        "Charge needs 4 nights to start, and your normal ranges become personal after 14 tracked nights. Without the tag a " +
        "number is still an estimate."

internal fun guideContent(score: GuideScore): GuideContent = when (score) {
    GuideScore.READINESS -> GuideContent(
        score, "Readiness", "0–100 · how ready you are now",
        intro = "Readiness blends this morning’s Charge and Rest, then comes down as you build Effort.",
        measures = "How ready you are right now to take on more today.",
        captured = "Each morning it blends Charge (80%) and Rest (20%): Charge to the power 0.8 times Rest to the power 0.2. " +
            "Rest also feeds into Charge, so it counts twice. Effort then takes off up to half, reached at your Effort target: " +
            "86 with a Charge of 67 or more, 67 from 34 to 66, and 48 below 34. The score moves halfway to a new reading every " +
            "15 minutes, and the word changes once it is 2 points past an edge.",
        means = "A higher category leaves more room to train. Today shows the word without a number; past days and the Trends " +
            "calendar use the morning figure.",
        note = "NOOP’s own weights, not validated against how ready people felt. Not medical advice.",
        inputs = listOf("Charge", "Rest", "Effort", "Effort target"),
        liquid = RidgeLiquid.CHARGE, bands = ScoringGuideBands.readiness(),
    )
    GuideScore.CHARGE -> GuideContent(
        score, "Charge", "0–100 · how recovered your body is",
        intro = "Charge reads your overnight signals and tells you how recovered you are this morning.",
        measures = "How recovered your body is this morning, from 0 to 100.",
        captured = "From last night: HRV, which counts most, then resting heart rate, sleep, breathing rate and skin temperature. " +
            "Your heart signals, breathing and skin temperature are compared with your own baseline, which gives recent nights " +
            "more weight. A signal that’s missing is left out. Charge needs 4 nights before it starts.",
        means = "Higher means your body is better placed for strain today. It sets the Effort target in Today’s plan.",
        note = "An estimate, not a diagnosis.",
        inputs = listOf("HRV", "Resting HR", "Sleep performance", "Respiratory rate", "Skin temperature"),
        liquid = RidgeLiquid.CHARGE, bands = ScoringGuideBands.chargeShown(),
    )
    GuideScore.EFFORT -> GuideContent(
        score, "Effort", "0–100 · cardiovascular load today",
        intro = "Effort grows with time at a raised heart rate and gets harder to climb the higher you go.",
        measures = "How much cardiovascular load you’ve built so far today, from 0 to 100.",
        captured = "From your heart rate, using zones between your resting and maximum heart rate; harder zones count for more. " +
            "Steps and active energy set a floor for long, easy activity. By default a new day starts when you fall asleep; " +
            "you can choose midnight in Settings.",
        means = "A higher number means a harder day. It isn’t good or bad on its own: match it to your Charge.",
        note = "A training estimate, not a diagnosis.",
        inputs = listOf("Heart rate", "HR zones", "Duration", "Resting and max HR", "Steps and energy"),
        liquid = RidgeLiquid.EFFORT, bands = ScoringGuideBands.effort(),
    )
    GuideScore.REST -> GuideContent(
        score, "Rest", "0–100 · last night’s sleep",
        intro = "Rest is mostly how long you slept, with how well you slept alongside.",
        measures = "Last night’s sleep, as a score from 0 to 100.",
        captured = "From the sleep NOOP detects. Half is time asleep against a fixed 8 hours, not your personal need; 20% is sleep " +
            "efficiency and 20% deep and REM sleep. The last 10% is set aside for consistency, but at present every night gets " +
            "the same 5 points for it. An imported WHOOP sleep performance is shown instead for any day that has one.",
        means = "Higher means a fuller night. Rest also feeds into Charge and Readiness.",
        note = "A sleep estimate, not a diagnosis.",
        inputs = listOf("Time asleep", "8-hour target", "Efficiency", "Deep and REM sleep"),
        liquid = RidgeLiquid.REST, bands = ScoringGuideBands.rest(),
    )
    GuideScore.SLEEP_SCORE -> GuideContent(
        score, "Sleep score", "0–100 · how well you slept",
        intro = "Sleep score is how well a night went, measured against your own need and routine.",
        measures = "How well last night went, from 0 to 100.",
        captured = "Half is time asleep against what you needed going into that night: your base need, plus extra for a high-Effort " +
            "day, plus part of your sleep debt. Sleeping past the need adds nothing. A quarter is how close your bed and wake " +
            "times were across that night and the three before; a two-hour spread scores nothing, and it needs at least two of " +
            "those three. A quarter is efficiency, the share of your time in bed that you were asleep.",
        means = "Higher means a fuller, steadier, less broken night. Unlike Rest it uses your own need and no sleep stages, and it " +
            "doesn’t change Charge, Rest or Readiness.",
        note = "NOOP’s own weights, not validated. Sleep stress isn’t included. Not medical advice.",
        inputs = listOf("Time asleep", "Sleep need", "Bed and wake times", "Efficiency"),
        liquid = RidgeLiquid.NIGHT,
    )
    GuideScore.STRESS -> GuideContent(
        score, "Stress", "0–3 · against your last 30 days",
        intro = "Stress compares your resting heart rate and HRV with your own recent days.",
        measures = "Load on your nervous system, estimated on a 0–3 scale.",
        captured = "The day’s score sets its resting heart rate and HRV against the 30 days before. A higher heart rate or lower " +
            "HRV raises it, and 1.5 is your usual. A stored daily stress value is used when there is one. The hour-by-hour line " +
            "on the Stress page is a separate read, which leaves out hours when motion data shows you were moving.",
        means = "Higher means your signals sit further from your usual in the strained direction. They can’t tell physical load " +
            "from mental load or illness.",
        note = "Not a diagnosis.",
        inputs = listOf("Resting HR", "HRV", "Previous 30 days"),
        liquid = RidgeLiquid.CHARGE, bands = ScoringGuideBands.stress(),
    )
    GuideScore.DEBT -> GuideContent(
        score, "Sleep debt", "time owed · last 14 tracked nights",
        intro = "Sleep debt is a running balance, not a verdict on one night.",
        measures = "The sleep you still owe, worked out over your last 14 nights with sleep data.",
        captured = "Night by night, NOOP adds your need to the debt you carried in, takes off what you slept, naps included, and " +
            "carries 55% of what’s left. Under 10 minutes counts as none. Extra sleep lowers the debt but never banks credit. " +
            "Nights without data are skipped. An imported WHOOP debt is used for its own day.",
        means = "Lower is better. A rising balance means your recent nights have fallen short of your need.",
        note = "A sleep estimate, not a diagnosis.",
        inputs = listOf("Sleep need", "Time asleep", "Naps", "14 tracked nights"),
        liquid = RidgeLiquid.REST,
    )
    GuideScore.VO2 -> GuideContent(
        score, "VO₂ max", "ml/kg/min · an estimate",
        intro = "Estimated weekly. Treat small changes as noise and watch the longer trend.",
        measures = "An estimate of how much oxygen your body can use at full effort.",
        captured = "It is worked out, not measured, and doesn’t use pace or GPS. With a waist measurement in your profile it uses " +
            "age, sex, waist, resting heart rate and how active your last week was. Without one it uses your resting heart rate " +
            "and a maximum heart rate predicted from your age. It needs your age, sex and at least 4 nights of resting heart rate.",
        means = "Higher generally points to better aerobic fitness. A wrist estimate can be well off for any one person.",
        note = "A fitness estimate, not a diagnosis.",
        inputs = listOf("Resting HR", "Age", "Sex", "Waist (optional)", "Activity (with waist)"),
        liquid = RidgeLiquid.REST,
    )
    GuideScore.BODY_AGE -> GuideContent(
        score, "Body age", "years · an estimate",
        intro = "Body age turns how a few of your measures compare with reference values into years.",
        measures = "An estimate of the age your recent measures resemble.",
        captured = "Updated weekly from your last 7 days: resting heart rate, hours of sleep, how regular your sleep is, HRV for " +
            "your age, and steps. It needs your age and at least 3 of those 5.",
        means = "Below your calendar age means these measures look better than their references; above means worse. It is a rough " +
            "guide, not your biological age.",
        note = "A model estimate, not a clinical measurement.",
        inputs = listOf("Resting HR", "Sleep hours", "Sleep regularity", "HRV", "Steps"),
        liquid = RidgeLiquid.REST,
    )
    GuideScore.FIT_AGE -> GuideContent(
        score, "Fitness age", "years · a fitness comparison",
        intro = "Fitness age compares your resting heart rate and activity with a reference person of your age.",
        measures = "The age that matches your recent aerobic-fitness signals.",
        captured = "Updated weekly from your last 7 days: your typical resting heart rate, and how many days reached an Effort of " +
            "30 or more and how high they went. It needs your age, sex and at least 4 nights of resting heart rate. It doesn’t " +
            "use your VO₂ max figure.",
        means = "Below your calendar age suggests better aerobic fitness than the reference. Allow about 5 years either way; it is " +
            "not a biological age.",
        note = "A fitness estimate, not a diagnosis.",
        inputs = listOf("Resting HR", "Active days", "Age", "Sex"),
        liquid = RidgeLiquid.REST,
    )
    GuideScore.BASELINES -> GuideContent(
        score, "Your normal", "HRV, resting HR, breathing, skin temperature",
        intro = "Your normal comes from your own nights, not from other people’s.",
        measures = "Your usual range for HRV, resting heart rate, breathing rate and skin temperature.",
        captured = "The ranges here and on charts come from your last 60 tracked nights, the average plus or minus one standard " +
            "deviation, from night 14. Before then HRV, resting heart rate and breathing use a general range.",
        means = "A reading inside your range is usual for you. Vital signs and Today’s alert use a wider range that favours recent " +
            "nights, so they can differ from a chart. Charge has its own baseline and compares each night with it.",
        note = "Your own ranges, not a medical reference.",
        inputs = listOf("Overnight readings", "Last 60 tracked nights", "Personal from night 14"),
        liquid = RidgeLiquid.CHARGE,
    )
}
