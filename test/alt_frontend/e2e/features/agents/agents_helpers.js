// @ts-check
/**
 * Navigation helper functions for agents feature E2E tests.
 */
import { navigateTo } from '../../helpers/navigation.js';

/**
 * Navigate to the agents list.
 * @param {import('@playwright/test').Page} page
 */
export async function navigateToAgents(page) {
  await navigateTo(page, '/agents');
}

/**
 * Navigate to a module's detail page.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
export async function navigateToModule(page, moduleId) {
  await navigateTo(page, `/agents/${moduleId}`);
}
