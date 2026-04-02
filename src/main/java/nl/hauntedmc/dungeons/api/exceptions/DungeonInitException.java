package nl.hauntedmc.dungeons.api.exceptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.util.HelperUtils;

public class DungeonInitException extends Exception {
   private final List<String> info;
   private final boolean trace;

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
      Dungeons.inst().getLogger().warning(HelperUtils.colorize("&cERROR :: The dungeon '" + folder.getName() + "' was not loaded:"));
      Dungeons.inst().getLogger().info(HelperUtils.colorize("&c├─ " + this.getMessage()));

      for (int i = 0; i < this.info.size(); i++) {
         String line = this.info.get(i);
         String prefix = i == this.info.size() - 1 ? "&c└─ " : "&c├─ ";
         Dungeons.inst().getLogger().info(HelperUtils.colorize(prefix + line));
      }

      if (this.trace) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + this.getMessage());
      }
   }

   public List<String> getInfo() {
      return this.info;
   }
}
