package org.nakii.valmora.module.blockloot;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.MiningFortune;

/**
 * Rolls and gives a global block-loot override's drop table for a break. Mirrors
 * {@code module.resource.ResourceManager.handleBlockBreak}'s Silk Touch / Mining Fortune logic,
 * simplified — no stages, regen, required-power, or pipeline hooks, since this is a one-shot
 * global override rather than a mineable node.
 */
public class BlockLootManager {

    private final Valmora plugin;
    private final BlockLootRegistry registry;

    public BlockLootManager(Valmora plugin, BlockLootRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    /**
     * @return {@code true} if {@code block}'s material has a configured override and this break
     * was fully handled (drops already given) — the caller should suppress vanilla drops.
     * {@code false} means the block isn't configured and vanilla handling should apply.
     */
    public boolean handleBlockBreak(Player player, Block block) {
        BlockLootConfig config = registry.get(block.getType());
        if (config == null) return false;

        // Vanilla Silk Touch (mirrors resource.silk-touch.enabled's exact semantics): exactly 1 of
        // the block's own material, ignoring the loot table and Mining Fortune — vanilla Silk
        // Touch ignores Fortune too.
        boolean silkTouch = plugin.getConfig().getBoolean("block-loot.silk-touch.enabled", true)
                && player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH);

        Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);

        if (silkTouch) {
            giveItem(player, config.material().name(), 1, dropLocation);
        } else {
            double miningFortune = MiningFortune.getPlayerMiningFortune(player);
            for (BlockLootDrop drop : config.drops()) {
                if (Math.random() < drop.chance()) {
                    int amount = MiningFortune.applyFortune(drop.rollAmount(), miningFortune);
                    giveItem(player, drop.itemId(), amount, dropLocation);
                }
            }
        }
        return true;
    }

    private void giveItem(Player player, String itemId, int amount, Location location) {
        ItemStack item = plugin.getItemManager().createItemStack(itemId);
        if (item == null) {
            plugin.getLogger().warning("[BlockLoot] Unknown item id '" + itemId + "' in a block-loot drop — skipped.");
            return;
        }
        item.setAmount(amount);
        plugin.getItemManager().giveOrPrivateDrop(player, item, location);
    }
}
