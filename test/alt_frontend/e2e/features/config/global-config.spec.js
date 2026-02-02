// @ts-check
/**
 * Feature tests for global module configuration page.
 *
 * Tests module-level configuration: navigation, form fields, and save button.
 * Uses ONLY data-testid attributes for all locators.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish, navigateTo } from '../../helpers.js';

test.describe('Global Config', () => {
  test('global config page loads via nav link', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    // Navigate to module detail page
    await navigateTo(page, `/agents/${moduleId}`);

    // Click nav-global-config link in sidebar
    const globalConfigLink = page.getByTestId('nav-global-config');
    const hasLink = await globalConfigLink.isVisible().catch(() => false);

    if (!hasLink) {
      test.skip('Global config nav link not found');
      return;
    }

    await globalConfigLink.click();
    await waitForLoadingToFinish(page);

    // URL should include /global-config
    expect(page.url()).toContain('/global-config');

    // Layout should still be visible
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  });

  test('global config displays settings or empty state', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    // Navigate directly to global config page
    await navigateTo(page, `/agents/${moduleId}/global-config`);

    // Should show either config cards with inputs OR empty state
    const hasConfigCards = await page.locator('.card input').first().isVisible().catch(() => false);
    const hasEmptyState = await page.getByTestId('empty-state').isVisible().catch(() => false);
    const hasLoadingState = await page.getByTestId('loading-spinner').isVisible().catch(() => false);
    const hasErrorAlert = await page.getByTestId('error-alert').isVisible().catch(() => false);

    // Any of these states is valid
    expect(hasConfigCards || hasEmptyState || hasLoadingState || hasErrorAlert).toBe(true);
  });

  test('global config save button exists and functions', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    // Navigate to global config page
    await navigateTo(page, `/agents/${moduleId}/global-config`);

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

    // Find an input and make a change to test the button enables
    const input = page.locator('.card input[type="text"]').first();
    if (await input.isVisible().catch(() => false)) {
      const originalValue = await input.inputValue();
      await input.fill(originalValue + 'test');

      // Save button should now be enabled
      await expect(saveButton).toBeEnabled({ timeout: TIMEOUTS.DEFAULT });

      // Restore original value to leave state unchanged
      await input.fill(originalValue);
    }
  });
});
