package nl.hauntedmc.dungeons.content.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Function that spawns one or more mobs at the configured location.
 *
 * <p>The spawned mob type and effective count can be scaled by dungeon difficulty, and branching
 * instances receive an origin-room tag so later systems can reason about where the mob came
 * from.</p>
 */
@AutoRegister(id = "dungeons.function.spawn_mob")
@SerializableAs("dungeons.function.spawn_mob")
public class SpawnMobFunction extends DungeonFunction {
    @PersistedField private String mob = "zombie";
    @PersistedField private String levelString = "0";
    @PersistedField private int maxCount = 1;
    @PersistedField private int delay = 0;
    @PersistedField private int interval = 0;
    @PersistedField private double yaw = 0.0;
    private int count;
    private final List<BukkitRunnable> spawners;

    /**
     * Creates the function from persisted configuration.
     */
    public SpawnMobFunction(Map<String, Object> config) {
        super("Mob Spawner", config);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.LOCATION);
        this.spawners = new ArrayList<>();
    }

    /**
     * Creates a new function with default spawn settings.
     */
    public SpawnMobFunction() {
        super("Mob Spawner");
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.LOCATION);
        this.spawners = new ArrayList<>();
    }

    /**
     * Refreshes the display name so the editor reflects the configured mob type.
     */
    @Override
    public void initialize() {
        super.initialize();
        this.setDisplayName(this.mob + " Spawner");
    }

    /**
     * Cancels any delayed or repeating spawn tasks that are still pending.
     */
    @Override
    public void onDisable() {
        for (BukkitRunnable spawner : this.spawners) {
            if (!spawner.isCancelled()) {
                spawner.cancel();
            }
        }

        this.spawners.clear();
    }

    /**
     * Resolves the effective spawn configuration and schedules the actual mob spawns.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        final PlayableInstance instance = this.instance.asPlayInstance();
        if (instance != null) {
            EntityType resolvedType = EntityType.fromName(this.mob);
            if (resolvedType == null) {
                try {
                    resolvedType = EntityType.valueOf(this.mob.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    RuntimeContext.plugin()
                            .getSLF4JLogger()
                            .warn(
                                    "Invalid mob type '{}' on SpawnMobFunction at {} in dungeon '{}'. Falling back to ZOMBIE.",
                                    this.mob,
                                    this.location == null
                                            ? "<unknown>"
                                            : this.location.getBlockX()
                                                    + ","
                                                    + this.location.getBlockY()
                                                    + ","
                                                    + this.location.getBlockZ(),
                                    instance.getDungeon().getWorldName(),
                                    exception);
                }
            }

            if (resolvedType == null) {
                resolvedType = EntityType.ZOMBIE;
            }

            // Difficulty scales health, damage, and count before any entities are spawned so all delayed
            // ticks use a stable snapshot of the effective values.
            double healthMod = 1.0;
            int maxCountModded = this.maxCount;
            double damageMod = 1.0;
            if (instance.getDungeon().isUseDifficultyLevels() && instance.getDifficulty() != null) {
                DungeonDifficulty difficulty = instance.getDifficulty();
                healthMod = difficulty.getMobHealthScale();
                maxCountModded = (int) (maxCountModded * difficulty.getMobSpawnScale());
                damageMod = difficulty.getMobDamageScale();
            }

            final int finalMaxCountMod = Math.max(0, maxCountModded);
            final double finalDamageMod = damageMod;
            double finalHealthMod = healthMod;
            final EntityType spawnType = resolvedType;
            if (finalMaxCountMod <= 0) {
                this.logger()
                        .warn(
                                "SpawnMobFunction in dungeon '{}' at {} was skipped because the effective spawn count was {}.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                finalMaxCountMod);
                return;
            }

            BukkitRunnable spawner =
                                        new BukkitRunnable() {
                        private int spawned;

                        /**
                         * Performs run.
                         */
                        public void run() {
                            if (this.spawned >= finalMaxCountMod) {
                                this.cancel();
                            } else {
                                // Offset the spawn into the center of the block and nudge upward when the target
                                // block is occupied so the entity does not suffocate immediately.
                                Location spawnPoint = SpawnMobFunction.this.location.clone();
                                spawnPoint.setX(spawnPoint.getX() + 0.5);
                                spawnPoint.setY(spawnPoint.getY() + 0.1);
                                spawnPoint.setZ(spawnPoint.getZ() + 0.5);
                                spawnPoint.setYaw((float) SpawnMobFunction.this.yaw);
                                if (spawnPoint.getBlock().isSolid()) {
                                    spawnPoint.setY(spawnPoint.getY() + 1.0);
                                }

                                if (instance.getInstanceWorld().spawnEntity(spawnPoint, spawnType)
                                        instanceof LivingEntity living) {
                                    EntityUtils.setMaxHealth(
                                            living, EntityUtils.getMaxHealth(living) * finalHealthMod);
                                    living.setHealth(EntityUtils.getMaxHealth(living));
                                    SpawnMobFunction.this.applyRoomTag(living);
                                    AttributeInstance attrib = living.getAttribute(Attribute.ATTACK_DAMAGE);
                                    if (attrib != null) {
                                        living
                                                .getAttribute(Attribute.ATTACK_DAMAGE)
                                                .addModifier(
                                                                                                                new AttributeModifier(
                                                                                                                                new NamespacedKey(RuntimeContext.plugin(), "dungeon_level"),
                                                                finalDamageMod - 1.0,
                                                                Operation.MULTIPLY_SCALAR_1));
                                    }
                                }

                                this.spawned++;
                            }
                        }
                    };
            spawner.runTaskTimer(RuntimeContext.plugin(), this.delay, Math.max(this.interval, 1));
            this.spawners.add(spawner);
        }
    }

    /**
     * Tags spawned mobs with their branching origin room so room-based logic can find them later.
     */
    private void applyRoomTag(Entity ent) {
        BranchingInstance instance = this.instance.as(BranchingInstance.class);
        if (instance != null) {
            InstanceRoom room = instance.getRoom(this.location);
            if (room != null) {
                ent.getPersistentDataContainer()
                        .set(
                                                                new NamespacedKey(RuntimeContext.plugin(), "originroom"),
                                PersistentDataType.STRING,
                                room.getUuid().toString());
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.CREEPER_HEAD);
        functionButton.setDisplayName("&dMob Spawner");
        functionButton.addLore("&eSpawns mobs at this location.");
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
                        this.button.setDisplayName("&d&lMob Name");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.spawn-mob.ask-mob");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.spawn-mob.current-mob",
                                LangUtils.placeholder("mob", SpawnMobFunction.this.mob));
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
                                LangUtils.sendMessage(player, "editor.function.spawn-mob.invalid-mob");
                                return;
                            }
                        }

                        SpawnMobFunction.this.mob = type.name();
                        LangUtils.sendMessage(
                                player,
                                "editor.function.spawn-mob.mob-set",
                                LangUtils.placeholder("mob", SpawnMobFunction.this.mob));
                        SpawnMobFunction.this.setDisplayName(SpawnMobFunction.this.mob + " Spawner");
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
                        this.button.setAmount(SpawnMobFunction.this.maxCount);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.spawn-mob.ask-count");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        SpawnMobFunction.this.maxCount = value.orElse(SpawnMobFunction.this.maxCount);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.spawn-mob.count-set",
                                    LangUtils.placeholder("count", String.valueOf(SpawnMobFunction.this.maxCount)));
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
                        this.button.setDisplayName("&d&lSpawn Delay");
                        this.button.setAmount(SpawnMobFunction.this.delay);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.spawn-mob.ask-delay");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        SpawnMobFunction.this.delay = value.orElse(SpawnMobFunction.this.delay);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.spawn-mob.delay-set",
                                    LangUtils.placeholder("delay", String.valueOf(SpawnMobFunction.this.delay)));
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
                        this.button = new MenuButton(Material.CLOCK);
                        this.button.setDisplayName("&d&lSpawn Interval");
                        this.button.setAmount(SpawnMobFunction.this.interval);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.spawn-mob.ask-interval");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        SpawnMobFunction.this.interval = value.orElse(SpawnMobFunction.this.interval);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.spawn-mob.interval-set",
                                    LangUtils.placeholder(
                                            "interval", String.valueOf(SpawnMobFunction.this.interval)));
                        }
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.COMPASS);
                        this.button.setDisplayName("&d&lMob Direction");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        SpawnMobFunction.this.yaw = MathUtils.round(player.getLocation().getYaw(), 0);
                        LangUtils.sendMessage(
                                player,
                                "editor.function.spawn-mob.direction-set",
                                LangUtils.placeholder("yaw", String.valueOf(SpawnMobFunction.this.yaw)));
                    }
                });
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
     * Returns the level string.
     */
    public String getLevelString() {
        return this.levelString;
    }

    /**
     * Sets the level string.
     */
    public void setLevelString(String levelString) {
        this.levelString = levelString;
    }

    /**
     * Returns the max count.
     */
    public int getMaxCount() {
        return this.maxCount;
    }

    /**
     * Sets the max count.
     */
    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    /**
     * Returns the delay.
     */
    public int getDelay() {
        return this.delay;
    }

    /**
     * Sets the delay.
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * Returns the interval.
     */
    public int getInterval() {
        return this.interval;
    }

    /**
     * Sets the interval.
     */
    public void setInterval(int interval) {
        this.interval = interval;
    }

    /**
     * Returns the yaw.
     */
    public double getYaw() {
        return this.yaw;
    }

    /**
     * Sets the yaw.
     */
    public void setYaw(double yaw) {
        this.yaw = yaw;
    }
}
