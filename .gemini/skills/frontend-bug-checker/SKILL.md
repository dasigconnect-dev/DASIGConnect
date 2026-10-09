---
name: Frontend Bug Checker
description: Systematically inspects the React/TypeScript frontend for common UI bugs, state management issues, and UX flaws.
---

# Frontend Bug Checker Skill

This skill provides a structured methodology for identifying bugs, race conditions, and UX issues in a React/TypeScript frontend application.

## Scope
When asked to perform a frontend bug check, rigorously examine the React components, custom hooks, API integration layers, and global state management.

## Bug Checking Checklist

### 1. React State & Component Lifecycle
- **Stale Closures:** Verify that `useEffect` and `useCallback` dependency arrays contain all referenced variables to prevent stale closures.
- **Memory Leaks:** Look for missing cleanup functions in `useEffect` (e.g., event listeners, abort controllers, timers).
- **Race Conditions:** Verify that asynchronous API calls have proper cancellation logic (like `AbortController`) so that late-resolving promises don't overwrite newer state.

### 2. Payload Mismatches & API Contracts
- **DTO Mismatch:** Ensure the TypeScript interfaces precisely match the JSON returned by the backend.
- **Dueling Logic:** Check for situations where the frontend tries to compute something complex and sends both the raw data *and* the result to the backend, leading to contradictory state (e.g., sending an explicitly chosen `albumId` but also sending a generic `autoMatch` flag).

### 3. Error Handling & Edge Cases
- **Null & Undefined:** Ensure safe access to nested object properties using optional chaining (`?.`) and nullish coalescing (`??`).
- **Loading & Error States:** Verify the UI gracefully handles loading states (disabling buttons, showing spinners) and surfaces human-readable error messages when API calls fail.
- **Empty States:** Ensure grids and lists render sensible empty-state fallbacks instead of just blank screens.

### 4. Performance & Re-renders
- Look for deeply nested component re-renders that could be optimized with `useMemo` or `React.memo`.
- Verify that large lists use virtualization or pagination instead of rendering thousands of DOM nodes.

## Execution
1. Read the relevant React components and hook files.
2. Search for common anti-patterns (e.g., missing dependencies, unhandled async errors, direct DOM manipulation).
3. Produce a structured bug report mapping findings to **Critical**, **Major**, and **Minor** severity levels.
4. Recommend actionable code changes for every identified bug.

