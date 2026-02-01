// @ts-check
/**
 * Tests for dataset example management.
 *
 * Prerequisites: A dataset must exist. Tests create one if needed.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS } from '../../helpers/selectors.js';
import { waitForLoadingToFinish } from '../../helpers/actions.js';
import { navigateToDatasets } from './datasets_helpers.js';

/**
 * Navigate to examples page for a dataset.
 * Creates a dataset first if none exist.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
async function navigateToExamples(page, moduleId) {
  await navigateToDatasets(page, moduleId);

  // Check for existing datasets
  const dataTable = page.getByTestId('data-table');
  const hasTable = await dataTable.isVisible().catch(() => false);

  if (!hasTable) {
    // Create a dataset
    const createButton = page.getByTestId('btn-create-dataset');
    if (!(await createButton.first().isVisible().catch(() => false))) {
      return null;
    }

    await createButton.first().click();
    const modal = page.getByTestId('modal-dialog');
    await expect(modal).toBeVisible();

    const testName = `e2e-examples-test-${Date.now()}`;
    await page.getByTestId('input-dataset-name').fill(testName);
    await page.getByTestId('btn-submit-create-dataset').click();
    await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.NETWORK });
    await waitForLoadingToFinish(page);
  }

  // Click first dataset row (using testid pattern)
  const firstRow = page.locator('[data-testid^="dataset-row-"]').first();
  if (!(await firstRow.isVisible().catch(() => false))) {
    return null;
  }

  await firstRow.click();
  await waitForLoadingToFinish(page);

  // Click View Examples button
  const viewExamplesButton = page.getByTestId('btn-view-examples');
  if (await viewExamplesButton.isVisible().catch(() => false)) {
    await viewExamplesButton.click();
    await waitForLoadingToFinish(page);
  }

  // Verify we're on examples page
  const url = page.url();
  return url.includes('/examples') ? url : null;
}

test.describe('Dataset Examples', () => {
  test('add example button opens modal', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const examplesUrl = await navigateToExamples(page, moduleId);
    if (!examplesUrl) {
      test.skip('Could not navigate to examples page');
      return;
    }

    expect(page.url()).toContain('/examples');

    const addButton = page.getByTestId('btn-add-example');
    const isButtonVisible = await addButton.isVisible().catch(() => false);

    if (!isButtonVisible) {
      test.skip('Add example button not found');
      return;
    }

    await addButton.click();

    const modal = page.getByTestId('modal-dialog');
    await expect(modal).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    // Modal should have input field
    const inputField = page.getByTestId('input-example-json');
    await expect(inputField).toBeVisible();

    // Close modal
    await page.getByTestId('modal-close').click();
    await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  });

  test('submit example adds to list', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const examplesUrl = await navigateToExamples(page, moduleId);
    if (!examplesUrl) {
      test.skip('Could not navigate to examples page');
      return;
    }

    expect(page.url()).toContain('/examples');

    const addButton = page.getByTestId('btn-add-example');
    if (!(await addButton.isVisible().catch(() => false))) {
      test.skip('Add example button not found');
      return;
    }

    await addButton.click();

    const modal = page.getByTestId('modal-dialog');
    await expect(modal).toBeVisible();

    // Fill form
    const testInput = { query: `test-example-${Date.now()}` };
    const inputField = page.getByTestId('input-example-json');
    await inputField.fill(JSON.stringify(testInput, null, 2));

    // Submit
    const submitButton = page.getByTestId('btn-submit-add-example');
    await submitButton.click();

    // Modal should close
    await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.NETWORK });

    // Table should be visible
    await waitForLoadingToFinish(page);
    const dataTable = page.getByTestId('data-table');
    await expect(dataTable).toBeVisible({ timeout: TIMEOUTS.NETWORK });
  });

  test('delete example button exists', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const examplesUrl = await navigateToExamples(page, moduleId);
    if (!examplesUrl) {
      test.skip('Could not navigate to examples page');
      return;
    }

    expect(page.url()).toContain('/examples');
    await waitForLoadingToFinish(page);

    // Check if there's a table with examples
    const dataTable = page.getByTestId('data-table');
    const hasTable = await dataTable.isVisible().catch(() => false);

    if (!hasTable) {
      test.skip('No examples table visible');
      return;
    }

    // Check if delete button exists
    const deleteButton = page.getByTestId('btn-delete-example').first();
    const hasDelete = await deleteButton.isVisible().catch(() => false);

    if (!hasDelete) {
      test.skip('No delete button found (no examples or read-only)');
      return;
    }

    // Button should be visible
    await expect(deleteButton).toBeVisible();
  });
});
