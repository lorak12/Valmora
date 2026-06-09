# Changelog

All notable changes to Valmora are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0-beta1]

First public beta. Production-readiness hardening pass.

### Added
- Versioned database schema migration framework (`valmora_schema_version` table) so future
  schema changes apply automatically and in order. Pre-versioning databases are detected and
  upgraded in place.
- Automated database tests (`SQLDataStoreTest`): schema creation, idempotent re-init,
  pre-versioning upgrade, and economy persistence round-trip.
- MockBukkit-based persistence test (`ProfilePersistenceMockTest`): full player/profile
  save+load round-trip including real `ItemStack` inventory serialization. Runs in an isolated
  JVM via the dedicated `testMock` Gradle task (wired into `check`/`build`) to avoid Bukkit
  static-state pollution from the rest of the suite.
- Configurable MySQL SSL via `database.mysql.use-ssl` (default `false`).
- `README.md`, `CHANGELOG.md`, and a GitHub Actions build workflow.
- `LICENSE` (GNU AGPL-3.0); the project is open-sourced under AGPL-3.0 with a separate
  commercial license available from the copyright holder.

### Changed
- `plugin.yml` version is now sourced from the Gradle build version (single source of truth).
- Gson and HikariCP are relocated under `org.nakii.valmora.lib.*` in the shaded jar to avoid
  classpath collisions with the server and other plugins.

### Fixed
- Database errors are now logged at `SEVERE` instead of being swallowed by `printStackTrace()`.
  A failed schema initialization now disables the plugin instead of silently losing data.
- `/item` and `/mob` now require `valmora.admin`, and `/gui` requires `valmora.admin.gui`
  (these previously had no permission gate).
- Removed an unused duplicate XP-threshold table from `Skill`; `SkillRegistry` is the single
  source of truth for the level curve.

[Unreleased]: https://github.com/nakii/valmora/compare/v1.0.0-beta1...HEAD
[1.0.0-beta1]: https://github.com/nakii/valmora/releases/tag/v1.0.0-beta1
