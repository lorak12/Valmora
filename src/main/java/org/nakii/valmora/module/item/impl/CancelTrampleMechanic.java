package org.nakii.valmora.module.item.impl;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;

/**
 * Marker mechanic granting crop-trample immunity while worn (e.g. Rancher's Boots' "Farmer's
 * Grace"). Unlike every other mechanic, this one has no meaningful "execute" step of its own — its
 * presence in an item's ability list is checked directly by {@code TrampleListener} at the moment
 * a player would trample farmland, rather than being fired through the normal ability-trigger
 * pipeline (a passive "immunity" doesn't fit the activate-once shape {@link AbilityMechanic}s
 * usually have). Give it any trigger in YAML (conventionally {@code PASSIVE}) — the trigger is
 * never actually checked for this one.
 */
public class CancelTrampleMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "CANCEL_TRAMPLE";
    }

    @Override
    public void execute(ExecutionContext context) {
        // Intentionally no-op — see class Javadoc. TrampleListener checks for this mechanic's
        // presence directly rather than invoking it through the trigger pipeline.
    }
}
