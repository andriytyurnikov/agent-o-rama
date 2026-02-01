// @ts-check
/**
 * Modal helper functions for alt-frontend E2E tests.
 */
import { expect } from '@playwright/test';
import { TIMEOUTS } from './selectors.js';

/**
 * Get the modal dialog locator.
 * @param {import('@playwright/test').Page} page
 * @returns {import('@playwright/test').Locator}
 */
export function getModal(page) {
  return page.getByTestId('modal-dialog');
}

/**
 * Check if modal is visible.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<boolean>}
 */
export async function isModalVisible(page) {
  return await getModal(page).isVisible().catch(() => false);
}

/**
 * Wait for modal to be visible.
 * @param {import('@playwright/test').Page} page
 * @param {number} [timeout]
 */
export async function waitForModal(page, timeout = TIMEOUTS.DEFAULT) {
  await expect(getModal(page)).toBeVisible({ timeout });
}

/**
 * Wait for modal to close.
 * @param {import('@playwright/test').Page} page
 * @param {number} [timeout]
 */
export async function waitForModalClose(page, timeout = TIMEOUTS.DEFAULT) {
  await expect(getModal(page)).not.toBeVisible({ timeout });
}

/**
 * Close the modal using the close button or Escape key.
 * @param {import('@playwright/test').Page} page
 */
export async function closeModal(page) {
  const modalClose = page.getByTestId('modal-close');
  if (await modalClose.isVisible().catch(() => false)) {
    await modalClose.click();
  } else {
    await page.keyboard.press('Escape');
  }
  await waitForModalClose(page);
}
