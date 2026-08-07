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

    @Override
    public void onEnable() {
        this.connectionListener = new PlayerConnectionListener(this);
        plugin.getServer().getPluginManager().registerEvents(connectionListener, plugin);

        ProfileGui.register(plugin);

        // Load existing players SYNCHRONOUSLY if this was a hot-reload to prevent async gap NPEs
        for (Player online : Bukkit.getOnlinePlayers()) {
            handleJoin(online.getUniqueId(), true);
        }
    }

    public void handleJoin(UUID uuid) {
        handleJoin(uuid, false);
    }

    public void handleJoin(UUID uuid, boolean sync) {
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
                activeSession.put(uuid, finalPlayer);
                new PlayerProfileLoadedEvent(uuid, finalPlayer).callEvent();
                Player bukkitPlayer = Bukkit.getPlayer(uuid);
                if (bukkitPlayer != null) {
                    ValmoraProfile active = finalPlayer.getActiveProfile();
                    active.touchLastUsed();
                    applyPlayerInventory(bukkitPlayer, active);
                    active.getStatManager().recalculateAttributes(bukkitPlayer);
                    active.getStatManager().recalculateStats(bukkitPlayer);
                }
            };

            if (sync) {
                finalize.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, finalize);
            }
        };

        if (sync) {
            processor.accept(dataStore.loadPlayer(uuid).join());
        } else {
            dataStore.loadPlayer(uuid).thenAcceptAsync(processor);
        }
    }

    @Override
    public void onDisable() {
        ProfileGui.unregister();

        if (connectionListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(connectionListener);
        }
        
        // Concurrent (fixed 2026-08-07, was a synchronous per-player .join() loop) — each save is
        // already a CompletableFuture backed by the DB executor's own thread pool, so dispatching
        // them all up front and joining once on the aggregate lets the DB layer's own concurrency
        // do the work, instead of serializing N round-trips one at a time (a real stall at scale:
        // thousands of cached players × per-save latency, all blocking plugin shutdown/reload).
        java.util.List<java.util.concurrent.CompletableFuture<Void>> saves = new java.util.ArrayList<>();
        for (ValmoraPlayer player : activeSession.values()) {
            saves.add(dataStore.savePlayer(player));
        }
        java.util.concurrent.CompletableFuture.allOf(saves.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        activeSession.clear();
    }

    @Override
    public String getId() {
        return "profiles";
    }

    public void handleQuit(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        ValmoraPlayer vp = activeSession.get(uuid);
        if (player != null && vp != null && vp.getActiveProfile() != null) {
            savePlayerInventory(player, vp.getActiveProfile());
        }
        ValmoraPlayer stored = activeSession.remove(uuid);
        if (stored != null) {
            // Not truly guarded against a hard process kill mid-write (that's an OS-level
            // concern no application code can fully close), but at minimum a save failure is no
            // longer silently swallowed — previously fire-and-forget with nothing observing the
            // future at all.
            dataStore.savePlayer(stored).exceptionally(ex -> {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Failed to save player " + uuid + " on quit — data for this session may be lost.", ex);
                return null;
            });
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
        dataStore.savePlayer(vp);
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
            dataStore.savePlayer(cached);
            onComplete.accept(true);
            return;
        }

        dataStore.loadPlayer(uuid).thenAccept(vp -> {
            boolean ok = false;
            if (vp != null) {
                ValmoraProfile active = vp.getActiveProfile();
                if (active != null) {
                    mutator.accept(active);
                    dataStore.savePlayer(vp);
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

    /** @return false if the profile was NOT created (no session, at the profile cap, or a duplicate name) */
    public boolean createProfile(UUID uuid, String profileName) {
        ValmoraPlayer vp = activeSession.get(uuid);
        if (vp == null) return false;
        if (vp.getProfiles().size() >= getMaxProfiles()) return false;
        for (ValmoraProfile existing : vp.getProfiles().values()) {
            if (existing.getName().equalsIgnoreCase(profileName)) return false;
        }
        ValmoraProfile newProfile = new ValmoraProfile(profileName);
        vp.addProfile(newProfile);
        dataStore.savePlayer(vp);
        return true;
    }

    public void createNextProfile(UUID uuid) {
        ValmoraPlayer vp = activeSession.get(uuid);
        if (vp == null) return;
        createProfile(uuid, pickNextProfileName(vp));
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
        dataStore.savePlayer(vp);
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
        PlayerInventory inv = player.getInventory();
        profile.setSavedInventory(inv.getStorageContents().clone());
        profile.setSavedArmor(inv.getArmorContents().clone());
        ItemStack offhand = inv.getItemInOffHand();
        profile.setSavedOffhand(offhand.getType().isAir() ? null : offhand.clone());
    }

    private void applyPlayerInventory(Player player, ValmoraProfile profile) {
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
