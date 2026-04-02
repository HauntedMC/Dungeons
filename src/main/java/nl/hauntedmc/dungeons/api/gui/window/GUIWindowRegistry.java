package nl.hauntedmc.dungeons.api.gui.window;

import java.util.HashMap;

public class GUIWindowRegistry {
   private static final HashMap<String, GUIWindow> windows = new HashMap<>();

   public static GUIWindow getWindow(String namespace) {
       return windows.get(namespace);
   }

   public static GUIWindow getWindowSilent(String namespace) {
       return windows.get(namespace);
   }

   protected static void put(GUIWindow GUIWindow) {
      windows.put(GUIWindow.getName(), GUIWindow);
   }
}
