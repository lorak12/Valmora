# This will outline varaible system used across the plugin.

1. Syntax
   Variables are represented as $variable_name$ or $variable.nested_variable$.

2. Types of variables

- Player variables: player.name, player.rank, player.stats, player.stats.STAT_NAME, player.stats.STAT_NAME.formatted, player.skills, player.skills.SKILL_NAME, player.skills.SKILL_NAME.formatted
- GUI variables: gui.quest_list.page, gui.quest_list.has_previous, gui.quest_list.has_next, gui.quest_list.next_page
- More will be added as needed.

3. Expresions
   You can also write expresions in variables like this: ${player.stats.strength.formatted + 10}$
   or write conditions like this: ${player.stats.strength.formatted > 10}$ or ${player.stats.strength.formatted < 10}$ or ${player.stats.strength.formatted == 10}$ or ${player.stats.strength > 10 ? "<green>True" : "<red>False"}$

4. Events
   Syntax: event_unique_name: "event_name arg1 arg2 ..."
   Example: hello_message: "message 'Hello $player.name$' delay:20"
   Event list:
   - message <message> delay:<seconds>
   - give <item>:<amount>,<item>:<amount>... notify[optional]
   - folder <events> [delay:<seconds>] [period:<seconds>]
   - tag [add|remove] <tag>
   - command <command> [delay:<seconds>] [period:<seconds> (if multiple commands)] [console|player|op]
   - gui <gui_name> [delay:<seconds>]
   - teleport x:y:z:world:yaw:pitch [delay:<seconds>]
   - objective [add|remove|complete] <obj1>,<obj2>,<obj3>... [delay:<seconds>] [period:<seconds> (if multiple objectives)]
   - if <condition> then <event> [else <event>]
   - stat [add|remove|set] <stat>:<amount>
   - skill <skillName> [add|remove|set] <skill>:<amount>

5. Contions
    Syntax: condition_name: "condition"
    Example: has_permission: "permission valmora.admin"
    Conditions:
    - permission <permission>
    - 
    
