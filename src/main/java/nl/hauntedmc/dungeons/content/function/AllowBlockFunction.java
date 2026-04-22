package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.trigger.DungeonStartTrigger;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Function that toggles location-specific block interaction permissions.
 *
 * <p>Once activated, listeners can consult this function to decide whether players may place,
 * break, or bucket-interact at the configured location.
 */
@AutoRegister(id = "dungeons.function.allow_block")
@SerializableAs("dungeons.function.allow_block")
public class AllowBlockFunction extends DungeonFunction {
    @PersistedField private boolean allowPlace = true;
    @PersistedField private boolean allowBreak = true;
    @PersistedField private boolean allowBucket = false;
    private boolean active = false;

    /**
     * Creates a new AllowBlockFunction instance.
     */
    public AllowBlockFunction(Map<String, Object> config) {
        super("Block Control", config);
        this.setRequiresTrigger(false);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.LOCATION);
    }

    /**
     * Creates a new AllowBlockFunction instance.
     */
    public AllowBlockFunction() {
        super("Block Control");
        this.setRequiresTrigger(false);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.LOCATION);
    }

    /**
     * Performs on enable.
     */
    @Override
    public void onEnable() {
        this.active = false;
        if (this.trigger == null) {
            this.setTrigger(new DungeonStartTrigger());
            this.trigger.setLocation(this.location);
            this.trigger.setInstance(this.instance);
            this.trigger.enable(this);
        }
    }

    /**
     * Performs on disable.
     */
    @Override
    public void onDisable() {
        this.active = false;
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        this.active = true;
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.GRASS_BLOCK);
        functionButton.setDisplayName("&dAllow Block Place/Break");
        functionButton.addLore("&eAllows whitelisting or");
        functionButton.addLore("&eblacklisting the placing or");
        functionButton.addLore("&ebreaking of a block at this");
        functionButton.addLore("&elocation.");
        return functionButton;
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
                        this.button = new MenuButton(Material.GRASS_BLOCK);
                        this.button.setDisplayName("&d&lToggle Place");
                        this.button.setEnchanted(AllowBlockFunction.this.allowPlace);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!AllowBlockFunction.this.allowPlace) {
                            LangUtils.sendMessage(player, "editor.function.allow-block.placing-allowed");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.allow-block.placing-denied");
                        }

                        AllowBlockFunction.this.allowPlace = !AllowBlockFunction.this.allowPlace;
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
                        this.button.setDisplayName("&d&lToggle Break");
                        this.button.setEnchanted(AllowBlockFunction.this.allowBreak);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!AllowBlockFunction.this.allowBreak) {
                            LangUtils.sendMessage(player, "editor.function.allow-block.breaking-allowed");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.allow-block.breaking-denied");
                        }

                        AllowBlockFunction.this.allowBreak = !AllowBlockFunction.this.allowBreak;
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.BUCKET);
                        this.button.setDisplayName("&d&lToggle Bucket");
                        this.button.setEnchanted(AllowBlockFunction.this.allowBucket);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!AllowBlockFunction.this.allowBucket) {
                            LangUtils.sendMessage(player, "editor.function.allow-block.buckets-allowed");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.allow-block.buckets-denied");
                        }

                        AllowBlockFunction.this.allowBucket = !AllowBlockFunction.this.allowBucket;
                    }
                });
    }

    /**
     * Returns whether allow place.
     */
    public boolean isAllowPlace() {
        return this.allowPlace;
    }

    /**
     * Returns whether allow break.
     */
    public boolean isAllowBreak() {
        return this.allowBreak;
    }

    /**
     * Returns whether allow bucket.
     */
    public boolean isAllowBucket() {
        return this.allowBucket;
    }

    /**
     * Returns whether active.
     */
    public boolean isActive() {
        return this.active;
    }
}
