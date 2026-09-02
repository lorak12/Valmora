package org.nakii.valmora.module.pack.validate;

import java.util.List;

/**
 * Extension point for content-type-specific reference-integrity checks against a pack about to be
 * installed (e.g. "does every item id this pack's quests reference actually exist, either in this
 * pack's own namespace or in base content?"). Deliberately generic: {@link PackValidator} itself
 * knows nothing about items, quests, or any other content type — a module that wants its content
 * checked when a pack references it registers an implementation via
 * {@link PackReferenceCheckerRegistry}, the same "extension point instead of special-casing" pattern
 * {@code ScriptModule}'s {@code VariableProvider}/{@code EventFactory} registries use (CLAUDE.md §10.4).
 *
 * <p><b>Status:</b> the registry mechanism exists and is wired into {@link PackValidator}, but no
 * concrete checkers are registered yet — wiring one in for a given content type (item refs from
 * quests, machine ids from recipes, etc.) is follow-up work per content type, matching how
 * {@code ModifierValidator} and {@code MachineModule}'s validator are today the only two content
 * types with any cross-reference validation at all.
 */
@FunctionalInterface
public interface PackReferenceChecker {

    /**
     * Checks references belonging to the pack {@code packId} (already installed into the live/shadow
     * registries at the time this runs). Returns warning strings for any dangling reference found;
     * an empty list means nothing to report.
     */
    List<String> check(String packId);
}
