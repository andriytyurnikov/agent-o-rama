# Alt-Frontend Route Conventions

This document describes the file-based routing system used in alt-frontend.

## The `$param` Convention

Routes are automatically generated from the file structure in `routes/`. Dynamic route parameters are encoded using the `$param` prefix, inspired by [Remix's routing conventions](https://remix.run/docs/en/main/file-conventions/routes).

### Why `$`?

The `$` character was chosen because it:
- Is valid in ClojureScript namespace names
- Is valid in file/directory names on all platforms
- Clearly distinguishes parameters from static segments
- Matches Remix's established convention

## File → Route Mapping

### Basic Rules

| File Pattern | Route Pattern | Example |
|--------------|---------------|---------|
| `index.cljs` | Directory path | `agents/index.cljs` → `/agents` |
| `name.cljs` | Directory + name | `agents/evaluations.cljs` → `/agents/evaluations` |
| `$param/` | `:param` segment | `$module_id/` → `/:module-id/` |
| `$param.cljs` | `:param` segment | `$experiment_id.cljs` → `/:experiment-id` |

### Naming Transformations

- Underscores → Hyphens: `global_config.cljs` → `/global-config`
- `$` prefix → `:` prefix: `$module_id` → `:module-id`

### Examples

```
File path                                              Route path
──────────────────────────────────────────────────────────────────────────────
routes/index.cljs                                    → /
routes/agents/index.cljs                             → /agents
routes/agents/$module_id/index.cljs                  → /agents/:module-id
routes/agents/$module_id/evaluations.cljs            → /agents/:module-id/evaluations
routes/agents/$module_id/global_config.cljs          → /agents/:module-id/global-config
routes/agents/$module_id/datasets/index.cljs         → /agents/:module-id/datasets
routes/agents/$module_id/datasets/$dataset_id/index.cljs
                                                     → /agents/:module-id/datasets/:dataset-id
routes/agents/$module_id/datasets/$dataset_id/examples.cljs
                                                     → /agents/:module-id/datasets/:dataset-id/examples
routes/agents/$module_id/datasets/$dataset_id/experiments/$experiment_id.cljs
                                                     → /agents/:module-id/datasets/:dataset-id/experiments/:experiment-id
routes/agents/$module_id/agent/$agent_name/index.cljs
                                                     → /agents/:module-id/agent/:agent-name
routes/agents/$module_id/agent/$agent_name/invocations/$invoke_id.cljs
                                                     → /agents/:module-id/agent/:agent-name/invocations/:invoke-id
```

## Route Names

Route names are automatically derived from the path:

| Route Path | Generated Name |
|------------|----------------|
| `/` | `:home` |
| `/agents` | `:agents` |
| `/agents/:module-id` | `:agents-detail` |
| `/agents/:module-id/evaluations` | `:evaluations` |
| `/agents/:module-id/datasets/:dataset-id` | `:datasets-detail` |
| `/agents/:module-id/datasets/:dataset-id/experiments/:experiment-id` | `:experiments-detail` |

**Naming logic:**
- Root path → `:home`
- Path ending with static segment → segment name (`:evaluations`, `:analytics`)
- Path ending with parameter → last static segment + `-detail` (`:datasets-detail`)

## Route File Structure

Each route file must export a `view` component:

```clojure
(ns alt-frontend.routes.agents.$module-id.index
  (:require [uix.core :refer [defui $]]))

(defui view [{:keys [module-id]}]
  ($ :div
     ($ :h1 "Module: " module-id)
     ;; ... component content
     ))
```

The view receives route parameters as props.

## Adding a New Route

1. Create a file in the appropriate location:
   ```bash
   # For /agents/:module-id/new-feature
   touch src/alt_frontend/routes/agents/\$module_id/new_feature.cljs
   ```

2. Add the namespace and view component:
   ```clojure
   (ns alt-frontend.routes.agents.$module-id.new-feature
     (:require [uix.core :refer [defui $]]))

   (defui view [{:keys [module-id]}]
     ($ :div "New feature for " module-id))
   ```

3. Build - the route is automatically registered:
   ```bash
   lein with-profile +ui run -m shadow.cljs.devtools.cli --npm compile :alt-dev
   ```

No changes to `router.cljs` or any other file required.

## Build Hook

The `build_hooks/route_registry_generator.clj` file runs at compile time and:

1. Scans `routes/` for `.cljs` files
2. Converts file paths to route configurations
3. Generates `routes/registry.cljs` with:
   - All namespace requires
   - `views` map: `{:route-name view-fn, ...}`
   - `routes` vector: Reitit route definitions

The generated `registry.cljs` is gitignored and regenerated on each build.

---

*Part of alt-frontend documentation. See [README.md](./README.md) for overview and other docs.*
