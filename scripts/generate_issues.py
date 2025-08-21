#!/usr/bin/env python3
"""
Generate GitHub issues from actionable items found in the docs/ folder.

Features:
- Scans Markdown for task checkboxes ("- [ ] ...") and TODO: lines
- Captures section heading context and a short code snippet
- Computes a stable fingerprint to avoid duplicates
- Creates issues via GitHub REST API, with labels optimized for agentic work
- Dry-run by default; requires GITHUB_TOKEN to create issues

Usage:
  python scripts/generate_issues.py --mode dry-run
  python scripts/generate_issues.py --mode create

Environment:
  GITHUB_TOKEN    Personal access token or Actions token (required for create)
  GITHUB_REPOSITORY  owner/repo (auto in Actions). Otherwise pass --repo.
  GITHUB_REF_NAME    Branch name for source links (auto in Actions). Defaults to main.

Optional:
  OPENAI_API_KEY   If set, the script will enrich issues with a suggested step list (not required).
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

try:
    # Prefer stdlib to avoid dependencies
    from urllib import request, parse, error
except Exception as e:  # pragma: no cover
    print(f"Failed to import urllib: {e}", file=sys.stderr)
    sys.exit(2)


DOCS_ROOT = Path(__file__).resolve().parents[1] / "docs"


TASK_RE = re.compile(r"^\s*[-*]\s+\[ \]\s+(?P<text>.+)$")
TODO_RE = re.compile(r"^\s*(?:TODO|ToDo|todo)[:\-\s]+(?P<text>.+)$")
HEADING_RE = re.compile(r"^(?P<hashes>#{1,6})\s+(?P<text>.+?)\s*$")


@dataclass
class Task:
    title: str
    file_path: Path
    line_no: int
    section_path: List[str]
    snippet: str
    fingerprint: str


def read_lines(p: Path) -> List[str]:
    try:
        return p.read_text(encoding="utf-8").splitlines()
    except UnicodeDecodeError:
        return p.read_text(encoding="utf-8", errors="ignore").splitlines()


def compute_fingerprint(repo: str, file_path: Path, line_no: int, text: str) -> str:
    key = f"{repo}:{file_path.as_posix()}:{line_no}:{text.strip().lower()}".encode("utf-8")
    return hashlib.sha1(key).hexdigest()


def capture_snippet(lines: List[str], idx: int, context: int = 3) -> str:
    start = max(0, idx - context)
    end = min(len(lines), idx + context + 1)
    snippet = "\n".join(lines[start:end])
    return snippet


def parse_markdown_tasks(md_path: Path, repo: str) -> List[Task]:
    lines = read_lines(md_path)
    section_stack: List[Tuple[int, str]] = []  # (level, text)
    tasks: List[Task] = []

    for i, line in enumerate(lines):
        # Maintain heading context
        m = HEADING_RE.match(line)
        if m:
            level = len(m.group("hashes"))
            text = m.group("text").strip()
            # Pop deeper/equal levels
            while section_stack and section_stack[-1][0] >= level:
                section_stack.pop()
            section_stack.append((level, text))
            continue

        # Match tasks
        t = TASK_RE.match(line) or TODO_RE.match(line)
        if t:
            text = t.group("text").strip()
            section_path = [s for _, s in section_stack]
            fingerprint = compute_fingerprint(repo, md_path.relative_to(DOCS_ROOT.parent), i + 1, text)
            snippet = capture_snippet(lines, i)
            tasks.append(
                Task(
                    title=text,
                    file_path=md_path.relative_to(DOCS_ROOT.parent),
                    line_no=i + 1,
                    section_path=section_path,
                    snippet=snippet,
                    fingerprint=fingerprint,
                )
            )
    return tasks


def scan_docs(repo: str) -> List[Task]:
    tasks: List[Task] = []
    if not DOCS_ROOT.exists():
        return tasks
    for p in DOCS_ROOT.rglob("*.md"):
        tasks.extend(parse_markdown_tasks(p, repo))
    return tasks


def gh_api_request(method: str, url: str, token: str, *, data: Optional[dict] = None, params: Optional[dict] = None) -> Tuple[int, dict, dict]:
    if params:
        qs = parse.urlencode(params)
        if qs:
            url = f"{url}?{qs}"
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "lorevault-docs-issue-generator",
    }
    body = None
    if data is not None:
        body = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = request.Request(url, data=body, headers=headers, method=method)
    try:
        with request.urlopen(req) as resp:
            status = resp.getcode()
            payload = resp.read().decode("utf-8")
            return status, json.loads(payload) if payload else {}, dict(resp.headers)
    except error.HTTPError as e:
        payload = e.read().decode("utf-8") if e.fp else ""
        try:
            j = json.loads(payload) if payload else {"message": e.reason}
        except Exception:
            j = {"message": payload or e.reason}
        return e.code, j, dict(getattr(e, "headers", {}))


def list_open_issues(repo: str, token: str) -> List[dict]:
    issues: List[dict] = []
    base = f"https://api.github.com/repos/{repo}/issues"
    page = 1
    while True:
        status, payload, _ = gh_api_request(
            "GET", base, token, params={"state": "open", "per_page": 100, "page": page}
        )
        if status != 200:
            print(f"Failed to list issues: {status} {payload}", file=sys.stderr)
            break
        batch = [i for i in payload if "pull_request" not in i]
        issues.extend(batch)
        if len(payload) < 100:
            break
        page += 1
    return issues


def make_issue_title(task: Task) -> str:
    prefix = "docs: "
    section = " / ".join(task.section_path[-3:]) if task.section_path else "docs"
    core = task.title.strip()
    title = f"{prefix}{core} [{section}]" if section else f"{prefix}{core}"
    return title[:240]


def area_labels_for_path(path: Path) -> List[str]:
    p = path.as_posix()
    if "/api/" in p:
        return ["area/api"]
    if "/data-model/" in p or "neo4j" in p:
        return ["area/graph"]
    if "/architecture/" in p:
        return ["area/docs"]
    if "/development/" in p:
        return ["area/agentic"]
    return ["area/docs"]


def build_issue_body(task: Task, repo: str, branch: str) -> str:
    file_url = f"https://github.com/{repo}/blob/{branch}/{task.file_path.as_posix()}#L{task.line_no}"
    section_display = " / ".join(task.section_path) if task.section_path else "docs"
    snippet = task.snippet
    # Guard code fences inside snippet
    if "```" in snippet:
        snippet = snippet.replace("```", "``\u200b`")
    body = f"""
<!-- fingerprint:{task.fingerprint} -->
Context source: {file_url}

Section: {section_display}

Goal
-----
{task.title}

Context snippet
---------------
```md
{snippet}
```

Acceptance criteria
-------------------
- [ ] Implement the change described above
- [ ] Update related docs if needed
- [ ] Add/adjust tests where applicable
- [ ] Lint/typecheck/tests pass in CI

Notes
-----
- Generated automatically from docs by scripts/generate_issues.py
- Please enrich with additional context or adjust labeling as needed
""".strip()
    return body


def enrich_with_ai_steps(body: str, api_key: Optional[str]) -> str:
    # Placeholder: do nothing if no key. We intentionally avoid making a network call here for safety.
    # This function is left as a hook for future enhancement.
    return body


def create_issue(repo: str, token: str, title: str, body: str, labels: List[str]) -> Optional[int]:
    url = f"https://api.github.com/repos/{repo}/issues"
    status, payload, _ = gh_api_request("POST", url, token, data={"title": title, "body": body, "labels": labels})
    if status in (200, 201):
        return payload.get("number")
    print(f"Failed to create issue: {status} {payload}", file=sys.stderr)
    return None


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Generate GitHub issues from docs")
    parser.add_argument("--mode", choices=["dry-run", "create"], default="dry-run")
    parser.add_argument("--repo", help="owner/repo; defaults to GITHUB_REPOSITORY")
    parser.add_argument("--branch", help="Branch for source links; defaults to GITHUB_REF_NAME or main")
    parser.add_argument("--limit", type=int, default=100, help="Max issues to create per run")
    args = parser.parse_args(argv)

    repo = args.repo or os.environ.get("GITHUB_REPOSITORY")
    if not repo:
        print("Missing --repo or GITHUB_REPOSITORY", file=sys.stderr)
        return 2
    branch = args.branch or os.environ.get("GITHUB_REF_NAME", "main")
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if args.mode == "create" and not token:
        print("GITHUB_TOKEN is required in create mode", file=sys.stderr)
        return 2

    tasks = scan_docs(repo)
    if not tasks:
        print("No actionable items found in docs/")
        return 0

    # Dedup using open issues fingerprints
    open_issues: List[dict] = []
    existing_fps: set[str] = set()
    if token:
        open_issues = list_open_issues(repo, token)
        for iss in open_issues:
            body = iss.get("body") or ""
            m = re.search(r"<!--\s*fingerprint:([0-9a-f]{40})\s*-->", body)
            if m:
                existing_fps.add(m.group(1))

    created = 0
    skipped = 0

    for task in tasks:
        if task.fingerprint in existing_fps:
            skipped += 1
            continue

        title = make_issue_title(task)
        labels = ["ai/task", "type/docs", "status/triage"] + area_labels_for_path(task.file_path)
        body = build_issue_body(task, repo, branch)
        body = enrich_with_ai_steps(body, os.environ.get("OPENAI_API_KEY"))

        if args.mode == "dry-run":
            print(f"DRY-RUN would create: '{title}' labels={labels} @ {task.file_path}:{task.line_no}")
            print(f"  fingerprint: {task.fingerprint}")
        else:
            issue_no = create_issue(repo, token, title, body, labels)
            if issue_no:
                created += 1
            if created >= args.limit:
                break

    print(f"Summary: tasks={len(tasks)} created={created} skipped={skipped} mode={args.mode}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
