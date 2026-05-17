The scripting language is based on a few basic building blocks which are outlined in the following sections. They can be freely combined to create any quest you want. All of these are defined using an instruction text.
```yml
Instruction Text Example

conditions: 
  myCondition: "health 10" 
events:
  myEvent: "hunger set 20"
objectives:
  myObjective: "mobkill ZOMBIE 10"
```
Events/Actions (the name can change but in code everywhere use Event)
In certain moments you will want something to happen. Updating the journal, setting tags, giving rewards, all these are done using events. You define them by specifying a name and instruction string like shown above. At the end of the instruction string you can add the conditions: (with or without s at the end) attribute followed by a list of condition names separated by commas, like conditions:angry,!quest_started. This will make an action fire only when these conditions are met.

Objectives
Objective are goals that player must complete. At first, they must be started for a player with the objective action. When the player completes the objective, all defined actions are run. For example, you could reward the player by giving them an item.

You define them in the objectives section as shown above. At the end of the instruction text you can add conditions and actions for the objective. Conditions will limit when the objective can be completed (e.g. killing zombies only at given location), and actions will fire when the objective is completed (e.g. giving a reward, or setting a tag which will enable collecting a reward from an NPC). You define these like that: conditions:con1,con2 actions:action1,action2 at the end of instruction text. Separate them by commas and never use spaces!

If you want to start an objective right after it was completed you can add the persistent argument at the end of its instruction string. For example, you could create a custom respawn system with a die objective. When the player dies, they will be teleported to the spawnpoint and the die objective will be started again. The persistent argument prevents the objective from being completed, although it will run all its actions. To cancel such an objective you need to use objective delete action.
Example: 
```yml
objectives:
  mineDiamonds: 'block DIAMONDS -10 actions:reward'
  die: 'die cancel respawn:100;200;300;world;90;0 actions:sendRespawnMessage conditions:hasCustomTotem'
```
Auto-Once objectives
If you want an objective to be active for every player right after joining, you can create a auto-once objective. This is done by adding auto-once argument to the instruction of the objective. When you then reload Valmora it is started for all online players and also will be started for every player who joins.

Possible use cases would be a quest which starts if a player reaches a specific location or breaks a specific block.

To prevent the objective from being started every time a player joins, a tag is set for the player whenever the objective is started. With this tag, the objective will not be started again.
These tags follow the syntax <package>.auto-once-<id>, where <id> is the objectives id and <package> the package where the objective is located.

Example
```yml
objectives:
  startQuestByMining: 'location 100;200;300;world 5 actions:start_quest_mine_folder auto-once'
```
Conditions
Conditions allow you to control what options are available to players in conversations, how the NPC responds or if the objective will be completed. They check if a given in-game state is present and return true or false as a result.

You can negate the condition (revert its output) by adding an exclamation mark (!) at the beginning of its name. This only works in the place where conditions are used (i.e. in conversations, not in the conditions section). If you do so, make sure to enclose the condition in quotes, otherwise YAML will give you a syntax error.

Example
```yml
conditions:
  hasFullHealth: "health 20"
events:
  helpWithHealing: "hunger set 20 conditions:!hasFullHealth"
```
Tags
Tags are little pieces of text you can assign to player. They are particularly useful to determine if player has started or completed quest. They are given with tag action and checked with tag condition. All tags are bound to a package, so if you add the questCompleted tag from within a package named monsterQuest, the tag will look like monsterQuest.questCompleted.

Read working across packages to learn how to work with tags across packages.

Points
Points are numbers that can be assigned to a player. You can set them with the point action. you want. You can also take the points away, even to negative numbers. Of course then you can check if player has (or doesn't have) certain amount with the point condition. They can be used as counter for specific number of quest done, as a reputation system in villages or even an NPC's attitude to player.
Packages
All quests you create are organized into packages. A single package can contain one or multiple quests - it's up to your liking. It is very important to have a good understand of packages. Read the packages chapter carefully.
Structure
A package is a folder with a "quest.yml" file. It must be placed inside the "Valmora/quests" directory.
Additionally, you can create extra files or sub-folders inside a package to organize your quest the way you want. Sub-folders of packages that contain a "quest.yml" are separate packages, they do not belong to the surrounding package in any way.

Let's take a look at a few examples:
A very simple package. It's defined by the package.yml and has two additional files.
Valmora
    │
    └── quests
    	    └── dailyQuest
    	    		├── quest.yml
    	    		├── dailyObjectives.yml
   	    		└── questNPC.yml
The package storyLine is defined by the quest.yml. It contains two sub-folders, both of them (including their files) are part of the package.
Valmora
    │
    └── quests
    	    └── storyLine
    	    		├── quest.yml
    	    		├── dragonInvestigaion
			│	├── peterDialog.yml
			│	├── mikeDialog.yml
			│       └── questCode.yml 
   	    		└── dragonFight
				├── kingDialog.yml
				├── events.yml
				└── dragonTalkEvents.yml 
The package weeklyQuests is defined by the quest.yml. It contains two sub-folders, they are not part of the package weeklyQuests. This is the case because they have their own quest.yml files. Because of that they are separate packages.
Valmora
    │
    └── quests
    	    └── weeklyQuests
    	    		├── quest.yml
    	    		├── generalQuestCode.yml
    	    		├── weekOne
			│	├── quest.yml
			│       └── questCode.yml 
   	    		└── weekTwo
				├── quest.yml
				└── questCode.yml 
Defining features🔗
You can freely define features (actions, conversations, items etc.) in all files of a quest package. However, they need to be defined in a section that defines their type.

The names of these features must be unique in that package, no matter which file they are in
```yml
events:
  teleportPlayer: "..."

conditions:
  hasDiamondArmor: "..."

objectives:
  killCrepper: "..."

items:
  legendarySword: "..."

conversations:
  bobsConversation:
    quester: Bob
    #...

menus:
  homeMenu:
    height: 3
    #...
```
Working across Packages
Accessing features from other packages can be very helpful to link quests together. All actions, conditions, objectives, items and conversations can be accessed.

You never need to access a specific file since feature names are unique within a package.

Top-Level Packages
You can access top-level packages (placed directly in "quests") by prefixing the feature's name with a greater than (>) and the package name.
Example:
Let's assume you have a rewards package that contains player reward actions.
Let's run the easyMobObjective action of the rewards package from another package:

Add a greater than (>) before the action name ➡ >easyMobObjective
Add the package name in front of the greater than ➡ rewards>easyMobObjective
An example usage could look like this:

```yml
zombieObjective: "mobkill ZOMBIE 5 actions:rewards>easyMobObjective"
```
Note that this only works for top-level packages (the rewards package is placed directly in the QuestPackages folder). Check the next paragraph to see how it's done for other packages.
Packages in Sub-folders
You can access packages in sub-folders by prefixing the feature's name with the package name and the path from the "QuestPackages" folder to the package.
Let's assume you have a dailyQuests package that contains a dailyQuestOne package. The dailyQuests package is located in the QuestPackages folder. Let's run the startDailyQuest action of the dailyQuestOne package from a third package:

Combine the action name with the package name ➡ dailyQuestOne>startDailyQuest
Add the path from the QuestPackages folder to the dailyQuestOne package seperated by dashes (-). ➡ dailyQuests-dailyQuestOne>startDailyQuest
An example usage could look like this:


zombieObjective: "mobkill ZOMBIE 5 actions:dailyQuests-dailyQuestOne>startDailyQuest"
Let's assume you have a dailyQuests package that contains a dailyQuestOne package. The dailyQuests package is contained inside a folder called repeatable which is located in the QuestPackages folder. Let's run the startDailyQuest action of the dailyQuestOne package from a third package:

Combine the action name with the package name ➡ dailyQuestOne>startDailyQuest
Add the path from the QuestPackages folder to the dailyQuestOne package seperated by dashes (-). ➡ repetable-dailyQuests-dailyQuestOne>startDailyQuest
An example usage could look like this:


zombieObjective: "mobkill ZOMBIE 5 actions:repetable-dailyQuests-dailyQuestOne>startDailyQuest"
Relative paths🔗
You can specify relative paths to a package instead of full paths. The underscore (_) means "one folder up" from the current packages "package.yml". In turn, a leading dash (-) combined with a folder name navigates "one folder down" into the given folder. Each package in the path must be separated by a dash.

This can be useful when distributing or moving packages. Instead of rewriting every package path to match the current location, relative paths will still work.
Let's assume you have a weeklyQuests folder that contains a weeklyQuestOne and a weeklyQuestTwo package. Let's run the startQuestTwo action of the weeklyQuestTwo package from the weeklyQuestOne package.

Combine the action name with the package name ➡ weeklyQuestTwo>startQuestTwo
Add the path from the current package.yml to the folder the package of interested lies in. This is done using underscores ("go one folder up"). A dash must be added after each underscore (-). ➡ _-weeklyQuestTwo>startQuestTwo
An example usage could look like this:


zombieObjective: "mobkill ZOMBIE 50 actions:_-weeklyQuestTwo>startQuestTwo"
Let's assume you have a weeklyQuests package that contains a weeklyQuestTwo package which contains another package called subQuest. Let's run the startQuest action of the subQuest package from the weeklyQuests package.

Combine the action name with the package name ➡ subQuest>startQuest
Add the path from the current package.yml to the folder the package of interest lies in. Package names must be seperated by dashes (-). The path must also be started with a dash to signal "from the current package downwards". ➡ -weeklyQuestTwo-subQuest>startQuest
An example usage could look like this:


zombieObjective: "mobkill ZOMBIE 50 actions:-weeklyQuestTwo-subQuest>startQuest"
Disabling Packages🔗
Packages are enabled by default, you can disable a package if you don't want it to be loaded. Set enabled inside the package section to true or false to enable or disable the package.


package:
  ## Optionally add this to the quest.yml
  enabled: false
Templates🔗
You should have experience creating and using packages before you start using templates. Templates are a way to create packages that can be used as a base for other packages to reduce the amount of repetitive work. Therefore, they are a great way to centralize logic or create utilities.

Using Templates🔗
Templates work exactly like packages, except that they are placed in the "Valmora/temlates" folder instead of the "Valmora/quests" folder and that they are not loaded as a ready to use package. Instead, they are used as a base for other packages by referring to them in the templates section inside the package section.


package:
  templates:
    - MyTemplate
    - SecondTemplate
If you use the above in a package, the MyTemplate and SecondTemplate templates would be used as a base for the package. This means that all the actions, objectives, conditions, etc. from the templates would be added to the package. If the package already contains an action/objective/condition with the same name as one from the template, the package's actions, objectives, conditions, etc. will be used instead of the one from the template.

If the same actions, objectives, conditions, etc. is defined in multiple templates, the one from the lists first template will be used.

You can also use templates in templates. Also in this case, the actions, objectives, conditions, etc. that are defined in the current template will be used instead of the ones from the template that is being used as a base.
Unified location formating🔗
Whenever you want to define locations in your actions, conditions, objectives or anywhere else, you will define it with this specific format. The location consists of 2 things: base and vector. Only the base is always required.

Base Location🔗
Locations are defined in the format x;y;z;world;yaw;pitch for example 100;200;300;world, where 100 is the x coordinate, 200 for y, 300 for z and world is the name of the world. All numbers may have decimal places. You can also omit the yaw and pitch values as they are optional. They define the rotation and if you want to set them you must provide them both. Yaw is the left-right rotation, pitch is the up-down rotation. A fully defined location may look like this: 100;200;300;world;90;-45.

Every single element may be a placeholder as well as the entire location itself. As an example you can use the %location% placeholder to get a player's current location.

Vectors🔗
The vector is a modification of the location. Vectors look like ->(10;2.5;-13) and are added to the end of the base. This will modify the location, X by 10, Y by 2.5 and Z by -13. For example, location written as 100;200;300;world_nether->(10;2.5;-13) will generate a location with X=110, Y=202.5 and Z=287 in the world world_nether.

Block Selectors🔗
When specifying a way of matching a block, a block selector is used.

Format🔗
The format of a block selector is: namespace:material[state=value,...]

Where:

namespace - (optional) The material namespace. If left out then it will be assumed to be 'minecraft'. Can be a regex.

material - The material the block is made of. All materials can be found in Spigots Javadocs. It can be a regex. If the regex ends with square brackets you have to add another pair of empty square brackets even if you don't want to use the state argument ([regex][]).
Instead of using a regex to match multiple materials you can also define a tag. Every tag matches a special group of blocks or items that can be grouped together logically. They can be using this format :blocks:flowers or minecraft:blocks:flowers. Be aware that a tag always starts with either : or a namespace.

state - (optional) The block states can be provided in a comma separated key=value list surrounded by square brackets. You can look up states in the Minecraft wiki. Any states left out will be ignored when matching. Values can be a regex.

Examples:

minecraft:stone - Matches all blocks of type STONE

redstone_wire - Matches all blocks of type REDSTONE_WIRE

redstone_wire[power=5] - Matches all blocks of type REDSTONE_WIRE and which have a power of 5

redstone_wire[power=5,facing=1] - Matches all blocks of type REDSTONE_WIRE and which have both a power of 5 and are facing 1

.*_LOG - Matches all LOGS

.* - Matches everything

.*[waterlogged=true] - Matches all waterlogged blocks

minecraft:blocks:flowers - Matches all flowers

:blocks:crops[age=0] - Matches all crops with an age of 0 meaning, not grown / just planted

Setting behaviour🔗
A block selector with a regex or tag as it's material name results in a random block out of all blocks that match that regex or tag. You cannot use a regex in block states when the block selector is used for placing blocks.

Matching behaviour🔗
The block state will ignore all additional block states on the block it's compared with by default. Example: fence[facing=north] matches fence[facing=north] and fence[facing=north,waterlogged=true] You can add an exactMatch argument if you only want to match blocks that exactly match the block state. A regex is allowed in any block state value when the block selector is used to match blocks.

Regex (Regular Expressions)🔗
A regular expression is a sequence of characters that specifies a search pattern for text. It's used in Valmora to check if game objects match a user-defined input. For example, Block Selectors use a regex to match multiple materials or block states. You can also use regular expressions in the variable condition or the password objective to match player names, item names, etc. These expressions are a very powerful tool, but can be confusing at first.

Common Use Cases🔗
Use Case	Regex
A specific text e.g. STONE	STONE
A text starting with STONE	STONE.*
A text ending with _LOG	.*_LOG
A specific number e.g. 42	^42$
A specific range of numbers, e.g. any number between 0 and 99	[0-9]{1,2}
Positive numbers only	^\d+$
Negative numbers only	^-\d+$
Any number	[-+]?[0-9]+\.?[0-9]+
Quoting & advanced YAML
Quoting🔗
Sometimes it is important to pass an argument that contains spaces or even a newline as an argument. For those cases you can use quotes.

Quoting examples

actions:
  multiline: "notify \"This is the first line.\nAnd here is the second line!\"" 
  quotes_in_quotes: 'notify "And he said: \"I have to tell you something!\""' 
  backslash: notify "\\o/" 
YAML🔗
Using YAML multiline syntax🔗
Very long instructions can be hard to read, but to improve readability there is a YAML feature that allows you to write easily readable formatted text that will work perfectly fine with instructions.

Folded multi-line block example

actions:
  long_text: >-
    notify
    This is a very long text.
    It will still be displayed as one single line in chat,
    no matter where you insert a newline.
    Even combined with "quoting
    there will be no newline" unless you "use a double linebreak,"

    as that is interpreted as a normal newline by YAML."

Conversations are the main way to interact with players in Valmora Quest Moduel. They are used to display text, ask questions and execute commands. This page contains the reference documentation for all conversation related features. Consider doing the conversation tutorial if you are just getting started.

General Information
A conversation is a sequence of questions and answers. It is started by a NPC and can be ended by both the player or the NPC.

conversations: #(1)!
  mayorHans: #(2)!
    quester: "Hans the Mayor" #(3)!
    first: "welcome,blacksmithReminder" #(4)!
    stop: "true"  #(5)!
    block_item_transfer: "true" #(16)!
    final_actions: "setCityState" #(6)!
    conversationIO: "menu" #(7)!
    interceptor: "simple" #(8)!
    interceptor_delay: 70 #(9)!
    NPC_options: #(10)!
      welcome:
        text: "Good day, dear %player%! Welcome back to my town." #(11)!
        actions: "playSound,giveMoney" #(12)!
        conditions: "firstVisit,!criminal" #(13)!
        pointers: "friendly,hostile" #(14)!
      blacksmithReminder:
        text: "Please visit the blacksmith, he has a task for you."
        conditions: "!criminal"
      howDareYou:
        text: "How dare you to talk to me like that?! Get out of my sight!"
    player_options: #(15)!
      friendly:
        text: "Thank you your honor, I'm happy to be here."
        actions: "givePresent"
        pointers: "blacksmithReminder"
      hostile:
        text: "Your Honor, I come bearing a ultimatum letter from the people. They have grown tired of your corruption and greed."
        conditions: 'hasUltimatumLetter'
        pointers: "howDareYou"

1.All conversation must be defined in a conversations section.
2.mayorHans is the name of the conversation, which is used to reference the conversation.
3.Hans is the visual name of NPC that is displayed during the conversation.
4.first are pointers to options the NPC will use at the beginning of the conversation. He will choose the first one that meets all conditions. You define these options in npc_options branch.
stop determines if player can move away from an NPC while in this conversation (false) or if he's stopped every time he tries to (true). If enabled, it will also suspend the conversation when the player quits, and resume it after he joins back in. This way he will have to finish his conversation no matter what. You can modify the distance at which the conversation is automatically stopped / player is teleported back with max_conversation_distance option in "config.yml".
final_actions are actions that will fire when the conversation ends, no matter how it ends (so you can create e.g. guards attacking the player if he tries to run). You can leave this option out if you don't need any final actions.
conversationIO optionally set the conversation style for this conversation. Multiple styles can be provided in a comma-separated list with the first valid one used. It's better to set this as a global config setting in "config.yml".
interceptor optionally set a chat interceptor for this conversation. Multiple interceptors can be provided in a comma-separated list with the first valid one used. It's better to set this as a global config setting in "config.yml".
interceptor_delay optionally set a delay (in ticks) after the conversation ends and before the interceptor is displayed. This can also be set globally in "config.yml".
NPC_options is a branch with texts said by the NPC.
text defines what will display on screen. If you don't want to set any actions/conditions/pointers to the option, just skip them. Only text is always required.
actions is a list of action names that will fire when an option is chosen (either by NPC or a player), defined similar to conditions.
conditions are names of conditions which must be met for this option to display, separated by commas.
pointers is list of pointers to the opposite branch (from NPC branch it will point to options player can choose from when answering, and from player branch it will point to different NPC reactions).
player_options is a branch with options the player can choose from.
block_item_transfer determines if player can move items while in this conversation (false) or if the item's movement is blocked (true). If not set the conversation.prevent_item_movement setting in the "config.yml" is used.
When an NPC wants to say something he will check conditions for the first option (in this case welcome). If they are met, he will choose it. Otherwise, he will skip to next option (note: conversation ends when there are no options left to choose). After choosing an option the NPC will execute any actions defined in it and say it's text. Then the player will see options defined in the player_options branch to which the pointers setting points, in this case friendly and hostile. If the conditions for a player options is not met, the option is simply not displayed, similar to texts from NPC. The player will choose the option they want, and it will point back to other NPC text, which points to next player options and so on.

If there are no possible options for player or NPC (either from not meeting any conditions or being not defined) the conversations ends. If the conversation ends unexpectedly, check the console - it could be an error in the configuration.

This can and will be a little confusing, so you should name your options, conditions and actions in a way which you will understand in the future. Don't worry though, if you make some mistake in configuration, the plugin will tell you this when running /bq reload.

Binding Conversations to NPCs
Conversations can be assigned to NPCs. This is done in the npc_conversations section:

npc_conversations:
  Hans: mayorHans #(1)!
The key is the NpcID, the value a ConversationID.
A NPC will only react to right clicks by default. This can be changed by setting npcs.accept_left_click in the "config.yml" to true.

You can assign the same conversation to multiple NPCs. It is not possible to assign multiple conversations to one NPC. For this purpose, have a look at cross-conversation-pointers though.

Conversation displaying
Valmora provides different conversation styles, so called "conversationIO's". They differ in their visual style and the way the player interacts with them.

Valmora uses the menu style by default if the server runs at least Minecraft version 1.21.4. If PacketEvents is installed it will use the packetevents style as fallback, otherwise the tellraw style will be used. You can change this setting globally by changing the default_io option in the "config.yml" file.

It is also possible to override this setting per conversation. Add a conversationIO: <type> setting to the conversation file at the top of the YAML hierarchy (which is the same level as quester or first options).

In both cases, you can choose from the following conversation styles:

!!! example "Conversation Styles" === "menu" A modern conversation style that works with some of Minecraft's native controls.

    @snippet:versions:mc-1.21.4@
    
    When `set_speed` is disabled the player won't be able to be moved by external sources and get "rubber banding"
    like effect when moving/selecting options.
    
 === "`packetevents`"
    Similar to `menu`, but it mounts the player client side on a fake entity instead.
    
    **Requires [PacketEvents](https://www.spigotmc.org/resources/80279/)**
    
    It uses the same Customization as `menu`.
Cross-Conversation Pointers
If you want to create a conversation with multiple NPCs at once or split a huge conversation into smaller, more focused files, you can point to both NPC and player options in other conversations. Use the cross-package syntax to do so.

There is one special case when you want to refer to the starting options of another conversation. In this case you do not specify an option name after the point (package>conversation.).

myConversationOption:
  text: "Look carefully at that guard over there..."
  pointers: "lookCareful,guardConv.lookDetected,mainStory>Mirko.interrupt" #(1)!
specialOption:
  text: "This option points to the starting options of the conversation 'guardConv' in the package 'myPackage'."
  pointers: "myPackage>guardConv."
lookCareful refers to another option in the same conversation named lookCareful.
guardConv.lookDetected refers to the option lookDetected in the conversation guardConv in the same package.
mainStory>Mirko.interrupt refers to the option interrupt in the conversation Mirko in the package mainStory.
Conversation Placeholders
You can use placeholders in the conversations. They will be resolved and displayed to the player when he starts a conversation. Check the placeholders list for more information about which placeholders exist.

!!! note If you use a placeholders incorrectly (for example trying to get a property of an objective which isn't active for the player, or using %quester% in message action), the placeholders will be replaced with empty string ("").


Chat Interceptors
While engaged in a conversation, it can be distracting when messages from other players or system messages interfere with the dialogue. A chat interceptor provides a method of intercepting those messages and then sending them after the conversation has ended.

You can specify the default chat interceptor by setting default_interceptor inside the "config.yml". Additionally, you can overwrite the default for each conversation by setting the interceptor key inside your conversation file.

The default configuration of Valmora sets the default_interceptor option to packetevents,simple. This means that it first tries to use the packetevents interceptor. If that fails it falls back to using the simple interceptor.

Valmora adds following interceptors: simple, packetevents and none:

The simple interceptor works with every server but only supports very basic functionality and may not work with plugins like Herochat.

The packetevents interceptor requires the PacketEvents plugin to be installed. It will work well in any kind of situation.

The none interceptor is an interceptor that won't intercept messages. That sounds useless until you have a conversation that you want to be excluded from interception. In this case you can just set interceptor: none inside your conversation file.
Formatter
Every single text that can be displayed supports the minimessage formatting strings like <red> and <bold>. You don't need to close tchem like </red>, but sometimes it makes it clear what exactly you are formatting.
Notification Settings
Valmora features a powerful notification system that allows you to display any information to your players. You can freely choose between many NotifyIO's like simple chat output, (sub)titles, advancements or sounds. They all come with unique options that allow you to customize them.
Sending custom notifications🔗
A truly custom notification can be sent using the notify action at any time.
Objective notifications🔗
Some objectives have a notify argument that can be added to their instruction. If you do so, the objective will send a notification to the player if they progress in the objective. You can also add an interval (notify:5) - in this case the player will get a notification every 5 steps towards the completion of the objective.


Built-in Notification Example

blocks_to_break: '{amount} blocks left to break'
Notify IO's & Categories
A NotifyIO is a method of displaying notifications to the player. Here's a demo video showing an example configuration of all NotifyIO's:
Most NotifyIO's have unique settings that somehow change how a notification is displayed. The actual message is either defined in the action that triggers the NotifyIO or the appropriate language file in the lang directory for all built-in notifications.

Notify🔗
Context: 
Syntax: notify <message> [category] [io] [any io specific settings]
Description: Send the notification to the player using the specified notifyIO.

Every notify IO has it's own specific settings. You must understand these too if you want to use the Notify system to it's full extend. Advanced users may also use Notify Categories to make their lives easier.
Warning

All colons (:) in the message part of the notification need to be escaped, including those inside placeholders. One backslash (\) is required when using no quoting at all (...) or single quotes ('...'). Two backslashes are required (\\) when using double quotes ("...").
You also need to escape the backslash itself, if you use double quotes for some things like \n.

Examples:
actionName: notify Peter:Heya %player%! ➡ actionName: notify Peter\:Heya %player%!
actionName: 'notify Peter:Heya %player%!' ➡ actionName: 'notify Peter\:Heya %player%!'
actionName: "notify Peter:Heya %player%!" ➡ actionName: "notify Peter\\:Heya %player%!"
otherAction: notify You own %math.calc:5% fish! ➡ otherAction: You own %math.calc\:5% fish!
newLine: "notify Some multiline \n message" ➡ newLine: "notify Some multiline \\n message"

Parameter	Syntax	Default Value	Explanation
message	Any text with spaces!		The message that will be displayed. Supports translations. Must be first
category	category:info	None	Will load all settings from that Notification Category. Can be a comma-separated list. The first existent category will be used.
io	io:bossbar	io:chat	Any NotifyIO Overrides the "category". settings.
any io specific settings	setting:value	None	Some notifyIO's provide specific settings. Can be used multiple times. Overrides the "category" settings.
```yml
Example

actions:
  #The simplest of all notify actions. Just a chat message:
  customAction: "notify Hello %player%!"  

  #It's the same as this one since 'chat' is the default IO.
  theSame: "notify Hello %player%! io:chat"

  #This one displays a title and a subtitle:
  myTitle: "notify This is a title.\\nThis is a subtitle. io:title"

  #Plays a sound:
  mySound: "notify io:sound sound:x.y.z"

  #This one explicitly defines an io (bossbar) and adds one bossbarIO option + one soundIO option:
  myBar: "notify This is a custom message. io:bossbar barColor:red sound:block.anvil.use"

  #Some actions with categories.
  myAction1: "notify This is a custom message! category:info"
  myAction2: "notify This is a custom message! category:firstChoice,secondChoice"

  #You can also override category settings:
  myAction3: "notify Another message! category:info io:advancement frame:challenge"
```
NotifyAll🔗
Context: 
Syntax: notifyall <message> [category] [io] [any io specific settings]
Description: Send the notification to all players using the specified notifyIO.

You can broadcast notifications to all online players on the server using the notifyall action. It works just like the notify action. Placeholders are resolved for each online player, not for the player the action is executed for.

Example

actions:
  announceDungeon: "notifyall A new dungeon has opened!"

Available NotifyIOs🔗
There are a bunch of notify IOs available. Below is a list of all available notifyIOs and their possible options.

Notify Syntax

actions:
  notifyExample: notify <message> io:<NotifyIO_Type> <option_1>:<option_1_value> <option_2>:<option_2_value> 
    <category>:<category_Name>

List:
- Chat
Option	Description
Sound	Any option from the SoundIO.
- Advancement
Option	Description
frame	What Achievement frame to use. Can be: challenge, goal, task
icon	What icon to show. Must be the vanilla name of an item. Example: minecraft:map
Sound	Any option from the SoundIO.
- Actionbar
Option	Description
Sound	Any option from the SoundIO.
- Bossbar
Option	Description
barFlags	What flags to add to the bossbar. PLAY_BOSS_MUSIC seems to be broken in either server or the game itself.
barColor	What color to draw the bar.
progress	What progress to show in the bar. A floating point number between 0.0 (empty) and 1.0 (full).
style	What bar style to use.
stay	How many ticks to keep the bar on screen. Defaults to 70.
countdown	Animates the progress of the bar if set. The value determines how often the bar is updated. Formula: 
Sound	Any option from the SoundIO.
- Title
Option	Description
fadeIn	Ticks to fade the title in. Default 10
stay	Ticks to keep title on screen. Default 70
fadeOut	Ticks to fade the title out. Default 20
Sound	Any option from the SoundIO.
- SubTitle
Option	Description
fadeIn	Ticks to fade the title in. Default 10
stay	Ticks to keep title on screen. Default 70
fadeOut	Ticks to fade the title out. Default 20
Sound	Any option from the SoundIO.
- Totem (shows a totem with custom model data)
Option	Description
customModelData	The CustomModelData to use. Prefere to use itemModel instead.
itemModel	Requires Minecraft version 1.21.4 or above! The ItemModel to use
Sound	Any option from the SoundIO.
- Sound
This IO just plays a sound. You can use its options in any other IO. You should read the wiki page of the playsound command as Minecraft's sound system is kinda strange. Just one example: Sound never moves in Minecraft. It's totally static. Keep that in mind when creating sounds close to a player. They can move around the sound and make it louder or quieter by walking towards / away from it.
Option	Description
sound	Sound to play. If blank, no sound. Either vanilla Minecraft sounds (get them using /playsound autocompletion) or the name of a sound from a resource pack.
soundcategory	The category in which the sound will be played.
soundvolume	Minecraft's special sound volume. Default: 1
soundpitch	Pitch of the sound. Default: 1 Min: 0 Max: 2
soundlocation	Default: The player's location. A location using the Valmora ULF.
soundplayeroffset	This option is special. See below.
soundplayeroffset
soundplayeroffset is an option to move the location of the sound based off the player's location as well as the soundlocation option. This option can be a number or a vector. 

This is only useful if you set the soundlocation option to a location that isn't the player's. Using a number will move the "source" of the sound so that it "points" towards the soundlocation option relative to the player's current location using the value that you set as distance increments. The sound will be at the actual location if the player is closer to the soundlocation then the soundplayeroffset would allow. 
Example Usage

You could make a "sound compass" that will play a sound in the direction of a point of interest.
A vector has to be in the format (x;y;z). This system will use the player's relative coordinate system. This means that the vectors' x-axis is left/right from the players head, the y-axis is up/down from the player's head location and the z-axis is in-front/behind the player's head; it will move along the player's head.
Example Usage

A Halloween event where the player hears a 👻 whispering into his left ear, no matter where he is or how he turns his head... 🎃
Categories🔗
Notify Categories are pre-defined NotifyIO settings. They can be applied to any notify action and are also used by Valmora's built-in notifications. All categories must be defined in a section called notifications.
Custom Categories🔗
Tip

Categories are very useful for notifications that you are going to be sending players multiple times and want to create a unified, consistent look and sound.

Custom categories are user-created presets for any notify action. They shorten your actions and enable you to change how a notification of a certain category looks in one central place. They do not allow you to set a message though as the message is an argument of the notify action!

Custom Categories Example Configuration

notifications:
  money: 
   io: advancement 
   icon: gold_ingot 
   sound: entity.item.pickup 
Warning

The only thing you must be careful with is the name of your custom categories. You could end up using a reserved name - these stem from Valmora's built-in notification categories. Changing these are a different feature. A full list of all reserved names can be found below.
Built-in Categories🔗
Default Categories

By default, only 2 built-in categories exist: error/info.

The table below contains all built-in notification categories.

You may notice that the "Categories" column lists two categories: One that matches the name of the notification message and error/info.

These work exactly like the user-made categories in the notify action. The first existent category (from left to right) will be used. This allows you to change all built-in notifications with just two entries in your notifications section:


notifications:
  info:
    io: chat 
  error:
    io: actionbar 
You can override the settings from the info/error category for any specific notification by adding it to the notifications section. When you create a category with a name that matches a notification message name, Valmora then defaults to that option over error/info

Example

notifications:
  info:
    io: actionbar
  error:
    io: actionbar
  new_journal_entry:  
    io: subtitle 
Notifications	Categories
Command Blocked	command_blocked, error
No Permission	no_permission, error
Inventory Full Backpack	inventory_full_backpack, inventory_full, error
Inventory Full Drop	inventory_full_drop, inventory_full, error
Language Changed	language_changed, info
Quest Cancelled	quest_cancelled, info
New Journal Entry	new_journal_entry, info
Conversation nothing to start	conversation_nothing_to_start, info
Conversation start	conversation_start, info
Conversation end	conversation_end, info
Conversation blocked	busy, error
Money Given	money_given, info
Money Taken	money_taken, info
Items given	items_given, info
Items taken	items_taken, info
Notifications	Categories
Points given	point_given, info
Points taken	point_taken, info
Points set	point_set, info
Points multiplied	point_multiplied, info
Animals to Breed	animals_to_breed, info
Blocks to Break	blocks_to_break, info
Blocks to Place	blocks_to_place, info
Mobs to click	mobs_to_click, info
Mobs to Kill	mobs_to_kill, info
Fish to catch	fish_to_catch, info
Players to kill	players_to_kill, info
Potions to brew	potions_to_brew, info
Sheep to shear	sheep_to_shear, info
Times to jump	times_to_jump, info
Animals to bread	animals_to_tame, info
Payment to receive	payment_to_receive, info
Levels to gain	level_to_gain, info
Items to enchant	items_to_enchant, info
Items to craft	items_to_craft, info
Items to smelt	items_to_smelt, info
Items to pickup	items_to_pickup, info
Hiding Players
You can also hide players for specific players in the player_hider section of your package. When the source_player meets the conditions, every player that meets the target_player conditions will be completely hidden from them. This is really useful if you want a lonely place on your server or your quests break when multiple players can see or affect each other. You can configure the interval which checks the conditions with the player_update_interval setting.

Special behaviour:

A player that meets the source_playerconditions can no longer be pushed by other players.
By leaving the e.g. source_player argument empty it will match all players.

player_hider:
  example_hider:  #All players in a special region cannot see any other players in that region. If a player is outside the region, they can still see the `target_player`.
    source_player: in_StoryRegion
    target_player: in_StoryRegion
  another_hider: #No one can see any players inside a secret room.
    #The source_player argument is left out to match all players.    
    target_player: in_secretRoom
  empty_hider: #in_Lobby is a world condition. Therefore, the lobby world appears empty for everyone that is in it.
    source_player: in_Lobby
    #The target_player argument is left out to match all players.
