package nl.hauntedmc.dungeons.gui.framework.window;

import java.util.HashMap;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.gui.framework.actions.Action;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.text.GuiTextUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;

/**
 * Registered inventory window with per-player state and click/open/close callbacks.
 */
public class GuiWindow implements Listener {
    private final String namespace;
    private final int size;
    private final String display;
    private boolean cancelClick = true;
    private final HashMap<Player, GuiInventory> inventories;
    private final HashMap<Integer, Button> buttons;
    private final HashMap<String, Action<InventoryOpenEvent>> openActions;
    private final HashMap<String, Action<InventoryCloseEvent>> closeActions;

    /** Creates and registers a new GUI window definition. */
    public GuiWindow(String namespace, int size, String displayName) {
        this.namespace = namespace;
        this.size = size;
        this.display = GuiTextUtils.fullColor(displayName);
        this.inventories = new HashMap<>();
        this.buttons = new HashMap<>();
        this.openActions = new HashMap<>();
        this.closeActions = new HashMap<>();
        Bukkit.getPluginManager().registerEvents(this, GuiService.plugin());
        GuiWindow previousWindow = GuiWindowRegistry.getWindow(namespace);
        if (previousWindow != null) {
            previousWindow.unregister();
        }

        // Window namespaces are unique; replacing the old registration avoids duplicate listeners
        // when menus are rebuilt at runtime.
        GuiWindowRegistry.register(this);
    }

    /** Sets whether clicks inside this GUI should be cancelled by default. */
    public final void setCancelClick(boolean cancel) {
        this.cancelClick = cancel;
    }

    /** Returns the player-scoped inventory snapshot for this window, if loaded. */
    public final GuiInventory getInventoryFor(Player player) {
        return this.inventories.get(player);
    }

    /** Adds or replaces a static button template in a given slot. */
    public final void addButton(int slot, Button button) {
        if (slot < this.size) {
            this.buttons.put(slot, button);
        }
    }

    /** Returns static button templates keyed by slot. */
    public final HashMap<Integer, Button> getButtons() {
        return this.buttons;
    }

    /** Returns the unique window namespace. */
    public final String getNamespace() {
        return this.namespace;
    }

    /** Returns this window inventory size. */
    public final int getSize() {
        return this.size;
    }

    /** Registers an inventory-open callback keyed by id. */
    public final void addOpenAction(String id, Action<InventoryOpenEvent> action) {
        this.openActions.put(id, action);
    }

    /** Registers an inventory-close callback keyed by id. */
    public final void addCloseAction(String id, Action<InventoryCloseEvent> action) {
        this.closeActions.put(id, action);
    }

    /** Pushes the player's current button snapshot into the live inventory view. */
    public final void updateButtons(Player player) {
        GuiInventory gui = this.inventories.get(player);
        Inventory inv = gui.inventory();

        for (Entry<Integer, Button> pair : gui.buttons().entrySet()) {
            inv.setItem(pair.getKey(), pair.getValue().getItem());
        }
    }

    /** Opens this window for a player, creating a player snapshot when needed. */
    public final void open(Player player) {
        GuiInventory gui;
        if (this.inventories.containsKey(player)) {
            gui = this.inventories.get(player);
        } else {
            gui =
                    new GuiInventory(
                            this,
                            Bukkit.createInventory(player, this.size, GuiTextUtils.component(this.display)));
            this.inventories.put(player, gui);
        }

        // Static button definitions are copied into the player's inventory snapshot on every open
        // so dynamic open actions can still mutate the per-player view afterward.
        Inventory inv = gui.inventory();

        for (Entry<Integer, Button> button : this.buttons.entrySet()) {
            inv.setItem(button.getKey(), button.getValue().getItem());
        }

        player.openInventory(inv);
    }

    /** Unregisters this window listener from Bukkit. */
    public final void unregister() {
        HandlerList.unregisterAll(this);
    }

    /** Routes button clicks to button command/action handlers. */
    @EventHandler
    protected void onButtonClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        GuiInventory gui = this.inventories.get(player);
        if (gui != null) {
            if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)
                    && event.getInventory() == gui.inventory()
                    && this.cancelClick) {
                event.setCancelled(true);
            }

            if (event.getClickedInventory() == gui.inventory()
                    || event.getInventory() == gui.inventory()) {
                if (this.cancelClick) {
                    event.setCancelled(true);
                }

                int slot = event.getRawSlot();
                Button button = gui.buttons().get(slot);
                if (button != null) {
                    button.click(event);
                }
            }
        }
    }

    /** Applies click-cancellation policy for this window inventory. */
    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof PlayerInventory)) {
            Player player = (Player) event.getWhoClicked();
            GuiInventory gui = this.inventories.get(player);
            if (gui != null) {
                if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)
                        && event.getInventory() == gui.inventory()
                        && this.cancelClick) {
                    event.setCancelled(true);
                }

                if (event.getClickedInventory() == gui.inventory()
                        || event.getInventory() == gui.inventory()) {
                    if (this.cancelClick) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    /** Prevents drag operations that modify this window inventory. */
    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onDrag(InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        GuiInventory gui = this.inventories.get(player);
        if (gui != null) {
            if (event.getInventory() == gui.inventory()) {
                event.setCancelled(true);
            }
        }
    }

    /** Runs registered open actions when this window is opened. */
    @EventHandler
    protected void onOpen(InventoryOpenEvent event) {
        Player player = (Player) event.getPlayer();
        GuiInventory gui = this.inventories.get(player);
        if (gui != null) {
            if (event.getInventory() == gui.inventory()) {

                for (Action<InventoryOpenEvent> action : this.openActions.values()) {
                    action.run(event);
                }
            }
        }
    }

    /** Runs registered close actions when this window is closed. */
    @EventHandler
    protected void onClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        GuiInventory gui = this.inventories.get(player);
        if (gui != null) {
            if (event.getInventory() == gui.inventory()) {

                for (Action<InventoryCloseEvent> action : this.closeActions.values()) {
                    action.run(event);
                }
            }
        }
    }
}
