package de.kleinanzeigenbot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionenTest {
    @Test fun zahlenweiseNichtAlsText() {
        assertTrue(Versionen.istNeuer("1.10", "1.9"))
        assertFalse(Versionen.istNeuer("1.9", "1.10"))
    }

    @Test fun vorangestelltesVWirdIgnoriert() {
        assertEquals(0, Versionen.vergleiche("v1.2", "1.2"))
        assertTrue(Versionen.istNeuer("v1.3", "1.2"))
    }

    @Test fun fehlendeStellenSindNull() {
        assertEquals(0, Versionen.vergleiche("1", "1.0.0"))
        assertTrue(Versionen.istNeuer("1.0.1", "1.0"))
    }

    @Test fun gleicheVersionIstNichtNeuer() {
        assertFalse(Versionen.istNeuer("1.0", "1.0"))
    }
}
