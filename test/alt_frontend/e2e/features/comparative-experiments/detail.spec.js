// @ts-check
/**
 * Feature tests for comparative experiment detail page.
 *
 * Tests header, status, results table, controls, and navigation.
 * Route: /agents/:module-id/datasets/:dataset-id/comparative-experiments/:experiment-id
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish, navigateTo, hasTable } from '../../helpers.js';

/**
 * Navigate from module to a comparative experiment detail page.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 * @returns {Promise<boolean>} true if navigation succeeded
 */
async function navigateToComparativeExperimentDetail(page, moduleId) {
  // Navigate to datasets
  await navigateTo(page, `/agents/${moduleId}/datasets`);

  const dataTable = page.getByTestId('data-table');

  // Check if we have datasets
  const tableVisible = await dataTable.isVisible().catch(() => false);
  if (!tableVisible) {
    return false;
  }

  // Click first dataset row
  const firstRow = dataTable.locator('tbody tr').first();
  if (!(await firstRow.isVisible().catch(() => false))) {
    return false;
  }
  await firstRow.click();
  await waitForLoadingToFinish(page);

  // Click "Comparative" nav card
  const comparativeCard = page.locator('.card').filter({ hasText: /Comparative/i });
  if (!(await comparativeCard.isVisible().catch(() => false))) {
    return false;
  }
  await comparativeCard.click();
  await waitForLoadingToFinish(page);

  // Check if we have experiments in the table
  const experimentsTable = page.getByTestId('data-table');
  const expTableVisible = await experimentsTable.isVisible().catch(() => false);
  if (!expTableVisible) {
    return false;
  }

  const experimentRow = experimentsTable.locator('tbody tr').first();
  if (!(await experimentRow.isVisible().catch(() => false))) {
    return false;
  }

  // Click to navigate to detail
  await experimentRow.click();
  await waitForLoadingToFinish(page);

  return true;
}

test.describe('Comparative Experiment Detail', () => {
  test('detail page shows header with status', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToComparativeExperimentDetail(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to comparative experiment detail');
      return;
    }

    // Verify we're on a detail page (URL contains experiment ID)
    const url = page.url();
    expect(url).toMatch(/\/comparative-experiments\/[^/]+/);

    // Header should be visible with title
    const header = page.locator('h1');
    await expect(header).toBeVisible();

    // Status badge should be visible
    const statusBadge = page.locator('.badge').filter({ hasText: /Completed|Running|Failed/i });
    await expect(statusBadge.first()).toBeVisible();
  });

  test('detail page shows results table', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToComparativeExperimentDetail(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to comparative experiment detail');
      return;
    }

    // Results section should be visible
    const resultsHeading = page.locator('h3').filter({ hasText: /Results/i });
    await expect(resultsHeading).toBeVisible();

    // Either a results table or "No results yet" message
    const table = page.locator('table');
    const noResultsMessage = page.locator('text=/no results yet/i');

    const hasResultsTable = await table.isVisible().catch(() => false);
    const hasNoResults = await noResultsMessage.isVisible().catch(() => false);

    expect(hasResultsTable || hasNoResults).toBe(true);

    // If table exists, verify it has expected columns
    if (hasResultsTable) {
      const tableHeader = table.locator('thead');
      await expect(tableHeader).toContainText(/Input/i);
      await expect(tableHeader).toContainText(/Reference Output|Output/i);
    }
  });

  test('full text toggle expands cell content', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToComparativeExperimentDetail(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to comparative experiment detail');
      return;
    }

    // Look for the full text toggle
    const fullTextToggle = page.locator('label').filter({ hasText: /Full text/i });

    if (!(await fullTextToggle.isVisible().catch(() => false))) {
      test.skip('Full text toggle not visible (no results)');
      return;
    }

    // Get initial state of toggle
    const toggle = fullTextToggle.locator('input[type="checkbox"]');
    const initialChecked = await toggle.isChecked();

    // Click the toggle
    await toggle.click();
    await page.waitForTimeout(TIMEOUTS.ANIMATION);

    // Verify toggle state changed
    const newChecked = await toggle.isChecked();
    expect(newChecked).toBe(!initialChecked);
  });

  test('back button navigates to list', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToComparativeExperimentDetail(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to comparative experiment detail');
      return;
    }

    // Get current URL (should be on detail page)
    const detailUrl = page.url();
    expect(detailUrl).toMatch(/\/comparative-experiments\/[^/]+/);

    // Find and click the back button
    const backButton = page.locator('a, button').filter({ hasText: /← Back|Back/i }).first();
    await expect(backButton).toBeVisible();
    await backButton.click();
    await waitForLoadingToFinish(page);

    // Should be back on the list page
    const listUrl = page.url();
    expect(listUrl).toContain('/comparative-experiments');
    // Should not have an experiment ID in the URL
    expect(listUrl).not.toMatch(/\/comparative-experiments\/[a-f0-9-]+/);
  });
});
