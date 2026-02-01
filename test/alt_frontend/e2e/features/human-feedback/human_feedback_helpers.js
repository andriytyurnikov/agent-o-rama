// @ts-check
/**
 * Navigation helper functions for human-feedback feature E2E tests.
 */
import { navigateTo } from '../../helpers/navigation.js';

/**
 * Navigate to human feedback queues for a module.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
export async function navigateToHumanFeedback(page, moduleId) {
  await navigateTo(page, `/agents/${moduleId}/human-feedback-queues`);
}

/**
 * Navigate to human metrics for a module.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
export async function navigateToHumanMetrics(page, moduleId) {
  await navigateTo(page, `/agents/${moduleId}/human-metrics`);
}
