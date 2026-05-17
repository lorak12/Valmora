package org.nakii.valmora.module.skill;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

public class SkillListener implements Listener {

    private final Valmora plugin;

    public SkillListener(Valmora plugin) {
        this.plugin = plugin;
    }

    private ValmoraProfile getProfile(Player player) {
        ValmoraPlayer vp = plugin.getPlayerManager().getSession(player.getUniqueId());
        if (vp == null) return null;
        return vp.getActiveProfile();
    }

    @EventHandler
    public void onSkillXpGain(SkillXpGainEvent event) {
        String message = "<aqua>+<yellow>" + event.getXp() + " <aqua>" + event.getSkill().getName() + " XP";
        plugin.getUIManager().getActionBar().showTemporary(event.getPlayer(), message, 20);
    }

    @EventHandler
    public void onSkillLevelUp(SkillLevelUpEvent event) {
        plugin.getUIManager().getChat().sendLevelUp(event.getPlayer(), event.getSkill().getName(), event.getNewLevel());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ValmoraProfile profile = getProfile(event.getPlayer());
        if (profile == null) return;
        String blockId = event.getBlock().getType().name();
        for (SkillDefinition skill : plugin.getSkillModule().getSkillRegistry().values()) {
            Double xp = skill.getSourceXp("BLOCK_BREAK", blockId);
            if (xp != null && xp > 0) {
                profile.getSkillManager().addXp(skill.getId(), xp, event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();
        ValmoraProfile profile = getProfile(killer);
        if (profile == null) return;
        String mobId = event.getEntityType().name();
        for (SkillDefinition skill : plugin.getSkillModule().getSkillRegistry().values()) {
            Double xp = skill.getSourceXp("MOB_KILL", mobId);
            if (xp != null && xp > 0) {
                profile.getSkillManager().addXp(skill.getId(), xp, killer);
            }
        }
    }

    @EventHandler
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        if (event.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.getCaught() == null) return;
        ValmoraProfile profile = getProfile(event.getPlayer());
        if (profile == null) return;

        String caughtId = "COD";
        if (event.getCaught() instanceof org.bukkit.entity.Item item) {
            caughtId = item.getItemStack().getType().name();
        }

        for (SkillDefinition skill : plugin.getSkillModule().getSkillRegistry().values()) {
            Double xp = skill.getSourceXp("FISHING", caughtId);
            if (xp != null && xp > 0) {
                profile.getSkillManager().addXp(skill.getId(), xp, event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropHarvest(BlockBreakEvent event) {
        ValmoraProfile profile = getProfile(event.getPlayer());
        if (profile == null) return;
        String blockType = event.getBlock().getType().name();
        for (SkillDefinition skill : plugin.getSkillModule().getSkillRegistry().values()) {
            Double xp = skill.getSourceXp("CROP_HARVEST", blockType);
            if (xp != null && xp > 0) {
                profile.getSkillManager().addXp(skill.getId(), xp, event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        String resultType = event.getRecipe().getResult().getType().name();
        for (SkillDefinition skill : plugin.getSkillModule().getSkillRegistry().values()) {
            Double xp = skill.getSourceXp("CRAFT_ITEM", resultType);
            if (xp != null && xp > 0) {
                profile.getSkillManager().addXp(skill.getId(), xp, player);
            }
        }
    }

    @EventHandler
    public void onBrew(BrewEvent event) {
        for (SkillDefinition skill : plugin.getSkillModule().getSkillRegistry().values()) {
            Double xp = skill.getSourceXp("BREW_POTION", "ANY");
            if (xp != null && xp > 0) {
                event.getContents().getViewers().forEach(viewer -> {
                    if (!(viewer instanceof Player player)) return;
                    ValmoraProfile profile = getProfile(player);
                    if (profile == null) return;
                    profile.getSkillManager().addXp(skill.getId(), xp, player);
                });
            }
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        ValmoraProfile profile = getProfile(event.getEnchanter());
        if (profile == null) return;
        for (SkillDefinition skill : plugin.getSkillModule().getSkillRegistry().values()) {
            Double xp = skill.getSourceXp("ENCHANT_ITEM", event.getItem().getType().name());
            if (xp != null && xp > 0) {
                profile.getSkillManager().addXp(skill.getId(), xp, event.getEnchanter());
            }
        }
    }
}

