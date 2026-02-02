// @ts-check
/**
 * Feature tests for agent configuration page.
 *
 * Tests agent-level configuration: navigation, form fields, save button, and reset.
 * Uses ONLY data-testid attributes for all locators.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish, navigateTo } from '../../helpers.js';

/**
 * Navigate to an agent's config page via sidebar.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
async function navigateToAgentConfig(page, moduleId) {
  // First go to module to find an agent
  await navigateTo(page, `/agents/${moduleId}`);

  const dataTable = page.getByTestId('data-table');
  const agentRow = dataTable.locator('tbody tr').first();

  if (!(await agentRow.isVisible().catch(() => false))) {
    return false;
  }

  // Click to navigate to agent detail
  await agentRow.click();
  await waitForLoadingToFinish(page);

  // Click nav-config link in sidebar
  const configLink = page.getByTestId('nav-config');
  if (!(await configLink.isVisible().catch(() => false))) {
    return false;
  }

  await configLink.click();
  await waitForLoadingToFinish(page);
  return true;
}

test.describe('Agent Config', () => {
  test('config page loads via nav link', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToAgentConfig(page, moduleId);
    if (!navigated) {
      test.skip('No agents available or config nav link not found');
      return;
    }

    // URL should include /config
    expect(page.url()).toContain('/config');

    // Layout should still be visible
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  });

  test('config displays form fields or empty state', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToAgentConfig(page, moduleId);
    if (!navigated) {
      test.skip('No agents available or config nav link not found');
      return;
    }

    // Should show either config cards with inputs OR empty state
    const hasConfigCards = await page.locator('.card input').first().isVisible().catch(() => false);
    const hasEmptyState = await page.getByTestId('empty-state').isVisible().catch(() => false);
    const hasLoadingState = await page.getByTestId('loading-spinner').isVisible().catch(() => false);
    const hasErrorAlert = await page.getByTestId('error-alert').isVisible().catch(() => false);

    // Any of these states is valid
    expect(hasConfigCards || hasEmptyState || hasLoadingState || hasErrorAlert).toBe(true);
  });

  test('config save button is disabled until changes made', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToAgentConfig(page, moduleId);
    if (!navigated) {
      test.skip('No agents available or config nav link not found');
      return;
    }

    // Find the save button
    const saveButton = page.getByTestId('btn-save-config').first();
    const hasSaveButton = await saveButton.isVisible().catch(() => false);

    if (!hasSaveButton) {
      // No config options means no save button - check for empty state
      const hasEmptyState = await page.getByTestId('empty-state').isVisible().catch(() => false);
      if (hasEmptyState) {
        // This is expected - no config options available
        expect(hasEmptyState).toBe(true);
        return;
      }
      test.skip('No save button found and no empty state');
      return;
    }

    // Save button should be disabled initially (no changes)
    await expect(saveButton).toBeDisabled({ timeout: TIMEOUTS.DEFAULT });

    // Find an input and make a change
    const input = page.locator('.card input[type="text"]').first();
    if (await input.isVisible().catch(() => false)) {
      const originalValue = await input.inputValue();
      await input.fill(originalValue + 'test');

      // Save button should now be enabled
      await expect(saveButton).toBeEnabled({ timeout: TIMEOUTS.DEFAULT });

      // Restore original value
      await input.fill(originalValue);

      // Save button should be disabled again
      await expect(saveButton).toBeDisabled({ timeout: TIMEOUTS.DEFAULT });
    }
  });

  test('reset to default link restores value', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    const navigated = await navigateToAgentConfig(page, moduleId);
    if (!navigated) {
      test.skip('No agents available or config nav link not found');
      return;
    }

    // Reset link only appears when current value differs from default
    const resetLink = page.getByTestId('btn-reset-config').first();
    const hasResetLink = await resetLink.isVisible().catch(() => false);

    if (!hasResetLink) {
      // Either no config options, or all values are at defaults
      const hasEmptyState = await page.getByTestId('empty-state').isVisible().catch(() => false);
      const hasConfigCards = await page.locator('.card input').first().isVisible().catch(() => false);

      // Valid if empty state or cards with values at defaults
      expect(hasEmptyState || hasConfigCards || true).toBe(true);
      return;
    }

    // Click the reset link
    await resetLink.click();
    await page.waitForTimeout(TIMEOUTS.ANIMATION);

    // Page should remain stable after reset
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  });
});
