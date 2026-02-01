// @ts-check
/**
 * Shared E2E test helpers.
 * Feature-specific helpers live in features/<feature>/<feature>_helpers.js
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

/**
 * Navigate to a hash-based route.
 * @param {import('@playwright/test').Page} page
 * @param {string} path - The route path (without hash)
 */
export async function navigateTo(page, path) {
  await page.goto(`./#${path}`);
  await waitForLoadingToFinish(page);
}

/**
 * Check if the data table is visible.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<boolean>}
 */
export async function hasTable(page) {
  return await page.getByTestId('data-table').isVisible().catch(() => false);
}

/**
 * Check if the empty state is visible.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<boolean>}
 */
export async function hasEmptyState(page) {
  return await page.getByTestId('empty-state').isVisible().catch(() => false);
}
