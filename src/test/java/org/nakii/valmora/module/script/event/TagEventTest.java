package org.nakii.valmora.module.script.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.profile.event.TagAddedEvent;
import org.nakii.valmora.module.script.event.impl.TagEvent;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Needs a real Bukkit server for the {@code Bukkit.getPluginManager().callEvent(...)} side effect. */
@Tag("mockbukkit")
class TagEventTest {

    private final TagEvent factory = new TagEvent();
    private ServerMock server;
    private ValmoraProfile profile;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();

        ValmoraAPI api = mock(ValmoraAPI.class);
        PlayerManager playerManager = mock(PlayerManager.class);
        when(api.getPlayerManager()).thenReturn(playerManager);
        when(api.getStatRegistry()).thenReturn(new StatRegistry());
        ValmoraAPI.setProvider(api);

        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        ValmoraPlayer vPlayer = new ValmoraPlayer(uuid);
        profile = new ValmoraProfile("Test");
        vPlayer.addProfile(profile);
        when(playerManager.getSession(uuid)).thenReturn(vPlayer);

        ctx = mock(ExecutionContext.class);
        when(ctx.getPlayerCaster()).thenReturn(Optional.of(player));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getName_returnsTag() {
        assertEquals("tag", factory.getName());
    }

    @Test
    void add_addsTagAndFiresTagAddedEvent() {
        AtomicReference<String> firedTag = new AtomicReference<>();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onTag(TagAddedEvent e) { firedTag.set(e.getTag()); }
        }, MockBukkit.createMockPlugin());

        factory.compile(new String[]{"add", "vip"}, EventOptions.DEFAULT).execute(ctx);

        assertTrue(profile.getTags().contains("vip"));
        assertEquals("vip", firedTag.get());
    }

    @Test
    void remove_removesTagWithoutFiringEvent() {
        profile.getTags().add("vip");
        AtomicReference<Boolean> fired = new AtomicReference<>(false);
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onTag(TagAddedEvent e) { fired.set(true); }
        }, MockBukkit.createMockPlugin());

        factory.compile(new String[]{"remove", "vip"}, EventOptions.DEFAULT).execute(ctx);

        assertFalse(profile.getTags().contains("vip"));
        assertFalse(fired.get());
    }

    @Test
    void tooFewArgs_isNoOp() {
        assertDoesNotThrow(() ->
                factory.compile(new String[]{"add"}, EventOptions.DEFAULT).execute(ctx));
        assertTrue(profile.getTags().isEmpty());
    }

    @Test
    void noPlayerCaster_isNoOp() {
        when(ctx.getPlayerCaster()).thenReturn(Optional.empty());
        assertDoesNotThrow(() ->
                factory.compile(new String[]{"add", "vip"}, EventOptions.DEFAULT).execute(ctx));
    }
}
