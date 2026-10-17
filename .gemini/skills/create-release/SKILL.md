---
name: Production Release Manager
description: Cuts clean, production-sanitized release branches from dev, increments semantic release versions, generates root RELEASE_NOTES.md adhering to Keep a Changelog standards, and formats release notes comments for GitHub PRs and releases.
---

# Production Release Manager Skill

This skill standardizes the workflow for preparing and cutting clean, production-ready release branches from `dev` into `main`.

You can trigger this skill by asking:
- *"Create a new release for prod"*
- *"Prepare the next release branch"*
- *"Generate release notes and cut release"*

---

## Release Principles & Clean Production Rules

1. **Strictly Clean Production Tree:**
   The production branch (`main`) and release branches (`release/vX.Y.Z`) must contain **only** production assets:
   - **MUST REMOVE:**
     - Agent directories: `.agent/`, `.claude/`, `.gemini/`
     - Internal markdown drafts: `docs/md/`, `docs/img/`, `docs/README.md`
     - Root developer documentation: `CLAUDE.md`, `DEPLOY.md`
     - Offline evaluations: `backend/evaluation/`
     - Scratch/temporary logs: `tmp/`, `outputs/`
   - **MUST RETAIN ONLY:**
     - Production source code: `backend/`, `frontend/`
     - Official PDF deliverables: `docs/pdf/`
     - Repository root metadata: `README.md`, `RELEASE_NOTES.md`, `docker-compose.yml`, `.gitignore`
2. **Incrementing Version Numbers:**
   - Every release gets a dedicated incrementing branch: `release/v<major>.<minor>.<patch>` (e.g., `release/v3.0.2` following `3.0.1`).
   - Semantic versioning applies: Patch for bug fixes/tests/refactors, Minor for new features, Major for breaking architecture shifts.
3. **Standardized Release Notes (Keep a Changelog):**
   - Release notes adhere to the **[Keep a Changelog](https://keepachangelog.com/)** and **[SemVer](https://semver.org/)** standards.
   - Categorized strictly under: `Security`, `Fixed`, `Changed`, `Removed`, and `Added`.
   - Placed at the repository root: `/RELEASE_NOTES.md`.
   - Also provided as a formatted markdown block for pasting as a comment on the GitHub PR or under GitHub Releases.

---

## Step-by-Step Release Workflow

### Step 1: Discover Diff and Increment Version
1. Ensure `dev` is up to date:
   ```bash
   git checkout dev
   git pull origin dev
   ```
2. Inspect all commits merged into `dev` since `main`:
   ```bash
   git log origin/main..dev --oneline
   ```
3. Determine the target release version by incrementing the current version (e.g., `3.0.1` -> `3.0.2`).

### Step 2: Create the Release Branch
Create a new branch from `dev`:
```bash
git checkout -b release/v<version>
```

### Step 3: Generate Root `RELEASE_NOTES.md`
Write `/RELEASE_NOTES.md` at the repository root adhering to Keep a Changelog:
- **Header:** `[Version] - YYYY-MM-DD`
- **Sections:**
  - `Security`: Vulnerability mitigations (e.g. CWE fixes, input sanitization).
  - `Fixed`: Bug and defect fixes aligned with test incidents (e.g. STD incidents).
  - `Changed`: Functional updates, UI refactorings, and configuration shifts.
  - `Removed`: Obsolete developer tools, stale demo code, or deprecated endpoints.
  - `Added`: New features, test suites, or documentation syncs.
- **Pull Requests Table:** PR numbers, hashes, scopes, descriptions, and authors.

### Step 4: Sanitize the Release Branch for Production
Strip all non-production files:
```bash
git rm -rf docs/md docs/img docs/README.md .agent .claude .gemini CLAUDE.md DEPLOY.md backend/evaluation
```
*Verify that only `backend/`, `frontend/`, `docs/pdf/`, `README.md`, `RELEASE_NOTES.md`, `docker-compose.yml`, and `.gitignore` remain.*

Commit the release preparation following Conventional Commits:
```bash
git add RELEASE_NOTES.md
git commit -m "chore: prepare release v<version>"
```

### Step 5: Publish Branch & GitHub PR / Release Note Comments
1. Push the release branch to origin:
   ```bash
   git push origin release/v<version>
   ```
2. Provide the user with:
   - **PR Title:** `chore: prepare release v<version>`
   - **PR Base:** `main` <- **Compare:** `release/v<version>`
   - **GitHub Comment / Release Body:** Copy-paste markdown block to paste into the GitHub PR conversation or GitHub Releases tab.
