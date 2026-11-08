#!/usr/bin/env python3
"""Check local documentation links, translations and executable setup references."""

import re
import subprocess
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parent.parent
GUIDES = (
    "getting-started", "configuration", "media-library", "docker", "themes",
    "development", "architecture", "troubleshooting", "maintainers", "verification",
)


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def prose(text):
    return re.sub(r"^```.*?^```\s*$", "", text, flags=re.MULTILINE | re.DOTALL)


def anchors(text):
    result = set()
    counts = {}
    for heading in re.findall(r"^#{1,6}\s+(.+?)\s*#*\s*$", prose(text), re.MULTILINE):
        slug = re.sub(r"[^\w\- ]", "", heading.replace("`", "").lower()).replace(" ", "-")
        count = counts.get(slug, 0)
        counts[slug] = count + 1
        result.add(slug if count == 0 else f"{slug}-{count}")
    return result


markdown = sorted({*ROOT.glob("*.md"), *ROOT.glob("docs/**/*.md"),
                   *ROOT.glob(".github/*.md"),
                   ROOT / "src/main/resources/static/themes/README.md"})
links = 0
for document in markdown:
    require(document.resolve().is_relative_to(ROOT), f"Document outside repository: {document}")
    text = document.read_text(encoding="utf-8")
    require(not re.search(r"/(?:Users|home)/[^\s/]+/|\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}:18081\b", text),
            f"Private host details in {document.relative_to(ROOT)}")
    require(not re.search(r"(?:\./mvnw|\bmvn\s+(?:verify|package|test|clean)|target/moviesites\.jar)", text),
            f"Obsolete active build command in {document.relative_to(ROOT)}")
    for target in re.findall(r"!?\[[^\]\n]*\]\(([^\s)]+)\)", prose(text)):
        parsed = urlsplit(target.strip("<>"))
        if parsed.scheme or parsed.netloc:
            continue
        destination = (document.parent / unquote(parsed.path)).resolve() if parsed.path else document
        require(destination.is_relative_to(ROOT) and destination.exists(),
                f"Broken local link in {document.relative_to(ROOT)}: {target}")
        if parsed.fragment and destination.suffix == ".md":
            require(unquote(parsed.fragment) in anchors(destination.read_text(encoding="utf-8")),
                    f"Broken heading link in {document.relative_to(ROOT)}: {target}")
        links += 1

for locale, readme_name in (("", "README.md"), ("ko/", "README.ko.md")):
    readme = (ROOT / readme_name).read_text(encoding="utf-8")
    for guide in GUIDES:
        path = f"docs/{locale}{guide}.md"
        require((ROOT / path).is_file() and f"]({path})" in readme,
                f"Missing guide/index entry: {path}")
require(not (ROOT / "docs/README.md").exists(), "Use the root README as the document index")

settings = set(re.findall(r"\$\{([A-Z][A-Z0-9_]*):", (ROOT / "src/main/resources/application.yml").read_text()))
for locale in ("", "ko/"):
    configuration = (ROOT / f"docs/{locale}configuration.md").read_text(encoding="utf-8")
    documented = set(re.findall(r"^\| `([A-Z][A-Z0-9_]*)` \|", configuration, re.MULTILINE))
    require(documented == settings, f"Configuration table differs from application settings: {locale or 'en'}")
example_keys = set(re.findall(r"^([A-Z][A-Z0-9_]*)=", (ROOT / ".env.example").read_text(), re.MULTILINE))
require(example_keys <= settings and {"MOVIESITES_MEDIA_ROOT", "MOVIESITES_DATA_ROOT", "MOVIESITES_THEME"} <= example_keys,
        "Environment example has missing or unknown settings")
ignored = subprocess.run(["git", "check-ignore", "-q", ".env.example"], cwd=ROOT, check=False)
require(ignored.returncode == 1, "Public .env.example must not be ignored")

for name in ("build.gradle.kts", "settings.gradle.kts", "gradlew", "gradlew.bat",
             "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties"):
    require((ROOT / name).is_file(), f"Missing standard build file: {name}")
for name in ("pom.xml", "mvnw", ".mvn"):
    require(not (ROOT / name).exists(), f"Obsolete build entry point: {name}")
properties = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
require(re.search(r"^distributionSha256Sum=[a-f0-9]{64}$", properties, re.MULTILINE),
        "Gradle distribution must have a pinned SHA-256 checksum")
require("validateDistributionUrl=true" in properties, "Gradle distribution URL validation must remain enabled")
print(f"Verified {len(markdown)} Markdown files, {links} local links, guide translations, settings and Gradle references.")
