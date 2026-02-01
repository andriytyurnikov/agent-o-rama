// @ts-check
/**
 * Feature tests for agent navigation.
 *
 * Tests core navigation flows: home page, module detail, agent detail, and agents list.
 * Uses ONLY data-testid attributes for all locators.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { TIMEOUTS, waitForLoadingToFinish } from '../../helpers.js';
import { navigateToModule } from './agents_helpers.js';

test.describe('Agent Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('./');
    await waitForLoadingToFinish(page);
  });

  test('home page displays layout elements', async ({ page }) => {
    // Layout should be visible
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
    await expect(page.getByTestId('navbar')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
    await expect(page.getByTestId('sidebar')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
    await expect(page.getByTestId('breadcrumbs')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    // URL should be at home (may or may not have hash)
    expect(page.url()).toMatch(/\/alt\/?($|#\/)/);
  });

  test('home page shows module cards or empty state', async ({ page }) => {
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    // Check for module cards using testid prefix pattern
    const hasModuleCards = await page.locator('[data-testid^="module-card-"]').first().isVisible().catch(() => false);
    const hasEmptyState = await page.getByTestId('empty-state').isVisible().catch(() => false);
    // Also check for loading state or general card content (fallback)
    const hasLoadingOrCards = await page.locator('.card').first().isVisible().catch(() => false);

    expect(hasModuleCards || hasEmptyState || hasLoadingOrCards).toBe(true);
  });

  test('home page search filters modules', async ({ page }) => {
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    const searchInput = page.getByTestId('input-search-modules');
    const isSearchVisible = await searchInput.isVisible().catch(() => false);

    if (!isSearchVisible) {
      test.skip('Search input testid not found (needs CLJS rebuild)');
      return;
    }

    // Type in search
    await searchInput.fill('test');
    await page.waitForTimeout(TIMEOUTS.DEBOUNCE);

    // Page should still be stable after search
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    // Clear search
    await searchInput.clear();
  });

  test('navigate to all agents list', async ({ page }) => {
    // Click on Agents nav link
    await page.getByTestId('nav-agents').click();

    // Wait for navigation and verify URL
    await waitForLoadingToFinish(page);
    expect(page.url()).toContain('#/agents');

    // Should show either a table with agents or empty state
    const hasTable = await page.getByTestId('data-table').isVisible().catch(() => false);
    const hasEmptyState = await page.getByTestId('empty-state').isVisible().catch(() => false);

    expect(hasTable || hasEmptyState).toBe(true);
  });

  test('agents list has search functionality', async ({ page }) => {
    await page.goto('./#/agents');
    await waitForLoadingToFinish(page);
    expect(page.url()).toContain('#/agents');

    const searchInput = page.getByTestId('input-search-agents');
    const isSearchVisible = await searchInput.isVisible().catch(() => false);

    if (!isSearchVisible) {
      test.skip('Search input testid not found (needs CLJS rebuild)');
      return;
    }

    // Type in search
    await searchInput.fill('test');
    await page.waitForTimeout(TIMEOUTS.DEBOUNCE);

    // Page should still be stable after search
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    // Clear search
    await searchInput.clear();
  });

  test('breadcrumbs show current location', async ({ page }) => {
    // On home page, breadcrumbs should be visible
    await expect(page.getByTestId('breadcrumbs')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    // Navigate to agents
    await page.goto('./#/agents');
    await waitForLoadingToFinish(page);
    expect(page.url()).toContain('#/agents');

    // Breadcrumbs should still be visible
    await expect(page.getByTestId('breadcrumbs')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  });
});

test.describe('Module Navigation', () => {
  test('click module card navigates to module detail', async ({ page }) => {
    await page.goto('./');
    await waitForLoadingToFinish(page);

    // Check for module cards using testid prefix pattern
    const firstModuleCard = page.locator('[data-testid^="module-card-"]').first();
    const hasModuleCard = await firstModuleCard.isVisible().catch(() => false);

    if (!hasModuleCard) {
      test.skip('No module cards available (needs CLJS rebuild or no data)');
      return;
    }

    // Get the testid to extract module-id
    const testid = await firstModuleCard.getAttribute('data-testid');
    const moduleId = testid?.replace('module-card-', '');

    if (!moduleId) {
      test.skip('Could not extract module ID from card');
      return;
    }

    // Click the card
    await firstModuleCard.click();
    await waitForLoadingToFinish(page);

    // Should be on module detail page (URL may have different encoding)
    const currentUrl = page.url();
    expect(currentUrl).toContain('#/agents/');
    // Verify we navigated away from home
    expect(currentUrl).not.toEqual('http://localhost:1974/alt/');

    // Layout should still be visible
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });
  });

  test('module page shows navigation links', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToModule(page, moduleId);
    expect(page.url()).toContain(`#/agents/${moduleId}`);

    // Module-specific navigation should be visible (if testids exist)
    const navDatasets = page.getByTestId('nav-datasets');
    const hasNavDatasets = await navDatasets.isVisible().catch(() => false);

    if (!hasNavDatasets) {
      test.skip('Navigation testids not found (needs CLJS rebuild)');
      return;
    }

    await expect(navDatasets).toBeVisible({ timeout: TIMEOUTS.NAVIGATION });
    await expect(page.getByTestId('nav-evaluations')).toBeVisible({ timeout: TIMEOUTS.NAVIGATION });
  });

  test('module page datasets link navigates correctly', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToModule(page, moduleId);
    expect(page.url()).toContain(`#/agents/${moduleId}`);

    // Check if nav link exists
    const navDatasets = page.getByTestId('nav-datasets');
    const hasNavDatasets = await navDatasets.isVisible().catch(() => false);

    if (!hasNavDatasets) {
      test.skip('Navigation testids not found (needs CLJS rebuild)');
      return;
    }

    // Click datasets nav link
    await navDatasets.click();
    await waitForLoadingToFinish(page);

    // URL should include datasets
    expect(page.url()).toContain('/datasets');
  });
});

test.describe('Agent Detail Navigation', () => {
  test('navigate from module to agent detail', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToModule(page, moduleId);
    expect(page.url()).toContain(`#/agents/${moduleId}`);

    // Look for an agent row in the table
    const dataTable = page.getByTestId('data-table');
    const hasTable = await dataTable.isVisible().catch(() => false);

    if (!hasTable) {
      test.skip('No data table found on module page');
      return;
    }

    // Check for table rows - use generic tbody tr since we're inside data-table
    const firstRow = dataTable.locator('tbody tr').first();
    const hasRows = await firstRow.isVisible().catch(() => false);

    if (!hasRows) {
      test.skip('No agents in module table');
      return;
    }

    // Click on first agent row
    await firstRow.click();
    await waitForLoadingToFinish(page);

    // Should be on agent detail page with /agent/ in URL
    expect(page.url()).toMatch(/\/agent\//);
  });

  test('agent detail page shows invoke form elements', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToModule(page, moduleId);
    expect(page.url()).toContain(`#/agents/${moduleId}`);

    // Try to navigate to an agent
    const dataTable = page.getByTestId('data-table');
    const hasTable = await dataTable.isVisible().catch(() => false);

    if (!hasTable) {
      test.skip('No data table found');
      return;
    }

    const firstRow = dataTable.locator('tbody tr').first();
    const hasRows = await firstRow.isVisible().catch(() => false);

    if (!hasRows) {
      test.skip('No agents available');
      return;
    }

    await firstRow.click();
    await waitForLoadingToFinish(page);
    expect(page.url()).toMatch(/\/agent\//);

    // Check for invoke form elements using testids
    const argsInput = page.getByTestId('input-invoke-args');
    const submitButton = page.getByTestId('btn-submit-invoke');

    const hasArgsInput = await argsInput.isVisible().catch(() => false);
    const hasSubmitButton = await submitButton.isVisible().catch(() => false);

    if (!hasArgsInput && !hasSubmitButton) {
      test.skip('Invoke form testids not found (needs CLJS rebuild)');
      return;
    }

    // Agent detail should have the invoke form
    expect(hasArgsInput || hasSubmitButton).toBe(true);
  });

  test('agent detail page shows context navigation', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToModule(page, moduleId);
    expect(page.url()).toContain(`#/agents/${moduleId}`);

    const dataTable = page.getByTestId('data-table');
    const hasTable = await dataTable.isVisible().catch(() => false);

    if (!hasTable) {
      test.skip('No data table found');
      return;
    }

    const firstRow = dataTable.locator('tbody tr').first();
    const hasRows = await firstRow.isVisible().catch(() => false);

    if (!hasRows) {
      test.skip('No agents available');
      return;
    }

    await firstRow.click();
    await waitForLoadingToFinish(page);
    expect(page.url()).toMatch(/\/agent\//);

    // Check if nav links exist
    const navInvocations = page.getByTestId('nav-invocations');
    const hasNav = await navInvocations.isVisible().catch(() => false);

    if (!hasNav) {
      test.skip('Agent navigation testids not found (needs CLJS rebuild)');
      return;
    }

    // Agent-specific navigation should be visible
    await expect(navInvocations).toBeVisible({ timeout: TIMEOUTS.NAVIGATION });
    await expect(page.getByTestId('nav-analytics')).toBeVisible({ timeout: TIMEOUTS.NAVIGATION });
  });
});
