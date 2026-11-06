package nl.hauntedmc.dungeons.util.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Configuration tree synchronization helpers for template-to-target merges.
 */
public final class ConfigSyncUtils {
    /** Utility class. */
    private ConfigSyncUtils() {}

    /**
     * Recursively adds missing keys from the template and removes keys that no longer exist there.
     * Existing values are preserved unless the node type changed between versions.
     */
    public static boolean syncMissingAndObsolete(
            ConfigurationSection template, ConfigurationSection target) {
        return syncMissingAndObsolete(template, target, Set.of());
    }

    /** Synchronizes a tree while leaving the specified top-level keys untouched. */
    public static boolean syncMissingAndObsolete(
            ConfigurationSection template, ConfigurationSection target, Set<String> excludedKeys) {
        if (template == null || target == null) {
            return false;
        }

        boolean changed = false;

        for (String key : new ArrayList<>(target.getKeys(false))) {
            if (excludedKeys.contains(key)) {
                continue;
            }
            if (!template.contains(key)) {
                if (target.isConfigurationSection(key)) {
                    continue;
                }
                target.set(key, null);
                changed = true;
            }
        }

        for (String key : template.getKeys(false)) {
            if (excludedKeys.contains(key)) {
                continue;
            }
            Object templateValue = template.get(key);
            if (templateValue instanceof ConfigurationSection templateSection) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    if (target.contains(key)) {
                        target.set(key, null);
                    }

                    targetSection = target.createSection(key);
                    changed = true;
                }

                changed |= syncMissingAndObsolete(templateSection, targetSection);
                continue;
            }

            if (templateValue == null) {
                if (!target.contains(key)) {
                    target.set(key, null);
                    changed = true;
                }
                continue;
            }

            if (!target.contains(key) || target.isConfigurationSection(key)) {
                target.set(key, copyValue(templateValue));
                changed = true;
            }
        }

        return changed;
    }

    /** Creates shallow copies for mutable list/map values. */
    private static Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }

        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(map);
        }

        return value;
    }
}
