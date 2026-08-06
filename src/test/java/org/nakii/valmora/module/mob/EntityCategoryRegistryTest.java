package org.nakii.valmora.module.mob;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Keys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link EntityCategoryRegistry} (relocated from the removed slayer module — originally
 * {@code SlayerCategoryRegistry}, Phase 3.4 of the refactor, see docs/REFACTOR/PROGRESS.md),
 * reproducing its exact behavior for the shipped default {@code entity_categories.yml}.
 */
public class EntityCategoryRegistryTest {

    private Valmora mockPlugin(File dataFolder) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EntityCategoryRegistryTest"));
        return plugin;
    }

    private LivingEntity mockEntity(EntityType type) {
        Zombie entity = mock(Zombie.class); // Zombie implements Monster in the Bukkit hierarchy
        when(entity.getType()).thenReturn(type);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.getKeys()).thenReturn(Set.of());
        when(pdc.get(Keys.MOB_ID_KEY, PersistentDataType.STRING)).thenReturn(null);
        return entity;
    }

    @Test
    void unregisteredCategoryFallsBackToEntityTypeNameEquality() {
        EntityCategoryRegistry registry = new EntityCategoryRegistry(); // never loaded
        LivingEntity blaze = mockEntity(EntityType.BLAZE);

        assertTrue(registry.matches(blaze, "BLAZE"));
        assertFalse(registry.matches(blaze, "ZOMBIE"));
    }

    @Test
    void unregisteredCategoryFallsBackToMobIdEquality() {
        EntityCategoryRegistry registry = new EntityCategoryRegistry();
        LivingEntity entity = mockEntity(EntityType.ZOMBIE);
        when(entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING))
                .thenReturn("zombie_king");

        assertTrue(registry.matches(entity, "zombie_king"));
    }

    @Test
    void instanceofRuleMatchesTheBukkitInterfaceHierarchy() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        Files.writeString(new File(dataFolder, "entity_categories.yml").toPath(),
                "categories:\n  monster:\n    match:\n      instanceof: \"Monster\"\n");

        EntityCategoryRegistry registry = new EntityCategoryRegistry();
        registry.load(mockPlugin(dataFolder));

        LivingEntity zombie = mockEntity(EntityType.ZOMBIE); // Zombie implements Monster
        assertTrue(registry.matches(zombie, "monster"));
        assertTrue(registry.matches(zombie, "MONSTER"), "category lookup must be case-insensitive");
    }

    @Test
    void alwaysRuleMatchesEveryEntity() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        Files.writeString(new File(dataFolder, "entity_categories.yml").toPath(),
                "categories:\n  all:\n    match:\n      always: true\n");

        EntityCategoryRegistry registry = new EntityCategoryRegistry();
        registry.load(mockPlugin(dataFolder));

        assertTrue(registry.matches(mockEntity(EntityType.PIG), "all"));
    }

    @Test
    void typeContainsRuleReproducesTheOldUndeadHardcodedList() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        Files.writeString(new File(dataFolder, "entity_categories.yml").toPath(),
                "categories:\n  undead:\n    match:\n      type_contains: [\"ZOMBIE\", \"SKELETON\", \"HUSK\"]\n");

        EntityCategoryRegistry registry = new EntityCategoryRegistry();
        registry.load(mockPlugin(dataFolder));

        assertTrue(registry.matches(mockEntity(EntityType.ZOMBIE), "undead"));
        assertTrue(registry.matches(mockEntity(EntityType.HUSK), "undead"));
        assertFalse(registry.matches(mockEntity(EntityType.PIG), "undead"));
    }

    @Test
    void hasPdcRuleMatchesOnKeyNameRegardlessOfNamespace() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        Files.writeString(new File(dataFolder, "entity_categories.yml").toPath(),
                "categories:\n  marked:\n    match:\n      has_pdc: \"boss_flag\"\n");

        EntityCategoryRegistry registry = new EntityCategoryRegistry();
        registry.load(mockPlugin(dataFolder));

        LivingEntity entity = mockEntity(EntityType.WITHER_SKELETON);
        when(entity.getPersistentDataContainer().getKeys())
                .thenReturn(Set.of(new NamespacedKey("valmora", "boss_flag")));

        assertTrue(registry.matches(entity, "marked"));
    }

    @Test
    void clearRemovesAllRegisteredCategories() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        Files.writeString(new File(dataFolder, "entity_categories.yml").toPath(),
                "categories:\n  all:\n    match:\n      always: true\n");

        EntityCategoryRegistry registry = new EntityCategoryRegistry();
        registry.load(mockPlugin(dataFolder));
        registry.clear();

        // "all" is no longer registered, so it falls through to entity-type-name equality (false here).
        assertFalse(registry.matches(mockEntity(EntityType.PIG), "all"));
    }

    @Test
    void matchingCategoriesReturnsAllMatchesForAnEntity() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        Files.writeString(new File(dataFolder, "entity_categories.yml").toPath(),
                "categories:\n" +
                        "  undead:\n    match:\n      type_contains: [\"ZOMBIE\"]\n" +
                        "  monster:\n    match:\n      instanceof: \"Monster\"\n" +
                        "  animal:\n    match:\n      instanceof: \"Animals\"\n");

        EntityCategoryRegistry registry = new EntityCategoryRegistry();
        registry.load(mockPlugin(dataFolder));

        LivingEntity zombie = mockEntity(EntityType.ZOMBIE);
        assertTrue(registry.matchingCategories(zombie).containsAll(java.util.List.of("UNDEAD", "MONSTER")));
        assertFalse(registry.matchingCategories(zombie).contains("ANIMAL"));
    }
}
