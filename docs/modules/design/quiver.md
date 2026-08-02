# Quiver Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package:** `org.nakii.valmora.module.quiver`
> **Module ID:** `quiver` | **Load order:** after `backpack`, before `progression`
> **Status:** implemented — ammo storage ✅; ability-side "quiver resource" cost still open (see [Unfinished Things / TODOs](#unfinished-things--todos))

---

## Overview

The Quiver module provides a **per-profile arrow store**. Players open it with `/quiver`,
which shows a 27-slot chest GUI (arrow-type items only) whose contents are saved to the
active `ValmoraProfile` and persisted to the database via the normal profile save/load path.

**Ammo draw model — inventory first, quiver only as fallback:**

- Bows/crossbows always draw ammo from the player's **normal inventory** first. Vanilla's own
  ammo check, draw animation, and arrow consumption are **left untouched**.
- Only when the player right-clicks a bow/crossbow with **zero arrows anywhere in their
  inventory or offhand** does the module **loan a single arrow** from the quiver into the
  inventory, *before* vanilla's ammo check runs. The rest of the draw/fire/consume flow then
  proceeds exactly as it would for an inventory arrow.
- A truly-out-of-ammo bow never fires an `EntityShootBowEvent`, so the fallback is hooked via
  `PlayerInteractEvent` instead (`QuiverListener.onBowUse`).

The module deliberately does **not** replace or intercept vanilla arrow consumption — it only
top-ups an empty inventory. See [Architecture & Key Classes](#architecture--key-classes) for
the exact flow.

**Module is part of the modular plugin system:** it implements `ReloadableModule`, registers
a single `Listener`, is enabled/disabled by `ModuleManager`, and hot-reloads via
`/valmora reload`. There is **no YAML configuration** — the module is entirely code-defined
(slot count, arrow tag, title text are hardcoded; see [Configuration (YAML)](#configuration-yaml)).

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/quiver/
├── QuiverModule.java          # ReloadableModule — GUI open/save, arrow checks, loan logic
├── QuiverListener.java        # Listener — inventory close/click guards + bow-use fallback
└── QuiverInventoryHolder.java # InventoryHolder — tags quiver inventories for the listeners
```

This follows the project's module convention (`XModule.java` + `XListener.java`); the module
has no `XRegistry`/`XLoader` because it is fully code-defined and loads no YAML.

| File | Role |
| --- | --- |
| `QuiverModule.java` | Module lifecycle (`onEnable`/`onDisable`/`getId`), opens/saves the GUI, `isArrow`, `hasArrowInInventory`, `loanArrowFromQuiver`. |
| `QuiverListener.java` | `InventoryCloseEvent` (persist), `InventoryClickEvent` (arrow-only guard), `PlayerInteractEvent` (bow-use fallback). |
| `QuiverInventoryHolder.java` | Minimal holder carrying the owning `Player` + the opened `Inventory`. |

**Wiring in `Valmora.java`:**

- Field declaration: `Valmora.java:122` (`private QuiverModule quiverModule;`)
- Instantiation: `Valmora.java:183` (`this.quiverModule = new QuiverModule(this);`)
- Module registration: `Valmora.java:221`
  `moduleManager.registerModule(quiverModule);   // Depends on playerManager for the active profile`
- Command registration: `Valmora.java:255-259` — `/quiver` executor calls
  `quiverModule.openQuiver(player)` **after** modules are enabled (per project rule §6.3 of
  `AGENTS.md`: commands are never registered inside a module).
- Concrete getter: `Valmora.java:430` — `getQuiverModule()` (note: **not** on the
  `ValmoraAPI` interface — see [API Exposed](#api-exposed)).

**plugin.yml command declaration** (`src/main/resources/plugin.yml:67-69`):

```yaml
  quiver:
    usage: /quiver
    description: Open your quiver.
```

The `quiver` command has **no `permission:` key**, so any player can use it.

---

## Architecture & Key Classes

### 1. Arrow storage — `QuiverModule`

`QuiverModule` (implements `ReloadableModule`, `QuiverModule.java:26`) owns the storage
contract on top of the active profile.

**Slot count** — `QuiverModule.java:28`:

```java
static final int QUIVER_SLOTS = 27;
```

This is a package-private constant used to create the inventory, snapshot it on close, and
populate it on open. Note the profile field also hardcodes 27 (`ValmoraProfile.java:36`) —
see [Possible Improvements / Changes](#possible-improvements--changes).

**Module lifecycle** (`QuiverModule.java:37-55`):

- `onEnable()` creates a fresh `QuiverListener(this)` and registers it with the plugin manager
  (`QuiverModule.java:38-41`). Idempotent — safe across hot reloads.
- `onDisable()` unregisters the listener via `HandlerList.unregisterAll(listener)` and nulls it
  (`QuiverModule.java:44-49`). This is the mandatory cleanup pattern from `AGENTS.md` §6.2 —
  without it, reloads would accumulate duplicate event handlers.
- `getId()` returns `"quiver"`, `getName()` returns `"Quiver"`.

**Resolving the active profile** — `QuiverModule.java:124-127`:

```java
private ValmoraProfile getProfile(Player player) {
    ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
    return session != null ? session.getActiveProfile() : null;
}
```

Every public operation (`openQuiver`, `saveQuiver`, `loanArrowFromQuiver`) is a silent no-op
if the player has no live session or no active profile (`getProfile` returns `null`).

**Opening the GUI** — `QuiverModule.java:57-72`:

```java
public void openQuiver(Player player) {
    ValmoraProfile profile = getProfile(player);
    if (profile == null) return;

    Component title = Formatter.format("<dark_gray>➶ Quiver");
    QuiverInventoryHolder holder = new QuiverInventoryHolder(player);
    Inventory inv = Bukkit.createInventory(holder, QUIVER_SLOTS, title);
    holder.setInventory(inv);

    ItemStack[] saved = profile.getQuiverItems();
    for (int i = 0; i < Math.min(saved.length, QUIVER_SLOTS); i++) {
        if (saved[i] != null) inv.setItem(i, saved[i]);
    }

    player.openInventory(inv);
}
```

- The title is MiniMessage-formatted via `Formatter.format(...)` (`util/Formatter.java:13-15`),
  which also force-strips italics via a MiniMessage post-processor (`util/Formatter.java:11`).
- The holder↔inventory back-reference is set explicitly because `Bukkit.createInventory` only
  receives the holder; the holder then hands it back in `getInventory()`.
- Saved items are copied **by reference** (`inv.setItem(i, saved[i])`), not cloned.

**Saving the GUI** — `QuiverModule.java:74-83`:

```java
public void saveQuiver(Player player, Inventory inv) {
    ValmoraProfile profile = getProfile(player);
    if (profile == null) return;

    ItemStack[] items = new ItemStack[QUIVER_SLOTS];
    for (int i = 0; i < QUIVER_SLOTS; i++) {
        items[i] = inv.getItem(i);
    }
    profile.setQuiverItems(items);
}
```

This snapshots the whole 27-slot inventory (including `null` slots) into the active profile.
It is called from `QuiverListener.onClose` — there is no live/incremental save while the GUI
is open.

### 2. Arrow validation & inventory checks

**`isArrow`** — `QuiverModule.java:85-87`:

```java
public boolean isArrow(ItemStack item) {
    return item != null && !item.getType().isAir() && Tag.ITEMS_ARROWS.isTagged(item.getType());
}
```

Uses the Bukkit `Tag.ITEMS_ARROWS` (the `minecraft:arrows` item tag: `arrow`,
`tipped_arrow`, `spectral_arrow`). This is the single gatekeeper for what may enter the
quiver and what counts as ammunition.

**`hasArrowInInventory`** — `QuiverModule.java:89-95`:

```java
public boolean hasArrowInInventory(Player player) {
    for (ItemStack item : player.getInventory().getContents()) {
        if (isArrow(item)) return true;
    }
    return isArrow(player.getInventory().getItemInOffHand());
}
```

Checks all 36 main-inventory slots **plus** the offhand. If any slot holds an arrow, the
quiver fallback is skipped (vanilla handles ammo entirely).

### 3. Bow-use fallback — `QuiverListener.onBowUse`

The fallback lives in `QuiverListener.java:45-68`. It is an `@EventHandler(ignoreCancelled = true)`
on `PlayerInteractEvent`:

```java
public void onBowUse(PlayerInteractEvent event) {
    Action action = event.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;

    Player player = event.getPlayer();
    ItemStack item = event.getItem();
    if (item == null) return;

    Material type = item.getType();
    if (type != Material.BOW && type != Material.CROSSBOW) return;
    if (player.getGameMode() == GameMode.CREATIVE) return;

    // A loaded crossbow already consumed its ammo when it was loaded, not on this fire click.
    if (type == Material.CROSSBOW
            && item.getItemMeta() instanceof CrossbowMeta crossbowMeta
            && crossbowMeta.hasChargedProjectiles()) {
        return;
    }

    if (module.hasArrowInInventory(player)) return;
    module.loanArrowFromQuiver(player);
}
```

Guard chain, in order:

| # | Guard | Purpose |
| --- | --- | --- |
| 1 | `action` is `RIGHT_CLICK_AIR` or `RIGHT_CLICK_BLOCK` | Only "draw" clicks. Left-click and physical block clicks are ignored. |
| 2 | `event.getHand() == EquipmentSlot.HAND` | Main hand only. Avoids double-firing from the offhand interaction (see `AGENTS.md` §11.8). |
| 3 | `event.getItem() != null` | Item present. |
| 4 | `Material.BOW` or `Material.CROSSBOW` | Only bow-type weapons. |
| 5 | `GameMode.CREATIVE` skip | Creative bows never consume arrows, so no top-up is needed. |
| 6 | Loaded crossbow skip | A crossbow consumed its arrow at **load** time; `CrossbowMeta.hasChargedProjectiles()` returns true for a loaded crossbow, so no ammo is needed on this click. |
| 7 | `module.hasArrowInInventory(player)` → return | Player already has arrows — let vanilla handle the draw entirely. |
| 8 | `module.loanArrowFromQuiver(player)` | Only reached when the inventory is completely out of arrows. |

**Why `PlayerInteractEvent` and not `EntityShootBowEvent`:** when a player has no arrows, the
bow can't even be drawn, so `EntityShootBowEvent` never fires. The loan must happen at the
right-click before vanilla's ammo check executes (documented in `QuiverModule.java:19-24`).

### 4. Loan logic — `QuiverModule.loanArrowFromQuiver`

`QuiverModule.java:97-122`:

```java
public boolean loanArrowFromQuiver(Player player) {
    ValmoraProfile profile = getProfile(player);
    if (profile == null) return false;

    ItemStack[] quiver = profile.getQuiverItems();
    for (int i = 0; i < quiver.length; i++) {
        ItemStack stack = quiver[i];
        if (!isArrow(stack)) continue;

        ItemStack loaned = stack.clone();
        loaned.setAmount(1);

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(loaned);
        if (!leftover.isEmpty()) return false; // no room — leave the quiver untouched

        stack.setAmount(stack.getAmount() - 1);
        if (stack.getAmount() <= 0) quiver[i] = null;
        return true;
    }
    return false;
}
```

Behavior:

- Scans quiver slots **in order** (0 → 26) for the first slot holding an arrow.
- Clones the stack and sets amount to 1, then `addItem`s it into the player's inventory
  (respecting normal stack merging / partial stack rules).
- **If there is no room** (`addItem` returned leftovers), the method returns `false` and the
  quiver is **left completely untouched** — nothing is consumed.
- Otherwise it decrements the source quiver stack by 1; if that empties the slot it's set to
  `null`. The original `ItemStack` object is mutated in place inside the profile array
  (`stack.setAmount(...)`), so the profile's `quiverItems` array reflects the change directly.
- Returns `true` on success, `false` when the quiver is empty or the inventory is full.

Note the **loan is permanent**: the loaned arrow is moved into the player's inventory and
will be consumed by vanilla like any inventory arrow. If the player cancels the draw, the
arrow simply stays in the inventory.

### 5. GUI holder — `QuiverInventoryHolder`

`QuiverInventoryHolder.java:7-22` — the minimal marker/carrier:

```java
public class QuiverInventoryHolder implements InventoryHolder {

    private final Player player;
    private Inventory inventory;

    public QuiverInventoryHolder(Player player) { this.player = player; }

    public Player getPlayer() { return player; }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
}
```

The listeners identify a quiver GUI exclusively via
`event.getInventory().getHolder() instanceof QuiverInventoryHolder`
(`QuiverListener.java:26`, `QuiverListener.java:33`). No PDC / display-name checks are used
(consistent with `AGENTS.md` §11.12 — GUI identity via holder, not names).

### 6. Event handling — `QuiverListener`

Registered once in `onEnable` (`QuiverModule.java:39-40`), unregistered in `onDisable`
(`QuiverModule.java:45-48`).

**`onClose(InventoryCloseEvent)`** — `QuiverListener.java:24-29`:

```java
public void onClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof QuiverInventoryHolder)) return;
    if (!(event.getPlayer() instanceof Player player)) return;
    module.saveQuiver(player, event.getInventory());
}
```

Any close of a quiver GUI (including via inventory switch or disconnect) snapshots its
contents back into the active profile. This is the only write path for stored arrows.

**`onClick(InventoryClickEvent)`** — `QuiverListener.java:31-39`:

```java
public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof QuiverInventoryHolder)) return;
    // Only allow arrow-type items in the quiver — reject anything else being placed in
    ItemStack cursor = event.getCursor();
    if (cursor != null && !cursor.getType().isAir() && !module.isArrow(cursor)) {
        event.setCancelled(true);
    }
}
```

Rejects placing a non-arrow item **from the cursor** into the quiver. See
[Possible Improvements / Changes](#possible-improvements--changes) for the shift-click gap.

### 7. Persistence path

The quiver is persisted as part of the profile save pipeline — there is no quiver-specific
database code inside the module itself. See [Data Model / Persistence](#data-model--persistence).

---

## Configuration (YAML)

**The Quiver module has no YAML configuration.**

- No `quiver:` section exists in `src/main/resources/config.yml`.
- No files are loaded by the module — there is no `XLoader`/`YamlLoader` usage in
  `module/quiver/` (unlike item/mob/skill modules).
- Everything is hardcoded in code:

| Hardcoded value | Location |
| --- | --- |
| Slot count `27` | `QuiverModule.java:28` (and mirrored in `ValmoraProfile.java:36`) |
| GUI title `<dark_gray>➶ Quiver` | `QuiverModule.java:61` |
| Arrow gate `Tag.ITEMS_ARROWS` | `QuiverModule.java:86` |
| Weapon types `BOW` / `CROSSBOW` | `QuiverListener.java:56` |

There are therefore no defaults to override and no per-server tunables (slot count, title,
allowed arrows) at this time.

---

## Data Model / Persistence

### In-memory: `ValmoraProfile.quiverItems`

`ValmoraProfile.java:35-36`:

```java
// Quiver (27 slots, arrow-type items only)
private ItemStack[] quiverItems = new ItemStack[27];
```

Accessors at `ValmoraProfile.java:97-98`:

```java
public ItemStack[] getQuiverItems() { return quiverItems; }
public void setQuiverItems(ItemStack[] items) { this.quiverItems = items; }
```

- The array is initialized to a **27-length array of nulls** — unlike `savedInventory`
  (which defaults to `null` to mean "no snapshot yet", `ValmoraProfile.java:28`), the quiver
  is always a concrete empty array. An empty quiver is therefore a 27-null array, not null.
- It lives on the **profile**, not the player — each profile has its own quiver, so switching
  profiles (`PlayerManager.switchProfile`, `module/profile/PlayerManager.java:150-163`)
  switches the visible quiver too.

### Database: `quiver` column on `valmora_profiles`

**Schema migration v2** — `SQLDataStore.java:117-120`:

```java
/** v2 — adds the quiver column (per-profile arrow storage). */
private void migrateToV2(Connection conn) throws SQLException {
    addColumnIfMissing(conn, "valmora_profiles", "quiver", "TEXT");
}
```

Applied by the migration framework in `SQLDataStore.java:109-112` (gated on
`from < 2`, then stamps schema version 2). `addColumnIfMissing`
(`SQLDataStore.java:164-171`) tolerates the "already exists" error, so the migration is
idempotent on fresh databases and re-runs.

**Load** — `SQLDataStore.java:246-249`:

```java
try {
    String quiverJson = rsProfiles.getString("quiver");
    profile.setQuiverItems(deserializeItemArray(quiverJson, profile.getQuiverItems().length));
} catch (SQLException ignored) {}
```

A missing column (shouldn't happen post-migration) is swallowed. `deserializeItemArray`
returns a fresh `size`-length array (`SQLDataStore.java:408-418`), using
`profile.getQuiverItems().length` = 27 as the size.

**Save** — inside the profile upsert (`SQLDataStore.java:303`, bound at `SQLDataStore.java:312`
insert and `SQLDataStore.java:325` update):

```java
String quiverJson = serializeItemArray(profile.getQuiverItems());
```

The column is the 11th insert column and the 9th update column; the `quiver` upsert SQL is
visible in both the MySQL and SQLite statements (`SQLDataStore.java:287-288`).

**Item array serialization** — `SQLDataStore.java:397-435`:

```java
private String serializeItemArray(ItemStack[] items) {
    if (items == null) return null;
    String[] encoded = new String[items.length];
    for (int i = 0; i < items.length; i++) {
        encoded[i] = encodeItem(items[i]);
    }
    return gson.toJson(encoded);
}

private ItemStack[] deserializeItemArray(String json, int size) {
    ItemStack[] result = new ItemStack[size];
    if (json == null) return result;
    String[] encoded = gson.fromJson(json, String[].class);
    if (encoded == null) return result;
    for (int i = 0; i < Math.min(encoded.length, size); i++) {
        if (encoded[i] == null) continue;
        result[i] = decodeItem(encoded[i]);
    }
    return result;
}

private String encodeItem(ItemStack item) {
    if (item == null || item.getType().isAir()) return null;
    try {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    } catch (Exception e) {
        return null;
    }
}

private ItemStack decodeItem(String encoded) {
    try {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
    } catch (Exception e) {
        return null;
    }
}
```

- The quiver is serialized as a **JSON array of base64-encoded item byte strings** (one
  element per slot; empty slots are `null`).
- `encodeItem` uses modern `ItemStack.serializeAsBytes()` / `deserializeBytes()` (Paper 1.21,
  component-aware — see `AGENTS.md` §11.5). Un-serializable items degrade to `null` rather
  than failing the save.
- This helper is shared and intended for "any future flat item-array profile field that
  isn't the multi-part player inventory" (comment at `SQLDataStore.java:397-398`); the
  player inventory itself uses a dedicated 41-slot serializer
  (`serializeInventory`/`deserializeInventory`, `SQLDataStore.java:353-395`).

### When quiver data hits the database

The module writes to the in-memory profile only (`saveQuiver`, `QuiverModule.java:74-83`).
The DB write happens through the normal profile save flow:

- On player quit — `PlayerManager.handleQuit` → `dataStore.savePlayer(stored)`
  (`PlayerManager.java:127-137`).
- On module disable / plugin disable — `PlayerManager.onDisable` saves every active session
  (`PlayerManager.java:116-118`), and `Valmora.onDisable` also flushes sessions before
  closing the store (`Valmora.java:270-275`).
- On profile creation — `dataStore.savePlayer(vp)` (`PlayerManager.java:196`, `:210`).

There is **no per-profile autosave timer** for the quiver (unlike the economy write-behind,
`config.yml` → `economy.autosave-interval-seconds`); quiver contents persist to disk only on
one of the save events above.

### Test coverage

- `SQLDataStoreTest.java` exercises the schema and migration:
  - `initCreatesSchemaAndStampsVersion` asserts the `quiver` column exists on a fresh DB
    (`SQLDataStoreTest.java:50`).
  - `initIsIdempotent` runs `init()` twice (`SQLDataStoreTest.java:58-68`).
  - `migratesPreVersioningDatabase` asserts the column is added to a legacy DB
    (`SQLDataStoreTest.java:91`).
  - `migratesV1DatabaseToAddQuiverColumn` builds a v1-schema DB and asserts the v2 migration
    adds `quiver` (`SQLDataStoreTest.java:98-123`).
- There is **no dedicated unit test** for `QuiverModule` / `QuiverListener` (the modules'
  bow-use logic depends on Bukkit's `Player`, `Inventory`, `PlayerInteractEvent`, so no test
  currently exists in `src/test`).

---

## API Exposed

| Accessor | Where | Visibility |
| --- | --- | --- |
| `QuiverModule getQuiverModule()` | `Valmora.java:430` | Concrete `Valmora` class only |
| `openQuiver(Player)` | `QuiverModule.java:57` | Public method on the module |
| `saveQuiver(Player, Inventory)` | `QuiverModule.java:74` | Public method on the module |
| `isArrow(ItemStack)` | `QuiverModule.java:85` | Public method on the module |
| `hasArrowInInventory(Player)` | `QuiverModule.java:89` | Public method on the module |
| `loanArrowFromQuiver(Player)` | `QuiverModule.java:102` | Public method on the module |
| `QUIVER_SLOTS` (`27`) | `QuiverModule.java:28` | **Package-private** constant |

**Important:** the Quiver module is **not** on the `ValmoraAPI` interface. `ValmoraAPI.java`
has no `getQuiverModule()` — cross-module consumers must either access it through the
concrete `Valmora.getInstance()` instance or use `ValmoraAPI.getInstance().getModuleManager().getModule("quiver")`.
Within the module itself, dependency access goes through `ValmoraAPI.getInstance()`
(`QuiverModule.java:125`) as required by the module conventions.

Currently **no other module consumes** any of these methods (verified by grep — only
`Valmora.java` and the `module/quiver` package reference `QuiverModule`/`openQuiver`/etc.).

---

## Dependencies & Consumers

**Dependencies (consumed by the quiver module):**

| Dependency | How used | Location |
| --- | --- | --- |
| `PlayerManager` | Resolve the player's live session + active profile | `QuiverModule.java:125` via `ValmoraAPI.getInstance().getPlayerManager().getSession(...)` |
| `ValmoraProfile` | Store `quiverItems` in-memory | `QuiverModule.java:14`, `ValmoraProfile.java:35-36` |
| `SQLDataStore` | Persist `quiverItems` as the `quiver` TEXT column | `SQLDataStore.java:117-120`, `:246-249`, `:303` |
| `Formatter` | MiniMessage GUI title | `QuiverModule.java:61`, `util/Formatter.java:13-15` |
| `Tag.ITEMS_ARROWS` | Arrow-type gate | `QuiverModule.java:86` |

**Consumer(s):**

| Consumer | How it uses the module | Location |
| --- | --- | --- |
| `Valmora.onEnable` | Registers the module and wires the `/quiver` command | `Valmora.java:221`, `:255-259` |
| `Valmora` (concrete) | Exposes `getQuiverModule()` | `Valmora.java:430` |

**Load order:** registered after `backpack` and before `progression`
(`Valmora.java:221-222`), with the comment `Depends on playerManager for the active profile`.
`playerManager` is registered earlier (`Valmora.java:191`), so the dependency is satisfied at
enable time. Registration order is preserved as required by `AGENTS.md` §5.

---

## Unfinished Things / TODOs

1. **Ability-side "quiver resource" cost — open.** `docs/UNFINISHED_FEATURES.md` §10
   (lines 172-192) explicitly separates the implemented *ammo storage* from the *different*
   concept referenced in some `bows.yml` ability descriptions: consuming arrows **as an
   ability cost** (e.g. "Consumes Prismarine Shards to harm Sea Creatures", "Consumes 1
   Sulphur to double damage per shot"). Those abilities are still marked
   `# DESCRIPTION-ONLY:` in `src/main/resources/items/bows.yml`:
   - `prismarine_bow` → `arrow_infusion` (`bows.yml:69-77`, comment at `:72`)
   - `slime_bow` → `arrow_infusion` (`bows.yml:125-131`, comment at `:128`)
   - `sulphur_bow` → `arrow_infusion` (`bows.yml:160-166`, comment at `:163`)
   - `venoms_touch` region (`bows.yml:257`) — quiver consumption to double shot damage
   - Related ability dumps: `docs/ABILITIES_DUMP.md:317,334,356,379`
   This is a mechanic-engine feature (a "spend N arrows from inventory or quiver" mechanic)
   that would interact with this module's storage but is not implemented. It needs the
   mechanic-engine work described in `UNFINISHED_FEATURES.md` §2.

2. **Unrelated lookalike — "Endless Quiver" set bonus.** `armor_sets.yml:3972-3976`
   defines a Skeleton Master chestplate ability named "Endless Quiver" (PASSIVE, "Your bows
   don't consume arrows"). This is a **set-bonus ability** (`SetBonusService`), not the
   Quiver module; it is documented here only to disambiguate.

3. **Crossbow niche:** the fallback handles a *loaded* crossbow by skipping
   (`QuiverListener.java:60-64`), because its ammo was consumed at load time — meaning a
   loaded crossbow won't pull from the quiver at all. Whether that is desired for the
   "crossbow + quiver" case is an open design question (no decision recorded).

---

## Possible Improvements / Changes

1. **Shift-click bypass of the arrow-only guard.** `QuiverListener.onClick` only inspects
   `event.getCursor()` (`QuiverListener.java:35-38`). On a **shift-click**, the cursor is
   usually empty, so a non-arrow item shift-clicked from the player's inventory into the
   quiver is **not** rejected by this handler. The code comment says "Only allow arrow-type
   items in the quiver", so the intent is arrow-only — consider also guarding
   `event.getClickedInventory()` / the moved `current` item, or locking quiver slots
   outright.

2. **No user feedback.** When the quiver is empty or the inventory is full,
   `loanArrowFromQuiver` returns `false` silently (`QuiverModule.java:115`, `:121`) — the
   player just fails to draw with no message. No message is shown either when the player
   tries to place a non-arrow item (`QuiverListener.java:36-38`). Consider sending a
   MiniMessage notice for both cases.

3. **Deduplicate the 27-slot constant.** The slot count is defined twice: `QUIVER_SLOTS` in
   `QuiverModule.java:28` and a literal `27` in `ValmoraProfile.java:36`. If the size ever
   changes they can drift. Consider a single shared constant (e.g. on `ValmoraProfile` or a
   shared quiver constants class).

4. **Quiver not exposed on `ValmoraAPI`.** Consumers must reach the module via the concrete
   `Valmora` class (`Valmora.java:430`) or `ModuleManager.getModule("quiver")`. Adding
   `getQuiverModule()` to the `ValmoraAPI` interface would make it usable by other modules
   following the standard API pattern (see `MODULE_DEVELOPMENT.md` §8).

5. **No autosave for the quiver.** Quiver contents persist only when the profile is saved
   (quit / disable / profile create — `PlayerManager.java:116-118`, `:196`, `:210`). A crash
   after closing the GUI loses changes. A periodic profile-save task (like the economy
   write-behind in `config.yml`) would close that window, but that is a cross-cutting profile
   change, not quiver-specific.

6. **Loan is an out-of-band inventory mutation.** The loaned arrow is permanently moved into
   the player's inventory (`QuiverModule.java:111-114`). If the player cancels the draw, the
   arrow remains. Alternative models (loan-and-return on cancel, or direct "infinite quiver
   ammo" draw) would need design input; the current design deliberately reuses vanilla
   consumption unchanged.

7. **Main-hand only.** `QuiverListener.java:49` requires `event.getHand() ==
   EquipmentSlot.HAND`; offhand bows never trigger the fallback. If offhand bow use is ever
   intended, this guard would need revisiting.

8. **No config surface.** Slot count, GUI title, and arrow tag are hardcoded. If server
   owners should tune these, a `quiver:` section in `config.yml` (loaded via `YamlLoader`)
   would be the standard way forward (see `MODULE_DEVELOPMENT.md` §4).
