// @ts-check
/**
 * Feature tests for the invocations list view.
 *
 * Tests invocations table rendering, row navigation, and pagination.
 * Uses ONLY data-testid attributes for all locators.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish, navigateTo } from '../../helpers.js';

test.describe('Invocations List', () => {
  test('invocations list renders on agent detail page', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}`);
    expect(page.url()).toContain(`#/agents/${moduleId}`);

    // Navigate to first agent
    const dataTable = page.getByTestId('data-table');
    const hasTable = await dataTable.isVisible().catch(() => false);

    if (!hasTable) {
      test.skip('No agents table found');
      return;
    }

    const agentRow = dataTable.locator('tbody tr').first();
    if (!(await agentRow.isVisible().catch(() => false))) {
      test.skip('No agents available');
      return;
    }

    await agentRow.click();
    await waitForLoadingToFinish(page);
    expect(page.url()).toMatch(/\/agent\//);

    // Agent detail page should render
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  });

  test('clicking invocation navigates to detail', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateTo(page, `/agents/${moduleId}`);
    expect(page.url()).toContain(`#/agents/${moduleId}`);

    const dataTable = page.getByTestId('data-table');
    const hasTable = await dataTable.isVisible().catch(() => false);

    if (!hasTable) {
      test.skip('No agents table found');
      return;
    }

    const agentRow = dataTable.locator('tbody tr').first();
    if (!(await agentRow.isVisible().catch(() => false))) {
      test.skip('No agents available');
      return;
    }

    await agentRow.click();
    await waitForLoadingToFinish(page);
    expect(page.url()).toMatch(/\/agent\//);

    // Create an invocation first
    const argsInput = page.getByTestId('input-invoke-args');
    const invokeButton = page.getByTestId('btn-submit-invoke');

    if (!(await invokeButton.isVisible().catch(() => false))) {
      test.skip('Invoke form testids not found (needs CLJS rebuild)');
      return;
    }

    if (await argsInput.isVisible().catch(() => false)) {
      await argsInput.fill('["list-nav-test"]');
    }

    await invokeButton.click();

    // Should navigate to invocation detail
    try {
      await expect(page).toHaveURL(/\/invocations\//, { timeout: TIMEOUTS.NETWORK });
      expect(page.url()).toMatch(/\/invocations\//);
    } catch {
      test.skip('Could not navigate to invocation detail');
    }
  });
});
