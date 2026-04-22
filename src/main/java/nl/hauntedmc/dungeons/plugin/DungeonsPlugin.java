package nl.hauntedmc.dungeons.plugin;

import nl.hauntedmc.dungeons.bootstrap.DungeonsApplication;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bukkit entrypoint for the plugin.
 *
 * <p>This class is intentionally thin. Bukkit requires lifecycle hooks and plugin-owned
 * integration points such as {@link #getDefaultWorldGenerator(String, String)} to live on the
 * concrete {@link JavaPlugin} subclass.
 */
public final class DungeonsPlugin extends JavaPlugin {
    private final DungeonsApplication application = new DungeonsApplication(this);

    /** Delegates plugin startup to the application composition root. */
    @Override
    public void onEnable() {
        this.application.start();
    }

    /** Delegates plugin shutdown to the application composition root. */
    @Override
    public void onDisable() {
        this.application.stop();
    }

    /**
     * Returns the world generator that matches the requested generator id.
     */
    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(
            @NotNull String worldName, @Nullable String id) {
        return this.application.resolveWorldGenerator(worldName, id);
    }
}
