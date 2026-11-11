package com.bluescope.ai.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BinaryDataTest {
    @Test
    fun `construction defensively copies input`() {
        val source = byteArrayOf(0x01, 0x02)
        val value = BinaryData.of(source)
        source[0] = 0x7F
        assertThat(value.toHex()).isEqualTo("0102")
    }

    @Test
    fun `read defensively copies content`() {
        val value = BinaryData.of(0xAA, 0xBB)
        val exported = value.toByteArray()
        exported[0] = 0
        assertThat(value.toHex()).isEqualTo("AABB")
    }

    @Test
    fun `equality and hash code use byte content`() {
        val first = BinaryData.of(byteArrayOf(1, 2, 3))
        val second = BinaryData.of(byteArrayOf(1, 2, 3))
        assertThat(first).isEqualTo(second)
        assertThat(first.hashCode()).isEqualTo(second.hashCode())
    }

    @Test
    fun `toString never exposes payload`() {
        val value = BinaryData.of(0xDE, 0xAD, 0xBE, 0xEF)
        assertThat(value.toString()).isEqualTo("BinaryData(size=4)")
        assertThat(value.toString()).doesNotContain("DEAD")
    }

    @Test
    fun `integer factory rejects values outside one byte`() {
        assertThrows(IllegalArgumentException::class.java) { BinaryData.of(256) }
    }
}
