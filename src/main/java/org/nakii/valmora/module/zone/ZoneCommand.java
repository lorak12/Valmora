package org.nakii.valmora.module.zone;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ZoneCommand implements TabExecutor {

    private static final String PREFIX = "<dark_gray>[<gold>Zone<dark_gray>] ";
    private static final List<String> FLAGS = List.of(
            "pvp", "natural-mob-spawning", "block-breaking", "block-placing");
    private static final List<String> SUBCOMMANDS = List.of(
            "create", "delete", "info", "list", "wand", "pos1", "pos2", "clear",
            "flag", "spawner", "visualize");
    private static final List<String> SPAWNER_SUBS = List.of("add", "remove", "list");

    private final Valmora plugin;
    private final ZoneModule zoneModule;

    public ZoneCommand(Valmora plugin, ZoneModule zoneModule) {
        this.plugin = plugin;
        this.zoneModule = zoneModule;
    }

    private ZoneManager mgr() { return zoneModule.getZoneManager(); }
    private ZoneRegistry reg() { return zoneModule.getZoneRegistry(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("valmora.admin")) {
            player.sendMessage(Formatter.format(PREFIX + "<red>No permission."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "wand" -> giveWand(player);
            case "pos1" -> pos(player, true);
            case "pos2" -> pos(player, false);
            case "clear" -> clearSel(player);
            case "create" -> create(player, args);
            case "delete" -> delete(player, args);
            case "info" -> info(player, args);
            case "list" -> listZones(player);
            case "flag" -> flag(player, args);
            case "spawner" -> spawner(player, args);
            case "visualize" -> visualize(player);
            default -> sendHelp(player);
        }
        return true;
    }

    // ── Sub-command implementations ──────────────────────────────────────────

    private void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Formatter.format("<gold><bold>Zone Wand"));
        meta.lore(List.of(
                Formatter.format("<gray>Left-click block: <white>Set Pos1"),
                Formatter.format("<gray>Right-click block: <white>Set Pos2")
        ));
        meta.getPersistentDataContainer().set(Keys.ZONE_WAND_KEY, PersistentDataType.BOOLEAN, true);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage(Formatter.format(PREFIX + "<green>Zone Wand given. Left-click = Pos1, Right-click = Pos2."));
    }

    private void pos(Player player, boolean isPos1) {
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        if (isPos1) {
            mgr().setPos1(player, x, y, z);
            player.sendMessage(Formatter.format(PREFIX + "<gray>Pos<white>1 <gray>set to <white>" + x + ", " + y + ", " + z));
        } else {
            mgr().setPos2(player, x, y, z);
            player.sendMessage(Formatter.format(PREFIX + "<gray>Pos<white>2 <gray>set to <white>" + x + ", " + y + ", " + z));
        }
    }

    private void clearSel(Player player) {
        mgr().clearSelection(player);
        player.sendMessage(Formatter.format(PREFIX + "<gray>Selection cleared."));
    }

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /zone create <id> [display-name]"));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!mgr().hasFullSelection(uuid)) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Select a region first (use /zone wand or /zone pos1/pos2)."));
            return;
        }
        String selWorld = mgr().getSelectionWorld(uuid);
        if (!player.getWorld().getName().equals(selWorld)) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Your selection is in a different world."));
            return;
        }

        String id = args[1].toLowerCase();
        if (reg().contains(id)) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Zone '" + id + "' already exists."));
            return;
        }

        String displayName = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : "<green>" + id;

        int[] p1 = mgr().getPos1(uuid);
        int[] p2 = mgr().getPos2(uuid);
        mgr().createZone(id, displayName, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], selWorld);
        mgr().clearSelection(player);
        player.sendMessage(Formatter.format(PREFIX + "<green>Zone '<white>" + id + "<green>' created."));
    }

    private void delete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /zone delete <id>"));
            return;
        }
        String id = args[1].toLowerCase();
        if (mgr().deleteZone(id)) {
            player.sendMessage(Formatter.format(PREFIX + "<green>Zone '<white>" + id + "<green>' deleted."));
        } else {
            player.sendMessage(Formatter.format(PREFIX + "<red>Zone '" + id + "' not found."));
        }
    }

    private void info(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /zone info <id>"));
            return;
        }
        ZoneDefinition zone = reg().get(args[1]).orElse(null);
        if (zone == null) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Zone '" + args[1] + "' not found."));
            return;
        }
        player.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
        player.sendMessage(Formatter.format(" <gold><bold>ZONE: " + zone.getId().toUpperCase()));
        player.sendMessage(Formatter.format(" <gray>Name: " + zone.getDisplayName()));
        player.sendMessage(Formatter.format(" <gray>World: <white>" + zone.getWorldName()));
        player.sendMessage(Formatter.format(" <gray>Min: <white>" + zone.getMinX() + ", " + zone.getMinY() + ", " + zone.getMinZ()));
        player.sendMessage(Formatter.format(" <gray>Max: <white>" + zone.getMaxX() + ", " + zone.getMaxY() + ", " + zone.getMaxZ()));
        ZoneFlags f = zone.getFlags();
        player.sendMessage(Formatter.format(" <gray>Flags: PvP=" + flag(f.pvp()) + " <gray>NaturalSpawning=" + flag(f.naturalMobSpawning())
                + " <gray>Break=" + flag(f.blockBreaking()) + " <gray>Place=" + flag(f.blockPlacing())));
        player.sendMessage(Formatter.format(" <gray>Spawners: <white>" + zone.getMobSpawners().size()));
        for (ZoneMobSpawner s : zone.getMobSpawners()) {
            player.sendMessage(Formatter.format("   <dark_gray>- <white>" + s.getId()
                    + " <gray>mob=" + s.getMobId()
                    + " max=" + s.getMaxAlive()
                    + " interval=" + s.getSpawnIntervalTicks()
                    + "t at <white>" + s.getX() + "," + s.getY() + "," + s.getZ()));
        }
        player.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
    }

    private String flag(boolean v) { return v ? "<green>true" : "<red>false"; }

    private void listZones(Player player) {
        player.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
        player.sendMessage(Formatter.format(" <gold><bold>ZONES (" + reg().size() + ")"));
        for (ZoneDefinition zone : reg().values()) {
            player.sendMessage(Formatter.format(" <gray>- <white>" + zone.getId()
                    + " <dark_gray>(" + zone.getWorldName() + ")"));
        }
        player.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
    }

    private void flag(Player player, String[] args) {
        // /zone flag <zoneId> <flag> <true|false>
        if (args.length < 4) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /zone flag <id> <pvp|natural-mob-spawning|block-breaking|block-placing> <true|false>"));
            return;
        }
        ZoneDefinition zone = reg().get(args[1]).orElse(null);
        if (zone == null) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Zone '" + args[1] + "' not found."));
            return;
        }
        boolean value;
        String rawValue = args[3].toLowerCase();
        if (rawValue.equals("true") || rawValue.equals("on") || rawValue.equals("yes") || rawValue.equals("1")) {
            value = true;
        } else if (rawValue.equals("false") || rawValue.equals("off") || rawValue.equals("no") || rawValue.equals("0")) {
            value = false;
        } else {
            player.sendMessage(Formatter.format(PREFIX + "<red>Invalid value. Use true or false."));
            return;
        }

        ZoneFlags old = zone.getFlags();
        ZoneFlags updated = switch (args[2].toLowerCase()) {
            case "pvp" -> new ZoneFlags(value, old.naturalMobSpawning(), old.blockBreaking(), old.blockPlacing());
            case "natural-mob-spawning" -> new ZoneFlags(old.pvp(), value, old.blockBreaking(), old.blockPlacing());
            case "block-breaking" -> new ZoneFlags(old.pvp(), old.naturalMobSpawning(), value, old.blockPlacing());
            case "block-placing" -> new ZoneFlags(old.pvp(), old.naturalMobSpawning(), old.blockBreaking(), value);
            default -> null;
        };
        if (updated == null) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Unknown flag '" + args[2] + "'. Valid: pvp, natural-mob-spawning, block-breaking, block-placing"));
            return;
        }
        mgr().setZoneFlags(zone.getId(), updated);
        player.sendMessage(Formatter.format(PREFIX + "<green>Set <white>" + args[2] + " = " + value + " <green>for zone '<white>" + zone.getId() + "<green>'."));
    }

    private void spawner(Player player, String[] args) {
        // /zone spawner <add|remove|list> ...
        if (args.length < 3) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /zone spawner <add|remove|list> <zoneId> [mobId] [spawnRadius] [maxAlive] [interval]"));
            return;
        }
        String sub = args[1].toLowerCase();
        String zoneId = args[2].toLowerCase();

        switch (sub) {
            case "add" -> spawnerAdd(player, args, zoneId);
            case "remove" -> spawnerRemove(player, args, zoneId);
            case "list" -> spawnerList(player, zoneId);
            default -> player.sendMessage(Formatter.format(PREFIX + "<red>Unknown spawner sub-command. Use add, remove, or list."));
        }
    }

    private void spawnerAdd(Player player, String[] args, String zoneId) {
        if (args.length < 4) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /zone spawner add <zoneId> <mobId> [spawnRadius=3] [maxAlive=5] [interval=400]"));
            return;
        }
        if (reg().get(zoneId).isEmpty()) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Zone '" + zoneId + "' not found."));
            return;
        }

        String mobId = args[3].toLowerCase();
        if (plugin.getMobManager().getMobDefinition(mobId) == null) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Mob '" + mobId + "' not found in mob registry."));
            return;
        }

        int spawnRadius = args.length > 4 ? parseInt(args[4], 3) : 3;
        int maxAlive = args.length > 5 ? parseInt(args[5], 5) : 5;
        int interval = args.length > 6 ? parseInt(args[6], 400) : 400;

        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();

        // Generate a unique spawner ID
        ZoneDefinition zone = reg().get(zoneId).get();
        String spawnerId = mobId + "_" + (zone.getMobSpawners().size() + 1);

        ZoneMobSpawner s = new ZoneMobSpawner(spawnerId, mobId, x, y, z, interval, maxAlive, spawnRadius * 4.0, spawnRadius);
        mgr().addSpawner(zoneId, s);
        player.sendMessage(Formatter.format(PREFIX + "<green>Added spawner '<white>" + spawnerId
                + "<green>' to zone '<white>" + zoneId + "<green>' at <white>" + x + ", " + y + ", " + z + "."));
    }

    private void spawnerRemove(Player player, String[] args, String zoneId) {
        if (args.length < 4) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Usage: /zone spawner remove <zoneId> <spawnerId>"));
            return;
        }
        String spawnerId = args[3];
        if (mgr().removeSpawner(zoneId, spawnerId)) {
            player.sendMessage(Formatter.format(PREFIX + "<green>Removed spawner '<white>" + spawnerId + "<green>'."));
        } else {
            player.sendMessage(Formatter.format(PREFIX + "<red>Spawner '" + spawnerId + "' not found in zone '" + zoneId + "'."));
        }
    }

    private void spawnerList(Player player, String zoneId) {
        ZoneDefinition zone = reg().get(zoneId).orElse(null);
        if (zone == null) {
            player.sendMessage(Formatter.format(PREFIX + "<red>Zone '" + zoneId + "' not found."));
            return;
        }
        if (zone.getMobSpawners().isEmpty()) {
            player.sendMessage(Formatter.format(PREFIX + "<gray>No spawners in zone '" + zoneId + "'."));
            return;
        }
        player.sendMessage(Formatter.format(" <gold>Spawners in <white>" + zoneId + " <gold>(" + zone.getMobSpawners().size() + ")"));
        for (ZoneMobSpawner s : zone.getMobSpawners()) {
            player.sendMessage(Formatter.format("  <gray>- <white>" + s.getId()
                    + " <gray>mob=<white>" + s.getMobId()
                    + " <gray>max=<white>" + s.getMaxAlive()
                    + " <gray>interval=<white>" + s.getSpawnIntervalTicks() + "t"
                    + " <gray>at <white>" + s.getX() + "," + s.getY() + "," + s.getZ()));
        }
    }

    private void visualize(Player player) {
        boolean on = mgr().toggleVisualization(player);
        player.sendMessage(Formatter.format(PREFIX + "<gray>Zone border visualization " + (on ? "<green>enabled" : "<red>disabled") + "."));
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("valmora.admin"))
            return List.of();

        List<String> completions = new ArrayList<>();
        List<String> zoneIds = new ArrayList<>(reg().getKeys());

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, completions);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "delete", "info", "flag" -> StringUtil.copyPartialMatches(args[1], zoneIds, completions);
                case "spawner" -> StringUtil.copyPartialMatches(args[1], SPAWNER_SUBS, completions);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("flag")) {
                StringUtil.copyPartialMatches(args[2], FLAGS, completions);
            } else if (args[0].equalsIgnoreCase("spawner")) {
                StringUtil.copyPartialMatches(args[2], zoneIds, completions);
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("flag")) {
                StringUtil.copyPartialMatches(args[3], List.of("true", "false"), completions);
            } else if (args[0].equalsIgnoreCase("spawner") && args[1].equalsIgnoreCase("add")) {
                List<String> mobIds = new ArrayList<>(plugin.getMobManager().getMobRegistry().getAllMobIds());
                StringUtil.copyPartialMatches(args[3], mobIds, completions);
            } else if (args[0].equalsIgnoreCase("spawner") && args[1].equalsIgnoreCase("remove")) {
                ZoneDefinition zone = reg().get(args[2]).orElse(null);
                if (zone != null) {
                    List<String> ids = zone.getMobSpawners().stream().map(ZoneMobSpawner::getId).toList();
                    StringUtil.copyPartialMatches(args[3], ids, completions);
                }
            }
        }

        completions.sort(String::compareTo);
        return completions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
        player.sendMessage(Formatter.format(" <gold><bold>ZONE COMMANDS"));
        player.sendMessage(Formatter.format(" <gray>/zone wand <dark_gray>- Give the zone selection wand"));
        player.sendMessage(Formatter.format(" <gray>/zone pos1 / pos2 <dark_gray>- Set selection corners at feet"));
        player.sendMessage(Formatter.format(" <gray>/zone clear <dark_gray>- Clear current selection"));
        player.sendMessage(Formatter.format(" <gray>/zone create <id> [name] <dark_gray>- Create zone from selection"));
        player.sendMessage(Formatter.format(" <gray>/zone delete <id> <dark_gray>- Delete a zone"));
        player.sendMessage(Formatter.format(" <gray>/zone info <id> <dark_gray>- Show zone details"));
        player.sendMessage(Formatter.format(" <gray>/zone list <dark_gray>- List all zones"));
        player.sendMessage(Formatter.format(" <gray>/zone flag <id> <flag> <true|false>"));
        player.sendMessage(Formatter.format(" <gray>/zone spawner add|remove|list ..."));
        player.sendMessage(Formatter.format(" <gray>/zone visualize <dark_gray>- Toggle zone border particles"));
        player.sendMessage(Formatter.format("<dark_gray><st>                                                        </st>"));
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
