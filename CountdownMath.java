package me.halloweencountdown;

import java.time.Duration;
import java.time.ZonedDateTime;

/** The countdown arithmetic, kept free of Minecraft code so it can be tested on its own. */
final class CountdownMath {

    /** Moments (in seconds before the target) that get an announcement. Must stay in descending order. */
    static final int[] MILESTONES = {259200, 172800, 86400, 43200, 21600, 10800, 3600, 1800, 600, 300, 120, 60, 30,
            10, 5, 4, 3, 2, 1};

    private CountdownMath() {
    }

    static long secondsLeft(ZonedDateTime target, ZonedDateTime now) {
        return Duration.between(now, target).getSeconds();
    }

    /** "2d 3h 15m 10s", leaving out units that are not needed. */
    static String format(long secs) {
        if (secs < 0) secs = 0;
        long d = secs / 86400, h = (secs % 86400) / 3600, m = (secs % 3600) / 60, s = secs % 60;
        if (d > 0) return d + "d " + h + "h " + m + "m " + s + "s";
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    /** The same moment spelled out for chat: "1 day", "30 minutes", "10 seconds". */
    static String words(long secs) {
        if (secs % 86400 == 0 && secs >= 86400) return plural(secs / 86400, "day");
        if (secs % 3600 == 0 && secs >= 3600) return plural(secs / 3600, "hour");
        if (secs % 60 == 0 && secs >= 60) return plural(secs / 60, "minute");
        return plural(secs, "second");
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }

    /**
     * Where the "last announced" marker should start so that milestones already passed are not announced
     * again: the smallest milestone that is still at or above the time left.
     */
    static long initialMarker(long secsLeft) {
        long marker = Long.MAX_VALUE;
        for (int m : MILESTONES) if (m >= secsLeft) marker = m;
        return marker;
    }

    /**
     * The milestone to announce right now, or -1. It looks from the closest milestone outward, so after a
     * lag spike only the latest milestone is announced and the skipped ones are not replayed.
     */
    static long dueMilestone(long secsLeft, long lastAnnounced) {
        for (int i = MILESTONES.length - 1; i >= 0; i--) {
            int m = MILESTONES[i];
            if (secsLeft <= m && lastAnnounced > m) return m;
        }
        return -1;
    }

    /** Bar progress from 1.0 (far away) down to 0.0 (now), over a window of days. */
    static float progress(long secsLeft, int windowDays) {
        double window = Math.max(1, windowDays) * 86400.0;
        return (float) Math.max(0.0, Math.min(1.0, secsLeft / window));
    }
}
