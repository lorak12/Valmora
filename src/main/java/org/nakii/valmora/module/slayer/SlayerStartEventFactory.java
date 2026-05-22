package org.nakii.valmora.module.slayer;

import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.util.Formatter;

public class SlayerStartEventFactory implements EventFactory {

    private final SlayerModule slayerModule;
    private final Valmora plugin;

    public SlayerStartEventFactory(SlayerModule slayerModule, Valmora plugin) {
        this.slayerModule = slayerModule;
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "slayer_start"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return ctx -> {};
        String slayerType = args[0];
        int tier;
        try { tier = Integer.parseInt(args[1]); } catch (NumberFormatException e) { return ctx -> {}; }
        final int finalTier = tier;

        return ctx -> ctx.getPlayerCaster().ifPresent(player -> startTask(player, slayerType, finalTier));
    }

    private void startTask(Player player, String slayerType, int tierNum) {
        SlayerDefinition def = slayerModule.getDefinition(slayerType);
        if (def == null) {
            player.sendMessage(Formatter.format("<red>Unknown slayer type: " + slayerType));
            return;
        }
        SlayerTier tier = def.getTier(tierNum);
        if (tier == null) {
            player.sendMessage(Formatter.format("<red>Tier " + tierNum + " does not exist for " + def.getName()));
            return;
        }

        ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (session == null || session.getActiveProfile() == null) return;
        ValmoraProfile profile = session.getActiveProfile();

        // Deny if task already active
        String active = (String) profile.getVariables().get("slayer.active");
        if (active != null && !active.isBlank()) {
            player.sendMessage(Formatter.format("<red>You already have an active slayer task. Finish it first."));
            return;
        }

        // Economy check
        if (tier.getCost() > 0) {
            var eco = plugin.getEconomy();
            if (eco != null && !eco.hasCoins(player, tier.getCost())) {
                player.sendMessage(Formatter.format(
                        "<red>You need <gold>" + (int) tier.getCost() + " coins</gold> to start this slayer task."));
                return;
            }
            if (eco != null) eco.removeCoins(player, tier.getCost());
        }

        // Set task state
        String taskKey = slayerType + ":" + tierNum;
        profile.getVariables().put("slayer.active", taskKey);
        profile.getVariables().put("slayer.kills", 0);
        profile.getVariables().remove("slayer.boss");

        player.sendMessage(Formatter.format(
                "<gold>[Slayer] <green>Task started: <white>" + def.getName() + " <gray>(Tier " + tierNum + ")"));
        player.sendMessage(Formatter.format(
                "<gray>Kill <white>" + tier.getKillsRequired() + " <gray>" + tier.getTargetCategory().toLowerCase()
                + " mobs to summon the boss."));
    }
}
