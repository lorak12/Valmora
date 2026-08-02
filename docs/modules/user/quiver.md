# Quiver Module — User Documentation

> **Version:** 0.1 | **Server:** Paper 1.21.x | **Java:** 21
> **Module ID:** `quiver` | **Command:** `/quiver`

---

## Overview

The Quiver module gives every player a **private, per-profile arrow storage bag** that works
seamlessly with bows and crossbows.

- Open it with **`/quiver`** — a 27-slot menu that only accepts arrows.
- Your arrows are saved to your **active character profile** (like your stats and skills), so
  each of your profiles keeps its own quiver.
- Bows and crossbows **use arrows from your normal inventory first** (exactly as vanilla
  Minecraft does). Only when you have **zero arrows in your inventory or offhand** does the
  game automatically pull **one arrow** from your quiver into your inventory so you can keep
  firing.

**How ammo is drawn, in practice:**

1. You hold a bow or crossbow and right-click to draw.
2. If you have arrows anywhere in your inventory or offhand → vanilla behavior, nothing changes.
3. If your inventory has no arrows at all → one arrow is moved from your quiver into your
   inventory, and the draw/fire proceeds normally.
4. The fired arrow is consumed normally (unless you're in Creative, which never consumes ammo).

The quiver is purely a **fallback ammo reserve** — it never replaces your inventory arrows or
interferes with vanilla arrow consumption.

---

## Player Guide

### Opening the quiver

- Use the command: **`/quiver`**
- The quiver opens as a 27-slot chest menu with a dark-gray "➶ Quiver" title.

### Storing arrows

- **Drag or click** arrows (normal `Arrow`, `Tipped Arrow`, and `Spectral Arrow`) into the
  menu to store them.
- **Non-arrow items are rejected** — the game refuses to let you place swords, blocks, food,
  etc. into the quiver.
- Each player can hold up to **27 slots** of arrows, and stacks follow normal Minecraft stack
  size rules (arrows stack to 64).
- **Important:** items are only saved when you close the menu. Close the inventory to
  finalize your changes.

### Drawing arrows automatically

- If you right-click a **bow** while your inventory has no arrows, the plugin loans **one
  arrow** from the quiver into your inventory so you can shoot.
- The same applies to **crossbows**, with one nuance: a crossbow that is *already loaded* does
  not pull from the quiver (its arrow was consumed when it was loaded). Unload/reload it, or
  ensure you have at least one arrow in inventory for the initial load.
- Each draw loans exactly **one arrow**. The quiver stack is decremented by one.
- If the quiver is **empty**, or your inventory has **no room**, nothing is loaned and the
  quiver is left untouched.

### Per-profile storage

- The quiver belongs to your **active profile**. If you switch profiles, you see that
  profile's own quiver (and the previous profile's arrows are kept safely stored for when you
  switch back).
- Quiver contents persist across server restarts.

### Quick tips

- Keep a spare quiver stocked so you never run out mid-fight.
- Since the quiver only tops you up when your inventory is empty, keeping even a single arrow
  in your inventory means that arrow gets used first — stock the quiver as your deep reserve.

---

## Admin Guide

### Permissions

- **`/quiver` requires no permission.** Any player can open their own quiver.
- (There is no `valmora.*`-gated quiver command. Admin item-giving / general admin commands
  use `valmora.admin`, but the quiver menu itself is open to everyone.)

### Integration notes

- **No configuration needed.** The module is fully code-defined; there are no YAML files or
  `config.yml` keys to manage.
- **Fixed behavior:** 27 slots, arrow-types only, inventory-first draw, single-arrow loan on
  empty inventory, main-hand bow/crossbow only, Creative-mode players never loan (they don't
  consume ammo anyway).
- **Persistence:** quiver contents are stored per profile in the database
  (`quiver` column on the `valmora_profiles` table) and are written on normal profile save
  points (player quit, plugin reload/shutdown, profile creation). There is no periodic
  autosave of the quiver — an unclean server crash between closing the menu and a save point
  can lose recent quiver edits. This matches the general profile persistence behavior.
- **Hot reload:** `/valmora reload` (requires `valmora.admin`) disables and re-enables the
  module cleanly; the quiver listener is unregistered and re-registered correctly.
- **Known scope:** the fallback only triggers on right-click draw when the inventory has no
  arrows, and only for the **main hand**. This is by design per the current implementation.

### Notes on lookalikes (not this feature)

- Some bow abilities in `bows.yml` (e.g. "Consumes 1 Sulphur to double damage per shot")
  reference consuming items *from the inventory or quiver* as an **ability cost**. That is a
  separate, **not yet implemented** feature (the "ability-side quiver resource cost"); the
  ammo-storage quiver described here is complete and unrelated.
- The Skeleton Master armor-set bonus named "Endless Quiver" ("Your bows don't consume
  arrows") is an armor set bonus, not this module.

---

## Configuration Reference

**There are no configuration keys for the Quiver module.**

- No `quiver:` section exists in `config.yml`.
- No module YAML files are loaded or generated under `plugins/Valmora/`.
- Nothing to tune: slot count, GUI title, and the allowed-arrow set are hardcoded in the
  plugin.

If a future version adds tuning (e.g. slot count), this section will document those options
here.
