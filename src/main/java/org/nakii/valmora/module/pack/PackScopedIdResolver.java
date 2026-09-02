package org.nakii.valmora.module.pack;

import org.nakii.valmora.api.registry.Registry;

/**
 * Resolves a bare (un-namespaced) content id written inside a pack's own YAML — e.g. a quest
 * referencing an item by its plain local id — against that pack's namespace first, falling back to
 * a bare global lookup so pack authors don't have to spell out {@code <pack_id>:} everywhere inside
 * their own files. An id that's already namespaced (contains {@code ':'}) is assumed to be a
 * deliberate cross-pack or explicit reference and is never rewritten.
 *
 * <p>This is a generic utility, not wired into any specific content type's loader — each content
 * type's own cross-reference resolution (quest→item, recipe→item, modifier→item, ...) opts in by
 * calling {@link #resolve} at the point it currently does a raw {@code registry.get(id)}. Only
 * {@link org.nakii.valmora.module.pack.validate.PackValidator}'s reference-integrity pass uses it
 * today (see its class doc for the per-content-type wiring status).
 */
public final class PackScopedIdResolver {

    private PackScopedIdResolver() {}

    /**
     * Resolves {@code rawId} against {@code currentPackId}'s namespace first (if the qualified form
     * exists in {@code registry}), then falls back to the raw id unchanged. If {@code rawId} is
     * already namespaced or {@code currentPackId} is {@code null}, returns {@code rawId} unchanged.
     */
    public static String resolve(String rawId, String currentPackId, Registry<?> registry) {
        if (rawId == null || rawId.contains(":") || currentPackId == null) {
            return rawId;
        }
        String qualified = currentPackId + ":" + rawId;
        if (registry.contains(qualified)) {
            return qualified;
        }
        return rawId;
    }
}
