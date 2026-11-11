package com.bluescope.ai.bluetooth.scanner

import kotlin.math.roundToInt

/** Exponential moving average for display only; raw observations remain available to persistence. */
internal class RssiSmoother(private val alpha: Double = 0.35) {
    init {
        require(alpha > 0.0 && alpha <= 1.0)
    }

    private val averages = mutableMapOf<ObservationSessionKey, Double>()

    fun add(key: ObservationSessionKey, rssi: Int): Int {
        val previous = averages[key]
        val average = if (previous == null) rssi.toDouble() else alpha * rssi + (1.0 - alpha) * previous
        averages[key] = average
        return average.roundToInt()
    }
}
