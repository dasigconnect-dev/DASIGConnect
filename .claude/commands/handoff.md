You are wrapping up a work session for the DASIGConnect capstone project. Your job is to write a full handoff, update the project docs, then commit and push everything so the next session starts clean.

Follow these steps **in order**. Do not skip any step.

---

## Step 1 — Gather session context

Run these in parallel:
- `git status` — see all modified/untracked files
- `git diff HEAD` — see the full diff of this session's work
- `git log --oneline -8` — see recent commits for context
- Read `TASKS.md` — know what was in-progress or pending

---

## Step 2 — Write / overwrite `HANDOFF.md`

Write `HANDOFF.md` in the **project root** (`C:\Users\Dan\Documents\Academic Files\3rd Year - 2nd Sem\Laviste\DASIGConnect\HANDOFF.md`) using this exact structure:

```
# Handoff — <YYYY-MM-DD> (Session N)

## What was done this session
- bullet points — be specific (component names, endpoint paths, migration numbers)

## Files changed
- `path/to/file` — one-line reason

## What's next
1. ordered list of the immediate next steps

## Blockers / notes
- anything the next session must know before writing code
```

---

## Step 3 — Update `TASKS.md`

Open `TASKS.md` and update it to reflect this session's work:
- Mark newly completed items as `Done:`
- Mark items now in progress as `In Progress:`
- Add any new tasks discovered this session under the correct module section
- Do NOT delete or reorder existing entries — only update status and append new ones

---

## Step 4 — Update `CLAUDE.md`

Open `CLAUDE.md` and update **only** the following sections — do not touch architecture, commands, or domain model sections:

1. **Current Local Status — date line** — update the date to today
2. **`module3` row in the Development Modules table** — append a one-sentence summary of what was added this session to the existing Notes cell
3. **Completed section** — append bullet points for everything finished this session (match the style of existing entries: feature name + short what-was-done summary)
4. **Known Gaps section** — remove any items that are now done; add any new gaps discovered

---

## Step 5 — Stage, commit, and push

Run the following **sequentially**:

1. Stage the doc files:
   ```
   git add HANDOFF.md TASKS.md CLAUDE.md
   ```

2. Stage ALL other modified tracked files from this session (use `git status` output to identify them — do not use `git add -A` which could include `.env` or build artifacts):
   - Stage only files that appear under "modified" or "new file" in `git status`
   - Never stage: `.env`, `target/`, `dist/`, `node_modules/`, `*.class`, `*.jar`

3. Commit with a message in this format:
   ```
   chore: session handoff YYYY-MM-DD

   - <one-line summary of the main feature/fix done>
   - Updated HANDOFF.md, TASKS.md, CLAUDE.md

   Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
   ```

4. Push:
   ```
   git push
   ```

5. Confirm the push succeeded by checking the output. If it fails (e.g. no upstream), run:
   ```
   git push --set-upstream origin <current-branch>
   ```

---

## Step 6 — Spoken summary

After the push succeeds, give the user a **3–4 sentence** spoken summary:
- What was completed this session
- What the next session should start with
- Confirm the push was successful and which branch was updated

Do not ask for confirmation before committing — just do it. Only pause if `git status` shows unexpected files (`.env`, secrets, large binaries) that should not be committed.
