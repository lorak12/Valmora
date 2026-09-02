package org.nakii.valmora.module.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PackContentFoldersTest {

    @Test
    void bareContentFolderNamesAreKnown() {
        assertTrue(PackContentFolders.isKnownContentEntry("items"));
        assertTrue(PackContentFolders.isKnownContentEntry("quests"));
        assertTrue(PackContentFolders.isKnownContentEntry("machines"));
    }

    @Test
    void subfolderEntriesAreKnownViaTheirTopSegment() {
        assertTrue(PackContentFolders.isKnownContentEntry("modifiers/groups"));
        assertTrue(PackContentFolders.isKnownContentEntry("recipes/anvil"));
    }

    @Test
    void unknownFolderIsRejected() {
        assertFalse(PackContentFolders.isKnownContentEntry("not_a_real_folder"));
        assertFalse(PackContentFolders.isKnownContentEntry(""));
        assertFalse(PackContentFolders.isKnownContentEntry(null));
    }

    @Test
    void sharedConfigWhitelistExcludesServerInstanceFiles() {
        assertTrue(PackContentFolders.isKnownSharedConfig("rarities.yml"));
        assertTrue(PackContentFolders.isKnownSharedConfig("item_types.yml"));
        assertFalse(PackContentFolders.isKnownSharedConfig("config.yml"),
                "config.yml is server-instance-specific, not mergeable pack content");
        assertFalse(PackContentFolders.isKnownSharedConfig("plugin.yml"));
        assertFalse(PackContentFolders.isKnownSharedConfig(null));
    }
}
