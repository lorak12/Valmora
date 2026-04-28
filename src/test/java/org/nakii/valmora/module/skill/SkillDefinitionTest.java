package org.nakii.valmora.module.skill;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

public class SkillDefinitionTest {

    @Test
    public void testGetSourceXp_Priority() {
        Map<String, Map<String, Double>> sources = new HashMap<>();
        Map<String, Double> blockBreak = new HashMap<>();
        
        // Exact matches
        blockBreak.put("DIAMOND_ORE", 100.0);
        blockBreak.put("OAK_LOG", 10.0);
        
        // Pattern matches
        blockBreak.put("*_ORE", 20.0);
        
        // Tag matches
        blockBreak.put("#MINECRAFT:LOGS", 5.0);
        
        // Default
        blockBreak.put("DEFAULT", 1.0);
        
        sources.put("BLOCK_BREAK", blockBreak);

        SkillDefinition skill = new SkillDefinition("test", "Test", "Desc", Material.BOOK, 60, "default", sources, null, new HashMap<>());

        // 1. Exact match should win
        assertEquals(100.0, skill.getSourceXp("BLOCK_BREAK", "DIAMOND_ORE"), "Exact match DIAMOND_ORE should win over *_ORE pattern");
        assertEquals(10.0, skill.getSourceXp("BLOCK_BREAK", "OAK_LOG"), "Exact match OAK_LOG should win over #MINECRAFT:LOGS tag");

        // 2. Pattern match should win over tag/default
        assertEquals(20.0, skill.getSourceXp("BLOCK_BREAK", "IRON_ORE"), "Pattern *_ORE should match IRON_ORE");

        // 3. Tag match (Mocking Tag/Bukkit)
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            Tag<Material> logsTag = mock(Tag.class);
            when(logsTag.isTagged(Material.SPRUCE_LOG)).thenReturn(true);
            
            NamespacedKey logsKey = NamespacedKey.minecraft("logs");
            bukkit.when(() -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, logsKey, Material.class)).thenReturn(logsTag);
            
            assertEquals(5.0, skill.getSourceXp("BLOCK_BREAK", "SPRUCE_LOG"), "Tag #MINECRAFT:LOGS should match SPRUCE_LOG");
        }

        // 4. Default
        try (MockedStatic<Material> materialMock = mockStatic(Material.class)) {
            materialMock.when(() -> Material.matchMaterial("DIRT")).thenReturn(Material.DIRT);
            assertEquals(1.0, skill.getSourceXp("BLOCK_BREAK", "DIRT"), "Default should be returned for unknown block");
        }
    }
}
