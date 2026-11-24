package com.noop.analytics

/**
 * Age- and sex-specific VO₂ max norms for the Baseline "typical for N" band (B2-3-2).
 *
 * Source: Kaminsky LA, Arena R, Myers J. "Reference Standards for Cardiorespiratory Fitness Measured With
 * Cardiopulmonary Exercise Testing: Data From the Fitness Registry and the Importance of Exercise National Database
 * (FRIEND) Registry." Mayo Clin Proc 2015;90(11):1515-1523, doi:10.1016/j.mayocp.2015.07.026, Table 3 ("Men from
 * FRIEND", "Women from FRIEND": treadmill tests with measured VO₂ max, adults free of known cardiovascular disease,
 * ml/kg/min). The values below are transcribed from that table (read from the PubMed Central author manuscript,
 * PMC4919021); do not change them without re-checking it.
 *
 * Caveats shown to the reader, not hidden: the norms are for a measured treadmill VO₂ max and the app's figure is a
 * wrist estimate (±3–5), so the band is a "typical for your age" guide, not a verdict. This is a reference band for
 * one chart, not an input to the fitness-age or body-age engines, which keep their own Nes-model basis.
 */
object Vo2Norms {
    /** The 25th and 75th percentile for one decade (ml/kg/min). */
    private data class Row(val p25: Double, val p75: Double)

    /** Decades 20–29, 30–39, … 70–79 (FRIEND Table 3, men). */
    private val men = listOf(
        Row(40.1, 55.2), Row(35.9, 49.2), Row(31.9, 45.0), Row(27.1, 39.7), Row(23.7, 34.5), Row(20.4, 30.4),
    )

    /** Decades 20–29, 30–39, … 70–79 (FRIEND Table 3, women). */
    private val women = listOf(
        Row(30.5, 44.7), Row(25.3, 36.1), Row(22.1, 32.4), Row(19.9, 27.6), Row(17.2, 23.8), Row(15.6, 20.8),
    )

    /** The age at the middle of the first decade (20–29); each next decade is ten years on. */
    private const val FIRST_MID = 24.5

    /**
     * The typical (25th–75th percentile) VO₂ max for [age] and [sex] ("male", "female" or "nonbinary"; anything
     * but "female" uses the men's table, as [FitnessAgeEngine] does). The table is by decade, so the band is
     * interpolated linearly between decade midpoints (24.5, 34.5, …, 74.5) to avoid a jump on a birthday, and held
     * at the end rows beyond them.
     */
    fun typicalRange(age: Int, sex: String): ClosedFloatingPointRange<Double> {
        val rows = if (sex.equals("female", ignoreCase = true)) women else men
        val pos = ((age - FIRST_MID) / 10.0).coerceIn(0.0, (rows.size - 1).toDouble())
        val i = pos.toInt().coerceAtMost(rows.size - 2)
        val t = pos - i
        val lo = rows[i].p25 + (rows[i + 1].p25 - rows[i].p25) * t
        val hi = rows[i].p75 + (rows[i + 1].p75 - rows[i].p75) * t
        return lo..hi
    }
}
