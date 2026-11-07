package nl.hauntedmc.dungeons.command;

import nl.hauntedmc.dungeons.model.dungeon.AccessKeyDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeam;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.TextUtils;
import nl.hauntedmc.dungeons.util.time.TimeUtils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    String formatDungeonDifficulties(DungeonDefinition dungeon) {
        if (!dungeon.isUseDifficultyLevels() || dungeon.getDifficultyLevels().isEmpty()) {
            return LangUtils.getMessage("commands.list.default-difficulty", false);
        }

        List<String> difficultyNames = new ArrayList<>(dungeon.getDifficultyLevels().keySet());
        difficultyNames.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", difficultyNames);
    }

    /** Runs format configured state. */
    String formatConfiguredState(Location location) {
        return LangUtils.getMessage(
                location == null
                        ? "commands.dungeon.info.labels.not-set"
                        : "commands.dungeon.info.labels.set",
                false);
    }

    /** Runs format enabled state. */
    String formatEnabledState(boolean enabled) {
        return LangUtils.getMessage(
                enabled
                        ? "commands.dungeon.info.labels.enabled"
                        : "commands.dungeon.info.labels.disabled",
                false);
    }

    /** Runs format yes no state. */
    String formatYesNoState(boolean value) {
        return LangUtils.getMessage(
                value ? "commands.player.status.labels.yes" : "commands.player.status.labels.no",
                false);
    }

    /** Runs format queue status. */
    String formatQueueStatus(Player targetPlayer, DungeonQueueEntry queue) {
        if (queue == null) {
            return LangUtils.getMessage("commands.player.status.labels.none", false);
        }

        String modeKey;
        if (!queue.isTeamQueue()) {
            modeKey = "commands.player.status.queue.modes.solo";
        } else if (queue.isLeader(targetPlayer.getUniqueId())) {
            modeKey = "commands.player.status.queue.modes.team-leader";
        } else {
            modeKey = "commands.player.status.queue.modes.team-member";
        }

        return LangUtils.getMessage(
                "commands.player.status.queue.entry",
                false,
                LangUtils.placeholder("dungeon", queue.getDungeon().getWorldName()),
                LangUtils.placeholder(
                        "difficulty", this.formatDifficultyValue(queue.getDifficulty())),
                LangUtils.placeholder("mode", LangUtils.getMessage(modeKey, false)));
    }

    /** Runs format team status. */
    String formatTeamStatus(Player targetPlayer, DungeonTeam team) {
        if (team == null) {
            return LangUtils.getMessage("commands.player.status.labels.none", false);
        }

        String stateKey =
                team.isStarted()
                        ? "commands.player.status.team.states.running"
                        : team.isQueued()
                                ? "commands.player.status.team.states.queued"
                                : "commands.player.status.team.states.idle";
        String state = LangUtils.getMessage(stateKey, false);
        if (team.isLeader(targetPlayer.getUniqueId())) {
            return LangUtils.getMessage(
                    "commands.player.status.team.as-leader",
                    false,
                    LangUtils.placeholder("count", String.valueOf(team.size())),
                    LangUtils.placeholder("state", state));
        }

        Player leader = Bukkit.getPlayer(team.getLeaderId());
        String leaderName = leader == null ? team.getLeaderId().toString() : leader.getName();
        return LangUtils.getMessage(
                "commands.player.status.team.as-member",
                false,
                LangUtils.placeholder("leader", leaderName),
                LangUtils.placeholder("count", String.valueOf(team.size())),
                LangUtils.placeholder("state", state));
    }

    /** Runs format reserved key status. */
    String formatReservedKeyStatus(DungeonPlayerSession targetSession) {
        if (targetSession == null || !targetSession.hasReservedAccessKey()) {
            return LangUtils.getMessage("commands.player.status.labels.none", false);
        }

        String reservedDungeon = targetSession.getReservedAccessKeyDungeon();
        return reservedDungeon == null || reservedDungeon.isBlank()
                ? LangUtils.getMessage("commands.player.status.labels.none", false)
                : reservedDungeon;
    }

    /** Runs format difficulty value. */
    String formatDifficultyValue(String difficulty) {
        return difficulty == null || difficulty.isBlank()
                ? LangUtils.getMessage("commands.player.status.labels.default-difficulty", false)
                : difficulty;
    }

    /** Runs format access key line. */
    String formatAccessKeyLine(int keyId, AccessKeyDefinition accessKey) {
        ItemStack item = accessKey.createItemCopy();
        String itemName =
                item == null
                        ? LangUtils.getMessage("commands.dungeon.keys.list.labels.unknown", false)
                        : ItemUtils.getItemDisplayName(item);
        String material =
                item == null
                        ? LangUtils.getMessage("commands.dungeon.keys.list.labels.unknown", false)
                        : TextUtils.humanize(item.getType().name());
        String amount = item == null ? "0" : String.valueOf(item.getAmount());
        return LangUtils.getMessage(
                "commands.dungeon.keys.list.line",
                false,
                LangUtils.placeholder("id", String.valueOf(keyId)),
                LangUtils.placeholder("item", itemName),
                LangUtils.placeholder("material", material),
                LangUtils.placeholder("amount", amount),
                LangUtils.placeholder("added_at", this.formatAccessKeyAddedAt(accessKey)),
                LangUtils.placeholder("added_by", this.formatAccessKeyAddedBy(accessKey)));
    }

    /** Runs format access key added at. */
    String formatAccessKeyAddedAt(AccessKeyDefinition accessKey) {
        Date addedAt = accessKey.getAddedAt();
        if (addedAt == null) {
            return LangUtils.getMessage("commands.dungeon.keys.list.labels.unknown", false);
        }

        return TimeUtils.formatDate(addedAt);
    }

    /** Runs format access key added by. */
    String formatAccessKeyAddedBy(AccessKeyDefinition accessKey) {
        String addedBy = accessKey.getAddedByName();
        if (addedBy == null || addedBy.isBlank()) {
            return LangUtils.getMessage("commands.dungeon.keys.list.labels.unknown", false);
        }

        return addedBy;
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
            String headerLine,
            List<String> lines,
            String noMatchKey,
            String footerKey,
            int requestedPage) {
        sender.sendMessage(headerLine);
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
            sender.sendMessage(lines.get(i));
        }

        sender.sendMessage(
                LangUtils.getMessage(
                        footerKey,
                        false,
                        LangUtils.placeholder("page", String.valueOf(page)),
                        LangUtils.placeholder("total_pages", String.valueOf(totalPages))));
    }
}
