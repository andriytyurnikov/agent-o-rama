// @ts-check
/**
 * Navigation helper functions for analytics feature E2E tests.
 */
import { navigateTo } from '../../helpers/navigation.js';

/**
 * Navigate to analytics for an agent.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 * @param {string} agentName
 */
export async function navigateToAnalytics(page, moduleId, agentName) {
  await navigateTo(page, `/agents/${moduleId}/agent/${agentName}/analytics`);
}

/**
 * Navigate to a module's detail page.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
export async function navigateToModule(page, moduleId) {
  await navigateTo(page, `/agents/${moduleId}`);
}
