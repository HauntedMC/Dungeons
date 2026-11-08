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

    /** Restores current typed YAML gamerule values. */
    public static void apply(World world, ConfigurationSection config) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.GAME_RULE);
        for (String name : world.getGameRules()) {
            NamespacedKey key = NamespacedKey.fromString(name);
            GameRule<?> rule = key == null ? null : registry.get(key);
            Object value = config.get("Gamerule." + name);
            if (rule != null && value != null) applyValue(world, rule, value);
        }
    }

    private static <T> void applyValue(World world, GameRule<T> rule, Object value) {
        if (!rule.getType().isInstance(value))
            throw new IllegalArgumentException("Invalid gamerule type: " + value);
        world.setGameRule(rule, rule.getType().cast(value));
    }
}
