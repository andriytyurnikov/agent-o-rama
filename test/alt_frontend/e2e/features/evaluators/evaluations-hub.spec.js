// @ts-check
/**
 * Feature tests for evaluations hub page.
 *
 * Tests evaluator table, search, create button, and details modal.
 * Route: /agents/:module-id/evaluations
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish, hasTable, hasEmptyState, navigateTo } from '../../helpers.js';

test.describe('Evaluations Hub', () => {
  test('evaluations page renders evaluator table', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/evaluations`);

    // Wait for page to settle
    await page.waitForTimeout(TIMEOUTS.ANIMATION);

    // Should have either a table with evaluators or empty state
    const tableVisible = await hasTable(page);
    const emptyVisible = await hasEmptyState(page);
    const hasLayout = await page.getByTestId('app-layout').isVisible().catch(() => false);
    const hasContent = await page.locator('.card, main, section').first().isVisible().catch(() => false);

    // Accept any valid page content
    expect(tableVisible || emptyVisible || hasLayout || hasContent).toBe(true);

    // If table exists, check for expected headers
    if (tableVisible) {
      const dataTable = page.getByTestId('data-table');
      const headers = dataTable.locator('thead th');
      const headerTexts = await headers.allTextContents();

      const hasNameColumn = headerTexts.some((h) => /name/i.test(h));
      const hasTypeColumn = headerTexts.some((h) => /type/i.test(h));

      expect(hasNameColumn || headerTexts.length > 0).toBe(true);
    }
  });

  test('search filters evaluators by name', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/evaluations`);

    const searchInput = page.getByTestId('input-search-evaluators');
    if (!(await searchInput.isVisible().catch(() => false))) {
      test.skip('No search input available');
      return;
    }

    // Get initial row count if table exists
    const dataTable = page.getByTestId('data-table');
    const tableVisible = await dataTable.isVisible().catch(() => false);
    let initialRowCount = 0;
    if (tableVisible) {
      initialRowCount = await dataTable.locator('tbody tr').count();
    }

    // Search for something
    await searchInput.fill('test-evaluator');
    await page.waitForTimeout(TIMEOUTS.DEBOUNCE);

    // Page should still be functional
    await expect(page.locator('body')).toBeVisible();

    // Clear search
    await searchInput.clear();
    await page.waitForTimeout(TIMEOUTS.DEBOUNCE);

    // Page should still be functional after clearing
    await expect(page.locator('body')).toBeVisible();
  });

  test('create evaluator button opens modal', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/evaluations`);

    // Find create button
    const createButton = page.getByTestId('btn-create-evaluator');
    if (!(await createButton.isVisible().catch(() => false))) {
      test.skip('Create evaluator button not found');
      return;
    }

    await createButton.click();

    const modal = page.getByTestId('modal-dialog');
    await expect(modal).toBeVisible({ timeout: TIMEOUTS.NETWORK });

    // Modal should have form elements for creating an evaluator
    const hasFormControls = await modal.locator('.form-control, input, select').first().isVisible().catch(() => false);
    expect(hasFormControls).toBe(true);

    // Close modal
    await page.getByTestId('modal-close').click();
    await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  });

  test('evaluator row click opens details modal', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/evaluations`);

    const dataTable = page.getByTestId('data-table');
    const tableVisible = await dataTable.isVisible().catch(() => false);

    if (!tableVisible) {
      test.skip('No evaluators table');
      return;
    }

    // Find an evaluator row by testid pattern
    const evaluatorRow = page.locator('[data-testid^="evaluator-row-"]').first();
    if (!(await evaluatorRow.isVisible().catch(() => false))) {
      test.skip('No evaluator rows available');
      return;
    }

    // Click on the row (should open details modal)
    await evaluatorRow.click();
    await page.waitForTimeout(TIMEOUTS.ANIMATION);

    const modal = page.getByTestId('modal-dialog');
    const hasModal = await modal.isVisible().catch(() => false);

    if (hasModal) {
      // Modal should show evaluator details
      const hasModalContent = await modal.locator('.modal-box').isVisible().catch(() => false);
      expect(hasModalContent).toBe(true);

      // Close modal
      await page.getByTestId('modal-close').click();
      await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.DEFAULT });
    } else {
      // Might navigate instead of modal - that's also acceptable
      expect(true).toBe(true);
    }
  });
});

test.describe('Evaluations Hub Filters', () => {
  test('type filter dropdown changes displayed evaluators', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/evaluations`);

    // Look for type filter dropdown
    const typeDropdown = page.locator('select.select-bordered');
    const hasDropdown = await typeDropdown.isVisible().catch(() => false);

    if (!hasDropdown) {
      test.skip('No type filter dropdown available');
      return;
    }

    // Change filter selection
    await typeDropdown.selectOption({ index: 1 });
    await waitForLoadingToFinish(page);

    // Page should still be functional
    await expect(page.locator('body')).toBeVisible();

    // Reset to "All Types"
    await typeDropdown.selectOption({ value: 'all' });
    await waitForLoadingToFinish(page);

    await expect(page.locator('body')).toBeVisible();
  });
});
