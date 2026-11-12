#!/usr/bin/env python3
"""
Check that the documentation still matches the code. Run from anywhere:

    python3 scripts/check-docs.py

It fails (exit code 1) when
  1. a REST endpoint in a Spring controller is missing from docs/API.md,
  2. an environment variable read by the backend (application.yml) or the frontend (import.meta.env.VITE_*)
     is not mentioned in any document,
  3. a relative link or image in README.md, CHANGELOG.md or docs/*.md points at a file or heading that
     doesn't exist,
  4. an image in docs/images is not used by any document,
  5. an API error code in ErrorCode.java has no translation in one of the locale files.

Only the standard library is used, so CI needs nothing extra.
"""
import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
JAVA = ROOT / "backend/src/main/java"
problems: list[str] = []


def doc_files() -> list[Path]:
    return [ROOT / "README.md", ROOT / "CHANGELOG.md", *sorted(DOCS.glob("*.md"))]


def norm_path(p: str) -> str:
    """'/api/users/{username}/stats?x=1' -> '/api/users/{}/stats'"""
    p = p.split("?")[0].strip()
    p = re.sub(r"\{[^}]*\}", "{}", p)
    return p.rstrip("/") or "/"


# ---------------------------------------------------------------- 1. endpoints

MAPPING = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping\s*(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?\"([^\"]*)\"[^)]*\)|\(\s*\)|)")


def endpoints() -> list[tuple[str, str, str]]:
    """(METHOD, full path, file) for every handler method."""
    found = []
    for f in JAVA.rglob("*Controller.java"):
        src = f.read_text(encoding="utf-8")
        class_prefix = ""
        m = re.search(r"@RequestMapping\(\s*\"([^\"]*)\"\s*\)\s*(?:@\w+(?:\([^)]*\))?\s*)*public\s+class", src)
        if m:
            class_prefix = m.group(1)
        for mm in MAPPING.finditer(src):
            verb, path = mm.group(1), mm.group(2) or ""
            if verb == "Request":
                continue  # class-level prefix
            found.append((verb.upper(), norm_path(class_prefix + path), f.name))
    return found


def code_paths(text: str) -> list[str]:
    """Paths inside `code spans`, including ones like `POST /api/ws/ticket`."""
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    return [p for line in text.splitlines()
            for span in re.findall(r"`([^`]+)`", line)
            for p in re.findall(r"(/[^\s`]*)", span)]


def check_endpoints() -> int:
    api = (DOCS / "API.md").read_text(encoding="utf-8")
    tokens = {norm_path(t) for t in code_paths(api)}
    # Section headings like  "## Users: `/api/users`"  give the prefix for relative paths in their table.
    sections: list[tuple[str, set[str]]] = []
    current_prefix, current = "", set()
    for line in api.splitlines():
        h = re.match(r"^#{2,3} .*`(/api[^`]*)`", line)
        if h:
            sections.append((current_prefix, current))
            current_prefix, current = norm_path(h.group(1)), set()
        current.update(norm_path(t) for t in code_paths(line))
    sections.append((current_prefix, current))

    eps = endpoints()
    for verb, path, file in eps:
        if path in tokens:
            continue
        ok = False
        for prefix, toks in sections:
            if prefix and path.startswith(prefix):
                rel = norm_path(path[len(prefix):] or "/")
                if rel in toks:
                    ok = True
                    break
        if not ok:
            problems.append(f"API.md: {verb} {path} ({file}) is not documented")
    return len(eps)


# ---------------------------------------------------------------- 2. environment variables

def check_env() -> int:
    names = set()
    for yml in (ROOT / "backend/src/main/resources").glob("application*.yml"):
        names.update(re.findall(r"\$\{([A-Z][A-Z0-9_]+)", yml.read_text(encoding="utf-8")))
    for f in (ROOT / "frontend/src").rglob("*.ts*"):
        names.update(re.findall(r"import\.meta\.env\.(VITE_[A-Z0-9_]+)", f.read_text(encoding="utf-8")))
    all_docs = "\n".join(p.read_text(encoding="utf-8") for p in doc_files())
    for n in sorted(names):
        if n not in all_docs:
            problems.append(f"docs: environment variable {n} is never mentioned")
    return len(names)


# ---------------------------------------------------------------- 3 + 4. links and images

def slug(heading: str) -> str:
    """GitHub-style anchor for a heading."""
    h = re.sub(r"[`*_~]|<[^>]+>", "", heading).strip().lower()
    h = unicodedata.normalize("NFC", h)
    h = "".join(ch for ch in h if ch.isalnum() or ch in " -")
    return h.replace(" ", "-")


def anchors(md: Path) -> set[str]:
    out, seen = set(), {}
    in_code = False
    for line in md.read_text(encoding="utf-8").splitlines():
        if line.startswith("```"):
            in_code = not in_code
        if in_code:
            continue
        m = re.match(r"^#{1,6}\s+(.*)$", line)
        if m:
            s = slug(m.group(1))
            n = seen.get(s, 0)
            out.add(s if n == 0 else f"{s}-{n}")
            seen[s] = n + 1
    return out


LINK = re.compile(r"!?\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)|<img[^>]+src=\"([^\"]+)\"")


def check_links() -> int:
    used_images: set[Path] = set()
    count = 0
    for md in doc_files():
        text = md.read_text(encoding="utf-8")
        text = re.sub(r"```.*?```", "", text, flags=re.S)  # ignore code blocks
        for m in LINK.finditer(text):
            target = m.group(1) or m.group(2)
            if re.match(r"^(https?:|mailto:|computer:)", target):
                continue
            count += 1
            path, _, frag = target.partition("#")
            dest = md if path == "" else (md.parent / path).resolve()
            if not dest.exists():
                problems.append(f"{md.relative_to(ROOT)}: broken link {target}")
                continue
            if dest.suffix.lower() in (".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp"):
                used_images.add(dest)
            if frag and dest.suffix == ".md" and frag not in anchors(dest):
                problems.append(f"{md.relative_to(ROOT)}: no heading #{frag} in {dest.relative_to(ROOT)}")
    for img in sorted((DOCS / "images").glob("*")):
        if img.resolve() not in used_images:
            problems.append(f"docs/images/{img.name} is not used by any document")
    return count


# ---------------------------------------------------------------- 5. error codes

def check_error_codes() -> int:
    src = next(JAVA.rglob("ErrorCode.java")).read_text(encoding="utf-8")
    start = src.index("{", src.index("enum ErrorCode")) + 1
    body = src[start: src.index(";", start)]
    codes = re.findall(r"^\s*([A-Z][A-Z0-9_]+)\s*\(", body, flags=re.M)
    for lang in ("nb", "en"):
        errors = json.loads((ROOT / f"frontend/src/i18n/locales/{lang}.json").read_text(encoding="utf-8")).get("errors", {})
        for c in codes:
            if c not in errors:
                problems.append(f"{lang}.json: no translation for error code errors.{c}")
    return len(codes)


def main() -> int:
    n_ep = check_endpoints()
    n_env = check_env()
    n_links = check_links()
    n_codes = check_error_codes()
    if problems:
        print(f"docs check failed ({len(problems)} problem(s)):")
        for p in problems:
            print("  -", p)
        return 1
    print(f"docs OK – {n_ep} endpoints, {n_env} environment variables, {n_links} links and images, {n_codes} error codes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
