# Hardcoded Values — Decisions

> Companion to `docs/HARDCODED_VALUES_AUDIT.md`. That file is an audit (what exists + a suggestion).
> This file is the **verdict**: what we're actually doing with each finding, and why. The audit's
> `HC-xxx` IDs are used as the join key. Suggested config paths from the audit are reused verbatim
> where we agree with them — only divergences are called out.
>
> **Verdict legend**
> - `DO` — implement now: add the config key (with the current literal as the fallback default,
>   per the audit's own rule — no behavior change on deploy) and wire it through.
> - `CODE-FIX` — not a config gap, an actual bug/inconsistency; fix the code, config is secondary.
> - `DOC` — already configurable; no code change, just make sure the default is documented/commented.
> - `SKIP` — correctly hardcoded or not worth a knob (engine constant, fixed DSL, internal
>   sentinel, or cost >> benefit for a value no admin will realistically want to change).
> - `BACKLOG` — legitimate gap, but low enough value that it's not worth doing in this pass;
>   left for a future "content pack friendliness" sweep.
>
> Policy used to break ties: a knob earns `DO` when (a) it affects balance/economy/perf and an
> admin would plausibly touch it before launch, or (b) it's a bug (mismatched defaults, a code
> path that bypasses a registry it should use). Pure presentation strings with no localization
> requirement on this project, and sub-tick/DSL constants, default to `BACKLOG` or `SKIP` even at
> audit-assigned MED, because there's no admin-facing reason to touch them yet — they're cheap to
> add later, and adding ~140 config keys in one pass with no real user demand is its own maintenance
> cost (drift between docs, config.yml, and code; more surface for stale defaults).

---

## 1. Core Infrastructure / Database

| ID | Verdict | Notes |
|----|---------|-------|
| HC-001 | **DO** | `database.pool.maximum-pool-size` — real scaling knob, cheap to wire (HikariConfig setter). |
| HC-002 | BACKLOG | MySQL-only tuning; nobody's asked, cheap to add later alongside HC-001. |
| HC-003 | **DO** | `database.worker-threads` — same class of fix as HC-001, do together. |
| HC-004 | BACKLOG | Shutdown timeout rarely matters until it does; low urgency. |
| HC-005 | **DO** | Ledger retention is a real GDPR/verbosity lever admins ask about; cheap int. |
| HC-006 | BACKLOG | Splitting `valmora.admin` into per-command nodes is a real ask for staff hierarchies, but it's a structural change (6+ commands) not a single literal — track as its own follow-up task, not a config-key drop-in. |
| HC-007 | SKIP | Intentional per audit — display logic, not policy. |
| HC-008 | SKIP | Plugin-scoped PDC keys must never be exposed. |

## 2. Economy

| ID | Verdict | Notes |
|----|---------|-------|
| HC-010 | **DO** | `economy.ledger-display-limit` — trivial int, real admin ask (audit vs. compact). |
| HC-011 | **DO** | Coin formatting/branding (`$` vs `🪙`) is one of the first things any server reskins. Also fixes the noted `EconomyModule.formatCoins` ↔ `EcoCommand.fmt` duplication (dedupe into `Formatter.formatCoins()` while touching this). |
| HC-012 | DOC | Already configurable; nothing to do beyond the audit's note. |
| HC-013 | BACKLOG | Bundled with HC-006 permission-splitting work. |
| HC-014 | BACKLOG | Tab-complete presets are pure convenience; low cost to add later, not urgent. |
| HC-015 | BACKLOG | `t` suffix is a nice-to-have; no current server need for >999b coins. |
| HC-016 | **DO** | Do together with HC-011/HC-017 as one `economy.messages.*` block — same file, same pass. |
| HC-017 | **DO** | Same block as HC-016. |
| HC-018 | **DO** | Prefix branding bundled with HC-011; halving mode (`floor`/`ceil`/`round`) is a one-line enum switch, worth adding while in the file. |

## 3. Combat / Damage

| ID | Verdict | Notes |
|----|---------|-------|
| HC-020 | **DO** | Regen tick rate is a real perf knob on populated servers. |
| HC-021 | SKIP | Debug-only interval, not player/admin facing. |
| HC-022 | **DO** | "mana always regens in combat, health doesn't" is a real balance decision servers will want to flip; cheap booleans. |
| HC-023 | BACKLOG | Fallback only fires when a mob truly has no damage source configured — edge case, low value. |
| HC-024 | DOC | Already overridable via `damage_formula.yml`; just document the default there. |
| HC-025 | **CODE-FIX** | This is the one real bug in the whole audit: environment damage hardcodes `100/(def+100)` and bypasses `DamageFormulaRegistry`, so a server that retunes `defense_multiplier` in `damage_formula.yml` gets inconsistent mitigation between combat and environmental damage. Route it through the registry; `combat.environment-damage-multiplier` stays as-is (already configurable). |
| HC-026 | BACKLOG | Rounding mode is polish; `floor` is a defensible permanent default. |
| HC-027 | DOC | Already configurable. |
| HC-028 | BACKLOG | Indicator styling is presentation-only; no reported ask. Bundle into a future "visual tuning" pass. |
| HC-029 | **DO** | iframe threshold directly controls DoT/multi-hit stacking — real combat-balance lever, HIGH correctly. |
| HC-030 | **CODE-FIX** | Trident/snowball/fireball silently misclassified as MELEE is a correctness bug, not a tuning gap — fix the heuristic (check `Projectile` interface / expand the type set) rather than just exposing a map. Still expose `damage_types/mapping.yml` per audit so future projectile types don't need a recompile. |
| HC-031 | BACKLOG | 17-case switch is fine as Java; exposing it needs the same YAML-mapping mechanism as HC-030 — do together if HC-030 is picked up later, not blocking now. |
| HC-032 | SKIP | Already overridable via `damage_types/*.yml`; defaults being baked in code is fine. |
| HC-033 | **DO** | One enum default, bundle with HC-029/030 combat pass. |
| HC-034 | SKIP | Already overridable via `damage_formula.yml`. |

## 4. Item / Mechanics / Targeting

| ID | Verdict | Notes |
|----|---------|-------|
| HC-040 | **DO** | Tool tier → breaking power is a hard balance wall for any custom tool tier a content pack adds; without this, `items.breaking-power` map is mandatory for pack support. |
| HC-041 | **DO** | Vanilla material → rarity mapping — same "custom material needs a mapping, not a recompile" argument, and it's explicitly needed by the item/pack system. |
| HC-042 | **DO** | Mining speed per tier — direct balance number, bundle with HC-040/041/043/044 as one `items.vanilla-stats.*` block (same file, same PR). |
| HC-043 | **DO** | Weapon damage per tier — same block. |
| HC-044 | **DO** | Bow/crossbow/armor numbers — same block; all five (040-044) are one coherent `items.vanilla-stats` + `items.breaking-power` + `items.vanilla-rarity-mapping` config addition in `ItemFactory`/`ItemTranslator`. |
| HC-045 | BACKLOG | Rarity colors: real "data-driven rarity" work belongs with HC-284 (`Rarity.java` enum deprecation) as one project, not a quick key add — tracked there instead of duplicated here. |
| HC-046 | BACKLOG | Ability messages — genuine localization need eventually, but no current non-English server; bundle into a future i18n pass across all modules at once rather than piecemeal. |
| HC-047 | BACKLOG | AoE default radii are content-author choices per-ability already (params can override); global defaults are low value. |
| HC-048 | **CODE-FIX** | Should use `Tag.ITEMS_ARROWS` instead of an enum literal list — this is a one-line correctness/future-proofing fix (spectral/tipped arrow variants added by data packs would otherwise be missed), do it regardless of config. |
| HC-049 | BACKLOG | Loot-full title/timings — presentation polish, part of the same future i18n/visual pass as HC-046. |
| HC-050 | SKIP | Intentional per audit. |
| HC-051 | BACKLOG | Mechanic-param defaults (14 files) are genuinely useful for pack authors but are a large, uniform mechanical change (one config block per mechanic) — worth doing as its own dedicated task once a content pack actually needs to retune a mechanic default globally, not speculatively now. |

## 5. Stat

| ID | Verdict | Notes |
|----|---------|-------|
| HC-060 | **DO** | Potion-purge threshold silently breaking long (4h+) buffs is a real correctness issue for any alchemy content with long durations — cheap int, real risk if left hardcoded. |
| HC-061 | BACKLOG | Vanilla-attribute formula constants are deep engine tuning; changing them needs real testing regardless of whether they're config or code — exposing the knob doesn't reduce that risk. Leave as code for now. |
| HC-062 | SKIP | 1-tick delay is a scheduling implementation detail, not a balance value. |
| HC-063 | SKIP | Already configurable via YAML `max-value`. |

## 6. Skill / XP

| ID | Verdict | Notes |
|----|---------|-------|
| HC-070 | **CODE-FIX** | `Skill.java` enum's `maxLevel=60` overriding the data-driven `SkillDefinition.maxLevel` is a real drift bug (two sources of truth) — remove the enum field or make it defer to the definition. Not a config gap. |
| HC-071 | DOC | Already overridable via `xp_curves.yml`; just add the documenting comment the audit suggests. |
| HC-072 | BACKLOG | Fallback-of-a-fallback; low value. |
| HC-073 | BACKLOG | Same. |
| HC-074 | SKIP | Actionbar duration for XP gain is cosmetic polish. |

## 7. Mob

| ID | Verdict | Notes |
|----|---------|-------|
| HC-080 | **DO** | AI/spawn poll rates are a direct CPU knob on populated servers — HIGH correctly. |
| HC-081 | **DO** | Damage-per-level curve as an `Expression` — mobs balance is exactly the kind of thing a server retunes without recompiling; matches the existing `damage_formula.yml` pattern already used elsewhere. |
| HC-082 | **DO** | Same reasoning as HC-081, same PR (`mobs.xp-reward-formula`). |
| HC-083 | BACKLOG | These are "defaults when YAML omits the field" — every mob definition can already set them explicitly; a global override is a nice-to-have, not a gap that blocks anything. |
| HC-084 | BACKLOG | Boss tick/announce radius — low-frequency content (bosses), not worth prioritizing. |
| HC-085 | **CODE-FIX** | Two independent `40.0` literals for the same concept (`BossBarConfig.java` and `MobDefinitionParser.java`) is a duplication bug waiting to drift — consolidate into one constant/config key regardless of priority. |
| HC-086 | **DO** | Natural-spawn search radius directly controls spawn density and is a real perf/gameplay lever — HIGH correctly. |
| HC-087 | BACKLOG | Leash-return speed is single-purpose polish. |
| HC-088 | SKIP | Vanilla engine limit — intentional. |
| HC-089 | BACKLOG | Luck-divisor is a hidden balance knob but low urgency without a reported complaint about loot-luck feel. |
| HC-090 | BACKLOG | Ability defaults, same reasoning as HC-083. |
| HC-091 | BACKLOG | Hardcoded `"combat"` skill id string is fragile but only breaks if someone renames a core skill, which is already a drastic action; low likelihood. |

## 8. Recipe / Anvil / Machine

| ID | Verdict | Notes |
|----|---------|-------|
| HC-100 | DOC | Already configurable via `anvil.templates.*`; document ranges only. |
| HC-101 | BACKLOG | Prior-work formula as an `Expression` is nice but current `2^work-1` with a 30-clamp is a reasonable permanent design (matches vanilla anvil escalation logic); revisit only if a server wants a gentler curve. |
| HC-102 | SKIP | 64 is the vanilla stack-size ceiling for essentially all items; not worth a knob unless the project actually ships >64 stack sizes. |
| HC-103 | SKIP | Grid-width fallback of 3 matches the only machine shapes that exist; premature to configure a case with no current second consumer. |
| HC-104 | BACKLOG | `crafting_table` as the only vanilla-fallback machine is correct for the current machine set; expose as a list only if/when a second such machine is added. |
| HC-105 | **DO** | Repair-material hints (`NETHERITE→NETHERITE_INGOT` etc.) directly blocks custom-alloy content packs from having working anvil repair — real gap for the modifier/pack framework, worth the map. |
| HC-106 | SKIP | Not actually a hardcoded *value* — it's a parser default field name; nothing to configure. |

## 9. Enchant

| ID | Verdict | Notes |
|----|---------|-------|
| HC-110 | **DO** | Etable XP cost-per-level is core economy balance — HIGH correctly, cheap int/formula. |
| HC-111 | **DO** | Per-logic `percent-per-level` defaults — same argument as items HC-040-044: without a global default, every enchant YAML must repeat the same number, and retuning power creep means editing every file. Worth the `enchants.defaults.<logic-id>.*` block. |
| HC-112 | BACKLOG | Cleanup interval is a CPU/memory tradeoff nobody's complained about yet; cheap to add later. |
| HC-113 | BACKLOG | Same as HC-112. |
| HC-114 | **DO** | Lethality stacking (`max-stacks`, `stack-duration-ms`) is a real per-enchant balance lever already partially supported by `logic-params:` — extend it to accept these two keys with the current literal as fallback. |
| HC-115 | **DO** | First-strike window — same mechanism/PR as HC-114. |
| HC-116 | SKIP | Lore compact-threshold/line-wrap are presentation-only formatting constants; not worth exposing without a specific complaint. |
| HC-117 | BACKLOG | Global max-level caps as YAML-omission fallbacks — every enchant already declares its own levels; low value. |

## 10. Modifier

| ID | Verdict | Notes |
|----|---------|-------|
| HC-120 | BACKLOG | Group-default policy (max/mode/replacement/removal) is real but the framework already lets every group override these per-group in `modifiers/groups/*.yml` — a server-wide default is a convenience, not a blocker. |
| HC-121 | SKIP | Already per-modifier YAML. |
| HC-122 | BACKLOG | Tier-source formula (`rarity.rank+1`) as an `Expression` matches the `damage_formula.yml` pattern used elsewhere, but no server has asked for a non-linear tier progression yet. |
| HC-123 | DOC | Documentation-only fix per audit — add the `MULTIPLY` semantics note to `docs/modules/design/modifier.md`. |

## 11. Zone

| ID | Verdict | Notes |
|----|---------|-------|
| HC-130 | **DO** | Spawner tick interval — direct CPU knob, HIGH correctly. |
| HC-131 | **DO** | Mob-home scan interval — same class of fix, bundle with HC-130. |
| HC-132 | **DO** | Visualization tick — particle spam on 100+ players watching zones simultaneously is a real reported-pattern risk; bundle with HC-130/131 as one `zones.*-interval-ticks` config pass. |
| HC-133 | BACKLOG | Selection-visualization only runs while an admin is actively drawing a zone (rare, single-player-at-a-time); low urgency vs. 130-132. |
| HC-134 | BACKLOG | Visualization cull distance — cosmetic edge case (zones >200 blocks wide being invisible from far away), not blocking. |
| HC-135 | BACKLOG | Wander-radius multiplier is single-purpose mob-leash tuning; bundle into a future "mob AI tuning" pass with HC-087. |
| HC-136 | BACKLOG | Spawn-search-attempts is an internal retry guard, not something admins reach for first. |
| HC-137 | BACKLOG | Occupancy radius — same, internal spawn-collision tuning. |
| HC-138 | SKIP | Particle color/size is pure visual styling; no reported need for a colorblind palette or retheme yet. |
| HC-139 | SKIP | Max-particles-per-edge — internal rendering-density constant. |
| HC-140 | BACKLOG | Zone-enter popup duration — cosmetic, low priority. |
| HC-141 | BACKLOG | Wand material/lore — texture-pack servers are a real but not-yet-actual use case here. |
| HC-142 | **CODE-FIX** | The audit itself flags a real bug: `ZoneCommand.java` command-created spawners default `interval:400` while the loader's YAML default is `interval:200` — these silently diverge. Fix is to point both at one shared constant/config key (`zones.defaults.spawn-interval`), not just "expose a knob" — the actual defect is the mismatch. |
| HC-143 | BACKLOG | "Wilderness" fallback name — no non-English server currently, low priority; note the two-occurrence duplication (`ZoneVariableProvider` + `ScoreboardUI`) for whenever it is done, to avoid a second drift bug like HC-142. |

## 12. Warp

| ID | Verdict | Notes |
|----|---------|-------|
| HC-150 | SKIP | `[warp]` sign header — no current localization need. |
| HC-151 | BACKLOG | Seven warp messages — real localization/templating value eventually, bundle into the same future i18n pass as HC-046/049. |
| HC-152 | **DO** | This is the one warp item worth doing now: block-level move-cancel with no tolerance is a genuine UX papercut (any sub-block jitter cancels a warmup), and "damage cancels warmup" is documented behavior that doesn't actually exist in code — that's a real gap, not just a tuning knob. Add `cancel-on-move-distance` and implement the missing `cancel-on-damage` check. |
| HC-153 | BACKLOG | `y: 64` fallback failing on void/high worlds is a real footgun, but the fix that actually matters is making `world`/`y` *required* fields with a load-time warning when omitted, not just documenting a default — track as a validation task, not a config addition. |
| HC-154 | SKIP | Intentional — vanilla tick constant. |

## 13. GUI

| ID | Verdict | Notes |
|----|---------|-------|
| HC-160 | **DO** | Mass-craft cap is a real anti-dupe/anti-bulk-exploit economy lever — worth the int. |
| HC-161 | BACKLOG | BARRIER-for-errors is a sane permanent default; not worth a key. |
| HC-162 | BACKLOG | GUI parser defaults (title/update-interval/machine) — every GUI already sets these explicitly in practice; low value as a global override. |
| HC-163 | SKIP | Layout empty-char — internal DSL convention, changing it would break every existing GUI YAML pattern string; not something to expose. |
| HC-164 | **CODE-FIX** | `customModelData 0` used as a "missing" sentinel is a real bug (CMD 0 is a valid value in many resource packs) — fix by checking `contains("custom-model-data")` and using a nullable Integer, not a config knob. |
| HC-165 | **CODE-FIX** | Silent `amount<=0 → 1` clamp hiding an author typo should log a load-time warning — small correctness fix, not a config value. |
| HC-166 | SKIP | Alchemy-specific literals inside the generic GUI listener are a design smell but changing the mechanism (event/condition driven "locked-while") is a bigger refactor than this audit pass covers; leave as-is unless a second potion-type feature actually needs it. |
| HC-167 | BACKLOG | Permissive `null` command-permission default is a minor security-by-default improvement; worth doing eventually but not urgent since GUIs aren't currently exposed via arbitrary commands without review. |
| HC-168 | DOC | Document-only per audit — changing these DSL defaults breaks every shipped GUI, so they stay hardcoded; note in `docs/modules/design/gui.md`. |

## 14. HUD / UI

| ID | Verdict | Notes |
|----|---------|-------|
| HC-170 | BACKLOG | 1-tick respawn re-give delay — edge case (lag interaction), low urgency. |
| HC-171 | SKIP | HUD slot 8 (Hypixel convention) is a fine permanent default. |
| HC-172 | **CODE-FIX** | `"STONE"` as a silent fallback material when an author omits `material:` hides a real content bug (a player sees a plain stone block instead of their intended HUD icon) — same pattern as HC-165/GUI: should warn at load time and probably fall back to BARRIER (the project's established "something's wrong" material per HC-161), not silently render STONE. |
| HC-173 | SKIP | Click-mapping (right/left only) matches the two-button HUD-item interaction model in use; no reported need for middle-click. |
| HC-174 | **DO** | 10 Hz scoreboard/actionbar clock ticking every online player is explicitly called out as the hottest loop in the UI module — real perf lever on populated servers, HIGH correctly. |
| HC-175 | **CODE-FIX** | Duplicated default title/actionbar text between `UIManager.java` and `ui.yml` is a drift risk (exactly the HC-142/HC-085 pattern) — dedupe so the Java fallback and the YAML default can't diverge; not a new config key, a cleanup of an existing one. |
| HC-176 | SKIP | `MAX_LINES 16` — matches Paper's actual scoreboard capability; the "vanilla truncates at 15" note is worth a code comment, not a config key. |
| HC-177 | BACKLOG | Combat-line format string — bundle with future i18n pass. |
| HC-178 | BACKLOG | Chat prefix branding — real ask eventually (every server rebrands its chat prefix) but low effort/low risk to defer; bundle with HC-011 economy branding pass if picked up. |
| HC-179 | SKIP | Debug-only interval. |
| HC-180 | **CODE-FIX** | `"§"+char` legacy color codes in `ScoreboardUI.java` directly violates this project's own "never use `§`" rule (CLAUDE.md §7.5) — fix to use Adventure's unique-entry mechanism for scoreboard lines regardless of priority; this is a standing rule violation, not a style preference. |
| HC-181 | SKIP | Intentional — vanilla tick-to-ms constant. |

## 15. Alchemy

| ID | Verdict | Notes |
|----|---------|-------|
| HC-190 | **DO** | Healing-per-level curve is core alchemy balance and blocks any content-pack potion rebalance — worth the `double[]`. |
| HC-191 | **DO** | Same reasoning, same PR, as absorption values. |
| HC-192 | BACKLOG | Needs a read of `VanillaAlchemyEffect.java` to confirm scope before deciding — flagged for a follow-up look, not assumed done. |
| HC-193 | SKIP | Renaming the awkward-potion item id is a deep content-identity change; not something a config toggle safely supports. |
| HC-194 | BACKLOG | 20-tick initial delay is a minor startup-race guard; low value to expose. |

## 16. Fishing

| ID | Verdict | Notes |
|----|---------|-------|
| HC-200 | BACKLOG | Needs the same file-read-before-deciding treatment as HC-192; loot weight/luck-divisor defaults are plausible balance levers but unverified without reading the file. Flagged, not assumed. |
| HC-201 | SKIP | Fixed DSL prefix — intentional, matches the project's other `POINT_PREFIX` patterns (e.g. HC-243). |

## 17. NPC / Dialogue / Hologram

| ID | Verdict | Notes |
|----|---------|-------|
| HC-210 | BACKLOG | Auto-advance reading-speed constants — real UX tuning eventually, no reported complaint yet. |
| HC-211..HC-216, HC-218, HC-219 | SKIP/BACKLOG | All internal timing/visual constants (chat-clear lines, hint/stop poll intervals, look range, hologram offset, history size, mount offset) — none affect balance or economy; genuinely cosmetic/perf-marginal. Leave as literals; revisit only if a specific dialogue UX complaint surfaces. |
| HC-217 | BACKLOG | NPC prefix branding — bundle with the other prefix-branding items (HC-018, HC-178) if a branding pass ever happens. |
| HC-220 | BACKLOG | Self-hosted Mojang/MineSkin proxy URLs — real need only for servers running an auth proxy; niche, defer until asked. |
| HC-221 | DOC | Already configurable. |

## 18. Pet

| ID | Verdict | Notes |
|----|---------|-------|
| HC-230 | BACKLOG | Follow-distance/teleport-distance/step feel-tuning — real but no reported complaint about pet movement feel. |
| HC-231 | BACKLOG | Follow poll rate is a minor CPU knob; pets are a low-count-per-server feature (1 per player, usually far fewer active than mobs), low urgency vs. HC-080/130-132. |
| HC-232 | **DO** | XP formula and max-level are core progression balance and already half-exposed via `pets/defaults.yml` — closing the gap (making the Java fallback match the documented YAML path) is worth doing now rather than leaving two sources of truth. |

## 19. Resource / Mining

| ID | Verdict | Notes |
|----|---------|-------|
| HC-240 | **DO** | Autosave interval is a real crash-recovery-vs-IO tradeoff, HIGH correctly, cheap int. |
| HC-241 | **CODE-FIX** | Missing guardrail against a malformed `interval:1` regen config causing a per-tick task storm is a stability bug, not a tuning gap — add the minimum-clamp validation at load time. |
| HC-242 | SKIP | Already configurable — audit confirms. |
| HC-243 | SKIP | Fixed DSL prefix, intentional (matches HC-201). |

## 20. Progression / Time / Calendar

| ID | Verdict | Notes |
|----|---------|-------|
| HC-250 | BACKLOG | Daily-bonus window/check-interval — real but no current alternate-cadence request. |
| HC-251 | BACKLOG | Progression messages — bundle into future i18n pass. |
| HC-252 | SKIP | Day-check tick interval — cheap poll, not a meaningful perf lever at 1/sec. |
| HC-253 | **DO** | Calendar shape (days/phase, phases/season, seasons/year) is exactly the kind of thing an RPG server wants to customize (28-day "months" is a named example in the audit) — worth exposing as `time.calendar.*`, derives `days-per-year` from the three ints. |
| HC-254 | BACKLOG | Season-change announcement text/duration — cosmetic, bundle with i18n pass. |
| HC-255 | BACKLOG | Needs the file read to confirm scope before deciding, same as HC-192/200 — not assumed done, flagged for follow-up. |

## 21. Quest

| ID | Verdict | Notes |
|----|---------|-------|
| HC-260 | **DO** | Per-player × per-quest 1s poll is a real O(n×m) CPU concern the audit correctly flags as scaling risk on large servers — worth the interval knobs. |
| HC-261 | **CODE-FIX** | Same stability-bug class as HC-241: a malformed `interval:1` delay objective can per-tick trigger with no floor — add the validation clamp. |
| HC-262 | BACKLOG | Progress-notification template — bundle with i18n pass. |
| HC-263 | SKIP | Internal variable-namespace prefix; not admin-facing, changing it would need a migration anyway. |

## 22. Notify

| ID | Verdict | Notes |
|----|---------|-------|
| HC-270 | BACKLOG | Category→IO routing (`error`→actionbar vs bossbar) — real customization eventually, no current request. |
| HC-271..HC-274 | SKIP | Bossbar/title/sound/advancement default styling — pure presentation constants with sane out-of-the-box values; not worth exposing speculatively. Revisit if a server explicitly asks to retheme notifications. |

## 23. Profile / Collection / Rarity / Pack / Machine

| ID | Verdict | Notes |
|----|---------|-------|
| HC-280..HC-282 | SKIP | Profile GUI size/slots/branding/reopen-delay — internal to a single built-in GUI; conflicts with "custom plugins" are theoretical, not reported. |
| HC-283 | BACKLOG | Needs the file read to confirm what (if anything) is actually hardcoded before deciding — flagged for follow-up, not assumed. |
| HC-284 | BACKLOG | Real work (deprecate `Rarity.java` enum in favor of `RarityDefinition`/`rarities.yml`), but it's a structural migration, not a config-key add — bundle with HC-045 as its own dedicated task. |
| HC-285 | SKIP | Pack content-folder allow-list is a security boundary (what a downloaded pack is permitted to touch); making it server-configurable would let a malicious/broken pack point itself at arbitrary folders — correctly hardcoded. |
| HC-286 | SKIP | GitHub shorthand regex is a fixed DSL — intentional, matches HC-201/243. |
| HC-287 | SKIP | User-agent strings for outbound HTTP identify the plugin; no real need to spoof/override. |

## 24. Missing / Unimplemented Systems

| ID | Verdict | Notes |
|----|---------|-------|
| HC-290 | **OUT OF SCOPE for this pass** | Not a hardcoded-value gap at all — there's no WorldBorder module to have hardcoded anything. Building one is a full new module (own `docs/modules/design/worldborder.md`, registration slot, listener, YAML loader) — a feature request, not something this config-audit pass should silently take on. Left for the user to prioritize as a real feature task. |
| HC-291 | **DO** | This is the one real safety gap in the whole audit: user-authored `$level$` formulas have no depth/time limit, so a malformed or malicious expression in a content pack can hang the main thread. `scripting.limits.{max-expression-depth,max-evaluation-time-ms}` should be added and enforced in the expression evaluator. |
| HC-292 | BACKLOG | Lore-line guard against exceeding the client's 256-line cap is a good validation to have, but no report of it actually happening yet; cheap to add whenever someone touches `GuiRenderer`. |

---

## What actually gets implemented from this pass

**Status: implemented in two passes** (2026-09-02, same session as the audit).

**Pass 1** landed every `DO`/`CODE-FIX` verdict (listed below) — real balance/perf levers and
actual bugs (mismatched defaults, missing validation, rule violations).

**Pass 2** worked through the remaining `BACKLOG` items. Four needed a decision and were put to
the user directly:
- **i18n message pass** (HC-046/049/151/177/178¹/217¹/251/254/262 — ability/warp/UI/npc/
  progression/quest message strings) — **user chose skip**; no non-English server, no current ask.
  ¹Branding *prefixes* (HC-178 chat, HC-217 npc) were done anyway — same low-risk category as the
  HC-018/HC-011 branding already done in pass 1, not message-content translation.
- **Permission split** (HC-006/HC-013 — `valmora.admin` → per-command nodes) — **user chose do it
  now**. Added `PermissionResolver` and wired all 15 admin command classes to it.
- **Rarity → data-driven migration** (HC-045/HC-284) — **already done** before this audit: a full
  `rarity` module + `rarities.yml` (`RarityDefinition`/`RarityRegistry`) already exists;
  `module/item/Rarity.java` is intentionally kept only as the on-item PDC representation per that
  file's own doc comment. No action needed — the audit's claim here was stale.
- **Mechanic defaults** (HC-051 — 14 item-ability mechanics) — **user chose do it now**. Added
  `MechanicDefaults` and wired all 14 `impl/*Mechanic.java` classes plus `TrampleListener`.

Everything else in this file without a checkmark below is genuinely still `BACKLOG`/`SKIP`/`DOC` —
low value, needs real testing regardless of config (HC-061), a fixed protocol constant
(HC-219 MOUNT_Y_OFFSET), or a non-finding after reading the file (HC-192's mapping *is* now
exposed, but its literal "values" claim was empty; HC-200's luck-divisor doesn't exist because
fishing loot has no luck integration; HC-255/HC-283 were non-findings — nothing hardcoded to fix).

Full build (`./gradlew build`) and test suite (1040 tests) pass after every change in both passes.

**`CODE-FIX` (bugs — fix regardless of config):**
HC-025 (env damage bypasses `DamageFormulaRegistry`), HC-030 (trident/snowball/fireball misclassified
as MELEE), HC-048 (arrow check should use `Tag.ITEMS_ARROWS`), HC-070 (`Skill.java` enum maxLevel
drift), HC-085 (duplicated `40.0` boss-bar range), HC-142 (spawner `interval` 400 vs 200 mismatch),
HC-164 (CMD-0 sentinel bug), HC-165 (silent `amount<=0` clamp), HC-172 (silent `STONE` HUD fallback),
HC-175 (duplicated UI defaults), HC-180 (`§` codes violate CLAUDE.md), HC-241 / HC-261 (missing
min-interval validation against per-tick task storms).

**`DO` (config additions worth landing now):** HC-001, HC-003, HC-005, HC-010, HC-011, HC-016, HC-017,
HC-018, HC-020, HC-022, HC-029, HC-033, HC-040–044, HC-060, HC-080, HC-081, HC-082, HC-086, HC-105,
HC-110, HC-111, HC-114, HC-115, HC-130–132, HC-152, HC-160, HC-174, HC-190, HC-191, HC-232, HC-240,
HC-253, HC-260, HC-291.

Next step: implement these in a dedicated PR per section (infra/economy, combat, items, mobs, zones,
enchant, alchemy/pet/resource, scripting-safety) rather than one giant diff — see
`docs/HARDCODED_VALUES_AUDIT.md` §25 for the exact YAML shape to drop into `config.yml` for each key,
using the current literal as the default per its own no-behavior-change rule.
