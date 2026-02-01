// playwright.config.js for alt-frontend
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  // The directory where alt-frontend E2E test files are located.
  testDir: './e2e',

  // Test file patterns
  testMatch: 'smoke/**/*.spec.js',

  // Global setup - runs once before all tests
  globalSetup: './e2e/setup/global-setup.js',

  // Run tests sequentially since they may share state.
  fullyParallel: false,

  // In CI, retry failed tests up to 2 times.
  retries: process.env.CI ? 2 : 0,

  // Reporter to use.
  reporter: 'html',

  // Default timeout for each test (30 seconds - keeps tests fast)
  timeout: 30000,

  // Default timeout for expect assertions (3 seconds)
  expect: {
    timeout: 3000,
  },

  use: {
    // The base URL for alt-frontend (served at /alt/)
    baseURL: 'http://localhost:1974/alt/',

    // Creates a trace file on the first retry of a failed test.
    trace: 'on-first-retry',

    // Screenshot on failure
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'smoke',
      testDir: './e2e/smoke',
      testMatch: '**/*.spec.js',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'features',
      testDir: './e2e/features',
      testMatch: '**/*.spec.js',
      // Note: Run smoke tests separately before features in CI
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // Server startup configuration.
  // In CI, we start the server manually, so we disable webServer here.
  ...(process.env.CI ? {} : {
    webServer: {
      // The command to start the alt-frontend dev server.
      command: 'lein with-profile +ui run -m shadow.cljs.devtools.cli watch alt-dev',

      // The URL Playwright will poll to know when the server is ready.
      url: 'http://localhost:1974/alt/',

      // If you already have a dev server running, Playwright will use it.
      reuseExistingServer: true,

      // Give the server up to 2 minutes to start up.
      timeout: 120 * 1000,

      // Pipe server output to console for debugging.
      stdout: 'pipe',
      stderr: 'pipe',
    },
  }),
});
