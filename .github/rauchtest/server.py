"""Kleiner Ersatz-Server für den Rauchtest: /health wie Inventar, eine Seite
mit dem Foto-Feld der echten Oberfläche, /upload, das die Bytes zählt, und
/scan wie in Inventar. Jeder Aufruf der Seite wird gezählt (Wischen = neu laden)."""
import html
import http.server
from pathlib import Path
from urllib.parse import parse_qs, urlparse

SEITE = (Path(__file__).parent / "seite.html").read_bytes()


class Handler(http.server.BaseHTTPRequestHandler):
    def _senden(self, code, body, typ="text/plain; charset=utf-8"):
        self.send_response(code)
        self.send_header("Content-Type", typ)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        adresse = urlparse(self.path)
        if adresse.path == "/health":
            self._senden(200, b'{"status": "ok"}', "application/json")
        elif adresse.path == "/scan":
            code = html.escape(parse_qs(adresse.query).get("code", [""])[0])
            self._senden(200, f"<!doctype html><meta charset=utf-8><h1>Gescannt: {code}</h1>".encode(),
                         "text/html; charset=utf-8")
        else:
            zaehler = Path("seitenaufrufe.txt")
            zaehler.write_text(str(int(zaehler.read_text() or 0) + 1 if zaehler.exists() else 1))
            self._senden(200, SEITE, "text/html; charset=utf-8")

    def do_POST(self):
        laenge = int(self.headers.get("Content-Length", "0"))
        daten = self.rfile.read(laenge)
        Path("upload-bytes.txt").write_text(str(len(daten)))
        self._senden(200, f"OK {len(daten)}".encode())


http.server.ThreadingHTTPServer(("0.0.0.0", 8106), Handler).serve_forever()
