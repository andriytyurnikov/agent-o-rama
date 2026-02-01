// @ts-check
import { expect } from '@playwright/test';
import { TIMEOUTS } from './selectors.js';

// Re-export for convenience
export { TIMEOUTS } from './selectors.js';

/**
 * Common actions for alt-frontend E2E tests.
 *
 * These are reusable action functions that encapsulate common UI interactions.
 */

/**
 * Wait for the WebSocket connection to be established.
 *
 * @param {import('@playwright/test').Page} page
 * @param {number} timeout - Maximum time to wait in milliseconds
 */
export async function waitForConnection(page, timeout = TIMEOUTS.DEFAULT) {
  const connectionStatus = page.getByTestId('connection-status');
  await expect(connectionStatus).toHaveAttribute('data-tip', 'Connected', { timeout });
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
