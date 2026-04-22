package nl.hauntedmc.dungeons.content.trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Trigger that fires after a configured number of player deaths.
 */
@AutoRegister(id = "dungeons.trigger.player_death")
@SerializableAs("dungeons.trigger.player_death")
public class PlayerDeathTrigger extends DungeonTrigger {
    @PersistedField private int deathsRequired = 1;
    @PersistedField private boolean oneDeathPerPlayer = false;
    private final List<UUID> playerDeaths;
    private transient boolean invalidDeathRequirementLogged;

    /**
     * Creates a new PlayerDeathTrigger instance.
     */
    public PlayerDeathTrigger(Map<String, Object> config) {
        super("Player Death", config);
        this.waitForConditions = true;
        this.playerDeaths = new ArrayList<>();
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
    }

    /**
     * Creates a new PlayerDeathTrigger instance.
     */
    public PlayerDeathTrigger() {
        super("Player Death");
        this.waitForConditions = true;
        this.playerDeaths = new ArrayList<>();
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
    }

    /**
     * Performs on player death.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();
        if (world == this.instance.getInstanceWorld()) {
            int requiredDeaths = Math.max(1, this.deathsRequired);
            if (requiredDeaths != this.deathsRequired && !this.invalidDeathRequirementLogged) {
                this.invalidDeathRequirementLogged = true;
                this.logger()
                        .warn(
                                "PlayerDeathTrigger in dungeon '{}' at {} had invalid required deaths {}. Clamping to 1.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                this.deathsRequired);
            }

            if (this.playerDeaths.size() < requiredDeaths) {
                if (!this.oneDeathPerPlayer || !this.playerDeaths.contains(player.getUniqueId())) {
                    if (this.matchesRoom(player.getLocation())) {
                        this.playerDeaths.add(player.getUniqueId());
                        if (this.playerDeaths.size() >= requiredDeaths) {
                            this.trigger(RuntimeContext.playerSessions().get(player));
                            if (this.allowRetrigger) {
                                this.playerDeaths.clear();
                            }
                        }
                    }
                }
            }
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
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.PLAYER_HEAD);
        functionButton.setDisplayName("&playerSession Death Counter");
        functionButton.addLore("&eTriggered when a certain number of");
        functionButton.addLore("&eplayers have died.");
        return functionButton;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.BONE);
                        this.button.setDisplayName("&d&lAmount");
                        this.button.setAmount(PlayerDeathTrigger.this.deathsRequired);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.trigger.player-death.ask-count");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        PlayerDeathTrigger.this.deathsRequired =
                                value.orElse(PlayerDeathTrigger.this.deathsRequired);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.trigger.player-death.count-set",
                                    LangUtils.placeholder(
                                            "count", String.valueOf(PlayerDeathTrigger.this.deathsRequired)));
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
                        this.button = new MenuButton(Material.REDSTONE);
                        this.button.setDisplayName("&d&lToggle Deaths Per Player");
                        this.button.setEnchanted(PlayerDeathTrigger.this.oneDeathPerPlayer);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!PlayerDeathTrigger.this.oneDeathPerPlayer) {
                            LangUtils.sendMessage(player, "editor.trigger.player-death.count-one-per-player");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.player-death.count-all");
                        }

                        PlayerDeathTrigger.this.oneDeathPerPlayer = !PlayerDeathTrigger.this.oneDeathPerPlayer;
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lAllow Retrigger");
                        this.button.setEnchanted(PlayerDeathTrigger.this.allowRetrigger);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!PlayerDeathTrigger.this.allowRetrigger) {
                            LangUtils.sendMessage(player, "editor.trigger.player-death.allow-repeat");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.player-death.prevent-repeat");
                        }

                        PlayerDeathTrigger.this.allowRetrigger = !PlayerDeathTrigger.this.allowRetrigger;
                    }
                });
        this.addRoomLimitToggleButton();
    }
}
