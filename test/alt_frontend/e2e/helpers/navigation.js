// @ts-check
/**
 * Navigation helper functions for alt-frontend E2E tests.
 */
import { waitForConnection, waitForLoadingToFinish } from './actions.js';

/**
 * Navigate to a hash-based route.
 * @param {import('@playwright/test').Page} page
 * @param {string} path - The route path (without hash)
 */
export async function navigateTo(page, path) {
  await page.goto(`./#${path}`);
  await waitForConnection(page).catch(() => {});
  await waitForLoadingToFinish(page);
}

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

/**
 * Navigate to datasets for a module.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
export async function navigateToDatasets(page, moduleId) {
  await navigateTo(page, `/agents/${moduleId}/datasets`);
}

/**
 * Navigate to evaluators for a module.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
export async function navigateToEvaluators(page, moduleId) {
  await navigateTo(page, `/agents/${moduleId}/evaluators`);
}

/**
 * Navigate to global config for a module.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 */
export async function navigateToGlobalConfig(page, moduleId) {
  await navigateTo(page, `/agents/${moduleId}/global-config`);
}

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

/**
 * Navigate to rules for an agent.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 * @param {string} agentName
 */
export async function navigateToRules(page, moduleId, agentName) {
  await navigateTo(page, `/agents/${moduleId}/agent/${agentName}/rules`);
}

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
 * Navigate to analytics for an agent.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 * @param {string} agentName
 */
export async function navigateToAnalytics(page, moduleId, agentName) {
  await navigateTo(page, `/agents/${moduleId}/agent/${agentName}/analytics`);
}

/**
 * Navigate to experiments for a dataset.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 * @param {string} datasetId
 */
export async function navigateToExperiments(page, moduleId, datasetId) {
  await navigateTo(page, `/agents/${moduleId}/datasets/${datasetId}/experiments`);
}
