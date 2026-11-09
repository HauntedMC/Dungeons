package nl.hauntedmc.dungeons.command;

import nl.hauntedmc.dungeons.command.CommandOutput.FilterPageQuery;
import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.gui.loot.LootTableEditor;
import nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Owns the loot command branch and its completions. */
final class LootCommandHandler implements CommandHandler {
    private final LootTableRepository lootTableManager;
    private final LootTableEditor editor;
    private final CommandOutput output;

    LootCommandHandler(
            LootTableRepository lootTableManager, LootTableEditor editor, CommandOutput output) {
        this.lootTableManager = lootTableManager;
        this.editor = editor;
        this.output = output;
    }

    /** Runs handle command. */
    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 3 && args[1].equalsIgnoreCase("info")) {
            nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.require(sender, "loot", null);
            var table = this.lootTableManager.get(args[2]);
            if (table == null) throw new IllegalArgumentException("Loot table not found");
            sender.sendMessage("[Dungeons] " + table.getNamespace() + ": " + table.getMinItems() + "–" + table.getMaxItems() + " items");
            return true;
        }
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.staff.loot")) {
            return true;
        }

        if (args.length < 2) {
            LangUtils.sendMessage(player, "commands.loot.usage");
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> this.handleLootCreate(player, args);
            case "edit" -> this.handleLootEdit(player, args);
            case "list" -> this.handleLootList(player, CommandArguments.shiftArgs(args));
            default -> {
                LangUtils.sendMessage(player, "commands.loot.usage");
                yield true;
            }
        };
    }

    /** Runs handle tab complete. */
    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2) {
            CommandArguments.addMatchingLiteralOptions(options, args[1], "create", "edit", "list");
            return options;
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("list")) {
            this.addMatchingLootTables(options, args[2]);
        } else if (args.length == 3 && !args[1].equalsIgnoreCase("create")) {
            this.addMatchingLootTables(options, args[2]);
        } else if (args.length == 4 && args[1].equalsIgnoreCase("list")) {
            CommandArguments.addMatchingLiteralOptions(options, args[3], "1", "2", "3", "4", "5");
        }

        return options;
    }

    /** Runs handle loot create. */
    private boolean handleLootCreate(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.loot.create.usage");
            return true;
        }

        LootTable table = this.lootTableManager.get(args[2]);
        if (table != null) {
            LangUtils.sendMessage(
                    player,
                    "commands.loot.create.already-exists",
                    LangUtils.placeholder("loot_table", args[2]));
            return true;
        }

        LootTable created = new LootTable(args[2]);
        if (!this.lootTableManager.put(created)) {
            LangUtils.sendMessage(player, "commands.loot.save-failed");
            return true;
        }
        this.editor.open(created, player);
        LangUtils.sendMessage(
                player,
                "commands.loot.create.success",
                LangUtils.placeholder("loot_table", args[2]));

        return true;
    }

    /** Runs handle loot edit. */
    private boolean handleLootEdit(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.loot.edit.usage");
            return true;
        }

        LootTable table = this.lootTableManager.get(args[2]);
        if (table == null) {
            LangUtils.sendMessage(
                    player,
                    "commands.loot.edit.not-found",
                    LangUtils.placeholder("loot_table", args[2]));
            return true;
        }

        if (this.editor.getEditor(table) != null) {
            LangUtils.sendMessage(player, "commands.loot.edit.already-editing");
            return true;
        }

        if (!this.lootTableManager.isWritable()) {
            LangUtils.sendMessage(player, "commands.loot.save-failed");
            return true;
        }
        this.editor.open(table, player);

        return true;
    }

    /** Runs handle loot list. */
    private boolean handleLootList(Player player, String[] args) {
        FilterPageQuery query =
                this.output.parseFilterPageQuery(player, args, 1, "commands.loot.list.usage");
        if (query == null) {
            return true;
        }

        List<LootTable> tables = new ArrayList<>(this.lootTableManager.getTables());
        tables.sort(
                (left, right) ->
                        String.CASE_INSENSITIVE_ORDER.compare(
                                left.getNamespace(), right.getNamespace()));

        List<String> lines = new ArrayList<>();
        String filter = query.filter().toLowerCase(Locale.ROOT);
        for (LootTable table : tables) {
            if (!filter.isBlank()
                    && !table.getNamespace().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }

            Player activeEditor = this.editor.getEditor(table);
            String editor =
                    activeEditor == null
                            ? LangUtils.getMessage("commands.loot.list.no-editor", false)
                            : activeEditor.getName();
            lines.add(
                    LangUtils.getMessage(
                            "commands.loot.list.line",
                            false,
                            LangUtils.placeholder("loot_table", table.getNamespace()),
                            LangUtils.placeholder("min", String.valueOf(table.getMinItems())),
                            LangUtils.placeholder("max", String.valueOf(table.getMaxItems())),
                            LangUtils.placeholder(
                                    "duplicates",
                                    this.output.formatYesNoState(table.isAllowDuplicates())),
                            LangUtils.placeholder("editor", editor)));
        }

        this.output.sendPaginatedLines(
                player,
                LangUtils.getMessage("commands.loot.list.header", false),
                lines,
                "commands.loot.list.no-match",
                "commands.loot.list.footer",
                query.page());
        return true;
    }

    /** Adds matching loot tables. */
    private void addMatchingLootTables(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (LootTable table : this.lootTableManager.getTables()) {
            String namespace = table.getNamespace().toLowerCase(Locale.ROOT);
            if (namespace.contains(lowerQuery)) {
                options.add(table.getNamespace());
            }
        }
    }
}
