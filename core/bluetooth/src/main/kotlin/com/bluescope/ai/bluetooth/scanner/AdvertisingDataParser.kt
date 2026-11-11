package com.bluescope.ai.bluetooth.scanner

/** Bounded parser for fields Android's [android.bluetooth.le.ScanRecord] does not expose directly. */
internal object AdvertisingDataParser {
    private const val DATA_TYPE_APPEARANCE = 0x19
    private const val MAX_RECORD_SIZE = 1_650

    fun appearance(record: ByteArray?): Int? {
        if (record == null || record.isEmpty() || record.size > MAX_RECORD_SIZE) return null
        var index = 0
        while (index < record.size) {
            val fieldLength = record[index].toInt() and 0xFF
            if (fieldLength == 0) return null
            val typeIndex = index + 1
            val fieldEndExclusive = typeIndex + fieldLength
            if (typeIndex >= record.size || fieldEndExclusive > record.size) return null
            val type = record[typeIndex].toInt() and 0xFF
            if (type == DATA_TYPE_APPEARANCE) {
                if (fieldLength < 3 || typeIndex + 2 >= record.size) return null
                val low = record[typeIndex + 1].toInt() and 0xFF
                val high = record[typeIndex + 2].toInt() and 0xFF
                return low or (high shl 8)
            }
            index = fieldEndExclusive
        }
        return null
    }
}
