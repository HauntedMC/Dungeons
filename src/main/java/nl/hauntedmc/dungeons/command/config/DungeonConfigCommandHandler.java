package nl.hauntedmc.dungeons.command.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.reward.CooldownPeriod;
import nl.hauntedmc.dungeons.gui.menu.PlayMenus;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Command handler for {@code /dungeon config}.
 *
 * <p>The handler resolves a target dungeon, looks up the referenced setting in
 * {@link DungeonConfigRegistry}, validates user input based on the setting's declared
 * {@link DungeonConfigValueType}, and persists the result back into the correct config file.</p>
 */
public final class DungeonConfigCommandHandler {
    private final DungeonRepository dungeonManager;
    private final PlayerSessionRegistry playerManager;
    private final ActiveInstanceRegistry activeInstanceManager;

    /**
     * Creates the config command handler against the runtime services it needs.
     */
    public DungeonConfigCommandHandler(DungeonRepository dungeonManager, PlayerSessionRegistry playerManager,
            ActiveInstanceRegistry activeInstanceManager) {
        this.dungeonManager = dungeonManager;
        this.playerManager = playerManager;
        this.activeInstanceManager = activeInstanceManager;
    }

    /**
     * Handles {@code /dungeon config} command execution.
     */
    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasAnyDungeonEditAccess(sender)) {
            LangUtils.sendMessage(sender, "general.errors.no-permission");
            return true;
        }

        if (args.length < 2) {
            this.sendUsage(sender);
            return true;
        }

        DungeonDefinition dungeon = this.resolveDungeon(sender, args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(sender, "commands.edit.open.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!this.hasEditAccess(sender, dungeon)) {
            LangUtils.sendMessage(sender, "commands.edit.open.no-permission");
            return true;
        }

        if (args.length == 2) {
            this.listSettings(sender, dungeon, null);
            return true;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("help")) {
            this.sendUsage(sender);
            return true;
        }

        if (action.equals("list")) {
            this.listSettings(sender, dungeon, args.length >= 4 ? this.joinArgs(args, 3) : null);
            return true;
        }

        DungeonConfigEntry entry = DungeonConfigRegistry.resolve(dungeon, action);
        if (entry == null || !entry.supports(dungeon)) {
            LangUtils.sendMessage(sender, "commands.config.unknown-setting", LangUtils.placeholder("setting", args[2]),
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            return true;
        }

        if (args.length == 3) {
            this.showSetting(sender, dungeon, entry);
            return true;
        }

        return this.applySetting(sender, dungeon, entry, this.joinArgs(args, 3));
    }

    /**
     * Produces tab completions for {@code /dungeon config}.
     */
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (!CommandUtils.hasAnyDungeonEditAccess(sender)) {
            return options;
        }

        if (args.length <= 1) {
            return this.completeDungeonTarget(sender, "");
        }

        if (args.length == 2) {
            return this.completeDungeonTarget(sender, args[1]);
        }

        DungeonDefinition dungeon = this.resolveDungeon(sender, args[1]);
        if (dungeon == null) {
            return options;
        }

        if (args.length == 3) {
            options.add("help");
            options.add("list");
            options.addAll(DungeonConfigRegistry.getKeys(dungeon, args[2]));
            return options;
        }

        DungeonConfigEntry entry = DungeonConfigRegistry.resolve(dungeon, args[2]);
        if (entry == null || !entry.supports(dungeon)) {
            return options;
        }

        return this.completeValue(entry, dungeon, this.joinArgs(args, 3));
    }

    /**
     * Runs complete dungeon target.
     */
    private List<String> completeDungeonTarget(CommandSender sender, String rawQuery) {
        List<String> options = new ArrayList<>();
        String query = rawQuery == null ? "" : rawQuery.toLowerCase(Locale.ROOT);

        if (sender instanceof Player player && ("current".contains(query) || query.isBlank())) {
            DungeonDefinition currentDungeon = this.resolveDungeon(player, "current");
            if (currentDungeon != null && this.hasEditAccess(sender, currentDungeon)) {
                options.add("current");
            }
        }

        for (DungeonDefinition dungeon : this.dungeonManager.getLoadedDungeons()) {
            if ((query.isBlank() || dungeon.getWorldName().toLowerCase(Locale.ROOT).contains(query))
                    && this.hasEditAccess(sender, dungeon)) {
                options.add(dungeon.getWorldName());
            }
        }

        return options;
    }

    /**
     * Applies setting.
     */
    private boolean applySetting(CommandSender sender, DungeonDefinition dungeon, DungeonConfigEntry entry,
            String rawValue) {
        rawValue = rawValue == null ? "" : rawValue.trim();

        // Branch creation and deletion mutate a structured subsection rather than a single scalar
        // config value, so they bypass the generic value-type parser below.
        if (entry.getConfigFile() == DungeonConfigFile.GENERATION) {
            if (entry.getKey().equals("branch.add")) {
                return this.addBranch(sender, dungeon, entry, rawValue);
            }
            if (entry.getKey().equals("branch.remove")) {
                return this.removeBranch(sender, dungeon, entry, rawValue);
            }
        }

        switch (entry.getValueType()) {
            case BOOLEAN -> {
                Boolean parsed = this.parseBoolean(rawValue);
                if (parsed == null) {
                    return this.sendInvalidValue(sender, entry, "Expected true or false.");
                }
                return this.persistValue(sender, dungeon, entry, parsed);
            }
            case INTEGER -> {
                Integer parsed = this.parseInteger(rawValue);
                if (parsed == null) {
                    return this.sendInvalidValue(sender, entry, "Expected a whole number.");
                }
                return this.persistValue(sender, dungeon, entry, parsed);
            }
            case DECIMAL -> {
                Double parsed = this.parseDouble(rawValue);
                if (parsed == null) {
                    return this.sendInvalidValue(sender, entry, "Expected a decimal number.");
                }
                return this.persistValue(sender, dungeon, entry, parsed);
            }
            case MATERIAL -> {
                Material parsed = this.parseMaterial(rawValue);
                if (parsed == null) {
                    return this.sendInvalidValue(sender, entry, "Expected a valid Bukkit material.");
                }
                return this.persistValue(sender, dungeon, entry, parsed.name());
            }
            case MATERIAL_LIST -> {
                List<String> parsed = this.parseMaterialList(rawValue);
                if (parsed == null) {
                    return this.sendInvalidValue(sender, entry,
                            "Expected a comma separated list of valid Bukkit materials, or clear.");
                }
                return this.persistValue(sender, dungeon, entry, parsed);
            }
            case ENTITY_TYPE_LIST -> {
                List<String> parsed = this.parseEntityTypeList(rawValue);
                if (parsed == null) {
                    return this.sendInvalidValue(sender, entry,
                            "Expected a comma separated list of valid Bukkit entity types, or clear.");
                }
                return this.persistValue(sender, dungeon, entry, parsed);
            }
            case STRING_LIST -> {
                return this.persistValue(sender, dungeon, entry, this.parseStringList(rawValue));
            }
            case GAMEMODE -> {
                GameMode parsed = this.parseGameMode(rawValue);
                if (parsed == null) {
                    return this.sendInvalidValue(sender, entry, "Expected a valid Bukkit gamemode.");
                }
                return this.persistValue(sender, dungeon, entry, parsed.name());
            }
            case WORLD_ENVIRONMENT -> {
                Environment parsed = this.parseEnvironment(rawValue);
                if (parsed == null) {
                    return this.sendInvalidValue(sender, entry,
                            "Expected NORMAL, NETHER, THE_END, or another valid environment.");
                }
                return this.persistValue(sender, dungeon, entry, parsed.name());
            }
            case COOLDOWN_PERIOD -> {
                CooldownPeriod parsed = this.parseCooldownPeriod(rawValue);
                if (parsed == null) {
                    return this.sendInvalidValue(sender, entry,
                            "Expected one of: TIMER, HOURLY, DAILY, WEEKLY, MONTHLY.");
                }
                return this.persistValue(sender, dungeon, entry, parsed.name());
            }
            case LOCATION -> {
                return this.applyLocationSetting(sender, dungeon, entry, rawValue);
            }
            case LAYOUT -> {
                String parsed = rawValue.toLowerCase(Locale.ROOT);
                boolean valid = entry.getSuggestions(dungeon).stream()
                        .anyMatch(candidate -> candidate.equalsIgnoreCase(parsed));
                if (!valid) {
                    return this.sendInvalidValue(sender, entry,
                            "Expected one of: " + String.join(", ", entry.getSuggestions(dungeon)) + ".");
                }
                return this.persistValue(sender, dungeon, entry, parsed);
            }
            case STRING, COLOR_STRING, CHUNK_GENERATOR -> {
                if (rawValue.isBlank()) {
                    return this.sendInvalidValue(sender, entry, "This setting needs a value.");
                }
                return this.persistValue(sender, dungeon, entry, rawValue);
            }
            default -> {
                return this.sendInvalidValue(sender, entry,
                        "This setting type is not supported by the command handler.");
            }
        }
    }

    /**
     * Applies location setting.
     */
    private boolean applyLocationSetting(CommandSender sender, DungeonDefinition dungeon, DungeonConfigEntry entry,
            String rawValue) {
        if (!(sender instanceof Player player)) {
            LangUtils.sendMessage(sender, "commands.config.location-player-only");
            return true;
        }

        String lowered = rawValue.toLowerCase(Locale.ROOT);
        if (!lowered.equals("here") && !(entry.getKey().equals("exit-location") && lowered.equals("clear"))) {
            return this.sendInvalidValue(sender, entry, "Use here, or clear for exit-location.");
        }

        switch (entry.getKey()) {
            case "lobby-location" -> {
                dungeon.setLobbySpawn(player.getLocation());
                this.sendUpdated(sender, dungeon, entry, this.formatLocation(dungeon.getLobbySpawn()), true);
                return true;
            }
            case "start-location" -> {
                dungeon.setStartSpawn(player.getLocation());
                this.sendUpdated(sender, dungeon, entry, this.formatLocation(dungeon.getStartSpawn()), true);
                return true;
            }
            case "exit-location" -> {
                if (lowered.equals("clear")) {
                    dungeon.setExit(null);
                    this.sendUpdated(sender, dungeon, entry, "<cleared>", true);
                } else {
                    dungeon.setExit(player.getLocation());
                    this.sendUpdated(sender, dungeon, entry, this.formatLocation(dungeon.getExitLoc()), true);
                }
                return true;
            }
        }

        return this.sendInvalidValue(sender, entry, "Unknown location target.");
    }

    /**
     * Adds branch.
     */
    private boolean addBranch(CommandSender sender, DungeonDefinition dungeon, DungeonConfigEntry entry,
            String rawValue) {
        BranchingDungeon branching = dungeon.asBranching();
        if (branching == null) {
            return this.sendInvalidValue(sender, entry, "This setting only exists on branching dungeons.");
        }

        String branchId = this.normalizeBranchId(rawValue);
        if (branchId == null) {
            return this.sendInvalidValue(sender, entry, "Use a branch id with letters, numbers, '-' or '_'.");
        }

        String path = "branching.branches." + branchId;
        if (branching.getGenConfig().isConfigurationSection(path)) {
            return this.sendInvalidValue(sender, entry, "That branch already exists.");
        }

        ConfigurationSection branchSection = branching.getGenConfig().createSection(path);
        branchSection.set("room_pool", new ArrayList<String>());
        branchSection.set("end_room_pool", new ArrayList<String>());
        branchSection.set("straightness", 0.5D);
        branchSection.set("room_target.min", 6);
        branchSection.set("room_target.max", 12);
        branchSection.set("room_target.enforce_minimum", true);
        branchSection.set("eligible_depth", "0+");
        branchSection.set("occurrences", "0-1");
        branchSection.set("enforce_occurrences", true);
        branching.saveGenerationConfig();
        branching.reloadGenerationSettings();
        this.sendUpdated(sender, dungeon, entry, branchId, false);
        return true;
    }

    /**
     * Removes branch.
     */
    private boolean removeBranch(CommandSender sender, DungeonDefinition dungeon, DungeonConfigEntry entry,
            String rawValue) {
        BranchingDungeon branching = dungeon.asBranching();
        if (branching == null) {
            return this.sendInvalidValue(sender, entry, "This setting only exists on branching dungeons.");
        }

        String branchId = this.normalizeBranchId(rawValue);
        if (branchId == null) {
            return this.sendInvalidValue(sender, entry, "Use an existing branch id.");
        }

        String path = "branching.branches." + branchId;
        if (!branching.getGenConfig().isConfigurationSection(path)) {
            return this.sendInvalidValue(sender, entry, "That branch does not exist.");
        }

        branching.getGenConfig().set(path, null);
        branching.saveGenerationConfig();
        branching.reloadGenerationSettings();
        this.sendUpdated(sender, dungeon, entry, branchId, false);
        return true;
    }

    /**
     * Runs persist value.
     */
    private boolean persistValue(CommandSender sender, DungeonDefinition dungeon, DungeonConfigEntry entry,
            Object value) {
        if (entry.getConfigFile() == DungeonConfigFile.MAIN) {
            // Main config changes update the live dungeon definition immediately because those values
            // are read directly by the runtime model.
            dungeon.getConfig().set(entry.getPath(), value);
            dungeon.saveConfig();
            dungeon.refreshRuntimeSettings();
            if (entry.getPath().startsWith("difficulty.")) {
                PlayMenus.initializeDifficultySelector(dungeon);
            }
            this.sendUpdated(sender, dungeon, entry, this.stringifyValue(value), false);
            return true;
        }

        BranchingDungeon branching = dungeon.asBranching();
        if (branching == null) {
            return this.sendInvalidValue(sender, entry, "This setting only exists on branching dungeons.");
        }

        branching.getGenConfig().set(entry.getPath(), value);
        branching.saveGenerationConfig();
        // Generation settings are reloaded on the branching model rather than the main dungeon
        // runtime settings because they affect future room/layout generation behavior.
        branching.reloadGenerationSettings();
        this.sendUpdated(sender, dungeon, entry, this.stringifyValue(value), false);
        return true;
    }

    /**
     * Runs send updated.
     */
    private void sendUpdated(CommandSender sender, DungeonDefinition dungeon, DungeonConfigEntry entry, String value,
            boolean immediate) {
        LangUtils.sendMessage(sender, "commands.config.updated", LangUtils.placeholder("setting", entry.getKey()),
                LangUtils.placeholder("dungeon", dungeon.getWorldName()), LangUtils.placeholder("value", value));
        if (immediate) {
            LangUtils.sendMessage(sender, "commands.config.updated-live");
        } else {
            LangUtils.sendMessage(sender, "commands.config.updated-runtime");
        }
    }

    /**
     * Runs show setting.
     */
    private void showSetting(CommandSender sender, DungeonDefinition dungeon, DungeonConfigEntry entry) {
        LangUtils.sendMessage(sender, "commands.config.setting-value", LangUtils.placeholder("setting", entry.getKey()),
                LangUtils.placeholder("value", this.getCurrentValue(dungeon, entry)),
                LangUtils.placeholder("description", entry.getDescription()));
    }

    /**
     * Runs list settings.
     */
    private void listSettings(CommandSender sender, DungeonDefinition dungeon, String filter) {
        String loweredFilter = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        sender.sendMessage(LangUtils.getMessage("commands.config.list-header", false,
                LangUtils.placeholder("dungeon", dungeon.getWorldName())));
        LangUtils.sendMessage(sender, "commands.config.list-usage",
                LangUtils.placeholder("dungeon", dungeon.getWorldName()));
        if (sender instanceof Player) {
            LangUtils.sendMessage(sender, "commands.config.list-shortcut");
        }

        DungeonConfigFile previousFile = null;
        boolean found = false;
        for (DungeonConfigEntry entry : DungeonConfigRegistry.getEntries(dungeon)) {
            if (!loweredFilter.isBlank()) {
                String searchable = (entry.getKey() + " " + entry.getDescription() + " " + entry.getPath())
                        .toLowerCase(Locale.ROOT);
                if (!searchable.contains(loweredFilter)) {
                    continue;
                }
            }

            if (entry.getConfigFile() != previousFile) {
                previousFile = entry.getConfigFile();
                sender.sendMessage(LangUtils.getMessage(previousFile == DungeonConfigFile.MAIN
                        ? "commands.config.files.main"
                        : "commands.config.files.generation", false));
            }

            sender.sendMessage(LangUtils.getMessage("commands.config.setting-line", false,
                    LangUtils.placeholder("setting", entry.getKey()),
                    LangUtils.placeholder("value", this.getCurrentValue(dungeon, entry)),
                    LangUtils.placeholder("description", entry.getDescription())));
            found = true;
        }

        if (!found) {
            LangUtils.sendMessage(sender, "commands.config.no-match");
        }
    }

    /**
     * Runs send usage.
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(LangUtils.getMessage("commands.config.help.header", false));
        LangUtils.sendMessageList(sender, "commands.config.help.lines");
    }

    /**
     * Runs send invalid value.
     */
    private boolean sendInvalidValue(CommandSender sender, DungeonConfigEntry entry, String message) {
        LangUtils.sendMessage(sender, "commands.config.invalid-value", LangUtils.placeholder("setting", entry.getKey()),
                LangUtils.placeholder("reason", message));
        return true;
    }

    /**
     * Returns the current value.
     */
    private String getCurrentValue(DungeonDefinition dungeon, DungeonConfigEntry entry) {
        if (entry.getValueType() == DungeonConfigValueType.LOCATION) {
            return switch (entry.getKey()) {
                case "lobby-location" -> this.formatLocation(dungeon.getLobbySpawn());
                case "start-location" -> this.formatLocation(dungeon.getStartSpawn());
                case "exit-location" -> this.formatLocation(dungeon.getExitLoc());
                default -> "<unknown>";
            };
        }

        if (entry.getKey().equals("branch.add")) {
            return "<new branch id>";
        }

        if (entry.getKey().equals("branch.remove")) {
            BranchingDungeon branching = dungeon.asBranching();
            if (branching == null) {
                return "<unsupported>";
            }

            ConfigurationSection branchSection = branching.getGenConfig()
                    .getConfigurationSection("branching.branches");
            if (branchSection == null || branchSection.getKeys(false).isEmpty()) {
                return "<none>";
            }

            List<String> branchNames = new ArrayList<>(branchSection.getKeys(false));
            Collections.sort(branchNames, String.CASE_INSENSITIVE_ORDER);
            return String.join(", ", branchNames);
        }

        FileConfiguration config = this.getTargetConfig(dungeon, entry);
        if (config == null) {
            return "<unsupported>";
        }

        return this.stringifyValue(config.get(entry.getPath()));
    }

    /**
     * Returns the target config.
     */
    private FileConfiguration getTargetConfig(DungeonDefinition dungeon, DungeonConfigEntry entry) {
        if (entry.getConfigFile() == DungeonConfigFile.MAIN) {
            return dungeon.getConfig();
        }

        BranchingDungeon branching = dungeon.asBranching();
        return branching == null ? null : branching.getGenConfig();
    }

    /**
     * Runs complete value.
     */
    private List<String> completeValue(DungeonConfigEntry entry, DungeonDefinition dungeon, String rawValue) {
        return switch (entry.getValueType()) {
            case BOOLEAN -> this.completeSimple(rawValue, List.of("true", "false"));
            case MATERIAL_LIST, ENTITY_TYPE_LIST, STRING_LIST ->
                this.completeList(rawValue, entry.getSuggestions(dungeon));
            case LOCATION -> this.completeSimple(rawValue, entry.getSuggestions(dungeon));
            default -> this.completeSimple(rawValue, entry.getSuggestions(dungeon));
        };
    }

    /**
     * Runs complete simple.
     */
    private List<String> completeSimple(String rawValue, List<String> candidates) {
        String query = rawValue == null ? "" : rawValue.toLowerCase(Locale.ROOT).trim();
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (query.isBlank() || candidate.toLowerCase(Locale.ROOT).contains(query)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    /**
     * Runs complete list.
     */
    private List<String> completeList(String rawValue, List<String> candidates) {
        String value = rawValue == null ? "" : rawValue.trim();
        String prefix = "";
        String token = value;
        int lastComma = value.lastIndexOf(',');
        if (lastComma >= 0) {
            prefix = value.substring(0, lastComma + 1);
            token = value.substring(lastComma + 1).trim();
        }

        Set<String> alreadySelected = new HashSet<>();
        if (!prefix.isBlank()) {
            for (String part : prefix.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    alreadySelected.add(trimmed.toUpperCase(Locale.ROOT));
                }
            }
        }

        List<String> matches = new ArrayList<>();
        if (prefix.isBlank() && (token.isBlank() || "clear".contains(token.toLowerCase(Locale.ROOT)))) {
            matches.add("clear");
        }

        for (String candidate : candidates) {
            if (alreadySelected.contains(candidate.toUpperCase(Locale.ROOT))) {
                continue;
            }

            if (token.isBlank() || candidate.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))) {
                matches.add(prefix + candidate);
            }
        }
        return matches;
    }

    /**
     * Runs normalize branch id.
     */
    private String normalizeBranchId(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.matches("[a-z0-9_-]+")) {
            return null;
        }

        return normalized;
    }

    /**
     * Runs parse boolean.
     */
    private Boolean parseBoolean(String rawValue) {
        return switch (rawValue.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "on", "enable", "enabled" -> true;
            case "false", "no", "n", "off", "disable", "disabled" -> false;
            default -> null;
        };
    }

    /**
     * Runs parse integer.
     */
    private Integer parseInteger(String rawValue) {
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Runs parse double.
     */
    private Double parseDouble(String rawValue) {
        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Runs parse material.
     */
    private Material parseMaterial(String rawValue) {
        Material exact = Material.getMaterial(rawValue.toUpperCase(Locale.ROOT));
        return exact != null ? exact : Material.matchMaterial(rawValue);
    }

    /**
     * Runs parse material list.
     */
    private List<String> parseMaterialList(String rawValue) {
        if (rawValue.equalsIgnoreCase("clear")) {
            return new ArrayList<>();
        }

        List<String> values = new ArrayList<>();
        for (String token : rawValue.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            Material material = this.parseMaterial(trimmed);
            if (material == null) {
                return null;
            }
            values.add(material.name());
        }
        return values;
    }

    /**
     * Runs parse entity type list.
     */
    private List<String> parseEntityTypeList(String rawValue) {
        if (rawValue.equalsIgnoreCase("clear")) {
            return new ArrayList<>();
        }

        List<String> values = new ArrayList<>();
        for (String token : rawValue.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            try {
                values.add(EntityType.valueOf(trimmed.toUpperCase(Locale.ROOT)).name());
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        return values;
    }

    /**
     * Runs parse string list.
     */
    private List<String> parseStringList(String rawValue) {
        if (rawValue.equalsIgnoreCase("clear")) {
            return new ArrayList<>();
        }

        List<String> values = new ArrayList<>();
        for (String token : rawValue.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    /**
     * Runs parse game mode.
     */
    private GameMode parseGameMode(String rawValue) {
        try {
            return GameMode.valueOf(rawValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Runs parse environment.
     */
    private Environment parseEnvironment(String rawValue) {
        try {
            return Environment.valueOf(rawValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Runs parse cooldown period.
     */
    private CooldownPeriod parseCooldownPeriod(String rawValue) {
        try {
            return CooldownPeriod.valueOf(rawValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Runs format location.
     */
    private String formatLocation(Location location) {
        if (location == null) {
            return "<unset>";
        }

        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + " (yaw "
                + Math.round(location.getYaw()) + ")";
    }

    /**
     * Runs stringify value.
     */
    private String stringifyValue(Object value) {
        if (value == null) {
            return "<unset>";
        }

        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return "<empty>";
            }
            List<String> rendered = new ArrayList<>();
            for (Object entry : list) {
                rendered.add(String.valueOf(entry));
            }
            return String.join(", ", rendered);
        }

        return String.valueOf(value);
    }

    /**
     * Runs join args.
     */
    private String joinArgs(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < args.length; index++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    /**
     * Returns whether it has edit access.
     */
    private boolean hasEditAccess(CommandSender sender, DungeonDefinition dungeon) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        return CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName())
                || CommandUtils.hasPermissionSilent(player, "dungeons.admin");
    }

    /**
     * Resolves dungeon.
     */
    private DungeonDefinition resolveDungeon(CommandSender sender, String input) {
        if (input.equalsIgnoreCase("current") && sender instanceof Player player) {
            DungeonPlayerSession playerSession = this.playerManager.get(player);
            if (playerSession != null && playerSession.getInstance() != null) {
                return playerSession.getInstance().getDungeon();
            }

            DungeonDefinition worldDungeon = this.dungeonManager.get(player.getWorld().getName());
            if (worldDungeon != null) {
                return worldDungeon;
            }

            var instance = this.activeInstanceManager.getDungeonInstance(player.getWorld().getName());
            if (instance != null) {
                return instance.getDungeon();
            }
        }

        return this.dungeonManager.get(input);
    }
}
