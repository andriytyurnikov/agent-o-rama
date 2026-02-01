// @ts-check
/**
 * Helper functions for datasets feature E2E tests.
 */
import { expect } from '@playwright/test';
import { TIMEOUTS } from '../../helpers.js';

/**
 * Open the create dataset modal.
 * @param {import('@playwright/test').Page} page
 */
export async function openCreateDatasetModal(page) {
  await page.getByTestId('btn-create-dataset').first().click();
  await expect(page.getByTestId('modal-dialog')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
}

/**
 * Create a new dataset.
 * @param {import('@playwright/test').Page} page
 * @param {string} name - The dataset name
 */
export async function createDataset(page, name) {
  console.log(`Creating dataset: ${name}`);
  await openCreateDatasetModal(page);

  const modal = page.getByTestId('modal-dialog');
  await modal.getByTestId('input-dataset-name').fill(name);
  await modal.getByTestId('btn-submit-create-dataset').click();
  await expect(page.getByTestId('modal-dialog')).not.toBeVisible({ timeout: TIMEOUTS.NETWORK });

  // Verify dataset was created
  const searchInput = page.locator('input[placeholder*="Search" i]');
  if (await searchInput.isVisible().catch(() => false)) {
    await searchInput.fill(name);
    await page.waitForTimeout(500);
    await expect(page.getByTestId('data-table').locator('tbody tr').filter({ hasText: name })).toBeVisible();
    await searchInput.clear();
    await page.waitForTimeout(300);
  } else {
    await expect(page.getByTestId('data-table').locator('tbody tr').filter({ hasText: name })).toBeVisible();
  }

  console.log(`Successfully created dataset: ${name}`);
}

/**
 * Delete a dataset by name.
 * @param {import('@playwright/test').Page} page
 * @param {string} name - The dataset name
 */
export async function deleteDataset(page, name) {
  if (process.env.SKIP_CLEANUP === '1' || process.env.SKIP_CLEANUP === 'true') {
    console.log(`Skipping cleanup: Keeping dataset "${name}"`);
    return;
  }

  console.log(`Deleting dataset: ${name}`);

  // Set up dialog handler
  let dialogHandled = false;
  const dialogHandler = async (dialog) => {
    if (!dialogHandled) {
      dialogHandled = true;
      await dialog.accept();
    }
  };
  page.once('dialog', dialogHandler);

  // Search for the dataset
  const searchInput = page.locator('input[placeholder*="Search" i]');
  if (await searchInput.isVisible().catch(() => false)) {
    await searchInput.fill(name);
    await page.waitForTimeout(500);
  }

  // Find and click delete
  const row = page.getByTestId('data-table').locator('tbody tr').filter({ hasText: name });
  await row.getByTestId('btn-delete-dataset').click();
  await page.waitForTimeout(500);

  // Clear search
  if (await searchInput.isVisible().catch(() => false)) {
    await searchInput.clear();
    await page.waitForTimeout(300);
  }

  // Verify deletion
  await expect(row).not.toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  console.log(`Successfully deleted dataset: ${name}`);
}
