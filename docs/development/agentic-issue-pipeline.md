# Agentic Issue Pipeline

This system automatically generates context-rich GitHub issues from actionable items in your documentation, optimized for AI agent consumption.

## How It Works

```
docs/*.md (TODO/checkboxes) → scripts/generate_issues.py → GitHub Issues → Auto-labeled → GitHub Project
```

### 1. **Source Detection**
The generator scans `docs/**/*.md` for:
- Task checkboxes: `- [ ] Implement feature X`  
- TODO markers: `TODO: Add validation logic`

### 2. **Issue Generation**
Each actionable item becomes a GitHub issue with:
- **Title**: `docs: <task> [<section>]`
- **Body**: Direct link to source line + context snippet + acceptance criteria
- **Labels**: `["ai/task", "type/docs", "status/triage", "area/*"]`
- **Fingerprint**: Embedded in body to prevent duplicates

### 3. **Auto-routing**
Issues with `ai/task` label automatically get added to your GitHub Project (if configured).

## Usage

### Manual (Local)

```bash
# Preview what would be created (safe)
python scripts/generate_issues.py --mode dry-run --repo chaixdev/lorevault-kb

# Create up to 25 issues
python scripts/generate_issues.py --mode create --repo chaixdev/lorevault-kb --limit 25
```

**Environment variables:**
- `GITHUB_TOKEN`: Personal access token (required for `--mode create`)
- `OPENAI_API_KEY`: Optional; enables AI-enhanced issue descriptions

### Via GitHub Actions

1. Go to **Actions** tab → **Generate Issues from Docs**
2. Click **Run workflow**
3. Choose:
   - **Mode**: `dry-run` (preview) or `create` (actual)
   - **Limit**: Max issues per run (default: 25)
   - **Branch**: For source links (default: main)

### Auto-add to Project

Issues labeled `ai/task` automatically appear in your GitHub Project board (if `PROJECT_URL` is configured).

## Configuration

### Repository Variables (Settings → Secrets and Variables → Actions)

- `PROJECT_URL`: Your GitHub Project v2 URL
  - Example: `https://github.com/users/chaixdev/projects/1`
  - **Optional**: Leave unset to skip auto-add to project

### Label Taxonomy

Edit `.github/labels.yml` to customize:

```yaml
# Areas (determines which part of system)
- name: area/api
- name: area/graph  
- name: area/agentic
- name: area/docs

# Status (workflow progression)
- name: status/triage    # New, needs refinement
- name: status/ready     # Ready for agent pickup
- name: status/in-progress

# AI-specific
- name: ai/task         # Optimized for agent execution
- name: ai/context      # Reference material
```

### Issue Deduplication

Each issue contains a hidden fingerprint based on:
- Repository name
- File path and line number  
- Task text (normalized)

The generator skips creating issues if the fingerprint already exists in open issues.

## Agent Integration

Issues labeled `ai/task` + `status/ready` are optimized for agent pickup:

- **Context links**: Direct GitHub URLs to relevant docs
- **Acceptance criteria**: Clear deliverables checklist
- **Area labels**: Help agents understand which part of the system
- **Section path**: Hierarchical context from doc headings

Agents can query for work: "Show me `ai/task` issues with `status/ready`"

## Example Generated Issue

**Title**: `docs: Generate Port TCK when creating new port interfaces [Testing Strategy / Developer Checklist]`

**Body**:
```markdown
Context source: https://github.com/chaixdev/lorevault-kb/blob/main/docs/rules/testing-strategy.md

Section: Testing Strategy / Test Development Guidelines / Developer Checklist

Goal
-----
Generate Port TCK when creating new port interfaces

Context snippet
---------------
\`\`\`md
### Developer Checklist

- [ ] Apply appropriate `@Tag` annotation
- [ ] Generate Port TCK when creating new port interfaces
- [ ] Ensure deterministic test data (no random values)
\`\`\`

Acceptance criteria
-------------------
- [ ] Implement the change described above
- [ ] Update related docs if needed  
- [ ] Add/adjust tests where applicable
- [ ] Lint/typecheck/tests pass in CI
```

**Labels**: `["ai/task", "type/docs", "status/triage", "area/agentic"]`

## Workflow Tips

### For Humans
1. Write TODOs/checkboxes in docs as you think of improvements
2. Run generator periodically to convert them into trackable issues
3. Triage issues: assign priority, move to `status/ready`
4. Review agent-completed work in PRs

### For Agents  
1. Query GitHub API for issues: `label:ai/task label:status/ready`
2. Read issue body for context links and acceptance criteria
3. Follow links to gather full context from docs
4. Implement changes and reference issue number in PR

### Managing Volume
- Use `--limit` parameter to control batch size
- Filter generated issues by area labels if working on specific components
- Mark completed TODOs in docs to avoid re-generation

## Troubleshooting

**No issues created**: Check `GITHUB_TOKEN` permissions (needs `repo` and `write:issues`)

**Duplicate issues**: The fingerprint system should prevent this. If you see duplicates, check if the source text changed slightly.

**Wrong area labels**: Adjust the path-based logic in `area_labels_for_path()` function in the script.

**Project auto-add not working**: Verify `PROJECT_URL` variable is set and the token has project write access.
