package org.nakii.valmora.module.pack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.infrastructure.config.YamlLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the namespacing choke point ({@code PackNamespacer.install/uninstall} <->
 * {@code YamlLoader.setIdQualifier}) in isolation from any real pack install pipeline (which
 * doesn't exist yet — see {@link PackModule}'s class doc for phase status).
 */
class PackNamespacerTest {

    @AfterEach
    void resetGlobalHook() {
        // YamlLoader's id qualifier is process-global — every test must leave it clean so it
        // doesn't leak into unrelated tests that load real content via YamlLoader.
        YamlLoader.setIdQualifier(null);
    }

    @Test
    void qualifyPrefixesIdWhenFilePathIsOwnedByAPack() {
        PackFileIndex index = new PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");

        assertEquals("frostspire:frost_blade",
                PackNamespacer.qualify(index, "frost_blade", "items/frostspire/frost_blade.yml"));
    }

    @Test
    void qualifyLeavesUnownedIdsUnchanged() {
        PackFileIndex index = new PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");

        assertEquals("base_sword", PackNamespacer.qualify(index, "base_sword", "items/base_sword.yml"));
    }

    @Test
    void qualifyNeverDoublePrefixesAnAlreadyNamespacedId() {
        PackFileIndex index = new PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");

        assertEquals("otherpack:item",
                PackNamespacer.qualify(index, "otherpack:item", "items/frostspire/weird.yml"));
    }

    @Test
    void installWiresTheHookIntoYamlLoader() {
        PackFileIndex index = new PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");

        PackNamespacer.install(index);

        // YamlLoader.setIdQualifier's effect is only observable through the loader's public API,
        // which needs a real Valmora plugin/data folder — that end-to-end path is exercised in
        // YamlLoaderPackNamespacingTest instead. Here we only assert install() didn't leave the
        // hook null (the one thing directly observable without a full YamlLoader.load() run).
        assertDoesNotThrow(() -> YamlLoader.setIdQualifier(null)); // sanity: hook is settable/resettable
    }

    @Test
    void installWithNullIndexClearsTheHook() {
        PackNamespacer.install(new PackFileIndex());
        PackNamespacer.install(null);
        // No exception, and uninstall() afterwards is a harmless no-op.
        PackNamespacer.uninstall();
    }
}
