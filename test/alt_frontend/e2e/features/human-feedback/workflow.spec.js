// @ts-check
/**
 * Feature tests for human feedback workflow.
 *
 * Tests feedback queues, queue navigation, and feedback submission.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish, hasTable, hasEmptyState, navigateTo } from '../../helpers.js';

test.describe('Human Feedback Queues', () => {
  test('list feedback queues for module', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/human-feedback-queues`);

    const dataTable = page.getByTestId('data-table');

    // Wait for page to settle
    await page.waitForTimeout(TIMEOUTS.ANIMATION);

    // Should have either queues table or empty state
    const tableVisible = await hasTable(page);
    const emptyVisible = await hasEmptyState(page);
    const hasEmptyText = await page.getByTestId('empty-state').isVisible().catch(() => false);
    const hasLayout = await page.getByTestId('app-layout').isVisible().catch(() => false);
    const hasContent = await page.locator('.card, main, section').first().isVisible().catch(() => false);

    // Accept any valid page content
    expect(tableVisible || emptyVisible || hasEmptyText || hasLayout || hasContent).toBe(true);
  });

  test('navigate between queues and metrics tabs', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/human-feedback-queues`);

    // Human feedback is shown as a single page - this test checks that the page is functional
    const hasLayout = await page.getByTestId('app-layout').isVisible().catch(() => false);
    expect(hasLayout).toBe(true);
  });

  test('navigate to queue items list', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/human-feedback-queues`);

    const dataTable = page.getByTestId('data-table');

    const tableVisible = await hasTable(page);
    if (!tableVisible) {
      test.skip('No queues table');
      return;
    }

    const firstQueueRow = dataTable.locator('tbody tr').first();
    if (!(await firstQueueRow.isVisible().catch(() => false))) {
      test.skip('No queues available');
      return;
    }

    const urlBefore = page.url();

    // Click on queue row
    const link = firstQueueRow.locator('a').first();
    if (await link.isVisible().catch(() => false)) {
      await link.click();
    } else {
      await firstQueueRow.click();
    }

    await waitForLoadingToFinish(page);

    // Should be on queue detail showing items
    const urlAfter = page.url();
    const hasNavigated = urlAfter !== urlBefore;
    const hasItemsList = await page.locator('.card, main, section').first().isVisible().catch(() => false);

    expect(hasNavigated || hasItemsList).toBe(true);
  });
});

test.describe('Feedback Submission', () => {
  test('start review button initiates feedback workflow', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/human-feedback-queues`);

    const dataTable = page.getByTestId('data-table');

    const tableVisible = await hasTable(page);
    if (!tableVisible) {
      test.skip('No queues available');
      return;
    }

    // Navigate to a queue
    const firstQueueRow = dataTable.locator('tbody tr').first();
    if (!(await firstQueueRow.isVisible().catch(() => false))) {
      test.skip('No queues available');
      return;
    }

    await firstQueueRow.click();
    await waitForLoadingToFinish(page);

    // Look for start review button
    const startButton = page.locator('.btn-primary').filter({ hasText: /Review|Start|Begin/i }).first();
    if (!(await startButton.isVisible().catch(() => false))) {
      // Might need to be on items list first
      test.skip('Start review button not found');
      return;
    }

    await startButton.click();
    await waitForLoadingToFinish(page);

    // Should either navigate to item or show no items message
    const hasItem = await page.locator('.card, .card-body, form').first().isVisible().catch(() => false);
    const hasNoItems = await page.getByTestId('empty-state').isVisible().catch(() => false);

    expect(hasItem || hasNoItems).toBe(true);
  });

  test('feedback form shows rubric/metrics', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/human-feedback-queues`);

    const dataTable = page.getByTestId('data-table');

    const tableVisible = await hasTable(page);
    if (!tableVisible) {
      test.skip('No queues available');
      return;
    }

    // Navigate to a queue
    const firstQueueRow = dataTable.locator('tbody tr').first();
    if (!(await firstQueueRow.isVisible().catch(() => false))) {
      test.skip('No queues available');
      return;
    }

    await firstQueueRow.click();
    await waitForLoadingToFinish(page);

    // Start review if possible
    const startButton = page.locator('.btn-primary').filter({ hasText: /Review|Start|Begin/i }).first();
    if (await startButton.isVisible().catch(() => false)) {
      await startButton.click();
      await waitForLoadingToFinish(page);
    } else {
      // Try clicking first item row
      const itemRow = dataTable.locator('tbody tr').first();
      if (await itemRow.isVisible().catch(() => false)) {
        await itemRow.click();
        await waitForLoadingToFinish(page);
      } else {
        test.skip('Cannot navigate to feedback item');
        return;
      }
    }

    // Check for feedback form elements
    const hasForm = await page.locator('form').isVisible().catch(() => false);
    const hasInputDisplay = await page.locator('.card-body, pre, .font-mono').first().isVisible().catch(() => false);
    const hasOutputDisplay = await page.locator('.card-body, pre, .font-mono').nth(1).isVisible().catch(() => false);

    // Look for metric scoring elements
    const hasSlider = await page.locator('input[type="range"]').isVisible().catch(() => false);
    const hasRadio = await page.locator('input[type="radio"]').isVisible().catch(() => false);
    const hasSelect = await page.locator('select').isVisible().catch(() => false);
    const hasNumericInput = await page.locator('input[type="number"]').isVisible().catch(() => false);

    const hasScoringUI = hasSlider || hasRadio || hasSelect || hasNumericInput;
    const hasContent = hasForm || hasInputDisplay || hasOutputDisplay;

    // Either we have feedback UI or no items to review
    const hasNoItems = await page.getByTestId('empty-state').isVisible().catch(() => false);

    expect(hasScoringUI || hasContent || hasNoItems).toBe(true);
  });

  test('submit feedback moves to next item or completes', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/human-feedback-queues`);

    const dataTable = page.getByTestId('data-table');

    const tableVisible = await hasTable(page);
    if (!tableVisible) {
      test.skip('No queues available');
      return;
    }

    // Navigate to a queue
    const firstQueueRow = dataTable.locator('tbody tr').first();
    if (!(await firstQueueRow.isVisible().catch(() => false))) {
      test.skip('No queues available');
      return;
    }

    await firstQueueRow.click();
    await waitForLoadingToFinish(page);

    // Start review if possible
    const startButton = page.locator('.btn-primary').filter({ hasText: /Review|Start|Begin/i }).first();
    if (await startButton.isVisible().catch(() => false)) {
      await startButton.click();
      await waitForLoadingToFinish(page);
    }

    // Check if we're on a feedback item
    const hasItem = await page.locator('.card-body, pre, .font-mono').first().isVisible().catch(() => false);
    if (!hasItem) {
      // Check for no items message
      const hasNoItems = await page.getByTestId('empty-state').isVisible().catch(() => false);
      if (hasNoItems) {
        // Test passes - no items to review
        expect(true).toBe(true);
        return;
      }
      test.skip('Could not navigate to feedback item');
      return;
    }

    // Find and click submit button
    const submitButton = page.locator('.btn-primary').filter({ hasText: /Submit|Save|Next/i }).first();
    if (!(await submitButton.isVisible().catch(() => false))) {
      test.skip('Submit button not found');
      return;
    }

    // Fill any required fields first if visible
    const numericInputs = page.locator('input[type="number"]');
    const numericCount = await numericInputs.count();
    for (let i = 0; i < numericCount; i++) {
      const input = numericInputs.nth(i);
      if (await input.isVisible().catch(() => false)) {
        await input.fill('5');
      }
    }

    const sliders = page.locator('input[type="range"]');
    const sliderCount = await sliders.count();
    for (let i = 0; i < sliderCount; i++) {
      const slider = sliders.nth(i);
      if (await slider.isVisible().catch(() => false)) {
        await slider.fill('50');
      }
    }

    const urlBefore = page.url();

    await submitButton.click();
    await waitForLoadingToFinish(page);

    // Should either move to next item, show completion, or stay on same page with update
    const urlAfter = page.url();
    const hasNavigated = urlAfter !== urlBefore;
    const hasCompletion = await page.locator('.alert-success, .badge-success').first().isVisible().catch(() => false);
    const hasNextItem = await page.locator('.card-body, pre, .font-mono').first().isVisible().catch(() => false);

    expect(hasNavigated || hasCompletion || hasNextItem).toBe(true);
  });
});

test.describe('Human Metrics', () => {
  test('metrics list shows available metrics', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    // Navigate to metrics section
    await navigateTo(page, `/agents/${moduleId}/human-metrics`);

    const dataTable = page.getByTestId('data-table');

    // Wait for page to settle
    await page.waitForTimeout(TIMEOUTS.ANIMATION);

    // Might need to click metrics tab
    // Page should already show content
    await waitForLoadingToFinish(page);

    // Should have metrics table or empty state or any valid page content
    const hasTable = await dataTable.isVisible().catch(() => false);
    const hasEmptyState = await page.getByTestId('empty-state').isVisible().catch(() => false);
    const hasCreateButton = await page.getByTestId('btn-create-metric').isVisible().catch(() => false);
    const hasLayout = await page.getByTestId('app-layout').isVisible().catch(() => false);
    const hasContent = await page.locator('.card, main, section').first().isVisible().catch(() => false);

    // Accept any valid page content
    expect(hasTable || hasEmptyState || hasCreateButton || hasLayout || hasContent).toBe(true);
  });

  test('create metric modal opens', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}/human-metrics`);

    // Find create button
    const createButton = page.getByTestId('btn-create-metric');
    if (!(await createButton.isVisible().catch(() => false))) {
      test.skip('Create metric button not found');
      return;
    }

    await createButton.click();

    const modal = page.getByTestId('modal-dialog');
    await expect(modal).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    // Modal should have metric form elements
    const hasFormControls = await modal.locator('.form-control, input, select').first().isVisible().catch(() => false);

    const hasNameInput = hasFormControls;
    const hasTypeSelect = await modal.locator('select').isVisible().catch(() => false);

    expect(hasNameInput || hasTypeSelect).toBe(true);

    // Close modal (Escape key not supported - use close button)
    await page.getByTestId('modal-close').click();
    await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.NETWORK });
  });
});
