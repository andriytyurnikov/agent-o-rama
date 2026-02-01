// @ts-check
/**
 * Navigation helper functions for evaluators feature E2E tests.
 */
import { navigateTo } from '../../helpers/navigation.js';

/**
 * Navigate to evaluators for a module.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
export async function navigateToEvaluators(page, moduleId) {
  await navigateTo(page, `/agents/${moduleId}/evaluators`);
}
