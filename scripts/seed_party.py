#!/usr/bin/env python3
"""
Add 1.4.0 demo data to a LOCAL TrafonDart that was filled by seed_demo.py: party games (finished Around the Clock and
Killer, a Cricket game and a doubles X01 game in progress) and challenges (one to you, one accepted and ready).

    python3 scripts/seed_party.py --admin oleander --password 'your-bootstrap-password'

The demo friends log in with seed_demo.py's demo password. Refuses to run against anything that isn't localhost
unless you pass --i-know-what-im-doing.
"""
import argparse
import random
import sys
from urllib.parse import urlparse

try:
    import requests
except ImportError:  # pragma: no cover
    sys.exit("pip install requests")

from seed_demo import DEMO_PASSWORD  # noqa: E402  (same folder)


class Client:
    def __init__(self, base, username, password):
        self.base = base.rstrip("/")
        self.s = requests.Session()
        self.s.get(self.base + "/api/auth/csrf")
        r = self.post("/api/auth/login", {"username": username, "password": password})
        if r.status_code != 200:
            sys.exit(f"login as {username} failed: {r.status_code} {r.text}")
        self.me = r.json()

    def get(self, path):
        return self.s.get(self.base + path).json()

    def post(self, path, body=None):
        return self.s.post(self.base + path, json=body or {}, headers={"X-XSRF-TOKEN": self.s.cookies.get("XSRF-TOKEN", "")})


def throw(c, game, segment, multiplier):
    r = c.post(f"/api/games/{game['id']}/throws", {"segment": segment, "multiplier": multiplier, "expectedDartCount": game["dartCount"]})
    if r.status_code != 200:
        sys.exit(f"throw failed: {r.text}")
    return r.json()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--admin", required=True)
    ap.add_argument("--password", required=True)
    ap.add_argument("--i-know-what-im-doing", action="store_true")
    a = ap.parse_args()
    if urlparse(a.base).hostname not in ("localhost", "127.0.0.1") and not a.i_know_what_im_doing:
        sys.exit("refusing to seed a non-local server")

    rnd = random.Random(4)
    admin = Client(a.base, a.admin, a.password)
    me = admin.me["id"]
    friends = {u["username"]: u["id"] for u in admin.get("/api/users") if u["id"] != me}
    names = list(friends)[:4]
    if len(names) < 4:
        sys.exit("run seed_demo.py first")

    def game(type_, players, teams=False, settings=None):
        r = admin.post("/api/games", {"type": type_, "playerIds": players, "teams": teams, "settings": settings or {}})
        if r.status_code != 201:
            sys.exit(f"create {type_} failed: {r.text}")
        return r.json()

    g = game("AROUND_THE_CLOCK", [me])
    for n in list(range(1, 21)) + [25]:
        while True:
            hit = rnd.random() < 0.7
            g = throw(admin, g, n if hit else 0, 1 if hit else 0)
            if hit:
                break

    g = game("KILLER", [me, friends[names[0]], friends[names[1]]], settings={"lives": 2})
    while g["status"] == "IN_PROGRESS":
        cur = next(p for p in g["players"] if p["slot"] == g["currentSlot"])
        side = next(s for s in g["sides"] if s["side"] == cur["side"])
        target = side if not side["killer"] else rnd.choice([s for s in g["sides"] if s["side"] != cur["side"] and not s["out"]])
        g = throw(admin, g, target["number"], 2 if rnd.random() < 0.5 else 1)

    g = game("CRICKET", [me, friends[names[0]], friends[names[2]]])
    for _ in range(23):
        seg = rnd.choice([20, 19, 18, 17, 20, 20, 19, 25, 5, 1])
        g = throw(admin, g, seg, rnd.choice([1, 2]) if seg == 25 else rnd.choice([1, 1, 2, 3]))

    g = game("X01", [me, friends[names[1]], friends[names[2]], friends[names[3]]], teams=True,
             settings={"startScore": 301, "outRule": "DOUBLE", "legsToWin": 1})
    for _ in range(14):
        g = throw(admin, g, rnd.choice([20, 19, 18, 5, 1]), rnd.choice([1, 1, 3]))

    challenger = Client(a.base, names[0], DEMO_PASSWORD)
    challenger.post("/api/challenges", {"opponentId": me, "startScore": 501, "inRule": "STRAIGHT", "outRule": "DOUBLE",
                                        "legsToWin": 3, "rated": True, "message": "Revansj i kveld? Jeg har trent."})
    ch = admin.post("/api/challenges", {"opponentId": friends[names[1]], "startScore": 301, "inRule": "STRAIGHT",
                                        "outRule": "DOUBLE", "legsToWin": 2, "rated": True}).json()
    Client(a.base, names[1], DEMO_PASSWORD).post(f"/api/challenges/{ch['id']}/accept")
    print("Party games and challenges added.")


if __name__ == "__main__":
    main()
