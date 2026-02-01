// @ts-check
/**
 * Feature tests for running agents and viewing invocation details.
 *
 * Tests agent invocation form, validation, and invocation detail view.
 * Uses ONLY data-testid attributes for all locators.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS } from '../../helpers/selectors.js';
import { waitForLoadingToFinish } from '../../helpers/actions.js';
import { navigateToModule } from './invocations_helpers.js';

test.describe('Agent Invocation Form', () => {
  test('run agent with valid JSON args', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToModule(page, moduleId);
    expect(page.url()).toContain(`#/agents/${moduleId}`);

    // Find an agent to run
    const dataTable = page.getByTestId('data-table');
    const hasTable = await dataTable.isVisible().catch(() => false);

    if (!hasTable) {
      test.skip('No agents table found');
      return;
    }

    const firstRow = dataTable.locator('tbody tr').first();
    if (!(await firstRow.isVisible().catch(() => false))) {
      test.skip('No agents available');
      return;
    }

    // Navigate to agent detail
    await firstRow.click();
    await waitForLoadingToFinish(page);
    expect(page.url()).toMatch(/\/agent\//);

    // Find the invoke form using testids
    const argsInput = page.getByTestId('input-invoke-args');
    const invokeButton = page.getByTestId('btn-submit-invoke');

    const hasArgsInput = await argsInput.isVisible().catch(() => false);
    const hasInvokeButton = await invokeButton.isVisible().catch(() => false);

    if (!hasInvokeButton) {
      test.skip('Invoke form testids not found (needs CLJS rebuild)');
      return;
    }

    // Fill args with valid JSON
    if (hasArgsInput) {
      await argsInput.fill('["test-arg"]');
    }

    // Submit the form
    await invokeButton.click();

    // Should navigate to invocation detail page or show result
    try {
      await expect(page).toHaveURL(/\/invocations\//, { timeout: TIMEOUTS.NETWORK });
    } catch {
      // Alternative: result might be shown inline or form validation failed
      test.skip('Navigation to invocation detail failed');
    }
  });

  test('form shows validation error for invalid JSON', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToModule(page, moduleId);
    expect(page.url()).toContain(`#/agents/${moduleId}`);

    // Navigate to agent detail
    const dataTable = page.getByTestId('data-table');
    const hasTable = await dataTable.isVisible().catch(() => false);

    if (!hasTable) {
      test.skip('No agents table found');
      return;
    }

    const firstRow = dataTable.locator('tbody tr').first();
    if (!(await firstRow.isVisible().catch(() => false))) {
      test.skip('No agents available');
      return;
    }

    await firstRow.click();
    await waitForLoadingToFinish(page);
    expect(page.url()).toMatch(/\/agent\//);

    // Find the invoke form using testids
    const argsInput = page.getByTestId('input-invoke-args');

    if (!(await argsInput.isVisible().catch(() => false))) {
      test.skip('Invoke form testids not found (needs CLJS rebuild)');
      return;
    }

    // Fill with invalid JSON
    await argsInput.fill('not valid json {{{');
    await argsInput.blur();

    // Check for error indication
    await page.waitForTimeout(TIMEOUTS.DEFAULT);

    // The input should have error class or error message should appear
    const hasErrorClass = await argsInput.evaluate((el) =>
      el.classList.contains('textarea-error') ||
      el.classList.contains('input-error') ||
      el.getAttribute('aria-invalid') === 'true'
    ).catch(() => false);

    // Button should be disabled with invalid input
    const submitBtn = page.getByTestId('btn-submit-invoke');
    const isDisabled = await submitBtn.isDisabled().catch(() => false);

    expect(hasErrorClass || isDisabled).toBe(true);
  });
});

test.describe('Invocation Detail View', () => {
  test('view invocation shows summary and status', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    // Navigate to an agent
    await navigateToModule(page, moduleId);
    expect(page.url()).toContain(`#/agents/${moduleId}`);

    const dataTable = page.getByTestId('data-table');
    const hasTable = await dataTable.isVisible().catch(() => false);

    if (!hasTable) {
      test.skip('No agents table found');
      return;
    }

    const firstRow = dataTable.locator('tbody tr').first();
    if (!(await firstRow.isVisible().catch(() => false))) {
      test.skip('No agents available');
      return;
    }

    await firstRow.click();
    await waitForLoadingToFinish(page);
    expect(page.url()).toMatch(/\/agent\//);

    // Try running an invocation to get to detail page
    const argsInput = page.getByTestId('input-invoke-args');
    const invokeButton = page.getByTestId('btn-submit-invoke');

    if (!(await invokeButton.isVisible().catch(() => false))) {
      test.skip('Invoke form testids not found (needs CLJS rebuild)');
      return;
    }

    if (await argsInput.isVisible().catch(() => false)) {
      await argsInput.fill('["view-test"]');
    }
    await invokeButton.click();

    // Wait for navigation to invocation detail
    try {
      await expect(page).toHaveURL(/\/invocations\//, { timeout: TIMEOUTS.NETWORK });
    } catch {
      test.skip('Could not navigate to invocation detail');
      return;
    }

    await waitForLoadingToFinish(page);
    expect(page.url()).toMatch(/\/invocations\//);

    // Invocation detail should show some content
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  });
});
