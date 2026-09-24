package org.nakii.valmora.module.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.util.Keys;

/**
 * VANILLA_CONTROL_AUDIT.md §18 — "A single EntityTargetEvent listener is the highest-leverage
 * hook for RPG mob AI". Prior to this listener nothing in the plugin ever intercepted vanilla's own
 * target selection, so Valmora mobs could never be restricted from aggroing NPCs, tag-gated players,
 * players standing in a given zone, etc. beyond the blunt {@code ai.aggro-range} FOLLOW_RANGE knob.
 *
 * <p>Deliberately narrow in scope: this does not replace vanilla's target *selection* (which mob
 * looks at which candidate, LoS, etc. — still entirely vanilla AI), it only gates whether a
 * selection is allowed to stick, via {@code ai.target-conditions} / {@code ai.ignore-npcs} on the
 * attacking mob's {@link MobDefinition}. Custom pathfinder goals (audit item #15) are a separate,
 * larger follow-up.
 */
public class MobTargetListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity attacker)) return;
        LivingEntity candidate = event.getTarget();
        if (candidate == null) return; // target being cleared — nothing to gate

        String mobId = attacker.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        if (mobId == null) return; // not a Valmora-defined mob — leave vanilla targeting alone

        MobDefinition definition = ValmoraAPI.getInstance().getMobManager().getMobRegistry().getMob(mobId).orElse(null);
        if (definition == null) return;

        if (definition.isIgnoreNpcs()
                && candidate.getPersistentDataContainer().has(Keys.NPC_ID_KEY, PersistentDataType.STRING)) {
            event.setCancelled(true);
            return;
        }

        if (!definition.getTargetConditionStrings().isEmpty()) {
            var ctx = new SimpleExecutionContext(candidate, attacker, candidate.getLocation(), null);
            if (!definition.canTarget(ctx)) {
                event.setCancelled(true);
            }
        }
    }
}
