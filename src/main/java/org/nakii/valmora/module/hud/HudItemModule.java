package org.nakii.valmora.module.hud;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HudItemModule implements ReloadableModule {

    private final Valmora plugin;
    private final Map<String, HudItemDefinition> definitions = new HashMap<>();
    private final Map<Integer, HudItemDefinition> bySlot = new HashMap<>();
    private HudItemListener listener;

    public HudItemModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        definitions.clear();
        bySlot.clear();
        loadDefinitions();

        this.listener = new HudItemListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        for (var player : Bukkit.getOnlinePlayers()) {
            giveHudItems(player);
        }
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        definitions.clear();
        bySlot.clear();
    }

    @Override
    public String getId() { return "hud"; }

    @Override
    public String getName() { return "HUD Items"; }

    public Collection<HudItemDefinition> getDefinitions() {
        return definitions.values();
    }

    public HudItemDefinition getBySlot(int slot) {
        return bySlot.get(slot);
    }

    public boolean isHudItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Keys.HUD_ITEM_KEY, PersistentDataType.STRING);
    }

    public void giveHudItems(org.bukkit.entity.Player player) {
        for (HudItemDefinition def : definitions.values()) {
            player.getInventory().setItem(def.getSlot(), def.getItem());
        }
    }

    private void loadDefinitions() {
        YamlLoader<HudItemDefinition> loader = new YamlLoader<>(plugin, "hud-items", "HUD Item");
        loader.load(this::parseDefinition, def -> {
            definitions.put(def.getId(), def);
            bySlot.put(def.getSlot(), def);
        });
    }

    private LoadResult<HudItemDefinition, String> parseDefinition(String id, ConfigurationSection section, String filePath) {
        try {
            int slot = section.getInt("slot", 8);
            boolean preventMove = section.getBoolean("prevent-move", true);
            boolean glow = section.getBoolean("glow", false);

            ConfigurationSection itemSec = section.getConfigurationSection("item");
            if (itemSec == null) {
                return LoadResult.failure("[" + filePath + "] HUD item '" + id + "' missing 'item' section.");
            }

            String materialStr = itemSec.getString("material", "STONE");
            Material material = Material.matchMaterial(materialStr);
            if (material == null) {
                return LoadResult.failure("[" + filePath + "] HUD item '" + id + "': invalid material '" + materialStr + "'.");
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (itemSec.contains("name")) {
                    meta.displayName(Formatter.format(itemSec.getString("name")));
                }
                if (itemSec.contains("lore")) {
                    meta.lore(Formatter.formatList(itemSec.getStringList("lore")));
                }
                if (itemSec.getInt("custom-model-data", 0) > 0) {
                    meta.setCustomModelData(itemSec.getInt("custom-model-data"));
                }
                if (glow) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                meta.getPersistentDataContainer().set(Keys.HUD_ITEM_KEY, PersistentDataType.STRING, id);
                item.setItemMeta(meta);
            }

            var parser = plugin.getScriptModule().getEventParser();
            CompiledEvent onRight = section.contains("on-right-click")
                    ? parser.parseList(section.getStringList("on-right-click"))
                    : ctx -> {};
            CompiledEvent onLeft = section.contains("on-left-click")
                    ? parser.parseList(section.getStringList("on-left-click"))
                    : ctx -> {};

            return LoadResult.success(new HudItemDefinition(id, slot, preventMove, item, onRight, onLeft));
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse HUD item '" + id + "': " + e.getMessage());
        }
    }
}
