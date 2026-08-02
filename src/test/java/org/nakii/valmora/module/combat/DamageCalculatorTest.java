package org.nakii.valmora.module.combat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.enchant.EnchantModule;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.enchant.EnchantmentRegistry;
import org.nakii.valmora.module.enchant.logic.SharpnessLogic;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.module.item.set.SetBonusRegistry;
import org.nakii.valmora.module.mob.MobDefinition;
import org.nakii.valmora.module.mob.MobManager;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.stat.StatDefinition;
import org.nakii.valmora.module.stat.StatRegistry;
import org.nakii.valmora.module.stat.SystemStats;
import org.nakii.valmora.util.Keys;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DamageCalculatorTest {

    private ValmoraAPI api;
    private PlayerManager playerManager;
    private MobManager mobManager;
    private EnchantModule enchantModule;
    private EnchantmentRegistry enchantmentRegistry;
    private StatRegistry statRegistry;
    private SystemStats systemStats;
    private ItemManager itemManager;
    private SetBonusRegistry setBonusRegistry;

    @BeforeEach
    void setUp() {
        api = mock(ValmoraAPI.class);
        playerManager = mock(PlayerManager.class);
        mobManager = mock(MobManager.class);
        enchantModule = mock(EnchantModule.class);
        enchantmentRegistry = mock(EnchantmentRegistry.class);
        systemStats = mock(SystemStats.class);

        when(systemStats.getHealth()).thenReturn("health");
        when(systemStats.getMana()).thenReturn("mana");
        when(systemStats.getDamage()).thenReturn("damage");
        when(systemStats.getStrength()).thenReturn("strength");
        when(systemStats.getDefense()).thenReturn("defense");
        when(systemStats.getCritChance()).thenReturn("crit_chance");
        when(systemStats.getCritDamage()).thenReturn("crit_damage");
        when(systemStats.getSpeed()).thenReturn("speed");
        when(systemStats.getHealthRegen()).thenReturn("health_regen");
        when(systemStats.getManaRegen()).thenReturn("mana_regen");
        when(systemStats.getLuck()).thenReturn("luck");

        statRegistry = new StatRegistry();
        statRegistry.register(new StatDefinition("health", "Health", 100.0, 10000.0, "<red>", "APPLE", "", true, null));
        statRegistry.register(new StatDefinition("mana", "Mana", 50.0, 5000.0, "<aqua>", "LAPIS_LAZULI", "", true, null));
        statRegistry.register(new StatDefinition("damage", "Damage", 0.0, Double.MAX_VALUE, "<red>", "IRON_SWORD", "", false, null));
        statRegistry.register(new StatDefinition("strength", "Strength", 0.0, Double.MAX_VALUE, "<red>", "BLAZE_POWDER", "", false, null));
        statRegistry.register(new StatDefinition("defense", "Defense", 0.0, Double.MAX_VALUE, "<gray>", "IRON_CHESTPLATE", "", false, null));
        statRegistry.register(new StatDefinition("crit_chance", "Crit Chance", 0.0, 100.0, "<gold>", "GOLD_NUGGET", "", false, null));
        statRegistry.register(new StatDefinition("crit_damage", "Crit Damage", 50.0, Double.MAX_VALUE, "<gold>", "GOLD_SWORD", "", false, null));
        statRegistry.register(new StatDefinition("speed", "Speed", 100.0, Double.MAX_VALUE, "<white>", "SUGAR", "", false, "MOVEMENT_SPEED"));
        statRegistry.register(new StatDefinition("health_regen", "Health Regen", 1.0, Double.MAX_VALUE, "<green>", "GLISTERING_MELON_SLICE", "", false, null));
        statRegistry.register(new StatDefinition("mana_regen", "Mana Regen", 2.0, Double.MAX_VALUE, "<aqua>", "PRISMARINE_CRYSTALS", "", false, null));
        statRegistry.register(new StatDefinition("luck", "Luck", 0.0, 100.0, "<yellow>", "RABBIT_FOOT", "", false, null));

        itemManager = mock(ItemManager.class);
        setBonusRegistry = mock(SetBonusRegistry.class);
        when(itemManager.getSetBonusRegistry()).thenReturn(setBonusRegistry);
        when(setBonusRegistry.get(any())).thenReturn(java.util.Optional.empty());

        when(api.getPlayerManager()).thenReturn(playerManager);
        when(api.getMobManager()).thenReturn(mobManager);
        when(api.getEnchantModule()).thenReturn(enchantModule);
        when(api.getStatRegistry()).thenReturn(statRegistry);
        when(api.getSystemStats()).thenReturn(systemStats);
        when(api.getItemManager()).thenReturn(itemManager);
        when(enchantModule.getRegistry()).thenReturn(enchantmentRegistry);

        ValmoraAPI.setProvider(api);
    }

    private void setupPlayer(Player player, UUID uuid, double damage, double strength, double defense, double critChance) {
        when(player.getUniqueId()).thenReturn(uuid);

        // Set up inventory and potion mocks BEFORE any stat operations (which call recalculateStats)
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getActivePotionEffects()).thenReturn(java.util.Collections.emptyList());
        when(inventory.getItemInMainHand()).thenReturn(null);
        when(inventory.getItemInOffHand()).thenReturn(null);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);

        ValmoraPlayer vPlayer = new ValmoraPlayer(uuid);
        ValmoraProfile profile = new ValmoraProfile("Test");

        // Zero out all defaults, then set the desired test values
        profile.getStatManager().reduceStat(player, "damage", statRegistry.get("damage").get().getDefaultValue());
        profile.getStatManager().reduceStat(player, "health", statRegistry.get("health").get().getDefaultValue());
        profile.getStatManager().reduceStat(player, "strength", statRegistry.get("strength").get().getDefaultValue());
        profile.getStatManager().reduceStat(player, "defense", statRegistry.get("defense").get().getDefaultValue());
        profile.getStatManager().reduceStat(player, "crit_chance", statRegistry.get("crit_chance").get().getDefaultValue());
        profile.getStatManager().reduceStat(player, "crit_damage", statRegistry.get("crit_damage").get().getDefaultValue());

        profile.getStatManager().addStat(player, "damage", damage);
        profile.getStatManager().addStat(player, "strength", strength);
        profile.getStatManager().addStat(player, "defense", defense);
        profile.getStatManager().addStat(player, "crit_chance", critChance);
        profile.getStatManager().addStat(player, "crit_damage", 50.0);

        vPlayer.addProfile(profile);
        when(playerManager.getSession(uuid)).thenReturn(vPlayer);
    }

    @Test
    void testMeleeDamageCalculation() {
        Player attacker = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        UUID attackerUuid = UUID.randomUUID();

        // 10 Damage, 50 Strength, 0 Crit Chance
        setupPlayer(attacker, attackerUuid, 10.0, 50.0, 0.0, 0.0);

        // 10 * (1 + 0.5) = 15
        DamageResult result = DamageCalculator.calculateDamage(attacker, victim, DamageType.MELEE);
        assertEquals(15.0, result.getFinalDamage());
    }

    @Test
    void testCriticalHitControl() {
        Player attacker = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        UUID attackerUuid = UUID.randomUUID();

        // 100% Crit Chance, 50% Crit Damage, 10 Damage, 0 Strength
        setupPlayer(attacker, attackerUuid, 10.0, 0.0, 0.0, 100.0);

        // 10 * (1.0) * (1.5) = 15
        DamageResult result = DamageCalculator.calculateDamage(attacker, victim, DamageType.MELEE);
        assertTrue(result.isCritical());
        assertEquals(15.0, result.getFinalDamage());

        // 0% Crit Chance
        setupPlayer(attacker, attackerUuid, 10.0, 0.0, 0.0, 0.0);
        result = DamageCalculator.calculateDamage(attacker, victim, DamageType.MELEE);
        assertFalse(result.isCritical());
        assertEquals(10.0, result.getFinalDamage());
    }

    @Test
    void testDefenseReductionFixed() {
        Player attacker = mock(Player.class);
        Player victim = mock(Player.class);
        UUID attackerUuid = UUID.randomUUID();
        UUID victimUuid = UUID.randomUUID();

        // Attacker: 100 Damage, 0 Strength, 0 Crit
        setupPlayer(attacker, attackerUuid, 100.0, 0.0, 0.0, 0.0);
        // Victim: 300 Defense
        setupPlayer(victim, victimUuid, 0.0, 0.0, 300.0, 0.0);

        // 100 * (100 / (300 + 100)) = 100 * 0.25 = 25
        DamageResult result = DamageCalculator.calculateDamage(attacker, victim, DamageType.MELEE);
        assertEquals(25.0, result.getFinalDamage());
    }

    @Test
    void testSharpnessEnchantment() {
        Player attacker = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        UUID attackerUuid = UUID.randomUUID();

        setupPlayer(attacker, attackerUuid, 10.0, 0.0, 0.0, 0.0);

        ItemStack sword = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(sword.hasItemMeta()).thenReturn(true);
        when(sword.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING)).thenReturn(true);
        when(pdc.get(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING)).thenReturn("sharpness:5");

        when(attacker.getInventory().getItemInMainHand()).thenReturn(sword);

        SharpnessLogic sharpnessLogic = new SharpnessLogic();
        EnchantmentDefinition sharpnessDef = mock(EnchantmentDefinition.class);
        when(sharpnessDef.getLogic()).thenReturn(sharpnessLogic);
        when(enchantmentRegistry.get("sharpness")).thenReturn(Optional.of(sharpnessDef));

        // 10 * 1.25 = 12.5 -> floor = 12
        DamageResult result = DamageCalculator.calculateDamage(attacker, victim, DamageType.MELEE);
        assertEquals(12.0, result.getFinalDamage());
    }

    @Test
    void testMobAttackingPlayer() {
        LivingEntity mob = mock(LivingEntity.class);
        Player player = mock(Player.class);
        UUID playerUuid = UUID.randomUUID();

        // Player: 100 Defense
        setupPlayer(player, playerUuid, 0.0, 0.0, 100.0, 0.0);

        // Mob Setup
        PersistentDataContainer mobPdc = mock(PersistentDataContainer.class);
        when(mob.getPersistentDataContainer()).thenReturn(mobPdc);
        when(mobPdc.get(Keys.MOB_ID_KEY, PersistentDataType.STRING)).thenReturn("zombie_king");

        MobDefinition mobDef = mock(MobDefinition.class);
        when(mobDef.getScaledDamage()).thenReturn(50.0);
        when(mobManager.getMobDefinition("zombie_king")).thenReturn(mobDef);

        // 50 damage * (100 / (100 + 100)) = 25
        DamageResult result = DamageCalculator.calculateDamage(mob, player, DamageType.MELEE);
        assertEquals(25.0, result.getFinalDamage());
    }
}
