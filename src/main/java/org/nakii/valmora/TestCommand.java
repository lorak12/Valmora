package org.nakii.valmora;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * QA harness for manually walking through an end-to-end recipe/anvil flow in game — hands out the
 * exact ingredients a scenario needs and prints the steps to run it, rather than automating the
 * steps themselves (enchanting/reforging/crafting are exactly what's being tested). Added alongside
 * the recipes-folder cleanup (CLAUDE.md §9.3) so {@code recipes/example_recipes.yml} and
 * {@code recipes/anvil/examples.yml} have a repeatable way to be exercised in a live server rather
 * than only read.
 */
public class TestCommand implements TabExecutor {

    private static final List<String> SCENARIOS = List.of("anvil", "crafting", "forge", "press", "enchant");

    private final Valmora plugin;

    public TestCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "anvil" -> runAnvil(player);
            case "crafting" -> runCrafting(player);
            case "forge" -> runForge(player);
            case "press" -> runPress(player);
            case "enchant" -> runEnchant(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        header(player, "TEST SCENARIOS");
        player.sendMessage(Formatter.format(" <gray>/test anvil <dark_gray>- keep-data-on-upgrade via the anvil"));
        player.sendMessage(Formatter.format(" <gray>/test crafting <dark_gray>- crafting_table SHAPED + SHAPELESS"));
        player.sendMessage(Formatter.format(" <gray>/test forge <dark_gray>- forge EXACT_SLOT chain"));
        player.sendMessage(Formatter.format(" <gray>/test press <dark_gray>- press machine (block-open trigger)"));
        player.sendMessage(Formatter.format(" <gray>/test enchant <dark_gray>- enchant overhaul: combo/stacking state, triggers, table cost/cap"));
        footer(player);
    }

    // ─── /test anvil ───

    /**
     * Exercises {@code apprentice_to_journeyman} in {@code recipes/anvil/examples.yml}: the player
     * enchants and reforges the tier-1 blade themselves (so the test actually proves
     * keep-data-on-upgrade, rather than the command faking already-attached data), then anvils it
     * into the tier-2 result and can compare enchant/reforge/name against the original.
     */
    private void runAnvil(Player player) {
        give(player, plugin.getItemManager().createItemStack("apprentice_blade"));
        give(player, new ItemStack(Material.AMETHYST_SHARD, 4));

        header(player, "TEST: ANVIL — keep-data-on-upgrade");
        player.sendMessage(Formatter.format(" <gray>You were given an <white>Apprentice's Blade <gray>and 4x <white>Amethyst Shard<gray>."));
        player.sendMessage(Formatter.format(" <yellow>1. <gray>Enchant the blade: <white>/item enchant sharpness 3"));
        player.sendMessage(Formatter.format(" <yellow>2. <gray>Reforge it: <white>/modifier apply reforges sharp"));
        player.sendMessage(Formatter.format(" <yellow>3. <gray>Open the anvil: <white>/gui open " + player.getName() + " anvil"));
        player.sendMessage(Formatter.format(" <yellow>4. <gray>Place the blade in the base slot, the shards in the material slot,"));
        player.sendMessage(Formatter.format("    <gray>and take the <white>Journeyman's Blade <gray>result."));
        player.sendMessage(Formatter.format(" <yellow>5. <gray>Verify: the Sharpness enchant, the reforge, and the item's name"));
        player.sendMessage(Formatter.format("    <gray>should all have carried over onto the upgraded blade."));
        footer(player);
    }

    // ─── /test crafting ───

    /** Exercises {@code backpack_tier1_craft} (SHAPED) then {@code backpack_tier2_craft} (SHAPELESS)
     *  in {@code recipes/example_recipes.yml}. */
    private void runCrafting(Player player) {
        give(player, new ItemStack(Material.LEATHER, 8));
        give(player, new ItemStack(Material.IRON_INGOT, 8));

        header(player, "TEST: CRAFTING TABLE — SHAPED + SHAPELESS");
        player.sendMessage(Formatter.format(" <gray>You were given 8x <white>Leather <gray>and 8x <white>Iron Ingot<gray>."));
        player.sendMessage(Formatter.format(" <yellow>1. <gray>Open the crafting table: <white>/gui open " + player.getName() + " crafting_table"));
        player.sendMessage(Formatter.format(" <yellow>2. <gray>Ring the 8 leather around the empty center slot to craft a"));
        player.sendMessage(Formatter.format("    <gray><white>Woven Backpack <gray>(SHAPED, letter-pattern syntax)."));
        player.sendMessage(Formatter.format(" <yellow>3. <gray>With the (empty) backpack + 8 iron ingots in the grid (any slots),"));
        player.sendMessage(Formatter.format("    <gray>craft the <white>Reinforced Backpack <gray>upgrade (SHAPELESS)."));
        footer(player);
    }

    // ─── /test forge ───

    /** Exercises {@code reinforced_ingot_craft} then {@code forged_blade_craft} (both EXACT_SLOT)
     *  in {@code recipes/example_recipes.yml}. */
    private void runForge(Player player) {
        give(player, new ItemStack(Material.IRON_INGOT, 2));
        give(player, new ItemStack(Material.DIAMOND, 1));
        give(player, new ItemStack(Material.IRON_SWORD, 1));

        header(player, "TEST: FORGE — EXACT_SLOT chain");
        player.sendMessage(Formatter.format(" <gray>You were given 2x <white>Iron Ingot<gray>, 1x <white>Diamond<gray>, and 1x <white>Iron Sword<gray>."));
        player.sendMessage(Formatter.format(" <yellow>1. <gray>Open the forge: <white>/gui open " + player.getName() + " forge"));
        player.sendMessage(Formatter.format(" <yellow>2. <gray>Put the 2 iron ingots in the left slot and the diamond in the"));
        player.sendMessage(Formatter.format("    <gray>middle slot to craft a <white>Reinforced Iron Ingot<gray>."));
        player.sendMessage(Formatter.format(" <yellow>3. <gray>Put the iron sword in the left slot and 2x the reinforced ingot"));
        player.sendMessage(Formatter.format("    <gray>in the middle slot to craft the <white>Forged Blade<gray>."));
        footer(player);
    }

    // ─── /test press ───

    /** Exercises {@code pressed_hoe} in {@code recipes/press_examples.yml} on the {@code press}
     *  example machine (block-open trigger, non-3-wide grid — {@code machines/press.yml}). */
    private void runPress(Player player) {
        give(player, new ItemStack(Material.STICK, 1));
        give(player, new ItemStack(Material.STRING, 1));
        give(player, new ItemStack(Material.IRON_INGOT, 1));
        give(player, new ItemStack(Material.LODESTONE, 1));

        header(player, "TEST: PRESS — block-open trigger + 1x3 grid");
        player.sendMessage(Formatter.format(" <gray>You were given a <white>Stick<gray>, <white>String<gray>, <white>Iron Ingot<gray>, and a <white>Lodestone<gray>."));
        player.sendMessage(Formatter.format(" <gray>(open-triggers match ANY block of that type server-wide, so the press deliberately"));
        player.sendMessage(Formatter.format(" <gray>uses an uncommon block rather than something like Iron Block — see machines/press.yml.)"));
        player.sendMessage(Formatter.format(" <yellow>1. <gray>Place the lodestone and right-click it to open the press."));
        player.sendMessage(Formatter.format(" <yellow>2. <gray>Place the stick, string, and iron ingot left-to-right, in that"));
        player.sendMessage(Formatter.format("    <gray>exact order, across the 3-slot row to craft an <white>Iron Hoe<gray>."));
        footer(player);
    }

    // ─── /test enchant ───

    /**
     * Exercises the enchant-overhaul content in {@code enchants/example_enchantments.yml} that
     * genuinely needs a live player/mob/timer to verify — the combo-counter/per-attacker-stacking
     * state engine, the ON_ATTACK_POST/ON_DEFEND_POST triggers, the out-of-combat stats: gating, and
     * the enchanting table's XP cost/level-cap enforcement — none of which a unit test can observe
     * (MockBukkit fakes entities/time; it can't fake "does this feel right mid-fight" or "does the
     * XP bar actually go down"). {@code EnchantContentMigrationTest} already proves the file parses;
     * this proves it plays correctly.
     */
    private void runEnchant(Player player) {
        give(player, new ItemStack(Material.DIAMOND_SWORD, 1));
        give(player, new ItemStack(Material.DIAMOND_SWORD, 1));
        give(player, new ItemStack(Material.DIAMOND_CHESTPLATE, 1));

        header(player, "TEST: ENCHANT OVERHAUL — state engine, triggers, table cost/cap");
        player.sendMessage(Formatter.format(" <gray>You were given two <white>Diamond Swords <gray>and a <white>Diamond Chestplate<gray>."));

        player.sendMessage(Formatter.format(" <yellow>1. <gray>Enchant sword #1: <white>/item enchant first_strike 3"));
        player.sendMessage(Formatter.format("    <gray>then <white>/item enchant life_steal 2 <gray>(same sword, held in your hand for both)."));
        player.sendMessage(Formatter.format(" <yellow>2. <gray>Switch hands, enchant sword #2: <white>/item enchant lethality 4"));
        player.sendMessage(Formatter.format(" <yellow>3. <gray>Enchant the chestplate: <white>/item enchant respite 4"));
        player.sendMessage(Formatter.format("    <gray>then <white>/item enchant thorns 3 <gray>(wear it while you do this)."));

        player.sendMessage(Formatter.format(""));
        player.sendMessage(Formatter.format(" <aqua><bold>Combo counter (First Strike) — hold sword #1:"));
        player.sendMessage(Formatter.format(" <yellow>4. <gray>Hit ONE mob 4+ times quickly. Hits 1-3 should each show a noticeably"));
        player.sendMessage(Formatter.format("    <gray>bigger damage number; hit 4+ should drop back to the un-boosted amount."));
        player.sendMessage(Formatter.format(" <yellow>5. <gray>Now hit a DIFFERENT mob. The bonus should apply again from hit 1 —"));
        player.sendMessage(Formatter.format("    <gray>the combo resets when you switch targets, it does not carry over."));
        player.sendMessage(Formatter.format(" <yellow>6. <gray>Watch your health bar while attacking — it should tick up slightly"));
        player.sendMessage(Formatter.format("    <gray>on every landed hit (Life Steal)."));

        player.sendMessage(Formatter.format(""));
        player.sendMessage(Formatter.format(" <aqua><bold>Per-attacker stacking (Lethality) — hold sword #2:"));
        player.sendMessage(Formatter.format(" <yellow>7. <gray>Hit the SAME mob repeatedly without switching — later hits should land"));
        player.sendMessage(Formatter.format("    <gray>harder as its effective defense drops (stacks build, capped at 4)."));
        player.sendMessage(Formatter.format(" <yellow>8. <gray>If you have a second player/account with their own Lethality sword,"));
        player.sendMessage(Formatter.format("    <gray>have them hit the same mob too — your stacks must NOT reset or share with"));
        player.sendMessage(Formatter.format("    <gray>theirs (this is the exact per-attacker bug the overhaul fixed — before it,"));
        player.sendMessage(Formatter.format("    <gray>two attackers on one target shared a single stack counter)."));
        player.sendMessage(Formatter.format(" <yellow>9. <gray>Stop hitting for 5+ seconds, then hit again — stacks should have reset"));
        player.sendMessage(Formatter.format("    <gray>to 0 (4-second expiry) rather than still being at their old count."));

        player.sendMessage(Formatter.format(""));
        player.sendMessage(Formatter.format(" <aqua><bold>Out-of-combat regen (Respite) + reflect (Thorns) — wear the chestplate:"));
        player.sendMessage(Formatter.format(" <yellow>10. <gray>Stand still, undamaged, for 10+ seconds — your natural regen should"));
        player.sendMessage(Formatter.format("     <gray>visibly speed up. Take a hit, then check again immediately — the bonus"));
        player.sendMessage(Formatter.format("     <gray>should stop as soon as you're in combat."));
        player.sendMessage(Formatter.format(" <yellow>11. <gray>Let a mob hit you several times — roughly 15% of hits should reflect"));
        player.sendMessage(Formatter.format("     <gray>1 damage back to it, with no server lag/console errors (this enchant is"));
        player.sendMessage(Formatter.format("     <gray>deliberately NOT routed through the normal damage pipeline, specifically"));
        player.sendMessage(Formatter.format("     <gray>to avoid a reflect-triggers-reflect loop against another Thorns wearer)."));

        player.sendMessage(Formatter.format(""));
        player.sendMessage(Formatter.format(" <aqua><bold>Enchanting table cost + level cap:"));
        player.sendMessage(Formatter.format(" <yellow>12. <gray>Get some XP levels (e.g. <white>/xp add " + player.getName() + " 50 levels</white>),"));
        player.sendMessage(Formatter.format("     <gray>then open the table: <white>/enchanting"));
        player.sendMessage(Formatter.format(" <yellow>13. <gray>Place a plain item, pick an enchant, pick a level. You should NOT be"));
        player.sendMessage(Formatter.format("     <gray>able to select a level above its etable-max-level (check <white>/item info"));
        player.sendMessage(Formatter.format("     <gray>while holding it, or the enchant's file, for that number)."));
        player.sendMessage(Formatter.format(" <yellow>14. <gray>Apply a valid level — your XP bar/level count should drop by exactly"));
        player.sendMessage(Formatter.format("     <gray>2 × the level you applied, and the click should be refused outright if"));
        player.sendMessage(Formatter.format("     <gray>you don't have enough (drop your XP first with <white>/xp set " + player.getName() + " 0 levels</white> to check)."));
        footer(player);
    }

    // ─── helpers ───

    private void give(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        player.getInventory().addItem(item);
    }

    private void header(Player player, String title) {
        player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        player.sendMessage(Formatter.format(" <gold><bold>" + title));
    }

    private void footer(Player player) {
        player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SCENARIOS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
