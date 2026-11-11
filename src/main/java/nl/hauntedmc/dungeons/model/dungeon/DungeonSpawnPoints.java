package nl.hauntedmc.dungeons.model.dungeon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;
import nl.hauntedmc.dungeons.util.world.LocationUtils;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Ordered, world-relative starting locations. Selection belongs to the instance, not the player.
 */
public final class DungeonSpawnPoints {
    private final LinkedHashMap<String, Location> points = new LinkedHashMap<>();

    public static DungeonSpawnPoints read(ConfigurationSection config) {
        DungeonSpawnPoints result = new DungeonSpawnPoints();
        if (config.contains("locations.starts")) {
            ConfigurationSection starts = config.getConfigurationSection("locations.starts");
            if (starts == null)
                throw new IllegalArgumentException(
                        "locations.starts must be a section of named locations");
            for (String id : starts.getKeys(false)) {
                ConfigurationSection point = starts.getConfigurationSection(id);
                if (point == null)
                    throw new IllegalArgumentException("Invalid spawnpoint locations.starts." + id);
                for (String axis : new String[] {"x", "y", "z"}) {
                    if (!(point.get(axis) instanceof Number))
                        throw new IllegalArgumentException(
                                "Missing or invalid locations.starts." + id + "." + axis);
                }
                for (String angle : new String[] {"yaw", "pitch"}) {
                    if (point.contains(angle) && !(point.get(angle) instanceof Number))
                        throw new IllegalArgumentException(
                                "Invalid locations.starts." + id + "." + angle);
                }
                result.add(id, LocationUtils.readLocation(point));
            }
        } else {
            Object legacy = config.get("locations.start");
            Location location =
                    legacy instanceof Location loc
                            ? loc
                            : LocationUtils.readLocation(
                                    config.getConfigurationSection("locations.start"));
            if (location != null) result.add("1", location);
        }
        return result;
    }

    public static String normalizeId(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException(
                    "Spawnpoint IDs must contain only letters, digits, hyphens or underscores");
        return id.toLowerCase(Locale.ROOT);
    }

    private static Location relative(Location location) {
        if (location == null
                || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ())
                || !Float.isFinite(location.getYaw())
                || !Float.isFinite(location.getPitch()))
            throw new IllegalArgumentException("Spawnpoint coordinates and facing must be finite");
        Location copy = location.clone();
        copy.setWorld(null);
        return copy;
    }

    public Map<String, Location> all() {
        Map<String, Location> copy = new LinkedHashMap<>();
        points.forEach((id, location) -> copy.put(id, location.clone()));
        return Collections.unmodifiableMap(copy);
    }

    public Location get(String id) {
        Location location = points.get(normalizeId(id));
        if (location == null) throw new IllegalArgumentException("Unknown spawnpoint: " + id);
        return location.clone();
    }

    public Location first() {
        return points.isEmpty() ? null : points.values().iterator().next().clone();
    }

    public Location select(RandomGenerator random) {
        if (points.isEmpty()) return null;
        return points.values().stream()
                .skip(random.nextInt(points.size()))
                .findFirst()
                .orElseThrow()
                .clone();
    }

    public String add(String name, Location location) {
        String id = name;
        if (id == null) {
            int number = 1;
            while (points.containsKey(Integer.toString(number))) number++;
            id = Integer.toString(number);
        }
        id = normalizeId(id);
        if (points.containsKey(id))
            throw new IllegalArgumentException("Spawnpoint already exists: " + id);
        points.put(id, relative(location));
        return id;
    }

    public void update(String id, Location location) {
        get(id);
        points.put(normalizeId(id), relative(location));
    }

    public void setFirst(Location location) {
        if (points.isEmpty()) add("1", location);
        else update(points.keySet().iterator().next(), location);
    }

    public void remove(String id) {
        get(id);
        points.remove(normalizeId(id));
    }

    public void write(ConfigurationSection config) {
        config.set("locations.start", null);
        config.createSection("locations.starts");
        points.forEach(
                (id, location) ->
                        LocationUtils.writeLocation("locations.starts." + id, config, location));
    }
}
