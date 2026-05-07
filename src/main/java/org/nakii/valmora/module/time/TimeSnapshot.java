package org.nakii.valmora.module.time;

public record TimeSnapshot(
        int hour, int minute, int dayInPhase,
        Phase phase, Season season, int year, long totalDays,
        String phaseName, String seasonName
) {
    public static final TimeSnapshot EPOCH =
            new TimeSnapshot(6, 0, 1, Phase.EARLY, Season.SPRING, 1, 0, "Early", "Spring");

    public String timeOfDayEmote() {
        if (hour >= 6 && hour < 18) return "☀";
        if (hour < 22) return "☾";
        return "✦";
    }

    public String timeOfDayMiniColor() {
        if (hour >= 6 && hour < 18) return "<yellow>";
        if (hour < 22) return "<gray>";
        return "<dark_gray>";
    }

    public String formattedTime() {
        return String.format("%02d:%02d", hour, minute);
    }

    public boolean isDay() {
        return hour >= 6 && hour < 18;
    }
}
