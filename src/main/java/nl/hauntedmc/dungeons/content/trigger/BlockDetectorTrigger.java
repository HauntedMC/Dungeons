package nl.hauntedmc.dungeons.content.trigger;

import java.util.Locale;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.MessageUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Trigger that listens for block placement or breaking at one exact location.
 */
@AutoRegister(id = "dungeons.trigger.block_detector")
@SerializableAs("dungeons.trigger.block_detector")
public class BlockDetectorTrigger extends DungeonTrigger {
    @PersistedField private boolean onBreak = false;
    @PersistedField private String material = "ANY";
    private transient boolean invalidMaterialLogged;

    /**
     * Creates a new BlockDetectorTrigger instance.
     */
    public BlockDetectorTrigger(Map<String, Object> config) {
        super("Block Detector", config);
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
    }

    /**
     * Creates a new BlockDetectorTrigger instance.
     */
    public BlockDetectorTrigger() {
        super("Block Detector");
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
    }

    /**
     * Performs initialize.
     */
    @Override
    public void initialize() {
        super.initialize();
        this.setDisplayName(this.material + " Block Detector");
    }

    /**
     * Performs on block place.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world == this.instance.getInstanceWorld()) {
            Block block = event.getBlock();
            Location targetLoc = block.getLocation();
            if (targetLoc.equals(this.location)) {
                event.setCancelled(false);
                Material configuredMaterial = this.resolveConfiguredMaterial(false);
                if (this.material.equalsIgnoreCase("ANY")
                        || configuredMaterial != null && block.getType() == configuredMaterial) {
                    if (!this.onBreak) {
                        this.trigger(RuntimeContext.playerSessions().get(player));
                    }
                }
            }
        }
    }

    /**
     * Performs on block break.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world == this.instance.getInstanceWorld()) {
            Block block = event.getBlock();
            Location targetLoc = block.getLocation();
            if (targetLoc.equals(this.location)) {
                event.setCancelled(false);
                Material configuredMaterial = this.resolveConfiguredMaterial(false);
                if (this.material.equalsIgnoreCase("ANY")
                        || configuredMaterial != null && block.getType() == configuredMaterial) {
                    if (this.onBreak) {
                        this.trigger(RuntimeContext.playerSessions().get(player));
                    }
                }
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.OBSERVER);
        button.setDisplayName("&aBlock Detector");
        button.addLore("&eTriggered when a block is placed");
        button.addLore("&eor broken at this location.");
        button.addLore("");
        button.addLore("&7Also allows placing or breaking");
        button.addLore("&7blocks at this location.");
        return button;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.OBSERVER);
                        this.button.setDisplayName("&d&lBreak or Place");
                        this.button.setEnchanted(BlockDetectorTrigger.this.onBreak);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!BlockDetectorTrigger.this.onBreak) {
                            LangUtils.sendMessage(player, "editor.trigger.block-detector.on-break");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.block-detector.on-place");
                        }

                        BlockDetectorTrigger.this.onBreak = !BlockDetectorTrigger.this.onBreak;
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        Material mat;
                        if (BlockDetectorTrigger.this.material.equalsIgnoreCase("ANY")) {
                            mat = Material.STONE;
                        } else {
                            mat = BlockDetectorTrigger.this.resolveConfiguredMaterial(true);
                            if (mat == null) {
                                mat = Material.BARRIER;
                            }
                        }

                        this.button = new MenuButton(mat);
                        this.button.setDisplayName("&d&lSet Block");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.trigger.block-detector.ask-block");
                        LangUtils.sendMessage(
                                player,
                                "editor.trigger.block-detector.current-block",
                                LangUtils.placeholder("block", BlockDetectorTrigger.this.material));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        String matString = message.toUpperCase();

                        try {
                            if (matString.equals("ANY")) {
                                BlockDetectorTrigger.this.material = matString;
                                return;
                            }

                            Material mat = Material.valueOf(matString);
                            if (!mat.isBlock()) {
                                                                throw new IllegalArgumentException("Invalid block");
                            }

                            BlockDetectorTrigger.this.material = matString;
                            BlockDetectorTrigger.this.setDisplayName(
                                    BlockDetectorTrigger.this.material + " Block Detector");
                            LangUtils.sendMessage(
                                    player,
                                    "editor.trigger.block-detector.block-set",
                                    LangUtils.placeholder("block", BlockDetectorTrigger.this.material));
                        } catch (IllegalArgumentException exception) {
                            LangUtils.sendMessage(player, "editor.trigger.block-detector.invalid-material");
                            MessageUtils.sendClickableLink(
                                    player,
                                    RuntimeContext.logPrefix()
                                            + ColorUtils.colorize("&bClick here to view a list of valid materials."),
                                    "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html");
                        }
                    }
                });
    }

    /**
     * Resolves configured material.
     */
    private Material resolveConfiguredMaterial(boolean logInvalid) {
        try {
            Material configured = Material.valueOf(this.material.toUpperCase(Locale.ROOT));
            if (!configured.isBlock()) {
                                throw new IllegalArgumentException("Configured material is not a block");
            }

            return configured;
        } catch (IllegalArgumentException exception) {
            if (logInvalid && !this.invalidMaterialLogged) {
                this.invalidMaterialLogged = true;
                this.logger()
                        .warn(
                                "BlockDetectorTrigger in dungeon '{}' at {} has invalid material '{}'.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                this.material,
                                exception);
            }

            return null;
        }
    }
}
