package de.kleinanzeigenbot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleasesTest {
    private fun release(tag: String, draft: Boolean, apk: Boolean = true) = """
        {"tag_name": "$tag", "draft": $draft, "prerelease": false, "html_url": "https://github.com/x/y/releases/$tag",
         "assets": [${if (apk) """{"name": "inventar-$tag.apk", "size": 4000000, "browser_download_url": "https://dl/$tag.apk"}""" else ""}]}
    """

    @Test fun neuestesVeroeffentlichtes() {
        val json = "[${release("v1.2", draft = true)}, ${release("v1.10", draft = false)}, ${release("v1.9", draft = false)}]"
        val r = Releases.auswaehlen(json)!!
        assertEquals("1.10", r.version)
        assertEquals("https://dl/v1.10.apk", r.apkUrl)
        assertEquals(4000000L, r.groesse)
    }

    @Test fun entwuerfeUndOhneApkZaehlenNicht() {
        val json = "[${release("v2.0", draft = true)}, ${release("v1.5", draft = false, apk = false)}]"
        assertNull(Releases.auswaehlen(json))
    }
}
