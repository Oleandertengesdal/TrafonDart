#!/usr/bin/env python3
"""
Fill a LOCAL TrafonDart with demo players, matches and a tournament so you can see the UI with real data.

    python3 scripts/seed_demo.py --admin oleander --password 'your-bootstrap-password'

Players are simulated dart-by-dart through the real API (including the server's checkout hints), so every
statistic you see was produced by the same code path as a real match. Refuses to run against anything that
isn't localhost unless you pass --i-know-what-im-doing.
"""
import argparse
import random
import sys
from urllib.parse import urlparse

try:
    import requests
except ImportError:  # pragma: no cover
    sys.exit("pip install requests")

FRIENDS = [
    ("kari", "Kari Nordmann", 0.42),
    ("ola", "Ola Hansen", 0.30),
    ("ingrid", "Ingrid Bakke", 0.36),
    ("sindre", "Sindre Lie", 0.22),
    ("aasmund", "Åsmund Ødegård", 0.27),
]
DEMO_PASSWORD = "demo-password-123"


class Client:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.s = requests.Session()
        self.s.get(self.base + "/api/auth/csrf")

    def _headers(self):
        return {"X-XSRF-TOKEN": self.s.cookies.get("XSRF-TOKEN", "")}

    def get(self, path):
        r = self.s.get(self.base + path)
        r.raise_for_status()
        return r.json()

    def put(self, path):
        r = self.s.put(self.base + path, headers=self._headers())
        r.raise_for_status()
        return r.json() if r.text else None

    def post(self, path, body=None, ok=(200, 201, 204)):
        r = self.s.post(self.base + path, json=body or {}, headers=self._headers())
        if r.status_code not in ok:
            raise RuntimeError(f"POST {path} -> {r.status_code} {r.text}")
        return r.json() if r.text else None


def aim(state, skill):
    """Pick a target like a human would, then 'throw' with some scatter."""
    me = state["currentPlayerIndex"]
    remaining = state["remaining"][me]
    route = state.get("checkout")
    target = route[0] if route else ("T20" if remaining > 60 else None)
    if target is None:
        target = f"S{min(remaining - 2, 20)}" if remaining > 2 else "D1"
    return scatter(target, skill)


NEIGHBOURS = {20: (1, 5), 19: (7, 3), 18: (4, 1), 17: (2, 3), 16: (8, 7), 15: (10, 2), 14: (9, 11), 13: (6, 4),
              12: (5, 9), 11: (14, 8), 10: (6, 15), 9: (12, 14), 8: (11, 16), 7: (16, 19), 6: (13, 10), 5: (20, 12),
              4: (18, 13), 3: (19, 17), 2: (17, 15), 1: (20, 18)}


def scatter(target, skill):
    if target == "BULL":
        r = random.random()
        return (25, 2) if r < skill * 0.5 else (25, 1) if r < skill else (random.randint(1, 20), 1)
    if target == "25":
        return (25, 1) if random.random() < skill * 1.3 else (random.randint(1, 20), 1)
    kind, n = target[0], int(target[1:])
    r = random.random()
    if kind == "S":
        if r < 0.85:
            return (n, 1)
        return (random.choice(NEIGHBOURS[n]), 1)
    if kind == "T":
        if r < skill:
            return (n, 3)
        if r < 0.9:
            return (n, 1)
        return (random.choice(NEIGHBOURS[n]), random.choice((1, 1, 3)))
    # double
    if r < skill * 0.9:
        return (n, 2)
    if r < 0.75:
        return (n, 1)
    return (0, 0)


def play(client, match_id, skills):
    state = client.get(f"/api/matches/{match_id}")
    while state["status"] == "IN_PROGRESS":
        seg, mult = aim(state, skills[state["currentPlayerIndex"]])
        state = client.post(f"/api/matches/{match_id}/throws",
                            {"segment": seg, "multiplier": mult, "expectedDartCount": state["dartCount"]})
    return state


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--admin", required=True)
    ap.add_argument("--password", required=True)
    ap.add_argument("--matches", type=int, default=14)
    ap.add_argument("--i-know-what-im-doing", action="store_true")
    a = ap.parse_args()
    if urlparse(a.base).hostname not in ("localhost", "127.0.0.1") and not a.i_know_what_im_doing:
        sys.exit("Refusing to seed demo data into a non-local server.")

    random.seed(7)
    admin = Client(a.base)
    me = admin.post("/api/auth/login", {"username": a.admin, "password": a.password})
    code = admin.post("/api/admin/invites", {"maxUses": len(FRIENDS), "validHours": 24, "note": "Demo data"})["code"]

    players = {me["username"]: (admin, me["id"], 0.33)}
    for username, real, skill in FRIENDS:
        c = Client(a.base)
        try:
            u = c.post("/api/auth/register", {"inviteCode": code, "username": username, "realName": real,
                                              "password": DEMO_PASSWORD, "preferredLocale": "nb"})
        except RuntimeError:
            u = c.post("/api/auth/login", {"username": username, "password": DEMO_PASSWORD})
        players[username] = (c, u["id"], skill)
    names = list(players)

    for i in range(a.matches):
        p1, p2 = random.sample(names, 2)
        c1, id1, s1 = players[p1]
        _, id2, s2 = players[p2]
        m = c1.post("/api/matches", {"player1Id": id1, "player2Id": id2, "startScore": random.choice((301, 501, 501)),
                                     "inRule": "STRAIGHT", "outRule": "DOUBLE", "legsToWin": random.choice((1, 2, 2, 3)),
                                     "startingPlayer": random.randint(0, 1), "rated": True})
        final = play(c1, m["id"], (s1, s2))
        w = final["players"][final["winnerIndex"]]["username"]
        print(f"match {i + 1:2d}: {p1} vs {p2} -> {w} ({final['legsWon'][0]}-{final['legsWon'][1]})")

    # Some banter on the latest matches.
    recent = admin.get("/api/matches/recent?limit=4")
    lines = ["Rått kast! 🎯", "Den utgangen der var syk.", "Revansj neste fredag?", "Snittet ditt er på vei opp!"]
    for i, m in enumerate(recent):
        players_in = {p["username"] for p in m["players"]}
        outsiders = [n for n in names if n not in players_in]
        fan = players[outsiders[i % len(outsiders)]][0]
        fan.put(f"/api/matches/{m['id']}/kudos")
        social = fan.post(f"/api/matches/{m['id']}/comments", {"body": lines[i % len(lines)]})
        winner = players[m["players"][m["winnerIndex"]]["username"]][0]
        winner.post(f"/api/matches/{m['id']}/comments", {"body": "Takk takk!", "parentId": social["comments"][0]["id"]})
        winner.put(f"/api/comments/{social['comments'][0]['id']}/like")

    # A knockout cup that is half played, and one live match to watch.
    ids = [players[n][1] for n in names]
    t = admin.post("/api/tournaments", {"name": "Trafon Cup", "format": "SINGLE_ELIMINATION", "startScore": 501,
                                        "inRule": "STRAIGHT", "outRule": "DOUBLE", "legsToWin": 2, "rated": True,
                                        "participantIds": ids})
    tid = t["summary"]["id"]
    detail = admin.post(f"/api/tournaments/{tid}/start")
    ready = [f for f in detail["fixtures"] if f["status"] == "READY"]
    for f in ready[:1]:
        mid = admin.post(f"/api/tournaments/{tid}/fixtures/{f['id']}/start")["matchId"]
        play(admin, mid, (0.35, 0.3))
    live = admin.post("/api/matches", {"player1Id": players["kari"][1], "player2Id": players["ola"][1],
                                       "startScore": 501, "inRule": "STRAIGHT", "outRule": "DOUBLE", "legsToWin": 3,
                                       "startingPlayer": 0, "rated": False})
    state = admin.get(f"/api/matches/{live['id']}")
    for _ in range(14):
        seg, mult = aim(state, 0.4)
        state = admin.post(f"/api/matches/{live['id']}/throws",
                           {"segment": seg, "multiplier": mult, "expectedDartCount": state["dartCount"]})
    print(f"live match: /matches/{live['id']}")
    print(f"tournament: /tournaments/{tid}")
    print(f"demo players log in with password '{DEMO_PASSWORD}'")


if __name__ == "__main__":
    main()
