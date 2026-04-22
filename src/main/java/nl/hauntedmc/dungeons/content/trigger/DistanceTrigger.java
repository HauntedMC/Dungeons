package nl.hauntedmc.dungeons.content.trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.instance.play.OpenInstance;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.entity.PlayerUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Trigger that periodically scans for players near its location.
 *
 * <p>The trigger can fire once per group or once per player and can optionally require the entire
 * living party by using the special {@code -1} count value.
 */
@AutoRegister(id = "dungeons.trigger.distance")
@SerializableAs("dungeons.trigger.distance")
public class DistanceTrigger extends DungeonTrigger {
    private BukkitRunnable playerScan;
    @PersistedField private double radius = 3.0;
    @PersistedField private int count = 1;
    @PersistedField private boolean forEachPlayer = false;
    private int finalCount;
    private transient boolean invalidConfigurationLogged;

    /**
     * Creates a new DistanceTrigger instance.
     */
    public DistanceTrigger(Map<String, Object> config) {
        super("Player Detector", config);
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
    }

    /**
     * Creates a new DistanceTrigger instance.
     */
    public DistanceTrigger() {
        super("Player Detector");
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.DETECTOR_RAIL);
        functionButton.setDisplayName("&playerSession Detector");
        functionButton.addLore("&eTriggered when a player or players");
        functionButton.addLore("&eare within range of this location.");
        return functionButton;
    }

    /**
     * Performs on enable.
     */
    @Override
    public void onEnable() {
        final PlayableInstance instance = this.instance.asPlayInstance();
        if (instance != null) {
            double effectiveRadius = Math.max(0.0, this.radius);
            if ((effectiveRadius != this.radius || this.count < -1 || this.count == 0)
                    && !this.invalidConfigurationLogged) {
                this.invalidConfigurationLogged = true;
                this.logger()
                        .warn(
                                "DistanceTrigger in dungeon '{}' at {} had invalid radius/count ({}, {}). Values were normalized.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                this.radius,
                                this.count);
            }

            if (this.count == -1) {
                this.finalCount = Math.max(instance.getLivingPlayers().size(), 1);
            } else {
                this.finalCount = Math.max(1, this.count);
            }

            final TriggerFireEvent event = new TriggerFireEvent(instance, this);
            this.playerScan =
                                        new BukkitRunnable() {
                        /**
                         * Performs run.
                         */
                        public void run() {
                            if (DistanceTrigger.this.count == -1) {
                                DistanceTrigger.this.finalCount = Math.max(instance.getLivingPlayers().size(), 1);
                            }

                            if (!DistanceTrigger.this.location.isWorldLoaded()) {
                                this.cancel();
                            } else if (DistanceTrigger.this.checkConditions(event)) {
                                List<Player> players =
                                        PlayerUtils.getPlayersWithin(
                                                DistanceTrigger.this.location,
                                                effectiveRadius,
                                                GameMode.SURVIVAL,
                                                GameMode.ADVENTURE);
                                players.removeIf(
                                        candidatePlayer ->
                                                !DistanceTrigger.this.matchesRoom(candidatePlayer.getLocation()));

                                // Already-triggered players are filtered out so non-retriggerable
                                // detectors do not fire repeatedly for the same presence.
                                for (UUID uuid : DistanceTrigger.this.playersTriggered) {
                                    Player triggeredPlayer = Bukkit.getPlayer(uuid);
                                    players.remove(triggeredPlayer);
                                }

                                List<UUID> playersFoundSnapshot =
                                        new ArrayList<>(DistanceTrigger.this.playersFound);
                                if (DistanceTrigger.this.allowRetrigger) {
                                    for (UUID uuid : playersFoundSnapshot) {
                                        Player player = Bukkit.getPlayer(uuid);
                                        if (!players.contains(player)) {
                                            DistanceTrigger.this.playersFound.remove(uuid);
                                        }
                                    }
                                }

                                if (players.size() >= DistanceTrigger.this.finalCount) {
                                    if (DistanceTrigger.this.forEachPlayer) {
                                        for (Player player : players) {
                                            DungeonPlayerSession dungeonPlayer =
                                                    RuntimeContext.playerSessions().get(player);
                                            if (!DistanceTrigger.this.hasTrackedTarget(dungeonPlayer)) {
                                                DistanceTrigger.this.rememberTriggeredTargets(dungeonPlayer);
                                                DistanceTrigger.this.trigger(dungeonPlayer, false);
                                            }
                                        }

                                        return;
                                    }

                                    boolean foundNewTarget = false;
                                    DungeonPlayerSession triggerSource = null;
                                    for (Player candidatePlayer : players) {
                                        DungeonPlayerSession dungeonPlayer =
                                                RuntimeContext.playerSessions().get(candidatePlayer);
                                        if (!DistanceTrigger.this.hasTrackedTarget(dungeonPlayer)) {
                                            DistanceTrigger.this.rememberTriggeredTargets(dungeonPlayer);
                                            foundNewTarget = true;
                                            if (triggerSource == null) {
                                                triggerSource = dungeonPlayer;
                                            }
                                        }
                                    }

                                    if (!foundNewTarget) {
                                        return;
                                    }

                                    DistanceTrigger.this.trigger(triggerSource);
                                }
                            }
                        }
                    };
            this.playerScan.runTaskTimer(RuntimeContext.plugin(), 0L, 10L);
        }
    }

    /**
     * Performs matches room.
     */
    private boolean matchesRoom(Location origin) {
        BranchingInstance instance = this.instance.as(BranchingInstance.class);
        if (instance == null) {
            return true;
        } else if (!this.limitToRoom) {
            return true;
        } else {
            InstanceRoom originRoom = instance.getRoom(origin);
            InstanceRoom thisRoom = instance.getRoom(this.location);
            return thisRoom == originRoom;
        }
    }

    /**
     * Performs on disable.
     */
    @Override
    public void onDisable() {
        if (this.playerScan != null && !this.playerScan.isCancelled()) {
            this.playerScan.cancel();
            this.playerScan = null;
        }
    }

    /**
     * Performs on trigger.
     */
    @Override
    public void onTrigger(TriggerFireEvent event) {
        if (!this.allowRetrigger
                && !(this.instance instanceof OpenInstance)
                && this.playerScan != null
                && !this.playerScan.isCancelled()) {
            this.playerScan.cancel();
        }
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lAllow Retrigger");
                        this.button.setEnchanted(DistanceTrigger.this.allowRetrigger);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!DistanceTrigger.this.allowRetrigger) {
                            LangUtils.sendMessage(player, "editor.trigger.distance.allow-repeat");
                            LangUtils.sendMessage(player, "editor.trigger.distance.allow-repeat-note");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.distance.prevent-repeat");
                        }

                        DistanceTrigger.this.allowRetrigger = !DistanceTrigger.this.allowRetrigger;
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.DETECTOR_RAIL);
                        this.button.setDisplayName("&d&lDistance");
                        this.button.setAmount((int) MathUtils.round(DistanceTrigger.this.radius, 0));
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.trigger.distance.ask-radius");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Double> value = InputUtils.readDoubleInput(player, message);
                        DistanceTrigger.this.radius = value.orElse(DistanceTrigger.this.radius);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.trigger.distance.radius-set",
                                    LangUtils.placeholder("radius", String.valueOf(DistanceTrigger.this.radius)));
                        }
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PLAYER_HEAD);
                        this.button.setDisplayName("&d&lMinimum Players");
                        this.button.setAmount(DistanceTrigger.this.count);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.trigger.distance.ask-player-count");
                        LangUtils.sendMessage(player, "editor.trigger.distance.enter-all");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        if (message.equalsIgnoreCase("all")) {
                            DistanceTrigger.this.count = -1;
                            LangUtils.sendMessage(player, "editor.trigger.distance.count-set-all");
                        } else {
                            Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                            DistanceTrigger.this.count = value.orElse(DistanceTrigger.this.count);
                            if (value.isPresent()) {
                                LangUtils.sendMessage(
                                        player,
                                        "editor.trigger.distance.count-set",
                                        LangUtils.placeholder("count", String.valueOf(DistanceTrigger.this.count)));
                            }
                        }
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.TARGET);
                        this.button.setDisplayName("&d&lFor Each Player");
                        this.button.setEnchanted(DistanceTrigger.this.forEachPlayer);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!DistanceTrigger.this.forEachPlayer) {
                            LangUtils.sendMessage(player, "editor.trigger.distance.behaviour-all-in-range");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.distance.behaviour-nearest");
                        }

                        DistanceTrigger.this.forEachPlayer = !DistanceTrigger.this.forEachPlayer;
                    }
                });
        this.addRoomLimitToggleButton();
    }

    /**
     * Sets the radius.
     */
    public void setRadius(double radius) {
        this.radius = radius;
    }

    /**
     * Sets the count.
     */
    public void setCount(int count) {
        this.count = count;
    }

    /**
     * Sets the for each player.
     */
    public void setForEachPlayer(boolean forEachPlayer) {
        this.forEachPlayer = forEachPlayer;
    }
}
