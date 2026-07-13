package nl.hauntedmc.dungeons.util.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed view helpers for per-dungeon configuration values with bundled-default fallbacks.
 */
public final class DungeonConfigView {
    /** Utility class. */
    private DungeonConfigView() {}

    /** Returns whether the dungeon lobby phase is enabled. */
    public static boolean isLobbyEnabled(FileConfiguration config) {
        return getBoolean(config, "locations.lobby.enabled", false);
    }

    /** Returns the delayed offline-kick timeout in seconds. */
    public static int getOfflineKickDelaySeconds(FileConfiguration config) {
        return Math.max(0, getInt(config, "players.offline_kick.delay_seconds", 300));
    }

    /** Returns whether potion effects are preserved on entry. */
    public static boolean shouldKeepPotionEffectsOnEntry(FileConfiguration config) {
        return getBoolean(config, "players.keep_on_entry.potion_effects", true);
    }

    /** Returns the shutdown grace period after a team disbands. */
    public static int getTeamDisbandShutdownDelaySeconds(FileConfiguration config) {
        return Math.max(0, getInt(config, "team.disband_shutdown_delay_seconds", 180));
    }

    /** Returns the idle unload delay for open instances in ticks. */
    public static int getOpenEmptyUnloadDelayTicks(FileConfiguration config) {
        return Math.max(0, getInt(config, "open.empty_unload_delay_ticks", 6000));
    }

    /** Returns whether explosion block damage is prevented in this dungeon. */
    public static boolean isExplosionBlockDamagePrevented(FileConfiguration config) {
        return getBoolean(config, "rules.world.prevent_explosion_block_damage", false);
    }

    /** Returns whether natural mob spawning is enabled in this dungeon. */
    public static boolean isNaturalMobSpawningEnabled(FileConfiguration config) {
        return getBoolean(config, "rules.spawning.natural_mobs", true);
    }

    /** Returns whether passive animal spawning is enabled in this dungeon. */
    public static boolean isAnimalSpawningEnabled(FileConfiguration config) {
        return getBoolean(config, "rules.spawning.animals", true);
    }

    /** Returns whether hostile monster spawning is enabled in this dungeon. */
    public static boolean isMonsterSpawningEnabled(FileConfiguration config) {
        return getBoolean(config, "rules.spawning.monsters", true);
    }

    /** Returns whether block breaking is allowed in this dungeon. */
    public static boolean canBreakBlocks(FileConfiguration config) {
        return getBoolean(config, "rules.building.break_blocks", true);
    }

    /** Returns whether block placement is allowed in this dungeon. */
    public static boolean canPlaceBlocks(FileConfiguration config) {
        return getBoolean(config, "rules.building.place_blocks", true);
    }

    /** Returns whether bucket use is allowed in this dungeon. */
    public static boolean canUseBuckets(FileConfiguration config) {
        return getBoolean(config, "rules.movement.buckets", true);
    }

    /** Returns whether ender pearl use is allowed in this dungeon. */
    public static boolean canUseEnderPearls(FileConfiguration config) {
        return getBoolean(config, "rules.movement.ender_pearls", true);
    }

    /** Returns whether armor durability loss is prevented in this dungeon. */
    public static boolean isArmorDurabilityLossPrevented(FileConfiguration config) {
        return getBoolean(config, "rules.combat.prevent_durability_loss.armor", false);
    }

    /** Returns whether weapon durability loss is prevented in this dungeon. */
    public static boolean isWeaponDurabilityLossPrevented(FileConfiguration config) {
        return getBoolean(config, "rules.combat.prevent_durability_loss.weapons", false);
    }

    /** Returns whether tool durability loss is prevented in this dungeon. */
    public static boolean isToolDurabilityLossPrevented(FileConfiguration config) {
        return getBoolean(config, "rules.combat.prevent_durability_loss.tools", false);
    }

    /** Returns title fade-in ticks used when showing the start title. */
    public static int getStartTitleFadeInTicks(FileConfiguration config) {
        return Math.max(0, getInt(config, "dungeon.start_title.fade_in_ticks", 10));
    }

    /** Returns title stay ticks used when showing the start title. */
    public static int getStartTitleStayTicks(FileConfiguration config) {
        return Math.max(0, getInt(config, "dungeon.start_title.stay_ticks", 70));
    }

    /** Returns title fade-out ticks used when showing the start title. */
    public static int getStartTitleFadeOutTicks(FileConfiguration config) {
        return Math.max(0, getInt(config, "dungeon.start_title.fade_out_ticks", 10));
    }

    /** Returns whether access cooldowns apply when a player leaves early. */
    public static boolean isAccessCooldownAppliedOnLeave(FileConfiguration config) {
        return getBoolean(config, "access.cooldown.on_leave", true);
    }

    /** Returns access cooldown cadence name. */
    public static String getAccessCooldownPeriodName(FileConfiguration config) {
        return getString(config, "access.cooldown.period", "TIMER");
    }

    /** Returns access cooldown amount or reset hour. */
    public static int getAccessCooldownValue(FileConfiguration config) {
        return Math.max(0, getInt(config, "access.cooldown.value", 60));
    }

    /** Returns access cooldown reset day for weekly and monthly cooldowns. */
    public static int getAccessCooldownResetDay(FileConfiguration config) {
        return Math.max(1, getInt(config, "access.cooldown.reset_day", 1));
    }

    /** Returns countdown warning offsets in seconds used before a time-limited run expires. */
    public static Set<Integer> getTimeLimitWarningSeconds(FileConfiguration config) {
        Set<Integer> defaults = Set.of(600, 300, 60);
        if (config == null || !config.contains("runs.time_limit_warning_seconds")) {
            return defaults;
        }

        List<?> values = config.getList("runs.time_limit_warning_seconds");
        if (values == null || values.isEmpty()) {
            return defaults;
        }

        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (Object value : values) {
            Integer parsed = parseInteger(value);
            if (parsed != null && parsed > 0) {
                result.add(parsed);
            }
        }

        return result.isEmpty() ? defaults : Set.copyOf(result);
    }

    /** Reads an integer config value with fallback and key-exists guarding. */
    private static int getInt(FileConfiguration config, String path, int fallback) {
        if (config == null) {
            return fallback;
        }

        if (config.contains(path)) {
            return config.getInt(path, fallback);
        }

        return fallback;
    }

    /** Reads a boolean config value with fallback and key-exists guarding. */
    private static boolean getBoolean(FileConfiguration config, String path, boolean fallback) {
        if (config == null) {
            return fallback;
        }

        if (config.contains(path)) {
            return config.getBoolean(path, fallback);
        }

        return fallback;
    }

    /** Reads a trimmed non-blank string config value with fallback. */
    private static String getString(FileConfiguration config, String path, String fallback) {
        if (config == null) {
            return fallback;
        }

        String value = null;
        if (config.contains(path)) {
            value = config.getString(path);
        }

        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }

    /** Attempts to coerce a config list element into an integer. */
    private static Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }
}
