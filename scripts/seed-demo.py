"""Opt-in API-based demo catalogue. Python 3; standard library only.

Never reads local PostgreSQL, uploads, or credentials. Use only against an empty
disposable demo deployment. Generated illustrations are original, not product photos.
"""
import argparse
import json
import math
from pathlib import Path
import secrets
import struct
import urllib.error
import urllib.parse
import urllib.request
import zlib

CATALOGUE = Path(__file__).with_name("demo-catalogue.json")


def illustration(category):
    """Small original raster illustrations, encoded as valid PNGs without dependencies."""
    width, height = 640, 480
    colors = {"Soccer": (52, 89, 75), "Basketball": (176, 91, 41),
              "Tennis": (125, 148, 64), "Fitness": (69, 86, 104),
              "Hockey": (66, 86, 101), "Baseball": (166, 104, 79), "Other": (181, 127, 45)}
    accent = colors[category]
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            dx, dy = x - 320, y - 225
            color = (237, 240, 231)
            if (dx / 175) ** 2 + ((y - 370) / 21) ** 2 < 1:
                color = (216, 222, 209)
            if category == "Fitness":
                if (abs(dx) < 155 and abs(dy) < 17) or (65 < abs(dx) < 115 and abs(dy) < 92) or (115 < abs(dx) < 145 and abs(dy) < 65):
                    color = accent
            elif category == "Hockey":
                if (dx / 136) ** 2 + (dy / 62) ** 2 < 1 or (abs(dx) < 125 and 0 < dy < 46):
                    color = accent
                if (dx / 125) ** 2 + ((dy + 16) / 45) ** 2 < 1:
                    color = (42, 55, 64)
            elif category == "Other":
                if 100 > dy > -110 and abs(dx) < (dy + 140) * 0.58 or abs(dx) < 155 and 100 < dy < 125:
                    color = accent
                if 30 < dy < 62 and abs(dx) < (dy + 140) * 0.58:
                    color = (250, 248, 232)
            elif dx * dx + dy * dy < 128 ** 2:
                color = accent
                if category == "Soccer":
                    angle = math.atan2(dy, dx)
                    radius = math.hypot(dx, dy)
                    if radius < 39 or (radius > 78 and math.cos(5 * angle) > 0.65):
                        color = (238, 239, 224)
                elif category == "Basketball":
                    if abs(dx) < 4 or abs(dy) < 4 or abs(abs(dx) - (55 + 0.002 * dy * dy)) < 4:
                        color = (66, 47, 35)
                elif category == "Tennis":
                    if abs(abs(dx) - (55 + 0.0025 * dy * dy)) < 5:
                        color = (244, 246, 216)
                elif category == "Baseball":
                    color = (249, 244, 226)
                    if abs(abs(dx) - (65 + 0.002 * dy * dy)) < 4:
                        color = (170, 65, 48)
            rows.extend(color)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")


def seed(base):
    token = None
    def api(path, data=None, content_type="application/json", method=None):
        headers = {"Content-Type": content_type}
        if token:
            headers["Authorization"] = "Bearer " + token
        body = json.dumps(data).encode() if isinstance(data, dict) else data
        request = urllib.request.Request(base + "/api" + path, data=body, headers=headers, method=method)
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    if api("/listings?size=1")["totalElements"] != 0:
        raise RuntimeError("Target already has available listings. Refusing to seed it.")
    # A fixed marker account makes retries refuse a partially seeded target.
    # Its random password is never printed or saved; visitors create their own accounts.
    email, password = "demo-catalogue@example.test", secrets.token_urlsafe(32)
    api("/auth/register", {"fullName": "SportMarket Demo", "email": email, "password": password})
    token = api("/auth/login", {"email": email, "password": password})["accessToken"]
    categories = {item["name"]: item["id"] for item in api("/categories")}
    for item in json.loads(CATALOGUE.read_text(encoding="utf-8")):
        payload = {key: value for key, value in item.items() if key != "category"}
        payload["categoryId"] = categories[item["category"]]
        listing = api("/listings", payload)
        boundary = "sportmarket-" + secrets.token_hex(16)
        image = illustration(item["category"])
        multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="demo.png"\r\nContent-Type: image/png\r\n\r\n'.encode()
                     + image + f"\r\n--{boundary}--\r\n".encode())
        api(f'/listings/{listing["id"]}/images', multipart, "multipart/form-data; boundary=" + boundary)
        print("Created:", item["title"])
    print("Demo catalogue created. Visitors register their own accounts to test listing management.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_url", help="Site origin, e.g. https://your-service.onrender.com")
    parser.add_argument("--confirm-empty-demo-target", action="store_true", required=True)
    args = parser.parse_args()
    target = urllib.parse.urlsplit(args.base_url)
    if target.scheme not in ("http", "https") or not target.hostname or target.username or target.password or target.path not in ("", "/") or target.query or target.fragment:
        parser.error("Supply a site origin without credentials, paths, queries, or fragments.")
    if target.scheme != "https" and target.hostname not in ("localhost", "127.0.0.1"):
        parser.error("Remote targets must use HTTPS.")
    try:
        seed(args.base_url.rstrip("/"))
    except urllib.error.HTTPError as error:
        raise SystemExit(f"API returned HTTP {error.code}. Stopped; completed demo records remain. A duplicate marker account prevents reruns.")
    except (RuntimeError, urllib.error.URLError) as error:
        raise SystemExit(str(error))
