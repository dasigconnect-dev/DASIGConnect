You are picking up a previous work session. Orient yourself quickly and brief the user.

Do the following steps in order:

1. Check for a `HANDOFF.md` in the project root — read it fully if it exists.
2. Read project memory for any relevant context.
3. Run `git log --oneline -5` to see recent commits.
4. Run `git status` to see the current working tree state.

Then give the user a brief (under 10 lines) spoken recap:
- What was done last session (from HANDOFF.md or memory)
- Current git state (clean / uncommitted changes / etc.)
- The single most important next step

End with: "Ready to continue — want to pick up with [next step], or is there something else first?"

Do not start implementing anything until the user responds.
