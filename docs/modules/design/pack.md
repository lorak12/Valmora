# Pack Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package:** `org.nakii.valmora.module.pack` (+ `.manifest`, `.validate`, `.install`, `.download`)
> **Module ID:** `pack`
> **Load order:** registered **last**, after `progression` — deliberately never a dependency of
> anything else; it only orchestrates other modules' existing reload machinery.
> **Status:** implemented — manifest parsing, mandatory content-id namespacing (transparent to every
> existing content loader), structural/engine-version/plugin-dependency/pack-dependency-graph
> validation, install/uninstall/upgrade-in-place against a locally-staged pack directory with
> automatic rollback on a mid-install failure, non-destructive shared-config merging with a
> precise per-key diff for uninstall, targeted module reload (only the modules a pack's content
> touches), and remote install from a direct URL or a `github:owner/repo@tag` release asset with
> SHA-256 verification and zip-slip/zip-bomb-guarded extraction. **Not implemented:** a hosted pack
> index (`/valmora pack install <bare-id>` — see §10 below), an export/bundler command for pack
> authors, and per-content-type reference-integrity checkers (the extension point exists,
> `PackValidator.validateReferences` + `PackReferenceCheckerRegistry`, but nothing is registered yet).

Design source: this document and the four-phase implementation it describes (`~/.claude/plans` —
Content Pack Manager plan). No prior design doc existed for this feature.

---

## 1. Overview

The Pack module is a **content bundle installer**: it lets a server admin run
`/valmora pack install <dir|url|github:owner/repo@tag>` to download (or read locally), validate, and
hot-load a self-contained bundle of Valmora content — items, mobs, quests, a modifier group, shared
rarity/type additions, anything the ~24 content folders and ~10 mergeable shared configs already
support — without hand-copying files or restarting the server, and to cleanly `uninstall` it again
later with **zero trace left behind**.

**Key facts to understand the design:**

- **A pack is a directory, not a new file format.** It's laid out exactly like the plugin's own
  `plugins/Valmora/` data folder — a `pack.yml` manifest at the root, plus whichever content folders
  and shared-config fragments it declares (`items/frost_blade.yml`, `rarities.yml`, ...). A remote
  pack is just this same directory shape, zipped.
- **Namespacing is mandatory and structural, not a convention admins have to follow.** Every content
  id a pack registers is transparently rewritten to `<pack_id>:<local_id>` by a single choke point in
  `YamlLoader` itself (§3) — collisions between packs, or between a pack and base content, are
  therefore *impossible*, not merely detected and rejected.
- **No pack-type-specific engine code**, matching the modifier framework's rule (CLAUDE.md,
  Modifier Framework section): there is no `if contentFolder == "items"` branch anywhere in this
  module. Content-folder→owning-module mapping is a static lookup table
  (`PackContentFolders`), shared-config merging is driven by YAML *shape* (list vs. section), not by
  filename, and reference-integrity checking is an extension point other modules opt into, not
  something this module hardcodes per content type.
- **Validation runs entirely before any file is written.** Manifest structure, engine-version
  compatibility, Bukkit plugin dependencies, and the pack-to-pack dependency graph (topological sort
  + cycle detection) are all checked against the manifest and the already-installed-pack ledger alone
  — a pack that fails any of these never touches the live data folder.
- **Every write is reversible.** A pack's own content lives exclusively under its own
  `<folder>/<pack_id>/` subpath (trivial to delete cleanly); a shared-config merge is tracked as a
  precise per-top-level-key diff (§6) so uninstall removes exactly what that pack added and nothing
  else, even if two packs both add rarities to the same file.
- **Reload is targeted, not global.** Installing a pack that only ships items+quests reloads only the
  `items` and `quest` modules (via `ModuleManager.reloadModules(Set<String>)`, added for this
  purpose), not the whole server via `/valmora reload`.

---

## 2. Architecture & Key Classes

### 2.1 `org.nakii.valmora.module.pack` — core

| Class | Role |
|---|---|
| `PackModule` | `ReloadableModule`, ID `pack`. Registered last. Owns a `PackManager` (rebuilt each `onEnable()`, loading the installed-pack cache from the DB ledger) and a `PackReferenceCheckerRegistry`. **Does not** own the namespacing hook's lifecycle — see §3's important note. |
| `PackManager` | The orchestrator — `install(File)`, `installFromSource(source, sha256, callback)`, `uninstall(packId)`, `rollback(packId, timestamp)`, `listInstalled()`. Ties together `PackManifestParser`, `PackValidator`/`PackDependencyResolver`, `PackInstaller`, `DataStore`, and `ModuleManager`. |
| `PackRecord` | The persisted ledger row (see §7): pack id, version, checksum, install time, the exact relative file paths it owns, its shared-config diff, and its hard-dependency pack ids. |
| `PackFileIndex` | In-memory `filePath -> owning pack id` index (folder-prefix registration, not path inference — see §3). Also knows how to `reindexFromFileManifest` a `PackRecord` after a restart, since the index itself is never persisted. |
| `PackNamespacer` | Wires a `PackFileIndex` into `YamlLoader`'s global id-qualifying hook (§3). |
| `PackScopedIdResolver` | Resolves a bare local id written inside a pack's own YAML against that pack's namespace first, falling back to a global lookup — an opt-in utility, not wired into any content type's loader yet. |
| `PackContentFolders` | Static whitelists/lookup tables: known content-folder entries, known mergeable shared configs, and the content-folder → owning-module-id map `PackManager` uses to compute a targeted reload set. |
| `SemVer` | Minimal semver parser/comparator + constraint evaluation (`>=1.0.0`) — the engine had no versioning convention before this. |

### 2.2 `org.nakii.valmora.module.pack.manifest`

| Class | Role |
|---|---|
| `PackManifest` | Parsed `pack.yml` shape — id/name/version/author/description/engine-version bounds/plugin+pack dependencies/`provides.content`/`provides.shared`/checksum. |
| `PackDependency` | One `depends.packs`/`soft_depends.packs` entry: an id + optional version constraint string. |
| `PackManifestParser` | Parses a `pack.yml` file (or an already-located `(id, section)` pair, matching `YamlLoader.SectionParser`'s shape). Follows the same "one top-level key = the entity's own id, no wrapper key" convention as every other content type. |

### 2.3 `org.nakii.valmora.module.pack.validate`

| Class | Role |
|---|---|
| `PackValidationReport` | Collected `errors` (blocking) + `warnings` (advisory) — matches `YamlLoader`/`ModifierValidator`'s "collect everything, report once" convention. |
| `PackValidator` | Static validation entry points: `validateManifest` (structure + engine version + plugin deps), `validateDependencyGraph` (delegates to `PackDependencyResolver`), `validateReferences` (delegates to the `PackReferenceCheckerRegistry` extension point — see §9). |
| `PackDependencyResolver` | Topological sort (Kahn's algorithm) of a candidate pack batch's `depends.packs` edges against each other and against already-installed packs; cycle detection; missing/incompatible hard dependency = error, soft dependency = warning only. |
| `PackReferenceChecker` / `PackReferenceCheckerRegistry` | Extension point (functional interface + registry, mirrors `ScriptModule`'s `VariableProvider` pattern) for a content-type module to register its own "does this pack's content reference anything that doesn't exist" check. Nothing registered yet — see §9. |

### 2.4 `org.nakii.valmora.module.pack.install`

| Class | Role |
|---|---|
| `PackInstaller` | The file-level write path: copies `provides.content` into `<folder>/<pack_id>/...`, registers ownership into `PackFileIndex`, merges `provides.shared` via `SharedConfigMerger`, assembles the `PackRecord`. `uninstall(record)` reverses all of it. A mid-install `IOException` triggers `rollbackPartialInstall` (deletes everything copied so far, restores the pre-merge shared-config snapshot). |
| `SharedConfigMerger` | Merges a pack's shared-config fragment into the live file **by YAML shape**, not by filename (see §6). `revert(file, diff)` undoes exactly a prior `merge`'s diff. |
| `PackBackupManager` | Snapshots (zips) only the shared-config files an install is about to touch, before it touches them — content-folder subpaths need no snapshot since they're exclusively pack-owned. Backs `PackManager.rollback` and `PackInstaller`'s own failure-path rollback. |

### 2.5 `org.nakii.valmora.module.pack.download`

| Class | Role |
|---|---|
| `PackDownloader` | Streams a URL to disk while computing SHA-256; rejects and deletes the partial file on a checksum mismatch or non-2xx response. Mirrors `SkinResolver`'s `HttpClient` idiom (the only prior async-HTTP precedent in this codebase). |
| `PackExtractor` | Extracts a downloaded archive into a fresh staging directory with a zip-slip guard (absolute-path rejection + normalized-path containment check against the staging root) and two independent zip-bomb guards (entry count, cumulative uncompressed bytes — checked incrementally during extraction, not from the archive's own reported sizes). |
| `GitHubReleaseResolver` | Parses `github:owner/repo[@tag]`, resolves it to a downloadable `.zip` asset URL via GitHub's Releases API. Split into pure functions (`parseShorthand`, `extractZipAssetUrl`) tested without network access, plus a thin `resolve()` glue method for the one live call. |

---

## 3. The Namespacing Choke Point (and a bug caught mid-implementation)

Every content id a pack registers must carry its pack's namespace prefix, mandatorily — this is what
makes cross-pack/base-content id collisions structurally impossible rather than merely detected.

The naive implementation would touch every one of the ~19 `new YamlLoader<>(...)` call sites across
the engine to thread a `sourceId`/pack-prefix through each loader's `registerAction`. Instead,
**`YamlLoader` itself gained one static hook**:

```java
// YamlLoader.java
private static volatile BiFunction<String, String, String> idQualifier;
public static void setIdQualifier(BiFunction<String, String, String> qualifier) { idQualifier = qualifier; }
```

Both of `YamlLoader`'s parse call sites (`load()` and `loadFilesAsSections()`) run every parsed id
through this hook before handing it to the caller's `SectionParser`. `PackNamespacer.install(index)`
installs a hook that consults a `PackFileIndex` (an explicit `filePath-prefix -> pack id` map,
populated by `PackInstaller` at install time — deliberately **not** inferred from folder structure,
since several content types already use subfolders for pure organisation with no pack-ownership
meaning at all, e.g. `recipes/anvil/`, `recipes/crafting/`). An id that's already namespaced (contains
`:`) is passed through unchanged, so a literal cross-pack reference in a pack author's own YAML is
never double-prefixed. This means **zero changes were needed to any of the ~19 existing loader call
sites** — the strongest realization of "no per-content-type Java" this module achieves.

**The bug, and the fix.** The natural first instinct — install the hook in `PackModule.onEnable()`,
uninstall it in `onDisable()` — is wrong, and it compiled and passed every test until an end-to-end
check caught it. `PackModule` is registered **last**, so its `onEnable()` runs after every
content-loading module has already loaded its content. Worse, `/valmora reload`
(`ModuleManager.reloadModules()`) disables every module in reverse order then re-enables all of them
in forward order **in one pass** — `PackModule.onDisable()` (last registered, so *first* disabled)
would tear the hook down, and it wouldn't come back until `PackModule.onEnable()` runs dead last in
the very same reload pass, after every earlier module had already reloaded its content unnamespaced.
The bug would have silently repeated on **every single `/valmora reload`**, not just first boot.

The fix: the hook's lifetime is **plugin-lifetime, not module-lifetime**. `Valmora.onEnable()` calls a
private `primePackNamespacing()` directly — after `dataStore.init()` succeeds, before
`moduleManager.enableModules()` runs at all — which constructs the `PackFileIndex`, installs the hook,
and rebuilds the index from the DB pack ledger (`PackFileIndex.reindexFromFileManifest`, one blocking
`dataStore.loadPackRecords().join()`, same one-time-startup-cost posture as `dataStore.init()` itself
immediately above it). `Valmora.onDisable()` calls `PackNamespacer.uninstall()` once, after
`moduleManager.disableModules()`, at actual plugin shutdown — never on a mid-session `/valmora
reload`. `PackModule` receives the already-primed `PackFileIndex` via its constructor and only manages
its own DB-backed bookkeeping (`PackManager`'s installed-pack cache) in its own `onEnable()`/
`onDisable()`, exactly like every other module's registry-clear-then-reload pattern.

---

## 4. Manifest Format (`pack.yml`)

```yaml
frostspire:                               # pack id — becomes the mandatory namespace prefix
  name: "Frostspire Expansion"
  version: "1.2.0"                        # semver (SemVer.parse)
  author: "SomeAuthor"
  description: "Adds the Frostspire biome, its mobs, quests, and a frost modifier group."
  engine_version_min: "1.0.0-beta1"       # checked against plugin.getDescription().getVersion()
  engine_version_max: null                # optional upper bound
  checksum: "sha256:...."                 # optional self-declared archive hash

  depends:
    plugins: ["Vault"]                    # checked via Bukkit.getPluginManager().getPlugin(name)
    packs:
      - id: base_rarities_pack
        version: ">=1.0.0"                # >=, <=, >, <, ==/=, or a bare version meaning ==
  soft_depends:
    packs:
      - id: optional_addon                # missing = warning only, install still proceeds

  provides:
    content:                              # copied to <folder>/<pack_id>/... ; each entry validated
      - items                             # against PackContentFolders.isKnownContentEntry
      - quests
      - modifiers/groups
      - modifiers/definitions
    shared:                               # non-destructively merged; validated against
      - rarities.yml                      # PackContentFolders.isKnownSharedConfig
      - item_types.yml
```

Like every other Valmora content type, the top-level key **is** the entity id — there is no
`manifest:` wrapper key. `PackManifestParser.parseFile` requires the file to have exactly one
top-level key for this reason (zero or more than one is a hard parse failure).

---

## 5. Validation Pipeline

Run by `PackManager.install`/`installFromSource` before any file is written, in this order (all
failures collected into one `PackValidationReport`, not fail-fast):

1. **Structural** (`PackValidator.validateManifest` → `validateStructure`): version/engine-version
   strings parse as semver; every `provides.content` entry is a known content folder (or subfolder of
   one); every `provides.shared` entry is a known mergeable shared config; every dependency's version
   constraint string parses.
2. **Engine version** (`validateEngineVersion`): the running plugin version must be `>= engine_version_min`
   and, if set, `<= engine_version_max`.
3. **Plugin dependencies** (`validatePluginDependencies`): every `depends.plugins` entry must resolve
   via `Bukkit.getPluginManager().getPlugin(name)`.
4. **Pack dependency graph** (`PackValidator.validateDependencyGraph` → `PackDependencyResolver.resolve`):
   built from the single candidate pack plus every already-installed pack (loaded from the DB ledger,
   represented as a minimal stand-in `PackManifest` — id/version only, since the full manifest isn't
   persisted). Missing/version-mismatched hard dependencies and cycles are errors; missing soft
   dependencies are warnings only.
5. **Reference integrity** (`PackValidator.validateReferences`): runs every registered
   `PackReferenceChecker` and folds findings in as warnings. See §9 — nothing is registered today.

Only after all of the above pass does `PackInstaller.install` run.

---

## 6. Shared-Config Merging

The ~10 mergeable shared configs (`PackContentFolders.SHARED_CONFIGS`) take two shapes at their top
level: a **list** (`item_types.yml`'s flat string list, `combat_pipeline.yml`'s `stages:` list of
stage maps) or a **section** (`rarities.yml`'s `rarities:` map of entries). `SharedConfigMerger`
merges by *shape*, not by filename:

- **List top-level key**: new elements not already present (by `.equals()`) are appended; the diff
  records their canonicalized (`String.valueOf`) form.
- **Section top-level key**: new sub-keys are added (`putIfAbsent` — generalizes
  `QuestPackageManager.mergeTemplates()`'s existing precedent); a colliding sub-key is left untouched
  and reported as a warning, never overwritten.
- **A top-level key entirely absent from the base file**: added wholesale; the diff records the
  single sentinel entry `"*"` (meaning "the whole key was added fresh").
- **Any other shape mismatch** (e.g. the fragment's key is a list but the base's is a scalar): left
  untouched, warned about.

The diff (`Map<String, List<String>>` per file — top-level key → added sub-keys/list-entries, or
`["*"]`) is exactly what `revert(file, diff)` needs to undo precisely this pack's contribution: delete
the whole key if it was the sentinel, remove just the listed sub-keys from a section, or remove just
the listed elements from a list. Two packs adding different rarities to the same `rarities.yml` file
each get their own independent diff and can be uninstalled independently without disturbing the
other's additions or any admin-authored content.

`PackRecord.sharedDiff` is `Map<String, Map<String, List<String>>>` (file → that file's diff) so one
pack's ledger row can track diffs across multiple shared-config files at once.

---

## 7. Database Ledger

`valmora_installed_packs` (added in `SQLDataStore.migrateToV8()`, `LATEST_SCHEMA_VERSION` bumped 7→8,
following the existing versioned in-code migration convention — no separate `.sql` files):

```sql
CREATE TABLE IF NOT EXISTS valmora_installed_packs (
  pack_id TEXT PRIMARY KEY,
  version TEXT NOT NULL,
  checksum TEXT,
  installed_at BIGINT NOT NULL,
  file_manifest TEXT NOT NULL,   -- JSON list of relative file paths this pack owns
  shared_diff TEXT NOT NULL,     -- JSON: {file -> {top-level key -> [added sub-keys/elements]}}
  depends_on TEXT                -- JSON list of pack ids this pack hard-depends on
);
```

`DataStore.savePackRecord`/`loadPackRecords`/`deletePackRecord` follow the existing
Gson-JSON-into-TEXT-column idiom (`EconomyLedgerEntry` etc.). This table is the **sole source of
truth** for what's installed — `PackFileIndex` (namespacing) and `PackManager`'s in-memory cache
(dependency/list/upgrade checks) are both rebuilt from it, on plugin startup and on `PackModule`
`onEnable()` respectively, never persisted independently.

---

## 8. Install / Uninstall / Rollback Semantics

- **`PackManager.install(File packSourceDir)`**: parse manifest → validate (§5) → if a pack with the
  same id is already installed, `uninstall` it first (upgrade = clean uninstall + fresh install, no
  in-place content diffing in this version) → `PackInstaller.install` → `dataStore.savePackRecord` →
  `ModuleManager.reloadModules(affectedModuleIds)`, where the affected set is computed from
  `manifest.providesContent()` via `PackContentFolders.moduleIdFor`.
- **`PackManager.installFromSource(source, sha256, callback)`**: resolves `source` (a direct URL, or
  a `github:...` shorthand via `GitHubReleaseResolver`) → `PackDownloader.download` → `PackExtractor.extract`
  into a fresh `.pack-staging/<nanoTime>/` directory — all on
  `Bukkit.getScheduler().runTaskAsynchronously` — then hops to the main thread via
  `Bukkit.getScheduler().runTask` for the actual `install(File)` call (since that touches
  `ModuleManager`/Bukkit state) and staging-directory cleanup, delivering the result to `callback` on
  the main thread.
- **`PackManager.uninstall(packId)`**: `PackInstaller.uninstall` (delete owned files + now-empty
  directories, revert shared diffs, clear `PackFileIndex` ownership) → `dataStore.deletePackRecord` →
  targeted reload. The affected module set for uninstall is recovered from the record's
  `fileManifest` (no manifest is persisted — see `PackManager.contentFoldersOwnedBy`, which finds the
  path segment matching the pack id and takes everything before it as the owning content-folder
  entry).
- **`PackManager.rollback(packId, timestampOrNull)`**: restores the most recent (or a specific)
  `PackBackupManager` snapshot over the live shared-config files and reloads the pack's modules. Used
  both for an explicit admin rollback and automatically by `PackInstaller` on a mid-install failure.
- **Every write path is synchronous on the calling thread** (including a blocking
  `CompletableFuture.join()` on DB calls) except the download/extract portion of
  `installFromSource` — matching `/valmora reload`'s own fully synchronous nature. This is
  admin-command-triggered and infrequent; a module reload touches Bukkit API and so cannot happen from
  an async continuation (CLAUDE.md §7.4).

---

## 9. Reference Integrity — Extension Point, Not Built-In Checks

`PackValidator.validateReferences` runs every `PackReferenceChecker` registered with a
`PackReferenceCheckerRegistry` and folds their findings in as warnings. This mirrors
`ScriptModule`'s `VariableProvider`/`EventFactory` registry pattern (CLAUDE.md §10.4) rather than
`PackValidator` special-casing item/quest/recipe reference checks itself. **No checker is registered
today** — wiring one in per content type (does a pack's quest reference an item id that actually
exists, either in the pack's own namespace or in base content?) is deliberate follow-up work, matching
today's status quo where only `ModifierValidator` and `MachineModule`'s own validator have any
cross-reference checking at all. `PackScopedIdResolver` (§2.1) is the utility a future checker (or a
content type's own runtime lookup code) would use to resolve a pack-local bare id.

---

## 10. Known Gaps / Deliberately Deferred

- **No hosted pack index.** `/valmora pack install <bare-id>` (resolving against a configured
  `config.yml: pack.index-url` JSON index) is not built — `index-url` exists as an unused config key
  reserved for this. Every install today needs a full URL, `github:owner/repo@tag`, or a local path.
- **No export/bundler command.** `/valmora pack export <folder> <output.zip>` (scaffold a `pack.yml`
  from a folder of loose content) doesn't exist yet.
- **No in-place upgrade diffing.** Reinstalling an already-installed pack id is uninstall-then-reinstall,
  not a smarter diff of what actually changed between versions.
- **Reference-integrity checkers**: extension point exists, nothing registered (§9).
- **`PackScopedIdResolver` isn't wired into any content type's own cross-reference resolution** (quest
  → item, recipe → item, modifier → item, ...) — a pack author must use fully-qualified
  `<pack_id>:<id>` ids everywhere outside the pack's own manifest-driven namespacing today, even
  within their own pack's files, unless a future content-type integration opts in.
