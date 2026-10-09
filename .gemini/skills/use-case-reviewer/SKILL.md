---
name: Use Case Reviewer
description: Verifies use case descriptions against the codebase and docs to find discrepancies, implemented features, and missing features. Updates related markdown docs.
---

# Use Case Reviewer Skill

This skill provides a standardized workflow for verifying a given use case description against the existing codebase and documentation. It ensures that the documentation is kept up-to-date with the actual implementation reality.

## Workflow

When the user asks you to review a use case description, execute the following steps rigorously:

### 1. Analyze the Provided Use Case
- Read the use case description provided by the user.
- Identify the key entities, user roles, core actions, and expected system responses/outcomes.
- Formulate a list of keywords to search the codebase (e.g., entity names, endpoint paths, service methods).

### 2. Codebase Investigation
- Use codebase search tools to find related implementations.
- Specifically investigate:
  - **API Layer (Controllers/Routers):** Are the necessary endpoints defined? Do they accept the correct payloads?
  - **Business Logic (Services/Managers):** Is the core logic implemented? Are guardrails, validations, or specific rules from the use case enforced?
  - **Data Layer (Repositories/Schemas/Migrations):** Are the required database tables, columns, and relationships present?
  - **Tests:** Are there unit or integration tests verifying this exact use case?

### 3. Documentation Review
- Locate existing documentation in the `docs/` directory (e.g., `docs/use-cases/`).
- Read the existing markdown files to understand the previously documented state of this use case.

### 4. Verification & Gap Analysis
- Cross-reference the provided use case description, the codebase implementation, and the existing documentation.
- Categorize your findings into three distinct buckets:
  - ✅ **Implemented:** Aspects of the use case that are perfectly reflected in the codebase.
  - ❌ **Not Implemented:** Aspects described in the use case that are entirely missing from the codebase.
  - ⚠️ **Discrepancies:** Aspects that are implemented in the code, but behave differently from the use case description (e.g., different fields, different validation rules, alternative architectural choices).

### 5. Reporting and Syncing Documentation
- Present a clear, structured summary of the gap analysis to the user.
- **Crucial Action 1 (Sync Use Case Doc):** Automatically update or create the relevant `docs/md/<use-case-name>.md` file.
  - **IMPORTANT FORMATTING RULE:** The resulting document MUST have the exact same structure, headings, and format as the use case description provided by the user.
  - **IMPORTANT CONTENT RULE:** However, the *content* within that document must be rewritten to strictly reflect the **codebase reality**. If the user's text describes a feature that differs from the code, update the text to match what the code actually does.
  - Add a section called `## Implementation Status` at the bottom detailing the exact implemented features, pending features, and noted discrepancies.
- **Crucial Action 2 (Sync SRS):** After updating the use case document, check for and update the `docs/md/srs.md` (Software Requirements Specification) file if applicable, ensuring that global feature lists, system constraints, and requirements accurately reflect the newly verified implementation status.

## Guiding Principles
- **Code is Truth:** If the use case text and the code conflict, point it out. Do not assume the text is right and the code is wrong without asking the user—it might be an intentional design pivot.
- **Thoroughness:** Do not just check file names. Look at the actual properties of DTOs, database schemas, and service method signatures.
- **Traceability:** Link to specific lines of code in your report to back up your claims about what is or isn't implemented.

