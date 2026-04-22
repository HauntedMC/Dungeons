package nl.hauntedmc.dungeons.content.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Trigger that reacts to players using a matching key item.
 */
@AutoRegister(id = "dungeons.trigger.key_item")
@SerializableAs("dungeons.trigger.key_item")
public class KeyItemTrigger extends DungeonTrigger {
    @PersistedField private ItemStack item;
    @PersistedField private boolean consumeItem = true;
    @PersistedField private boolean useAnywhere = false;
    @PersistedField private String clickType = "RIGHT";
    private ClickType click;

    /**
     * Creates a new KeyItemTrigger instance.
     */
    public KeyItemTrigger(Map<String, Object> config) {
        super("Key Item", config);
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
        this.item = ItemUtils.getDefaultKeyItem();
    }

    /**
     * Creates a new KeyItemTrigger instance.
     */
    public KeyItemTrigger() {
        super("Key Item");
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
        this.item = ItemUtils.getDefaultKeyItem();
    }

    /**
     * Performs initialize.
     */
    @Override
    public void initialize() {
        super.initialize();
        try {
            this.click = ClickType.valueOf(this.clickType);
        } catch (IllegalArgumentException exception) {
            this.clickType = ClickType.RIGHT.name();
            this.click = ClickType.RIGHT;
            this.logger()
                    .warn(
                            "KeyItemTrigger in dungeon '{}' at {} had invalid click type. Falling back to RIGHT.",
                            this.dungeonNameForLogs(),
                            this.locationForLogs(),
                            exception);
        }
    }

    /**
     * Performs on use item.
     */
    @EventHandler
    public void onUseItem(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() == this.instance.getInstanceWorld()) {
            if (event.getHand() != EquipmentSlot.OFF_HAND) {
                if (this.click.hasAction(event.getAction())) {
                    if (!this.useAnywhere) {
                        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                                && event.getAction() != Action.LEFT_CLICK_BLOCK) {
                            return;
                        }

                        if (event.getClickedBlock() == null) {
                            return;
                        }

                        Location blockLoc = event.getClickedBlock().getLocation();
                        Location aboveLoc = blockLoc.clone();
                        aboveLoc.setY(blockLoc.getY() + 1.0);
                        Location belowLoc = blockLoc.clone();
                        belowLoc.setY(blockLoc.getY() - 1.0);
                        if (!blockLoc.equals(this.location)
                                && !aboveLoc.equals(this.location)
                                && !belowLoc.equals(this.location)) {
                            return;
                        }
                    }

                    ItemStack usedItem = event.getItem();
                    ItemStack item = this.item.clone();

                    if (usedItem != null && usedItem.getType() != Material.AIR) {
                        if (item.isSimilar(usedItem)) {
                            if (this.consumeItem) {
                                usedItem.setAmount(usedItem.getAmount() - 1);
                            }

                            DungeonPlayerSession playerSession =
                                    RuntimeContext.playerSessions().get(player);
                            this.trigger(playerSession);
                            event.setCancelled(true);
                        }
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
        MenuButton button = new MenuButton(Material.ITEM_FRAME);
        button.setDisplayName("&aKey Item Detector");
        button.addLore("&eTriggers when a player uses");
        button.addLore("&ean item with right-click.");
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
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lAllow Retrigger");
                        this.button.setEnchanted(KeyItemTrigger.this.allowRetrigger);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!KeyItemTrigger.this.allowRetrigger) {
                            LangUtils.sendMessage(player, "editor.trigger.key-item.allow-repeat");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.key-item.prevent-repeat");
                        }

                        KeyItemTrigger.this.allowRetrigger = !KeyItemTrigger.this.allowRetrigger;
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.ITEM_FRAME);
                        this.button.setDisplayName("&d&lChoose Item");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        RuntimeContext.guiService().openGui(player, "selectitem_trigger");
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.FURNACE);
                        this.button.setDisplayName("&d&lToggle Consume Item");
                        this.button.setEnchanted(KeyItemTrigger.this.consumeItem);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!KeyItemTrigger.this.consumeItem) {
                            LangUtils.sendMessage(player, "editor.trigger.key-item.consume");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.key-item.do-not-consume");
                        }

                        KeyItemTrigger.this.consumeItem = !KeyItemTrigger.this.consumeItem;
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.TARGET);
                        this.button.setDisplayName("&d&lToggle Use Anywhere");
                        this.button.setEnchanted(KeyItemTrigger.this.useAnywhere);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!KeyItemTrigger.this.useAnywhere) {
                            LangUtils.sendMessage(player, "editor.trigger.key-item.use-anywhere");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.key-item.use-here-only");
                        }

                        KeyItemTrigger.this.useAnywhere = !KeyItemTrigger.this.useAnywhere;
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PLAYER_HEAD);
                        this.button.setDisplayName("&d&lClick Type: " + KeyItemTrigger.this.clickType);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        String currentClickType = KeyItemTrigger.this.clickType;
                        switch (currentClickType) {
                            case "RIGHT":
                                KeyItemTrigger.this.clickType = "LEFT";
                                break;
                            case "LEFT":
                                KeyItemTrigger.this.clickType = "BOTH";
                                break;
                            case "BOTH":
                                KeyItemTrigger.this.clickType = "RIGHT";
                        }

                        KeyItemTrigger.this.click = ClickType.valueOf(KeyItemTrigger.this.clickType);
                        LangUtils.sendMessage(
                                player,
                                "editor.trigger.key-item.click-type-set",
                                LangUtils.placeholder("click_type", KeyItemTrigger.this.clickType.toString()));
                        playerSession.showHotbar(this.menu, false);
                    }
                });
    }

    /**
     * Returns the item.
     */
    public ItemStack getItem() {
        return this.item;
    }

    /**
     * Sets the item.
     */
    public void setItem(ItemStack item) {
        this.item = item;
    }

    /**
     * Sets the consume item.
     */
    public void setConsumeItem(boolean consumeItem) {
        this.consumeItem = consumeItem;
    }

    /**
     * Sets the use anywhere.
     */
    public void setUseAnywhere(boolean useAnywhere) {
        this.useAnywhere = useAnywhere;
    }

    private enum ClickType {
        RIGHT(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK),
        LEFT(Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK),
        BOTH(
                Action.RIGHT_CLICK_AIR,
                Action.RIGHT_CLICK_BLOCK,
                Action.LEFT_CLICK_AIR,
                Action.LEFT_CLICK_BLOCK);

        private final List<Action> actions = new ArrayList<>();

        ClickType(Action... actions) {
            Collections.addAll(this.actions, actions);
        }

        /**
         * Returns whether it has action.
         */
        public boolean hasAction(Action action) {
            return this.actions.contains(action);
        }
    }
}
