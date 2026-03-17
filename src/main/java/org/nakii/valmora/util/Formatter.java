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

    public static String capitalize(String text){
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }
}
