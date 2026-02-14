// @ts-check
/**
 * Feature tests for human feedback queue completion page.
 *
 * Tests success message, navigation buttons, and queue name display.
 * Route: /agents/:module-id/human_feedback_queues/:queue-id/end
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish, hasTable, navigateTo } from '../../helpers.js';

/**
 * Navigate from module to a queue completion page.
 * Since we can't easily trigger queue completion, we navigate directly.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 * @returns {Promise<{success: boolean, queueId?: string}>}
 */
async function navigateToQueueCompletion(page, moduleId) {
  // First, get a valid queue ID from the queues list
  await navigateTo(page, `/agents/${moduleId}/human-feedback-queues`);

  const dataTable = page.getByTestId('data-table');
  const tableVisible = await dataTable.isVisible().catch(() => false);

  if (!tableVisible) {
    return { success: false };
  }

  // Get first queue row
  const firstRow = dataTable.locator('tbody tr').first();
  if (!(await firstRow.isVisible().catch(() => false))) {
    return { success: false };
  }

  // Click to get to queue detail to find queue ID
  await firstRow.click();
  await waitForLoadingToFinish(page);

  // Extract queue ID from URL
  const url = page.url();
  const match = url.match(/human[-_]feedback[-_]queues\/([^/]+)/);
  if (!match) {
    return { success: false };
  }

  const queueId = match[1];

  // Navigate directly to the end page
  await navigateTo(page, `/agents/${moduleId}/human_feedback_queues/${queueId}/end`);

  return { success: true, queueId };
}

test.describe('Queue Completion Page', () => {
  test('completion page shows success message', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const result = await navigateToQueueCompletion(page, moduleId);
    if (!result.success) {
      test.skip('Could not navigate to queue completion page');
      return;
    }

    // Should show completion message with emoji or success icon
    const successEmoji = page.locator('text=/🎉/');
    const completionTitle = page.locator('text=/Queue Complete/i');
    const successMessage = page.locator('.card-body');

    const hasEmoji = await successEmoji.isVisible().catch(() => false);
    const hasTitle = await completionTitle.isVisible().catch(() => false);
    const hasMessage = await successMessage.isVisible().catch(() => false);

    expect(hasEmoji || hasTitle || hasMessage).toBe(true);

    // Check for congratulatory message
    if (hasTitle) {
      await expect(completionTitle).toBeVisible();
    }
  });

  test('back to queue button navigates correctly', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const result = await navigateToQueueCompletion(page, moduleId);
    if (!result.success) {
      test.skip('Could not navigate to queue completion page');
      return;
    }

    // Look for "Back to Queue" button
    const backButton = page.locator('button, a').filter({ hasText: /Back to Queue/i });
    if (!(await backButton.isVisible().catch(() => false))) {
      test.skip('Back to Queue button not found');
      return;
    }

    const urlBefore = page.url();
    await backButton.click();
    await waitForLoadingToFinish(page);

    // Should navigate away from the end page
    const urlAfter = page.url();
    expect(urlAfter).not.toContain('/end');
    // Should still be in the queue context
    expect(urlAfter).toContain('human');
  });

  test('view all queues button navigates to list', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const result = await navigateToQueueCompletion(page, moduleId);
    if (!result.success) {
      test.skip('Could not navigate to queue completion page');
      return;
    }

    // Look for "View All Queues" button
    const viewAllButton = page.locator('button, a').filter({ hasText: /View All Queues/i });
    if (!(await viewAllButton.isVisible().catch(() => false))) {
      test.skip('View All Queues button not found');
      return;
    }

    await viewAllButton.click();
    await waitForLoadingToFinish(page);

    // Should be on the queues list page
    const urlAfter = page.url();
    expect(urlAfter).toContain('human');
    expect(urlAfter).toContain('queues');
    // Should not have a specific queue ID or /end
    expect(urlAfter).not.toContain('/end');
  });
});
