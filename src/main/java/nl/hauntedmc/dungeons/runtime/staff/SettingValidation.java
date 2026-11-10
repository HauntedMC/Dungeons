package nl.hauntedmc.dungeons.runtime.staff;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;

import java.util.List;

/** Domain validation supplements parsing; rejected values never reach persistence. */
public final class SettingValidation {
    private SettingValidation() {}

    public static void validate(DungeonDefinition dungeon, String path, Object value) {
        if (path.endsWith("cooldown.period")) {
            String prefix = path.substring(0, path.length() - "period".length());
            String period = String.valueOf(value);
            validateCooldownValue(period, dungeon.getConfig().getInt(prefix + "value", 1));
            validateResetDay(period, dungeon.getConfig().getInt(prefix + "reset_day", 1));
        }
        if (path.endsWith("room_pool")
                && value instanceof List<?> rooms
                && dungeon.asBranching() != null) {
            for (Object room : rooms)
                if (dungeon.asBranching().getRoom(String.valueOf(room)) == null)
                    throw new IllegalArgumentException("Unknown room reference: " + room);
        }
        if (!(value instanceof Number number)) return;
        double n = number.doubleValue();
        if (!Double.isFinite(n)) throw new IllegalArgumentException("Value must be finite");
        if (n < 0) throw new IllegalArgumentException("Value must not be negative");
        if (path.equals("team.max_size") && (n < 1 || n > 1000))
            throw new IllegalArgumentException("Team size must be 1–1000");
        if (path.endsWith("straightness") && n > 1)
            throw new IllegalArgumentException("Straightness must be between 0 and 1");
        if (path.endsWith(".min")) {
            String max = path.substring(0, path.length() - 4) + ".max";
            if (dungeon.getConfig().contains(max) && n > dungeon.getConfig().getDouble(max))
                throw new IllegalArgumentException("Minimum cannot exceed maximum");
        }
        if (path.endsWith(".max")) {
            String min = path.substring(0, path.length() - 4) + ".min";
            if (dungeon.getConfig().contains(min) && n < dungeon.getConfig().getDouble(min))
                throw new IllegalArgumentException("Maximum cannot be below minimum");
        }
        if (path.endsWith("cooldown.value")) {
            String period =
                    dungeon.getConfig().getString(path.replace(".value", ".period"), "DAILY");
            validateCooldownValue(period, n);
        }
        if (path.endsWith("cooldown.reset_day")) {
            String period =
                    dungeon.getConfig().getString(path.replace(".reset_day", ".period"), "DAILY");
            validateResetDay(period, n);
        }
        if ((path.contains("minutes") || path.contains("seconds") || path.contains("ticks"))
                && n > 1000000)
            throw new IllegalArgumentException("Duration exceeds the supported limit");
    }

    private static void validateCooldownValue(String period, double value) {
        if (value < 0 || !period.equalsIgnoreCase("TIMER") && value > 23)
            throw new IllegalArgumentException(
                    "Reset hour must be 0–23; set cooldown.value before changing the cadence");
    }

    private static void validateResetDay(String period, double day) {
        if (day < 1 || day > (period.equalsIgnoreCase("WEEKLY") ? 7 : 31))
            throw new IllegalArgumentException(
                    "Invalid reset day for the selected cadence; set cooldown.reset_day first");
    }
}
