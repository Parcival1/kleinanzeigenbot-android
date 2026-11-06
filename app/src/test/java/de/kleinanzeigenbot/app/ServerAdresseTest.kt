package de.kleinanzeigenbot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAdresseTest {
    @Test fun ohneSchemaWirdHttp() {
        assertEquals("http://192.168.1.20:8106", ServerAdresse.normalisieren(" 192.168.1.20:8106 "))
    }

    @Test fun schraegstrichAmEndeFaelltWeg() {
        assertEquals("https://inventar.local", ServerAdresse.normalisieren("https://inventar.local/"))
        assertEquals("http://host:8106/inventar", ServerAdresse.normalisieren("host:8106/inventar/"))
    }

    @Test fun unsinnWirdAbgelehnt() {
        assertNull(ServerAdresse.normalisieren(""))
        assertNull(ServerAdresse.normalisieren("zwei worte"))
        assertNull(ServerAdresse.normalisieren("ftp://host"))
        assertNull(ServerAdresse.normalisieren("http://"))
    }

    @Test fun gleicherServer() {
        val basis = "http://192.168.1.20:8106"
        assertTrue(ServerAdresse.gehoertZu(basis, "http://192.168.1.20:8106/items/3"))
        assertFalse(ServerAdresse.gehoertZu(basis, "http://192.168.1.20:8107/vnc.html"))
        assertFalse(ServerAdresse.gehoertZu(basis, "https://www.kleinanzeigen.de/s-anzeige/1"))
        assertTrue(ServerAdresse.gehoertZu("http://host", "http://HOST:80/x"))
    }
}
