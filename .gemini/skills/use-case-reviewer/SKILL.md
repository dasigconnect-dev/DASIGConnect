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
- **Crucial Action 1 (Sync Use Case Doc):** Automatically update or create the relevant `docs/md/<use-case-name>.md` file using the **Dual-Version Format**.
  - **Top Version (Business/Product View):** This section MUST use the following exact structure for the use case text (the "actual document"), representing the formal product requirements:
    ```
    Use Case ID
    [ID]
    Use Case Name
    [Name]
    Actor(s)
    [Actors]
    Precondition(s)
    [Preconditions]
    Main Flow
    [Main flow steps. This MUST reflect how the users actually interact with the system and how the use case naturally flows from a user's perspective, avoiding overly technical internal details.]
    Alternative Flow(s)
    [Alternative flows]
    Postcondition(s)
    [Postconditions]
    ```
  - **Separator:** Add a separator line `=========================================`
  - **Bottom Version (Developer/Technical View):** This section is strictly for developers to take note of what changed, how the codebase currently reflects the requirements, and any technical details related to the use case development. It MUST include a `## Implementation Status` section detailing exactly what is ✅ Implemented, ❌ Not Implemented, and ⚠️ Discrepancies, along with any relevant technical notes. Both the top and bottom versions should be updated every time there's a change if necessary.
- **Crucial Action 2 (Sync SRS):** After updating the use case document, check for and update the `docs/md/srs.md` (Software Requirements Specification) file if applicable, ensuring that global feature lists, system constraints, and requirements accurately reflect the newly verified implementation status.

## Guiding Principles
- **Code is Truth:** If the use case text and the code conflict, point it out. Do not assume the text is right and the code is wrong without asking the user—it might be an intentional design pivot.
- **Thoroughness:** Do not just check file names. Look at the actual properties of DTOs, database schemas, and service method signatures.
- **Traceability:** Link to specific lines of code in your report to back up your claims about what is or isn't implemented.

