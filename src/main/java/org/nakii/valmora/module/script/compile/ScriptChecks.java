package org.nakii.valmora.module.script.compile;

import org.nakii.valmora.infrastructure.config.diag.ConfigSource;
import org.nakii.valmora.infrastructure.config.diag.DiagnosticSink;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.Severity;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Script checks that need every module enabled first. Currently: {@code $namespace.path$}
 * variables whose namespace no module provides (a typo like {@code $plyer.level$} silently reads
 * as null). Namespaces are recorded while content compiles and checked in {@link #report}.
 *
 * <p>Only dotted variables are checked — a bare {@code $amount$} is usually a parameter the
 * calling code supplies — and names an entry declares as local (GUI loop variables) are skipped.
 * Off with {@code diagnostics.unknown-variable-namespace: off}.
 */
public final class ScriptChecks {

    private static final int MAX_SOURCES = 20;

    private final Map<String, Set<ConfigSource>> namespaces = new ConcurrentHashMap<>();

    /** Called for every variable token the expression parser sees. */
    public void onVariable(String token) {
        LoadScope scope = LoadScope.current().orElse(null);
        if (scope == null || token == null) return;
        String path = token.startsWith("$") && token.endsWith("$") && token.length() > 1
                ? token.substring(1, token.length() - 1) : token;
        int dot = path.indexOf('.');
        if (dot <= 0) return;
        String ns = path.substring(0, dot).toLowerCase();
        if (scope.isLocal(ns) || scope.isLocal("*")) return;
        Set<ConfigSource> sources = namespaces.computeIfAbsent(ns, k -> ConcurrentHashMap.newKeySet());
        if (sources.size() < MAX_SOURCES) sources.add(scope.source());
    }

    /** Reports namespaces not in {@code known} and forgets what was recorded. */
    public void report(Collection<String> known, DiagnosticSink sink) {
        Map<String, Set<ConfigSource>> recorded = new java.util.HashMap<>(namespaces);
        namespaces.clear();
        if (!enabled()) return;
        Set<String> knownLower = new java.util.HashSet<>();
        for (String k : known) knownLower.add(k.toLowerCase());
        for (Map.Entry<String, Set<ConfigSource>> e : recorded.entrySet()) {
            if (knownLower.contains(e.getKey())) continue;
            List<ConfigSource> sources = new ArrayList<>(e.getValue());
            ConfigSource first = sources.get(0);
            StringBuilder msg = new StringBuilder("unknown variable namespace '$").append(e.getKey())
                    .append(".…$' — no module provides it, so it reads as null");
            if (sources.size() > 1) {
                msg.append("; also used in ");
                sources.stream().skip(1).limit(3).forEach(s -> msg.append(s.describe()).append(", "));
                msg.setLength(msg.length() - 2);
                if (sources.size() > 4) msg.append(" (+").append(sources.size() - 4).append(" more)");
            }
            sink.accept(first.diagnostic(Severity.WARN, msg.toString(), Suggestions.hint(e.getKey(), known)));
        }
    }

    private static boolean enabled() {
        try {
            var plugin = org.nakii.valmora.Valmora.getInstance();
            if (plugin != null && plugin.getConfig() != null) {
                return !"off".equalsIgnoreCase(plugin.getConfig().getString("diagnostics.unknown-variable-namespace", "warn"));
            }
        } catch (RuntimeException ignored) {
            // no server (tests)
        }
        return true;
    }
}
