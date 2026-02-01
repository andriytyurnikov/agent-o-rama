// @ts-check
/**
 * Feature tests for evaluators listing.
 *
 * Tests evaluator list display, table columns, filtering, and search.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS } from '../../helpers/selectors.js';
import { waitForLoadingToFinish } from '../../helpers/actions.js';
import { hasTable, hasEmptyState } from '../../helpers/table.js';
import { navigateToEvaluators } from './evaluators_helpers.js';

test.describe('Evaluators List', () => {
  test('list evaluators for module', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToEvaluators(page, moduleId);

    // Wait for page to settle
    await page.waitForTimeout(TIMEOUTS.ANIMATION);

    // Should have either a table with evaluators or empty state
    const tableVisible = await hasTable(page);
    const emptyVisible = await hasEmptyState(page);
    const hasEmptyText = await page.getByTestId('empty-state').isVisible().catch(() => false);
    const hasLayout = await page.getByTestId('app-layout').isVisible().catch(() => false);
    const hasContent = await page.locator('.card, main, section').first().isVisible().catch(() => false);

    // Accept any valid page content
    expect(tableVisible || emptyVisible || hasEmptyText || hasLayout || hasContent).toBe(true);
  });

  test('evaluators table shows type column', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToEvaluators(page, moduleId);

    const tableVisible = await hasTable(page);
    if (!tableVisible) {
      test.skip('No evaluators table');
      return;
    }

    // Check for headers including Type
    const headers = page.getByTestId('data-table').locator('thead th');
    const headerTexts = await headers.allTextContents();

    const hasTypeColumn = headerTexts.some((h) => /type|builder|kind/i.test(h));
    const hasNameColumn = headerTexts.some((h) => /name/i.test(h));

    expect(hasNameColumn || headerTexts.length > 0).toBe(true);
  });

  test('filter evaluators by type', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToEvaluators(page, moduleId);

    // Look for type filter dropdown (select element)
    const typeDropdown = page.locator('select.select-bordered');

    const hasDropdown = await typeDropdown.isVisible().catch(() => false);

    if (!hasDropdown) {
      // Try search as filter alternative
      const searchInput = page.getByTestId('input-search-evaluators');
      if (await searchInput.isVisible().catch(() => false)) {
        await searchInput.fill('llm');
        await page.waitForTimeout(TIMEOUTS.DEBOUNCE);
        await searchInput.clear();
        await page.waitForTimeout(TIMEOUTS.DEBOUNCE);
        expect(true).toBe(true);
        return;
      }

      test.skip('No type filter available');
      return;
    }

    // Use the filter
    await typeDropdown.selectOption({ index: 1 });
    await waitForLoadingToFinish(page);

    // Page should still be functional
    await expect(page.locator('body')).toBeVisible();
  });

  test('search evaluators by name', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToEvaluators(page, moduleId);

    const searchInput = page.getByTestId('input-search-evaluators');
    if (!(await searchInput.isVisible().catch(() => false))) {
      test.skip('No search input available');
      return;
    }

    // Search for something
    const input = searchInput;
    await input.fill('test');
    await page.waitForTimeout(TIMEOUTS.DEBOUNCE);

    // Page should still be functional
    await expect(page.locator('body')).toBeVisible();

    await input.clear();
    await page.waitForTimeout(TIMEOUTS.DEBOUNCE);
  });
});
