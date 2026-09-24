package org.nakii.valmora.module.mob;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.Severity;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.module.item.ItemRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A single broken part of a mob (e.g. one drop) is reported, not fatal to the whole mob. */
class MobDefinitionParserTest {

    private ItemManager items() {
        ItemManager items = mock(ItemManager.class);
        ItemRegistry registry = mock(ItemRegistry.class);
        when(items.getItemRegistry()).thenReturn(registry);
        when(registry.getAllItemIds()).thenReturn(Set.of("rotten_bone", "ghoul_heart"));
        when(items.createItemStack("rotten_bone")).thenReturn(mock(ItemStack.class));
        return items;
    }

    private List<ConfigDiagnostic> parse(String text, java.util.function.Consumer<Boolean> success) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(text);
        List<ConfigDiagnostic> diags = new ArrayList<>();
        try (LoadScope ignored = LoadScope.enter("Mobs", "mobs/test.yml", "ghoul", diags::add)) {
            var result = MobDefinitionParser.parse("ghoul", c.getConfigurationSection("ghoul"), "mobs/test.yml", items());
            success.accept(result.isSuccess());
        }
        return diags;
    }

    @Test
    void unknownDropIsSkippedButMobLoads() throws Exception {
        List<Boolean> ok = new ArrayList<>();
        List<ConfigDiagnostic> diags = parse("""
                ghoul:
                  category: UNDEAD
                  type: ZOMBIE
                  loot-table:
                    drops:
                      - item: rotten_bone
                        chance: 0.5
                      - item: rotten_bnoe
                """, ok::add);
        assertEquals(List.of(true), ok);
        ConfigDiagnostic d = diags.stream().filter(x -> x.severity() == Severity.WARN).findFirst().orElseThrow();
        assertEquals("loot-table.drops[1].item", d.path());
        assertEquals("did you mean 'rotten_bone'?", d.hint());
    }

    @Test
    void missingRequiredFieldsReportAllProblems() throws Exception {
        List<Boolean> ok = new ArrayList<>();
        List<ConfigDiagnostic> diags = parse("""
                ghoul:
                  categry: UNDEAD
                  type: ZOMBEE
                """, ok::add);
        assertEquals(List.of(false), ok);
        assertTrue(diags.stream().anyMatch(d -> "categry".equals(d.path()) && d.severity() == Severity.WARN
                && "did you mean 'category'?".equals(d.hint())));
        assertTrue(diags.stream().anyMatch(d -> "category".equals(d.path()) && d.isError()));
        assertTrue(diags.stream().anyMatch(d -> "type".equals(d.path()) && d.isError()
                && "did you mean 'ZOMBIE'?".equals(d.hint())));
    }

    @Test
    void badOptionalValuesFallBackToDefaults() throws Exception {
        List<Boolean> ok = new ArrayList<>();
        List<ConfigDiagnostic> diags = parse("""
                ghoul:
                  category: UNDEAD
                  type: ZOMBIE
                  damage-type: MEELE
                  resistances:
                    FIER: 0.5
                """, ok::add);
        assertEquals(List.of(true), ok);
        assertEquals(2, diags.stream().filter(d -> d.severity() == Severity.WARN).count());
    }
}
