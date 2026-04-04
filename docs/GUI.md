This will outline structure of GUI system on design level.

Core Concepts:

- YAML-based GUI definitions.
- all of the plugin's GUIs are defined in YAML files including system ones for skills, stats, etc.
- it should be as easy as possible to create, update and manage GUIs.

Structure:

```yaml
# inside /gui/example.yml (one gui per file)
menu_title: "<aqua>Example GUI"
open_command: ["example_gui"] #optional
size: 27 # only multiples of 9
update_interval: 20 #optional
open_events: [] #optional
open_conditions: [] #optional
items:
  "first_item":
    slot: 0
    material: "STONE" #can be a valmora item id (if its a custom item then we ignore everything else like lore, display-name, etc.)
    display-name: "<red>Example Item" #supports variables
    lore:
      - "<gray>This is an example item." #supports variables
    view_conditions: [] #optional
    click_actions: [] #optional
    fallback_item: "fallback_item" #optional, if not provided then it will be air, points to another gui item definition
  "fallback_item": #if no slot is present, it means it'a fallback item for other item(s).
    material: "OAK_LOG"
    display-name: "<red>Fallback Item"
    lore:
      - "<gray>This is a fallback item."
    view_conditions: [] #optional
    click_actions: [] #optional
```

List of other fields:

- left_click_actions: []
- right_click_actions: []
- left_click_conditions: []
- right_click_conditions: []
- shift_right_click_actions: []
- shift_right_click_conditions: []
- shift_left_click_actions: []
- shift_left_click_conditions: []

More extensive gui example with pagination and smart areas

```yaml
menu_title: "<gradient:gold:yellow>Quest Journal <gray>(Page $gui.quest_list.page$)"
open_command: ["quests", "journal"]
size: 54 # 6 rows
update_interval: 20

items:
  # -----------------------------------------------------------------
  # SECTION: STATIC HEADER
  # -----------------------------------------------------------------
  "player_profile":
    slot: 4
    material: "PLAYER_HEAD"
    texture: "base64_texture" #when a player head we expect base64 texture
    display-name: "<yellow>$player.name$'s Progress"
    lore:
      - "<gray>Rank: <white>$player.rank$"
      - "<gray>Total Points: <gold>$player.stats.points$"
      - ""
      - "<green>Keep up the good work!"

  # -----------------------------------------------------------------
  # SECTION: DYNAMIC LIST (The "Smart" Area)
  # -----------------------------------------------------------------
  # slots: Defines the 28 slots (7x4 area) where quest data will flow.
  # dynamic_source: The variable returning the list of quest objects.
  # element_name: The local variable prefix (e.g., $q.name$).
  "quest_list":
    slots:
      [
        10,
        11,
        12,
        13,
        14,
        15,
        16,
        19,
        20,
        21,
        22,
        23,
        24,
        25,
        28,
        29,
        30,
        31,
        32,
        33,
        34,
        37,
        38,
        39,
        40,
        41,
        42,
        43,
      ]
    dynamic_source: "$player.quests$"
    element_name: "q"
    material: "PAPER"
    display-name: "<aqua>Quest: <white>$q.title$"
    lore:
      - "<dark_gray>ID: #$q.id$"
      - ""
      - "<gray>Status: $q.status_formatted$"
      - "<gray>Progress: <yellow>$q.progress$%"
      - ""
      - "<gray>Rewards:"
      - "<dark_gray> - <white>$q.reward_xp$ XP"
      - "<dark_gray> - <white>$q.reward_coins$ Coins"
    click_actions:
      - "message <gray>You selected the quest: <white>$q.title$"
      - "gui close"
    fallback_item: "empty_quest_slot"

  # Placeholder for slots in the 'quest_list' range that have no data
  "empty_quest_slot":
    fallback: true
    material: "LIGHT_GRAY_STAINED_GLASS_PANE"
    display-name: "<dark_gray>Locked/Empty Slot"
    lore:
      - "<gray>Complete more milestones to"
      - "<gray>unlock further quests."

  # -----------------------------------------------------------------
  # SECTION: PAGINATION CONTROLS
  # -----------------------------------------------------------------

  # Previous Page Button
  "prev_page_button":
    slot: 48
    material: "FEATHER"
    display-name: "<yellow>← Previous Page"
    lore:
      - "<gray>Current: <white>$gui.quest_list.page$"
    view_conditions:
      - "$gui.quest_list.has_previous$ == true"
    click_actions:
      - "gui previous_page quest_list"
    fallback_item: "page_border_filler"

  # Next Page Button
  "next_page_button":
    slot: 50
    material: "FEATHER"
    display-name: "<yellow>Next Page →"
    lore:
      - "<gray>Next: <white>$gui.quest_list.next_page$"
    view_conditions:
      - "$gui.quest_list.has_next$ == true"
    click_actions:
      - "gui next_page quest_list"
    fallback_item: "page_border_filler"

  # -----------------------------------------------------------------
  # SECTION: STATIC UTILITIES & DECORATION
  # -----------------------------------------------------------------
  "refresh_button":
    slot: 49
    material: "SUNFLOWER"
    display-name: "<green>Refresh Journal"
    lore:
      - "<gray>Click to update your"
      - "<gray>current quest progress."
    click_actions:
      - "gui refresh"

  "close_menu":
    slot: 53
    material: "BARRIER"
    display-name: "<red>Close Journal"
    click_actions:
      - "gui close"

  # Decoration for empty borders/pagination gaps
  "page_border_filler":
    fallback: true
    material: "GRAY_STAINED_GLASS_PANE"
    display-name: " "

  "border_filler":
    slots:
      [
        0,
        1,
        2,
        3,
        5,
        6,
        7,
        8,
        9,
        17,
        18,
        26,
        27,
        35,
        36,
        44,
        45,
        46,
        47,
        51,
        52,
      ]
    material: "BLACK_STAINED_GLASS_PANE"
    display-name: " "
```
