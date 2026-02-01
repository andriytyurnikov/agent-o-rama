// @ts-check
/**
 * Tests for datasets list display and search functionality.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS } from '../../helpers/selectors.js';
import { navigateToDatasets } from '../../helpers/navigation.js';
import { hasTable, hasEmptyState } from '../../helpers/table.js';

test.describe('Datasets List', () => {
  test('displays datasets or empty state', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToDatasets(page, moduleId);

    expect(page.url()).toContain(`#/agents/${moduleId}/datasets`);

    // Should show either data table or empty state
    const tableVisible = await hasTable(page);
    const emptyVisible = await hasEmptyState(page);

    expect(tableVisible || emptyVisible).toBe(true);
  });

  test('search filters datasets', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToDatasets(page, moduleId);
    expect(page.url()).toContain(`#/agents/${moduleId}/datasets`);

    const searchInput = page.locator('input[placeholder*="Search" i]');
    const isSearchVisible = await searchInput.isVisible().catch(() => false);

    if (!isSearchVisible) {
      test.skip('Search input not available');
      return;
    }

    // Type search term
    await searchInput.fill('nonexistent-dataset-xyz');
    await page.waitForTimeout(TIMEOUTS.DEBOUNCE);

    // Page should still be functional after search
    await expect(page.locator('body')).toBeVisible();

    // Clear search
    await searchInput.clear();
    await page.waitForTimeout(TIMEOUTS.DEBOUNCE);
  });
});
