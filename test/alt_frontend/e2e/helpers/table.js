// @ts-check
/**
 * Table helper functions for alt-frontend E2E tests.
 */

/**
 * Get the data table locator.
 * @param {import('@playwright/test').Page} page
 * @returns {import('@playwright/test').Locator}
 */
export function getDataTable(page) {
  return page.getByTestId('data-table');
}

/**
 * Get a table row by its text content.
 * @param {import('@playwright/test').Page} page
 * @param {string} text
 * @returns {import('@playwright/test').Locator}
 */
export function getTableRow(page, text) {
  return getDataTable(page).locator('tbody tr').filter({ hasText: text });
}

/**
 * Get the first table row.
 * @param {import('@playwright/test').Page} page
 * @returns {import('@playwright/test').Locator}
 */
export function getFirstTableRow(page) {
  return getDataTable(page).locator('tbody tr').first();
}

/**
 * Check if the data table is visible.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<boolean>}
 */
export async function hasTable(page) {
  return await getDataTable(page).isVisible().catch(() => false);
}

/**
 * Check if the empty state is visible.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<boolean>}
 */
export async function hasEmptyState(page) {
  return await page.getByTestId('empty-state').isVisible().catch(() => false);
}

/**
 * Get table headers.
 * @param {import('@playwright/test').Page} page
 * @returns {import('@playwright/test').Locator}
 */
export function getTableHeaders(page) {
  return getDataTable(page).locator('thead th');
}

/**
 * Get all table header texts.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<string[]>}
 */
export async function getTableHeaderTexts(page) {
  return await getTableHeaders(page).allTextContents();
}
