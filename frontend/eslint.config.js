import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';

/**
 * Flat ESLint config for the operator console (ARCHITECTURE.md §3.3, §8.4).
 *
 * §8.4's pipeline opens with "Lint + Format", and `npm run lint` was wired in package.json from the
 * start — but no config file had ever existed, so the script failed with "ESLint couldn't find an
 * eslint.config.js" rather than linting anything. Added at M12, when the CI workflow that runs it
 * became real.
 *
 * The rules that carry a project directive are errors, not warnings:
 *
 *   - `@typescript-eslint/no-explicit-any` — CLAUDE.md §3 says no `any`; use `unknown` and narrow.
 *   - `react-hooks/rules-of-hooks` and `exhaustive-deps` — the streaming stores depend on effects
 *     firing exactly when their dependencies say they do (§3.4).
 *
 * Type-aware linting (`recommendedTypeChecked`) is deliberately not enabled: it needs a second full
 * type-check pass over the project on every lint, and `npm run typecheck` already runs `tsc` in
 * strict mode. Two passes for one guarantee is not worth the CI minutes.
 */
export default tseslint.config(
  {
    // Build output and coverage are not sources.
    ignores: ['dist/**', 'coverage/**', 'playwright-report/**', 'test-results/**', 'storybook-static/**'],
  },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser, ...globals.es2021 },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,

      '@typescript-eslint/no-explicit-any': 'error',

      // These two are the React-Compiler-era rules that arrived with eslint-plugin-react-hooks v7.
      // They are warnings here, deliberately, and the reason is specific rather than general
      // reluctance: the three places they fire are patterns §3.4 requires.
      //
      //   * MapView keeps the latest tracks/alerts in a ref assigned during render, because the map
      //     is created once and lives outside React — its handlers must read current values, not a
      //     closure captured at mount. Moving the assignment into an effect would make it a frame
      //     stale against the §2.5.1 10 Hz budget.
      //   * MapView calls setFailed(true) from the catch around map construction — error handling in
      //     an effect, which is what an effect is for.
      //   * useTelemetryHistory clears its sample buffer when the selected track changes; one
      //     track's trend is not another's.
      //
      // All three are covered by tests and were exercised in the M11 live run. Left visible as
      // warnings rather than silenced, so a genuinely careless setState in an effect still shows up.
      'react-hooks/set-state-in-effect': 'warn',
      'react-hooks/refs': 'warn',

      // An unused parameter prefixed with _ is a deliberate signature-shape marker, not a mistake.
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
      ],

      // Fast Refresh only works when a module exports components and nothing else. Route components
      // are the documented exception (§3.3 allows their default export), as are the design-system
      // barrels that re-export constants alongside components.
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },

  // Test files legitimately do things production code must not: reach into internals, build partial
  // fixtures, and assert on shapes that are deliberately not the real type.
  {
    files: ['**/*.test.{ts,tsx}', '**/test/**', 'e2e/**', 'e2e-live/**', '*.config.ts'],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      'react-refresh/only-export-components': 'off',
    },
  },
);
