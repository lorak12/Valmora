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
        this.alchemyManager = new AlchemyManager(plugin.getConfig().getInt("alchemy.max-active-effects", 10));
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Initializing Alchemy System...");

        alchemyManager.setMaxActiveEffects(plugin.getConfig().getInt("alchemy.max-active-effects", 10));
        alchemyManager.clear();

        // alchemy/modifiers.yml has its own format and loader (loadModifiers) — not effect entries.
        YamlLoader<AlchemyEffect> loader = new YamlLoader<AlchemyEffect>(plugin, "alchemy", "Alchemy Effect").ignoreFiles("modifiers.yml");
        loader.load(AlchemyEffectLoader.parser(), alchemyManager::registerEffect);

        registerHardcodedEffects();

        loadModifiers();

        plugin.getRecipeModule().getRecipeEngine()
                .registerHandler("alchemy", new AlchemyMachineHandler(plugin, alchemyManager));

        plugin.getScriptModule().registerProvider(new AlchemyVariableProvider());

        double splashRadius = plugin.getConfig().getDouble("alchemy.splash-radius", 4.0);
        this.listener = new AlchemyListener(alchemyManager, splashRadius);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        int intervalTicks = plugin.getConfig().getInt("alchemy.tick-interval", 20);
        tickTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Drives every entity currently holding an active effect, not just online players —
            // e.g. a mob hit by a DEBUFF splash/lingering potion now also gets onTick (poison DOT).
            for (java.util.UUID uuid : alchemyManager.getTrackedEntityIds()) {
                org.bukkit.entity.Entity entity = plugin.getServer().getEntity(uuid);
                if (entity instanceof org.bukkit.entity.LivingEntity living) {
                    alchemyManager.tick(living);
                }
            }
        }, 20L, intervalTicks).getTaskId();
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Alchemy System...");

        if (plugin.getRecipeModule() != null) {
            plugin.getRecipeModule().unregisterHandler("alchemy");
        }

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

        // HC-192: alchemy.effects.vanilla.<id>: {type, amplifier-scales} — additional
        // vanilla-potion-backed alchemy effects without a code change (e.g. "blindness").
        var vanillaSec = plugin.getConfig().getConfigurationSection("alchemy.effects.vanilla");
        if (vanillaSec != null) {
            for (String effectId : vanillaSec.getKeys(false)) {
                var entrySec = vanillaSec.getConfigurationSection(effectId);
                if (entrySec == null) continue;
                String typeName = entrySec.getString("type", effectId);
                PotionEffectType type = org.bukkit.Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.minecraft(typeName.toLowerCase()));
                if (type == null) {
                    plugin.getLogger().warning("[Alchemy] alchemy.effects.vanilla." + effectId + ": unknown potion type '" + typeName + "'.");
                    continue;
                }
                boolean amplifierScales = entrySec.getBoolean("amplifier-scales", false);
                alchemyManager.registerHardcodedEffect(new VanillaAlchemyEffect(effectId, type, amplifierScales));
            }
        }

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

        try (org.nakii.valmora.infrastructure.config.diag.LoadSession session =
                     org.nakii.valmora.infrastructure.config.diag.LoadSession.open(plugin, "Alchemy modifiers", "alchemy/modifiers.yml")) {
            org.bukkit.configuration.file.YamlConfiguration cfg = session.readYaml(modifiersFile, "alchemy/modifiers.yml");
            if (cfg == null) return;
            for (String key : cfg.getKeys(false)) {
                if (!List.of("level", "duration", "splash").contains(key)) {
                    session.warn("alchemy/modifiers.yml", key, "unknown category — use level, duration or splash",
                            org.nakii.valmora.infrastructure.config.diag.Suggestions.hint(key, List.of("level", "duration", "splash")));
                }
            }
            session.loaded(loadModifierCategory(session, cfg, "level", AlchemyModifierType.LEVEL));
            session.loaded(loadModifierCategory(session, cfg, "duration", AlchemyModifierType.DURATION));
            session.loaded(loadModifierCategory(session, cfg, "splash", AlchemyModifierType.SPLASH));
        }
    }

    private int loadModifierCategory(org.nakii.valmora.infrastructure.config.diag.LoadSession session,
                                     org.bukkit.configuration.file.YamlConfiguration cfg,
                                     String section, AlchemyModifierType type) {
        List<?> entries = cfg.getList(section);
        if (entries == null) return 0;

        int loaded = 0;
        for (int i = 0; i < entries.size(); i++) {
            Object obj = entries.get(i);
            if (!(obj instanceof Map<?, ?> map)) continue;
            try (var scope = session.entry("alchemy/modifiers.yml", section + "[" + i + "]")) {
                Object rawItem = map.get("item");
                if (rawItem == null) {
                    scope.warn("missing item: — entry skipped");
                    continue;
                }
                String itemId = String.valueOf(rawItem);
                scope.sub("item").ref(org.nakii.valmora.infrastructure.config.refs.Kinds.ITEM_OR_MATERIAL, itemId);

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
                loaded++;
            } catch (Exception e) {
                session.error("alchemy/modifiers.yml", section + "[" + i + "]",
                        "invalid modifier (" + (type == AlchemyModifierType.LEVEL ? "needs bonus:" : type == AlchemyModifierType.DURATION
                                ? "needs seconds:" : "needs duration-multiplier:") + "): " + e.getMessage());
            }
        }
        return loaded;
    }
}
