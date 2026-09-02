package org.nakii.valmora.module.pack;

import org.nakii.valmora.infrastructure.config.YamlLoader;

/**
 * Wires {@link PackFileIndex} into every {@link YamlLoader} in the engine as a single, central
 * choke point: content ids parsed from a file that {@link PackFileIndex} says belongs to a pack are
 * transparently rewritten to {@code <pack_id>:<local_id>} before any loader's parser or
 * {@code registerAction} ever sees them. This is what makes mandatory namespacing "free" for every
 * content type — no per-loader changes were needed across the ~19 {@code YamlLoader} call sites,
 * matching the modifier framework's "no group/type-specific Java" precedent taken one step further.
 *
 * <p>Base/vanilla content (files {@link PackFileIndex} has no owner for) passes through unchanged.
 * An id that's already namespaced (contains {@code ':'}) is also passed through unchanged, so a
 * pack author who writes a fully-qualified cross-pack reference as a literal id isn't double-prefixed.
 */
public final class PackNamespacer {

    private PackNamespacer() {}

    /**
     * Installs {@code index} as the active namespacing source for every {@link YamlLoader} in the
     * JVM. Called once from {@link PackModule#onEnable()}; a {@code null} index (or never calling
     * this at all) leaves {@link YamlLoader} qualifying nothing, which is the correct behavior
     * before the pack module has enabled or after it's disabled.
     */
    public static void install(PackFileIndex index) {
        if (index == null) {
            YamlLoader.setIdQualifier(null);
            return;
        }
        YamlLoader.setIdQualifier((id, filePath) -> qualify(index, id, filePath));
    }

    /** Uninstalls the hook — every {@link YamlLoader} goes back to passing ids through unchanged. */
    public static void uninstall() {
        YamlLoader.setIdQualifier(null);
    }

    static String qualify(PackFileIndex index, String id, String filePath) {
        if (id == null || id.contains(":")) {
            return id;
        }
        return index.ownerOf(filePath)
                .map(packId -> packId + ":" + id)
                .orElse(id);
    }
}
