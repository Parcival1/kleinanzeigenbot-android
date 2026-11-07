package de.kleinanzeigenbot.app

import java.net.URI

/**
 * Zentrale Anmeldung (SSO, z. B. Authentik) in der WebView.
 *
 * Fremde Seiten öffnet die App sonst im Browser. Ab „/login/sso“ auf dem Server bleibt die erste
 * fremde Adresse (der Anbieter) in der App, bis der Server wieder eine andere Seite zeigt – sonst
 * landete die Anmeldung im Browser und die App bliebe abgemeldet. Andere fremde Seiten gehen
 * auch während der Anmeldung in den Browser.
 */
class Anmeldung {
    private var laeuft = false
    private var anbieter: String? = null

    /** true = in der App laden, false = im Browser öffnen. */
    fun inAppLaden(server: String, url: String): Boolean {
        if (ServerAdresse.gehoertZu(server, url)) {
            serverSeite(url)
            return true
        }
        if (!laeuft) return false
        val ursprung = ursprung(url) ?: return false
        if (anbieter == null) anbieter = ursprung
        return anbieter == ursprung
    }

    /** Eine Seite ist fertig geladen (auch nach „Zurück“, das [inAppLaden] nicht sieht). */
    fun geladen(server: String, url: String) {
        if (ServerAdresse.gehoertZu(server, url)) serverSeite(url)
    }

    private fun serverSeite(url: String) {
        val pfad = try { URI(url).path ?: "" } catch (e: Exception) { "" }
        when {
            pfad == "/login/sso" -> { laeuft = true; anbieter = null }
            pfad.startsWith("/login/sso/") -> Unit  // Rückkehr vom Anbieter, gleich danach die Zielseite
            else -> { laeuft = false; anbieter = null }
        }
    }

    private fun ursprung(url: String): String? {
        val u = try { URI(url) } catch (e: Exception) { return null }
        val schema = u.scheme?.lowercase() ?: return null
        if (schema != "http" && schema != "https") return null
        val host = u.host?.lowercase() ?: return null
        val port = if (u.port != -1) u.port else if (schema == "https") 443 else 80
        return "$schema://$host:$port"
    }
}
