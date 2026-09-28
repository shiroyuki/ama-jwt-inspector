---
name: frontend-development
description: Use when the user wants to build UI components or applications using React (Ant Design) or Angular (ng-zorro-antd), following SOLID principles.
---

# Frontend Development Skill (React/Ant Design & Angular/ng-zorro-antd)

This skill provides guidance on building scalable, maintainable, and high-quality frontend applications using industry-standard UI libraries while strictly adhering to SOLID principles.

## SOLID Principles in Frontend Development

- **Single Responsibility (SRP):** A component or service should do one thing. Break down large components into smaller, reusable units. (e.g., a "SearchButton" shouldn't handle API calls; call a "SearchService").
- **Open/Closed (OCP):** Code should be open for extension but closed for modification. Use composition over inheritance. (e.g., use props/inputs to extend behavior instead of changing component logic).
- **Liskov Substitution (LSP):** Sub-components (or child components) should be replaceable without breaking the app. Ensure consistent interface expectations.
- **Interface Segregation (ISP):** Don't force a component to depend on props/methods it doesn't use. Split large interfaces into smaller ones.
- **Dependency Inversion (DIP):** Depend on abstractions, not concretions. Inject dependencies (Services in Angular, Context/Hooks in React) rather than hardcoding logic inside components.

---

## React + Ant Design Guide

### Architecture
- **Component Structure:** Use a "Container/Presentational" pattern or "Hooks-based" separation.
- **State Management:** Prefer local state for UI-only state; use Context/Zustand for global state.
- **Ant Design usage:** 
  - Use `Space` for layouting.
  - Use `Form` component for handling inputs to ensure validation and `Rule` consistency.
  - Use `Typography`, `Button`, `Input` consistently.

### SOLID Example (React)
- **SR:** Create a `UserAvatar` component that only displays an image.
- **DI:** Pass a `fetchUser` function as a dependency to a `UserList` component rather than hardcoding the fetch call.

---

## Angular + ng-zorro-antd Guide

### Architecture
- **Module System:** Use Lazy Loading for features.
- **Reactive Forms:** Always prefer `FormGroup` and `FormControl` for complex inputs.
- **Angular Material/NG-ZORRO:** Use shared components for wrappers.

### SOLID Example (Angular)
- **SR:** Separate "Data Fetching" into a `DataService` and "UI Rendering" into a `Component`.
- **OCP:** Use `ContentProjection` (`ng-content`) to allow users to inject custom UI into a generic wrapper.

---

## Best Practices
- **Typography:** Maintain a consistent scale.
- **Accessibility:** Ensure ARIA labels and keyboard navigation are handled by the UI library.
- **Responsive Design:** Use the built-in `Grid` systems (`Row`, `Col`) of both libraries.
- **Type Safety:** Always use TypeScript interfaces for data models and component inputs.
