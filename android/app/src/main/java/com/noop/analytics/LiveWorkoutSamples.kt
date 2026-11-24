package com.noop.analytics

import com.noop.data.HrSample

/**
 * The heart-rate samples a live-recorded workout collects. The app adds one for every strap state update, and a state
 * update can be a new battery reading or a new R-R beat as well as a new heart rate, so several can land in the same
 * whole second. Appending each one gave the calorie and Effort estimates several samples for one second. One second keeps
 * one sample, the latest.
 */
internal object LiveWorkoutSamples {
    fun append(samples: List<HrSample>, sample: HrSample): List<HrSample> =
        if (samples.isNotEmpty() && samples.last().ts == sample.ts) samples.dropLast(1) + sample else samples + sample
}
