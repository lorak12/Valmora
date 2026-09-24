package org.nakii.valmora.module.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.util.DebugManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class PlayerManager implements ReloadableModule {
    private final DataStore dataStore;
    private final Map<UUID, ValmoraPlayer> activeSession = new HashMap<>();
    private final Valmora plugin;
    private final Random random = new Random();

    public PlayerManager(Valmora plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    private PlayerConnectionListener connectionListener;
    private org.bukkit.scheduler.BukkitTask autosaveTask;

    /**
     * Join generation per player, bumped on every join and cleared on quit. An async load only
     * installs its session if its generation is still current — otherwise the player quit (or
     * quit and rejoined) while it was in flight, and installing it would either leave a ghost
     * session for an offline player or replace a newer session with a stale one.
     */
    private final Map<UUID, Long> joinGeneration = new HashMap<>();
    private long nextGeneration = 0;

    @Override
    public void onEnable() {
        this.connectionListener = new PlayerConnectionListener(this);
        plugin.getServer().getPluginManager().registerEvents(connectionListener, plugin);

        ProfileGui.register(plugin);

        // Load existing players SYNCHRONOUSLY if this was a hot-reload to prevent async gap NPEs
        for (Player online : Bukkit.getOnlinePlayers()) {
            try {
                handleJoin(online.getUniqueId(), true);
            } catch (RuntimeException e) {
                // One bad profile must not stop every later player from loading.
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Failed to reload session for " + online.getName(), e);
            }
        }

        // Periodic autosave: previously profiles were only written on quit/switch/shutdown, so a
        // crash lost everything since each player's join.
        long intervalTicks = Math.max(30L, plugin.getConfig().getLong("profiles.autosave-interval-seconds", 300L)) * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autosaveAll, intervalTicks, intervalTicks);
    }

    /** Captures every online player's live inventory and saves their session (main thread). */
    public void autosaveAll() {
        for (Map.Entry<UUID, ValmoraPlayer> entry : activeSession.entrySet()) {
            Player online = Bukkit.getPlayer(entry.getKey());
            ValmoraProfile active = entry.getValue().getActiveProfile();
            if (online != null && active != null) {
                savePlayerInventory(online, active);
            }
            save(entry.getValue());
        }
    }

    /**
     * Saves a session, logging (rather than dropping) any failure. The returned future never
     * completes exceptionally, so callers may join on it freely.
     */
    public java.util.concurrent.CompletableFuture<Void> save(ValmoraPlayer player) {
        return dataStore.savePlayer(player).exceptionally(ex -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to save player " + player.getUuid() + " — changes since the last successful save may be lost.", ex);
            return null;
        });
    }

    private void onLoadFailed(UUID uuid, Throwable error) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Profile load failed for " + uuid
                + " — disconnecting instead of creating a blank profile over the real data.", error);
        joinGeneration.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.kick(org.nakii.valmora.util.Formatter.format(
                    "<red>Your profile data could not be loaded.\n<gray>Nothing was changed — please try rejoining in a moment."));
        }
    }

    public void handleJoin(UUID uuid) {
        handleJoin(uuid, false);
    }

    public void handleJoin(UUID uuid, boolean sync) {
        long generation = ++nextGeneration;
        joinGeneration.put(uuid, generation);
        java.util.function.Consumer<ValmoraPlayer> processor = (player) -> {
            ValmoraPlayer finalPlayer = player != null ? player : new ValmoraPlayer(uuid);
            if (finalPlayer.getProfiles().isEmpty()) {
                String defaultName = plugin.getConfig().getString("profiles.default-name", "Earth");
                ValmoraProfile defaultProfile = new ValmoraProfile(defaultName);
                
                // Initialize their starting health to their Max Health
                String healthId = plugin.getStatModule().getSystemStats().getHealth();
                double maxHealth = defaultProfile.getStatManager().getStat(healthId);
                defaultProfile.getPlayerState().heal(maxHealth, defaultProfile.getStatManager());
                
                finalPlayer.addProfile(defaultProfile);
                plugin.getLogger().info("Created default profile for " + uuid);
            }   

            Runnable finalize = () -> {
                Long current = joinGeneration.get(uuid);
                if (current == null || current != generation || Bukkit.getPlayer(uuid) == null) {
                    DebugManager.log("profiles", "discarding stale session load for " + uuid);
                    return;
                }
                activeSession.put(uuid, finalPlayer);
                Player bukkitPlayer = Bukkit.getPlayer(uuid);
                if (bukkitPlayer != null) {
                    ValmoraProfile active = finalPlayer.getActiveProfile();
                    active.touchLastUsed();
                    applyPlayerInventory(bukkitPlayer, active);
                    active.getStatManager().recalculateAttributes(bukkitPlayer);
                    active.getStatManager().recalculateStats(bukkitPlayer);
                    DebugManager.log("profiles", bukkitPlayer.getName() + " session loaded, active profile="
                            + active.getId() + " (" + active.getName() + "), profiles=" + finalPlayer.getProfiles().size());
                }
                // Fired last, once the saved inventory and stats are applied, so listeners (HUD
                // items, quest join triggers, ...) act on the player's real state — anything they
                // put in the inventory before this point was wiped by applyPlayerInventory.
                new PlayerProfileLoadedEvent(uuid, finalPlayer).callEvent();
            };

            if (sync) {
                finalize.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, finalize);
            }
        };

        if (sync) {
            ValmoraPlayer loaded;
            try {
                loaded = dataStore.loadPlayer(uuid).join();
            } catch (java.util.concurrent.CompletionException e) {
                onLoadFailed(uuid, e.getCause() != null ? e.getCause() : e);
                return;
            }
            processor.accept(loaded);
        } else {
            dataStore.loadPlayer(uuid).whenComplete((loaded, error) -> {
                if (error != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> onLoadFailed(uuid, error));
                } else {
                    processor.accept(loaded);
                }
            });
        }
    }

    @Override
    public void onDisable() {
        ProfileGui.unregister();

        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }

        if (connectionListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(connectionListener);
        }
        
        // A profile's savedInventory/savedArmor/savedOffhand fields are only refreshed on quit or
        // profile switch (see savePlayerInventory's other call sites) — while a player is actively
        // online, they're a stale snapshot from whenever that last happened. Re-capture the live
        // inventory for every online player right before persisting below, otherwise this reload
        // writes that stale snapshot to the DB and then handleJoin()'s sync reload path re-applies
        // it on top of the player's current inventory — silently reverting anything they picked up,
        // dropped, or deleted (e.g. via the creative-mode inventory) since their last quit/switch.
        for (Map.Entry<UUID, ValmoraPlayer> entry : activeSession.entrySet()) {
            Player online = Bukkit.getPlayer(entry.getKey());
            ValmoraProfile active = entry.getValue().getActiveProfile();
            if (online != null && active != null) {
                savePlayerInventory(online, active);
            }
        }

        // Concurrent (fixed 2026-08-07, was a synchronous per-player .join() loop) — each save is
        // already a CompletableFuture backed by the DB executor's own thread pool, so dispatching
        // them all up front and joining once on the aggregate lets the DB layer's own concurrency
        // do the work, instead of serializing N round-trips one at a time (a real stall at scale:
        // thousands of cached players × per-save latency, all blocking plugin shutdown/reload).
        java.util.List<java.util.concurrent.CompletableFuture<Void>> saves = new java.util.ArrayList<>();
        for (ValmoraPlayer player : activeSession.values()) {
            saves.add(save(player));
        }
        java.util.concurrent.CompletableFuture.allOf(saves.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        activeSession.clear();
        joinGeneration.clear();
    }

    @Override
    public String getId() {
        return "profiles";
    }

    public void handleQuit(UUID uuid) {
        DebugManager.log("profiles", "handleQuit " + uuid);
        Player player = Bukkit.getPlayer(uuid);
        ValmoraPlayer vp = activeSession.get(uuid);
        if (player != null && vp != null && vp.getActiveProfile() != null) {
            savePlayerInventory(player, vp.getActiveProfile());
        }
        joinGeneration.remove(uuid);
        // Temporary buffs belong to this session; they used to leak into the next one.
        org.nakii.valmora.module.item.TemporaryStatService.clear(uuid);
        if (player != null) {
            // Leave no Valmora values in the vanilla player file (attributes, passive effects) —
            // they're re-applied from the profile on the next join.
            try {
                plugin.getStatModule().resetAttributes(player);
                org.nakii.valmora.module.item.PassiveEffects.clear(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to reset attributes of " + player.getName(), e);
            }
        }
        ValmoraPlayer stored = activeSession.remove(uuid);
        if (stored != null) {
            // The data store runs a player's saves and loads in order, so a quick rejoin's load
            // is guaranteed to see this save.
            save(stored);
        }
    }

    public void switchProfile(Player player, String profileName) {
        ValmoraPlayer vp = activeSession.get(player.getUniqueId());
        for (ValmoraProfile profile : vp.getProfiles().values()) {
            if (profile.getName().equalsIgnoreCase(profileName)) {
                switchProfile(player, profile.getId());
                return;
            }
        }
        player.sendMessage(Component.text("Profile not found: " + profileName, NamedTextColor.RED));
    }

    public void switchProfile(Player player, UUID profileId) {
        ValmoraPlayer vp = activeSession.get(player.getUniqueId());
        if (vp == null) return;
        ValmoraProfile current = vp.getActiveProfile();
        if (current != null) savePlayerInventory(player, current);
        // Buffs from the previous profile must not carry over into this one.
        org.nakii.valmora.module.item.TemporaryStatService.clear(player.getUniqueId());
        vp.setActiveProfile(profileId);
        ValmoraProfile next = vp.getActiveProfile();
        if (next != null) {
            next.touchLastUsed();
            applyPlayerInventory(player, next);
            next.getStatManager().recalculateStats(player);
            next.getStatManager().recalculateAttributes(player);
        }
        // Persist the new active-profile id immediately — previously only saved on quit/disable,
        // so a crash right after switching would revert the selection on next join.
        save(vp);
        DebugManager.log("profiles", player.getName() + " switched profile: " + (current != null ? current.getId() : "none")
                + " -> " + profileId);
    }

    public ValmoraPlayer getSession(UUID uuid) {
        return activeSession.get(uuid);
    }

    /**
     * Applies {@code mutator} to a player's active profile and persists the change, working for
     * both online and offline players. Online players use the live cached session directly
     * (mutator runs synchronously, {@code onComplete} fires immediately); offline players are
     * loaded from the database, mutated, saved back, and {@code onComplete} runs on the main
     * thread once the save completes. {@code onComplete} receives {@code true} on success,
     * {@code false} if the player has never played before or has no active profile.
     *
     * <p>Used by admin commands that need to act on an offline target (e.g. {@code /skill give}) —
     * see {@code EconomyModule.readOffline}/{@code writeOffline} for the analogous pattern used by
     * {@code /eco}.
     */
    public void withOfflineProfile(UUID uuid, java.util.function.Consumer<ValmoraProfile> mutator,
                                    java.util.function.Consumer<Boolean> onComplete) {
        ValmoraPlayer cached = activeSession.get(uuid);
        if (cached != null) {
            ValmoraProfile active = cached.getActiveProfile();
            if (active == null) { onComplete.accept(false); return; }
            mutator.accept(active);
            save(cached);
            onComplete.accept(true);
            return;
        }

        dataStore.loadPlayer(uuid).whenComplete((vp, error) -> {
            boolean ok = false;
            if (error != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Offline edit of " + uuid + " aborted: profile failed to load.", error);
            } else if (vp != null) {
                ValmoraProfile active = vp.getActiveProfile();
                if (active != null) {
                    mutator.accept(active);
                    save(vp);
                    ok = true;
                }
            }
            boolean finalOk = ok;
            Bukkit.getScheduler().runTask(plugin, () -> onComplete.accept(finalOk));
        });
    }

    public boolean isLoaded(UUID uuid) {
        return activeSession.containsKey(uuid);
    }

    public int getMaxProfiles() {
        return plugin.getConfig().getInt("profiles.max-profiles", 4);
    }

    public String pickNextProfileName(ValmoraPlayer vp) {
        List<String> used = new ArrayList<>();
        for (ValmoraProfile p : vp.getProfiles().values()) used.add(p.getName().toLowerCase());

        List<String> pool = plugin.getConfig().getStringList("profiles.planet-names");
        List<String> available = new ArrayList<>();
        for (String name : pool) {
            if (!used.contains(name.toLowerCase())) available.add(name);
        }
        if (available.isEmpty()) return "Profile " + (vp.getProfiles().size() + 1);
        return available.get(random.nextInt(available.size()));
    }

    public enum CreateResult { OK, NO_SESSION, AT_CAP, DUPLICATE_NAME }

    /** @deprecated use {@link #createProfileResult(UUID, String)} for a result that distinguishes the failure reason. */
    @Deprecated
    public boolean createProfile(UUID uuid, String profileName) {
        return createProfileResult(uuid, profileName) == CreateResult.OK;
    }

    public CreateResult createProfileResult(UUID uuid, String profileName) {
        ValmoraPlayer vp = activeSession.get(uuid);
        if (vp == null) return CreateResult.NO_SESSION;
        if (vp.getProfiles().size() >= getMaxProfiles()) return CreateResult.AT_CAP;
        for (ValmoraProfile existing : vp.getProfiles().values()) {
            if (existing.getName().equalsIgnoreCase(profileName)) return CreateResult.DUPLICATE_NAME;
        }
        ValmoraProfile newProfile = new ValmoraProfile(profileName);
        vp.addProfile(newProfile);
        save(vp);
        return CreateResult.OK;
    }

    public record CreateOutcome(CreateResult result, String name) {}

    /** Creates a profile with a random unused name from {@code profiles.planet-names}. */
    public CreateOutcome createNextProfile(UUID uuid) {
        ValmoraPlayer vp = activeSession.get(uuid);
        if (vp == null) return new CreateOutcome(CreateResult.NO_SESSION, null);
        String name = pickNextProfileName(vp);
        return new CreateOutcome(createProfileResult(uuid, name), name);
    }

    public enum DeleteResult { OK, NO_SESSION, NOT_FOUND, ONLY_PROFILE, IS_ACTIVE }

    /**
     * Deletes a profile, guarding against corrupting the session — previously only the GUI
     * enforced these two checks (can't delete your only profile, can't delete the active one
     * without switching away first); {@code /profile delete} bypassed them entirely.
     */
    public DeleteResult deleteProfile(UUID playerUuid, UUID profileId) {
        ValmoraPlayer vp = activeSession.get(playerUuid);
        if (vp == null) return DeleteResult.NO_SESSION;
        if (!vp.getProfiles().containsKey(profileId)) return DeleteResult.NOT_FOUND;
        if (vp.getProfiles().size() <= 1) return DeleteResult.ONLY_PROFILE;
        ValmoraProfile active = vp.getActiveProfile();
        if (active != null && active.getId().equals(profileId)) return DeleteResult.IS_ACTIVE;

        vp.removeProfile(profileId);
        dataStore.deleteProfile(profileId);
        save(vp);
        return DeleteResult.OK;
    }

    // Legacy command-compatible overload; keep for ProfileCommand
    public DeleteResult deleteProfile(UUID uuid, String profileName) {
        ValmoraPlayer vp = activeSession.get(uuid);
        if (vp == null) return DeleteResult.NO_SESSION;
        return vp.getProfiles().values().stream()
                .filter(p -> p.getName().equalsIgnoreCase(profileName))
                .findFirst()
                .map(p -> deleteProfile(uuid, p.getId()))
                .orElse(DeleteResult.NOT_FOUND);
    }

    private void savePlayerInventory(Player player, ValmoraProfile profile) {
        // Snapshot the player's live alchemy effects into the profile too (same call sites: quit,
        // autosave, switch, disable), so they survive restarts.
        var alchemy = plugin.getAlchemyManager();
        if (alchemy != null) {
            java.util.List<PlayerState.SavedEffect> saved = new java.util.ArrayList<>();
            for (var effect : alchemy.getActiveEffects(player.getUniqueId())) {
                PlayerState.SavedEffect s = new PlayerState.SavedEffect();
                s.effectId = effect.effectId();
                s.level = effect.level();
                s.expiresAtMs = effect.expiresAtMs();
                saved.add(s);
            }
            profile.getPlayerState().setAlchemyEffects(saved);
        }
        PlayerInventory inv = player.getInventory();
        profile.setSavedInventory(inv.getStorageContents().clone());
        profile.setSavedArmor(inv.getArmorContents().clone());
        ItemStack offhand = inv.getItemInOffHand();
        profile.setSavedOffhand(offhand.getType().isAir() ? null : offhand.clone());
    }

    private void applyPlayerInventory(Player player, ValmoraProfile profile) {
        var alchemy = plugin.getAlchemyManager();
        if (alchemy != null) {
            java.util.List<org.nakii.valmora.module.alchemy.effect.ActiveEffect> restored = new java.util.ArrayList<>();
            for (PlayerState.SavedEffect s : profile.getPlayerState().getAlchemyEffects()) {
                if (s.effectId != null) restored.add(new org.nakii.valmora.module.alchemy.effect.ActiveEffect(s.effectId, s.level, s.expiresAtMs));
            }
            alchemy.restoreEffects(player.getUniqueId(), restored);
        }
        PlayerInventory inv = player.getInventory();
        inv.clear();
        if (profile.getSavedInventory() != null) inv.setStorageContents(profile.getSavedInventory());
        if (profile.getSavedArmor() != null) inv.setArmorContents(profile.getSavedArmor());
        if (profile.getSavedOffhand() != null) inv.setItemInOffHand(profile.getSavedOffhand());
    }

    public Collection<ValmoraPlayer> getAllSessions() {
        return activeSession.values();
    }

    public void syncVisualHealth(org.bukkit.entity.Player player, PlayerState state, StatManager stats) {
        double maxHealth = stats.getStat(plugin.getStatModule().getSystemStats().getHealth());
        double current = state.getCurrentHealth();

        double visualHearts = plugin.getConfig().getDouble("combat.visual-health-hearts", 10.0);
        double visualScale = visualHearts * 2.0; // vanilla health points = 2 per heart

        // Calculate percentage of health remaining
        double percentage = current / maxHealth;

        // Map it to the configured vanilla HP scale
        double visualHealth = percentage * visualScale;

        // Prevent vanilla death if they still have custom health > 0
        if (current > 0 && visualHealth < 0.5) {
            visualHealth = 0.5; // Half a heart minimum if alive
        }

        // Use Paper's health scaling so the UI is always locked to the configured heart count
        player.setHealthScale(visualScale);
        player.setHealthScaled(true);

        if (current <= 0) {
            player.setHealth(0); // Trigger actual vanilla death event!
        } else {
            player.setHealth(visualHealth);
        }
}

}
