package org.nakii.valmora.module.script.event;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.mob.MobDefinition;
import org.nakii.valmora.module.mob.MobManager;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.event.impl.SpawnMobEventFactory;
import org.nakii.valmora.Valmora;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SpawnMobEventFactoryTest {

    private final SpawnMobEventFactory factory = new SpawnMobEventFactory();
    private MobManager mobManager;
    private MobDefinition definition;
    private ExecutionContext ctx;
    private Location location;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        mobManager = mock(MobManager.class);
        when(api.getMobManager()).thenReturn(mobManager);

        // Only exercised on the "unknown mob id" warning path.
        ScriptModule scriptModule = mock(ScriptModule.class);
        Valmora plugin = mock(Valmora.class);
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(scriptModule.getValmora()).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("ValmoraTest"));

        ValmoraAPI.setProvider(api);

        definition = mock(MobDefinition.class);
        when(mobManager.getMobDefinition("zombie_minion")).thenReturn(definition);

        World world = mock(World.class);
        location = new Location(world, 10, 64, 10);

        ctx = mock(ExecutionContext.class);
        when(ctx.getLocation()).thenReturn(location);
    }

    @Test
    void getName_returnsSpawnMob() {
        assertEquals("spawn_mob", factory.getName());
    }

    @Test
    void noArgs_isNoOp() {
        assertDoesNotThrow(() -> factory.compile(new String[0], EventOptions.DEFAULT).execute(ctx));
        verifyNoInteractions(mobManager);
    }

    @Test
    void unknownMobId_logsAndSkipsSpawn() {
        when(mobManager.getMobDefinition("nope")).thenReturn(null);
        factory.compile(new String[]{"nope"}, EventOptions.DEFAULT).execute(ctx);
        verify(mobManager, never()).spawnMob(any(), any());
    }

    @Test
    void defaultCount_spawnsOnce() {
        factory.compile(new String[]{"zombie_minion"}, EventOptions.DEFAULT).execute(ctx);
        verify(mobManager, times(1)).spawnMob(eq(definition), any());
    }

    @Test
    void explicitCount_spawnsThatManyTimes() {
        factory.compile(new String[]{"zombie_minion", "3"}, EventOptions.DEFAULT).execute(ctx);
        verify(mobManager, times(3)).spawnMob(eq(definition), any());
    }

    @Test
    void invalidCount_defaultsToOne() {
        factory.compile(new String[]{"zombie_minion", "abc"}, EventOptions.DEFAULT).execute(ctx);
        verify(mobManager, times(1)).spawnMob(eq(definition), any());
    }

    @Test
    void zeroRadius_spawnsExactlyAtBaseLocation() {
        factory.compile(new String[]{"zombie_minion", "1"}, EventOptions.DEFAULT).execute(ctx);
        verify(mobManager).spawnMob(eq(definition), eq(location));
    }

    @Test
    void radiusArg_spawnsWithinOffsetOfBase() {
        factory.compile(new String[]{"zombie_minion", "2", "radius:5"}, EventOptions.DEFAULT).execute(ctx);
        verify(mobManager, times(2)).spawnMob(eq(definition), argThat(loc ->
                loc.getWorld() == location.getWorld()
                        && loc.getY() == location.getY()
                        && Math.abs(loc.getX() - location.getX()) <= 5.0
                        && Math.abs(loc.getZ() - location.getZ()) <= 5.0));
    }

    @Test
    void nullLocation_isNoOp() {
        when(ctx.getLocation()).thenReturn(null);
        factory.compile(new String[]{"zombie_minion"}, EventOptions.DEFAULT).execute(ctx);
        verify(mobManager, never()).spawnMob(any(), any());
    }
}
