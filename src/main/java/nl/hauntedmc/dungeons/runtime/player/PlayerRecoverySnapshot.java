package nl.hauntedmc.dungeons.runtime.player;

import nl.hauntedmc.dungeons.util.entity.EntityUtils;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;

/** A complete pre-entry snapshot; item bytes preserve Paper data components and custom metadata. */
record PlayerRecoverySnapshot(
        ItemStack[] contents,
        ItemStack[] armor,
        int heldSlot,
        Location location,
        GameMode gameMode,
        float exp,
        int level,
        int totalExp,
        double health,
        int food,
        float saturation,
        float exhaustion,
        boolean allowFlight,
        boolean flying,
        float flySpeed,
        float walkSpeed,
        int fireTicks,
        float fallDistance,
        int air,
        List<PotionEffect> effects) {
    static PlayerRecoverySnapshot capture(Player player) {
        return new PlayerRecoverySnapshot(
                copy(player.getInventory().getContents()),
                copy(player.getInventory().getArmorContents()),
                player.getInventory().getHeldItemSlot(),
                player.getLocation().clone(),
                player.getGameMode(),
                player.getExp(),
                player.getLevel(),
                player.getTotalExperience(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getFlySpeed(),
                player.getWalkSpeed(),
                player.getFireTicks(),
                player.getFallDistance(),
                player.getRemainingAir(),
                List.copyOf(player.getActivePotionEffects()));
    }

    static ItemStack[] copy(ItemStack[] items) {
        return Arrays.stream(items)
                .map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }

    static List<String> encode(ItemStack[] items) {
        return Arrays.stream(items)
                .map(
                        item ->
                                item == null
                                        ? ""
                                        : Base64.getEncoder()
                                                .encodeToString(item.serializeAsBytes()))
                .toList();
    }

    static ItemStack[] decode(List<?> items) {
        return items.stream()
                .map(
                        value -> {
                            if (!(value instanceof String encoded))
                                throw new IllegalArgumentException("Invalid item record");
                            return encoded.isEmpty()
                                    ? null
                                    : ItemStack.deserializeBytes(
                                            Base64.getDecoder().decode(encoded));
                        })
                .toArray(ItemStack[]::new);
    }

    void write(ConfigurationSection data) {
        data.set("contents", encode(contents));
        data.set("armor", encode(armor));
        data.set("held", heldSlot);
        data.set("world", location.getWorld().getUID().toString());
        data.set("x", location.getX());
        data.set("y", location.getY());
        data.set("z", location.getZ());
        data.set("yaw", location.getYaw());
        data.set("pitch", location.getPitch());
        data.set("gamemode", gameMode.name());
        data.set("exp", exp);
        data.set("level", level);
        data.set("total-exp", totalExp);
        data.set("health", health);
        data.set("food", food);
        data.set("saturation", saturation);
        data.set("exhaustion", exhaustion);
        data.set("allow-flight", allowFlight);
        data.set("flying", flying);
        data.set("fly-speed", flySpeed);
        data.set("walk-speed", walkSpeed);
        data.set("fire", fireTicks);
        data.set("fall", fallDistance);
        data.set("air", air);
        data.set("effects", effects);
    }

    static PlayerRecoverySnapshot read(ConfigurationSection data) {
        Objects.requireNonNull(data, "Missing recovery snapshot");
        for (String key :
                List.of(
                        "contents",
                        "armor",
                        "held",
                        "world",
                        "x",
                        "y",
                        "z",
                        "yaw",
                        "pitch",
                        "gamemode",
                        "exp",
                        "level",
                        "total-exp",
                        "health",
                        "food",
                        "saturation",
                        "exhaustion",
                        "allow-flight",
                        "flying",
                        "fly-speed",
                        "walk-speed",
                        "fire",
                        "fall",
                        "air",
                        "effects")) {
            if (!data.contains(key))
                throw new IllegalArgumentException("Missing snapshot field " + key);
        }
        for (String key :
                List.of(
                        "held",
                        "x",
                        "y",
                        "z",
                        "yaw",
                        "pitch",
                        "exp",
                        "level",
                        "total-exp",
                        "health",
                        "food",
                        "saturation",
                        "exhaustion",
                        "fly-speed",
                        "walk-speed",
                        "fire",
                        "fall",
                        "air")) {
            if (!(data.get(key) instanceof Number number)
                    || !Double.isFinite(number.doubleValue())) {
                throw new IllegalArgumentException("Invalid numeric snapshot field " + key);
            }
        }
        if (!(data.get("allow-flight") instanceof Boolean)
                || !(data.get("flying") instanceof Boolean)
                || data.getDouble("fly-speed") < -1
                || data.getDouble("fly-speed") > 1
                || data.getDouble("walk-speed") < -1
                || data.getDouble("walk-speed") > 1
                || data.getDouble("saturation") < 0
                || data.getDouble("exhaustion") < 0
                || data.getInt("total-exp") < 0) {
            throw new IllegalArgumentException("Invalid player recovery attributes");
        }
        // Resolve the world only when applying, so an unloaded world never destroys the record.
        var location =
                new Location(
                        null,
                        data.getDouble("x"),
                        data.getDouble("y"),
                        data.getDouble("z"),
                        (float) data.getDouble("yaw"),
                        (float) data.getDouble("pitch"));
        var contents = decode(Objects.requireNonNull(data.getList("contents")));
        var armor = decode(Objects.requireNonNull(data.getList("armor")));
        if (contents.length != 41
                || armor.length != 4
                || data.getInt("held") < 0
                || data.getInt("held") > 8
                || !Double.isFinite(data.getDouble("health"))
                || data.getDouble("health") <= 0
                || data.getInt("food") < 0
                || data.getInt("food") > 20
                || data.getInt("level") < 0
                || !Double.isFinite(data.getDouble("exp"))
                || data.getDouble("exp") < 0
                || data.getDouble("exp") > 1) {
            throw new IllegalArgumentException("Invalid player recovery snapshot");
        }
        UUID.fromString(data.getString("world"));
        List<PotionEffect> effects = new ArrayList<>();
        for (Object effect : Objects.requireNonNull(data.getList("effects")))
            effects.add((PotionEffect) effect);
        return new PlayerRecoverySnapshot(
                contents,
                armor,
                data.getInt("held"),
                location,
                GameMode.valueOf(data.getString("gamemode")),
                (float) data.getDouble("exp"),
                data.getInt("level"),
                data.getInt("total-exp"),
                data.getDouble("health"),
                data.getInt("food"),
                (float) data.getDouble("saturation"),
                (float) data.getDouble("exhaustion"),
                data.getBoolean("allow-flight"),
                data.getBoolean("flying"),
                (float) data.getDouble("fly-speed"),
                (float) data.getDouble("walk-speed"),
                data.getInt("fire"),
                (float) data.getDouble("fall"),
                data.getInt("air"),
                effects);
    }

    void apply(Player player, World world) {
        Location target = location.clone();
        target.setWorld(Objects.requireNonNull(world, "Recovery world is unavailable"));
        if (player.isDead()) throw new IllegalStateException("Player must respawn before recovery");
        if (player.getVehicle() != null) player.getVehicle().removePassenger(player);
        if (!player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN))
            throw new IllegalStateException("Recovery teleport was rejected");
        player.getInventory().setContents(copy(contents));
        player.getInventory().setArmorContents(copy(armor));
        player.getInventory().setHeldItemSlot(heldSlot);
        this.restoreMovement(player);
        player.setTotalExperience(totalExp);
        player.setLevel(level);
        player.setExp(exp);
        for (PotionEffect effect : player.getActivePotionEffects())
            player.removePotionEffect(effect.getType());
        player.addPotionEffects(effects);
        player.setHealth(Math.min(health, EntityUtils.getMaxHealth(player)));
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setFireTicks(fireTicks);
        player.setFallDistance(fallDistance);
        player.setRemainingAir(air);
    }

    void restoreMovement(Player player) {
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying && allowFlight);
        player.setFlySpeed(flySpeed);
        player.setWalkSpeed(walkSpeed);
    }
}
