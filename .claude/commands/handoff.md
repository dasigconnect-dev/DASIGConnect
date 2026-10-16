You are wrapping up a DASIGConnect work session. Record the work in Git and the pull request instead of creating local handoff or task-tracker files.

## 1. Inspect the session

Run:

- `git status --short`
- `git diff --check`
- `git diff --stat`
- `git log --oneline -8`

Do not stage `.env` files, credentials, generated outputs, temporary files, or unrelated user documents.

## 2. Verify the change

Run the checks relevant to the files changed:

- Backend: targeted tests, then the full Maven test suite when practical.
- Frontend: lint, production build, and relevant Playwright tests.
- Documents: render and visually inspect the final artifact when layout matters.

## 3. Commit and push

Stage only files that belong to the current task. Use a concise conventional commit message, push the feature branch, and create or update its pull request.

The pull request description is the handoff record. It must state:

- what changed;
- why it changed;
- files or subsystems affected;
- verification performed;
- known limitations or follow-up work.

## 4. Final report

Give the user the branch, commit, pull-request URL, verification results, and remaining work. Do not create or update `HANDOFF.md` or `TASKS.md`.
