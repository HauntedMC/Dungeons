package nl.hauntedmc.dungeons.util.world;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Typed gamerule persistence using the world's available rules and registry identifiers. */
public final class GameRuleUtils {
    private GameRuleUtils() {}

    /** Captures values on the server thread, preserving the existing YAML paths. */
    public static void capture(World world, ConfigurationSection config) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.GAME_RULE);
        for (String name : world.getGameRules()) {
            NamespacedKey key = NamespacedKey.fromString(name);
            GameRule<?> rule = key == null ? null : registry.get(key);
            if (rule != null) config.set("Gamerule." + name, world.getGameRuleValue(rule));
        }
    }

    /** Restores both string values from older saves and native YAML booleans and integers. */
    public static void apply(World world, ConfigurationSection config) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.GAME_RULE);
        for (String name : world.getGameRules()) {
            NamespacedKey key = NamespacedKey.fromString(name);
            GameRule<?> rule = key == null ? null : registry.get(key);
            String value = config.getString("Gamerule." + name);
            if (rule != null && value != null) applyValue(world, rule, value);
        }
    }

    private static <T> void applyValue(World world, GameRule<T> rule, String value) {
        Object parsed;
        if (rule.getType() == Boolean.class) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Invalid boolean gamerule value: " + value);
            }
            parsed = Boolean.valueOf(value);
        } else if (rule.getType() == Integer.class) {
            parsed = Integer.valueOf(value);
        } else {
            throw new IllegalArgumentException("Unsupported gamerule type: " + rule.getType());
        }
        world.setGameRule(rule, rule.getType().cast(parsed));
    }
}
