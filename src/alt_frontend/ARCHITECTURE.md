# Alt-Frontend Architectural Corrections

Alt-frontend is a **comparative refactoring**—demonstrating that certain main UI choices are suboptimal while maintaining feature parity. Each correction is documented here with detailed rationale.

## Correction #1: CSS Abstraction Level

**Main UI choice:** Pure Tailwind utility classes, no component abstraction.

**The problem:**
```clojure
;; Main UI: every button repeats verbose utility strings
:class "px-2 py-1 bg-blue-100 text-blue-800 rounded-full text-xs font-medium inline-flex items-center gap-1"
```

- No semantic component styles (what is a "button"? a "card"?)
- Verbose, repetitive markup throughout codebase
- No central place to adjust component appearance
- Requires `cn` helper to manage class concatenation
- Theming requires touching every component

**Alt-frontend correction:** DaisyUI component classes.
```clojure
;; Alt-frontend: semantic, concise, themeable
:class "btn btn-primary btn-sm"
```

**Benefits gained:**
- Semantic class names improve readability
- Component styles defined once, used everywhere
- Built-in theming (change theme, all components update)
- Reduced markup verbosity
- Still build-less (both use CDN delivery)

**Complexity trade-off:** None. DaisyUI adds no build step, no runtime cost. Pure improvement.

---

## Correction #2: Route-Based File Organization

**Main UI choice:** Feature-based flat file structure.

**The problem:**
```
src/cljs/com/rpl/agent_o_rama/ui/
├── agents.cljs           # Contains: index, agent detail, invocations, invoke
├── datasets.cljs         # Contains: index, detail, examples
├── experiments/
│   ├── index.cljs
│   ├── regular-detail.cljs
│   ├── comparative.cljs
│   └── comparative-detail.cljs
├── analytics.cljs
├── evaluators.cljs
├── module-page.cljs
└── ... 38 files total
```

- Route `/agents/:module-id/agent/:agent-name/invocations/:invoke-id` → where's the code?
- Must grep or read `ui.cljs` route definitions to find component
- Multiple views crammed into single files (`agents.cljs` = 4 different routes)
- No clear mapping between URL structure and file structure
- Adding a new route requires deciding which existing file to modify

**Alt-frontend correction:** Route-based directory structure (Next.js/Remix convention).
```
src/alt_frontend/routes/
├── index.cljs                                    # /
└── agents/
    ├── index.cljs                                # /agents
    └── $module_id/
        ├── index.cljs                            # /agents/:module-id
        ├── evaluations.cljs                      # /agents/:module-id/evaluations
        ├── global_config.cljs                    # /agents/:module-id/global-config
        ├── datasets/
        │   ├── index.cljs                        # /agents/:module-id/datasets
        │   └── $dataset_id/
        │       ├── index.cljs                    # .../datasets/:dataset-id
        │       ├── examples.cljs                 # .../examples
        │       └── experiments/
        │           ├── index.cljs                # .../experiments
        │           └── $experiment_id.cljs       # .../experiments/:experiment-id
        └── agent/$agent_name/
            ├── index.cljs                        # .../agent/:agent-name
            ├── invocations/
            │   ├── index.cljs                    # .../invocations
            │   └── $invoke_id.cljs               # .../invocations/:invoke-id
            ├── analytics.cljs                    # .../analytics
            ├── config.cljs                       # .../config
            └── rules/
                ├── index.cljs                    # .../rules
                └── $rule_name/action_log.cljs    # .../rules/:rule-name/action-log
```

**Benefits gained:**
- URL path → file path is a direct mapping
- Finding code for any route is trivial (no grepping)
- One file per route, single responsibility
- Adding new routes = adding new files in obvious locations
- Directory structure documents the application's navigation
- Scales cleanly as routes grow

**Complexity trade-off:** More files, deeper nesting. But files are smaller and self-contained. Net improvement for navigation and maintainability.

---

## Correction #3: Centralized Icon System

**Main UI choice:** Hardcoded inline SVGs scattered throughout components.

**The problem:**
```clojure
;; Main UI: SVG paths repeated in every component that needs an icon
($ :svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   ($ :path {:strokeLinecap "round" :strokeLinejoin "round" :strokeWidth "2"
             :d "M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"}))
```

- Same SVG path copied 4+ times across different files (trash icon alone appears in rules, evaluations, metrics, queues)
- No consistency in icon sizing or styling
- Difficult to update icons across the codebase
- Verbose, cluttered component code
- No semantic meaning (what does this SVG represent?)

**Alt-frontend correction:** Centralized Heroicons via `@heroicons/react` npm package.
```clojure
;; Import once, use everywhere
(require '[alt-frontend.components.icons :as icons])

;; Semantic, concise, consistent
($ icons/trash {:class "h-5 w-5"})
($ icons/chevron-left)  ; uses default h-5 w-5
($ icons/settings {:class "h-8 w-8 text-primary"})
```

**Icon module location:** `src/alt_frontend/components/icons.cljs`

**Available icons:**
- Navigation: `chevron-left`, `chevron-right`, `chevron-down`, `chevron-up`, `arrow-external`, `home`
- Actions: `trash`, `plus`, `check`, `x-mark`, `refresh`, `play`, `download`, `upload`
- UI: `menu`, `search`, `settings`, `info`, `ellipsis-vertical`, `filter-icon`, `adjustments`
- Status: `warning`, `error`, `success`
- Domain: `agent`, `dataset`, `evaluator`, `chart`, `rules`, `document`, `queue`, `calendar`, `clock`, `grid`, `user`

**Adding new icons:**
1. Find the icon name in Heroicons (https://heroicons.com)
2. Add to imports in `components/icons.cljs`
3. Create wrapper with `(defui icon-name [props] ($ IconComponent (icon-props props)))`

**Benefits gained:**
- Single source of truth for all icons
- Consistent sizing and styling via `icon-props` helper
- Semantic icon names improve code readability
- Easy to swap icon libraries in the future
- Reduced bundle duplication (one import vs repeated inline SVGs)

**Complexity trade-off:** Minimal. Adds one shared component file. All icons come from the already-installed `@heroicons/react` package.

---

*Additional corrections will be documented as alt-frontend development progresses.*

---

*Part of alt-frontend documentation. See [README.md](./README.md) for overview and other docs.*
