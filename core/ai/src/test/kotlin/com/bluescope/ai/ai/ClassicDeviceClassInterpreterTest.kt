package com.bluescope.ai.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClassicDeviceClassInterpreterTest {
    @Test
    fun `extracts major class while ignoring service and minor bits`() {
        val interpreted = ClassicDeviceClassInterpreter.interpret(0x200000 or 0x0400 or 0x0018)

        assertThat(interpreted?.category).isEqualTo("Audio/Vidéo")
    }

    @Test
    fun `returns no interpretation for uncategorized class`() {
        assertThat(ClassicDeviceClassInterpreter.interpret(0)).isNull()
    }
}
