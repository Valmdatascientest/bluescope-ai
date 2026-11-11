package com.bluescope.ai.domain.model

/**
 * Immutable binary value object.
 *
 * Both construction and reads use defensive copies so a platform callback cannot mutate a
 * snapshot after it crossed the domain boundary. Equality is based on content, not identity.
 */
class BinaryData private constructor(private val content: ByteArray) {
    val size: Int get() = content.size

    fun isEmpty(): Boolean = content.isEmpty()

    fun toByteArray(): ByteArray = content.copyOf()

    fun toHex(maxBytes: Int = content.size): String {
        require(maxBytes >= 0) { "maxBytes doit être positif" }
        val displayed = content.take(maxBytes).joinToString(separator = "") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
        return if (maxBytes < content.size) "$displayed…" else displayed
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is BinaryData && content.contentEquals(other.content))

    override fun hashCode(): Int = content.contentHashCode()

    override fun toString(): String = "BinaryData(size=$size)"

    companion object {
        val EMPTY = BinaryData(byteArrayOf())

        fun of(bytes: ByteArray): BinaryData =
            if (bytes.isEmpty()) EMPTY else BinaryData(bytes.copyOf())

        fun of(vararg bytes: Int): BinaryData = of(
            ByteArray(bytes.size) { index ->
                require(bytes[index] in 0..255) { "Chaque octet doit être compris entre 0 et 255" }
                bytes[index].toByte()
            },
        )
    }
}
