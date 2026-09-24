# Updating, Editing and Reloading Safely

What happens to your server's data when you update Valmora, edit its YAML, or reload. It also
covers the tools for checking and repairing things.

## Updating the plugin

- **`config.yml`:** new settings are added to your file automatically, with their comments. Your
  values are never changed. A backup of the previous file goes to `plugins/Valmora/backups/`. The
  `config-version` key at the top is managed by Valmora; don't edit it.
- **Default content** (`items/`, `mobs/`, `machines/`, …):
  - New default files are installed.
  - Files you haven't edited are updated to the new version. To turn this off, set
    `resources.auto-update-unmodified-defaults: false`.
  - For files you *have* edited, the new version is written next to yours as `<file>.new` (Valmora
    ignores `.new` files) so you can merge the changes yourself.
  - Default files you deleted stay deleted.
- **Player data** in the database is upgraded automatically.
- **Downgrading is refused.** If the database or a profile was written by a newer Valmora, the
  plugin (or that player's join) stops instead of risking corruption. Restore a backup, or install
  the newer version again.

## Editing content

- **Existing items follow your edits.** Changing an item's `stats:`, `rarity:`, `item-type:`,
  name or lore applies to items players already own. Stats apply immediately; the name and lore
  update the next time the item is seen (on join, container open, pickup or held-item change, and
  for everyone online right after a reload).
  - **Kept through edits:** anvil renames, anvil `add_lore`, enchants, reforges, gemstones and pet
    levels.
  - **Rarity colours and names** come from `rarities.yml`, which can also define new rarities
    for items.
- **Existing mobs follow your edits.** Health (keeping the current health percentage), damage,
  speed, aggro range and flags are re-applied to live mobs, and bosses keep their boss bars.
  - When you **delete** a mob's definition, what happens to its live mobs depends on
    `mobs.orphan-policy`: `keep` (they stay as plain mobs) or `remove` (they despawn).
- **Lowering a cap** (a max level on an enchant, pet, skill or progression node, or a potion's max
  level) applies to players and items above it. Their stored value is kept, so raising the cap
  again brings it back.
- **Changing an XP curve** never gives a skill's level rewards twice, and never skips them.
- **Quest objectives:** progress no longer shifts when you add, remove or reorder objectives. For
  full safety, give each objective an `id:`. Without one, objectives are identified by type and
  order within that type.
- **Collection stages** can be inserted or renumbered; rewards are tracked per stage (by its `id:`
  or its `required` amount).
- **Changing a stat's `default:`** applies to existing players; only what was actually allocated
  to them is stored.
- **Making a storage GUI smaller** returns the items that no longer fit to the player instead of
  deleting them.
- **Calendar events** that you edit or delete while they're running still get their `on-end`
  actions.

### Renaming things: `previous-ids`

To rename an item, mob, skill, quest, collection, pet, enchant, modifier, warp or progression
tree, keep the old id listed under `previous-ids`:

```yaml
blazing_sword:            # new id
  previous-ids: [fire_sword]
  material: DIAMOND_SWORD
  ...
```

Existing items, mobs and player progress under `fire_sword` then keep working as
`blazing_sword`. If two entries claim the same old id, it's ignored and a warning is logged.

## Reloading

- `/valmora validate` checks every content file for YAML and definition errors **without changing
  anything**. Run it before reloading.
- `/valmora reload` now tells you what went wrong: modules that failed, and each content error.
  **A broken entry or file never disappears from the live server.** It keeps its last working
  version until you fix it, even across restarts (these versions are stored in
  `plugins/Valmora/.last-good/`). Entries you delete on purpose are removed as expected.
- **Nothing that belongs to the players is disrupted by a reload:**
  - open menus are closed safely;
  - dialogues end;
  - timed quest objectives continue;
  - pets are re-summoned;
  - buffs, zones and HP/mana stay as they were.

## Repair tools

- `/valmora orphans <player>` lists saved progress that points at content that no longer exists
  (and isn't covered by `previous-ids`). It's kept on purpose: restoring the content, or adding a
  `previous-ids` entry, brings it back. `/valmora orphans <player> purge` deletes it.
- `plugins/Valmora/recovery/undecodable_items.log` keeps the raw data of any saved item that
  couldn't be read (e.g. after a server downgrade), so it can be recovered by hand. The player
  loads without it instead of being locked out.
- If a player's profile or balance can't be read, they're disconnected with a message and **nothing
  is overwritten**. Check the console for the database error, and let them rejoin.
- Profiles autosave every `profiles.autosave-interval-seconds` (default 300).
