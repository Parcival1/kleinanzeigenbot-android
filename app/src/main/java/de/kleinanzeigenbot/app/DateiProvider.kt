package de.kleinanzeigenbot.app

import androidx.core.content.FileProvider

/** Eigene Klasse, damit es keinen Konflikt mit FileProvidern aus Bibliotheken gibt. */
class DateiProvider : FileProvider()
