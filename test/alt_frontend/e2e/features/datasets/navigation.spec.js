// @ts-check
/**
 * Tests for dataset navigation.
 */
import { test, expect } from '../../setup/test-fixtures.js';
import { waitForLoadingToFinish } from '../../helpers/actions.js';
import { navigateToDatasets } from './datasets_helpers.js';

test.describe('Dataset Navigation', () => {
  test('click row navigates to dataset detail', async ({ moduleId, page }) => {
    if (!moduleId) {
      test.skip('No module available');
      return;
    }

    await navigateToDatasets(page, moduleId);
    expect(page.url()).toContain(`#/agents/${moduleId}/datasets`);

    await waitForLoadingToFinish(page);

    const dataTable = page.getByTestId('data-table');
    const hasTable = await dataTable.isVisible().catch(() => false);

    if (!hasTable) {
      test.skip('No datasets available to navigate to');
      return;
    }

    // Use testid pattern for dataset rows
    const firstRow = page.locator('[data-testid^="dataset-row-"]').first();
    const isRowVisible = await firstRow.isVisible().catch(() => false);

    if (!isRowVisible) {
      test.skip('No dataset rows visible');
      return;
    }

    const urlBefore = page.url();

    // Click first row
    await firstRow.click();

    await waitForLoadingToFinish(page);

    // Assert URL changed to dataset detail (actually goes to examples)
    const urlAfter = page.url();
    expect(urlAfter).not.toBe(urlBefore);
    expect(urlAfter).toMatch(/#\/agents\/[^/]+\/datasets\/[^/]+/);
  });
});
