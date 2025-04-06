package net.playavalon.mythicdungeons.avngui.GUI;

import java.util.ArrayList;
import java.util.HashMap;
import net.playavalon.mythicdungeons.avngui.AvnGUI;
import org.bukkit.entity.Player;

public class WindowGroup {
   private final ArrayList<Window> windows = new ArrayList<>();
   private final HashMap<String, Window> windowsByName = new HashMap<>();
   private String namespace;
   private HashMap<Player, Integer> playerPosition;

   public WindowGroup() {
      WindowGroupManager.put(this);
      this.playerPosition = new HashMap<>();
   }

   public WindowGroup(String namespace) {
      this.namespace = namespace;
      WindowGroupManager.put(this);
      this.playerPosition = new HashMap<>();
      if (AvnGUI.debug) {
         System.out.println("Registered GUI Group: " + namespace);
      }
   }

   public final void addWindow(Window window) {
      this.windows.add(window);
      this.windowsByName.put(window.getName(), window);
      if (AvnGUI.debug) {
         System.out.println("Added '" + window.getName() + "' to group: " + this.namespace);
      }
   }

   public final void removeWindow(String namespace) {
      Window window = this.windowsByName.get(namespace);
      if (window == null) {
         System.out.println("ERROR :: Window '" + namespace + "' is not in group '" + this.namespace + "'!");
      } else {
         this.windowsByName.remove(namespace);
         this.windows.remove(window);
      }
   }

   public final String getName() {
      return this.namespace;
   }

   public final ArrayList<Window> getWindows() {
      return this.windows;
   }

   public final Window getWindow(String namespace) {
      return this.windowsByName.get(namespace);
   }

   public final void open(Player player) {
      if (this.windows.isEmpty()) {
         System.out.println("ERROR :: Window group '" + this.namespace + "' is empty and cannot be opened!");
      } else {
         Window window = this.windows.get(0);
         window.open(player);
         this.playerPosition.put(player, 0);
      }
   }

   public final void next(Player player) {
      int currentWindow = this.playerPosition.get(player);
      int nextWindow = currentWindow + 1;
      if (nextWindow < this.windows.size()) {
         this.playerPosition.put(player, nextWindow);
         Window window = this.windows.get(nextWindow);
         window.open(player);
      }
   }

   public final void previous(Player player) {
      int currentWindow = this.playerPosition.get(player);
      int prevWindow = currentWindow - 1;
      if (prevWindow >= 0) {
         this.playerPosition.put(player, prevWindow);
         Window window = this.windows.get(prevWindow);
         window.open(player);
      }
   }
}
