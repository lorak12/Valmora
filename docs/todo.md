[] Enchanting system
[] Redo stats system
[] Finish all of the prebuild gui's
[*] Fix pagination inside guis
[*] Make the skills_detail a dynamic gui.
[] Implement better Item types and maybe item categories
[] Implement etable and anvil guis with these constraints: Dynamic Context Injection: Currently, our PaginatedComponent loops over fixed lists (like $player.skill.list$). For an Enchanting Table, we need to loop over available enchantments for the item currently sitting in the input slot. We will need a new Variable Provider (e.g., $gui.input.0.available_enchants$) that dynamically recalculates whenever the item in the input slot changes. Inventory Update Triggers: Right now, the GUI updates on a fixed interval (update-interval: 20). If a player places a sword in the table, they shouldn't have to wait 1 second for the enchant list to appear. We will need an on-slot-update event block in the GUI config. Anvil Cost Math: Combining two items with custom enchants requires specific math (e.g., Sharpness 4 + Sharpness 4 = Sharpness 5, but Sharpness 4 + Sharpness 3 = Sharpness 4). We will need a dedicated script event (e.g., anvil_combine <inputSlot1> <inputSlot2> <outputSlot>) that handles this math internally in Java, rather than trying to do complex math using the expression parser in YAML. Enchantment Books in Anvil: The GUI script needs to recognize if input2 is an Enchanted Book and read its PDC to know what enchant to apply to input1.

[] Make a docs for adding a new module and how to handle configs, calling other modules etc.

---

TODO for tomorrow:
[] Configure opencode to work with my project
[] create the docs for module development
[] fix the project so there is no errors
