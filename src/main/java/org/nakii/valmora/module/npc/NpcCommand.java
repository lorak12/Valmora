package org.nakii.valmora.module.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NpcCommand implements TabExecutor {

    private static final String PREFIX = "<dark_gray>[<gold>NPC<dark_gray>] ";
    private static final List<String> SUBCOMMANDS = List.of(
            "create", "delete", "list", "info", "tp", "move",
            "rename", "settype", "setyaw", "conversation", "clearconv",
            "skin", "near", "reload", "look", "showname"
    );

    private final Valmora plugin;

    public NpcCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    // ── Command dispatch ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("valmora.admin")) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>No permission."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        NpcManager nm = plugin.getNpcManager();
        if (nm == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>NPC module is not loaded."));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create"       -> cmdCreate(sender, args, nm);
            case "delete"       -> cmdDelete(sender, args, nm);
            case "list"         -> cmdList(sender, nm);
            case "info"         -> cmdInfo(sender, args, nm);
            case "tp"           -> cmdTp(sender, args, nm);
            case "move"         -> cmdMove(sender, args, nm);
            case "rename"       -> cmdRename(sender, args, nm);
            case "settype"      -> cmdSetType(sender, args, nm);
            case "setyaw"       -> cmdSetYaw(sender, args, nm);
            case "conversation" -> cmdConversation(sender, args, nm);
            case "clearconv"    -> cmdClearConv(sender, args, nm);
            case "skin"         -> cmdSkin(sender, args, nm);
            case "near"         -> cmdNear(sender, args, nm);
            case "reload"       -> cmdReload(sender);
            case "look"         -> cmdLook(sender, args, nm);
            case "showname"     -> cmdShowName(sender, args, nm);
            default             -> sendHelp(sender);
        }
        return true;
    }

    // ── Subcommand implementations ────────────────────────────────────────────

    private void cmdCreate(CommandSender sender, String[] args, NpcManager nm) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Formatter.format(PREFIX + "<red>Only players can use this.")); return; }
        if (args.length < 3) { player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc create <id> <entity_type>")); return; }

        String id = args[1].toLowerCase();
        if (nm.getRegistry().contains(id)) {
            player.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' already exists."));
            return;
        }

        EntityType type = parseEntityType(args[2]);
        if (type == null) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Unknown entity type: <white>" + args[2]));
            return;
        }

        Location loc = player.getLocation();
        NpcDefinition def = new NpcDefinition(
                id,
                "<white>" + id.replace("_", " "),
                type,
                loc.getWorld().getName(),
                round2(loc.getX()), round2(loc.getY()), round2(loc.getZ()),
                round2f(loc.getYaw()),
                List.of(), List.of(),
                null,
                NpcDefinition.DEFAULT_SOURCE
        );

        nm.registerAndSpawn(def);
        saveNpc(def, false);

        player.sendMessage(Formatter.format(PREFIX + "<green>Created NPC '<white>" + id + "<green>' (" + type.name() + ") at your location."));
        player.sendMessage(Formatter.format(PREFIX + "<gray>Tip: use <white>/npc rename " + id + " <MiniMessage name> <gray>to set a display name."));
    }

    private void cmdDelete(CommandSender sender, String[] args, NpcManager nm) {
        if (args.length < 2) { sender.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc delete <id>")); return; }

        String id = args[1].toLowerCase();
        NpcDefinition def = nm.getRegistry().get(id).orElse(null);
        if (def == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' not found."));
            return;
        }

        nm.removeNpc(id);
        boolean removed = deleteFromFile(def);

        sender.sendMessage(Formatter.format(PREFIX + "<green>Deleted NPC '<white>" + id + "<green>'."));
        if (!removed) {
            sender.sendMessage(Formatter.format(PREFIX + "<yellow>Note: this NPC is defined in <white>" + def.getSourceFile()
                    + "<yellow> — remove it there to prevent it coming back on reload."));
        }
    }

    private void cmdList(CommandSender sender, NpcManager nm) {
        var npcs = nm.getRegistry().values();
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>NPCs (" + npcs.size() + ")"));
        for (NpcDefinition def : npcs) {
            Location live = nm.getSpawnedLocation(def.getId());
            String status = live != null ? "<green>●" : "<red>●";
            Component line = Formatter.format(
                    " " + status + " <white>" + def.getId()
                    + " <dark_gray>(" + def.getEntityType().name() + ")"
                    + " <gray>@ " + def.getWorldName()
                    + " " + (int) def.getX() + "," + (int) def.getY() + "," + (int) def.getZ()
            ).clickEvent(ClickEvent.runCommand("/npc info " + def.getId()))
             .hoverEvent(HoverEvent.showText(Formatter.format("<gray>Click to view info")));
            sender.sendMessage(line);
        }
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
    }

    private void cmdInfo(CommandSender sender, String[] args, NpcManager nm) {
        if (args.length < 2) { sender.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc info <id>")); return; }

        NpcDefinition def = nm.getRegistry().get(args[1].toLowerCase()).orElse(null);
        if (def == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + args[1] + "<red>' not found."));
            return;
        }

        Location live = nm.getSpawnedLocation(def.getId());
        String spawnStatus = live != null ? "<green>Spawned" : "<red>Not spawned";

        sender.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>NPC: " + def.getId().toUpperCase()));
        sender.sendMessage(Formatter.format(" <gray>Display name: " + def.getDisplayName()));
        sender.sendMessage(Formatter.format(" <gray>Entity type:  <white>" + def.getEntityType().name()));
        sender.sendMessage(Formatter.format(" <gray>World:        <white>" + def.getWorldName()));
        sender.sendMessage(Formatter.format(" <gray>Position:     <white>" + round2(def.getX()) + ", " + round2(def.getY()) + ", " + round2(def.getZ()) + " <gray>yaw: <white>" + round2f(def.getYaw())));
        sender.sendMessage(Formatter.format(" <gray>Status:       " + spawnStatus));
        if (def.getBoundConversationId() != null) {
            sender.sendMessage(Formatter.format(" <gray>Conversation: <aqua>" + def.getBoundConversationId()));
        } else {
            sender.sendMessage(Formatter.format(" <gray>Conversation: <dark_gray>none"));
        }
        sender.sendMessage(Formatter.format(" <gray>Right-click:  <dark_gray>" + (def.getOnRightClick().isEmpty() ? "none" : def.getOnRightClick().size() + " action(s)")));
        sender.sendMessage(Formatter.format(" <gray>Left-click:   <dark_gray>" + (def.getOnLeftClick().isEmpty() ? "none" : def.getOnLeftClick().size() + " action(s)")));
        sender.sendMessage(Formatter.format(" <gray>Source file:  <dark_gray>" + def.getSourceFile()));
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
    }

    private void cmdTp(CommandSender sender, String[] args, NpcManager nm) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Formatter.format(PREFIX + "<red>Only players can use this.")); return; }
        if (args.length < 2) { player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc tp <id>")); return; }

        NpcDefinition def = nm.getRegistry().get(args[1].toLowerCase()).orElse(null);
        if (def == null) {
            player.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + args[1] + "<red>' not found."));
            return;
        }

        World world = Bukkit.getWorld(def.getWorldName());
        if (world == null) {
            player.sendMessage(Formatter.format(PREFIX + "<red>World '<white>" + def.getWorldName() + "<red>' is not loaded."));
            return;
        }

        Location dest = new Location(world, def.getX(), def.getY(), def.getZ(), def.getYaw(), 0f);
        player.teleportAsync(dest);
        player.sendMessage(Formatter.format(PREFIX + "<green>Teleported to NPC '<white>" + def.getId() + "<green>'."));
    }

    private void cmdMove(CommandSender sender, String[] args, NpcManager nm) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Formatter.format(PREFIX + "<red>Only players can use this.")); return; }
        if (args.length < 2) { player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc move <id>")); return; }

        String id = args[1].toLowerCase();
        NpcDefinition def = nm.getRegistry().get(id).orElse(null);
        if (def == null) {
            player.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' not found."));
            return;
        }

        Location loc = player.getLocation();
        NpcDefinition updated = def.withPosition(loc.getWorld().getName(), round2(loc.getX()), round2(loc.getY()), round2(loc.getZ()), round2f(loc.getYaw()));
        boolean spawned = nm.updateAndRespawn(updated);
        saveNpc(updated, true);

        player.sendMessage(Formatter.format(PREFIX + "<green>Moved NPC '<white>" + id + "<green>' to your position."));
        warnIfSpawnFailed(player, spawned, id);
    }

    private void cmdRename(CommandSender sender, String[] args, NpcManager nm) {
        if (args.length < 3) { sender.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc rename <id> <name...>")); return; }

        String id = args[1].toLowerCase();
        NpcDefinition def = nm.getRegistry().get(id).orElse(null);
        if (def == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' not found."));
            return;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        NpcDefinition updated = def.withDisplayName(newName);
        boolean spawned = nm.updateAndRespawn(updated);
        saveNpc(updated, true);

        sender.sendMessage(Formatter.format(PREFIX + "<green>Renamed NPC '<white>" + id + "<green>' to: " + newName));
        warnIfSpawnFailed(sender, spawned, id);
    }

    private void cmdSetType(CommandSender sender, String[] args, NpcManager nm) {
        if (args.length < 3) { sender.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc settype <id> <entity_type>")); return; }

        String id = args[1].toLowerCase();
        NpcDefinition def = nm.getRegistry().get(id).orElse(null);
        if (def == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' not found."));
            return;
        }

        EntityType type = parseEntityType(args[2]);
        if (type == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>Unknown entity type: <white>" + args[2]));
            return;
        }

        NpcDefinition updated = def.withEntityType(type);
        boolean spawned = nm.updateAndRespawn(updated);
        saveNpc(updated, true);

        sender.sendMessage(Formatter.format(PREFIX + "<green>Changed type of '<white>" + id + "<green>' to <white>" + type.name() + "<green>."));
        warnIfSpawnFailed(sender, spawned, id);
    }

    private void cmdSetYaw(CommandSender sender, String[] args, NpcManager nm) {
        if (args.length < 2) { sender.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc setyaw <id> [yaw]")); return; }

        String id = args[1].toLowerCase();
        NpcDefinition def = nm.getRegistry().get(id).orElse(null);
        if (def == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' not found."));
            return;
        }

        float yaw;
        if (args.length >= 3) {
            try { yaw = Float.parseFloat(args[2]); }
            catch (NumberFormatException e) {
                sender.sendMessage(Formatter.format(PREFIX + "<red>Invalid yaw value."));
                return;
            }
        } else if (sender instanceof Player player) {
            yaw = round2f(player.getLocation().getYaw());
        } else {
            sender.sendMessage(Formatter.format(PREFIX + "<red>Provide a yaw value when running from console."));
            return;
        }

        NpcDefinition updated = def.withYaw(yaw);
        boolean spawned = nm.updateAndRespawn(updated);
        saveNpc(updated, true);

        sender.sendMessage(Formatter.format(PREFIX + "<green>Set yaw of '<white>" + id + "<green>' to <white>" + yaw + "<green>."));
        warnIfSpawnFailed(sender, spawned, id);
    }

    private void cmdConversation(CommandSender sender, String[] args, NpcManager nm) {
        if (args.length < 3) { sender.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc conversation <id> <dialogue_id>")); return; }

        String id = args[1].toLowerCase();
        NpcDefinition def = nm.getRegistry().get(id).orElse(null);
        if (def == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' not found."));
            return;
        }

        String convId = args[2].toLowerCase();
        NpcDefinition updated = def.withConversation(convId);
        boolean spawned = nm.updateAndRespawn(updated);
        saveNpc(updated, true);

        sender.sendMessage(Formatter.format(PREFIX + "<green>Bound conversation '<white>" + convId + "<green>' to NPC '<white>" + id + "<green>'."));
        warnIfSpawnFailed(sender, spawned, id);
    }

    private void cmdClearConv(CommandSender sender, String[] args, NpcManager nm) {
        if (args.length < 2) { sender.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc clearconv <id>")); return; }

        String id = args[1].toLowerCase();
        NpcDefinition def = nm.getRegistry().get(id).orElse(null);
        if (def == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' not found."));
            return;
        }

        NpcDefinition updated = def.withConversation(null);
        boolean spawned = nm.updateAndRespawn(updated);
        saveNpc(updated, true);

        sender.sendMessage(Formatter.format(PREFIX + "<green>Cleared conversation binding from NPC '<white>" + id + "<green>'."));
        warnIfSpawnFailed(sender, spawned, id);
    }

    private void cmdSkin(CommandSender sender, String[] args, NpcManager nm) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Formatter.format(PREFIX + "<red>Only players can use this.")); return; }
        if (args.length < 3) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc skin <id> <player|url|file|reset> [value]"));
            return;
        }

        String id = args[1].toLowerCase();
        NpcDefinition def = nm.getRegistry().get(id).orElse(null);
        if (def == null) { player.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' not found.")); return; }
        if (def.getEntityType() != org.bukkit.entity.EntityType.MANNEQUIN) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Skins only apply to <white>MANNEQUIN<red>-type NPCs."));
            return;
        }

        String type = args[2].toLowerCase();

        // ── reset ────────────────────────────────────────────────────────────
        if (type.equals("reset")) {
            applySkin(player, nm, def, null, null, "Skin reset.");
            return;
        }

        if (args.length < 4) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc skin <id> " + type + " <value>"));
            return;
        }
        String value = args[3];

        switch (type) {
            // ── player <name> ─────────────────────────────────────────────────
            case "player" -> {
                player.sendMessage(Formatter.format(PREFIX + "<gray>Fetching skin for <white>" + value + "<gray>..."));
                SkinResolver.fetch(value, plugin, new SkinResolver.Callback() {
                    @Override public void onSuccess(String tex, String sig) {
                        applySkin(player, nm, def, tex, sig, "Applied skin of <white>" + value + "<green>.");
                    }
                    @Override public void onFailure(String reason) {
                        player.sendMessage(Formatter.format(PREFIX + "<red>Failed: <white>" + reason));
                    }
                });
            }
            // ── url <url> ─────────────────────────────────────────────────────
            case "url" -> {
                player.sendMessage(Formatter.format(PREFIX + "<gray>Uploading to Mineskin.org — this may take a few seconds..."));
                SkinResolver.fetchFromUrl(value, plugin, new SkinResolver.Callback() {
                    @Override public void onSuccess(String tex, String sig) {
                        applySkin(player, nm, def, tex, sig, "Applied skin from URL.");
                    }
                    @Override public void onFailure(String reason) {
                        player.sendMessage(Formatter.format(PREFIX + "<red>Failed: <white>" + reason));
                    }
                });
            }
            // ── file <filename> ───────────────────────────────────────────────
            case "file" -> {
                SkinFileServer fileServer = plugin.getNpcModule().getSkinFileServer();
                if (fileServer == null) {
                    player.sendMessage(Formatter.format(PREFIX + "<red>Skin file server is not enabled. "
                            + "Set <white>npc-skin-server.enabled: true<red> in config.yml and reload."));
                    return;
                }
                java.io.File skinFile = new java.io.File(fileServer.getSkinsDir(), value);
                if (!skinFile.exists()) {
                    player.sendMessage(Formatter.format(PREFIX + "<red>File not found: <white>" + value
                            + "<red>. Place the PNG in <white>" + fileServer.getSkinsDir().getPath()));
                    return;
                }
                String url = fileServer.urlFor(value);
                player.sendMessage(Formatter.format(PREFIX + "<gray>Applying skin from file: <white>" + value));
                SkinResolver.fetchFromUrl(url, plugin, new SkinResolver.Callback() {
                    @Override public void onSuccess(String tex, String sig) {
                        applySkin(player, nm, def, tex, sig, "Applied skin from file <white>" + value + "<green>.");
                    }
                    @Override public void onFailure(String reason) {
                        player.sendMessage(Formatter.format(PREFIX + "<red>Failed: <white>" + reason));
                    }
                });
            }
            default -> player.sendMessage(Formatter.format(
                    PREFIX + "<red>Unknown skin type '<white>" + type + "<red>'. Use: player, url, file, reset"));
        }
    }

    private void applySkin(Player player, NpcManager nm, NpcDefinition def,
                           String texture, String signature, String successMsg) {
        NpcDefinition updated = def.withSkin(texture, signature);
        boolean spawned = nm.updateAndRespawn(updated);
        saveNpc(updated, true);
        player.sendMessage(Formatter.format(PREFIX + "<green>" + successMsg));
        warnIfSpawnFailed(player, spawned, def.getId());
    }

    private void cmdNear(CommandSender sender, String[] args, NpcManager nm) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Formatter.format(PREFIX + "<red>Only players can use this.")); return; }

        double radius = 32.0;
        if (args.length >= 2) {
            try { radius = Double.parseDouble(args[1]); }
            catch (NumberFormatException ignored) {}
        }

        Location origin = player.getLocation();
        double radiusSq = radius * radius;
        List<NpcDefinition> nearby = new ArrayList<>();

        for (NpcDefinition def : nm.getRegistry().values()) {
            if (!def.getWorldName().equals(origin.getWorld().getName())) continue;
            double dx = def.getX() - origin.getX();
            double dy = def.getY() - origin.getY();
            double dz = def.getZ() - origin.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSq) nearby.add(def);
        }

        if (nearby.isEmpty()) {
            player.sendMessage(Formatter.format(PREFIX + "<gray>No NPCs within " + (int) radius + " blocks."));
            return;
        }

        player.sendMessage(Formatter.format(PREFIX + "<gold>" + nearby.size() + " NPC(s) within " + (int) radius + " blocks:"));
        for (NpcDefinition def : nearby) {
            double dist = Math.sqrt(Math.pow(def.getX() - origin.getX(), 2)
                    + Math.pow(def.getY() - origin.getY(), 2)
                    + Math.pow(def.getZ() - origin.getZ(), 2));
            player.sendMessage(Formatter.format(
                    "  <white>" + def.getId()
                    + " <dark_gray>(" + def.getEntityType().name() + ")"
                    + " <gray>" + (int) dist + "m away"
            ));
        }
    }

    private void cmdLook(CommandSender sender, String[] args, NpcManager nm) {
        if (args.length < 2) { sender.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc look <id>")); return; }

        String id = args[1].toLowerCase();
        NpcDefinition def = nm.getRegistry().get(id).orElse(null);
        if (def == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' not found."));
            return;
        }

        NpcDefinition updated = def.withLookAtPlayer(!def.isLookAtPlayer());
        nm.getRegistry().register(updated.getId(), updated);
        saveNpc(updated, true);

        String state = updated.isLookAtPlayer() ? "<green>enabled" : "<red>disabled";
        sender.sendMessage(Formatter.format(PREFIX + "<white>" + id + " <gray>look-at-player " + state + "<gray>."));
    }

    private void cmdShowName(CommandSender sender, String[] args, NpcManager nm) {
        if (args.length < 2) { sender.sendMessage(Formatter.format(PREFIX + "<red>Usage: /npc showname <id>")); return; }

        String id = args[1].toLowerCase();
        NpcDefinition def = nm.getRegistry().get(id).orElse(null);
        if (def == null) {
            sender.sendMessage(Formatter.format(PREFIX + "<red>NPC '<white>" + id + "<red>' not found."));
            return;
        }

        NpcDefinition updated = def.withShowName(!def.isShowName());
        boolean spawned = nm.updateAndRespawn(updated);
        saveNpc(updated, true);

        String state = updated.isShowName() ? "<green>shown" : "<red>hidden";
        sender.sendMessage(Formatter.format(PREFIX + "<white>" + id + " <gray>name tag is now " + state + "<gray>."));
        warnIfSpawnFailed(sender, spawned, id);
    }

    private void cmdReload(CommandSender sender) {
        sender.sendMessage(Formatter.format(PREFIX + "<aqua>Reloading NPC module..."));
        plugin.getModuleManager().reloadModules();
        sender.sendMessage(Formatter.format(PREFIX + "<green>Modules reloaded."));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    /**
     * Saves (or updates) an NPC definition in its source YAML file.
     * @param overwrite if true, replaces an existing entry; if false, only writes if not present
     */
    private void saveNpc(NpcDefinition def, boolean overwrite) {
        File file = new File(plugin.getDataFolder(), def.getSourceFile());
        file.getParentFile().mkdirs();
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        if (!overwrite && config.contains(def.getId())) return;

        var sec = config.createSection(def.getId());
        sec.set("display-name", def.getDisplayName());
        sec.set("entity-type", def.getEntityType().name());
        sec.set("world", def.getWorldName());
        sec.set("x", def.getX());
        sec.set("y", def.getY());
        sec.set("z", def.getZ());
        sec.set("yaw", def.getYaw());
        if (def.getBoundConversationId() != null) {
            sec.set("conversation", def.getBoundConversationId());
        }
        sec.set("on-right-click", def.getOnRightClick());
        sec.set("on-left-click", def.getOnLeftClick());
        if (def.getSkinTexture() != null) {
            sec.set("skin-texture", def.getSkinTexture());
            sec.set("skin-signature", def.getSkinSignature());
        } else {
            sec.set("skin-texture", null);
            sec.set("skin-signature", null);
        }
        sec.set("look-at-player", def.isLookAtPlayer());
        sec.set("show-name", def.isShowName());

        if (!def.getHolograms().isEmpty()) {
            List<Map<String, Object>> holoList = new ArrayList<>();
            for (HologramDefinition h : def.getHolograms()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", h.getName());
                m.put("text", h.getText());
                Map<String, Object> vec = new LinkedHashMap<>();
                vec.put("x", h.getOffsetX());
                vec.put("y", h.getOffsetY());
                vec.put("z", h.getOffsetZ());
                m.put("vector", vec);
                if (!h.getConditions().isEmpty()) m.put("conditions", h.getConditions());
                m.put("check_interval", h.getCheckInterval());
                holoList.add(m);
            }
            sec.set("holograms", holoList);
        } else {
            sec.set("holograms", null);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[NPC] Failed to save NPC '" + def.getId() + "': " + e.getMessage());
        }
    }

    /**
     * Removes an NPC entry from its source file.
     * @return true if the entry was found and removed, false if it was not in the file
     */
    private boolean deleteFromFile(NpcDefinition def) {
        File file = new File(plugin.getDataFolder(), def.getSourceFile());
        if (!file.exists()) return false;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.contains(def.getId())) return false;
        config.set(def.getId(), null);
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[NPC] Failed to delete NPC '" + def.getId() + "' from file: " + e.getMessage());
            return false;
        }
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("valmora.admin")) return List.of();

        List<String> completions = new ArrayList<>();
        NpcManager nm = plugin.getNpcManager();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, completions);
            return sorted(completions);
        }

        String sub = args[0].toLowerCase();

        // All commands that take <id> as arg2
        if (args.length == 2 && !sub.equals("create") && !sub.equals("list") && !sub.equals("reload") && !sub.equals("near")) {
            if (nm != null) StringUtil.copyPartialMatches(args[1], new ArrayList<>(nm.getRegistry().getKeys()), completions);
            return sorted(completions);
        }

        if (args.length == 3) {
            switch (sub) {
                case "create", "settype" -> {
                    StringUtil.copyPartialMatches(args[2], livingEntityTypeNames(), completions);
                    return sorted(completions);
                }
                case "conversation" -> {
                    if (plugin.getDialogueManager() != null) {
                        List<String> convIds = new ArrayList<>(plugin.getDialogueManager().getDialogueRegistry().getKeys());
                        StringUtil.copyPartialMatches(args[2], convIds, completions);
                    }
                    return sorted(completions);
                }
                case "setyaw" -> {
                    if (sender instanceof Player player) {
                        completions.add(String.valueOf(Math.round(player.getLocation().getYaw())));
                    }
                    return completions;
                }
                case "skin" -> {
                    StringUtil.copyPartialMatches(args[2], List.of("player", "url", "file", "reset"), completions);
                    return sorted(completions);
                }
            }
        }

        if (args.length == 4 && sub.equals("skin")) {
            String skinType = args[2].toLowerCase();
            switch (skinType) {
                case "player" -> {
                    // Suggest online player names
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    StringUtil.copyPartialMatches(args[3], names, completions);
                    return sorted(completions);
                }
                case "file" -> {
                    // Suggest files in plugins/Valmora/skins/
                    SkinFileServer fs = plugin.getNpcModule() != null ? plugin.getNpcModule().getSkinFileServer() : null;
                    if (fs != null && fs.getSkinsDir().isDirectory()) {
                        String[] files = fs.getSkinsDir().list((d, n) -> n.endsWith(".png"));
                        if (files != null) StringUtil.copyPartialMatches(args[3], List.of(files), completions);
                    }
                    return sorted(completions);
                }
                case "url" -> {
                    completions.add("<direct_image_url>");
                    return completions;
                }
            }
        }

        return completions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>NPC COMMANDS"));
        sender.sendMessage(Formatter.format(" <gray>/npc create <id> <entity_type> <dark_gray>- Create at your location"));
        sender.sendMessage(Formatter.format(" <gray>/npc delete <id> <dark_gray>- Despawn and remove NPC"));
        sender.sendMessage(Formatter.format(" <gray>/npc list <dark_gray>- List all registered NPCs"));
        sender.sendMessage(Formatter.format(" <gray>/npc info <id> <dark_gray>- Show NPC details"));
        sender.sendMessage(Formatter.format(" <gray>/npc tp <id> <dark_gray>- Teleport to NPC"));
        sender.sendMessage(Formatter.format(" <gray>/npc move <id> <dark_gray>- Move NPC to your position"));
        sender.sendMessage(Formatter.format(" <gray>/npc rename <id> <name...> <dark_gray>- Set display name (MiniMessage)"));
        sender.sendMessage(Formatter.format(" <gray>/npc settype <id> <entity_type> <dark_gray>- Change entity type"));
        sender.sendMessage(Formatter.format(" <gray>/npc setyaw <id> [yaw] <dark_gray>- Set facing (default: your yaw)"));
        sender.sendMessage(Formatter.format(" <gray>/npc skin <id> player <name> <dark_gray>- Apply skin from a player"));
        sender.sendMessage(Formatter.format(" <gray>/npc skin <id> url <url> <dark_gray>- Apply skin from image URL"));
        sender.sendMessage(Formatter.format(" <gray>/npc skin <id> file <file.png> <dark_gray>- Apply from plugins/Valmora/skins/"));
        sender.sendMessage(Formatter.format(" <gray>/npc skin <id> reset <dark_gray>- Remove custom skin"));
        sender.sendMessage(Formatter.format(" <gray>/npc conversation <id> <dialogue_id> <dark_gray>- Bind dialogue"));
        sender.sendMessage(Formatter.format(" <gray>/npc clearconv <id> <dark_gray>- Unbind dialogue"));
        sender.sendMessage(Formatter.format(" <gray>/npc near [radius] <dark_gray>- List nearby NPCs"));
        sender.sendMessage(Formatter.format(" <gray>/npc look <id> <dark_gray>- Toggle look-at-player on/off"));
        sender.sendMessage(Formatter.format(" <gray>/npc showname <id> <dark_gray>- Toggle floating name tag on/off"));
        sender.sendMessage(Formatter.format(" <gray>/npc reload <dark_gray>- Reload all modules"));
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
    }

    private static EntityType parseEntityType(String name) {
        try {
            EntityType t = EntityType.valueOf(name.toUpperCase());
            return isSpawnableNpcType(t) ? t : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isSpawnableNpcType(EntityType t) {
        if (t == EntityType.PLAYER) return false;
        return t.getEntityClass() != null
                && LivingEntity.class.isAssignableFrom(t.getEntityClass());
    }

    private static List<String> livingEntityTypeNames() {
        return Arrays.stream(EntityType.values())
                .filter(NpcCommand::isSpawnableNpcType)
                .map(t -> t.name().toLowerCase())
                .collect(Collectors.toList());
    }

    private void warnIfSpawnFailed(CommandSender sender, boolean spawned, String id) {
        if (!spawned) sender.sendMessage(Formatter.format(PREFIX + "<yellow>Warning: NPC '<white>" + id + "<yellow>' could not be spawned. Check entity type and world name."));
    }

    private static List<String> sorted(List<String> list) {
        list.sort(String::compareTo);
        return list;
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static float round2f(float v) { return Math.round(v * 100.0f) / 100.0f; }
}
