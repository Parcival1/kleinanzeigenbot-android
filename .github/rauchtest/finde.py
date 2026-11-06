"""Sucht in einem uiautomator-Abzug einen Knoten und gibt dessen Mitte aus.

    finde.py ui.xml text "Foto aufnehmen"     (Text oder Beschreibung enthält …)
    finde.py ui.xml id de.kleinanzeigenbot.app:id/fertig
"""
import re
import sys
import xml.etree.ElementTree as ET

datei, art, wert = sys.argv[1:4]
for knoten in ET.parse(datei).iter("node"):
    if art == "id":
        treffer = knoten.get("resource-id") == wert
    else:
        treffer = wert in (knoten.get("text") or "") or wert in (knoten.get("content-desc") or "")
    if treffer:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", knoten.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
