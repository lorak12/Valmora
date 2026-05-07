package org.nakii.valmora.module.alchemy.brewing;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.alchemy.AlchemyManager;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffectType;
import org.nakii.valmora.module.recipe.DynamicMachineHandler;
import org.nakii.valmora.module.recipe.RecipeDefinition;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AlchemyMachineHandler implements DynamicMachineHandler {

    private static final String AWKWARD_POTION_ID = "awkward_potion";

    private final Valmora plugin;
    private final AlchemyManager alchemyManager;

    public AlchemyMachineHandler(Valmora plugin, AlchemyManager alchemyManager) {
        this.plugin = plugin;
        this.alchemyManager = alchemyManager;
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs) {
        return match(inputs, null);
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, @Nullable Player player) {
        ItemStack base = getItem(inputs, "base");
        ItemStack ingredient = getItem(inputs, "ingredient");
        ItemStack potency = getItem(inputs, "potency");
        ItemStack durationMod = getItem(inputs, "duration_mod");
        ItemStack splash = getItem(inputs, "splash");

        if (isEmpty(base) || isEmpty(ingredient)) return Optional.empty();

        // Step 1: Water Bottle + Nether Wart → Awkward Potion
        if (isWaterBottle(base) && ingredient.getType() == Material.NETHER_WART) {
            ItemStack awkward = buildAwkwardPotion();
            return Optional.of(RecipeDefinition.dynamic("alchemy", awkward, inp -> {
                consume(inp, "base", 1);
                consume(inp, "ingredient", 1);
            }));
        }

        // Step 2: Awkward Potion + ingredient → Brewed Potion
        if (!isAwkwardPotion(base)) return Optional.empty();

        Optional<AlchemyEffect> effectOpt = alchemyManager.getEffectByIngredient(ingredient.getType());
        if (effectOpt.isEmpty()) return Optional.empty();

        AlchemyEffect effect = effectOpt.get();
        boolean hasPotency = !isEmpty(potency) && potency.getType() == Material.GLOWSTONE_DUST;
        boolean hasDuration = !isEmpty(durationMod) && durationMod.getType() == Material.REDSTONE;
        boolean isSplash = !isEmpty(splash) && splash.getType() == Material.GUNPOWDER;

        int level = Math.min(1 + (hasPotency ? 1 : 0), effect.getMaxLevel());
        int alchemyLevel = getAlchemyLevel(player);
        double alchemyBonus = 1.0 + alchemyLevel * 0.01;
        double durationMult = hasDuration ? 1.5 : 1.0;
        int finalDuration = (int) (effect.getDuration(level) * alchemyBonus * durationMult);

        ItemStack potion = buildPotionItem(effect, level, finalDuration, isSplash);

        return Optional.of(RecipeDefinition.dynamic("alchemy", potion, inp -> {
            consume(inp, "base", 1);
            consume(inp, "ingredient", 1);
            if (hasPotency) consume(inp, "potency", 1);
            if (hasDuration) consume(inp, "duration_mod", 1);
            if (isSplash) consume(inp, "splash", 1);
        }));
    }

    // ── Builders ─────────────────────────────────────────────────────────

    private ItemStack buildAwkwardPotion() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(org.bukkit.potion.PotionType.MUNDANE);
        meta.setColor(Color.fromRGB(100, 80, 150));
        meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, AWKWARD_POTION_ID);
        meta.getPersistentDataContainer().set(Keys.RARITY_KEY, PersistentDataType.STRING, "COMMON");
        meta.displayName(Formatter.format("<white>Awkward Potion"));
        List<Component> lore = new ArrayList<>();
        lore.add(Formatter.format("<gray>A base for brewing custom potions."));
        lore.add(Component.empty());
        lore.add(Formatter.format("<dark_gray><italic>COMMON"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildPotion(AlchemyEffect effect, int level, int durationSeconds, boolean isSplash) {
        return buildPotionItem(effect, level, durationSeconds, isSplash);
    }

    private ItemStack buildPotionItem(AlchemyEffect effect, int level, int durationSeconds, boolean isSplash) {
        Material mat = isSplash ? Material.SPLASH_POTION : Material.POTION;
        ItemStack item = new ItemStack(mat);
        PotionMeta meta = (PotionMeta) item.getItemMeta();

        if (effect.getColor() != null) meta.setColor(effect.getColor());
        meta.setBasePotionType(org.bukkit.potion.PotionType.MUNDANE);

        meta.getPersistentDataContainer().set(Keys.ALCHEMY_EFFECT_ID, PersistentDataType.STRING, effect.getId());
        meta.getPersistentDataContainer().set(Keys.ALCHEMY_EFFECT_LEVEL, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(Keys.ALCHEMY_DURATION, PersistentDataType.INTEGER, durationSeconds);
        meta.getPersistentDataContainer().set(Keys.ALCHEMY_IS_SPLASH, PersistentDataType.BYTE, isSplash ? (byte) 1 : (byte) 0);
        meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, "alchemy:" + effect.getId());

        String rarityColor = getRarityColor(effect.getRarity());
        meta.displayName(Formatter.format(effect.getName() + " " + toRoman(level)));

        List<Component> lore = new ArrayList<>();
        if (effect.getLore() != null) {
            for (String line : effect.getLore()) lore.add(Formatter.format(line));
        }
        if (!effect.getStats().isEmpty()) {
            lore.add(Component.empty());
            var registry = org.nakii.valmora.api.ValmoraAPI.getInstance().getStatRegistry();
            for (String statId : effect.getStats().keySet()) {
                double val = effect.getStatValue(statId, level);
                String sign = val >= 0 && effect.getType() == AlchemyEffectType.BUFF ? "+" : "";
                String displayName = registry.get(statId)
                        .map(org.nakii.valmora.module.stat.StatDefinition::getDisplayName)
                        .orElse(statId);
                lore.add(Formatter.format("<gray>" + displayName + ": <" +
                        (effect.getType() == AlchemyEffectType.BUFF ? "green" : "red") + ">" + sign + (int) val));
            }
        }
        lore.add(Component.empty());
        lore.add(Formatter.format("<gray>Duration: <white>" + durationSeconds + "s"));
        if (isSplash) lore.add(Formatter.format("<gray>Type: <light_purple>Splash"));
        lore.add(Component.empty());
        lore.add(Formatter.format(rarityColor + "<italic>" + effect.getRarity()));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ItemStack getItem(Map<String, ItemStack> inputs, String key) {
        return inputs.get(key);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() == 0;
    }

    private boolean isWaterBottle(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        if (item.hasItemMeta()) {
            String id = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            if (id != null) return false;
            if (item.getItemMeta() instanceof PotionMeta pm) {
                return pm.getBasePotionType() == org.bukkit.potion.PotionType.WATER
                        || pm.getBasePotionType() == null;
            }
        }
        return item.getType() == Material.POTION;
    }

    private boolean isAwkwardPotion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        return AWKWARD_POTION_ID.equals(id);
    }

    private void consume(Map<String, ItemStack> inputs, String key, int amount) {
        ItemStack item = inputs.get(key);
        if (item != null && !item.getType().isAir()) {
            item.setAmount(Math.max(0, item.getAmount() - amount));
        }
    }

    private int getAlchemyLevel(@Nullable Player player) {
        if (player == null) return 0;
        try {
            var session = plugin.getPlayerManager().getSession(player.getUniqueId());
            if (session == null) return 0;
            var profile = session.getActiveProfile();
            if (profile == null) return 0;
            double xp = profile.getSkillManager().getXp("alchemy");
            var data = plugin.getSkillModule().getSkillRegistry()
                    .getProgressData("default", xp);
            return data.currentLevel();
        } catch (Exception e) {
            return 0;
        }
    }

    private String getRarityColor(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "UNCOMMON" -> "<green>";
            case "RARE" -> "<blue>";
            case "EPIC" -> "<dark_purple>";
            case "LEGENDARY" -> "<gold>";
            default -> "<gray>";
        };
    }

    private String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
