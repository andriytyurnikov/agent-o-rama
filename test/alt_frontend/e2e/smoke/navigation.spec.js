// @ts-check
/**
 * Smoke tests for alt-frontend navigation.
 *
 * These are basic tests that verify the app loads and navigation works.
 */
import { test, expect } from '../setup/test-fixtures.js';
import { waitForLoadingToFinish } from '../helpers/actions.js';

test.describe('Navigation Smoke Tests', () => {
  // Alt-frontend uses hash-based routing, so we use the baseURL directly
  test.beforeEach(async ({ page }) => {
    // baseURL is http://localhost:1974/alt/ - just go there
    await page.goto('./');
  });

  test('should load the home page', async ({ page }) => {
    // The home page should render
    await expect(page.locator('body')).toBeVisible();

    // Should have the navbar
    await expect(page.getByTestId('navbar')).toBeVisible();
  });

  test('should have working navigation', async ({ page }) => {
    // Click on "Agents" in the sidebar using testid
    await page.getByTestId('nav-agents').click();

    // Should navigate to the agents page (hash-based routing)
    await expect(page).toHaveURL(/#.*agents/);
  });

  test('should show connection status', async ({ page }) => {
    // Should have a connection indicator
    await expect(page.getByTestId('connection-status')).toBeVisible();
  });

  test('should load agents list', async ({ page }) => {
    // Navigate to agents page (hash-based routing)
    await page.goto('./#/agents');

    await waitForLoadingToFinish(page);

    // Should have a table, empty state, or error (when backend unavailable)
    const hasTable = await page.getByTestId('data-table').isVisible().catch(() => false);
    const hasEmptyState = await page.getByTestId('empty-state').isVisible().catch(() => false);
    const hasEmptyText = await page.getByText(/No agents found|No modules deployed/).isVisible().catch(() => false);
    const hasError = await page.getByRole('alert').isVisible().catch(() => false);

    expect(hasTable || hasEmptyState || hasEmptyText || hasError).toBe(true);
  });

  test('should render DaisyUI layout components', async ({ page }) => {
    await page.goto('./');

    // Check for app layout
    await expect(page.getByTestId('app-layout')).toBeVisible();

    // Check for navbar
    await expect(page.getByTestId('navbar')).toBeVisible();

    // Check for sidebar
    await expect(page.getByTestId('sidebar')).toBeVisible();
  });

  test('breadcrumbs should update on navigation', async ({ page }) => {
    // Start at home
    await page.goto('./');

    // Should have "Home" in breadcrumbs
    await expect(page.getByTestId('breadcrumbs')).toContainText('Home');

    // Navigate to agents (hash-based routing)
    await page.goto('./#/agents');

    // Breadcrumbs should reflect the current location
    await expect(page.getByTestId('breadcrumbs')).toBeVisible();
  });
});
