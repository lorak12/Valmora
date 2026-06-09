# Valmora

A modular RPG engine for **Paper 1.21.11**. Valmora is built as a collection of hot-reloadable
modules (items, mobs, skills, abilities, GUIs, recipes, quests, economy, scripting, an RPG calendar,
and more) wired together through a shared API.

## Requirements

- **Server:** Paper 1.21.11 (not Spigot / CraftBukkit)
- **Java:** 21
- **Plugin dependency:** [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/)
  must be installed on the server (declared as a hard `depend` — used for NPC dialogue interception).

## Installation

1. Build the plugin (see below) or download a release jar.
2. Drop `Valmora-<version>-all.jar` and the PacketEvents plugin into your server's `plugins/` folder.
3. Start the server once to generate `plugins/Valmora/config.yml`, then configure as needed.

By default Valmora uses an embedded **SQLite** database (`plugins/Valmora/database.db`) requiring zero
setup. For multi-server networks, set `database.type: mysql` in `config.yml` and fill in the MySQL block.

## Building

```bash
./gradlew build        # Compile, test, and produce the shaded jar in build/libs/
./gradlew testUnit     # Fast unit + config-validation tests (no server needed)
./gradlew runServer    # Launch a Paper 1.21.11 dev server with the plugin loaded
```

The build output is a fat jar (`build/libs/Valmora-<version>-all.jar`). Gson and HikariCP are
relocated under `org.nakii.valmora.lib.*` to avoid classpath collisions with the server and other
plugins.

## Hot reload

While the server is running, `/valmora reload` (requires `valmora.admin`) disables and re-enables all
modules in order.

## Permissions

- `valmora.admin` — administrative commands (`/item`, `/mob`, `/eco`, `/zone`, `/npc`, `/potion`,
  `/valmora`, and the mutating subcommands of `/stat`).
- `valmora.admin.gui` — the `/gui` debugging command.

Player-facing menu commands (`/profile`, `/skill`, `/warp`, `/quest`, `/collections`, `/accessories`,
`/effects`, `/stat list`) are available to everyone by default.

## Documentation

Developer and design documentation lives in [`docs/`](docs/), with `CLAUDE.md` and
`docs/VALMORA_DOCUMENTATION.md` as the primary references.

## License

Valmora is licensed under the **GNU Affero General Public License v3.0** (AGPL-3.0) — see
[`LICENSE`](LICENSE). In short: you may use, study, modify, and share it, but any distributed or
network-hosted derivative must also be released under the AGPL with its source available.

The project is also available under a separate **commercial license**. If you want to use Valmora
in a way the AGPL does not permit (for example, a closed-source or paid distribution), contact the
copyright holder to arrange commercial terms.

Copyright © 2026 nakii. All rights reserved.
