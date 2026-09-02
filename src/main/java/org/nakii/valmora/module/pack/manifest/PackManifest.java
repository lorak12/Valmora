package org.nakii.valmora.module.pack.manifest;

import java.util.List;

/**
 * The parsed shape of one pack's {@code pack.yml} (docs/modules/design/pack.md §2). {@code id} both
 * identifies the pack and becomes the mandatory namespace prefix ({@code <id>:<local_id>}) every
 * content id it registers carries — see {@link org.nakii.valmora.module.pack.PackNamespacer}.
 *
 * @param id the pack's own id — also its content-id namespace prefix
 * @param name display name
 * @param version the pack's own semver version
 * @param author author/attribution string
 * @param description one-line description
 * @param engineVersionMin minimum compatible engine (plugin) version, required
 * @param engineVersionMax maximum compatible engine version, or {@code null} for no upper bound
 * @param dependsPlugins other Bukkit plugin names this pack requires present
 * @param dependsPacks other packs this pack hard-depends on (install/enable fails if unmet)
 * @param softDependsPacks other packs this pack soft-depends on (missing = warning only)
 * @param providesContent content-folder entries this pack ships (copied under {@code <folder>/<id>/...})
 * @param providesShared shared/misc config files this pack non-destructively merges into
 * @param checksum the pack's self-declared archive checksum, or {@code null}
 */
public record PackManifest(
        String id,
        String name,
        String version,
        String author,
        String description,
        String engineVersionMin,
        String engineVersionMax,
        List<String> dependsPlugins,
        List<PackDependency> dependsPacks,
        List<PackDependency> softDependsPacks,
        List<String> providesContent,
        List<String> providesShared,
        String checksum
) {
}
