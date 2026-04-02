package nl.hauntedmc.dungeons.api.gui.window;

import java.util.HashMap;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.api.gui.GUIAPI;
import nl.hauntedmc.dungeons.api.gui.actions.Action;
import nl.hauntedmc.dungeons.api.gui.buttons.Button;
import nl.hauntedmc.dungeons.api.gui.utility.StringUtils;
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

public class GUIWindow implements Listener {
   private final String namespace;
   private final int size;
   private final String display;
   private boolean cancelClick = true;
    private final HashMap<Player, GUIInventory> inventories;
   private final HashMap<Integer, Button> buttons;
   private final HashMap<String, Action<InventoryOpenEvent>> openActions;
   private final HashMap<String, Action<InventoryCloseEvent>> closeActions;

   public GUIWindow(String namespace, int size, String displayname) {
      this.namespace = namespace;
      this.size = size;
      this.display = StringUtils.fullColor(displayname);
      this.inventories = new HashMap<>();
      this.buttons = new HashMap<>();
      this.openActions = new HashMap<>();
      this.closeActions = new HashMap<>();
      Bukkit.getPluginManager().registerEvents(this, GUIAPI.plugin);
      GUIWindow oldGUIWindow = GUIWindowRegistry.getWindowSilent(namespace);
      if (oldGUIWindow != null) {
         oldGUIWindow.unregister();
      }

      GUIWindowRegistry.put(this);
   }

   public final void setCancelClick(boolean cancel) {
      this.cancelClick = cancel;
   }

   public final GUIInventory getPlayersGui(Player player) {
      return this.inventories.get(player);
   }

   public final void addButton(int slot, Button button) {
      if (slot < this.size) {
         this.buttons.put(slot, button);
      }
   }

   public final HashMap<Integer, Button> getButtons() {
      return this.buttons;
   }

   public final String getName() {
      return this.namespace;
   }

   public final int getSize() {
      return this.size;
   }

   public final void addOpenAction(String id, Action<InventoryOpenEvent> action) {
      this.openActions.put(id, action);
   }

   public final void addCloseAction(String id, Action<InventoryCloseEvent> action) {
      this.closeActions.put(id, action);
   }

   public final void updateButtons(Player player) {
      GUIInventory gui = this.inventories.get(player);
      Inventory inv = gui.getInv();

      for (Entry<Integer, Button> pair : gui.getButtons().entrySet()) {
         inv.setItem(pair.getKey(), pair.getValue().getItem());
      }
   }

   public final void open(Player player) {
      GUIInventory gui;
      if (this.inventories.containsKey(player)) {
         gui = this.inventories.get(player);
      } else {
         gui = new GUIInventory(this, Bukkit.createInventory(player, this.size, StringUtils.component(this.display)));
         this.inventories.put(player, gui);
      }

      Inventory inv = gui.getInv();

      for (Entry<Integer, Button> button : this.buttons.entrySet()) {
         inv.setItem(button.getKey(), button.getValue().getItem());
      }

      player.openInventory(inv);
   }

   public final void unregister() {
      HandlerList.unregisterAll(this);
   }

   @EventHandler
   protected void onButtonClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      GUIInventory gui = this.inventories.get(player);
      if (gui != null) {
         if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)
            && event.getInventory() == gui.getInv()
            && this.cancelClick) {
            event.setCancelled(true);
         }

         if (event.getClickedInventory() == gui.getInv() || event.getInventory() == gui.getInv()) {
            if (this.cancelClick) {
               event.setCancelled(true);
            }

            int slot = event.getRawSlot();
            Button button = gui.getButtons().get(slot);
            if (button != null) {
               button.click(event);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   protected void onClick(InventoryClickEvent event) {
      if (!(event.getClickedInventory() instanceof PlayerInventory)) {
         Player player = (Player)event.getWhoClicked();
         GUIInventory gui = this.inventories.get(player);
         if (gui != null) {
            if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)
               && event.getInventory() == gui.getInv()
               && this.cancelClick) {
               event.setCancelled(true);
            }

            if (event.getClickedInventory() == gui.getInv() || event.getInventory() == gui.getInv()) {
               if (this.cancelClick) {
                  event.setCancelled(true);
               }

            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   protected void onDrag(InventoryDragEvent event) {
      Player player = (Player)event.getWhoClicked();
      GUIInventory gui = this.inventories.get(player);
      if (gui != null) {
         if (event.getInventory() == gui.getInv()) {
             event.setCancelled(true);
         }
      }
   }

   @EventHandler
   protected void onOpen(InventoryOpenEvent event) {
      Player player = (Player)event.getPlayer();
      GUIInventory gui = this.inventories.get(player);
      if (gui != null) {
         if (event.getInventory() == gui.getInv()) {

            for (Action<InventoryOpenEvent> action : this.openActions.values()) {
               action.run(event);
            }
         }
      }
   }

   @EventHandler
   protected void onClose(InventoryCloseEvent event) {
      Player player = (Player)event.getPlayer();
      GUIInventory gui = this.inventories.get(player);
      if (gui != null) {
         if (event.getInventory() == gui.getInv()) {

            for (Action<InventoryCloseEvent> action : this.closeActions.values()) {
               action.run(event);
            }
         }
      }
   }
}
