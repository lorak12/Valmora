package org.nakii.valmora.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public class Formatter {

    static MiniMessage miniMessage = MiniMessage.builder().postProcessor(component -> component.style(component.style().decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE))).build();

    public static Component format(String text){
        return miniMessage.deserialize(text);
    }

    public static List<Component> formatList(List<String> text){
        return text.stream().map(Formatter::format).toList();
    }

    private static final String[] ROMAN_ONES = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
    private static final String[] ROMAN_TENS = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};

    /** Renders a positive level as a Roman numeral (e.g. 5 -&gt; "V", 14 -&gt; "XIV"), for display
     *  contexts that traditionally show enchant/effect/ability levels this way instead of a raw
     *  integer. Supports 1-99; falls back to the plain integer outside that range (matches the
     *  fallback behavior of the three call sites this consolidates: {@code GuiVariableProvider},
     *  {@code AlchemyMachineHandler}, {@code AlchemyVariableProvider}). */
    public static String toRoman(int level) {
        if (level < 1 || level > 99) return String.valueOf(level);
        return ROMAN_TENS[level / 10] + ROMAN_ONES[level % 10];
    }

    /** Title-cases every word in {@code text} (e.g. "forge titan" / "FORGE_TITAN".replace("_"," ") -> "Forge Titan"). */
    public static String capitalize(String text){
        if (text == null || text.isEmpty()) return text;
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
