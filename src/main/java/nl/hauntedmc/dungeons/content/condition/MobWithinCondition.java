package nl.hauntedmc.dungeons.content.condition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Condition that counts nearby living entities of a configured type.
 *
 * <p>The check can require either an exact count or a minimum count inside the configured radius.
 */
@AutoRegister(id = "dungeons.condition.mob_within")
@SerializableAs("dungeons.condition.mob_within")
public class MobWithinCondition extends TriggerCondition {
    @PersistedField private String entityName = "zombie";
    @PersistedField private double radius = 3.0;
    @PersistedField private int count = 1;
    @PersistedField private boolean mobsExact = false;
    private transient boolean invalidConfigurationLogged;

    /**
     * Creates a new MobWithinCondition instance.
     */
    public MobWithinCondition(Map<String, Object> config) {
        super("mobswithin", config);
    }

    /**
     * Creates a new MobWithinCondition instance.
     */
    public MobWithinCondition() {
        super("mobswithin");
    }

    /**
     * Performs check.
     */
    @Override
    public boolean check(TriggerFireEvent event) {
        if (this.location == null || this.location.getWorld() == null) {
            if (!this.invalidConfigurationLogged) {
                this.invalidConfigurationLogged = true;
                this.logger()
                        .warn(
                                "MobWithinCondition in dungeon '{}' has no valid location.",
                                this.dungeonNameForLogs());
            }
            return false;
        }

        double effectiveRadius = Math.max(0.0, this.radius);
        int requiredMobs = Math.max(0, this.count);
        Collection<Entity> entities =
                this.location
                        .getWorld()
                        .getNearbyEntities(this.location, effectiveRadius, effectiveRadius, effectiveRadius);
        List<LivingEntity> mobs = new ArrayList<>();
        EntityType type = null;

        try {
            type = EntityType.valueOf(this.entityName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            if (!this.invalidConfigurationLogged) {
                this.invalidConfigurationLogged = true;
                this.logger()
                        .warn(
                                "MobWithinCondition in dungeon '{}' at {} had invalid entity/radius/count ('{}', {}, {}). Falling back to ZOMBIE, {}, {}.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                this.entityName,
                                this.radius,
                                this.count,
                                effectiveRadius,
                                requiredMobs,
                                exception);
            }
        }

        if (type == null) {
            type = EntityType.ZOMBIE;
        }

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity && entity.getType() == type) {
                mobs.add((LivingEntity) entity);
            }
        }

        return this.mobsExact ? mobs.size() == requiredMobs : mobs.size() >= requiredMobs;
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.CREEPER_HEAD);
        functionButton.setDisplayName("&dMobs Within");
        functionButton.addLore("&eChecks for a number of specified");
        functionButton.addLore("&emobs within a configured radius");
        functionButton.addLore("&efrom this function.");

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
                        this.button = new MenuButton(Material.ZOMBIE_HEAD);
                        this.button.setDisplayName("&d&lSet Mob Type");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.condition.mob-within.ask-mob");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        EntityType type = EntityType.fromName(message);
                        if (type == null) {
                            try {
                                type = EntityType.valueOf(message.toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException exception) {
                                LangUtils.sendMessage(player, "editor.condition.mob-within.invalid-mob");
                                return;
                            }
                        }

                        MobWithinCondition.this.entityName = type.name();
                        LangUtils.sendMessage(
                                player,
                                "editor.condition.mob-within.mob-set",
                                LangUtils.placeholder("mob", MobWithinCondition.this.entityName));
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
                        this.button.setAmount((int) MathUtils.round(MobWithinCondition.this.radius, 0));
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.condition.mob-within.ask-radius");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Double> value = InputUtils.readDoubleInput(player, message);
                        MobWithinCondition.this.radius = value.orElse(MobWithinCondition.this.radius);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.condition.mob-within.radius-set",
                                    LangUtils.placeholder("radius", String.valueOf(MobWithinCondition.this.radius)));
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
                        this.button = new MenuButton(Material.REPEATER);
                        this.button.setDisplayName(
                                MobWithinCondition.this.mobsExact
                                        ? "&d&lMobs Required"
                                        : "&d&lMinimum Mobs Required");
                        this.button.setAmount(MobWithinCondition.this.count);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.condition.mob-within.ask-count");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        MobWithinCondition.this.count = value.orElse(MobWithinCondition.this.count);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.condition.mob-within.count-set",
                                    LangUtils.placeholder("count", String.valueOf(MobWithinCondition.this.count)));
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
                        this.button.setDisplayName("&d&lRequired Mobs Exact");
                        this.button.setEnchanted(MobWithinCondition.this.mobsExact);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!MobWithinCondition.this.mobsExact) {
                            LangUtils.sendMessage(player, "editor.condition.mob-within.exact");
                        } else {
                            LangUtils.sendMessage(player, "editor.condition.mob-within.minimum");
                        }

                        MobWithinCondition.this.mobsExact = !MobWithinCondition.this.mobsExact;
                    }
                });
    }

    /**
     * Returns the entity name.
     */
    public String getEntityName() {
        return this.entityName;
    }

    /**
     * Sets the entity name.
     */
    public void setEntityName(String entityName) {
        this.entityName = entityName;
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
}
