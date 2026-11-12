"""Download verified presentation assets. Does not connect to or modify MySQL."""
import concurrent.futures
import html
import io
import json
from pathlib import Path
import re
import unicodedata
import urllib.parse
import urllib.request

from bs4 import BeautifulSoup
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "app/web"
DEST = WEB / "photos"
PAGES = [
    "https://www.manutd.com/en/players-and-staff/first-team",
    "https://www.manutd.com/en/teams/under-21s-team",
    "https://www.manutd.com/en/teams/mens-team/shea-lacey",
]
# Identity codes cross-checked against the 2025-26 FPL public data snapshot.
FPL_CODES = {"casemiro": "61256", "tyrell-malacia": "222690", "tyler-fredricson": "547676"}
FPL_REFERENCE = "https://github.com/vaastav/Fantasy-Premier-League/blob/master/data/2025-26/players_raw.csv"


def slug(name):
    name = unicodedata.normalize("NFD", name).replace("ı", "i")
    name = "".join(c for c in name if not unicodedata.combining(c)).lower()
    return re.sub(r"[^a-z0-9]+", "-", name).strip("-")


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://www.manutd.com/"})
    with urllib.request.urlopen(req, timeout=25) as response:
        return response.read()


def official_images(url):
    soup = BeautifulSoup(get(url), "html.parser")
    images = {}
    for image in soup.find_all("img"):
        src = image.get("src", "")
        if not src.startswith("https://dynamic-crop-cdn.scoreplay.io/"):
            continue
        link = image.find_parent("a")
        href = link.get("href", "") if link else ""
        if href.startswith("/en/teams/"):
            images[href.rsplit("/", 1)[-1]] = (src, "https://www.manutd.com" + href)
        elif "shea lacey profile" in image.get("alt", "").lower():
            images["shea-lacey"] = (src, url)
    return images


def main():
    DEST.mkdir(exist_ok=True)
    catalog = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        for found in pool.map(official_images, PAGES):
            catalog.update(found)
    seed = (ROOT / "db/04_seed.sql").read_text("utf-8")
    players_section = seed.split("INSERT INTO CauThu(", 1)[1].split(";", 1)[0]
    players = re.findall(r"\(\d+,'([^']+)','(\d{4}-\d{2}-\d{2})',", players_section)
    records = {}

    def download(player):
        name, birth = player
        key = slug(name)
        reference = None
        if key in catalog:
            url, source = catalog[key]
            split = urllib.parse.urlsplit(url)
            query = dict(urllib.parse.parse_qsl(split.query))
            query.update(w="240", h="327")
            url = urllib.parse.urlunsplit(split._replace(query=urllib.parse.urlencode(query)))
            owner = "Manchester United"
        else:
            code = FPL_CODES[key]  # Fail explicitly for an unverified identity.
            url = f"https://resources.premierleague.com/premierleague25/photos/players/110x140/{code}.png"
            source = url
            reference = FPL_REFERENCE
            owner = "Premier League"
        picture = Image.open(io.BytesIO(get(url))).convert("RGBA")
        picture.thumbnail((240, 327))
        picture.save(DEST / (key + ".webp"), "WEBP", quality=88)
        return key + "|" + birth, {"name": name, "src": "/photos/" + key + ".webp", "source": source, "imageSource": url, "owner": owner, "identityReference": reference}

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for key, record in pool.map(download, players):
            records[key] = record
    if len(records) != len(players):
        raise RuntimeError("Incomplete photo catalog")
    (WEB / "player-photos.js").write_text("'use strict';\nwindow.PLAYER_PHOTOS=Object.freeze(" + json.dumps(records, ensure_ascii=False, indent=2) + ");\n", "utf-8")
    credits = "".join("<li><strong>" + html.escape(r["name"]) + "</strong> · © " + r["owner"] + ' · <a href="' + html.escape(r["source"], quote=True) + '">Nguồn ảnh</a>' + (' · <a href="' + r["identityReference"] + '">Đối chiếu mã cầu thủ</a>' if r["identityReference"] else "") + "</li>" for r in records.values())
    (WEB / "photo-credits.html").write_text('<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Nguồn ảnh cầu thủ</title><link rel="stylesheet" href="/style.css"><main><h1>Nguồn ảnh cầu thủ</h1><p>Ảnh được lưu cục bộ để hiển thị hồ sơ. Bản quyền thuộc chủ sở hữu nguồn; ảnh không xác nhận đăng ký hoặc hợp đồng ở mùa đang xem.</p><ul class="photo-credits">' + credits + '</ul><a href="/#players">Quay lại cầu thủ</a></main></html>', "utf-8")
    print(f"Downloaded {len(records)}/{len(players)} verified player portraits.")


if __name__ == "__main__":
    main()
