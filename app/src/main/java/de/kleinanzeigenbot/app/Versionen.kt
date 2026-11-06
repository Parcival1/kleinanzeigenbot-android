package de.kleinanzeigenbot.app

/** Versionen zahlenweise vergleichen: "v1.10" > "1.9", "1.0" == "1". */
object Versionen {
    fun teile(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .map { teil -> teil.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun vergleiche(a: String, b: String): Int {
        val x = teile(a)
        val y = teile(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val unterschied = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (unterschied != 0) return unterschied.coerceIn(-1, 1)
        }
        return 0
    }

    fun istNeuer(kandidat: String, installiert: String): Boolean = vergleiche(kandidat, installiert) > 0
}
