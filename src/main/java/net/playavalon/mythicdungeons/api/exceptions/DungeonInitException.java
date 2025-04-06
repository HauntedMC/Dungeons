package net.playavalon.mythicdungeons.api.exceptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.utility.helpers.Util;

public class DungeonInitException extends Exception {
   private List<String> info;
   private boolean trace;

   public DungeonInitException(String message, boolean printStackTrace, String... info) {
      super(message);
      this.trace = printStackTrace;
      if (info == null) {
         this.info = new ArrayList<>();
      } else {
         this.info = new ArrayList<>(List.of(info));
      }
   }

   public void addMessage(String message) {
      this.info.add(message);
   }

   public void printError(File folder) {
      MythicDungeons.inst().getLogger().warning(Util.colorize("&cERROR :: The dungeon '" + folder.getName() + "' was not loaded:"));
      MythicDungeons.inst().getLogger().info(Util.colorize("&c├─ " + this.getMessage()));

      for (int i = 0; i < this.info.size(); i++) {
         String line = this.info.get(i);
         String prefix = i == this.info.size() - 1 ? "&c└─ " : "&c├─ ";
         MythicDungeons.inst().getLogger().info(Util.colorize(prefix + line));
      }

      if (this.trace) {
         this.printStackTrace();
      }
   }

   public List<String> getInfo() {
      return this.info;
   }
}
