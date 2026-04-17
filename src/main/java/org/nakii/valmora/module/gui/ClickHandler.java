package org.nakii.valmora.module.gui;

import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.scripting.Condition;

public record ClickHandler(
    Condition conditions,
    CompiledEvent actions,
    CompiledEvent failActions
) {
}
