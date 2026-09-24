package org.nakii.valmora.infrastructure.config.diag;

import java.util.function.Supplier;

/**
 * Runs a compile step (a script list, a condition, a formula) "at" a location inside the entry
 * being loaded, so anything the DSL compilers report points at e.g. {@code abilities.dash.conditions}
 * rather than just the entry.
 */
public final class ScriptCompile {

    private ScriptCompile() {}

    /** Runs {@code body} with {@code scope.sub(path)} as the current scope (just runs it if {@code scope} is null). */
    public static <T> T at(LoadScope scope, String path, Supplier<T> body) {
        if (scope == null) return body.get();
        try (LoadScope ignored = scope.sub(path).push()) {
            return body.get();
        }
    }

    /** {@link #at(LoadScope, String, Supplier)} relative to the current scope, if any. */
    public static <T> T at(String path, Supplier<T> body) {
        return at(LoadScope.current().orElse(null), path, body);
    }
}
