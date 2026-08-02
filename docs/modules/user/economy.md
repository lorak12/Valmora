# Economy Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `economy` | **Command:** `/eco` | **Config:** `config.yml` → `economy:`

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

Valmora has a built-in **coin economy**. Every player has **two balances**:

| Balance | What it is | Notes |
|---|---|---|
| **Purse** | Your spendable wallet | Coins you carry on you. Used to pay for quests, reforging, and ability costs. |
| **Bank** | Your storage | Coins you set aside so they are safe while you adventure. |

Your balances are saved automatically in the background and when you log off or the server stops — you do **not** need to do anything to keep your coins. A short crash window (default up to 60 seconds) can lose coins changed right before an unclean shutdown, but normal logoffs and clean restarts never lose anything.

```
Purse  ←—  earn (kill rewards, quests, items)  ——  spend (quests, forge, abilities)
   │                                            │
   └────── deposit ──► Bank ── withdraw ────────┘
```

> **Two "coins" caveat:** some older Valmora content (e.g. the anvil item-merging cost) still uses a legacy `player.var.coins` profile variable that is **separate** from this economy's purse/bank. If a feature doesn't seem to touch your purse, it may be using that older variable instead.

---

## Player Guide

### Where coins come from (earning)

- **Kill rewards.** Killing custom Valmora mobs pays their `gold-reward` directly into your purse (`mobs/*.yml`). Example defaults: `test_mobs.yml:10,42` (5 / 3 coins), `test_boss.yml:10` (1000 coins), `shardworks_mobs.yml:14,29` (15 / 25 coins).
- **Quests and slayers.** Quest completion events and slayer tier rewards can pay coins via the `economy_add` script event. The bundled slayers grant, e.g., 250–5000 coins per tier (`slayers/zombie.yml:10-66`).
- **Items.** Some items have a `GIVE_COINS` ability that grants coins on use/kill (e.g. the Raider Axe's "earn 20 coins from kills").
- **Server grants.** Admins can add coins with `/eco add` (see [Admin Guide](#admin-guide)).

### Where coins go (spending)

- **Slayer activation.** Starting a slayer tier charges its `cost` from your purse (`SlayerStartEventFactory.java:60-69`).
- **Reforging.** Using the forge to reforge an item charges a coin cost from your purse.
- **Item abilities.** Some items have a `TAKE_COINS` cost (e.g. "costs 100× weapon damage" — the Crown of Greed).

### Using the bank

Open the **Bank of Valmora** GUI (your server decides where — commonly a menu shortcut or a command) to move coins between your purse and bank:

- **Deposit** — left-click the green chest to choose an amount, or right-click to deposit your entire purse.
- **Withdraw** — click the red dropper to choose an amount. You can withdraw **all**, **half**, **20%**, or a specific amount (the sign button opens a text-input dialog, e.g. `2.5k`).
- **Why bother?** Money you carry in your purse is **risky** — when you die, you **lose half your purse** (it is removed, not dropped on the ground). Coins in the bank are safe.

> **Death penalty:** death removes exactly half of whatever is in your purse at the time. There is currently **no config option** to change or disable this. Banked coins are never touched by death.

### Reading your balances

- Your **purse** is shown on the default scoreboard (`ui.yml:23` — `Purse: 🪙 …`).
- The bank GUI shows purse and bank values directly, updating every few seconds.
- Custom GUIs and NPC dialogues can display `$economy.purse$`, `$economy.bank$`, or `$economy.total$` anywhere script variables are supported.

---

## Admin Guide

### `/eco` command

`/eco <get|set|add|remove> <player> [purse|bank] [amount]`

| Subcommand | Purpose | Examples |
|---|---|---|
| `get` | Show a player's balance(s). Third argument `purse` or `bank` shows one; omitted shows both. | `/eco get Steve`, `/eco get Steve purse`, `/eco get Steve bank` |
| `set` | Overwrite a balance to an exact value. Amount must be `≥ 0`. | `/eco set Steve purse 5000`, `/eco set Steve bank 0` |
| `add` | Add coins to a balance. Amount must be `> 0`. | `/eco add Steve purse 1000`, `/eco add Steve bank 2500` |
| `remove` | Subtract coins from a balance. Amount must be `> 0`. | `/eco remove Steve purse 500` |
| `purse` / `bank` | Which balance the `set`/`add`/`remove` subcommands operate on (required for those). | — |

**Amount formats** are flexible — you can type plain numbers or abbreviations, and even do arithmetic:

- `500`, `2.5k` (= 2,500), `1m` (= 1,000,000), `1b`
- Expressions like `1k+500`, `3k-1`, `(1k+500)*2` are also accepted.

**Limitations:**
- The target player must be **online** (`/eco` rejects offline players).
- Only the **purse** and **bank** balances are editable — the legacy `player.var.coins` variable is a separate profile variable and is **not** touched by `/eco`.

### Permissions

| Permission | Grants |
|---|---|
| `valmora.admin` | Use `/eco`. Also required by the command in `plugin.yml` (`plugin.yml:39-42`). |

This is the same permission used by `/valmora`, `/item`, `/mob`, `/zone`, etc. There is no player-facing economy command — players interact with the economy through the bank GUI, quests, and item abilities.

### Scripting reference (for content creators)

Admins/servers can use these in any scriptable context (quests, GUI actions, NPC dialogue, mob events):

| Script event | Effect | Example |
|---|---|---|
| `economy_add <amount>` | Adds coins to the player's purse | `economy_add 500` |
| `economy_remove <amount>` | Removes coins from the player's purse | `economy_remove 250` |
| `economy_deposit <amount\|all\|half>` | Moves purse → bank (with chat feedback) | `economy_deposit half` |
| `economy_withdraw <amount\|all\|half\|X%>` | Moves bank → purse (with chat feedback) | `economy_withdraw 20%` |
| `economy_deposit_all` | Moves the entire purse → bank | `economy_deposit_all` |

**Script variables** available for display and conditions:

| Variable | Value |
|---|---|
| `$economy.purse$` | Raw purse amount (number) |
| `$economy.purse.formatted$` | Formatted purse, e.g. `🪙 1.000.000` |
| `$economy.bank$` | Raw bank amount (number) |
| `$economy.total$` | Purse + bank (number) |

### Tuning autosave

See [Configuration Reference](#configuration-reference) for `economy.autosave-interval-seconds`. In short:

- **Lower** it (e.g. `15`) if you want less coin loss on a sudden crash — at the cost of slightly more frequent database writes.
- **Higher** it (e.g. `300`) if you want fewer writes on a busy server — you accept a wider crash-loss window.
- **Balance is always available in memory**, so the interval never affects gameplay; it only bounds how much could be lost on an unclean shutdown. Logouts and clean restarts always save immediately regardless.

### Reloading

Config changes to the `economy:` section take effect on `/valmora reload` (requires `valmora.admin`). A reload flushes all balances to the database first, so no coins are lost.

---

## Configuration Reference

The economy module is configured in `config.yml` under the `economy:` key. There is **no separate economy content folder** — balances are stored in the database.

| Key | Type | Default | Description |
|---|---|---|---|
| `economy.autosave-interval-seconds` | number | `60` | How often (in seconds) dirty balances are flushed to the database in one batched save. Balances are always safe to use in memory; this only controls how much progress could be lost if the server crashes uncleanly. A clean shutdown/reload always flushes everything immediately. |

Related settings that affect where balances are stored:

| Key | Type | Default | Description |
|---|---|---|---|
| `database.type` | `sqlite` \| `mysql` | `sqlite` | SQLite stores balances in `plugins/Valmora/database.db` (recommended for single servers). MySQL syncs balances across multiple servers. |
| `database.mysql.host` | string | `localhost` | MySQL server address (only when `type: mysql`). |
| `database.mysql.port` | number | `3306` | MySQL port (only when `type: mysql`). |
| `database.mysql.database` | string | `valmora` | MySQL database name (only when `type: mysql`). |
| `database.mysql.username` | string | `root` | MySQL user (only when `type: mysql`). |
| `database.mysql.password` | string | *(empty)* | MySQL password (only when `type: mysql`). |
| `database.mysql.use-ssl` | boolean | `false` | Enable SSL/TLS for the MySQL connection. |

> **Death penalty note:** losing half your purse on death is currently **hardcoded** and cannot be changed from config. If you want different behavior, that requires a code change (see `docs/modules/design/economy.md`, "Possible Improvements / Changes").
