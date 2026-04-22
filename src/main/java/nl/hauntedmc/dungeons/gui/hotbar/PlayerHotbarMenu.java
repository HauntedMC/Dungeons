package nl.hauntedmc.dungeons.gui.hotbar;

import java.util.HashMap;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuAction;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerHotbarSnapshot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Hotbar-based menu model used by the dungeon editor.
 *
 * <p>Unlike inventory windows, hotbar menus intercept normal interaction and repurpose the player's
 * hotbar slots as immediate editor actions.
 */
public abstract class PlayerHotbarMenu extends PlayerHotbarSnapshot implements Listener {
    protected final HashMap<Integer, MenuItem> menuItems = new HashMap<>();
    protected final HashMap<Integer, MenuAction<PlayerEvent>> selectActions;
    protected final HashMap<Integer, MenuAction<PlayerItemHeldEvent>> hoverActions;
    protected int selected;

    /** Creates a hotbar menu with default action maps and close button in slot 8. */
    public PlayerHotbarMenu() {
        this.selectActions = new HashMap<>();
        this.hoverActions = new HashMap<>();
        this.menuItems.put(8, MenuItem.CLOSE);
    }

    @Override
    public void applyTo(PlayerInventory inventory) {
        this.renderMenu();
        super.applyTo(inventory);
    }

    @Override
    public void applyTo(PlayerInventory inventory, boolean restoreHeldSlot) {
        this.renderMenu();
        super.applyTo(inventory, restoreHeldSlot);
    }

    /** Adds a menu item to the next available slot index. */
    public void addMenuItem(MenuItem item) {
        this.addMenuItem(this.menuItems.size(), item);
    }

    /** Adds or replaces a menu item at a specific hotbar slot. */
    public void addMenuItem(int slot, MenuItem item) {
        this.menuItems.put(slot, item);
        item.setMenu(this);
    }

    /** Rebuilds button visuals and writes them into the snapshot hotbar. */
    public void renderMenu() {
        for (Entry<Integer, MenuItem> pair : this.menuItems.entrySet()) {
            int slot = pair.getKey();
            MenuItem item = pair.getValue();
            if (item == null) {
                this.setItem(slot, null);
            } else {
                item.buildButton();
                if (item.getButton() == null) {
                    this.setItem(slot, null);
                } else {
                    this.setItem(slot, item.getButton().getItem());
                }
            }
        }
    }

    /** Shows this menu for a player session. */
    public void openFor(DungeonPlayerSession playerSession) {
        playerSession.showHotbar(this);
    }

    /** Handles right-click slot select interaction while this menu is active. */
    @EventHandler(priority = EventPriority.LOW)
    public void onSlotSelect(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR) {
            Player player = event.getPlayer();
            DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
            if (playerSession.getCurrentHotbar() == this) {
                if (event.getHand() != EquipmentSlot.OFF_HAND) {
                    this.selected = player.getInventory().getHeldItemSlot();
                    MenuItem menuItem = this.menuItems.get(this.selected);
                    if (menuItem != null) {
                        // Right click is treated as the primary "select" gesture for hotbar menus
                        // so the player can keep the editor open without moving items around.
                        event.setCancelled(true);
                        player.playSound(
                                player.getLocation(), "minecraft:item.lodestone_compass.lock", 1.0F, 1.2F);
                        playerSession.setChatListening(false);
                        menuItem.runSelectActions(event);
                        this.renderMenu();
                    }
                }
            }
        }
    }

    /** Handles right-click-on-entity slot select interaction while this menu is active. */
    @EventHandler(priority = EventPriority.LOW)
    public void onSlotSelect(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        if (playerSession.getCurrentHotbar() == this) {
            if (event.getHand() != EquipmentSlot.OFF_HAND) {
                this.selected = player.getInventory().getHeldItemSlot();
                MenuItem menuItem = this.menuItems.get(this.selected);
                if (menuItem != null) {
                    event.setCancelled(true);
                    player.playSound(
                            player.getLocation(), "minecraft:item.lodestone_compass.lock", 1.0F, 1.2F);
                    playerSession.setChatListening(false);
                    menuItem.runSelectActions(event);
                    this.renderMenu();
                }
            }
        }
    }

    /** Handles left-click slot actions while this menu is active. */
    @EventHandler(priority = EventPriority.LOW)
    public void onSlotClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK
                || event.getAction() == Action.LEFT_CLICK_AIR) {
            Player player = event.getPlayer();
            DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
            if (playerSession.getCurrentHotbar() == this) {
                if (event.getHand() != EquipmentSlot.OFF_HAND) {
                    this.selected = player.getInventory().getHeldItemSlot();
                    MenuItem menuItem = this.menuItems.get(this.selected);
                    if (menuItem != null) {
                        event.setCancelled(true);
                        player.playSound(
                                player.getLocation(), "minecraft:item.lodestone_compass.lock", 1.0F, 1.2F);
                        playerSession.setChatListening(false);
                        menuItem.runClickActions(event);
                        this.renderMenu();
                    }
                }
            }
        }
    }

    /** Prevents inventory clicks from replacing managed hotbar items. */
    @EventHandler
    public void onClickHotbarItem(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        if (playerSession != null) {
            if (event.getClickedInventory() instanceof PlayerInventory) {
                if (playerSession.getCurrentHotbar() == this) {
                    int slot = event.getSlot();
                    if (slot <= 8) {
                        ItemStack newItem = this.getItem(slot);
                        if (newItem != null && newItem.getType() != Material.AIR) {
                            event.setCancelled(true);
                            player.closeInventory();
                        }
                    }
                }
            }
        }
    }

    /** Prevents creative-inventory edits from replacing managed hotbar items. */
    @EventHandler
    public void onClickHotbarItem(InventoryCreativeEvent event) {
        Player player = (Player) event.getWhoClicked();
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        if (playerSession != null) {
            if (event.getClickedInventory() instanceof PlayerInventory) {
                if (playerSession.getCurrentHotbar() == this) {
                    int slot = event.getSlot();
                    if (slot <= 8) {
                        ItemStack newItem = this.getItem(slot);
                        if (newItem != null && newItem.getType() != Material.AIR) {
                            event.setCancelled(true);
                            player.closeInventory();
                        }
                    }
                }
            }
        }
    }

    /** Prevents drag operations from replacing managed hotbar items. */
    @EventHandler
    public void onDragHotbarItem(InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        if (playerSession != null) {
            if (event.getInventory() instanceof PlayerInventory) {
                if (playerSession.getCurrentHotbar() == this) {
                    for (int slot : event.getInventorySlots()) {
                        if (slot <= 8) {
                            ItemStack newItem = this.getItem(slot);
                            if (newItem != null && newItem.getType() != Material.AIR) {
                                event.setCancelled(true);
                                player.closeInventory();
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /** Runs hover-enter actions when the held hotbar slot changes. */
    @EventHandler
    public void onHover(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        if (playerSession != null) {
            if (playerSession.getCurrentHotbar() == this) {
                int newSlot = event.getNewSlot();
                MenuItem menuItem = this.menuItems.get(newSlot);
                if (menuItem != null) {
                    menuItem.runHoverActions(event);
                    this.renderMenu();
                }
            }
        }
    }

    /** Runs hover-leave actions when the held hotbar slot changes. */
    @EventHandler
    public void onUnhover(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        if (playerSession != null) {
            if (playerSession.getCurrentHotbar() == this) {
                int oldSlot = event.getPreviousSlot();
                MenuItem menuItem = this.menuItems.get(oldSlot);
                if (menuItem != null) {
                    menuItem.runUnhoverActions(event);
                    this.renderMenu();
                }
            }
        }
    }

    /** Creates a lightweight empty hotbar menu instance. */
    public static PlayerHotbarMenu createMenu() {
        return new PlayerHotbarMenu() {};
    }

    /** Returns menu items keyed by hotbar slot. */
    public HashMap<Integer, MenuItem> getMenuItems() {
        return this.menuItems;
    }

    /** Returns the currently selected hotbar slot. */
    public int getSelected() {
        return this.selected;
    }

    /** Sets the currently selected hotbar slot. */
    public void setSelected(int selected) {
        this.selected = selected;
    }
}
