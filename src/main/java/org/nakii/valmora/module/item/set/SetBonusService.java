package org.nakii.valmora.module.item.set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.item.ItemDefinition;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.util.Keys;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies armor set-bonus stats during stat recalculation. Counts how many pieces of each set the
 * player wears, then grants every tier whose {@code pieces-required} threshold is met (cumulative).
 */
public final class SetBonusService {

    private SetBonusService() {}

    public static void applyTo(Player player, StatManager statManager) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        SetBonusRegistry registry = api.getItemManager().getSetBonusRegistry();
        if (registry == null) return;

        // Count worn pieces per set id.
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            String itemId = armor.getItemMeta().getPersistentDataContainer()
                    .get(Keys.ITEM_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING);
            if (itemId == null) continue;
            ItemDefinition def = api.getItemManager().getItemRegistry().getItem(itemId).orElse(null);
            if (def == null || def.getSet() == null) continue;
            counts.merge(def.getSet().toLowerCase(), 1, Integer::sum);
        }

        // Apply each set's matching tiers.
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            registry.get(entry.getKey()).ifPresent(bonus -> {
                int worn = entry.getValue();
                for (SetBonusDefinition.Tier tier : bonus.tiers()) {
                    if (worn >= tier.piecesRequired()) {
                        tier.stats().forEach(statManager::addModifier);
                    }
                }
            });
        }
    }
}
