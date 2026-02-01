# Alt-Frontend

An experimental alternative frontend for Agent-o-rama using DaisyUI and route-based file organization.

## Quick Start

```bash
# Development (watch mode)
lein with-profile +ui run -m shadow.cljs.devtools.cli --npm watch :alt-dev

# Production build
lein with-profile +ui run -m shadow.cljs.devtools.cli --npm compile :alt-frontend
```

Access at `http://localhost:1974/alt/` (path-based routing: `/alt/agents/...`)

## Documentation

| File | Purpose |
|------|---------|
| [ROUTES.md](./ROUTES.md) | Route convention reference (`$param` syntax) |
| [PARITY.md](./PARITY.md) | Feature parity tracking with main UI |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Design rationale for architectural choices |

## Directory Structure

```
alt_frontend/
├── main.cljs              # Entry point
├── router.cljs            # Reitit router setup
├── lib/                   # Infrastructure
│   ├── state.cljs         # Atom-based state management
│   ├── queries.cljs       # Query caching (TanStack-like)
│   ├── forms.cljs         # Form state management
│   ├── utils.cljs         # URL encoding, debounce hooks
│   ├── time.cljs          # Time formatting utilities
│   ├── json.cljs          # JSON formatting utilities
│   └── ws/sente.cljs      # WebSocket client
├── components/            # Shared UI components
│   ├── layout.cljs        # DaisyUI drawer, navbar, breadcrumbs
│   ├── icons.cljs         # Centralized Heroicons
│   ├── modal.cljs         # Modal dialogs
│   ├── ui.cljs            # Common UI elements (stat-card, nav-card, section-card)
│   ├── views.cljs         # Shared views (404)
│   ├── chart.cljs         # Chart components (Recharts)
│   ├── rules_form.cljs    # Rules creation/editing form
│   ├── evaluator_form.cljs # Evaluator creation/editing form
│   └── invocation_graph.cljs # ReactFlow DAG visualization
├── routes/                # Route-based views
│   ├── registry.cljs      # Auto-generated (gitignored)
│   ├── index.cljs         # /
│   └── agents/...         # Nested route structure
└── build_hooks/           # Shadow-cljs build hooks (Clojure)
    └── route_registry_generator.clj

resource/alt-frontend/
├── index.html             # HTML template (DaisyUI/Tailwind CDN)
└── public/                # Build output directory
```

## Route Convention (Summary)

Routes are defined by file structure using the `$param` convention:

```
File path                                    → Route
─────────────────────────────────────────────────────────
routes/index.cljs                            → /
routes/agents/index.cljs                     → /agents
routes/agents/$module_id/index.cljs          → /agents/:module-id
routes/agents/$module_id/datasets/index.cljs → /agents/:module-id/datasets
```

**Rules:**
- `$param_name` in directory/file names → `:param-name` route parameter
- `index.cljs` → directory path (no `/index` suffix)
- `name.cljs` → adds `/name` to path
- Underscores → hyphens (`global_config.cljs` → `/global-config`)

**Adding a new route:** Create the file in the right location. The build hook auto-generates `routes/registry.cljs`. See [ROUTES.md](./ROUTES.md) for full details.

## Route File Template

```clojure
(ns alt-frontend.routes.agents.$module-id.example
  (:require [uix.core :refer [defui $]]
            [alt-frontend.lib.utils :as utils]
            [alt-frontend.lib.time :as time]
            [alt-frontend.lib.json :as json]
            [alt-frontend.components.ui :as ui]))

(defui view [{:keys [module-id]}]
  (let [decoded-id (utils/url-decode module-id)]
    ($ :div {:class "space-y-4"}
       ($ :h1 {:class "text-2xl font-bold"} "Example Page")
       ($ :p {:class "text-base-content/60"} (str "Module: " decoded-id)))))
```

Each route file exports a `view` component that receives route params as props.

## Shared Utility Libraries

All route files should import from these centralized utility modules to avoid code duplication:

### `lib/utils.cljs` - URL & Hooks

| Function | Description |
|----------|-------------|
| `url-decode` | Decode URL-encoded strings (`%20` → ` `) |
| `url-encode` | Encode strings for URLs |
| `use-debounced-value` | Hook for debouncing search inputs |

```clojure
(utils/url-decode "hello%20world")  ; => "hello world"
(utils/use-debounced-value search-term 300)  ; debounce by 300ms
```

### `lib/time.cljs` - Time Formatting

| Function | Description |
|----------|-------------|
| `format-relative-time` | "5m ago", "2h ago", "3d ago" |
| `format-timestamp` | Full locale date/time string |
| `format-timestamp-with-ms` | Full timestamp with milliseconds |
| `format-duration` | Duration between two timestamps |
| `format-duration-ms` | Format raw milliseconds |

```clojure
(time/format-relative-time 1706123456789)  ; => "2h ago"
(time/format-duration start-ms end-ms)     ; => "1.23s"
```

### `lib/json.cljs` - JSON Formatting

| Function | Description |
|----------|-------------|
| `pretty-json` | Pretty-print with 2-space indent |
| `to-json` | Compact JSON (no whitespace) |
| `truncate-json` | Pretty JSON, truncated to max length |
| `truncate-json-compact` | Compact JSON, truncated to max length |

```clojure
(json/pretty-json {:foo "bar"})       ; => "{\n  \"foo\": \"bar\"\n}"
(json/truncate-json {:foo "bar"} 10)  ; => "{\n  \"foo..." (truncated)
```

## Shared UI Components

### `ui/stat-card` - Statistics Display

```clojure
($ ui/stat-card {:title "Total Agents"
                 :value 42
                 :icon ($ icons/agent {:class "h-8 w-8"})
                 :loading? false})
```

### `ui/nav-card` - Navigation Cards

```clojure
($ ui/nav-card {:title "Analytics"
                :description "View performance metrics"
                :on-click #(rfe/push-state :analytics {...})})
```

### `ui/section-card` - Content Sections with Loading States

```clojure
($ ui/section-card {:title "Agents"
                    :icon ($ icons/agent)
                    :loading? loading?
                    :error error}
   ($ agents-table {...}))  ; children shown when not loading/error
```

## Test IDs for E2E Testing

Components include `data-testid` attributes for stable E2E test selectors. This avoids brittle CSS class selectors that break when styling changes.

### Layout Components (`components/layout.cljs`)

| TestID | Element | Usage |
|--------|---------|-------|
| `app-layout` | Root drawer container | Verify app structure |
| `navbar` | Top navigation bar | Check navbar visibility |
| `sidebar` | Side navigation drawer | Check sidebar visibility |
| `breadcrumbs` | Breadcrumb navigation | Verify navigation context |
| `connection-status` | WebSocket status indicator | Wait for connection |
| `connection-badge` | Connection status badge | Check connected/disconnected |
| `mobile-menu-toggle` | Hamburger menu button | Mobile navigation testing |

### Modal Components (`components/modal.cljs`)

| TestID | Element | Usage |
|--------|---------|-------|
| `modal-dialog` | Modal dialog container | Wait for modal to appear/close |
| `modal-content` | Modal content box | Interact with modal content |
| `modal-close` | Close button (×) | Close modal programmatically |

### UI Components (`components/ui.cljs`)

| TestID | Element | Usage |
|--------|---------|-------|
| `data-table` | Data table element | Find table, count rows |
| `loading-spinner` | Loading indicator | Wait for loading to finish |
| `empty-state` | Empty state placeholder | Check for no-data state |

Components also accept an optional `:testid` prop for custom test IDs:

```clojure
;; Custom testid for specific table
($ ui/data-table {:testid "agents-table" :columns [...] :rows [...]})

;; Custom testid for specific empty state
($ ui/empty-state {:testid "no-datasets" :title "No datasets"})
```

### Using TestIDs in Playwright Tests

```javascript
// Prefer getByTestId over CSS class selectors
const modal = page.getByTestId('modal-dialog');
await expect(modal).toBeVisible();

// Use testid to scope other selectors
const table = page.getByTestId('data-table');
const rows = await table.locator('tbody tr').all();

// Wait for connection
const status = page.getByTestId('connection-status');
await expect(status).toHaveAttribute('data-tip', 'Connected');
```

## Technology Stack

| Component | Choice |
|-----------|--------|
| UI Framework | UIx (React wrapper) |
| Styling | DaisyUI v5 + Tailwind CSS v4 (CDN) |
| Routing | Reitit (path-based, HTML5 History) |
| State | Atom + Specter + hooks |
| WebSocket | Sente (shared `/chsk` endpoint) |

## E2E Testing

Alt-frontend has its own Playwright test suite at `test/alt_frontend/e2e/`.

### Running E2E Tests Locally

**Option 1: Auto-start server (simple)**

```bash
cd test/alt_frontend
npx playwright test
```

Playwright will automatically start the dev server via the `webServer` config.

**Option 2: Manual server (faster iteration)**

```bash
# Terminal 1: Start the server with modules loaded
lein repl
# In REPL:
(require '[com.rpl.agent-o-rama :as aor])
(require '[com.rpl.rama.test :as rtest])
(require '[com.rpl.agent.basic.basic-agent :as basic])
(require '[com.rpl.agent.e2e-test-agent :as e2e])

(def ipc (rtest/create-ipc))
(rtest/launch-module! ipc basic/BasicAgentModule {:tasks 1 :threads 1})
(rtest/launch-module! ipc e2e/E2ETestAgentModule {:tasks 1 :threads 1})
(aor/start-ui ipc)

# Terminal 2: Build alt-frontend in watch mode
lein with-profile +ui run -m shadow.cljs.devtools.cli watch :alt-dev

# Terminal 3: Run tests (server already running)
cd test/alt_frontend
npx playwright test
```

**Option 3: Production build (CI-like)**

```bash
# Build production bundle
lein with-profile +ui run -m shadow.cljs.devtools.cli release :alt-frontend

# Start server with pre-built frontend
lein with-profile +dev,+ui run -m ci-playwright-setup --no-frontend &

# Wait for server, then run tests
cd test/alt_frontend
CI=true npx playwright test --workers=1
```

### Test Structure

```
test/alt_frontend/
├── playwright.config.js      # Playwright configuration
└── e2e/
    ├── setup/
    │   ├── global-setup.js   # Runs once before all tests
    │   └── test-fixtures.js  # Shared test fixtures
    ├── helpers/
    │   ├── pages/            # Page Object classes
    │   ├── selectors.js      # TestID selectors
    │   └── actions.js        # Reusable test actions
    ├── smoke/                # Quick sanity checks (run first)
    │   ├── app-loads.spec.js
    │   └── navigation.spec.js
    └── features/             # Feature tests (run after smoke)
        ├── agents/
        ├── datasets/
        ├── experiments/
        └── ...
```

### Test Projects

The Playwright config defines three projects that run in order:

| Project | Directory | Purpose |
|---------|-----------|---------|
| `smoke` | `e2e/smoke/` | Basic sanity checks, runs first |
| `features` | `e2e/features/` | Full feature tests, depends on smoke |
| `legacy` | `e2e/*.spec.js` | Old flat structure (migration compatibility) |

Run specific project: `npx playwright test --project=smoke`

### Key Patterns (from main app CI)

The main app's CI workflow (`/.github/workflows/playwright.yml`) demonstrates these patterns:

1. **Separate build from runtime** - Build frontend first, then start server with `--no-frontend`
2. **Health check loop** - Poll server URL before running tests
3. **Single worker in CI** - Use `--workers=1` to avoid race conditions
4. **Retries for flakiness** - `retries: process.env.CI ? 2 : 0`
5. **Trace on retry** - `trace: 'on-first-retry'` for debugging failures

### Debugging Failed Tests

```bash
# Run with UI mode (interactive)
npx playwright test --ui

# Run specific test file
npx playwright test smoke/navigation.spec.js

# Show browser during test
npx playwright test --headed

# Generate and view HTML report
npx playwright show-report
```

## Development Notes

- Both frontends share the same Sente WebSocket endpoint (`/chsk`)
- Alt-frontend served at `/alt/*`, main UI at `/`
- Build output goes to `resource/alt-frontend/public/`
- All 24 routes have feature parity with main UI (see [PARITY.md](./PARITY.md))
- E2E tests located in `test/alt_frontend/e2e/`
