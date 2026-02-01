// @ts-check
import { TIMEOUTS } from './selectors.js';

// Re-export for convenience
export { TIMEOUTS } from './selectors.js';

/**
 * Common actions for alt-frontend E2E tests.
 *
 * These are reusable action functions that encapsulate common UI interactions.
 */

/**
 * Check if cleanup should be skipped (e.g., for debugging).
 * Set SKIP_CLEANUP=1 environment variable to skip cleanup.
 *
 * @returns {boolean}
 */
export function shouldSkipCleanup() {
  return process.env.SKIP_CLEANUP === '1' || process.env.SKIP_CLEANUP === 'true';
}

/**
 * Wait for loading spinners to disappear.
 *
 * @param {import('@playwright/test').Page} page
 * @param {number} timeout - Maximum time to wait in milliseconds
 */
export async function waitForLoadingToFinish(page, timeout = TIMEOUTS.DEFAULT) {
  const loadingSpinner = page.getByTestId('loading-spinner');
  await loadingSpinner.waitFor({ state: 'hidden', timeout }).catch(() => {
    // It's OK if there are no loading spinners
  });
}
