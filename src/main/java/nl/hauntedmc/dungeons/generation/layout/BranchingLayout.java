package nl.hauntedmc.dungeons.generation.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.WhitelistEntry;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.math.RandomCollection;
import nl.hauntedmc.dungeons.util.math.RangedNumber;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;

/**
 * Layout algorithm that builds a trunk path and optional side branches.
 *
 * <p>This layout tries to satisfy required rooms and branch constraints while keeping the main path
 * within a configured room target.
 */
public class BranchingLayout extends Layout {
    private double straightness;
    private RangedNumber branchDepth;
    private RangedNumber branchOccurrences;
    private int minBranchRooms;
    private int maxBranchRooms;
    private boolean strictBranches;
    private int targetSize;
    private int branchCount;

    /**
     * Creates a branching layout with trunk and branch settings loaded from configuration.
     */
    public BranchingLayout(BranchingDungeon dungeon, YamlConfiguration config) {
        super(dungeon, config);
    }

    @Override
        public LayoutValidationReport validateConfiguration() {
        LayoutValidationReport report = super.validateConfiguration();
        this.validateStraightness(
                report,
                "branching.trunk.straightness",
                this.config.getDouble("branching.trunk.straightness", 0.5));
        this.validateRoomTarget(
                report,
                "branching.trunk.room_target",
                this.config.getInt("branching.trunk.room_target.min", 12),
                this.config.getInt("branching.trunk.room_target.max", 24));

        ConfigurationSection branchesSection =
                this.config.getConfigurationSection("branching.branches");
        if (branchesSection == null) {
            return report;
        }

        for (String branchName : branchesSection.getKeys(false)) {
            String prefix = "branching.branches." + branchName;
            this.validateStraightness(
                    report,
                    prefix + ".straightness",
                    branchesSection.getDouble(branchName + ".straightness", 0.5));
            this.validateRoomTarget(
                    report,
                    prefix + ".room_target",
                    branchesSection.getInt(branchName + ".room_target.min", 6),
                    branchesSection.getInt(branchName + ".room_target.max", 12));
            this.validateRangedNumber(
                    report,
                    prefix + ".eligible_depth",
                    branchesSection.getString(branchName + ".eligible_depth", "0+"),
                    true);
            this.validateRangedNumber(
                    report,
                    prefix + ".occurrences",
                    branchesSection.getString(branchName + ".occurrences", "0+"),
                    true);
        }

        return report;
    }

    @Override
        protected Layout.GenerationResult tryLayout() {
        RuntimeContext.logger()
                .info("Generating branching layout for dungeon '{}'.", this.dungeon.getWorldName());

        this.roomWhitelist = this.resolveRooms(this.config.getStringList("branching.trunk.room_pool"));
        this.endRoomWhitelist =
                this.resolveRooms(this.config.getStringList("branching.trunk.end_room_pool"));
        this.straightness = this.config.getDouble("branching.trunk.straightness", 0.5);
        this.minBranchRooms = this.config.getInt("branching.trunk.room_target.min", 12);
        this.maxBranchRooms = this.config.getInt("branching.trunk.room_target.max", 24);
        this.strictRooms = this.config.getBoolean("branching.trunk.room_target.enforce_minimum", true);
        this.roomCount = 0;
        this.totalRooms = 0;
        this.branchCount = 0;
        this.queuedRoomCount = 0;
        this.countsByRoom.clear();
        this.requiredRooms.clear();
        this.roomAreas.clear();
        this.roomAnchors.clear();
        this.queue.clear();
        this.first = null;
        this.generationCheckpoint = null;
        this.roomsToAdd = new ArrayList<>();
        this.queuedCountsByRoom = new HashMap<>();
        this.queuedRoomAreas = new ArrayList<>();
        this.queuedRoomAnchors = new HashMap<>();
        this.targetSize = MathUtils.getRandomNumberInRange(this.minBranchRooms, this.maxBranchRooms);

        List<Integer> available = new ArrayList<>();
        for (int i = 1; i <= this.targetSize; i++) {
            available.add(i);
        }

        labelRequired:
        for (BranchingRoomDefinition room : this.dungeon.getUniqueRooms().values()) {
            if (room.getOccurrences().getMin() > 0.0) {
                for (int i = 0; i < room.getOccurrences().getMin(); i++) {
                    int depth = MathUtils.getRandomNumberInRange(1, this.targetSize);

                    while (this.requiredRooms.containsKey(depth) && !this.cancelled) {
                        if (this.requiredRooms.size() >= this.targetSize) {
                            this.recordFailure(GenerationFailureReason.BRANCH_TARGET_TOO_SMALL);
                            continue labelRequired;
                        }

                        depth = MathUtils.getRandomNumber(available.toArray(new Integer[0]));
                        if (depth == -1) {
                            this.recordFailure(GenerationFailureReason.BRANCH_TARGET_TOO_SMALL);
                            continue labelRequired;
                        }
                    }

                    this.requiredRooms.put(depth, room);
                    available.remove(Integer.valueOf(depth));
                }
            }
        }

        Layout.GenerationResult result = this.generateTrunk();
        if (!result.isPassed()) {
            return result;
        } else {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .info(
                            "Generated trunk for dungeon '{}' with {} rooms.",
                            this.dungeon.getWorldName(),
                            this.roomCount);

            result = this.generateAllBranches();
            if (!result.isPassed()) {
                return result;
            } else if (!this.verifyLayout()) {
                return Layout.GenerationResult.BAD_LAYOUT;
            } else {
                RuntimeContext.logger()
                        .info("Found valid branching layout for dungeon '{}'.", this.dungeon.getWorldName());
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .info(
                                "Dungeon '{}' layout contains {} rooms across {} branches.",
                                this.dungeon.getWorldName(),
                                this.totalRooms,
                                this.branchCount);
                return Layout.GenerationResult.SUCCESS;
            }
        }
    }

        protected Layout.GenerationResult generateTrunk() {
        if (!this.selectFirstRoom()) {
            return Layout.GenerationResult.NO_START_ROOM;
        } else {
            this.generationCheckpoint = this.first;
            this.queuedRoomCount = this.roomCount;
            this.queuedCountsByRoom = new HashMap<>(this.countsByRoom);
            this.queuedRoomAreas = new ArrayList<>(this.roomAreas);
            this.queuedRoomAnchors = new HashMap<>(this.roomAnchors);
            int segmentTries = 0;

            while (segmentTries <= 50) {
                segmentTries++;
                InstanceRoom prevCheckpoint = this.generationCheckpoint;
                List<Connector> usedCheckConnectors =
                        new ArrayList<>(this.generationCheckpoint.getUsedConnectors());
                int savedRoomCount = this.queuedRoomCount;
                List<BoundingBox> savedRoomAreas = new ArrayList<>(this.queuedRoomAreas);
                Map<BranchingRoomDefinition, Integer> savedCountsByRoom =
                        new HashMap<>(this.queuedCountsByRoom);
                Map<SimpleLocation, InstanceRoom> savedRoomAnchors = new HashMap<>(this.queuedRoomAnchors);
                List<InstanceRoom> savedRoomsToAdd = new ArrayList<>(this.roomsToAdd);

                Layout.GenerationResult result = this.processQueue(25);
                if (!result.isPassed()) {
                    RuntimeContext.plugin()
                            .getSLF4JLogger()
                            .warn(
                                    "Trunk segment generation failed for dungeon '{}': {}",
                                    this.dungeon.getWorldName(),
                                    result.getMsg());
                }

                if (this.queuedRoomCount >= this.minBranchRooms) {
                    break;
                }

                if (this.generationCheckpoint == null) {
                    this.generationCheckpoint = prevCheckpoint;
                    this.generationCheckpoint.setUsedConnectors(usedCheckConnectors);
                    this.queue.add(this.generationCheckpoint);
                    this.queuedRoomCount = savedRoomCount;
                    this.queuedRoomAreas = savedRoomAreas;
                    this.queuedCountsByRoom = savedCountsByRoom;
                    this.queuedRoomAnchors = savedRoomAnchors;
                    this.roomsToAdd = savedRoomsToAdd;
                } else {
                    this.queue.add(this.generationCheckpoint);
                    RuntimeContext.plugin()
                            .getSLF4JLogger()
                            .info(
                                    "Generating trunk for dungeon '{}': {} / {} rooms.",
                                    this.dungeon.getWorldName(),
                                    this.queuedRoomCount,
                                    this.minBranchRooms);

                    segmentTries = 0;
                }
            }

            if (this.strictRooms && this.queuedRoomCount < this.minBranchRooms) {
                this.recordFailure(GenerationFailureReason.BRANCH_TARGET_TOO_SMALL);
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .warn(
                                "Failed to generate trunk for dungeon '{}': required at least {} rooms but generated {}.",
                                this.dungeon.getWorldName(),
                                this.minBranchRooms,
                                this.queuedRoomCount);

                return Layout.GenerationResult.BAD_LAYOUT;
            } else {
                this.generationCheckpoint = null;

                for (InstanceRoom room : this.roomsToAdd) {
                    this.addRoom(room);
                }

                return Layout.GenerationResult.SUCCESS;
            }
        }
    }

        protected Layout.GenerationResult generateAllBranches() {
        int totalBranches = 0;
        ConfigurationSection branchesSection =
                this.config.getConfigurationSection("branching.branches");
        if (branchesSection != null) {
            for (String path : branchesSection.getKeys(false)) {
                this.roomWhitelist.clear();
                this.branchCount = 0;

                this.roomWhitelist = this.resolveRooms(branchesSection.getStringList(path + ".room_pool"));
                this.endRoomWhitelist =
                        this.resolveRooms(branchesSection.getStringList(path + ".end_room_pool"));

                String depthString = branchesSection.getString(path + ".eligible_depth", "0+");
                this.branchDepth = new RangedNumber(depthString);
                String occurrencesString = branchesSection.getString(path + ".occurrences", "0+");
                this.branchOccurrences = new RangedNumber(occurrencesString);
                this.straightness = branchesSection.getDouble(path + ".straightness", 0.5);
                this.minBranchRooms = branchesSection.getInt(path + ".room_target.min", 6);
                this.maxBranchRooms = branchesSection.getInt(path + ".room_target.max", 12);
                this.strictRooms = branchesSection.getBoolean(path + ".room_target.enforce_minimum", true);
                this.strictBranches = branchesSection.getBoolean(path + ".enforce_occurrences", true);

                RuntimeContext.logger()
                        .info("Generating branch '{}' for dungeon '{}'.", path, this.dungeon.getWorldName());

                Layout.GenerationResult result = this.generateBranches();
                if (!result.isPassed()) {
                    RuntimeContext.logger()
                            .warn(
                                    "Failed to generate branch '{}' for dungeon '{}'.",
                                    path,
                                    this.dungeon.getWorldName());
                    return result;
                }

                totalBranches += this.branchCount;
            }
        }

        this.branchCount = totalBranches;
        return Layout.GenerationResult.SUCCESS;
    }

        private List<BranchingRoomDefinition> resolveRooms(List<String> roomNames) {
        List<BranchingRoomDefinition> resolved = new ArrayList<>();
        for (String roomName : roomNames) {
            BranchingRoomDefinition room = this.dungeon.getRoom(roomName);
            if (room != null) {
                resolved.add(room);
            }
        }
        return resolved;
    }

        private void validateRoomTarget(
            LayoutValidationReport report, String pathPrefix, int min, int max) {
        if (min > max) {
            report.addError(pathPrefix + ".min is greater than " + pathPrefix + ".max.");
        }
    }

        private void validateStraightness(LayoutValidationReport report, String path, double value) {
        if (value < 0.0 || value > 1.0) {
            report.addError(path + " must be between 0.0 and 1.0.");
        }
    }

        private void validateRangedNumber(
            LayoutValidationReport report, String path, String rawValue, boolean nonNegativeOnly) {
        try {
            RangedNumber range = new RangedNumber(rawValue);
            if (nonNegativeOnly
                    && (range.getMin() < 0.0 || (range.getMax() != -1.0 && range.getMax() < 0.0))) {
                report.addError(path + " cannot be negative.");
            }
        } catch (RuntimeException exception) {
            report.addError(path + " is not a valid ranged number.");
        }
    }

        protected Layout.GenerationResult generateBranches() {
        ArrayList<InstanceRoom> rooms = new ArrayList<>(this.getAllRooms());
        rooms.removeIf(
                room ->
                        room.isEndRoom()
                                || room.isStartRoom()
                                || room.getAvailableConnectors().isEmpty()
                                || !this.branchDepth.isValueWithin(room.getDepth()));

        int minOccurrences = Math.max(0, (int) this.branchOccurrences.getMin());
        int maxOccurrences =
                this.branchOccurrences.getMax() == -1.0
                        ? Math.max(minOccurrences, rooms.size())
                        : Math.max(minOccurrences, (int) this.branchOccurrences.getMax());
        int targetBranches = MathUtils.getRandomNumberInRange(minOccurrences, maxOccurrences);
        if (targetBranches <= 0) {
            return Layout.GenerationResult.SUCCESS;
        }

        int branchTries = 0;
        int maxBranchTries = Math.max(10, targetBranches * 5);

        while (branchTries <= maxBranchTries && !rooms.isEmpty() && this.branchCount < targetBranches) {
            branchTries++;
            this.queuedRoomCount = 0;
            this.roomCount = 0;
            this.targetSize = MathUtils.getRandomNumberInRange(this.minBranchRooms, this.maxBranchRooms);

            InstanceRoom branchRoom = rooms.get(MathUtils.getRandomNumberInRange(0, rooms.size() - 1));
            Layout.GenerationResult result = this.generateBranch(branchRoom);

            if (result.isPassed() && this.queuedRoomCount > 0 && this.verifyBranch()) {
                this.branchCount++;

                for (InstanceRoom room : this.roomsToAdd) {
                    this.addRoom(room);
                }

                if (this.branchCount >= targetBranches) {
                    break;
                }

                branchTries = 0;
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .info(
                                "Generated branch {} for dungeon '{}' with {} rooms.",
                                this.branchCount,
                                this.dungeon.getWorldName(),
                                this.roomCount);

                if (branchRoom.getAvailableConnectors().isEmpty()) {
                    rooms.remove(branchRoom);
                }
            } else {
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .warn(
                                "Failed to generate branch for dungeon '{}': {}",
                                this.dungeon.getWorldName(),
                                result.getMsg());

                if (branchRoom.getAvailableConnectors().isEmpty()) {
                    rooms.remove(branchRoom);
                }
            }
        }

        if (this.strictBranches && !(this.branchCount >= this.branchOccurrences.getMin())) {
            this.recordFailure(GenerationFailureReason.MISSING_REQUIRED_ROOM);
            return Layout.GenerationResult.BAD_LAYOUT;
        }

        return Layout.GenerationResult.SUCCESS;
    }

        protected Layout.GenerationResult generateBranch(InstanceRoom startRoom) {
        this.queue.clear();
        this.queue.add(startRoom);
        this.generationCheckpoint = startRoom;
        this.roomsToAdd = new ArrayList<>();
        this.queuedCountsByRoom = new HashMap<>(this.countsByRoom);
        this.queuedRoomAreas = new ArrayList<>();
        this.queuedRoomAnchors = new HashMap<>();
        int segmentTries = 0;

        while (segmentTries <= 50) {
            segmentTries++;
            InstanceRoom prevCheckpoint = this.generationCheckpoint;
            List<Connector> usedCheckConnectors =
                    new ArrayList<>(this.generationCheckpoint.getUsedConnectors());
            int savedRoomCount = this.queuedRoomCount;
            List<BoundingBox> savedRoomAreas = new ArrayList<>(this.queuedRoomAreas);
            Map<BranchingRoomDefinition, Integer> savedCountsByRoom =
                    new HashMap<>(this.queuedCountsByRoom);
            Map<SimpleLocation, InstanceRoom> savedRoomAnchors = new HashMap<>(this.queuedRoomAnchors);
            List<InstanceRoom> savedRoomsToAdd = new ArrayList<>(this.roomsToAdd);

            Layout.GenerationResult result = this.processQueue(25);
            if (!result.isPassed()) {
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .warn(
                                "Branch segment generation failed for dungeon '{}': {}",
                                this.dungeon.getWorldName(),
                                result.getMsg());
            }

            if (this.queuedRoomCount >= this.minBranchRooms) {
                return Layout.GenerationResult.SUCCESS;
            }

            if (this.generationCheckpoint == null) {
                this.generationCheckpoint = prevCheckpoint;
                this.generationCheckpoint.setUsedConnectors(usedCheckConnectors);
                this.queuedRoomCount = savedRoomCount;
                this.queuedRoomAreas = savedRoomAreas;
                this.queuedCountsByRoom = savedCountsByRoom;
                this.queuedRoomAnchors = savedRoomAnchors;
                this.roomsToAdd = savedRoomsToAdd;
                this.queue.add(this.generationCheckpoint);
            } else {
                this.queue.add(this.generationCheckpoint);
                segmentTries = 0;
            }
        }

        this.recordFailure(GenerationFailureReason.BRANCH_TARGET_TOO_SMALL);
        return Layout.GenerationResult.BAD_LAYOUT;
    }

    @Override
        protected boolean tryConnectors(InstanceRoom from) {
        if (!from.getAvailableConnectors().isEmpty()) {
            boolean straight = MathUtils.getRandomBoolean(this.straightness);
            if (straight && from.getStartConnector() != null) {
                List<Connector> connectors =
                        from.getConnectorsByDirection(
                                from.getStartConnector().getLocation().getDirection().getOpposite());
                Collections.shuffle(connectors);

                for (Connector connector : connectors) {
                    if (this.queuedRoomCount >= this.targetSize) {
                        from.setEndRoom(true);
                        return false;
                    }

                    if (this.tryConnector(connector, from)) {
                        from.addUsedConnector(connector);
                        return true;
                    }
                }
            }

            List<Connector> connectors = from.getAvailableConnectors();
            Collections.shuffle(connectors);

            for (Connector connector : connectors) {
                if (this.queuedRoomCount >= this.targetSize) {
                    from.setEndRoom(true);
                    return false;
                }

                if (this.tryConnector(connector, from)) {
                    from.addUsedConnector(connector);
                    return true;
                }
            }
        }

        return false;
    }

        protected boolean verifyBranch() {
        if (!this.strictRooms) {
            return true;
        }

        if (this.queuedRoomCount >= this.minBranchRooms) {
            return true;
        }

        this.recordFailure(GenerationFailureReason.BRANCH_TARGET_TOO_SMALL);
        return false;
    }

    @Override
        public RandomCollection<BranchingRoomDefinition> filterRooms(
            Connector connector, InstanceRoom from) {
        int nextDepth = from.getDepth() + 1;
        List<BranchingRoomDefinition> fullWhitelist = new ArrayList<>();
        fullWhitelist.addAll(this.roomWhitelist);
        fullWhitelist.addAll(this.endRoomWhitelist);

        List<WhitelistEntry> validRooms =
                connector.getValidRooms(this.dungeon, from.getSource().getOrigin());
        RandomCollection<BranchingRoomDefinition> weightedRooms = new RandomCollection<>();
        boolean finalSlot = this.queuedRoomCount == this.targetSize - 1;

        for (WhitelistEntry entry : validRooms) {
            BranchingRoomDefinition room = entry.getRoom(this.dungeon);
            if (room == null) {
                continue;
            }

            boolean allowedByBranchWhitelist =
                    this.roomWhitelist.isEmpty() || fullWhitelist.contains(room);
            boolean allowedByDepth =
                    !(nextDepth < room.getDepth().getMin())
                            && (room.getDepth().getMax() == -1.0 || !(nextDepth > room.getDepth().getMax()));
            boolean allowedByOccurrences = !this.isRoomMaxedOut(room);

            boolean allowedByTerminalRule;
            if (finalSlot) {
                allowedByTerminalRule = this.isValidEndRoomCandidate(room);
                if (!allowedByTerminalRule) {
                    this.recordFailure(GenerationFailureReason.TERMINAL_ROOM_REQUIRED);
                }
            } else {
                allowedByTerminalRule =
                        room.getConnectors().size() != 1 || this.isConfiguredStartRoom(room);
            }

            if (!allowedByDepth) {
                this.recordFailure(GenerationFailureReason.DEPTH_OUT_OF_RANGE);
            }

            if (!allowedByOccurrences) {
                this.recordFailure(GenerationFailureReason.ROOM_MAXED_OUT);
            }

            if (allowedByBranchWhitelist
                    && allowedByDepth
                    && allowedByOccurrences
                    && allowedByTerminalRule) {
                double weight = entry.getWeight();
                int count = this.getRoomCount(room);

                if (count < room.getOccurrences().getMin()
                        && room.getDepth().getMin() == room.getDepth().getMax()
                        && nextDepth == room.getDepth().getMin()) {
                    weightedRooms.clear();
                    weightedRooms.add(room.getWeight(), room);
                    return weightedRooms;
                }

                weightedRooms.add(weight, room);
            }
        }

        BranchingRoomDefinition requiredRoom = this.requiredRooms.get(nextDepth);
        if (requiredRoom != null) {
            if (!weightedRooms.contains(requiredRoom)) {
                this.recordFailure(GenerationFailureReason.REQUIRED_ROOM_UNREACHABLE);
                this.requiredRooms.remove(nextDepth);
                this.requiredRooms.put(nextDepth + 1, requiredRoom);
            } else {
                weightedRooms.clear();
                weightedRooms.add(1.0, requiredRoom);
            }
        }

        return weightedRooms;
    }

    /**
     * Returns the configured trunk straightness bias in the range {@code [0,1]}.
     */
    public double getStraightness() {
        return this.straightness;
    }

    /**
     * Returns the eligible depth range where side branches may start.
     */
    public RangedNumber getBranchDepth() {
        return this.branchDepth;
    }

    /**
     * Returns how many branches may be created for the current generation run.
     */
    public RangedNumber getBranchOccurrences() {
        return this.branchOccurrences;
    }

    /**
     * Returns the minimum branch room target for the active branch profile.
     */
    public int getMinBranchRooms() {
        return this.minBranchRooms;
    }

    /**
     * Returns the maximum branch room target for the active branch profile.
     */
    public int getMaxBranchRooms() {
        return this.maxBranchRooms;
    }

    /**
     * Returns whether branch generation enforces the minimum target strictly.
     */
    public boolean isStrictBranches() {
        return this.strictBranches;
    }
}
