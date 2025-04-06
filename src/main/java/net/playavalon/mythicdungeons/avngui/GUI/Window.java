package net.playavalon.mythicdungeons.avngui.GUI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import net.playavalon.mythicdungeons.avngui.AvnAPI;
import net.playavalon.mythicdungeons.avngui.AvnGUI;
import net.playavalon.mythicdungeons.avngui.GUI.Actions.Action;
import net.playavalon.mythicdungeons.avngui.GUI.Buttons.Button;
import net.playavalon.mythicdungeons.avngui.Utility.StringUtils;
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

public class Window implements Listener {
   private final String namespace;
   private final int size;
   private String display;
   private boolean cancelClick = true;
   private boolean cancelDrag = true;
   private boolean allowPlayerInventoryClick;
   private WindowGroup group;
   private final HashMap<Player, GUIInventory> inventories;
   private final HashMap<Integer, Button> buttons;
   private final HashMap<String, Action<InventoryOpenEvent>> openActions;
   private final HashMap<String, Action<InventoryCloseEvent>> closeActions;
   private final HashMap<String, Action<InventoryClickEvent>> clickActions;
   private final List<Player> viewers;

   public Window(String namespace, int size, String displayname) {
      this.namespace = namespace;
      this.size = size;
      this.display = StringUtils.fullColor(displayname);
      this.inventories = new HashMap<>();
      this.buttons = new HashMap<>();
      this.openActions = new HashMap<>();
      this.closeActions = new HashMap<>();
      this.clickActions = new HashMap<>();
      this.viewers = new ArrayList<>();
      Bukkit.getPluginManager().registerEvents(this, AvnAPI.plugin);
      Window oldWindow = WindowManager.getWindowSilent(namespace);
      if (oldWindow != null) {
         oldWindow.unregister();
      }

      WindowManager.put(this);
      if (AvnGUI.debug) {
         System.out.println("Registered GUI Window: " + namespace);
      }
   }

   public Window(String namespace, int size, String displayname, WindowGroup group) {
      this.namespace = namespace;
      this.size = size;
      this.display = StringUtils.fullColor(displayname);
      this.inventories = new HashMap<>();
      this.buttons = new HashMap<>();
      this.openActions = new HashMap<>();
      this.closeActions = new HashMap<>();
      this.clickActions = new HashMap<>();
      this.group = group;
      this.viewers = new ArrayList<>();
      Bukkit.getPluginManager().registerEvents(this, AvnAPI.plugin);
      WindowManager.put(this);
      if (AvnGUI.debug) {
         System.out.println("Registered GUI Window: " + namespace);
      }

      group.addWindow(this);
   }

   public final void setCancelClick(boolean cancel) {
      this.cancelClick = cancel;
   }

   public final void setCancelDrag(boolean cancel) {
      this.cancelDrag = cancel;
   }

   public final void setAllowPlayerInventoryClick(boolean allow) {
      this.allowPlayerInventoryClick = allow;
   }

   public final GUIInventory getPlayersGui(Player player) {
      return this.inventories.get(player);
   }

   public final List<Player> getViewers() {
      return this.viewers;
   }

   public final void addButton(int slot, Button button) {
      if (slot < this.size) {
         this.buttons.put(slot, button);
      }
   }

   public final Button editPlayersButton(Player player, int slot) {
      GUIInventory inv = this.inventories.get(player);
      Button baseButton = inv.getButtons().get(slot);
      if (baseButton == null) {
         return null;
      } else {
         Button button = new Button(baseButton);
         inv.setButton(slot, button);
         return button;
      }
   }

   public final void removePlayersButton(Player player, int slot) {
      GUIInventory inv = this.inventories.get(player);
      inv.removeButton(slot);
   }

   public final HashMap<Integer, Button> getButtons() {
      return this.buttons;
   }

   public final void removeButton(int slot) {
      this.buttons.remove(slot);
   }

   public final void setLabel(String label) {
      this.display = StringUtils.fullColor(label);
   }

   public final String getLabel() {
      return this.display;
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

   public final void addClickAction(String id, Action<InventoryClickEvent> action) {
      this.clickActions.put(id, action);
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
         gui = new GUIInventory(this, Bukkit.createInventory(player, this.size, this.display));
         this.inventories.put(player, gui);
      }

      Inventory inv = gui.getInv();

      for (Entry<Integer, Button> button : this.buttons.entrySet()) {
         inv.setItem(button.getKey(), button.getValue().getItem());
      }

      player.openInventory(inv);
   }

   public final void next(Player player) {
      if (this.group != null) {
         this.group.next(player);
      }
   }

   public final void previous(Player player) {
      if (this.group != null) {
         this.group.previous(player);
      }
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
               button.click(event, this);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   protected void onClick(InventoryClickEvent event) {
      if (!this.allowPlayerInventoryClick || !(event.getClickedInventory() instanceof PlayerInventory)) {
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

               for (Action<InventoryClickEvent> action : this.clickActions.values()) {
                  action.run(event);
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
            if (this.cancelDrag) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler
   protected void onOpen(InventoryOpenEvent event) {
      Player player = (Player)event.getPlayer();
      GUIInventory gui = this.inventories.get(player);
      if (gui != null) {
         if (event.getInventory() == gui.getInv()) {
            this.viewers.add(player);

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
            this.viewers.remove(player);

            for (Action<InventoryCloseEvent> action : this.closeActions.values()) {
               action.run(event);
            }
         }
      }
   }
}
