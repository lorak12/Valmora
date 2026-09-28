package org.nakii.valmora.api.pipeline.flow;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Every flow domain that has been loaded — backs {@code /valmora pipeline flows} and
 * {@code pipeline_set}'s runtime key check. Held by {@code ScriptModule} next to the
 * {@code HookBus} (and, like it, survives reloads; domains re-register on every load).
 */
public final class FlowRegistry {

    private final Map<String, FlowDomain> domains = new ConcurrentSkipListMap<>();

    public void register(FlowDomain domain) {
        domains.put(domain.id().toLowerCase(Locale.ROOT), domain);
    }

    public Optional<FlowDomain> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(domains.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<FlowDomain> all() {
        return Collections.unmodifiableCollection(domains.values());
    }
}
