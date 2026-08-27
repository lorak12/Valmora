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
