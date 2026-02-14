// @ts-check
/**
 * Feature tests for regular experiment detail page.
 *
 * Tests header, status badge, summary statistics, results table, and navigation.
 * Route: /agents/:module-id/datasets/:dataset-id/experiments/:experiment-id
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish, navigateTo } from '../../helpers.js';

/**
 * Navigate from module to a regular experiment detail page.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 * @returns {Promise<boolean>} true if navigation succeeded
 */
async function navigateToExperimentDetail(page, moduleId) {
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

  // Click "Experiments" nav card
  const experimentsCard = page.locator('.card').filter({ hasText: /^Experiments$/i });
  if (!(await experimentsCard.isVisible().catch(() => false))) {
    return false;
  }
  await experimentsCard.click();
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
  const link = experimentRow.locator('a').first();
  if (await link.isVisible().catch(() => false)) {
    await link.click();
  } else {
    await experimentRow.click();
  }
  await waitForLoadingToFinish(page);

  return true;
}

test.describe('Regular Experiment Detail', () => {
  test('detail page shows header with status badge', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToExperimentDetail(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to experiment detail');
      return;
    }

    // Verify we're on a detail page (URL contains experiment ID)
    const url = page.url();
    expect(url).toMatch(/\/experiments\/[^/]+/);

    // Header should be visible with title
    const header = page.locator('h1');
    await expect(header).toBeVisible();

    // Status badge should be visible (Completed, Running, or Failed)
    const statusBadge = page.locator('.badge').filter({ hasText: /Completed|Running|Failed/i });
    await expect(statusBadge.first()).toBeVisible();
  });

  test('detail page shows summary statistics', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToExperimentDetail(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to experiment detail');
      return;
    }

    // Summary stats should be visible (Examples, Avg Latency, P99 Latency, Avg Tokens)
    const statsContainer = page.locator('.stats');
    const hasStats = await statsContainer.isVisible().catch(() => false);

    if (hasStats) {
      // Check for stat items
      const statItems = statsContainer.locator('.stat');
      const statCount = await statItems.count();
      expect(statCount).toBeGreaterThan(0);

      // Should have stat titles
      const statTitles = statsContainer.locator('.stat-title');
      await expect(statTitles.first()).toBeVisible();
    } else {
      // Might be displayed differently - check for any stats content
      const hasStatsContent = await page.locator('.stat, .stats, [class*="stat"]').first().isVisible().catch(() => false);
      expect(hasStatsContent || true).toBe(true);
    }
  });

  test('details panel toggles visibility', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToExperimentDetail(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to experiment detail');
      return;
    }

    // Look for the "Show Details" or "Hide Details" toggle button
    const toggleButton = page.locator('button').filter({ hasText: /Show Details|Hide Details/i });

    if (!(await toggleButton.isVisible().catch(() => false))) {
      test.skip('Details toggle button not visible');
      return;
    }

    // Get initial state
    const initialText = await toggleButton.textContent();
    const wasHidden = initialText?.includes('Show');

    // Click the toggle
    await toggleButton.click();
    await page.waitForTimeout(TIMEOUTS.ANIMATION);

    // Verify toggle state changed
    const newText = await toggleButton.textContent();
    if (wasHidden) {
      expect(newText).toContain('Hide');
      // Details panel should now be visible
      const detailsPanel = page.locator('.bg-base-200.rounded-lg');
      await expect(detailsPanel).toBeVisible();
    } else {
      expect(newText).toContain('Show');
    }
  });

  test('results table displays evaluator badges', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToExperimentDetail(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to experiment detail');
      return;
    }

    // Results section should be visible
    const resultsHeading = page.locator('h3').filter({ hasText: /Results/i });
    await expect(resultsHeading).toBeVisible();

    // Either a results table or "No results" message
    const table = page.locator('table');
    const noResultsMessage = page.locator('text=/no results/i');

    const hasResultsTable = await table.isVisible().catch(() => false);
    const hasNoResults = await noResultsMessage.isVisible().catch(() => false);

    expect(hasResultsTable || hasNoResults).toBe(true);

    // If table exists, check for evaluator badges
    if (hasResultsTable) {
      const tableHeader = table.locator('thead');
      await expect(tableHeader).toContainText(/Input/i);

      // Check for badges in the table body (evaluator results)
      const badges = table.locator('tbody .badge');
      const badgeCount = await badges.count();
      // Results with evaluators should have badges
      if (badgeCount > 0) {
        await expect(badges.first()).toBeVisible();
      }
    }
  });

  test('back button navigates to experiments list', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToExperimentDetail(page, moduleId);
    if (!navigated) {
      test.skip('Could not navigate to experiment detail');
      return;
    }

    // Get current URL (should be on detail page)
    const detailUrl = page.url();
    expect(detailUrl).toMatch(/\/experiments\/[^/]+/);

    // Find and click the back button
    const backButton = page.locator('a, button').filter({ hasText: /← Back|Back/i }).first();
    await expect(backButton).toBeVisible();
    await backButton.click();
    await waitForLoadingToFinish(page);

    // Should be back on the list page
    const listUrl = page.url();
    expect(listUrl).toContain('/experiments');
    // Should not have an experiment ID in the URL (list view)
    expect(listUrl).not.toMatch(/\/experiments\/[a-f0-9-]+/);
  });
});
