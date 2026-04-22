package nl.hauntedmc.dungeons.content.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Function that force-loads or releases a square chunk radius around its location.
 */
@AutoRegister(id = "dungeons.function.chunk_load")
@SerializableAs("dungeons.function.chunk_load")
public class ChunkLoadFunction extends DungeonFunction {
    @PersistedField protected int radius = 0;

    /**
     * Creates a new ChunkLoadFunction instance.
     */
    public ChunkLoadFunction(Map<String, Object> config) {
        super("Chunk Load", config);
        this.targetType = FunctionTargetType.NONE;
        this.setCategory(FunctionCategory.DUNGEON);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Creates a new ChunkLoadFunction instance.
     */
    public ChunkLoadFunction() {
        super("Chunk Load");
        this.targetType = FunctionTargetType.NONE;
        this.setCategory(FunctionCategory.DUNGEON);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Performs on disable.
     */
    @Override
    public void onDisable() {
        Chunk chunk = this.location.getChunk();

        for (int x = -this.radius; x <= this.radius; x++) {
            for (int z = -this.radius; z <= this.radius; z++) {
                Chunk c = chunk.getWorld().getChunkAt(chunk.getX() + x, chunk.getZ() + z);
                if (c.isForceLoaded()) {
                    c.setForceLoaded(false);
                }
            }
        }

        if (chunk.isForceLoaded()) {
            chunk.setForceLoaded(false);
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.STRUCTURE_BLOCK);
        functionButton.setDisplayName("&6Chunk Loader");
        functionButton.addLore("&eForce-loads the chunks in");
        functionButton.addLore("&ea radius around this function");
        functionButton.addLore("&eusing it as the center.");
        functionButton.addLore("Triggering again releases the chunk.");
        functionButton.addLore("&7Useful for keeping mobs and");
        functionButton.addLore("&7redstone responsive even when");
        functionButton.addLore("&7players are far away.");
        return functionButton;
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        List<Chunk> chunks = new ArrayList<>();
        Chunk chunk = this.location.getChunk();
        for (int x = -this.radius; x <= this.radius; x++) {
            for (int z = -this.radius; z <= this.radius; z++) {
                chunks.add(chunk.getWorld().getChunkAt(chunk.getX() + x, chunk.getZ() + z));
            }
        }

        boolean shouldForceLoad = false;
        for (Chunk loadedChunk : chunks) {
            if (!loadedChunk.isForceLoaded()) {
                shouldForceLoad = true;
                break;
            }
        }

        for (Chunk loadedChunk : chunks) {
            loadedChunk.setForceLoaded(shouldForceLoad);
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
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.chunk-load.ask-radius");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> optRadius = InputUtils.readIntegerInput(player, message);
                        ChunkLoadFunction.this.radius =
                                Math.max(optRadius.orElse(ChunkLoadFunction.this.radius), 0);
                        LangUtils.sendMessage(
                                player,
                                "editor.function.chunk-load.radius-set",
                                LangUtils.placeholder("radius", String.valueOf(ChunkLoadFunction.this.radius)));
                    }

                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.REDSTONE);
                        this.button.setDisplayName("&d&lRadius");
                        this.button.setAmount(ChunkLoadFunction.this.radius);
                    }
                });
    }
}
