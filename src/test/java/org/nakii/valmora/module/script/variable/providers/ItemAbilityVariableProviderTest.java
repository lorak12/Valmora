package org.nakii.valmora.module.script.variable.providers;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.rarity.RarityDefinition;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the "item" namespace provider. {@link ItemAbilityVariableProvider} is
 * deliberately the ONLY class registered under the {@code item} namespace (see its javadoc) —
 * {@code ScriptModule.registerProvider} keys providers by namespace in a flat map, so a second
 * competing registration (as an earlier pass of the modifier framework briefly had) would silently
 * clobber whichever registered first, breaking either the ability pipeline variables or the
 * modifier ones depending on module load order. This test locks in that both halves work from one
 * provider instance.
 */
class ItemAbilityVariableProviderTest {

    private final ItemAbilityVariableProvider provider = new ItemAbilityVariableProvider();

    private static class DummyExecutionContext implements ExecutionContext {
        @Override public org.bukkit.entity.LivingEntity getCaster() { return null; }
        @Override public java.util.Optional<org.bukkit.entity.LivingEntity> getTarget() { return java.util.Optional.empty(); }
        @Override public org.bukkit.Location getLocation() { return null; }
        @Override public org.nakii.valmora.api.scripting.VariableResolver getVariableResolver() { return null; }
        @Override public org.nakii.valmora.api.scripting.TagService getTagService() { return null; }
        @Override public org.bukkit.configuration.ConfigurationSection getParams() { return null; }
    }

    @Test
    void resolvesAbilityPipelineAttachments() {
        ExecutionContext ctx = new DummyExecutionContext();
        ctx.set("item:ability_id", "thundering_strike");
        ctx.set("item:ability_trigger", "ON_HIT");

        assertEquals("thundering_strike", provider.resolve(new String[]{"ability_id"}, ctx));
        assertEquals("ON_HIT", provider.resolve(new String[]{"ability_trigger"}, ctx));
    }

    @Test
    void resolvesRarityProperties() {
        ExecutionContext ctx = new DummyExecutionContext();
        RarityDefinition legendary = new RarityDefinition("LEGENDARY", "legendary", "Legendary", "<gold>", 4, 2.0);
        ctx.set(ItemAbilityVariableProvider.RARITY_ATTACHMENT_KEY, legendary);

        assertEquals("legendary", provider.resolve(new String[]{"rarity"}, ctx));
        assertEquals("legendary", provider.resolve(new String[]{"rarity", "id"}, ctx));
        assertEquals("Legendary", provider.resolve(new String[]{"rarity", "name"}, ctx));
        assertEquals(4, provider.resolve(new String[]{"rarity", "rank"}, ctx));
        assertEquals(2.0, provider.resolve(new String[]{"rarity", "power"}, ctx));
    }

    @Test
    void resolvesRarityToNullWhenUnattached() {
        ExecutionContext ctx = new DummyExecutionContext();
        assertNull(provider.resolve(new String[]{"rarity"}, ctx));
        assertNull(provider.resolve(new String[]{"rarity", "power"}, ctx));
    }

    @Test
    void resolvesTypeAndId() {
        ExecutionContext ctx = new DummyExecutionContext();
        ctx.set(ItemAbilityVariableProvider.TYPE_ATTACHMENT_KEY, "SWORD");
        ctx.set(ItemAbilityVariableProvider.ID_ATTACHMENT_KEY, "fierce_reforge_stone");

        assertEquals("SWORD", provider.resolve(new String[]{"type"}, ctx));
        assertEquals("fierce_reforge_stone", provider.resolve(new String[]{"id"}, ctx));
    }

    @Test
    void resolvesStatsByKey() {
        ExecutionContext ctx = new DummyExecutionContext();
        ctx.set(ItemAbilityVariableProvider.STATS_ATTACHMENT_KEY, Map.of("damage", 20.0, "strength", 5.0));

        assertEquals(20.0, provider.resolve(new String[]{"stats", "damage"}, ctx));
        assertEquals(5.0, provider.resolve(new String[]{"stats", "strength"}, ctx));
        assertNull(provider.resolve(new String[]{"stats", "unknown_stat"}, ctx));
        assertNull(provider.resolve(new String[]{"stats"}, ctx)); // no key -> null, not the whole map
    }

    @Test
    void emptyPathResolvesToNull() {
        assertNull(provider.resolve(new String[]{}, new DummyExecutionContext()));
    }
}
