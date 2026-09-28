package org.nakii.valmora.api.pipeline.flow;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Declares one flow of a domain — its id (the key under {@code flows:}), the built-in steps and
 * anchors it may contain, and the keys {@code pipeline_set} may write — and holds its current
 * {@link CompiledFlow}.
 *
 * <p>Instances are long-lived ({@code static final} in the domain's {@code XFlows} class) so the
 * last good compiled flow survives a reload that fails to compile a new one.
 */
public final class FlowSpec<S extends FlowState> {

    private final String domainId;
    private final String id;
    private final Map<String, BuiltinStepFactory<S>> builtins = new LinkedHashMap<>();
    private final Map<String, AnchorSpec> anchors = new LinkedHashMap<>();
    private final List<String> writableKeys = new ArrayList<>();
    private final AtomicReference<CompiledFlow> current = new AtomicReference<>();
    private volatile boolean warnedMissing;

    public FlowSpec(String domainId, String id) {
        this.domainId = domainId;
        this.id = id;
    }

    public FlowSpec<S> builtin(BuiltinStepFactory<S> factory) {
        builtins.put(factory.id().toLowerCase(Locale.ROOT), factory);
        return this;
    }

    public FlowSpec<S> anchor(String point, boolean stopsOnInterrupt, String description) {
        anchors.put(point.toLowerCase(Locale.ROOT), new AnchorSpec(point, stopsOnInterrupt, description));
        return this;
    }

    public FlowSpec<S> writable(String... keys) {
        Collections.addAll(writableKeys, keys);
        return this;
    }

    /**
     * Runs the current compiled flow. If none was ever compiled (the domain never loaded), nothing
     * runs and {@link FlowResult#SKIPPED} is returned — the caller's vanilla/fallback behaviour
     * applies — and that's logged once.
     */
    public FlowResult run(ExecutionContext ctx, S state) {
        CompiledFlow flow = current.get();
        if (flow == null) {
            if (!warnedMissing) {
                warnedMissing = true;
                Valmora plugin = Valmora.getInstance();
                if (plugin != null) {
                    plugin.getLogger().warning("[Pipeline] Flow '" + key() + "' was never loaded — skipping it.");
                }
            }
            return FlowResult.SKIPPED;
        }
        return flow.run(ctx, state);
    }

    public String domainId() {
        return domainId;
    }

    public String id() {
        return id;
    }

    public String key() {
        return domainId + "." + id;
    }

    public BuiltinStepFactory<S> builtin(String name) {
        return name == null ? null : builtins.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<BuiltinStepFactory<S>> builtins() {
        return Collections.unmodifiableCollection(builtins.values());
    }

    public AnchorSpec anchorSpec(String point) {
        return point == null ? null : anchors.get(point.toLowerCase(Locale.ROOT));
    }

    public Collection<AnchorSpec> anchors() {
        return Collections.unmodifiableCollection(anchors.values());
    }

    public List<String> writableKeys() {
        return Collections.unmodifiableList(writableKeys);
    }

    public CompiledFlow current() {
        return current.get();
    }

    /** Installs a newly compiled flow (atomic swap); {@code null} uninstalls it (tests). */
    public void install(CompiledFlow flow) {
        current.set(flow);
        if (flow != null) warnedMissing = false;
    }
}
