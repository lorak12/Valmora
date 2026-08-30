[*] accessory - removed as a module; merged into backpack.md (item-type + GUI STORAGE component, no dedicated module)
[] alchemy - large changes to DOT for non-player entities and potion effects not cleared on reload
[*] backpack - covers both backpack and accessory (no dedicated module for either; see docs/modules/design/backpack.md)
[] calendar
[] collection
[] combat
[] core
[] economy
[] enchant - now a YAML-driven engine (variables/combat/triggers/state/stats) alongside the original logic: Java hook, not just Java; see docs/modules/design/enchant.md
[] fishing
[] gui
[] hud
[] item
[] mob
[] modifier - generic modifier framework (docs/Valmora_Modifier_Framework_Design.docx); replaced the reforge module (see below)
[] notify
[] npc
[*] pipeline (cross-cutting, docs/modules/design/pipeline.md — no user doc, admin-facing surface is YAML `*_pipeline.yml` files + `/valmora pipeline list`, documented inline)
[] pet
[] profile
[] progression
[] quest
[*] quiver - removed, no replacement shipped; docs deleted (see docs/modules/design/backpack.md §6 for a note)
[*] rarity - data-driven rarity metadata (rarities.yml); documented in docs/modules/design/modifier.md rather than its own file, since it exists to serve the modifier framework
[] recipe
[*] reforge - removed; migrated onto the generic modifier framework as shipped content (modifiers/groups/reforges.yml, modifiers/definitions/reforges.yml) — see docs/modules/design/modifier.md and docs/MODIFIER_FRAMEWORK_BACKLOG.md
[] resource
[] script
[] skill
[*] slayer - removed as a module; rebuilt as quest packages + GUI (see docs/modules/design/slayer.md)
[] stat
[] time
[] ui
[] warp
[] zone