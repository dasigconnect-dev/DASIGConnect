You are wrapping up a work session and need to leave a clear handoff for whoever (or whatever Claude session) picks this up next.

Do the following steps in order:

1. Run `git status` and `git diff HEAD` to review all changes in this session.
2. Run `git log --oneline -5` to see recent commits.
3. Check the current task list for any in-progress or pending items.
4. Review project memory for relevant context.

Then create or overwrite `HANDOFF.md` in the **project root** (the directory Claude Code is currently running in) with this structure:

```
# Handoff — <date>

## What was done this session
- bullet points of completed work

## Files changed
- list key files that were modified and why

## What's next
- ordered list of pending tasks or next steps

## Blockers / notes
- anything the next person needs to know before diving in
```

After writing the file, give the user a 2-3 sentence spoken summary of the handoff so they can confirm it's accurate before closing the session.
