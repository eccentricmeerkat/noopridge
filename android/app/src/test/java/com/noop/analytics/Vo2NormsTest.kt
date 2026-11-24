package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/** The band is the published FRIEND 25th–75th percentile (Kaminsky 2015, Table 3), interpolated between decade midpoints. */
class Vo2NormsTest {
    @Test
    fun nearADecadeMidpointItIsAlmostTheTablesOwnValue() {
        // Ages are whole years, so 25 and 45 sit 0.5 y past a midpoint: 0.05 of the way to the next decade's row.
        val m25 = Vo2Norms.typicalRange(25, "male")
        assertEquals(40.1 + (35.9 - 40.1) * 0.05, m25.start, 1e-9)
        val f45 = Vo2Norms.typicalRange(45, "female")
        assertEquals(22.1 + (19.9 - 22.1) * 0.05, f45.start, 1e-9)
        assertEquals(32.4 + (27.6 - 32.4) * 0.05, f45.endInclusive, 1e-9)
    }

    @Test
    fun interpolatesBetweenDecadeMidpoints() {
        // Men, 40: 5.5 years past the 30s midpoint (34.5), so 0.55 of the way to the 40s midpoint (44.5).
        val r = Vo2Norms.typicalRange(40, "male")
        assertEquals(35.9 + (31.9 - 35.9) * 0.55, r.start, 1e-9)    // 33.7
        assertEquals(49.2 + (45.0 - 49.2) * 0.55, r.endInclusive, 1e-9) // 46.89
    }

    @Test
    fun heldAtTheEndRowsOutsideTheTable() {
        assertEquals(40.1, Vo2Norms.typicalRange(18, "male").start, 1e-9)
        assertEquals(30.4, Vo2Norms.typicalRange(90, "male").endInclusive, 1e-9)
        assertEquals(15.6, Vo2Norms.typicalRange(90, "female").start, 1e-9)
    }

    @Test
    fun nonbinaryUsesTheMensTableLikeFitnessAge() {
        assertEquals(Vo2Norms.typicalRange(40, "male"), Vo2Norms.typicalRange(40, "nonbinary"))
    }

    @Test
    fun womenSitBelowMenAtEveryAge() {
        for (age in 20..79) {
            val w = Vo2Norms.typicalRange(age, "female"); val m = Vo2Norms.typicalRange(age, "male")
            assert(w.start < m.start && w.endInclusive < m.endInclusive) { "age $age" }
        }
    }
}
