// Registers the @testing-library/jest-dom matchers (toBeInTheDocument, etc.) for the jsdom component
// tests. Harmless for the plain-TypeScript tests, which never touch the DOM.
import '@testing-library/jest-dom/vitest';
