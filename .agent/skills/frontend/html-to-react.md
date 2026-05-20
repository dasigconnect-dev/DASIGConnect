---
name: html-to-react
description: "Converts static HTML mockup/prototype into a production-ready Vite + React application. Actions: convert, transform, migrate, wire, connect, refactor, and rebuild HTML to React. Triggers: HTML file, mockup, prototype, static page, convert to React, migrate to Vite, wire to backend, connect API, transform UI, replicate design, HTML to JSX, HTML to components. Tasks: preserve UI design, colors, layout, and visual identity exactly; decompose HTML into modular React components; wire forms and data to a backend API by matching variable names; create professional folder architecture without over-engineering; generate reusable JSX components, custom hooks, and API service files; ensure fully responsive output matching the original HTML layout."
---

# HTML to React Converter

Converts a static HTML mockup into a production-ready **Vite + React** application. Strictly replicates the original UI — colors, layout, spacing, typography, and overall vibe — while decomposing it into modular React components, wiring it to the backend API, and organizing everything into a clean, professional folder structure.

---

## When to Apply

### Must Use

- User provides an HTML file or mockup and wants it converted to React
- User says "convert", "transform", "migrate", or "rebuild" an HTML page to React/Vite
- User wants to wire a static HTML UI to a backend (REST API, Spring Boot, Node, etc.)
- User wants to turn a prototype/mockup into real working React components
- User wants to match variable names from the backend in the frontend

### Recommended

- HTML page exists but is not componentized or reusable
- Frontend and backend are already built separately and need to be connected
- User wants responsive behavior added to an existing static design

### Skip

- No HTML file or mockup is provided
- User is building from scratch with no existing design reference
- Pure backend or API work with no UI involved

---

## Phase 1 — Analyze the HTML

Before writing any code, deeply analyze the provided HTML file:

### 1.1 Visual Audit
- Identify **all colors** used (background, text, borders, buttons, badges, etc.) — extract exact hex/rgb values
- Identify **typography** — font families, sizes, weights, line heights
- Identify **spacing rhythm** — padding, margin, gap patterns
- Identify **UI style** — flat, glassmorphism, dark mode, minimal, etc.
- Identify **layout structure** — grid, flexbox, fixed sidebar, top navbar, etc.
- Note any **animations or transitions** present

### 1.2 Component Decomposition
Break the HTML into logical React components. Follow this decision rule:

| If the element... | Then... |
|---|---|
| Repeats more than once | Extract as a reusable component |
| Has its own clear responsibility | Extract as a component |
| Is a full page section | Extract as a page-level component |
| Is a single line of JSX | Keep inline, do not over-componentize |

### 1.3 Backend Wiring Audit
- Identify all `<form>` elements and their `input` fields
- Identify all tables, lists, or cards that display data
- Note all button actions (submit, delete, edit, fetch, etc.)
- Match field names, variable names, and data keys against the provided backend code/API
- If no backend is provided, infer REST conventions (`/api/resource`, standard CRUD)

---

## Phase 2 — Folder Architecture

Use this structure. Do not add extra folders unless clearly necessary:

```
src/
├── api/
│   └── [resource].api.js        # All axios/fetch calls for one resource
├── components/
│   ├── common/                  # Truly reusable: Button, Modal, Input, Badge, Table
│   └── [feature]/               # Feature-specific components
│       └── [ComponentName].jsx
├── hooks/
│   └── use[Resource].js         # Custom hooks for data fetching and state
├── pages/
│   └── [PageName].jsx           # Page-level components (routed)
├── utils/
│   └── formatters.js            # Date, currency, string helpers only if needed
├── App.jsx
├── main.jsx
└── index.css                    # Global styles, CSS variables, resets
```

### Rules
- **No barrel files** (`index.js` re-exports) unless the project grows beyond 10+ components
- **No Redux or Zustand** unless the user explicitly asks — use `useState` + `useContext` or custom hooks
- **No unnecessary abstraction** — if a component is used once and is simple, keep it in the page file
- **One component per file** — always
- **Name files exactly like their component** — `UserCard.jsx` exports `UserCard`

---

## Phase 3 — Strict UI Replication Rules

This is the most critical phase. The output must look **identical** to the HTML mockup.

### 3.1 Color Preservation
- Extract all colors from the HTML/CSS into CSS custom properties in `index.css`:
```css
:root {
  --color-primary: #extracted-value;
  --color-bg: #extracted-value;
  --color-text: #extracted-value;
  /* ... all colors found in the HTML */
}
```
- **Never substitute** colors with Tailwind defaults or approximations
- If Tailwind is used, extend the theme in `tailwind.config.js` with the exact extracted values

### 3.2 Layout Replication
- Replicate the exact layout structure (sidebar width, navbar height, grid columns, card dimensions)
- Use the same CSS approach as the original (if HTML used flexbox, use flexbox; if grid, use grid)
- Do not simplify the layout — replicate it precisely

### 3.3 Typography Replication
- Keep the same font family (import from Google Fonts if needed in `index.html`)
- Keep the same font sizes, weights, and line heights
- Do not substitute with Tailwind typography defaults unless they match exactly

### 3.4 Spacing Replication
- Match padding and margin values exactly from the original HTML
- If the HTML used a consistent spacing unit (e.g., 8px rhythm), preserve it

### 3.5 Component Vibe Preservation
- Preserve all visual details: border-radius, box-shadow, opacity, gradients, hover effects
- Replicate all transitions and animations from the original CSS
- Preserve dark/light mode if present

---

## Phase 4 — Responsiveness

Add full responsiveness even if the original HTML was not responsive:

### Breakpoints (Standard)
```css
/* Mobile first */
/* sm: 640px, md: 768px, lg: 1024px, xl: 1280px */
```

### Rules
- Sidebars collapse to a hamburger menu on mobile (`< 768px`)
- Tables become scrollable horizontally on mobile, or transform to card layout
- Navbars stack or collapse on mobile
- Grid columns reduce: 3-col → 2-col → 1-col
- Font sizes scale down on small screens
- Touch targets are minimum 44×44px on mobile

---

## Phase 5 — Backend Wiring

### 5.1 Variable Name Matching
When the backend code or API is provided:
- Read all field names from DTOs, models, or API response shapes
- Map HTML `input name` attributes and display labels to exact backend field names
- **Do not rename** backend variables — match them exactly in state, form fields, and API calls

Example:
```js
// Backend DTO has: { firstName, lastName, emailAddress }
// React state must use the same names:
const [formData, setFormData] = useState({
  firstName: '',
  lastName: '',
  emailAddress: ''
});
```

### 5.2 API Service File
Create one file per backend resource in `src/api/`:
```js
// src/api/users.api.js
import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export const getUsers = () => axios.get(`${BASE_URL}/users`);
export const getUserById = (id) => axios.get(`${BASE_URL}/users/${id}`);
export const createUser = (data) => axios.post(`${BASE_URL}/users`, data);
export const updateUser = (id, data) => axios.put(`${BASE_URL}/users/${id}`, data);
export const deleteUser = (id) => axios.delete(`${BASE_URL}/users/${id}`);
```

### 5.3 Custom Hook per Resource
```js
// src/hooks/useUsers.js
import { useState, useEffect } from 'react';
import { getUsers } from '../api/users.api';

export const useUsers = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    getUsers()
      .then(res => setUsers(res.data))
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return { users, loading, error, setUsers };
};
```

### 5.4 Form Wiring
- Use controlled inputs with `useState`
- On submit: call the API service function, handle loading and error states
- Show loading state on the submit button during the request
- Show inline error messages on failure

### 5.5 Environment Variables
Always use `.env` for the API base URL:
```
# .env
VITE_API_URL=http://localhost:8080/api
```

---

## Phase 6 — Output Checklist

Before delivering the converted code, verify:

### UI Fidelity
- [ ] All colors match the original HTML exactly
- [ ] Layout structure is identical (sidebar, navbar, grid, etc.)
- [ ] Font family, sizes, and weights are preserved
- [ ] Spacing, padding, and margins match
- [ ] All hover effects, shadows, and border-radius are preserved
- [ ] Animations and transitions are replicated
- [ ] No Tailwind default colors used unless they match the original

### React Quality
- [ ] Each component has a single, clear responsibility
- [ ] No component is over-split (no single-line components as separate files)
- [ ] No prop drilling beyond 2 levels (use context or custom hook if needed)
- [ ] All lists use proper `key` props
- [ ] No inline styles unless for dynamic values — use CSS classes

### Backend Wiring
- [ ] All field names match the backend exactly
- [ ] API base URL is in `.env` as `VITE_API_URL`
- [ ] Each resource has its own API service file in `src/api/`
- [ ] Each data-fetching concern has a custom hook in `src/hooks/`
- [ ] Forms show loading state during submission
- [ ] Forms show error messages on failure
- [ ] CORS is accounted for (note to user if backend needs CORS config)

### Responsiveness
- [ ] Layout works on mobile (320px), tablet (768px), and desktop (1280px)
- [ ] No horizontal scroll on mobile
- [ ] Sidebars collapse on mobile
- [ ] Tables are scrollable or reflow on mobile
- [ ] Touch targets are at least 44×44px

### Project Structure
- [ ] Folder structure follows the defined architecture
- [ ] No unnecessary files or folders created
- [ ] `index.css` contains all CSS variables extracted from original HTML
- [ ] `.env` file is created with `VITE_API_URL`
- [ ] `.env` is listed in `.gitignore`

---

## Anti-Patterns — Never Do These

| Anti-Pattern | Why |
|---|---|
| Changing colors to "cleaner" alternatives | Destroys the original design intent |
| Creating a component for every single element | Over-engineering — keep simple things simple |
| Using Redux for simple CRUD pages | Unnecessary complexity |
| Hardcoding the API URL | Breaks across environments |
| Renaming backend variables in the frontend | Causes mismatch bugs and confusion |
| Adding libraries not in the original stack | Scope creep — ask the user first |
| Making the layout "better" without being asked | Not your job — replicate first, suggest improvements after |
| Using placeholder colors like `gray-500` | Always extract and use exact original values |
