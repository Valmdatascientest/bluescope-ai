package com.bluescope.ai.domain.model

import java.util.UUID

@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "DeviceId ne peut pas être vide" }
    }

    companion object {
        fun random(): DeviceId = DeviceId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class ScanSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "ScanSessionId ne peut pas être vide" }
    }

    companion object {
        fun random(): ScanSessionId = ScanSessionId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class AnalysisId(val value: String) {
    init {
        require(value.isNotBlank()) { "AnalysisId ne peut pas être vide" }
    }

    companion object {
        fun random(): AnalysisId = AnalysisId(UUID.randomUUID().toString())
    }
}

/** A probability-like score whose invariant is enforced at the domain boundary. */
@JvmInline
value class Confidence private constructor(val value: Double) : Comparable<Confidence> {
    override fun compareTo(other: Confidence): Int = value.compareTo(other.value)

    fun asPercent(): Int = (value * 100).toInt()

    companion object {
        val NONE = Confidence(0.0)
        val CERTAIN = Confidence(1.0)

        fun of(value: Double): Confidence {
            require(value.isFinite() && value in 0.0..1.0) {
                "La confiance doit être un nombre fini compris entre 0 et 1"
            }
            return Confidence(value)
        }
    }
}
