package dev.charlesferreira.treino.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class InitialsTest {
    @Test
    fun `same as initials in plan ts`() {
        assertEquals("CF", initials("Charles Ferreira"))
        assertEquals("CS", initials("  charles  de  souza "))
        assertEquals("CH", initials("Charles"))
        assertEquals("A", initials("a"))
        assertEquals("", initials("   "))
    }
}
