package org.nakii.valmora.api.pipeline.flow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One {@code *_pipeline.yml} file: its flows, its {@code HookBus} point prefix, and the point names
 * its legacy {@code stages:} entries may use as {@code when:}.
 *
 * @param legacyPoints point names (without the prefix) valid as a legacy stage's {@code when:} —
 *                     kept explicitly because a point can outlive the flow that used to own it
 *                     (e.g. {@code combat:on_death} is now an anchor of the mob {@code death} flow)
 */
public record FlowDomain(String id, String fileName, String pointPrefix, List<FlowSpec<?>> flows,
                         List<String> legacyPoints) {

    public FlowSpec<?> flow(String flowId) {
        for (FlowSpec<?> spec : flows) {
            if (spec.id().equalsIgnoreCase(flowId)) return spec;
        }
        return null;
    }

    public List<String> flowIds() {
        List<String> ids = new ArrayList<>();
        for (FlowSpec<?> spec : flows) ids.add(spec.id());
        return ids;
    }

    /** Every key {@code pipeline_set} may write in any of this domain's flows. */
    public Set<String> writableKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (FlowSpec<?> spec : flows) keys.addAll(spec.writableKeys());
        return keys;
    }
}
