package org.nakii.valmora.module.alchemy;

import org.bukkit.event.HandlerList;
import org.bukkit.potion.PotionEffectType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.alchemy.brewing.AlchemyMachineHandler;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffectLoader;
import org.nakii.valmora.module.alchemy.effect.hardcoded.AbsorptionAlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.hardcoded.DamageAlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.hardcoded.HealingAlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.hardcoded.PoisonAlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.hardcoded.VanillaAlchemyEffect;
import org.nakii.valmora.module.alchemy.gui.AlchemyVariableProvider;
import org.nakii.valmora.module.alchemy.modifier.AlchemyModifier;
import org.nakii.valmora.module.alchemy.modifier.AlchemyModifierType;

import java.io.File;
import java.util.List;
import java.util.Map;

public class AlchemyModule implements ReloadableModule {

    private final Valmora plugin;
    private final AlchemyManager alchemyManager;

    private AlchemyListener listener;
    private int tickTaskId = -1;

    public AlchemyModule(Valmora plugin) {
        this.plugin = plugin;
        int maxEffects = plugin.getConfig().getInt("alchemy.max-active-effects", 10);
        this.alchemyManager = new AlchemyManager(maxEffects);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Initializing Alchemy System...");

        alchemyManager.clear();

        YamlLoader<AlchemyEffect> loader = new YamlLoader<>(plugin, "alchemy", "Alchemy Effect");
        loader.load(AlchemyEffectLoader.parser(), alchemyManager::registerEffect);

        registerHardcodedEffects();

        loadModifiers();

        plugin.getRecipeModule().getRecipeEngine()
                .registerHandler("alchemy", new AlchemyMachineHandler(plugin, alchemyManager));

        plugin.getScriptModule().registerProvider(new AlchemyVariableProvider());

        this.listener = new AlchemyListener(alchemyManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        int intervalTicks = plugin.getConfig().getInt("alchemy.tick-interval", 20);
        tickTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
                alchemyManager.tick(player);
            }
        }, 20L, intervalTicks).getTaskId();
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Alchemy System...");

        if (tickTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }

        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }

        alchemyManager.clear();
    }

    @Override
    public String getId() { return "alchemy"; }

    @Override
    public String getName() { return "Alchemy System"; }

    public AlchemyManager getAlchemyManager() { return alchemyManager; }

    // ── Hardcoded effect registration ────────────────────────────────────

    private void registerHardcodedEffects() {
        // Vanilla potion effects
        alchemyManager.registerHardcodedEffect(new VanillaAlchemyEffect("jump_boost",    PotionEffectType.JUMP_BOOST,      true));
        alchemyManager.registerHardcodedEffect(new VanillaAlchemyEffect("night_vision",  PotionEffectType.NIGHT_VISION,    false));
        alchemyManager.registerHardcodedEffect(new VanillaAlchemyEffect("invisibility",  PotionEffectType.INVISIBILITY,    false));
        alchemyManager.registerHardcodedEffect(new VanillaAlchemyEffect("fire_resistance", PotionEffectType.FIRE_RESISTANCE, false));

        // Custom mechanics
        alchemyManager.registerHardcodedEffect(new HealingAlchemyEffect());
        alchemyManager.registerHardcodedEffect(new PoisonAlchemyEffect());
        alchemyManager.registerHardcodedEffect(new AbsorptionAlchemyEffect());
        alchemyManager.registerHardcodedEffect(new DamageAlchemyEffect());
    }

    // ── Modifier loading ─────────────────────────────────────────────────

    private void loadModifiers() {
        File modifiersFile = new File(plugin.getDataFolder(), "alchemy/modifiers.yml");
        if (!modifiersFile.exists()) {
            plugin.saveResource("alchemy/modifiers.yml", false);
        }

        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(modifiersFile);

        loadModifierCategory(cfg, "level", AlchemyModifierType.LEVEL);
        loadModifierCategory(cfg, "duration", AlchemyModifierType.DURATION);
        loadModifierCategory(cfg, "splash", AlchemyModifierType.SPLASH);

        plugin.getLogger().info("Loaded " + countModifiers(cfg) + " alchemy modifier(s).");
    }

    private void loadModifierCategory(org.bukkit.configuration.file.YamlConfiguration cfg,
                                      String section, AlchemyModifierType type) {
        List<?> entries = cfg.getList(section);
        if (entries == null) return;

        for (Object obj : entries) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            try {
                String itemId = (String) map.get("item");
                if (itemId == null) continue;

                boolean requiresMaxBase = Boolean.TRUE.equals(map.get("requires-max-base"));

                AlchemyModifier modifier = switch (type) {
                    case LEVEL -> {
                        int bonus = ((Number) map.get("bonus")).intValue();
                        yield new AlchemyModifier(itemId, type, bonus, 0, 1.0, requiresMaxBase);
                    }
                    case DURATION -> {
                        int seconds = ((Number) map.get("seconds")).intValue();
                        yield new AlchemyModifier(itemId, type, 0, seconds, 1.0, requiresMaxBase);
                    }
                    case SPLASH -> {
                        double mult = ((Number) map.get("duration-multiplier")).doubleValue();
                        yield new AlchemyModifier(itemId, type, 0, 0, mult, requiresMaxBase);
                    }
                };

                alchemyManager.registerModifier(modifier);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load alchemy modifier entry in section '" + section + "': " + e.getMessage());
            }
        }
    }

    private int countModifiers(org.bukkit.configuration.file.YamlConfiguration cfg) {
        int count = 0;
        for (String section : List.of("level", "duration", "splash")) {
            List<?> list = cfg.getList(section);
            if (list != null) count += list.size();
        }
        return count;
    }
}
