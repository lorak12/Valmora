package org.nakii.valmora.module.stat.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired whenever a player's <b>base</b> stat value changes via {@code StatManager.addStat}/
 * {@code reduceStat}/{@code setStat}/{@code resetStat} — not on every recalculation (which would
 * fire constantly for every equipped item/enchant/effect re-application) and not for the
 * modifier-only {@code addModifier} path used inside {@code recalculateStats}. Lets quest/audit
 * systems react to a genuine stat change without polling.
 *
 * <p>This is informational only — not cancellable, since the mutation has already happened by
 * the time it fires (the event carries the before/after values, it doesn't gate the change).
 */
public class StatModifyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String statId;
    private final double oldValue;
    private final double newValue;

    public StatModifyEvent(Player player, String statId, double oldValue, double newValue) {
        this.player = player;
        this.statId = statId;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Player getPlayer() { return player; }
    public String getStatId() { return statId; }
    public double getOldValue() { return oldValue; }
    public double getNewValue() { return newValue; }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
