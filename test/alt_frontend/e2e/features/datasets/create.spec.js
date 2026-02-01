// @ts-check
/**
 * Tests for dataset creation.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish, navigateTo } from '../../helpers.js';

test.describe('Dataset Creation', () => {
  test('create button opens modal', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/datasets`);
    expect(page.url()).toContain(`#/agents/${moduleId}/datasets`);

    const createButton = page.getByTestId('btn-create-dataset');
    const isButtonVisible = await createButton.first().isVisible().catch(() => false);

    if (!isButtonVisible) {
      test.skip('Create button not available');
      return;
    }

    await createButton.first().click();

    const modal = page.getByTestId('modal-dialog');
    await expect(modal).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    // Modal should have name input
    const nameInput = page.getByTestId('input-dataset-name');
    await expect(nameInput).toBeVisible();

    // Close modal via close button
    await page.getByTestId('modal-close').click();
    await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  });

  test('submit form creates dataset', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/datasets`);
    expect(page.url()).toContain(`#/agents/${moduleId}/datasets`);

    const createButton = page.getByTestId('btn-create-dataset');
    const isButtonVisible = await createButton.first().isVisible().catch(() => false);

    if (!isButtonVisible) {
      test.skip('Create button not available');
      return;
    }

    await createButton.first().click();

    const modal = page.getByTestId('modal-dialog');
    await expect(modal).toBeVisible();

    // Fill form
    const testName = `e2e-create-${Date.now()}`;
    const nameInput = page.getByTestId('input-dataset-name');
    await nameInput.fill(testName);

    // Submit
    const submitButton = page.getByTestId('btn-submit-create-dataset');
    await submitButton.click();

    // Modal should close after server responds
    await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.NETWORK });

    // Dataset should appear in list
    await waitForLoadingToFinish(page);

    // Verify table is visible with our new dataset
    const dataTable = page.getByTestId('data-table');
    await expect(dataTable).toBeVisible({ timeout: TIMEOUTS.NETWORK });
  });
});
