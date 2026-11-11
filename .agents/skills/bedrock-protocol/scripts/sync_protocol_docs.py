#!/usr/bin/env python3
import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPOSITORY = "https://github.com/EndstoneMC/protocol-docs.git"
SKILL_DIR = Path(__file__).resolve().parent.parent
DOCS_DIR = SKILL_DIR / ".cache" / "protocol-docs"
DISPLAY_DIR = ".cache/protocol-docs"
RELEASE_LINE = re.compile(
    r"^- \*\*(Current Release|Minecraft Version|Network Version):\*\*\s*(.+)$"
)


def sanitize(message):
    return message.replace(str(SKILL_DIR), "<skill>")


def git(*args, cwd=DOCS_DIR):
    result = subprocess.run(
        ["git", "-C", str(cwd), *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        details = (result.stderr or result.stdout).strip()
        raise RuntimeError(sanitize(details) or f"git {args[0]} failed")
    return result.stdout.strip()


def display_path():
    """Cache path relative to the repository root, so log lines are unambiguous."""
    try:
        root = Path(git("rev-parse", "--show-toplevel", cwd=SKILL_DIR)).resolve()
        return str(DOCS_DIR.relative_to(root))
    except (RuntimeError, ValueError):
        return DISPLAY_DIR


def report_release(readme):
    found = False
    if readme.is_file():
        for line in readme.read_text(encoding="utf-8").splitlines():
            match = RELEASE_LINE.match(line)
            if match:
                found = True
                print(f"{match.group(1)}: {match.group(2)}")
    if not found:
        print(
            f"warning: {readme.name} reports no release metadata; "
            "verify the synced cache before trusting it",
            file=sys.stderr,
        )


def sync(ref):
    display = display_path()

    if DOCS_DIR.exists() and not DOCS_DIR.is_dir():
        raise RuntimeError(f"{display} exists and is not a directory")

    if (DOCS_DIR / ".git").is_dir():
        if git("status", "--porcelain"):
            raise RuntimeError(
                f"{display} has local changes; move them elsewhere before syncing"
            )
    else:
        if DOCS_DIR.exists() and any(DOCS_DIR.iterdir()):
            raise RuntimeError(
                f"{display} is not empty and is not a Git repository"
            )
        DOCS_DIR.mkdir(parents=True, exist_ok=True)
        git("init", "--quiet")
        git("remote", "add", "origin", REPOSITORY)

    if "origin" not in git("remote").splitlines():
        git("remote", "add", "origin", REPOSITORY)
    git("remote", "set-url", "origin", REPOSITORY)
    git("fetch", "--depth", "1", "origin", ref or "HEAD")
    git("checkout", "--detach", "FETCH_HEAD")

    commit = git("rev-parse", "HEAD")
    print(f"protocol-docs synced to {display}")
    print(f"Requested ref: {ref or 'upstream HEAD'}")
    print(f"Commit: {commit}")

    report_release(DOCS_DIR / "README.md")


def main():
    parser = argparse.ArgumentParser(
        description="Shallow-sync EndstoneMC protocol-docs into this skill's cache."
    )
    parser.add_argument(
        "--ref",
        help="branch, tag, or commit to sync; defaults to the upstream default branch",
    )
    args = parser.parse_args()

    if args.ref is not None and args.ref.startswith("-"):
        parser.error("--ref must not start with '-' (git would read it as an option)")

    if shutil.which("git") is None:
        parser.error("git is required but was not found on PATH")

    try:
        sync(args.ref)
    except (OSError, RuntimeError) as error:
        print(f"error: {sanitize(str(error))}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
