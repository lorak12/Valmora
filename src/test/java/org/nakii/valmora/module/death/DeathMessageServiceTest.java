package org.nakii.valmora.module.death;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.module.combat.DeathContextCache;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link DeathMessageService}'s placeholder substitution and default-template fallback
 *  (VANILLA_CONTROL_AUDIT.md §9/§14 — custom, non-DamageSource-based death messages). */
public class DeathMessageServiceTest {

    private Valmora plugin;
    private FileConfiguration config;
    private MockedStatic<Valmora> valmoraStatic;
    private MockedStatic<DeathContextCache> cacheStatic;
    private Player victim;
    private UUID victimId;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        valmoraStatic = mockStatic(Valmora.class);
        valmoraStatic.when(Valmora::getInstance).thenReturn(plugin);

        cacheStatic = mockStatic(DeathContextCache.class);

        victimId = UUID.randomUUID();
        victim = mock(Player.class);
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getName()).thenReturn("Steve");
    }

    @AfterEach
    void tearDown() {
        valmoraStatic.close();
        cacheStatic.close();
    }

    private String render(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void fallsBackToDefaultTemplateWhenNoCachedContext() {
        config.set("death.messages.default", "<gray>%victim% died.");
        cacheStatic.when(() -> DeathContextCache.consume(victimId)).thenReturn(null);

        assertEquals("Steve died.", render(DeathMessageService.build(victim)));
    }

    @Test
    void fallsBackToDefaultTemplateWhenNoTypeSpecificEntry() {
        config.set("death.messages.default", "<gray>%victim% died.");
        DeathContextCache.Context ctx = new DeathContextCache.Context(DamageType.FALL, null, null);
        cacheStatic.when(() -> DeathContextCache.consume(victimId)).thenReturn(ctx);

        assertEquals("Steve died.", render(DeathMessageService.build(victim)));
    }

    @Test
    void substitutesAttackerAndUsesTypeSpecificTemplate() {
        config.set("death.messages.default", "<gray>%victim% died.");
        config.set("death.messages.MELEE", "<gray>%victim% was slain by %attacker%.");

        Player attacker = mock(Player.class);
        when(attacker.getName()).thenReturn("Alex");
        DeathContextCache.Context ctx = new DeathContextCache.Context(DamageType.MELEE, attacker, null);
        cacheStatic.when(() -> DeathContextCache.consume(victimId)).thenReturn(ctx);

        assertEquals("Steve was slain by Alex.", render(DeathMessageService.build(victim)));
    }

    @Test
    void substitutesWeaponDisplayName() {
        config.set("death.messages.MELEE", "<gray>%victim% was slain by %attacker% using %weapon%.");

        Player attacker = mock(Player.class);
        when(attacker.getName()).thenReturn("Alex");
        ItemStack weapon = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(weapon.hasItemMeta()).thenReturn(true);
        when(weapon.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.displayName()).thenReturn(Component.text("Excalibur"));

        DeathContextCache.Context ctx = new DeathContextCache.Context(DamageType.MELEE, attacker, weapon);
        cacheStatic.when(() -> DeathContextCache.consume(victimId)).thenReturn(ctx);

        assertEquals("Steve was slain by Alex using Excalibur.", render(DeathMessageService.build(victim)));
    }

    @Test
    void weaponWithoutDisplayNameFallsBackToMaterialName() {
        config.set("death.messages.MELEE", "<gray>%victim% was slain by %attacker% using %weapon%.");

        Player attacker = mock(Player.class);
        when(attacker.getName()).thenReturn("Alex");
        ItemStack weapon = mock(ItemStack.class);
        when(weapon.hasItemMeta()).thenReturn(false);
        when(weapon.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);

        DeathContextCache.Context ctx = new DeathContextCache.Context(DamageType.MELEE, attacker, weapon);
        cacheStatic.when(() -> DeathContextCache.consume(victimId)).thenReturn(ctx);

        assertEquals("Steve was slain by Alex using Diamond Sword.", render(DeathMessageService.build(victim)));
    }

    @Test
    void broadcastEnabledReadsConfigDefaultTrue() {
        assertTrue(DeathMessageService.broadcastEnabled());
        config.set("death.broadcast-enabled", false);
        assertFalse(DeathMessageService.broadcastEnabled());
    }
}
