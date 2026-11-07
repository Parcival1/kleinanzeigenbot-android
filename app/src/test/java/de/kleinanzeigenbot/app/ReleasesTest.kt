package de.kleinanzeigenbot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleasesTest {
    private val server = "http://192.168.30.15:8127"
    private val sha = "a".repeat(64)

    @Test fun angebotDesServers() {
        val json = """{"version": "1.2", "apk": "/app/android/kleinanzeigenbot-1.2.apk", "groesse": 4000000, "sha256": "$sha"}"""
        val r = Releases.auswaehlen(json, server)!!
        assertEquals("1.2", r.version)
        assertEquals("$server/app/android/kleinanzeigenbot-1.2.apk", r.apkUrl)
        assertEquals(4000000L, r.groesse)
        assertEquals(sha, r.sha256)
    }

    @Test fun fremdeAdressenUndUnvollstaendigesZaehlenNicht() {
        assertNull(Releases.auswaehlen("""{"version": "1.2", "apk": "https://boese.example/x.apk", "sha256": "$sha"}""", server))
        assertNull(Releases.auswaehlen("""{"version": "1.2", "apk": "//boese.example/x.apk", "sha256": "$sha"}""", server))
        assertNull(Releases.auswaehlen("""{"version": "1.2", "apk": "/app/android/x.apk"}""", server))
        assertNull(Releases.auswaehlen("""{"apk": "/app/android/x.apk", "sha256": "$sha"}""", server))
        assertNull(Releases.auswaehlen("kein json", server))
    }
}
