package org.nakii.valmora.api.pipeline.flow;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * A numeric step parameter: a literal, a pre-compiled expression (evaluated against the running
 * flow — its state is exposed first so {@code $fishing.*$}-style variables resolve), or a live
 * {@code config.yml} lookup (the fallback for params that predate flows — see
 * docs/PIPELINE_FLOWS_PLAN.md §4.3).
 */
@FunctionalInterface
public interface NumberParam {

    double get(ExecutionContext ctx, FlowState state);

    static NumberParam constant(double value) {
        return (ctx, state) -> value;
    }

    /** Reads {@code path} from {@code config.yml} on every call (as the pre-flow code did), else {@code def}. */
    static NumberParam config(String path, double def) {
        return (ctx, state) -> {
            Valmora plugin = Valmora.getInstance();
            return plugin != null && plugin.getConfig() != null ? plugin.getConfig().getDouble(path, def) : def;
        };
    }
}
