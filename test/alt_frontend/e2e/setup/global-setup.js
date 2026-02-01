// @ts-check
/**
 * Global setup for alt-frontend E2E tests.
 *
 * This file runs once before all tests.
 */

export default async function globalSetup() {
  console.log('Alt-frontend E2E tests starting...');

  // Add any global setup logic here:
  // - Database seeding
  // - Authentication token generation
  // - Environment validation

  // Validate required environment
  const baseUrl = process.env.BASE_URL || 'http://localhost:1974/alt/';
  console.log(`Base URL: ${baseUrl}`);

  // Log test environment info
  console.log(`CI: ${process.env.CI || 'false'}`);
  console.log(`SKIP_CLEANUP: ${process.env.SKIP_CLEANUP || 'false'}`);
}
