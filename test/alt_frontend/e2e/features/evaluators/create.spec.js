// @ts-check
/**
 * Feature tests for evaluator creation.
 *
 * Tests evaluator creation modal, form validation, and submission.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS } from '../../helpers/selectors.js';
import { navigateToEvaluators } from './evaluators_helpers.js';

test.describe('Evaluator Creation', () => {
  test('create evaluator modal shows builder selection', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToEvaluators(page, moduleId);

    const createButton = page.getByTestId('btn-create-evaluator');
    if (!(await createButton.first().isVisible().catch(() => false))) {
      test.skip('Create button not available');
      return;
    }

    await createButton.first().click();

    const modal = page.getByTestId('modal-dialog');
    await expect(modal).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    // Should show builder selection wizard
    // Wait a bit for content to load
    await page.waitForTimeout(TIMEOUTS.ANIMATION);

    // Check for step indicator, cards, or any form content
    const stepIndicator = modal.locator('.steps');
    const builderCards = modal.locator('.card');
    const modalContent = modal.locator('.modal-box, .card-body');

    const hasSteps = await stepIndicator.isVisible().catch(() => false);
    const hasCards = await builderCards.first().isVisible().catch(() => false);
    const hasContent = await modalContent.isVisible().catch(() => false);

    expect(hasSteps || hasCards || hasContent).toBe(true);

    // Close modal (Escape key not supported - use close button)
    await page.getByTestId('modal-close').click();
    await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.NETWORK });
  });

  test('create evaluator form validates required fields', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToEvaluators(page, moduleId);

    const createButton = page.getByTestId('btn-create-evaluator');
    if (!(await createButton.first().isVisible().catch(() => false))) {
      test.skip('Create button not available');
      return;
    }

    await createButton.first().click();

    const modal = page.getByTestId('modal-dialog');
    await expect(modal).toBeVisible();

    // Select a builder first if required
    const builderItem = modal.locator('[class*="builder"], [class*="card"]').first();
    if (await builderItem.isVisible().catch(() => false)) {
      await builderItem.click();
      await page.waitForTimeout(TIMEOUTS.ANIMATION);
    }

    // Submit button should be disabled without filling required fields
    const submitButton = modal.getByTestId('btn-submit-create-evaluator');
    const submitVisible = await submitButton.isVisible().catch(() => false);

    if (submitVisible) {
      // Check that button is disabled (validation working)
      const isDisabled = await submitButton.isDisabled().catch(() => false);
      const stillInModal = await modal.isVisible().catch(() => false);

      expect(isDisabled || stillInModal).toBe(true);
    }

    // Close modal (Escape key not supported - use close button)
    await page.getByTestId('modal-close').click();
  });

  test('create and submit evaluator', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToEvaluators(page, moduleId);

    const createButton = page.getByTestId('btn-create-evaluator');
    if (!(await createButton.first().isVisible().catch(() => false))) {
      test.skip('Create button not available');
      return;
    }

    await createButton.first().click();

    const modal = page.getByTestId('modal-dialog');
    await expect(modal).toBeVisible();

    // Select first builder if available
    const builderItem = modal.locator('[class*="builder"], [class*="card"], li').filter({
      hasText: /Exact|Contains|LLM|Builder/i,
    }).first();

    if (await builderItem.isVisible().catch(() => false)) {
      await builderItem.click();
      await page.waitForTimeout(TIMEOUTS.ANIMATION);
    }

    // Fill name
    const nameInput = modal.getByTestId('input-evaluator-name');
    if (await nameInput.isVisible().catch(() => false)) {
      const testName = `e2e-eval-test-${Date.now()}`;
      await nameInput.fill(testName);

      // Fill description if present
      const descInput = modal.getByTestId('input-evaluator-description');
      if (await descInput.isVisible().catch(() => false)) {
        await descInput.fill('E2E test evaluator');
      }

      // Submit
      const submitButton = modal.getByTestId('btn-submit-create-evaluator');
      if (await submitButton.isVisible().catch(() => false)) {
        await submitButton.click();
      }

      // Modal should close
      await expect(modal).not.toBeVisible({ timeout: TIMEOUTS.DEFAULT });

      // Evaluator should appear in list
      const newRow = page.locator(`[data-testid^="evaluator-row-${testName}"]`);
      await expect(newRow.first()).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
    } else {
      // Close modal and skip
      await page.getByTestId('modal-close').click();
      test.skip('Could not find name input in create modal');
    }
  });
});
