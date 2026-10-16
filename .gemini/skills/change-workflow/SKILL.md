---
name: Change Workflow
description: A standardized git and documentation workflow to execute whenever code changes are made. Handles branching, tests, docs, commits, and PR descriptions.
---

# Change Workflow Skill

Whenever we make changes to the codebase (e.g., implementing a feature, fixing a bug, refactoring), follow this standardized workflow to ensure high-quality, traceable, and well-tested contributions. 

You can trigger this by asking me to "run the change workflow" or "finalize these changes".

## Workflow Steps

### 1. Create a New Branch
- Always start by creating a new, descriptively named git branch from the current development branch (e.g., `feature/<feature-name>`, `fix/<bug-description>`).
- Run `git checkout -b <branch-name>`.

### 2. Update Related Test Functions
- Identify which parts of the codebase were modified.
- Create new unit or integration tests for new logic, or update existing test functions to reflect any changed behaviors or new edge cases.
- Run the test suite to ensure everything passes before proceeding.

### 3. Update Documentation (If Necessary)
- Review the changes to determine if any project documentation needs updating.
- Check and update the relevant files in the `docs/` folder:
  - **Use Cases:** Update `docs/md/srs/` use cases to match the actual implementation.
  - **SRS (Software Requirements Specification):** Update global feature lists or system constraints.
  - **SDD (Software Design Document):** Update architectural decisions, database schemas, or component diagrams.
  - **README.md:** Update if the setup instructions, environment variables, or core commands have changed.

### 4. Separate Commits
- Do not lump all changes into a single massive commit.
- Group logically related changes together and create separate, atomic commits for a clean git history. 
- Example: 
  - Commit 1: `feat: implement core logic for X`
  - Commit 2: `test: add unit tests for X`
  - Commit 3: `docs: update use case and SRS for X`

### 5. Generate Pull Request Details
- Once all changes are committed, generate a structured Pull Request summary and present it to the user in the chat so they can use it for their PR.
- The PR summary MUST include the following sections:
  - **What Changed:** A clear, concise summary of the business logic, UI updates, or bug fixes introduced.
  - **What Files Are Touched:** A bulleted list of the key files modified, added, or deleted (no need to list every single test file if there are too many, just group them).
  - **Breaking Changes:** Explicitly state `Yes` or `No`. If `Yes`, describe exactly what broke (e.g., API contract changes, database schema drops, removed features) and how to migrate.

