package org.nakii.valmora.api.pipeline.flow;

/**
 * An anchor a flow may contain: a {@code HookBus} point (full name, e.g. {@code "fishing:pre_catch"})
 * where legacy {@code stages:} entries and Java addon hooks run.
 *
 * @param stopsOnInterrupt whether an {@code interrupt} from a stage at this point stops the flow by
 *                         default — true for the "pre_*" points whose interrupt always cancelled
 *                         something, false for the informational ones whose interrupt never had an
 *                         effect (overridable per step with {@code stop-on-interrupt:})
 */
public record AnchorSpec(String point, boolean stopsOnInterrupt, String description) {

    public String name() {
        int colon = point.lastIndexOf(':');
        return colon < 0 ? point : point.substring(colon + 1);
    }
}
