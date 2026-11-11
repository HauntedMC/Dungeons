package nl.hauntedmc.dungeons.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.config.PluginConfigView;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

/** Presentation and pagination shared by command handlers; no lifecycle or file mutations. */
final class CommandOutput {
    private final DungeonsPlugin plugin;
    private final DungeonRepository dungeonManager;

    CommandOutput(DungeonsPlugin plugin, DungeonRepository dungeonManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
    }

    record FilterPageQuery(String filter, int page) {}

    /** Returns the filtered dungeons. */
    List<DungeonDefinition> getFilteredDungeons(String filter, boolean includeType) {
        List<DungeonDefinition> dungeons = new ArrayList<>();
        for (DungeonDefinition dungeon : this.dungeonManager.getLoadedDungeons()) {
            if (this.matchesDungeonFilter(dungeon, filter, includeType)) {
                dungeons.add(dungeon);
            }
        }

        dungeons.sort(
                (left, right) ->
                        String.CASE_INSENSITIVE_ORDER.compare(
                                left.getWorldName(), right.getWorldName()));
        return dungeons;
    }

    /** Runs matches dungeon filter. */
    boolean matchesDungeonFilter(DungeonDefinition dungeon, String filter, boolean includeType) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        String lowerFilter = filter.toLowerCase(Locale.ROOT);
        if (dungeon.getWorldName().toLowerCase(Locale.ROOT).contains(lowerFilter)) {
            return true;
        }

        String displayName = dungeon.getDisplayName();
        if (displayName != null && displayName.toLowerCase(Locale.ROOT).contains(lowerFilter)) {
            return true;
        }

        return includeType
                && this.getDungeonTypeName(dungeon).toLowerCase(Locale.ROOT).contains(lowerFilter);
    }

    /** Returns the dungeon type name. */
    String getDungeonTypeName(DungeonDefinition dungeon) {
        return dungeon.getConfig().getString("dungeon.type", dungeon.getClass().getSimpleName());
    }

    /** Runs format dungeon difficulties. */
    Component formatDungeonDifficulties(CommandSender audience, DungeonDefinition dungeon) {
        if (!dungeon.isUseDifficultyLevels() || dungeon.getDifficultyLevels().isEmpty()) {
            return LangUtils.component("commands.list.default-difficulty", audience, false);
        }

        List<String> difficultyNames = new ArrayList<>(dungeon.getDifficultyLevels().keySet());
        difficultyNames.sort(String.CASE_INSENSITIVE_ORDER);
        return Component.text(String.join(", ", difficultyNames));
    }

    /** Runs format configured state. */
    Component formatConfiguredState(CommandSender audience, Location location) {
        return LangUtils.component(
                location == null
                        ? "commands.dungeon.info.labels.not-set"
                        : "commands.dungeon.info.labels.set",
                audience,
                false);
    }

    /** Runs format enabled state. */
    Component formatEnabledState(CommandSender audience, boolean enabled) {
        return LangUtils.component(
                enabled
                        ? "commands.dungeon.info.labels.enabled"
                        : "commands.dungeon.info.labels.disabled",
                audience,
                false);
    }

    /** Runs format yes no state. */
    Component formatYesNoState(CommandSender audience, boolean value) {
        return LangUtils.component(
                value ? "general.labels.yes" : "general.labels.no", audience, false);
    }

    /** Runs parse filter page query. */
    FilterPageQuery parseFilterPageQuery(
            CommandSender sender, String[] args, int startIndex, String usageKey) {
        int remainingArgs = args.length - startIndex;
        if (remainingArgs < 0 || remainingArgs > 2) {
            LangUtils.sendMessage(sender, usageKey);
            return null;
        }

        String filter = "";
        int page = 1;

        if (remainingArgs >= 1) {
            String firstArg = args[startIndex];
            if (remainingArgs == 1 && this.isInteger(firstArg)) {
                Optional<Integer> parsed = InputUtils.readIntegerInput(sender, firstArg);
                if (parsed.isEmpty()) {
                    return null;
                }

                page = Math.max(1, parsed.get());
            } else {
                filter = firstArg;
            }
        }

        if (remainingArgs == 2) {
            Optional<Integer> parsed = InputUtils.readIntegerInput(sender, args[startIndex + 1]);
            if (parsed.isEmpty()) {
                return null;
            }

            page = Math.max(1, parsed.get());
        }

        return new FilterPageQuery(filter, page);
    }

    /** Returns whether integer. */
    boolean isInteger(String value) {
        return value != null && value.matches("-?\\d+");
    }

    /** Runs send paginated lines. */
    void sendPaginatedLines(
            CommandSender sender,
            Component headerLine,
            List<Component> lines,
            String noMatchKey,
            String footerKey,
            int requestedPage) {
        LangUtils.sendRaw(sender, headerLine);
        if (lines.isEmpty()) {
            LangUtils.sendMessage(sender, noMatchKey);
            return;
        }

        int pageSize = PluginConfigView.getCommandListPageSize(this.plugin.getConfig());
        int totalPages = Math.max(1, (int) Math.ceil(lines.size() / (double) pageSize));
        int page = Math.clamp(requestedPage, 1, totalPages);
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, lines.size());
        for (int i = startIndex; i < endIndex; i++) {
            LangUtils.sendRaw(sender, lines.get(i));
        }

        LangUtils.sendMessage(
                sender,
                footerKey,
                LangUtils.placeholder("page", String.valueOf(page)),
                LangUtils.placeholder("total_pages", String.valueOf(totalPages)));
    }
}
