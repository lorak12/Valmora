package org.nakii.valmora.module.worldrules;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

import java.util.logging.Logger;

/**
 * VANILLA_CONTROL_AUDIT.md §8 high-priority gap: "GameRules wholesale — none are set anywhere in
 * Valmora — biggest environmental cluster". This module is deliberately a thin, purely config-driven
 * pass-through: it changes nothing unless {@code world.gamerules.<GAME_RULE_NAME>} is explicitly set
 * in config.yml, applied to every currently-loaded world on enable/reload and to every world loaded
 * afterward (see {@link WorldRulesListener}). No default ruleset is shipped enabled — see the
 * commented examples in config.yml — since the "right" RPG defaults (keepInventory, natural
 * regeneration, fire/fall/drowning damage, etc.) are a game-design call, not a code one.
 *
 * <p>Registered first (alongside {@code script}/{@code time}) since it has no dependencies and
 * several later modules (combat's damage gates, alchemy) may eventually want to read these same
 * values — see {@code docs/modules/design/INTEGRATION.md} for the module dependency map.
 */
public class WorldRulesModule implements ReloadableModule {

    private final Valmora plugin;
    private final WorldRulesListener listener;

    public WorldRulesModule(Valmora plugin) {
        this.plugin = plugin;
        this.listener = new WorldRulesListener(this);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        for (World world : Bukkit.getWorlds()) {
            applyTo(world);
        }
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(listener);
    }

    @Override
    public String getId() {
        return "world_rules";
    }

    @Override
    public String getName() {
        return "World Rules";
    }

    /** Applies every configured {@code world.gamerules.*} entry to {@code world}; unset keys are left untouched. */
    public void applyTo(World world) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("world.gamerules");
        if (section == null) return;
        Logger log = plugin.getLogger();
        for (String key : section.getKeys(false)) {
            GameRule<?> rule = GameRule.getByName(key);
            if (rule == null) {
                log.warning("[WorldRules] Unknown game rule '" + key + "' in config.yml world.gamerules — skipping.");
                continue;
            }
            Class<?> type = rule.getType();
            Object value;
            if (type == Boolean.class) {
                value = section.getBoolean(key);
            } else if (type == Integer.class) {
                value = section.getInt(key);
            } else {
                log.warning("[WorldRules] Game rule '" + key + "' has an unsupported value type " + type
                        + " — skipping.");
                continue;
            }
            @SuppressWarnings("unchecked")
            GameRule<Object> objectRule = (GameRule<Object>) rule;
            world.setGameRule(objectRule, value);
        }
    }
}
