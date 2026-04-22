package nl.hauntedmc.dungeons.content.function;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.MessageUtils;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Function that places or removes a configured block at its location.
 *
 * <p>When placing directional blocks, the function also applies the configured facing or axis.
 */
@AutoRegister(id = "dungeons.function.block_editor")
@SerializableAs("dungeons.function.block_editor")
public class BlockEditorFunction extends DungeonFunction {
    @PersistedField private String blockType = "AIR";
    @PersistedField private boolean remove = false;
    @PersistedField private String direction = "NORTH";
    private transient boolean invalidBlockTypeLogged;

    /**
     * Creates a new BlockEditorFunction instance.
     */
    public BlockEditorFunction(Map<String, Object> config) {
        super("Block Editor", config);
        this.setCategory(FunctionCategory.LOCATION);
    }

    /**
     * Creates a new BlockEditorFunction instance.
     */
    public BlockEditorFunction() {
        super("Block Editor");
        this.setCategory(FunctionCategory.LOCATION);
    }

    /**
     * Performs initialize.
     */
    @Override
    public void initialize() {
        super.initialize();
        if (this.remove) {
            this.setDisplayName(this.blockType + " Remover");
        } else {
            this.setDisplayName(this.blockType + " Placer");
        }
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        Block block = this.location.getBlock();
        if (this.remove) {
            Material configuredMaterial = this.resolveConfiguredMaterial(false);
            if (this.blockType.equalsIgnoreCase("ANY")
                    || configuredMaterial != null && block.getType() == configuredMaterial) {
                block.setType(Material.AIR);
            }
        } else {
            Material mat = this.resolveConfiguredMaterial(true);
            if (mat == null) {
                return;
            }

            block.setType(mat);
            BlockData data = block.getBlockData();
            BlockState state = block.getState();
            switch (data) {
                case Directional dirData -> {
                    try {
                        BlockFace dir = BlockFace.valueOf(this.direction);
                        dirData.setFacing(dir);
                        state.setBlockData(dirData);
                        state.update(true);
                    } catch (IllegalArgumentException exception) {
                        RuntimeContext.plugin()
                                .getSLF4JLogger()
                                .error(
                                        "Block editor function in dungeon '{}' has invalid direction '{}' at {},{},{}. Valid values: {}",
                                        this.instance.getDungeon().getWorldName(),
                                        this.direction,
                                        this.location.getBlockX(),
                                        this.location.getBlockY(),
                                        this.location.getBlockZ(),
                                        Arrays.toString(BlockFace.values()));
                    }
                }
                case Rotatable rotData -> {
                    try {
                        BlockFace dir = BlockFace.valueOf(this.direction);
                        rotData.setRotation(dir);
                        state.setBlockData(rotData);
                        state.update(true);
                    } catch (IllegalArgumentException exception) {
                        RuntimeContext.plugin()
                                .getSLF4JLogger()
                                .error(
                                        "Block editor function in dungeon '{}' has invalid rotation '{}' at {},{},{}. Valid values: {}",
                                        this.instance.getDungeon().getWorldName(),
                                        this.direction,
                                        this.location.getBlockX(),
                                        this.location.getBlockY(),
                                        this.location.getBlockZ(),
                                        Arrays.toString(BlockFace.values()));
                    }
                }
                case Orientable rotData -> {
                    try {
                        Axis dir = Axis.valueOf(this.direction);
                        rotData.setAxis(dir);
                        state.setBlockData(rotData);
                        state.update(true);
                    } catch (IllegalArgumentException exception) {
                        RuntimeContext.plugin()
                                .getSLF4JLogger()
                                .error(
                                        "Block editor function in dungeon '{}' has invalid axis '{}' at {},{},{}. Valid values: {}",
                                        this.instance.getDungeon().getWorldName(),
                                        this.direction,
                                        this.location.getBlockX(),
                                        this.location.getBlockY(),
                                        this.location.getBlockZ(),
                                        Arrays.toString(Axis.values()));
                    }
                }
                default -> {}
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.OBSERVER);
        functionButton.setDisplayName("&dBlock Editor");
        functionButton.addLore("&ePlaces or removes a specified");
        functionButton.addLore("&eblock at the function's location.");
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
                        this.button = new MenuButton(Material.GRASS_BLOCK);
                        this.button.setDisplayName("&d&lBlock Type");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!BlockEditorFunction.this.remove) {
                            LangUtils.sendMessage(player, "editor.function.block-editor.ask-place-block");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.block-editor.ask-remove-block");
                        }

                        LangUtils.sendMessage(
                                player,
                                "editor.function.block-editor.current-block",
                                LangUtils.placeholder("block", BlockEditorFunction.this.blockType));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        String matString = message.toUpperCase();

                        try {
                            if (BlockEditorFunction.this.remove && matString.equals("ANY")) {
                                BlockEditorFunction.this.blockType = matString;
                                BlockEditorFunction.this.setDisplayName("Block Remover");
                                LangUtils.sendMessage(
                                        player,
                                        "editor.function.block-editor.block-set",
                                        LangUtils.placeholder("block", BlockEditorFunction.this.blockType));
                                return;
                            }

                            Material mat = Material.valueOf(matString);
                            if (!mat.isBlock()) {
                                                                throw new IllegalArgumentException("Invalid block");
                            }

                            BlockEditorFunction.this.blockType = matString;
                            if (!BlockEditorFunction.this.remove) {
                                BlockEditorFunction.this.setDisplayName(
                                        BlockEditorFunction.this.blockType + " Placer");
                            } else {
                                BlockEditorFunction.this.setDisplayName(
                                        BlockEditorFunction.this.blockType + " Remover");
                            }

                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.block-editor.block-set",
                                    LangUtils.placeholder("block", BlockEditorFunction.this.blockType));
                        } catch (IllegalArgumentException exception) {
                            LangUtils.sendMessage(player, "editor.function.block-editor.invalid-material");
                            MessageUtils.sendClickableLink(
                                    player,
                                    LangUtils.getMessage("editor.function.block-editor.valid-materials-link"),
                                    "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html");
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
                        this.button = new MenuButton(Material.BARRIER);
                        this.button.setDisplayName("&d&lToggle Remover");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!BlockEditorFunction.this.remove) {
                            LangUtils.sendMessage(player, "editor.function.block-editor.mode-remove");
                            BlockEditorFunction.this.setDisplayName(
                                    BlockEditorFunction.this.blockType + " Remover");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.block-editor.mode-place");
                            BlockEditorFunction.this.setDisplayName(
                                    BlockEditorFunction.this.blockType + " Placer");
                        }

                        BlockEditorFunction.this.remove = !BlockEditorFunction.this.remove;
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.MAGENTA_GLAZED_TERRACOTTA);
                        this.button.setDisplayName("&d&lBlock Direction");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        Material mat = BlockEditorFunction.this.resolveConfiguredMaterial(true);
                        if (mat == null) {
                            LangUtils.sendMessage(player, "editor.function.block-editor.invalid-material");
                            this.setCancelled(true);
                            return;
                        }

                        BlockData data = mat.createBlockData();
                        if (BlockEditorFunction.this.remove) {
                            LangUtils.sendMessage(
                                    player, "editor.function.block-editor.no-direction-while-removing");
                            this.setCancelled(true);
                        } else if (!(data instanceof Directional)
                                && !(data instanceof Rotatable)
                                && !(data instanceof Orientable)) {
                            LangUtils.sendMessage(player, "editor.function.block-editor.no-direction-for-block");
                            this.setCancelled(true);
                        } else {
                            LangUtils.sendMessage(player, "editor.function.block-editor.ask-direction");
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.block-editor.current-direction",
                                    LangUtils.placeholder("direction", BlockEditorFunction.this.direction));
                        }
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        String directionString = message.toUpperCase();
                        Material mat = BlockEditorFunction.this.resolveConfiguredMaterial(true);
                        if (mat == null) {
                            LangUtils.sendMessage(player, "editor.function.block-editor.invalid-material");
                            return;
                        }

                        BlockData data = mat.createBlockData();
                        if (data instanceof Directional) {
                            try {
                                BlockEditorFunction.this.direction = directionString;
                                LangUtils.sendMessage(
                                        player,
                                        "editor.function.block-editor.direction-set",
                                        LangUtils.placeholder("direction", BlockEditorFunction.this.direction));
                            } catch (IllegalArgumentException exception) {
                                LangUtils.sendMessage(player, "editor.function.block-editor.invalid-direction");
                                MessageUtils.sendClickableLink(
                                        player,
                                        LangUtils.getMessage("editor.function.block-editor.valid-directions-link"),
                                        "https://hub.spigotmc.org/javadocs/spigot/org/bukkit/block/BlockFace.html");
                            }
                        } else if (data instanceof Rotatable) {
                            try {
                                BlockEditorFunction.this.direction = directionString;
                                LangUtils.sendMessage(
                                        player,
                                        "editor.function.block-editor.direction-set",
                                        LangUtils.placeholder("direction", BlockEditorFunction.this.direction));
                            } catch (IllegalArgumentException exception) {
                                LangUtils.sendMessage(player, "editor.function.block-editor.invalid-direction");
                                MessageUtils.sendClickableLink(
                                        player,
                                        LangUtils.getMessage("editor.function.block-editor.valid-directions-link"),
                                        "https://hub.spigotmc.org/javadocs/spigot/org/bukkit/block/BlockFace.html");
                            }
                        } else {
                            try {
                                BlockEditorFunction.this.direction = directionString;
                                LangUtils.sendMessage(
                                        player,
                                        "editor.function.block-editor.axis-set",
                                        LangUtils.placeholder("axis", BlockEditorFunction.this.direction));
                            } catch (IllegalArgumentException exception) {
                                LangUtils.sendMessage(player, "editor.function.block-editor.invalid-direction");
                                LangUtils.sendMessage(player, "editor.function.block-editor.valid-axis-directions");
                            }
                        }
                    }
                });
    }

    /**
     * Returns the block type.
     */
    public String getBlockType() {
        return this.blockType;
    }

    /**
     * Sets the block type.
     */
    public void setBlockType(String blockType) {
        this.blockType = blockType;
    }

    /**
     * Returns whether remove.
     */
    public boolean isRemove() {
        return this.remove;
    }

    /**
     * Returns the direction.
     */
    public String getDirection() {
        return this.direction;
    }

    /**
     * Resolves configured material.
     */
    private Material resolveConfiguredMaterial(boolean logInvalid) {
        try {
            Material material = Material.valueOf(this.blockType.toUpperCase(Locale.ROOT));
            if (!material.isBlock()) {
                                throw new IllegalArgumentException("Configured material is not a block");
            }

            return material;
        } catch (IllegalArgumentException exception) {
            if (logInvalid && !this.invalidBlockTypeLogged) {
                this.invalidBlockTypeLogged = true;
                this.logger()
                        .warn(
                                "BlockEditorFunction in dungeon '{}' at {} has invalid block type '{}'.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                this.blockType,
                                exception);
            }

            return null;
        }
    }
}
