package nl.hauntedmc.dungeons.command;

import java.util.List;
import java.util.Locale;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import org.bukkit.command.CommandSender;

/** Shared catalogue discovery and scoped editor completion policy for command branches. */
final class DungeonCompletions {
    private DungeonCompletions() {}

    /** Adds matching dungeon names. */
    static void addMatchingDungeonNames(
            DungeonRepository dungeonManager, List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (DungeonDefinition dungeon : dungeonManager.getLoadedDungeons()) {
            String namespace = dungeon.getWorldName().toLowerCase(Locale.ROOT);
            if (namespace.contains(lowerQuery)) {
                options.add(dungeon.getWorldName());
            }
        }
    }

    /** Adds matching editable dungeon names. */
    static void addMatchingEditableDungeonNames(
            DungeonRepository dungeonManager,
            CommandSender sender,
            List<String> options,
            String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (DungeonDefinition dungeon : dungeonManager.getLoadedDungeons()) {
            if (!CommandUtils.hasDungeonEditAccess(sender, dungeon.getWorldName())) {
                continue;
            }

            String namespace = dungeon.getWorldName().toLowerCase(Locale.ROOT);
            if (namespace.contains(lowerQuery)) {
                options.add(dungeon.getWorldName());
            }
        }
    }

    /** Adds matching play difficulty options. */
    static void addMatchingPlayDifficultyOptions(
            DungeonRepository dungeonManager, List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (DungeonDefinition dungeon : dungeonManager.getLoadedDungeons()) {
            if (!dungeon.isUseDifficultyLevels()) {
                continue;
            }

            for (String difficulty : dungeon.getDifficultyLevels().keySet()) {
                String option = dungeon.getWorldName() + ":" + difficulty;
                if (option.toLowerCase(Locale.ROOT).contains(lowerQuery)
                        && !options.contains(option)) {
                    options.add(option);
                }
            }
        }
    }
}
