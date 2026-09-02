# Pack Module — Admin Guide

The **Pack module** lets you install, uninstall, and roll back self-contained bundles of Valmora
content ("packs") — a new biome's worth of items/mobs/quests, a new modifier group, whatever — as a
single unit, hot-loaded with no server restart. See `docs/modules/design/pack.md` for internals.

---

## 1. Admin Command: `/valmora pack`

Requires `valmora.admin` (same node the rest of `/valmora`'s subcommands use).

| Command | Description |
|---|---|
| `/valmora pack install <dir\|url\|github:owner/repo@tag> [--sha256 <hash>]` | Installs a pack. `<dir>` is a local staged-pack folder; a `http(s)://` URL or `github:...` shorthand downloads it first. `--sha256` verifies the download before anything is extracted. |
| `/valmora pack inspect <dir>` | Validates a locally-staged pack (manifest structure, engine version, plugin dependencies) **without installing it** — a dry run. |
| `/valmora pack uninstall <pack-id>` | Removes an installed pack: its content, its shared-config additions, and its DB record. Leaves everything else untouched. |
| `/valmora pack list` | Lists every installed pack and its version. |
| `/valmora pack rollback <pack-id> [timestamp]` | Restores a pack's shared-config files from its most recent (or a specific) pre-install backup snapshot. |

Tab completion suggests installed pack ids for `uninstall`/`rollback`.

Examples:
```
/valmora pack install github:someauthor/frostspire-pack@v1.2.0
/valmora pack install https://example.com/downloads/frostspire-pack.zip --sha256 3a7bd3e2...
/valmora pack install plugins/Valmora/staging/frostspire
/valmora pack inspect plugins/Valmora/staging/frostspire
/valmora pack uninstall frostspire
/valmora pack rollback frostspire
```

Installing a pack whose id is already installed **upgrades it in place** — the old version is cleanly
uninstalled first, then the new one is installed. There's no need to `uninstall` before reinstalling
a newer version of the same pack.

---

## 2. What a Pack Is

A pack is a directory (or a `.zip` of one) shaped exactly like the plugin's own data folder:

```
frostspire/
├── pack.yml                 # required — the manifest, see §3
├── items/
│   └── frost_blade.yml
├── quests/
│   └── frost_quest.yml
├── modifiers/
│   ├── groups/frost_infusions.yml
│   └── definitions/frost_infusions.yml
└── rarities.yml              # a fragment — only the NEW rarities this pack adds
```

Any content folder Valmora already supports (`items`, `mobs`, `quests`, `recipes`, `guis`,
`modifiers/...`, and the rest — `docs/modules/modules.md` lists every module) can appear here. Any of
the ~10 mergeable shared configs (`rarities.yml`, `item_types.yml`, `mob_categories.yml`,
`entity_categories.yml`, `combat_pipeline.yml`, `resource_pipeline.yml`, `fishing_pipeline.yml`,
`item_pipeline.yml`, `mob_pipeline.yml`, `ui.yml`) can appear too, as a **fragment** containing only
what the pack adds — the pack's `rarities.yml` should never be a full copy of the server's rarity
list, just the new entries.

---

## 3. Writing `pack.yml`

```yaml
frostspire:                               # this IS the pack id — no wrapper key, same as every
                                           # other Valmora content file (items, mobs, recipes, ...)
  name: "Frostspire Expansion"
  version: "1.2.0"
  author: "SomeAuthor"
  description: "Adds the Frostspire biome, its mobs, quests, and a frost modifier group."
  engine_version_min: "1.0.0-beta1"       # required — the plugin version this pack needs at minimum
  engine_version_max: null                # optional upper bound

  depends:
    plugins: []                           # other Bukkit plugins this pack needs installed
    packs:
      - id: base_rarities_pack            # another pack this one requires
        version: ">=1.0.0"                # optional constraint: >=, <=, >, <, ==, or a bare version
  soft_depends:
    packs: []                             # missing = a warning, install still proceeds

  provides:
    content:                              # which content folders this pack ships
      - items
      - quests
      - modifiers/groups
      - modifiers/definitions
    shared:                               # which shared configs this pack adds fragments to
      - rarities.yml
```

**`provides.content`/`provides.shared` are checked, not decorative.** Naming a folder Valmora doesn't
recognize, or a shared file that isn't one of the ~10 mergeable ones, fails validation before anything
installs — `/valmora pack inspect` will tell you exactly which entry is wrong.

**Version fields must parse as semver** (`MAJOR.MINOR.PATCH`, an optional `-prerelease` suffix like
the plugin's own `1.0.0-beta1`). `engine_version_min` is required; if the server's running Valmora
version is older, install fails with a clear message rather than installing incompatible content.

---

## 4. Content IDs Are Automatically Namespaced

Every id a pack's content declares — an item id, a quest id, a modifier group id, anything — is
automatically prefixed with the pack's own id: an `items/frost_blade.yml` file containing a top-level
key `frost_blade` registers as `frostspire:frost_blade`, not `frost_blade`. This happens transparently
— you write ordinary content files exactly like base content, with no prefix of your own — and it's
what makes installing two packs (or a pack and base content) that happen to use the same short name
completely safe: `frostspire:frost_blade` and `another_pack:frost_blade` never collide.

**One consequence:** if your pack's own quest/recipe/modifier needs to reference your pack's own item
by id, you currently need to spell out the full `frostspire:frost_blade` form yourself — bare local
ids aren't automatically resolved against your pack's namespace yet inside content files (only the
registration side is automatic). Base-content ids (anything not shipped by a pack) are never
namespaced and are referenced exactly as they always have been.

---

## 5. Shared Config Fragments Are Additive Only

A pack's `rarities.yml` (or any other shared-config entry) is **merged**, never replaces the server's
file. Concretely:

- If your fragment adds a rarity/item-type/pipeline-stage/etc. that doesn't already exist, it's added.
- If your fragment's key name **collides** with something already in the server's file (an admin's
  own customization, or another installed pack's addition), your entry is **skipped** and a warning is
  shown — the existing value always wins, nothing is ever silently overwritten.
- Uninstalling the pack removes **exactly** what it added, nothing more — an admin's own edits to the
  same file, or another pack's additions, are never touched.

---

## 6. Safety

- **Checksums**: pass `--sha256 <hash>` on `install` to verify a downloaded archive before it's
  extracted — a mismatch aborts the install and deletes the partial download.
- **Zip-slip/zip-bomb guards**: a downloaded archive can't write outside its own staging directory, and
  is capped by both total entry count and total uncompressed size (`config.yml`: `pack.max-entries`,
  default 5000; `pack.max-extracted-size-mb`, default 200).
- **Rollback**: a pack install that fails partway through automatically rolls back everything it wrote
  — you never end up with a half-installed pack. `/valmora pack rollback <id>` also lets you manually
  restore a pack's shared-config state from before its most recent install/upgrade.
- **Dependency checks run before any file is written**: a pack missing a required plugin, a required
  other pack, or requiring an engine version this server doesn't meet, is rejected outright — nothing
  is installed and nothing needs to be cleaned up.

---

## 7. What's Not Here Yet

- **No pack index / marketplace.** There's no `/valmora pack install <bare-name>` that looks packs up
  by name from a hosted list — every install needs a direct URL, `github:owner/repo@tag`, or a local
  path.
- **No `/valmora pack export`** command for turning a folder of loose content into a shareable pack —
  you write `pack.yml` by hand today.
