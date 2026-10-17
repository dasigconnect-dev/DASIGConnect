---
name: Production Release Manager
description: Creates clean, production-sanitized release branches from dev, increments semantic release versions, generates root RELEASE_NOTES.md, and prepares release notes comments for GitHub PRs and releases.
---

# Production Release Manager Skill

This skill standardizes the end-to-end workflow for cutting clean, production-ready release branches from `dev` into `main`.

You can trigger this skill by asking:
- *"Create a new release for prod"*
- *"Prepare the next release branch"*
- *"Generate release notes and cut release"*

---

## Release Principles & Constraints

1. **Clean Production Tree:**
   The production branch (`main`) and any production release branch (`release/vX.Y.Z`) must be **completely clean** and stripped of internal documentation drafts, agent configuration directories, and local evaluations:
   - **MUST REMOVE:** `docs/md/`, `.agent/`, `.claude/`, `.gemini/`, `backend/evaluation/`, and temporary scratch files.
   - **MUST RETAIN:** `backend/`, `frontend/`, `docs/pdf/`, `docs/img/`, `docs/README.md`, `README.md`, `DEPLOY.md`, `docker-compose.yml`, `.gitignore`, and the root `RELEASE_NOTES.md`.
2. **Incrementing Version Numbers:**
   - Every release gets a dedicated incrementing branch: `release/v<major>.<minor>.<patch>` (e.g., `release/v3.0.2` following `3.0.1`).
   - Semantic versioning applies: Patch for bug fixes/tests/refactors, Minor for new features, Major for breaking architecture shifts.
3. **Dual Release Notes Distribution:**
   - A markdown file at the repository root: `/RELEASE_NOTES.md`.
   - A GitHub-ready markdown comment block formatted for PRs and GitHub Releases.

---

## Step-by-Step Release Workflow

### Step 1: Discover Diff and Increment Version
1. Ensure your local `dev` branch is up-to-date:
   ```bash
   git checkout dev
   git pull origin dev
   ```
2. Inspect all commits merged into `dev` since `main`:
   ```bash
   git log origin/main..dev --oneline
   ```
3. Determine the target release version by incrementing the current version (e.g. `3.0.1` -> `3.0.2`).

### Step 2: Create the Release Branch
Create a new branch from `dev`:
```bash
git checkout -b release/v<version>
```

### Step 3: Generate Root `RELEASE_NOTES.md`
Generate `/RELEASE_NOTES.md` at the repository root containing:
- **Header:** Version, Release Date, Source Branch (`dev`), Target Branch (`main`), Team Code (`2627-sem1-it411-38`).
- **Executive Summary:** Overview of stabilization, security patches, test suites, and refactorings.
- **Key Highlights:**
  - 🛡️ Security & Authorization (e.g., CWE patches, RBAC guards).
  - 🧪 Backend Test Coverage & Quality (new controller tests, MockMvc suites).
  - ⚡ Frontend Production Polish (ESLint zero-warnings, dead demo code elimination).
  - 📋 Living Documentation & Incident Matrix updates.
- **Pull Request Table:** PR number, commit SHA, scope, and summary.
- **Pre-Deployment Checklist:** Migrations, environment configurations, and smoke test URLs.

### Step 4: Sanitize the Release Branch for Production
Strip all internal and agent directories so only production assets remain:
```bash
git rm -rf docs/md .agent .claude .gemini backend/evaluation
```
*Note: Ensure `docs/pdf/`, `docs/img/`, `docs/README.md`, and root `RELEASE_NOTES.md` remain intact.*

Commit the release preparation following Conventional Commits:
```bash
git add RELEASE_NOTES.md
git commit -m "release: v<version> production readiness"
```

### Step 5: Push and Prepare GitHub PR & Comment
1. Push the release branch to origin:
   ```bash
   git push origin release/v<version>
   ```
2. Provide the user with:
   - PR Title: `release: v<version> production readiness`
   - PR Base: `main` <- Compare: `release/v<version>`
   - Copy-paste markdown block to paste as a comment on the GitHub PR or under GitHub Releases.
