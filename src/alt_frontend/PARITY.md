# Alt-Frontend Parity Tracking

This file tracks feature parity between the main UI and alt-frontend.

**Main UI baseline:** `master` @ [`a7fdf56`](https://github.com/redplanetlabs/agent-o-rama/commit/a7fdf56) (2026-01-27)
**Routes defined in:** `src/cljs/com/rpl/agent_o_rama/ui.cljs` (lines 38-82)

## Status Legend

| Status | Meaning |
|--------|---------|
| `stub` | Route exists, placeholder UI only |
| `partial` | Some functionality implemented |
| `complete` | Feature parity with main UI |
| `n/a` | Not applicable or intentionally different |

## Route Parity

| Route | Main UI Name | Alt-Frontend File | Status |
|-------|--------------|-------------------|--------|
| `/` | `:home` | `routes/index.cljs` | **complete** |
| `/agents` | `:agents/index` | `routes/agents/index.cljs` | **complete** |
| `/agents/:module-id` | `:module/detail` | `routes/agents/$module_id/index.cljs` | **complete** |
| `/agents/:module-id/datasets` | `:module/datasets` | `routes/agents/$module_id/datasets/index.cljs` | **complete** |
| `/agents/:module-id/datasets/:dataset-id` | `:module/dataset-detail` | `routes/agents/$module_id/datasets/$dataset_id/index.cljs` | **complete** |
| `/agents/:module-id/datasets/:dataset-id/examples` | `:module/dataset-detail.examples` | `routes/agents/$module_id/datasets/$dataset_id/examples.cljs` | **complete** |
| `/agents/:module-id/datasets/:dataset-id/experiments` | `:module/dataset-detail.experiments` | `routes/agents/$module_id/datasets/$dataset_id/experiments/index.cljs` | **complete** |
| `/agents/:module-id/datasets/:dataset-id/experiments/:experiment-id` | `:module/dataset-detail.experiment-detail` | `routes/agents/$module_id/datasets/$dataset_id/experiments/$experiment_id.cljs` | **complete** |
| `/agents/:module-id/datasets/:dataset-id/comparative-experiments` | `:module/dataset-detail.comparative-experiments` | `routes/agents/$module_id/datasets/$dataset_id/comparative_experiments/index.cljs` | **complete** |
| `/agents/:module-id/datasets/:dataset-id/comparative-experiments/:experiment-id` | `:module/dataset-detail.comparative-experiment-detail` | `routes/agents/$module_id/datasets/$dataset_id/comparative_experiments/$experiment_id.cljs` | **complete** |
| `/agents/:module-id/evaluations` | `:module/evaluations` | `routes/agents/$module_id/evaluations.cljs` | **complete** |
| `/agents/:module-id/human-metrics` | `:module/human-metrics` | `routes/agents/$module_id/human_metrics.cljs` | **complete** |
| `/agents/:module-id/human-feedback-queues` | `:module/human-feedback-queues` | `routes/agents/$module_id/human_feedback_queues/index.cljs` | **complete** |
| `/agents/:module-id/human-feedback-queues/:queue-id` | `:module/human-feedback-queue-detail` | `routes/agents/$module_id/human_feedback_queues/$queue_id/index.cljs` | **complete** |
| `/agents/:module-id/human-feedback-queues/:queue-id/items/:item-id` | `:module/human-feedback-queue-item` | `routes/agents/$module_id/human_feedback_queues/$queue_id/items/$item_id.cljs` | **complete** |
| `/agents/:module-id/human-feedback-queues/:queue-id/end` | `:module/human-feedback-queue-end` | `routes/agents/$module_id/human_feedback_queues/$queue_id/end.cljs` | **complete** |
| `/agents/:module-id/global-config` | `:module/global-config` | `routes/agents/$module_id/global_config.cljs` | **complete** |
| `/agents/:module-id/agent/:agent-name` | `:agent/detail` | `routes/agents/$module_id/agent/$agent_name/index.cljs` | **complete** |
| `/agents/:module-id/agent/:agent-name/invocations` | `:agent/invocations` | `routes/agents/$module_id/agent/$agent_name/invocations/index.cljs` | **complete** |
| `/agents/:module-id/agent/:agent-name/invocations/:invoke-id` | `:agent/invocation-detail` | `routes/agents/$module_id/agent/$agent_name/invocations/$invoke_id.cljs` | **complete** |
| `/agents/:module-id/agent/:agent-name/analytics` | `:agent/analytics` | `routes/agents/$module_id/agent/$agent_name/analytics.cljs` | **complete** |
| `/agents/:module-id/agent/:agent-name/rules` | `:agent/rules` | `routes/agents/$module_id/agent/$agent_name/rules/index.cljs` | **complete** |
| `/agents/:module-id/agent/:agent-name/rules/:rule-name/action-log` | `:agent/action-log` | `routes/agents/$module_id/agent/$agent_name/rules/$rule_name/action_log.cljs` | **complete** |
| `/agents/:module-id/agent/:agent-name/config` | `:agent/config` | `routes/agents/$module_id/agent/$agent_name/config.cljs` | **complete** |

**Total: 24 routes | stub: 0 | partial: 0 | complete: 24**

## How to Check Parity

When main UI adds new routes:

1. Check `src/cljs/com/rpl/agent_o_rama/ui.cljs` for route changes (lines 38-82)
2. Compare against this table
3. Create missing route files following the `$param` convention
4. Update this table with new entries

## Shared Infrastructure Parity

| Component | Main UI | Alt-Frontend | Status |
|-----------|---------|--------------|--------|
| WebSocket client | `ui/sente.cljs` | `lib/ws/sente.cljs` | **complete** |
| State management | `ui/state.cljs` (Specter) | `lib/state.cljs` (Specter) | **complete** |
| Query/caching | `ui/queries.cljs` | `lib/queries.cljs` | **complete** |
| Real-time streaming | `ui/streaming.cljs` | `lib/streaming.cljs` | **complete** |
| UI components | utilities | `components/ui.cljs` (DaisyUI) | **partial** |
| Layout | `ui.cljs` (sidebar) | `components/layout.cljs` (DaisyUI drawer) | **complete** |
| Forms/modals | `ui/forms.cljs` | `lib/forms.cljs`, `components/modal.cljs` | **complete** |

## Main UI Component Files

Reference for implementing alt-frontend equivalents:

| Main UI File | Lines | Purpose |
|--------------|-------|---------|
| `ui/agents.cljs` | ~800 | Agent list, detail, invocations, invoke |
| `ui/datasets.cljs` | ~600 | Dataset list, detail, examples |
| `ui/experiments/index.cljs` | ~200 | Experiment list |
| `ui/experiments/regular-detail.cljs` | ~400 | Experiment detail |
| `ui/experiments/comparative.cljs` | ~200 | Comparative experiments |
| `ui/experiments/comparative-detail.cljs` | ~500 | Comparative experiment detail |
| `ui/analytics.cljs` | ~400 | Analytics dashboard |
| `ui/evaluators.cljs` | ~300 | Evaluator management |
| `ui/rules.cljs` | ~200 | Rules management |
| `ui/action-log.cljs` | ~150 | Action log viewer |
| `ui/human-feedback-queues.cljs` | ~400 | Human feedback queue UI |
| `ui/config-page.cljs` | ~200 | Agent config |
| `ui/global-config-page.cljs` | ~150 | Global config |
| `ui/module-page.cljs` | ~150 | Module overview |
| `ui/invocation-graph-view.cljs` | ~600 | Trace visualization (ReactFlow) |

## Remaining Gaps

**None.** All 24 routes are now complete with feature parity.

### Invocation Detail (`/agents/:module-id/agent/:agent-name/invocations/:invoke-id`)

**Status:** ✅ Complete

| Feature | Main UI | Alt-Frontend |
|---------|---------|--------------|
| Summary card (status, timing, args) | ✅ | ✅ |
| Final result display | ✅ | ✅ |
| Exceptions panel | ✅ | ✅ |
| Metadata display | ✅ | ✅ |
| Node-by-node trace (collapsible) | ✅ | ✅ |
| Live polling for running invocations | ✅ | ✅ |
| **DAG graph visualization (ReactFlow)** | ✅ | ✅ |
| **Graph/Trace view toggle** | ✅ | ✅ |

**Implementation:** The alt-frontend now includes a complete ReactFlow-based DAG visualization with:
- Dagre automatic layout
- Custom node rendering with status indicators
- Interactive node selection with detail panel
- Zoom, pan, and minimap controls
- Toggle between Graph View and Trace View (persisted to localStorage)

---

*Part of alt-frontend documentation. See [README.md](./README.md) for overview and other docs.*
