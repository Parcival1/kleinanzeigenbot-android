#!/bin/bash
# Rauchtest im Emulator: erster Start, Serveradresse, Weboberfläche,
# eigene Kamera, ein Foto aufnehmen, Upload kommt beim Server an.
set -euo pipefail
APK="$1"
PKG=de.kleinanzeigenbot.app
HIER="$(cd "$(dirname "$0")" && pwd)"
SCHRITT=0

bild() { SCHRITT=$((SCHRITT + 1)); adb exec-out screencap -p > "rauchtest-$SCHRITT-$1.png" || true; }

abzug() {
    for _ in 1 2 3; do
        adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1 && return 0
        sleep 1
    done
    return 1
}

# warte <art> <wert> [sekunden]: bis der Knoten da ist, Mitte steht dann in $XY
warte() {
    local ende=$((SECONDS + ${3:-60}))
    while [ $SECONDS -lt $ende ]; do
        if abzug && XY="$(python3 "$HIER/finde.py" ui.xml "$1" "$2")"; then return 0; fi
        # Systemmeldung „… isn't responding“ (z. B. Pixel Launcher im Emulator) wegtippen.
        if [ -f ui.xml ] && W="$(python3 "$HIER/finde.py" ui.xml id android:id/aerr_wait)"; then
            echo "Systemmeldung weggetippt"
            adb shell input tap $W
        fi
        sleep 2
    done
    echo "::error::Nicht gefunden: $1 $2"
    bild fehler
    cat ui.xml || true
    adb logcat -d | grep -E "AndroidRuntime|$PKG" | tail -80 || true
    exit 1
}

tippe() { adb shell input tap $XY; }

python3 "$HIER/server.py" > server.log 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT
sleep 1
curl -fsS http://127.0.0.1:8106/health

# Absturz-/Hängt-Meldungen anderer Apps unterdrücken (Emulator auf dem Runner ist langsam).
adb shell settings put global hide_error_dialogs 1 || true
adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.CAMERA
adb logcat -c
adb shell am start -W -n "$PKG/de.kleinanzeigenbot.app.MainActivity"

echo "== erster Start: Willkommen und Serveradresse"
warte text "Willkommen" 90
bild willkommen
# Die Emulator-Tastatur verschluckt die erste Eingabe gelegentlich: prüfen und wiederholen.
for versuch in 1 2 3; do
    warte id "$PKG:id/eingabe"; tippe
    sleep 2
    adb shell input text "10.0.2.2:8106"
    sleep 1
    abzug && python3 "$HIER/finde.py" ui.xml text "10.0.2.2:8106" >/dev/null && break
    echo "Eingabe nicht angekommen (Versuch $versuch)"
done
warte text "10.0.2.2:8106" 10
# Enter statt Knopf: die Tastatur verdeckt/verschiebt „Verbinden“ im Emulator.
adb shell input keyevent 66

echo "== Weboberfläche geladen"
warte text "Rauchtest-Seite" 90
bild seite

echo "== Foto-Feld öffnet die eigene Kamera"
warte text "Foto aufnehmen"; tippe
warte id "$PKG:id/ausloeser" 60
sleep 4
bild kamera
tippe

echo "== Foto aufgenommen"
warte text "1 Foto" 60
bild ein-foto
warte id "$PKG:id/fertig"; tippe

echo "== Upload kommt an"
warte text "hochgeladen OK" 90
bild hochgeladen
BYTES="$(cat upload-bytes.txt)"
echo "Upload: $BYTES Bytes"
[ "$BYTES" -gt 20000 ] || { echo "::error::Upload zu klein ($BYTES Bytes)"; exit 1; }

echo "== Nach unten wischen lädt die Seite neu"
VORHER="$(cat seitenaufrufe.txt)"
for _ in 1 2 3; do
    adb shell input swipe 540 500 540 1600 400
    sleep 4
    [ "$(cat seitenaufrufe.txt)" -gt "$VORHER" ] && break
done
echo "Seitenaufrufe: $VORHER -> $(cat seitenaufrufe.txt)"
[ "$(cat seitenaufrufe.txt)" -gt "$VORHER" ] || { echo "::error::Wischen hat die Seite nicht neu geladen"; bild wischen; exit 1; }
warte text "Rauchtest-Seite" 60

echo "== Scannen: Nummer eintippen öffnet den Artikel"
warte text "Etikett scannen"; tippe
warte id "$PKG:id/eintippen" 60
sleep 3
bild scanner
tippe
warte id "android:id/button1" 30
# Wie bei der Serveradresse: Feld antippen, tippen, prüfen, ggf. wiederholen.
for versuch in 1 2 3; do
    warte text "I-00012" 10; tippe          # Platzhalter des leeren Felds
    sleep 2
    adb shell input text "I-00042"
    sleep 1
    abzug && python3 "$HIER/finde.py" ui.xml text "I-00042" >/dev/null && break
    echo "Eingabe nicht angekommen (Versuch $versuch)"
done
warte text "I-00042" 10
bild eintippen
# Enter statt „Öffnen“: die Tastatur verschiebt den Dialog.
adb shell input keyevent 66
warte text "Gescannt: I-00042" 60
bild gescannt

echo "== App läuft noch"
adb shell pidof "$PKG"
# Nur Abstürze der App zählen; uiautomator selbst stürzt im Emulator gelegentlich ab.
if adb logcat -d -b crash | grep -q "Process: $PKG"; then
    adb logcat -d -b crash
    exit 1
fi
echo "Rauchtest bestanden."
