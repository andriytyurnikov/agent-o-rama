// @ts-check
/**
 * Navigation helper functions for experiments feature E2E tests.
 */
import { navigateTo } from '../../helpers.js';

/**
 * Navigate to experiments for a dataset.
 * @param {import('@playwright/test').Page} page
 * @param {string} moduleId
 * @param {string} datasetId
 */
export async function navigateToExperiments(page, moduleId, datasetId) {
  await navigateTo(page, `/agents/${moduleId}/datasets/${datasetId}/experiments`);
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
