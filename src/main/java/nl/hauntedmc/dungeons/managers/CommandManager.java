package nl.hauntedmc.dungeons.managers;

import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.commands.dungeon.DungeonsCommand;
import nl.hauntedmc.dungeons.commands.dungeon.NotReadyCommand;
import nl.hauntedmc.dungeons.commands.dungeon.ReadyCommand;
import nl.hauntedmc.dungeons.commands.dungeon.RewardsCommand;
import nl.hauntedmc.dungeons.commands.party.RecruitCommand;

public class CommandManager {
   private final Dungeons plugin;

   public CommandManager(Dungeons plugin) {
      this.plugin = plugin;
      this.registerCommands();
   }

   private void registerCommands() {
      new DungeonsCommand(this.plugin, "dungeon");
      new NotReadyCommand(this.plugin, "notready");
      new ReadyCommand(this.plugin, "ready");
      new RewardsCommand(this.plugin, "dungeonrewards");
      new RecruitCommand(this.plugin, "recruit");
   }
}
