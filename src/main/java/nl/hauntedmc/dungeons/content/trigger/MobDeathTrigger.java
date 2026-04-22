package nl.hauntedmc.dungeons.content.trigger;

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
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Trigger that listens for matching mob deaths inside the instance.
 */
@AutoRegister(id = "dungeons.trigger.mob_death")
@SerializableAs("dungeons.trigger.mob_death")
public class MobDeathTrigger extends DungeonTrigger {
    @PersistedField private String mob = "any";
    @PersistedField private int count = 1;
    @PersistedField private double radius = 0.0;
    private int status;
    private transient boolean invalidConfigurationLogged;

    /**
     * Creates a new MobDeathTrigger instance.
     */
    public MobDeathTrigger(Map<String, Object> config) {
        super("Mob Death", config);
        this.waitForConditions = true;
        this.setCategory(TriggerCategory.GENERAL);
        this.setHasTarget(true);
    }

    /**
     * Creates a new MobDeathTrigger instance.
     */
    public MobDeathTrigger() {
        super("Mob Death");
        this.waitForConditions = true;
        this.setCategory(TriggerCategory.GENERAL);
        this.setHasTarget(true);
    }

    /**
     * Performs initialize.
     */
    @Override
    public void initialize() {
        super.initialize();
        this.setDisplayName(this.mob + " Death");
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.ROTTEN_FLESH);
        functionButton.setDisplayName("&5Mob Death Counter");
        functionButton.addLore("&eTriggered when a certain number of");
        functionButton.addLore("&ea mob has died.");
        return functionButton;
    }

    /**
     * Performs on mob death.
     */
    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
            LivingEntity ent = event.getEntity();
            int requiredKills = Math.max(1, this.count);
            double effectiveRadius = Math.max(0.0, this.radius);
            if ((requiredKills != this.count || effectiveRadius != this.radius)
                    && !this.invalidConfigurationLogged) {
                this.invalidConfigurationLogged = true;
                this.logger()
                        .warn(
                                "MobDeathTrigger in dungeon '{}' at {} had invalid count/radius ({}, {}). Values were normalized.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                this.count,
                                this.radius);
            }

            boolean mobMatches =
                    this.mob.equalsIgnoreCase("any")
                            || event.getEntity().getType().name().equalsIgnoreCase(this.mob);

            if (mobMatches) {
                if (this.matchesRoom(ent)) {
                    if (effectiveRadius == 0.0
                            || !(this.location.distance(ent.getLocation()) > effectiveRadius)) {
                        if (this.status < requiredKills) {
                            this.status++;
                            if (this.status >= requiredKills) {
                                Player killer = ent.getKiller();
                                DungeonPlayerSession playerSession = null;
                                if (killer != null) {
                                    playerSession = RuntimeContext.playerSessions().get(killer);
                                }

                                this.trigger(playerSession);
                                if (this.allowRetrigger) {
                                    this.status = 0;
                                }
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
    public boolean matchesRoom(Entity ent) {
        if (!this.limitToRoom) {
            return true;
        } else {
            BranchingInstance instance = this.instance.as(BranchingInstance.class);
            if (instance == null) {
                return false;
            } else {
                InstanceRoom room = instance.getRoom(this.location);
                if (room == null) {
                    return false;
                } else {
                    String uuidString =
                            ent.getPersistentDataContainer()
                                    .get(
                                                                                        new NamespacedKey(RuntimeContext.plugin(), "originroom"),
                                            PersistentDataType.STRING);
                    if (uuidString == null) {
                        return false;
                    } else {
                        try {
                            UUID uuid = UUID.fromString(uuidString);
                            return uuid.equals(room.getUuid());
                        } catch (IllegalArgumentException exception) {
                            this.logger()
                                    .warn(
                                            "MobDeathTrigger in dungeon '{}' at {} encountered invalid origin room metadata '{}'.",
                                            this.dungeonNameForLogs(),
                                            this.locationForLogs(),
                                            uuidString,
                                            exception);
                            return false;
                        }
                    }
                }
            }
        }
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
                        this.button = new MenuButton(Material.PAPER);
                        this.button.setDisplayName("&d&lMob");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.trigger.mob-death.ask-mob");
                        LangUtils.sendMessage(
                                player,
                                "editor.trigger.mob-death.current-mob",
                                LangUtils.placeholder("mob", MobDeathTrigger.this.mob));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        MobDeathTrigger.this.mob = message;
                        LangUtils.sendMessage(
                                player, "editor.trigger.mob-death.mob-set", LangUtils.placeholder("mob", message));
                        MobDeathTrigger.this.setDisplayName(MobDeathTrigger.this.mob + " Death");
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.BONE);
                        this.button.setDisplayName("&d&lAmount");
                        this.button.setAmount(MobDeathTrigger.this.count);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.trigger.mob-death.ask-count");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        MobDeathTrigger.this.count = value.orElse(MobDeathTrigger.this.count);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.trigger.mob-death.count-set",
                                    LangUtils.placeholder("count", String.valueOf(MobDeathTrigger.this.count)));
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
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lAllow Retrigger");
                        this.button.setEnchanted(MobDeathTrigger.this.allowRetrigger);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!MobDeathTrigger.this.allowRetrigger) {
                            LangUtils.sendMessage(player, "editor.trigger.mob-death.allow-repeat");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.mob-death.prevent-repeat");
                        }

                        MobDeathTrigger.this.allowRetrigger = !MobDeathTrigger.this.allowRetrigger;
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
                        this.button.setDisplayName("&d&lMob Radius");
                        this.button.setAmount((int) MobDeathTrigger.this.radius);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.trigger.mob-death.ask-radius");
                        LangUtils.sendMessage(
                                player,
                                "editor.trigger.mob-death.current-radius",
                                LangUtils.placeholder("radius", String.valueOf(MobDeathTrigger.this.radius)));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Double> value = InputUtils.readDoubleInput(player, message);
                        MobDeathTrigger.this.radius = value.orElse(MobDeathTrigger.this.radius);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.trigger.mob-death.radius-set",
                                    LangUtils.placeholder("radius", String.valueOf(MobDeathTrigger.this.radius)));
                        }
                    }
                });
        this.addRoomLimitToggleButton();
    }

    /**
     * Returns the mob.
     */
    public String getMob() {
        return this.mob;
    }

    /**
     * Sets the mob.
     */
    public void setMob(String mob) {
        this.mob = mob;
    }

    /**
     * Returns the count.
     */
    public int getCount() {
        return this.count;
    }

    /**
     * Sets the count.
     */
    public void setCount(int count) {
        this.count = count;
    }

    /**
     * Returns the radius.
     */
    public double getRadius() {
        return this.radius;
    }

    /**
     * Sets the radius.
     */
    public void setRadius(double radius) {
        this.radius = radius;
    }
}
