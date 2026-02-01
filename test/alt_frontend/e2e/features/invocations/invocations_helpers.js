// @ts-check
/**
 * Navigation helper functions for invocations feature E2E tests.
 */
import { navigateTo } from '../../helpers/navigation.js';

/**
 * Navigate to invocations for an agent.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 * @param {string} agentName
 */
export async function navigateToInvocations(page, moduleId, agentName) {
  await navigateTo(page, `/agents/${moduleId}/agent/${agentName}/invocations`);
}

/**
 * Navigate to a module's detail page.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
export async function navigateToModule(page, moduleId) {
  await navigateTo(page, `/agents/${moduleId}`);
}
