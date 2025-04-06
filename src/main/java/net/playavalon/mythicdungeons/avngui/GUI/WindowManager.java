package net.playavalon.mythicdungeons.avngui.GUI;

import java.util.HashMap;

public class WindowManager {
   private static final HashMap<String, Window> windows = new HashMap<>();

   protected static void registerWindow(Window window) {
      windows.put(window.getName(), window);
   }

   public static Window getWindow(String namespace) {
      Window window = windows.get(namespace);
      if (window == null) {
         System.out.println("ERROR :: GUI Window '" + namespace + "' was not found!");
         return null;
      } else {
         return window;
      }
   }

   public static Window getWindowSilent(String namespace) {
      Window window = windows.get(namespace);
      return window == null ? null : window;
   }

   protected static void put(Window window) {
      windows.put(window.getName(), window);
   }
}
