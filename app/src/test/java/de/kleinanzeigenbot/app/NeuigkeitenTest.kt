package de.kleinanzeigenbot.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NeuigkeitenTest {
    private val alle = listOf(
        Neuigkeit("1.2", "", listOf("c")),
        Neuigkeit("1.1", "", listOf("b")),
        Neuigkeit("1.0", "", listOf("a")),
    )

    @Test fun nurEintraegeSeitDerZuletztGezeigten() {
        assertEquals(listOf("1.2", "1.1"), Neuigkeiten.zwischen(alle, "1.0", "1.2").map { it.version })
        assertEquals(listOf("1.1"), Neuigkeiten.zwischen(alle, "1.0", "1.1").map { it.version })
    }

    @Test fun mitgelieferteDateiIstGueltigUndEnthaeltDieAktuelleVersion() {
        val json = File("src/main/assets/neuigkeiten.json").readText()
        val eintraege = Neuigkeiten.lesen(json)
        val marke = File("../marke.properties").readLines()
            .first { it.startsWith("VERSION=") }.removePrefix("VERSION=").trim()
        assertEquals(marke, eintraege.first().version)
        eintraege.forEach { assert(it.punkte.isNotEmpty()) }
    }
}
