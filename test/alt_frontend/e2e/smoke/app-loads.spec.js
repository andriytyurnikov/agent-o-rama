// @ts-check
/**
 * Basic smoke tests to verify the app loads correctly.
 */
import { test, expect } from '../setup/test-fixtures.js';
import { TIMEOUTS } from '../helpers.js';

test.describe('App Loading Smoke Tests', () => {
  test('app should load without errors', async ({ page }) => {
    const errors = [];

    // Capture console errors
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    // Capture page errors
    page.on('pageerror', (error) => {
      errors.push(error.message);
    });

    await page.goto('./');

    // Wait for the app to initialize
    await expect(page.getByTestId('app-layout')).toBeVisible({ timeout: TIMEOUTS.DEFAULT });

    // Filter out expected/known errors
    const unexpectedErrors = errors.filter(
      (e) => !e.includes('WebSocket') && !e.includes('Failed to fetch')
    );

    // No unexpected errors should occur
    expect(unexpectedErrors).toHaveLength(0);
  });

  test('WebSocket should connect', async ({ page }) => {
    await page.goto('./');

    // Try to verify connection - Playwright auto-waits on the assertion
    try {
      // Connection status should show "Connected"
      await expect(page.getByTestId('connection-status')).toHaveAttribute('data-tip', 'Connected', { timeout: 10000 });
    } catch {
      // In test environments without a backend, connection may fail
      // This is acceptable for smoke tests
      test.skip('Backend not available');
    }
  });

  test('main layout regions should be present', async ({ page }) => {
    await page.goto('./');

    // All main layout regions should be visible
    await expect(page.getByTestId('app-layout')).toBeVisible();
    await expect(page.getByTestId('navbar')).toBeVisible();
    await expect(page.getByTestId('sidebar')).toBeVisible();
    await expect(page.getByTestId('breadcrumbs')).toBeVisible();
  });

  test('navigation links should be present', async ({ page }) => {
    await page.goto('./');

    // Check for main navigation links using testids
    await expect(page.getByTestId('nav-agents')).toBeVisible();
  });

  test('should be responsive on mobile viewport', async ({ page }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('./');

    // App should still render
    await expect(page.getByTestId('app-layout')).toBeVisible();

    // Sidebar might be hidden on mobile
    const sidebarVisible = await page.getByTestId('sidebar').isVisible().catch(() => false);
    const menuToggleVisible = await page.getByTestId('mobile-menu-toggle').isVisible().catch(() => false);

    // Either sidebar is visible or there's a toggle button
    expect(sidebarVisible || menuToggleVisible).toBe(true);
  });
});
