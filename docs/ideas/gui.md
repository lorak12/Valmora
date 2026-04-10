guis stystem with syntax from the example files, unified
dynamic processing machines. by making a recipe and specyfing a machine or a station like "crafting_table", "anvil", "forge" users can create new processing machines. Then when specyfying a recipe they can choose a station where it can be crafted. In gui we can output a string with machine name, crafting layout so eg.
" S "
" S "
" I "
where the letters are items also defined in the string, so the final string can look like this: crafting_table;" S "" S "" I ";I:stick:1,S:SUPER_INGOT:16 (16 super ingots per slot)
then a parser will read process the string and find a recipe that looks like this and return a string with the output item and the amount of items to be crafted. so the final string can look like this: SUPER_SWORD:1 with aditional metadata in {enchants: ["sharpness:5"]} etc. that need special handling becouse are not standard from the items ids.
a custom recipe with a custom machine can look like this:
"forge;"F""F""C";F:FORGE_COAL:1,F:FORGE_IRON:16;SUPER_SWORD:1"
This allows to create custom machines and recipes that can be used in the game. While keeping the ui yaml based.
