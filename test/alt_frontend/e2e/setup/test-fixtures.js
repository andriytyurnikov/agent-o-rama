// @ts-check
import { test as base, expect } from '@playwright/test';
import { waitForLoadingToFinish } from '../helpers.js';

/**
 * Extended test fixtures for alt-frontend E2E tests.
 *
 * Usage:
 *   import { test, expect } from '../setup/test-fixtures.js';
 *
 *   test('example', async ({ moduleId, page }) => {
 *     if (!moduleId) {
 *       test.skip('No module available');
 *       return;
 *     }
 *     // ... test code using moduleId and page
 *   });
 */

export const test = base.extend({
  /**
   * Module ID fixture - navigates to agents and opens the first available module.
   * Returns the module ID or null if no modules exist.
   */
  moduleId: async ({ page }, use) => {
    // Navigate to agents list
    await page.goto('./#/agents');
    await waitForLoadingToFinish(page);

    // Try to open the first module
    const firstRow = page.getByTestId('data-table').locator('tbody tr').first();
    if (!(await firstRow.isVisible().catch(() => false))) {
      await use(null);
      return;
    }

    // Click to navigate to module
    const link = firstRow.locator('a').first();
    if (await link.isVisible().catch(() => false)) {
      await link.click();
    } else {
      await firstRow.click();
    }

    await waitForLoadingToFinish(page);

    // Extract module ID from URL
    const url = page.url();
    const match = url.match(/\/agents\/([^/]+)/);
    const moduleId = match ? match[1] : null;

    await use(moduleId);
  },
});

// Re-export expect for convenience
export { expect };
