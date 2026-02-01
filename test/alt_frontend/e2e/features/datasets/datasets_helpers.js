// @ts-check
/**
 * Helper functions for datasets feature E2E tests.
 */
import { expect } from '@playwright/test';
import { navigateTo } from '../../helpers/navigation.js';
import { getTableRow } from '../../helpers/table.js';
import { getModal, waitForModal, waitForModalClose } from '../../helpers/modal.js';
import { shouldSkipCleanup, TIMEOUTS } from '../../helpers/actions.js';

/**
 * Navigate to datasets for a module.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
export async function navigateToDatasets(page, moduleId) {
  await navigateTo(page, `/agents/${moduleId}/datasets`);
}

/**
 * Open the create dataset modal.
 * @param {import('@playwright/test').Page} page
 */
export async function openCreateDatasetModal(page) {
  await page.getByTestId('btn-create-dataset').first().click();
  await waitForModal(page);
}

/**
 * Create a new dataset.
 * @param {import('@playwright/test').Page} page
 * @param {string} name - The dataset name
 */
export async function createDataset(page, name) {
  console.log(`Creating dataset: ${name}`);
  await openCreateDatasetModal(page);

  const modal = getModal(page);
  await modal.getByTestId('input-dataset-name').fill(name);
  await modal.getByTestId('btn-submit-create-dataset').click();
  await waitForModalClose(page, TIMEOUTS.NETWORK);

  // Verify dataset was created
  const searchInput = page.locator('input[placeholder*="Search" i]');
  if (await searchInput.isVisible().catch(() => false)) {
    await searchInput.fill(name);
    await page.waitForTimeout(500);
    await expect(getTableRow(page, name)).toBeVisible();
    await searchInput.clear();
    await page.waitForTimeout(300);
  } else {
    await expect(getTableRow(page, name)).toBeVisible();
  }

  console.log(`Successfully created dataset: ${name}`);
}

/**
 * Delete a dataset by name.
 * @param {import('@playwright/test').Page} page
 * @param {string} name - The dataset name
 */
export async function deleteDataset(page, name) {
  if (shouldSkipCleanup()) {
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
  const row = getTableRow(page, name);
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
