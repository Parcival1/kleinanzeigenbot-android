# KleinanzeigenBot für Android

Android-App für den selbst gehosteten KleinanzeigenBot (Inventar- und
Inseratsverwaltung mit kleinanzeigen-bot). Die App zeigt die Weboberfläche des eigenen Servers und
ergänzt, was im Browser am Handy fehlt:

- **Kamera für kleine Bildschirme:** Vorschau über den ganzen Bildschirm,
  großer Auslöser in Daumenhöhe, mehrere Fotos am Stück, Vorschaubilder zum
  Verwerfen, Blitz, Tippen zum Scharfstellen, Zoomen mit zwei Fingern.
  „Fertig“ übergibt alle Fotos an den Upload der Weboberfläche.
- **Etiketten drucken** über den Android-Druckdienst.
- **Updates:** Bei jedem Öffnen (höchstens einmal pro Minute) sucht die App
  unter den Releases dieses Repos nach einer neueren Version und bietet sie
  an. „Später“ blendet dieselbe Version 4 Stunden aus. Nach einem Update
  zeigt sie einmal „Was ist neu“.

Die Serveradresse wird beim ersten Start eingegeben; im Code steht keine.
Der Server ist nur im eigenen Netz erreichbar.

## Bauen und veröffentlichen

Gebaut wird nur auf GitHub Actions (`.github/workflows/android.yml`):
Unit-Tests, Bau, Signaturprüfung, Rauchtest im Emulator (erster Start,
Serveradresse, Kamera, ein Foto, Upload). Ein Tag `v*` legt ein Release als
**Entwurf** an; veröffentlicht wird es erst, wenn alle Tests grün sind.

Neues Release:

1. `VERSION` in `marke.properties` erhöhen (je Release +0.1).
2. Oben in `app/src/main/assets/neuigkeiten.json` einen Eintrag für die
   Version ergänzen – laienverständlich.
3. Committen, pushen, Tag `v<VERSION>` pushen.

`APPLICATION_ID` nie ändern, sonst installiert Android eine zweite App statt
eines Updates. Alle Releases müssen mit demselben Schlüssel signiert sein
(Prüfsumme des Zertifikats steht im Workflow).

## Lizenz

MIT
