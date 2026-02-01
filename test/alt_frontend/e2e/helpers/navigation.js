// @ts-check
/**
 * Generic navigation helper functions for alt-frontend E2E tests.
 *
 * Feature-specific navigation helpers are colocated with their feature tests.
 * See features/<feature>/<feature>_helpers.js for feature-specific navigation.
 */
import { waitForLoadingToFinish } from './actions.js';

/**
 * Navigate to a hash-based route.
 * @param {import('@playwright/test').Page} page
 * @param {string} path - The route path (without hash)
 */
export async function navigateTo(page, path) {
  await page.goto(`./#${path}`);
  await waitForLoadingToFinish(page);
}
