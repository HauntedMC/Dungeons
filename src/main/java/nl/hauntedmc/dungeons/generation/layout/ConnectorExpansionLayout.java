package nl.hauntedmc.dungeons.generation.layout;

import java.util.Optional;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.WhitelistEntry;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.math.RandomCollection;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Layout algorithm that expands outward connector by connector until room targets are met.
 */
public class ConnectorExpansionLayout extends Layout {
    /**
     * Creates a connector-expansion layout driven by generator configuration values.
     */
    public ConnectorExpansionLayout(BranchingDungeon dungeon, YamlConfiguration config) {
        super(dungeon, config);
    }

    @Override
        protected boolean tryConnectors(InstanceRoom from) {
        if (from.getAvailableConnectors().isEmpty()) {
            return false;
        } else {
            for (Connector connector : from.getAvailableConnectors()) {
                if (MathUtils.getRandomBoolean(connector.getSuccessChance())) {
                    if (this.queuedRoomCount >= this.maxRooms) {
                        from.setEndRoom(true);
                        return false;
                    }

                    if (this.tryConnector(connector, from)) {
                        from.getUsedConnectors().add(connector);
                    }
                }
            }

            return true;
        }
    }

    /**
     * Builds weighted room candidates for the connector based on depth and occurrence constraints.
     */
    @Override
    public RandomCollection<BranchingRoomDefinition> filterRooms(
            Connector connector, InstanceRoom from) {
        int nextDepth = from.getDepth() + 1;
        RandomCollection<BranchingRoomDefinition> weightedRooms = new RandomCollection<>();

        for (WhitelistEntry entry :
                connector.getValidRooms(this.dungeon, from.getSource().getOrigin())) {
            BranchingRoomDefinition room = entry.getRoom(this.dungeon);
            if (room == null) {
                continue;
            }

            if (!(nextDepth < room.getDepth().getMin())
                    && (room.getDepth().getMax() == -1.0 || !(nextDepth > room.getDepth().getMax()))
                    && !this.isRoomMaxedOut(room)) {
                double weight = entry.getWeight();
                int count = this.getRoomCount(room);
                if (count < room.getOccurrences().getMin()) {
                    weight *= room.getOccurrences().getMin() - count + 1.0;
                }

                weightedRooms.add(weight, room);
            }
        }

        return weightedRooms;
    }

    @Override
        protected boolean verifyLayout() {
        if (this.roomCount < this.minRooms) {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .warn(
                            "Generated layout for dungeon '{}' only has {} rooms; minimum required is {}.",
                            this.dungeon.getWorldName(),
                            this.roomCount,
                            this.minRooms);
        }

        return super.verifyLayout()
                && this.roomCount >= this.minRooms
                && this.roomCount <= this.maxRooms;
    }

    @Override
        protected boolean requiresGlobalRoomTargetValidation() {
        return true;
    }

    @Override
        public void initializeConnectorEditMenu() {
        super.initializeConnectorEditMenu();
        if (this.dungeon.getLayout() instanceof ConnectorExpansionLayout) {
            this.connectorEditMenu.addMenuItem(
                                        new ChatMenuItem() {
                        @Override
                                                public void buildButton() {
                            this.button = new MenuButton(Material.ENDER_EYE);
                            this.button.setDisplayName("&d&lConnector Use Chance");
                            this.button.addLore("&eHow likely a room will generate");
                            this.button.addLore("&eat this connector.");
                        }

                        @Override
                                                public void onSelect(Player player) {
                            DungeonPlayerSession playerSession =
                                    RuntimeContext.playerSessions().get(player);
                            Connector connector = playerSession.getActiveConnector();
                            if (connector != null) {
                                double chance = connector.getSuccessChance();
                                LangUtils.sendMessage(player, "editor.layout.minecrafty.ask-success-chance");
                                LangUtils.sendMessage(
                                        player,
                                        "editor.layout.minecrafty.current-success-chance",
                                        LangUtils.placeholder("chance", String.valueOf(MathUtils.round(chance, 2))));
                            }
                        }

                        @Override
                                                public void onInput(Player player, String message) {
                            DungeonPlayerSession playerSession =
                                    RuntimeContext.playerSessions().get(player);
                            Connector connector = playerSession.getActiveConnector();
                            if (connector != null) {
                                Optional<Double> value = InputUtils.readDoubleInput(player, message);
                                connector.setSuccessChance(value.orElse(connector.getSuccessChance()) / 100.0);
                                if (value.isPresent()) {
                                    double chance = value.get();
                                    LangUtils.sendMessage(
                                            player,
                                            "editor.layout.minecrafty.success-chance-set",
                                            LangUtils.placeholder("chance", String.valueOf(MathUtils.round(chance, 2))));
                                    if (chance >= 1.0) {
                                        LangUtils.sendMessage(player, "editor.layout.minecrafty.success-chance-note");
                                    }
                                }
                            }
                        }
                    });
        }
    }
}
