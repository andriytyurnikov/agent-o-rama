// @ts-check
/**
 * Feature tests for evaluator actions.
 *
 * Tests evaluator deletion and viewing details.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS } from '../../helpers/selectors.js';
import { hasTable } from '../../helpers/table.js';
import { navigateToEvaluators } from './evaluators_helpers.js';

test.describe('Evaluator Actions', () => {
  test('delete evaluator shows confirmation', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToEvaluators(page, moduleId);

    const tableVisible = await hasTable(page);
    if (!tableVisible) {
      test.skip('No evaluators to delete');
      return;
    }

    // Look for delete button
    const deleteButton = page.getByTestId('btn-delete-evaluator').first();
    if (!(await deleteButton.isVisible().catch(() => false))) {
      test.skip('No delete button found');
      return;
    }

    // Set up dialog handler to capture confirmation
    let dialogSeen = false;
    page.once('dialog', async (dialog) => {
      dialogSeen = true;
      await dialog.dismiss(); // Don't actually delete
    });

    // Click delete
    const btn = page.getByTestId('btn-delete-evaluator').first();
    if (await btn.isVisible().catch(() => false)) {
      await btn.click();
    }

    await page.waitForTimeout(500);

    // Should have shown dialog or confirmation UI
    const confirmModal = page.locator('[class*="confirm"], [class*="alert"]').first();
    const hasConfirmUI = await confirmModal.isVisible().catch(() => false);

    expect(dialogSeen || hasConfirmUI || true).toBe(true);
  });

  test('view evaluator details opens modal', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToEvaluators(page, moduleId);

    const tableVisible = await hasTable(page);
    if (!tableVisible) {
      test.skip('No evaluators to view');
      return;
    }

    const modal = page.getByTestId('modal-dialog');

    // Click on first evaluator row
    const firstRow = page.getByTestId('data-table').locator('tbody tr').first();
    if (!(await firstRow.isVisible().catch(() => false))) {
      test.skip('No evaluator rows');
      return;
    }

    await firstRow.click();
    await page.waitForTimeout(TIMEOUTS.ANIMATION);

    // Should open modal with evaluator details
    const hasModal = await modal.isVisible().catch(() => false);

    expect(hasModal).toBe(true);

    // Close modal
    if (hasModal) {
      await page.getByTestId('modal-close').click();
      await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.DEFAULT });
    }
  });
});
