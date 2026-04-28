package nl.hauntedmc.dungeons.command.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import nl.hauntedmc.dungeons.content.dungeon.OpenDungeon;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.reward.CooldownPeriod;
import nl.hauntedmc.dungeons.generation.layout.BranchingLayout;
import nl.hauntedmc.dungeons.generation.layout.ConnectorExpansionLayout;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

/**
 * Registry of dungeon settings that can be edited through {@code /dungeon config}.
 *
 * <p>The registry combines a static set of common settings with dynamic entries derived from the
 * current dungeon definition, such as difficulty presets and branching branch settings.</p>
 */
public final class DungeonConfigRegistry {
    private static final Predicate<DungeonDefinition> ANY_DUNGEON = dungeon -> true;
    private static final Predicate<DungeonDefinition> BRANCHING_ONLY = dungeon -> dungeon.asBranching() != null;
    private static final Predicate<DungeonDefinition> NON_BRANCHING_ONLY = dungeon -> dungeon.asBranching() == null;
    private static final Predicate<DungeonDefinition> OPEN_ONLY = dungeon -> dungeon instanceof OpenDungeon;
    private static final Predicate<DungeonDefinition> BRANCHING_LAYOUT_ONLY = dungeon -> dungeon.asBranching() != null
            && dungeon.asBranching().getLayout() instanceof BranchingLayout;
    private static final Predicate<DungeonDefinition> MINECRAFTY_LAYOUT_ONLY = dungeon -> dungeon.asBranching() != null
            && dungeon.asBranching().getLayout() instanceof ConnectorExpansionLayout;

    private static final List<String> BOOLEAN_VALUES = List.of("true", "false");
    private static final List<String> CHUNK_GENERATOR_VALUES = List.of("NATURAL", "dungeons:void",
            "dungeons:block.STONE");
    private static final List<String> LAYOUT_VALUES = List.of("minecrafty", "branching");
    private static final List<String> ENVIRONMENT_VALUES = Arrays.stream(Environment.values()).map(Enum::name).sorted()
            .toList();
    private static final List<String> GAMEMODE_VALUES = Arrays.stream(GameMode.values()).map(Enum::name).sorted()
            .toList();
    private static final List<String> COOLDOWN_VALUES = Arrays.stream(CooldownPeriod.values()).map(Enum::name).sorted()
            .toList();
    private static final List<String> MATERIAL_VALUES = Arrays.stream(Material.values()).map(Enum::name).sorted()
            .toList();
    private static final List<String> ENTITY_VALUES = Arrays.stream(EntityType.values()).map(Enum::name).sorted()
            .toList();
    private static final List<String> RANGED_NUMBER_VALUES = List.of("0+", "0-1", "1+", "8", ">=8", "<=8");

    private static final Map<String, DungeonConfigEntry> entriesByLookup = new LinkedHashMap<>();
    private static final List<DungeonConfigEntry> rootEntries = new ArrayList<>();

    static {
        register(main("enabled", "Enables or disables joining this dungeon.", "dungeon.enabled",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("display-name", "Shown in menus, messages and titles.", "dungeon.display_name",
                DungeonConfigValueType.COLOR_STRING, aliases("name")));
        register(main("dimension", "World environment for new instances.", "dungeon.environment",
                DungeonConfigValueType.WORLD_ENVIRONMENT, suggestions(ENVIRONMENT_VALUES)));
        register(main("gamemode", "Gamemode enforced inside the dungeon.", "players.gamemode",
                DungeonConfigValueType.GAMEMODE, suggestions(GAMEMODE_VALUES)));
        register(main("chunk-generator", "Chunk generator used for future instances.", "dungeon.chunk_generator",
                DungeonConfigValueType.CHUNK_GENERATOR, suggestions(CHUNK_GENERATOR_VALUES)));
        register(main("time-limit", "Time limit in minutes, 0 disables the limit.", "runs.time_limit_minutes",
                DungeonConfigValueType.INTEGER));
        register(main("player-lives", "Number of lives per player, 0 means unlimited.", "players.lives",
                DungeonConfigValueType.INTEGER));
        register(main("max-team-size", "Maximum amount of players allowed to start this dungeon together.",
                "team.max_size", DungeonConfigValueType.INTEGER));
        register(main("max-instances", "Maximum number of active instances for this dungeon, 0 means unlimited.",
                "runs.max_active", DungeonConfigValueType.INTEGER));
        register(main("cleanup-delay", "Ticks to wait before disposing empty play instances.",
                "runs.cleanup_delay_ticks", DungeonConfigValueType.INTEGER));
        register(main("open-max-players", "Maximum players allowed inside one open run, 0 means unlimited.",
                "open.max_players", DungeonConfigValueType.INTEGER, OPEN_ONLY, dungeon -> List.of(),
                List.of()));
        register(main("open-preload-world", "Keeps one open run preloaded for faster joins.",
                "open.preload_world", DungeonConfigValueType.BOOLEAN, OPEN_ONLY,
                suggestions(BOOLEAN_VALUES), List.of()));
        register(main("show-title-on-start", "Shows the dungeon title when a run starts.",
                "dungeon.show_title_on_start", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("keep-inventory", "Keeps player inventory when entering.", "players.keep_on_entry.inventory",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("keep-health", "Keeps player health when entering.", "players.keep_on_entry.health",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("keep-food", "Keeps player food level when entering.", "players.keep_on_entry.food",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("keep-exp", "Keeps player experience when entering.", "players.keep_on_entry.experience",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("keep-potions", "Keeps active potion effects when entering.",
                "players.keep_on_entry.potion_effects", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("dungeon-item-inheritance", "Transfers dungeon items to the next player when someone leaves.",
                "players.transfer_dungeon_items", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("dead-players-spectate", "Moves dead players into spectating flow.", "players.spectate_on_death",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("close-when-all-spectating", "Closes the run when everyone is spectating.",
                "players.close_when_everyone_spectates", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("kick-offline-players", "Removes offline players from active runs.",
                "players.offline_kick.enabled", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("kick-offline-delay", "Seconds before an offline player is kicked.",
                "players.offline_kick.delay_seconds", DungeonConfigValueType.INTEGER));
        register(main("join-commands",
                "Comma separated console commands run when each player enters. Use {player} or <player>.",
                "players.join_commands", DungeonConfigValueType.STRING_LIST,
                suggestions(List.of("god {player} disable", "fly {player} disable", "speed walk 1 {player}"))));
        register(main("team-disband-shutdown-delay",
                "Seconds a started dungeon stays open after its team is dissolved or the leader leaves.",
                "team.disband_shutdown_delay_seconds", DungeonConfigValueType.INTEGER));
        register(main("give-loot-after-completion",
                "Queues loot to be claimed after completion instead of giving it directly.",
                "rewards.deliver_on_finish", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("difficulty-enabled", "Enables configured difficulty levels.", "difficulty.enabled",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("difficulty-menu", "Shows the difficulty selector menu before starting.", "difficulty.use_menu",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));

        register(main("lobby-enabled", "Enables a lobby phase before the run starts.", "locations.lobby.enabled",
                DungeonConfigValueType.BOOLEAN, NON_BRANCHING_ONLY, suggestions(BOOLEAN_VALUES),
                aliases("use-lobby")));
        register(main("lobby-location", "Sets the lobby location with 'here'.", "locations.lobby.spawn",
                DungeonConfigValueType.LOCATION, NON_BRANCHING_ONLY, suggestions(List.of("here")), aliases("lobby")));
        register(main("start-location", "Sets the dungeon start location with 'here'.", "locations.start",
                DungeonConfigValueType.LOCATION, suggestions(List.of("here")), aliases("spawn", "start")));
        register(main("exit-location", "Sets the dungeon exit location with 'here', or clears it with 'clear'.",
                "locations.exit", DungeonConfigValueType.LOCATION, suggestions(List.of("here", "clear")),
                aliases("exit")));
        register(main("always-use-exit", "Always sends players to the configured exit when they leave.",
                "locations.exit_on_leave", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));

        register(main("access-cooldown-enabled", "Enables access cooldowns.", "access.cooldown.enabled",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("access-cooldown-leader-only", "Only requires and applies access cooldowns for the team leader.",
                "access.cooldown.leader_only", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("access-cooldown-on-start", "Applies the access cooldown when the run starts.",
                "access.cooldown.on_start", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("access-cooldown-on-finish", "Applies the access cooldown on completion.",
                "access.cooldown.on_finish", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("access-cooldown-on-leave", "Applies the access cooldown when a player leaves.",
                "access.cooldown.on_leave", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("access-cooldown-on-lose-lives", "Applies the access cooldown when a player loses all lives.",
                "access.cooldown.on_lives_depleted", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("access-cooldown-type", "Cooldown cadence.", "access.cooldown.period",
                DungeonConfigValueType.COOLDOWN_PERIOD, suggestions(COOLDOWN_VALUES)));
        register(main("access-cooldown-time", "Cooldown amount, or reset hour for non timer periods.",
                "access.cooldown.value", DungeonConfigValueType.INTEGER));
        register(main("access-cooldown-reset-day", "Reset day for weekly and monthly cooldowns.",
                "access.cooldown.reset_day", DungeonConfigValueType.INTEGER));
        register(main("loot-cooldown-enabled", "Enables reward cooldowns.", "rewards.loot_cooldown.enabled",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("loot-cooldown-per-reward", "Tracks loot cooldowns per reward function.",
                "rewards.loot_cooldown.track_per_reward", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("loot-cooldown-type", "Reward cooldown cadence.", "rewards.loot_cooldown.period",
                DungeonConfigValueType.COOLDOWN_PERIOD, suggestions(COOLDOWN_VALUES)));
        register(main("loot-cooldown-time", "Cooldown amount, or reset hour for non timer periods.",
                "rewards.loot_cooldown.value", DungeonConfigValueType.INTEGER));
        register(main("loot-cooldown-reset-day", "Reset day for weekly and monthly loot cooldowns.",
                "rewards.loot_cooldown.reset_day", DungeonConfigValueType.INTEGER));
        register(main("access-keys-consume-on-entry", "Consumes a matching key when the run starts.",
                "access.keys.consume_on_entry", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("access-keys-leader-only", "Only requires a dungeon key from the team leader.",
                "access.keys.leader_only", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));

        register(main("spawn-mobs", "Allows natural mob spawning in instances.", "rules.spawning.natural_mobs",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("spawn-animals", "Allows passive animal spawning in instances.", "rules.spawning.animals",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("spawn-monsters", "Allows hostile monster spawning in instances.", "rules.spawning.monsters",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("disable-random-tick", "Sets random tick speed to 0 in instances.",
                "rules.world.freeze_random_ticks", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("allow-break-blocks", "Allows players to break blocks.", "rules.building.break_blocks",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("allow-break-placed-blocks", "Allows players to break blocks they placed.",
                "rules.building.break_placed_blocks", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("allow-place-blocks", "Allows players to place blocks.", "rules.building.place_blocks",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("allow-place-entities", "Allows placing entity items like armor stands.",
                "rules.building.place_entities", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("allow-bucket", "Allows using buckets.", "rules.movement.buckets", DungeonConfigValueType.BOOLEAN,
                suggestions(BOOLEAN_VALUES)));
        register(main("allow-enderpearl", "Allows ender pearl usage.", "rules.movement.ender_pearls",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("allow-chorus-fruit", "Allows chorus fruit teleportation.", "rules.movement.chorus_fruit",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("allow-commands", "Allows player commands inside the dungeon.", "rules.commands.allow_all",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("allow-craft-banned-items", "Allows crafting items that are otherwise banned.",
                "rules.items.allow_craft_banned", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("allow-pickup-banned-items", "Allows picking up banned items.", "rules.items.allow_pickup_banned",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("allow-storage-banned-items", "Allows storing banned items.", "rules.items.allow_storage_banned",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("banned-materials",
                "Comma separated material list banned inside the dungeon. Use clear to empty.",
                "rules.items.banned_materials", DungeonConfigValueType.MATERIAL_LIST, suggestions(MATERIAL_VALUES)));
        register(main("prevent-explosion-block-damage", "Prevents explosions from damaging blocks.",
                "rules.world.prevent_explosion_block_damage", DungeonConfigValueType.BOOLEAN,
                suggestions(BOOLEAN_VALUES)));
        register(main("prevent-plant-growth", "Prevents plants from growing in instances.",
                "rules.world.prevent_plant_growth", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("hide-death-messages", "Suppresses player death messages.", "rules.combat.hide_death_messages",
                DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        register(main("pvp", "Allows player versus player damage.", "rules.combat.pvp", DungeonConfigValueType.BOOLEAN,
                suggestions(BOOLEAN_VALUES)));
        register(main("prevent-durability-loss-weapons", "Prevents durability loss on weapons.",
                "rules.combat.prevent_durability_loss.weapons", DungeonConfigValueType.BOOLEAN,
                suggestions(BOOLEAN_VALUES)));
        register(main("prevent-durability-loss-tools", "Prevents durability loss on tools.",
                "rules.combat.prevent_durability_loss.tools", DungeonConfigValueType.BOOLEAN,
                suggestions(BOOLEAN_VALUES)));
        register(main("prevent-durability-loss-armor", "Prevents durability loss on armor.",
                "rules.combat.prevent_durability_loss.armor", DungeonConfigValueType.BOOLEAN,
                suggestions(BOOLEAN_VALUES)));
        register(main("map-floor-depth", "Floor depth used when rendering dungeon maps.", "map.floor_depth",
                DungeonConfigValueType.INTEGER));

        register(main("block-place-whitelist",
                "Comma separated material whitelist for block placing. Use clear to empty.",
                "rules.building.place_whitelist", DungeonConfigValueType.MATERIAL_LIST, suggestions(MATERIAL_VALUES)));
        register(main("block-place-blacklist",
                "Comma separated material blacklist for block placing. Use clear to empty.",
                "rules.building.place_blacklist", DungeonConfigValueType.MATERIAL_LIST, suggestions(MATERIAL_VALUES)));
        register(main("block-break-whitelist",
                "Comma separated material whitelist for block breaking. Use clear to empty.",
                "rules.building.break_whitelist", DungeonConfigValueType.MATERIAL_LIST, suggestions(MATERIAL_VALUES)));
        register(main("block-break-blacklist",
                "Comma separated material blacklist for block breaking. Use clear to empty.",
                "rules.building.break_blacklist", DungeonConfigValueType.MATERIAL_LIST, suggestions(MATERIAL_VALUES)));
        register(main("damage-protected-entities",
                "Comma separated entity types protected from damage. Use clear to empty.",
                "rules.entities.damage_protected", DungeonConfigValueType.ENTITY_TYPE_LIST,
                suggestions(ENTITY_VALUES)));
        register(main("interact-protected-entities",
                "Comma separated entity types protected from interaction. Use clear to empty.",
                "rules.entities.interact_protected", DungeonConfigValueType.ENTITY_TYPE_LIST,
                suggestions(ENTITY_VALUES)));
        register(main("allowed-commands", "Comma separated command labels allowed in the dungeon. Use clear to empty.",
                "rules.commands.allow_list", DungeonConfigValueType.STRING_LIST));
        register(main("disallowed-commands",
                "Comma separated command labels blocked in the dungeon. Use clear to empty.",
                "rules.commands.deny_list", DungeonConfigValueType.STRING_LIST));

        register(generation("layout", "Branching room layout algorithm.", "generator.layout",
                DungeonConfigValueType.LAYOUT, suggestions(LAYOUT_VALUES)));
        register(generation("room-target-min", "Minimum generated rooms for minecrafty layouts.",
                "generator.room_target.min", DungeonConfigValueType.INTEGER, MINECRAFTY_LAYOUT_ONLY,
                dungeon -> List.of(), List.of()));
        register(generation("room-target-max", "Maximum generated rooms for minecrafty layouts.",
                "generator.room_target.max", DungeonConfigValueType.INTEGER, MINECRAFTY_LAYOUT_ONLY,
                dungeon -> List.of(), List.of()));
        register(generation("enforce-room-target-minimum",
                "Requires minecrafty layouts to reach the configured minimum room count.",
                "generator.room_target.enforce_minimum", DungeonConfigValueType.BOOLEAN, MINECRAFTY_LAYOUT_ONLY,
                suggestions(BOOLEAN_VALUES), List.of()));
        register(generation("seal-open-connectors-with",
                "Block used to seal unused connectors. Use AIR to leave them open.",
                "generator.seal_open_connectors_with", DungeonConfigValueType.MATERIAL, suggestions(MATERIAL_VALUES)));
    }

    /**
     * Creates a new DungeonConfigRegistry instance.
     */
    private DungeonConfigRegistry() {
    }

    /**
     * Returns a statically registered config entry by key, path, or alias.
     */
    public static DungeonConfigEntry get(String key) {
        if (key == null) {
            return null;
        }

        return entriesByLookup.get(key.toLowerCase(Locale.ROOT));
    }

    /**
     * Resolves a config entry for the supplied dungeon, including dynamic dungeon-specific entries.
     */
    public static DungeonConfigEntry resolve(DungeonDefinition dungeon, String key) {
        DungeonConfigEntry staticEntry = get(key);
        if (staticEntry != null && staticEntry.supports(dungeon)) {
            return staticEntry;
        }

        if (key == null) {
            return null;
        }

        String lowered = key.toLowerCase(Locale.ROOT);
        for (DungeonConfigEntry entry : getDynamicEntries(dungeon)) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(lowered)
                    || entry.getPath().toLowerCase(Locale.ROOT).equals(lowered)) {
                return entry;
            }

            for (String alias : entry.getAliases()) {
                if (alias.toLowerCase(Locale.ROOT).equals(lowered)) {
                    return entry;
                }
            }
        }

        return null;
    }

    /**
     * Returns every config entry that applies to the supplied dungeon.
     */
    public static List<DungeonConfigEntry> getEntries(DungeonDefinition dungeon) {
        List<DungeonConfigEntry> entries = new ArrayList<>();
        for (DungeonConfigEntry entry : rootEntries) {
            if (entry.supports(dungeon)) {
                entries.add(entry);
            }
        }

        entries.addAll(getDynamicEntries(dungeon));
        return entries;
    }

    /**
     * Returns the dynamic entries.
     */
    private static List<DungeonConfigEntry> getDynamicEntries(DungeonDefinition dungeon) {
        List<DungeonConfigEntry> entries = new ArrayList<>();

        // Difficulty presets and branching branches are driven by the current dungeon contents, so
        // their command-visible settings must be rebuilt dynamically instead of being globally static.
        ConfigurationSection difficultySettings = dungeon.getConfig().getConfigurationSection("difficulty.presets");
        if (difficultySettings != null) {
            for (String difficultyName : difficultySettings.getKeys(false).stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
                String pathPrefix = "difficulty.presets." + difficultyName + ".";
                String keyPrefix = "difficulty." + difficultyName.toLowerCase(Locale.ROOT) + ".";
                String readableName = "'" + difficultyName + "' difficulty";

                entries.add(main(keyPrefix + "icon-material", "Selector icon material for " + readableName + ".",
                        pathPrefix + "icon.material", DungeonConfigValueType.MATERIAL, suggestions(MATERIAL_VALUES)));
                entries.add(main(keyPrefix + "icon-custom-model-data",
                        "Selector icon custom model data for " + readableName + ". Use -1 to disable.",
                        pathPrefix + "icon.custom_model_data", DungeonConfigValueType.INTEGER));
                entries.add(
                        main(keyPrefix + "icon-display-name", "Selector icon display name for " + readableName + ".",
                                pathPrefix + "icon.display_name", DungeonConfigValueType.COLOR_STRING));
                entries.add(main(keyPrefix + "icon-lore",
                        "Comma separated selector lore for " + readableName + ". Use clear to empty.",
                        pathPrefix + "icon.lore", DungeonConfigValueType.STRING_LIST));
                entries.add(main(keyPrefix + "mob-health", "Mob health multiplier for " + readableName + ".",
                        pathPrefix + "scaling.mob_health", DungeonConfigValueType.DECIMAL));
                entries.add(main(keyPrefix + "mob-count", "Mob count multiplier for " + readableName + ".",
                        pathPrefix + "scaling.mob_count", DungeonConfigValueType.DECIMAL));
                entries.add(main(keyPrefix + "mob-damage", "Mob damage multiplier for " + readableName + ".",
                        pathPrefix + "scaling.mob_damage", DungeonConfigValueType.DECIMAL));
                entries.add(main(keyPrefix + "bonus-loot", "Bonus loot rule for " + readableName + ".",
                        pathPrefix + "scaling.bonus_loot", DungeonConfigValueType.STRING,
                        suggestions(RANGED_NUMBER_VALUES)));
            }
        }

        BranchingDungeon branching = dungeon.asBranching();
        if (branching == null) {
            return entries;
        }

        if (!(branching.getLayout() instanceof BranchingLayout)) {
            return entries;
        }

        List<String> roomNames = branching.getUniqueRooms().keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        entries.add(generation("trunk.room-pool", "Comma separated room whitelist for the trunk. Use clear to empty.",
                "branching.trunk.room_pool", DungeonConfigValueType.STRING_LIST, suggestions(roomNames)));
        entries.add(generation("trunk.end-room-pool",
                "Comma separated terminal room whitelist for the trunk. Use clear to empty.",
                "branching.trunk.end_room_pool", DungeonConfigValueType.STRING_LIST, suggestions(roomNames)));
        entries.add(
                generation("trunk.straightness", "How strongly the trunk favors straight generation. Range 0.0 to 1.0.",
                        "branching.trunk.straightness", DungeonConfigValueType.DECIMAL));
        entries.add(generation("trunk.room-target-min", "Minimum rooms for the trunk path.",
                "branching.trunk.room_target.min", DungeonConfigValueType.INTEGER));
        entries.add(generation("trunk.room-target-max", "Maximum rooms for the trunk path.",
                "branching.trunk.room_target.max", DungeonConfigValueType.INTEGER));
        entries.add(generation("trunk.enforce-room-target-minimum",
                "Whether the trunk must satisfy its minimum room target.",
                "branching.trunk.room_target.enforce_minimum", DungeonConfigValueType.BOOLEAN,
                suggestions(BOOLEAN_VALUES)));

        ConfigurationSection branchSettings = branching.getGenConfig().getConfigurationSection("branching.branches");
            List<String> branchNames = branchSettings == null
                    ? List.of()
                    : branchSettings.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            entries.add(generation("branch.add", "Create a named branch with default settings.", "branching.branches",
                    DungeonConfigValueType.STRING, BRANCHING_LAYOUT_ONLY, ignoredDungeon -> List.of(), List.of()));
            entries.add(generation("branch.remove", "Remove a named branch.", "branching.branches",
                    DungeonConfigValueType.STRING, BRANCHING_LAYOUT_ONLY, ignoredDungeon -> branchNames, List.of()));

        if (branchSettings == null) {
            return entries;
        }

        for (String branchName : branchNames) {
            String pathPrefix = "branching.branches." + branchName + ".";
            String keyPrefix = "branch." + branchName.toLowerCase(Locale.ROOT) + ".";
            String readableName = "'" + branchName + "' branch";

            entries.add(generation(keyPrefix + "room-pool",
                    "Comma separated room whitelist for the " + readableName + ". Use clear to empty.",
                    pathPrefix + "room_pool", DungeonConfigValueType.STRING_LIST, suggestions(roomNames)));
            entries.add(generation(keyPrefix + "end-room-pool",
                    "Comma separated terminal room whitelist for the " + readableName + ". Use clear to empty.",
                    pathPrefix + "end_room_pool", DungeonConfigValueType.STRING_LIST, suggestions(roomNames)));
            entries.add(generation(keyPrefix + "straightness",
                    "How strongly the " + readableName + " favors straight generation. Range 0.0 to 1.0.",
                    pathPrefix + "straightness", DungeonConfigValueType.DECIMAL));
            entries.add(generation(keyPrefix + "room-target-min", "Minimum rooms for the " + readableName + ".",
                    pathPrefix + "room_target.min", DungeonConfigValueType.INTEGER));
            entries.add(generation(keyPrefix + "room-target-max", "Maximum rooms for the " + readableName + ".",
                    pathPrefix + "room_target.max", DungeonConfigValueType.INTEGER));
            entries.add(generation(keyPrefix + "enforce-room-target-minimum",
                    "Whether the " + readableName + " must satisfy its minimum room target.",
                    pathPrefix + "room_target.enforce_minimum", DungeonConfigValueType.BOOLEAN,
                    suggestions(BOOLEAN_VALUES)));
            entries.add(generation(keyPrefix + "eligible-depth", "Depth rule for the " + readableName + ".",
                    pathPrefix + "eligible_depth", DungeonConfigValueType.STRING, suggestions(RANGED_NUMBER_VALUES)));
            entries.add(generation(keyPrefix + "occurrences", "Occurrence rule for the " + readableName + ".",
                    pathPrefix + "occurrences", DungeonConfigValueType.STRING, suggestions(RANGED_NUMBER_VALUES)));
            entries.add(generation(keyPrefix + "enforce-occurrences",
                    "Whether the " + readableName + " must satisfy its branch occurrence target.",
                    pathPrefix + "enforce_occurrences", DungeonConfigValueType.BOOLEAN, suggestions(BOOLEAN_VALUES)));
        }

        return entries;
    }

    /**
     * Returns the command-facing keys that apply to the supplied dungeon, optionally filtered by a
     * search query.
     */
    public static List<String> getKeys(DungeonDefinition dungeon, String query) {
        String lowered = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return getEntries(dungeon).stream().map(DungeonConfigEntry::getKey)
                .filter(key -> lowered.isBlank() || key.toLowerCase(Locale.ROOT).contains(lowered))
                .sorted(Comparator.naturalOrder()).toList();
    }

    /**
     * Returns the statically registered root config entries.
     */
    public static Collection<DungeonConfigEntry> getEntries() {
        return Collections.unmodifiableList(rootEntries);
    }

    /**
     * Runs register.
     */
    private static void register(DungeonConfigEntry entry) {
        rootEntries.add(entry);
        index(entry.getKey(), entry);
        index(entry.getPath(), entry);
        for (String alias : entry.getAliases()) {
            index(alias, entry);
        }
    }

    /**
     * Runs index.
     */
    private static void index(String lookup, DungeonConfigEntry entry) {
        entriesByLookup.put(lookup.toLowerCase(Locale.ROOT), entry);
    }

    /**
     * Runs main.
     */
    private static DungeonConfigEntry main(String key, String description, String path,
            DungeonConfigValueType valueType) {
        return main(key, description, path, valueType, ANY_DUNGEON, dungeon -> List.of(), List.of());
    }

    /**
     * Runs main.
     */
    private static DungeonConfigEntry main(String key, String description, String path,
            DungeonConfigValueType valueType, Function<DungeonDefinition, List<String>> suggestionsProvider) {
        return main(key, description, path, valueType, ANY_DUNGEON, suggestionsProvider, List.of());
    }

    /**
     * Runs main.
     */
    private static DungeonConfigEntry main(String key, String description, String path,
            DungeonConfigValueType valueType, List<String> aliases) {
        return main(key, description, path, valueType, ANY_DUNGEON, dungeon -> List.of(), aliases);
    }

    /**
     * Runs main.
     */
    private static DungeonConfigEntry main(String key, String description, String path,
            DungeonConfigValueType valueType, Predicate<DungeonDefinition> supportPredicate,
            Function<DungeonDefinition, List<String>> suggestionsProvider, List<String> aliases) {
        return new DungeonConfigEntry(key, description, DungeonConfigFile.MAIN, path, valueType, supportPredicate,
                suggestionsProvider, aliases);
    }

    /**
     * Runs main.
     */
    private static DungeonConfigEntry main(String key, String description, String path,
            DungeonConfigValueType valueType, Function<DungeonDefinition, List<String>> suggestionsProvider,
            List<String> aliases) {
        return main(key, description, path, valueType, ANY_DUNGEON, suggestionsProvider, aliases);
    }

    /**
     * Runs generation.
     */
    private static DungeonConfigEntry generation(String key, String description, String path,
            DungeonConfigValueType valueType) {
        return generation(key, description, path, valueType, dungeon -> List.of(), List.of());
    }

    /**
     * Runs generation.
     */
    private static DungeonConfigEntry generation(String key, String description, String path,
            DungeonConfigValueType valueType, Function<DungeonDefinition, List<String>> suggestionsProvider) {
        return generation(key, description, path, valueType, suggestionsProvider, List.of());
    }

    /**
     * Runs generation.
     */
    private static DungeonConfigEntry generation(String key, String description, String path,
            DungeonConfigValueType valueType, Function<DungeonDefinition, List<String>> suggestionsProvider,
            List<String> aliases) {
        return generation(key, description, path, valueType, BRANCHING_ONLY, suggestionsProvider, aliases);
    }

    /**
     * Runs generation.
     */
    private static DungeonConfigEntry generation(String key, String description, String path,
            DungeonConfigValueType valueType, Predicate<DungeonDefinition> supportPredicate,
            Function<DungeonDefinition, List<String>> suggestionsProvider, List<String> aliases) {
        return new DungeonConfigEntry(key, description, DungeonConfigFile.GENERATION, path, valueType, supportPredicate,
                suggestionsProvider, aliases);
    }

    /**
     * Runs suggestions.
     */
    private static Function<DungeonDefinition, List<String>> suggestions(List<String> values) {
        return dungeon -> values;
    }

    /**
     * Runs aliases.
     */
    private static List<String> aliases(String... aliases) {
        return Arrays.stream(aliases).map(alias -> alias.toLowerCase(Locale.ROOT)).collect(Collectors.toList());
    }
}
