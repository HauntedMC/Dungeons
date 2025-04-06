package net.playavalon.mythicdungeons.avngui.GUI;

import java.util.HashMap;

public class WindowGroupManager {
   private static final HashMap<String, WindowGroup> groups = new HashMap<>();

   protected static void registerGroup(WindowGroup group) {
      groups.put(group.getName(), group);
   }

   public static WindowGroup getGroup(String namespace) {
      WindowGroup group = groups.get(namespace);
      if (group == null) {
         System.out.println("ERROR :: GUI Group '" + namespace + "' was not found!");
         return null;
      } else {
         return group;
      }
   }

   protected static void put(WindowGroup group) {
      groups.put(group.getName(), group);
   }
}
