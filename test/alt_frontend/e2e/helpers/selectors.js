// @ts-check
/**
 * Centralized selectors for alt-frontend E2E tests.
 *
 * Uses data-testid attributes added to components for stable, semantic selectors.
 * See src/alt_frontend/components/ for testid definitions.
 *
 * USAGE: Use page.getByTestId('testid-name') directly in tests and page objects.
 * Common testid names:
 * - Layout: app-layout, navbar, sidebar, breadcrumbs, connection-status, mobile-menu-toggle
 * - Modal: modal-dialog, modal-content, modal-close
 * - UI: data-table, loading-spinner, empty-state
 * - Navigation: nav-agents, nav-datasets, nav-evaluations, nav-analytics, nav-rules, nav-config
 * - Buttons: btn-create-*, btn-delete-*, btn-submit-*
 * - Inputs: input-*
 */

/**
 * Timeout constants for different scenarios (in milliseconds).
 */
export const TIMEOUTS = {
  /** Fast assertions - for visibility checks */
  FAST: 500,
  /** Default for most operations */
  DEFAULT: 500,
  /** Navigation and loading */
  NAVIGATION: 1000,
  /** Network operations and WebSocket */
  NETWORK: 3000,
  /** Search debounce delay */
  DEBOUNCE: 500,
  /** Animation settle time */
  ANIMATION: 300,
};

/** @deprecated Use TIMEOUTS.FAST instead */
export const DEFAULT_TIMEOUT = TIMEOUTS.FAST;

/**
 * Common text patterns used in tests.
 */
export const PATTERNS = {
  // Search placeholders
  SEARCH: /Search/i,
  SEARCH_EVALUATORS: /Search evaluators/i,
  SEARCH_DATASETS: /Search datasets/i,

  // Status badges
  STATUS_RUNNING: /Running|In Progress|Pending/i,
  STATUS_COMPLETED: /Complete|Finished|Done|Success/i,
  STATUS_FAILED: /Failed|Error|Exception/i,

  // Common button labels
  SUBMIT: /Submit|Save|Create|Confirm/i,
  CANCEL: /Cancel|Close|Dismiss/i,
  DELETE: /Delete|Remove/i,
  EDIT: /Edit|Modify|Update/i,
  INVOKE: /Invoke|Run|Execute|Submit/i,

  // Empty states
  NO_DATA: /No\s+\w+|Empty|Create your first/i,
  NO_RESULTS: /No results|Nothing found|No matches/i,

  // Agent invocation
  ARGS_PLACEHOLDER: /\[arg1, arg2|args|arguments/i,

  // Time/duration
  TIMING: /Duration|Started|Finished|Time|Latency/i,
};

/**
 * Wait for search debounce to complete.
 * Use this instead of hardcoded waitForTimeout after search input.
 *
 * @param {import('@playwright/test').Page} page
 */
export async function waitForSearchDebounce(page) {
  await page.waitForTimeout(TIMEOUTS.DEBOUNCE);
}

/**
 * Wait for animation to settle.
 * Use this instead of hardcoded waitForTimeout after UI transitions.
 *
 * @param {import('@playwright/test').Page} page
 */
export async function waitForAnimation(page) {
  await page.waitForTimeout(TIMEOUTS.ANIMATION);
}
