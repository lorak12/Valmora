package org.nakii.valmora.module.enchant;

import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.scripting.Condition;

/**
 * One compiled {@code triggers.<TRIGGER>:} block — conditions, actions, and optional fail-actions.
 * Deliberately an enchant-local copy of the same shape {@code module.gui.GuiEventBlock} uses
 * (rather than reusing that class) so {@code module.enchant} has no dependency on {@code module.gui},
 * per the enchant/modifier(-adjacent-module) separation the user asked for.
 */
public record EnchantTriggerBlock(
    Condition conditions,
    CompiledEvent actions,
    CompiledEvent failActions
) {
}
