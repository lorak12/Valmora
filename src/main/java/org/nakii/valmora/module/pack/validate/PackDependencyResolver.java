package org.nakii.valmora.module.pack.validate;

import org.nakii.valmora.module.pack.SemVer;
import org.nakii.valmora.module.pack.manifest.PackDependency;
import org.nakii.valmora.module.pack.manifest.PackManifest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the install order for a batch of candidate packs against each other and against already
 * installed packs: topologically sorts hard/soft {@code depends.packs} edges (Kahn's algorithm),
 * detects cycles among the candidates, and checks every dependency's presence and version
 * constraint. Missing/incompatible <em>hard</em> dependencies and cycles are errors (block install);
 * missing/incompatible <em>soft</em> dependencies are warnings only.
 */
public final class PackDependencyResolver {

    private PackDependencyResolver() {}

    public record Result(List<String> installOrder, PackValidationReport report) {
    }

    /**
     * @param candidates   packs being installed in this batch, keyed by pack id (case-insensitive
     *                     lookup is the caller's responsibility — ids are compared as given)
     * @param alreadyInstalled packs already installed on the server, keyed by pack id
     */
    public static Result resolve(Map<String, PackManifest> candidates, Map<String, PackManifest> alreadyInstalled) {
        PackValidationReport report = new PackValidationReport();

        // Dependency presence + version checks.
        for (PackManifest pack : candidates.values()) {
            checkDependencies(pack, pack.dependsPacks(), false, candidates, alreadyInstalled, report);
            checkDependencies(pack, pack.softDependsPacks(), true, candidates, alreadyInstalled, report);
        }

        // Topological sort of the candidate batch only — already-installed packs are treated as
        // already-satisfied prerequisites, not reordered.
        Map<String, Set<String>> adjacency = new HashMap<>(); // dependency id -> {dependents}
        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : candidates.keySet()) {
            adjacency.put(id, new HashSet<>());
            inDegree.put(id, 0);
        }
        for (PackManifest pack : candidates.values()) {
            for (PackDependency dep : allDeps(pack)) {
                if (candidates.containsKey(dep.id()) && !dep.id().equals(pack.id())) {
                    if (adjacency.get(dep.id()).add(pack.id())) {
                        inDegree.merge(pack.id(), 1, Integer::sum);
                    }
                }
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String next = queue.poll();
            order.add(next);
            for (String dependent : adjacency.get(next)) {
                int updated = inDegree.merge(dependent, -1, Integer::sum);
                if (updated == 0) queue.add(dependent);
            }
        }

        if (order.size() != candidates.size()) {
            Set<String> unresolved = new HashSet<>(candidates.keySet());
            unresolved.removeAll(order);
            report.addError("Circular pack dependency detected among: " + unresolved);
        }

        return new Result(order, report);
    }

    private static List<PackDependency> allDeps(PackManifest pack) {
        List<PackDependency> all = new ArrayList<>(pack.dependsPacks());
        all.addAll(pack.softDependsPacks());
        return all;
    }

    private static void checkDependencies(PackManifest pack, List<PackDependency> deps, boolean soft,
                                           Map<String, PackManifest> candidates, Map<String, PackManifest> alreadyInstalled,
                                           PackValidationReport report) {
        String kind = soft ? "soft dependency" : "dependency";
        for (PackDependency dep : deps) {
            PackManifest resolved = candidates.containsKey(dep.id()) ? candidates.get(dep.id()) : alreadyInstalled.get(dep.id());
            if (resolved == null) {
                String msg = "Pack '" + pack.id() + "' has a missing " + kind + " on '" + dep.id() + "'";
                if (soft) report.addWarning(msg); else report.addError(msg);
                continue;
            }
            if (dep.versionConstraint() != null && !dep.versionConstraint().isBlank()) {
                try {
                    SemVer resolvedVersion = SemVer.parse(resolved.version());
                    if (!resolvedVersion.satisfies(dep.versionConstraint())) {
                        String msg = "Pack '" + pack.id() + "' requires '" + dep.id() + "' " + dep.versionConstraint()
                                + " but found " + resolved.version();
                        if (soft) report.addWarning(msg); else report.addError(msg);
                    }
                } catch (IllegalArgumentException e) {
                    report.addError("Pack '" + pack.id() + "' has an unparseable version constraint on '"
                            + dep.id() + "': " + e.getMessage());
                }
            }
        }
    }
}
