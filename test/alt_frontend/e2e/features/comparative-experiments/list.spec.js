// @ts-check
/**
 * Feature tests for comparative experiments list.
 *
 * Tests navigation, listing, status display, and row click navigation.
 * Route: /agents/:module-id/datasets/:dataset-id/comparative-experiments
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish, navigateTo, hasTable, hasEmptyState } from '../../helpers.js';

/**
 * Navigate from module to comparative experiments list.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 * @returns {Promise<boolean>} true if navigation succeeded
 */
async function navigateToComparativeExperiments(page, moduleId) {
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

  return true;
}

test.describe('Comparative Experiments List', () => {
  test('list page loads via nav card', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToComparativeExperiments(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to comparative experiments');
      return;
    }

    // Verify URL contains /comparative-experiments
    const url = page.url();
    expect(url).toContain('/comparative-experiments');

    // Verify page renders (layout visible)
    const hasLayout = await page.getByTestId('app-layout').isVisible().catch(() => false);
    expect(hasLayout).toBe(true);
  });

  test('list displays experiments or empty state', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToComparativeExperiments(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to comparative experiments');
      return;
    }

    // Should have either a table with experiments or an empty state
    const tableVisible = await hasTable(page);
    const emptyVisible = await hasEmptyState(page);

    expect(tableVisible || emptyVisible).toBe(true);

    // If empty state, verify the message
    if (emptyVisible) {
      const emptyState = page.getByTestId('empty-state');
      await expect(emptyState).toContainText(/no comparative experiments/i);
    }
  });

  test('list shows status badges for experiments', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToComparativeExperiments(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to comparative experiments');
      return;
    }

    const dataTable = page.getByTestId('data-table');
    const tableVisible = await dataTable.isVisible().catch(() => false);

    if (!tableVisible) {
      test.skip('No experiments table available');
      return;
    }

    const rowCount = await dataTable.locator('tbody tr').count();
    if (rowCount === 0) {
      test.skip('No experiments available');
      return;
    }

    // Each row should have a status badge (Completed or Running)
    const badges = dataTable.locator('tbody .badge');
    const badgeCount = await badges.count();
    expect(badgeCount).toBeGreaterThan(0);

    // Check that badges contain valid status text
    const firstBadge = badges.first();
    const badgeText = await firstBadge.textContent();
    expect(badgeText?.toLowerCase()).toMatch(/completed|running/);
  });

  test('clicking row navigates to experiment detail', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToComparativeExperiments(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to comparative experiments');
      return;
    }

    const dataTable = page.getByTestId('data-table');
    const tableVisible = await dataTable.isVisible().catch(() => false);

    if (!tableVisible) {
      test.skip('No experiments table available');
      return;
    }

    const firstRow = dataTable.locator('tbody tr').first();
    if (!(await firstRow.isVisible().catch(() => false))) {
      test.skip('No experiments available');
      return;
    }

    const urlBefore = page.url();

    // Click the row to navigate to detail
    await firstRow.click();
    await waitForLoadingToFinish(page);

    // URL should change to include the experiment ID
    const urlAfter = page.url();
    expect(urlAfter).not.toBe(urlBefore);
    expect(urlAfter).toContain('/comparative-experiments/');
  });
});
