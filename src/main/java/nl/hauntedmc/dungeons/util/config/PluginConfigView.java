package nl.hauntedmc.dungeons.util.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed view helpers for plugin-level configuration values with sane defaults.
 */
public final class PluginConfigView {
    /** Utility class. */
    private PluginConfigView() {}

    /** Returns maximum number of active instances allowed globally. */
    public static int getMaxActiveInstances(FileConfiguration config) {
        return Math.max(0, getInt(config, "instances.max_active",  10));
    }

    /** Returns configured page size for `/dungeons list` output. */
    public static int getCommandListPageSize(FileConfiguration config) {
        return Math.max(1, getInt(config, "commands.list_page_size", 10));
    }

    /** Returns editor open timeout in seconds. */
    public static int getEditOpenTimeoutSeconds(FileConfiguration config) {
        return Math.max(1, getInt(config, "editor.open_timeout_seconds", 10));
    }

    /** Returns editor autosave interval in seconds. */
    public static int getEditAutosaveIntervalSeconds(FileConfiguration config) {
        return Math.max(0, getInt(config, "editor.autosave_interval_seconds", 300));
    }

    /** Returns material name for the function editor tool item. */
    public static String getFunctionToolMaterialName(FileConfiguration config) {
        return getString(config, "editor.tools.function_item", "FEATHER");
    }

    /** Returns hotbar slot index for the function editor tool. */
    public static int getFunctionToolSlot(FileConfiguration config) {
        return sanitizeHotbarSlot(getInt(config, "editor.tools.function_slot", 0), 0);
    }

    /** Returns 1-based display slot for the function editor tool. */
    public static int getFunctionToolDisplaySlot(FileConfiguration config) {
        return getFunctionToolSlot(config) + 1;
    }

    /** Returns material name for the room editor tool item. */
    public static String getRoomToolMaterialName(FileConfiguration config) {
        return getString(config, "editor.tools.room_item", "GOLDEN_AXE");
    }

    /** Returns hotbar slot index for the room editor tool. */
    public static int getRoomToolSlot(FileConfiguration config) {
        int functionSlot = getFunctionToolSlot(config);
        int configuredSlot = sanitizeHotbarSlot(getInt(config, "editor.tools.room_slot", 1), 1);
        if (configuredSlot != functionSlot) {
            return configuredSlot;
        }

        int fallback = functionSlot == 0 ? 1 : 0;
        return sanitizeHotbarSlot(fallback, 1);
    }

    /** Returns 1-based display slot for the room editor tool. */
    public static int getRoomToolDisplaySlot(FileConfiguration config) {
        return getRoomToolSlot(config) + 1;
    }

    /** Returns update interval for editor previews in ticks. */
    public static int getEditorPreviewUpdateIntervalTicks(FileConfiguration config) {
        return Math.max(1, getInt(config, "editor.preview.update_interval_ticks", 20));
    }

    /** Returns whether function marker preview rendering is enabled. */
    public static boolean isFunctionMarkerPreviewEnabled(FileConfiguration config) {
        return getBoolean(config, "editor.preview.function_markers.enabled", true);
    }

    /** Returns visible radius for static function markers in blocks. */
    public static double getStaticFunctionMarkerVisibleRadiusBlocks(FileConfiguration config) {
        return Math.max(
                0.0,
                getDouble(
                        config, "editor.preview.function_markers.static_visible_radius_blocks", 15.0));
    }

    /** Returns visible radius for branching function markers in blocks. */
    public static double getBranchingFunctionMarkerVisibleRadiusBlocks(FileConfiguration config) {
        return Math.max(
                0.0,
                getDouble(
                        config,
                        "editor.preview.function_markers.branching_visible_radius_blocks",
                        10.0));
    }

    /** Returns whether branching room-bounds preview rendering is enabled. */
    public static boolean isBranchingRoomPreviewEnabled(FileConfiguration config) {
        return getBoolean(config, "editor.preview.branching_room_bounds.enabled", true);
    }

    /** Returns visible radius for branching room previews in blocks. */
    public static double getBranchingRoomPreviewVisibleRadiusBlocks(FileConfiguration config) {
        return Math.max(
                0.0,
                getDouble(
                        config, "editor.preview.branching_room_bounds.visible_radius_blocks", 20.0));
    }

    /** Returns generation layout timeout in seconds. */
    public static int getLayoutGenerationTimeoutSeconds(FileConfiguration config) {
        return Math.max(1, getInt(config, "generation.layout_timeout_seconds", 5));
    }

    /** Returns safe-spawn search timeout in seconds. */
    public static int getSafeSpawnSearchTimeoutSeconds(FileConfiguration config) {
        return Math.max(1, getInt(config, "generation.safe_spawn_search_timeout_seconds", 5));
    }

    /** Returns team invite expiration in milliseconds. */
    public static long getTeamInviteExpiryMillis(FileConfiguration config) {
        return Math.max(1L, getInt(config, "team.invite_expiry_seconds", 120)) * 1000L;
    }

    /** Returns expiration for unstarted team instances in ticks. */
    public static long getUnstartedTeamExpiryTicks(FileConfiguration config) {
        return Math.max(0L, getInt(config, "team.unstarted_expiry_seconds", 1800)) * 20L;
    }

    /** Returns countdown warning offsets (seconds) used before forced disband shutdown. */
    public static Set<Integer> getDisbandShutdownWarningSeconds(FileConfiguration config) {
        Set<Integer> defaults = Set.of(60, 30, 10, 5, 4, 3, 2, 1);
        if (config == null || !config.contains("team.disbanded_instance_warning_seconds")) {
            return defaults;
        }

        List<?> values = config.getList("team.disbanded_instance_warning_seconds");
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
    private static int getInt(
            FileConfiguration config, String path, int fallback) {
        if (config == null) {
            return fallback;
        }

        if (config.contains(path)) {
            return config.getInt(path, fallback);
        }

        return fallback;
    }

    /** Reads a double config value with fallback and key-exists guarding. */
    private static double getDouble(
            FileConfiguration config, String path, double fallback) {
        if (config == null) {
            return fallback;
        }

        if (config.contains(path)) {
            return config.getDouble(path, fallback);
        }

        return fallback;
    }

    /** Reads a boolean config value with fallback and key-exists guarding. */
    private static boolean getBoolean(
            FileConfiguration config, String path, boolean fallback) {
        if (config == null) {
            return fallback;
        }

        if (config.contains(path)) {
            return config.getBoolean(path, fallback);
        }

        return fallback;
    }

    /** Reads a trimmed non-blank string config value with fallback. */
    private static String getString(
            FileConfiguration config, String path, String fallback) {
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

    /** Parses an integer from number or string values. */
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

    /** Clamps configured hotbar slot to valid range [0, 8]. */
    private static int sanitizeHotbarSlot(int slot, int fallback) {
        return slot >= 0 && slot <= 8 ? slot : fallback;
    }
}
