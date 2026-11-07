package de.kleinanzeigenbot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnmeldungTest {
    private val server = "http://192.168.30.15:8127"
    private val authentik = "http://192.168.20.14:8106/application/o/authorize/?client_id=x"

    @Test fun fremdeSeitenOhneAnmeldungImBrowser() {
        val a = Anmeldung()
        assertTrue(a.inAppLaden(server, "$server/items"))
        assertFalse(a.inAppLaden(server, authentik))
        assertFalse(a.inAppLaden(server, "https://www.kleinanzeigen.de/s-anzeige/1"))
    }

    @Test fun anbieterBleibtWaehrendDerAnmeldungInDerApp() {
        val a = Anmeldung()
        assertTrue(a.inAppLaden(server, "$server/login/sso?next=/"))
        assertTrue(a.inAppLaden(server, authentik))
        assertTrue(a.inAppLaden(server, "http://192.168.20.14:8106/if/flow/default-authentication-flow/"))
        // Andere fremde Seiten auch jetzt nicht.
        assertFalse(a.inAppLaden(server, "https://boese.example/"))
        assertTrue(a.inAppLaden(server, "$server/login/sso/callback?code=c&state=s"))
        assertTrue(a.inAppLaden(server, "http://192.168.20.14:8106/if/flow/x/"))
        // Zielseite erreicht: Anmeldung vorbei, Anbieter wieder im Browser.
        assertTrue(a.inAppLaden(server, "$server/"))
        assertFalse(a.inAppLaden(server, authentik))
    }

    @Test fun zurueckZurAnmeldeseiteBeendetDieAnmeldung() {
        val a = Anmeldung()
        a.inAppLaden(server, "$server/login/sso")
        assertTrue(a.inAppLaden(server, authentik))
        a.geladen(server, "$server/login")
        assertFalse(a.inAppLaden(server, authentik))
    }

    @Test fun nurHttpUndHttps() {
        val a = Anmeldung()
        a.inAppLaden(server, "$server/login/sso")
        assertFalse(a.inAppLaden(server, "intent://scan#Intent;end"))
    }
}
