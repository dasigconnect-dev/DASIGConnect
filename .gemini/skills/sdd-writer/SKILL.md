---
name: sdd-writer
description: Use this skill to generate a System Design Document (SDD) for a specific use case, saving it to docs/md/sdd/uc<#>.
---

# SDD Writer Skill

This skill guides the agent to write a System Design Document (SDD) based on an existing Use Case document (from `docs/md/`) and the codebase.

## Objective

Generate a structured System Design Document for a specific Use Case (e.g., UC-1.1), documenting its user interface design, front-end components, back-end components, object-oriented design, and data design.

## Instructions

1. **Read the Source Use Case Document**: Read the Use Case markdown file (e.g., `docs/md/UC-1.1-administrator-account-management.md`) to understand the functional requirements, flow, and business logic.
2. **Review Existing SDD (If Applicable)**: If an existing SDD is provided by the user or already exists in `docs/md/sdd/`, read it first. Review and validate its contents against the current codebase. 
3. **Explore the Codebase**: Search the frontend and backend codebase to map the requirements to the actual React components, Spring Boot components (Controllers, Services, Repositories), and database schema / entities.
4. **Generate or Update the SDD**: Write (or update) a markdown file named `uc<#>-<name>.md` (e.g., `uc1.1-admin-management.md`) and save it to the `docs/md/sdd/` directory. (Create the directory if it does not exist).
5. **Follow the Exact Structure**: You MUST format the output exactly as demonstrated in the template below, regardless of whether you are creating a new SDD from scratch or updating an existing one.

## Format / Template

Below is an example of the required SDD format. Use this EXACT section numbering and structure, substituting `<UC>` and `<#>` with the appropriate use case identifier.

```markdown
UC <#> - <Use Case Name>
<UC>.<#>.1 User Interface Design
<Screen or Dialog Name>
<Description of the UI, actions, validations, and states>

<Screen or Dialog Name>
<Description of the UI, actions, validations, and states>

<UC>.<#>.2 Front-end Component(s)
<ComponentName>
<Description of what it does, endpoints called (e.g., GET /api/...), and what it renders>
React functional component / <Route description>

<ComponentName>
<Description of what it does>
React functional component

<UC>.<#>.3 Back-end Component(s)
<ControllerName>
<Description of endpoints handled>
Spring Boot REST Controller

<ServiceName>
<Description of business logic, validations, and rules enforced>
Spring Boot Service Component

<RepositoryName>
<Description of data access operations>
Spring Data JPA Repository

<UC>.<#>.4 Object-Oriented Component(s)
<Mermaid Class Diagram / Use Case Flow - PLACEHOLDER>

<UC>.<#>.5 Data Design
<Mermaid ERD - PLACEHOLDER>
```

### Guidelines for filling out sections:

- **X.X.1 User Interface Design**: Describe the layout, interactions, states (e.g., active, pending), and validations based on what the frontend actually implements.
- **X.X.2 Front-end Component(s)**: List the primary React components handling this use case. Specify the REST API endpoints they call and their type (e.g., "React functional component").
- **X.X.3 Back-end Component(s)**: List the primary Spring Boot classes (Controllers, Services, Repositories). Summarize their responsibilities and rules enforced.
- **X.X.4 Object-Oriented Component(s)**: Always insert `<Mermaid Class Diagram / Use Case Flow - PLACEHOLDER>`.
- **X.X.5 Data Design**: Always insert `<Mermaid ERD - PLACEHOLDER>`.
- If a section contains multiple items (e.g., multiple components), list them sequentially with a blank line between them, just like the template.
