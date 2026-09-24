package org.nakii.valmora.module.rarity;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.LoadSession;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.util.DebugManager;

import java.io.File;
import java.util.Locale;

/**
 * Loads {@code rarities.yml} into a {@link RarityRegistry} — the single source of truth for rarity
 * metadata (CLAUDE.md §Rarities, docs/Valmora_Modifier_Framework_Design.docx §4). Registered early
 * (right after {@code script}/{@code stat}, before {@code item}/{@code modifier}) since both the
 * item stack builder and the modifier engine read rarity metadata.
 *
 * <p>Unlike most content, {@code rarities.yml} is a single root-level file (not a folder of
 * per-entry YAMLs), so it's loaded directly rather than through {@link
 * org.nakii.valmora.infrastructure.config.YamlLoader}.
 */
public class RarityModule implements ReloadableModule {

    private final Valmora plugin;
    private final RarityRegistry registry = new RarityRegistry();

    public RarityModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        registry.clear();
        File file = new File(plugin.getDataFolder(), "rarities.yml");
        if (!file.exists()) {
            plugin.saveResource("rarities.yml", false);
        }
        if (!file.exists()) {
            plugin.getLogger().warning("rarities.yml is missing — no rarities loaded.");
            return;
        }

        try (LoadSession session = LoadSession.open(plugin, "Rarities", "rarities.yml")) {
            YamlConfiguration config = session.readYaml(file, "rarities.yml");
            if (config == null) return;
            ConfigurationSection section = config.getConfigurationSection("rarities");
            if (section == null) {
                session.error("rarities.yml", null, "no top-level 'rarities:' section — no rarities loaded");
                return;
            }

            java.util.Map<Integer, String> ranks = new java.util.HashMap<>();
            for (String key : section.getKeys(false)) {
                ConfigurationSection def = section.getConfigurationSection(key);
                if (def == null) {
                    session.warn("rarities.yml", key, "expected a section (id, name, color, rank, ...) — ignored");
                    continue;
                }
                try (LoadScope scope = session.entry("rarities.yml", key)) {
                    String id = def.getString("id", key.toLowerCase(Locale.ROOT));
                    String name = def.getString("name", key);
                    String color = def.getString("color", "<white>");
                    int rank = def.getInt("rank", 0);
                    double power = def.getDouble("power", 1.0);
                    String sameRank = ranks.putIfAbsent(rank, key);
                    if (sameRank != null) {
                        scope.sub("rank").warn("rank " + rank + " is also used by '" + sameRank
                                + "' — rarity ordering between them is undefined");
                    }

                    java.util.Map<String, Double> extra = new java.util.HashMap<>();
                    for (String extraKey : def.getKeys(false)) {
                        if (extraKey.equals("id") || extraKey.equals("name") || extraKey.equals("color")
                                || extraKey.equals("rank") || extraKey.equals("power")) continue;
                        if (def.isDouble(extraKey) || def.isInt(extraKey) || def.isLong(extraKey)) {
                            extra.put(extraKey.toLowerCase(Locale.ROOT), def.getDouble(extraKey));
                        } else {
                            scope.sub(extraKey).warn("extra rarity values must be numbers, got '" + def.get(extraKey) + "' — ignored");
                        }
                    }

                    registry.register(new RarityDefinition(key, id, name, color, rank, power, extra));
                    session.loaded();
                    DebugManager.log("rarity", "loaded '" + key + "' (id=" + id + ", rank=" + rank
                            + ", power=" + power + ", extra=" + extra.keySet() + ")");
                } catch (Exception e) {
                    session.error("rarities.yml", key, "failed to parse rarity: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onDisable() {
        registry.clear();
    }

    @Override
    public String getId() { return "rarity"; }

    @Override
    public String getName() { return "Rarity Registry"; }

    public RarityRegistry getRegistry() { return registry; }
}
