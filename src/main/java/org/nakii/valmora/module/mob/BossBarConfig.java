package org.nakii.valmora.module.mob;

import net.kyori.adventure.bossbar.BossBar;

/**
 * Immutable config for a mob's boss bar. Only mobs flagged {@code boss-bar.enabled: true}
 * get a {@link net.kyori.adventure.bossbar.BossBar} shown to nearby players.
 */
public class BossBarConfig {
    private final boolean enabled;
    private final BossBar.Color color;
    private final BossBar.Overlay overlay;
    private final double range;

    public BossBarConfig(boolean enabled, BossBar.Color color, BossBar.Overlay overlay, double range) {
        this.enabled = enabled;
        this.color = color;
        this.overlay = overlay;
        this.range = range;
    }

    public static BossBarConfig disabled() {
        return new BossBarConfig(false, BossBar.Color.RED, BossBar.Overlay.PROGRESS, 40.0);
    }

    public boolean isEnabled() { return enabled; }
    public BossBar.Color getColor() { return color; }
    public BossBar.Overlay getOverlay() { return overlay; }
    public double getRange() { return range; }
}
