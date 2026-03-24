# Valmora Formatting Guidelines

## Core Principles
All plugin messages should use MiniMessage format for rich text styling, avoiding legacy `§` or `&` color codes. 

## Standard Colors
- **Primary Text:** `<gray>` or `<white>` depending on context (usually `<gray>` for body, `<white>` for emphasis).
- **Highlights/Values:** `<yellow>`, `<green>`, or `<aqua>` for numbers, variables, or important states.
- **Success:** `<green>`
- **Error/Warning:** `<red>`
- **Plugin Name/Branding:** `<gold>`

## Prefixes
### System Messages
- Generic/System: `<dark_gray>[<gold>Valmora<dark_gray>] <white>`

### Command Output
Commands like `/item`, `/mob`, and `/profile` should use a consistent header and border format when printing multi-line info, or a simple prefix for single lines.

**Single Line Pattern:**
`<dark_gray>[<gold>Valmora<dark_gray>] <gray>Your message here <yellow><value>`

**Multi-Line Info Box:**
```
<dark_gray><st>                                                </st>
 <gold><bold>TITLE OF INFO BOX
 <gray>Label 1: <yellow>Value 1
 <gray>Label 2: <green>Value 2
<dark_gray><st>                                                </st>
```

## Command Specifics

### /item & /mob commands
- **Success (Give/Spawn):** `<dark_gray>[<gold>Valmora<dark_gray>] <green>Gave <white><amount>x <item_name> <green>to <player>`
- **Error (Not Found):** `<dark_gray>[<gold>Valmora<dark_gray>] <red>Item/Mob '<id>' not found!`
- **Reloading:** `<dark_gray>[<gold>Valmora<dark_gray>] <green>Configuration reloaded.`
- **Lists:**
```
<dark_gray><st>                                                </st>
 <gold><bold>AVAILABLE ITEMS
 <gray>- <white>item_1
 <gray>- <white>item_2
<dark_gray><st>                                                </st>
```

### /profile command
- Needs to display active profile info clearly utilizing the Multi-Line Info Box format.
- **Profile Info Output:**
```
<dark_gray><st>                                                </st>
 <gold><bold>PROFILE INFO
 <gray>ID: <white><uuid>
 <gray>Name: <yellow><profile_name>
 <gray>Health: <red><current_health>/<max_health>
 <gray>Mana: <aqua><current_mana>/<max_mana>
 <gray>Active Profile: <green>Yes/No
<dark_gray><st>                                                </st>
```
