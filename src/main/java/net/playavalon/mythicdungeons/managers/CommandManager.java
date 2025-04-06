package net.playavalon.mythicdungeons.managers;

import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.commands.dungeon.LeaveCommand;
import net.playavalon.mythicdungeons.commands.dungeon.MythicDungeonsCommand;
import net.playavalon.mythicdungeons.commands.dungeon.NotReadyCommand;
import net.playavalon.mythicdungeons.commands.dungeon.ReadyCommand;
import net.playavalon.mythicdungeons.commands.dungeon.RewardsCommand;
import net.playavalon.mythicdungeons.commands.dungeon.StuckCommand;
import net.playavalon.mythicdungeons.commands.party.MythicPartyCommand;
import net.playavalon.mythicdungeons.commands.party.PartyChatCommand;
import net.playavalon.mythicdungeons.commands.party.RecruitCommand;

public class CommandManager {
   private final MythicDungeons plugin;

   public CommandManager(MythicDungeons plugin) {
      this.plugin = plugin;
      this.registerCommands();
   }

   private void registerCommands() {
      new MythicDungeonsCommand(this.plugin, "md");
      new LeaveCommand(this.plugin, "leave");
      new NotReadyCommand(this.plugin, "notready");
      new ReadyCommand(this.plugin, "ready");
      new RewardsCommand(this.plugin, "rewards");
      new StuckCommand(this.plugin, "stuck");
      new RecruitCommand(this.plugin, "recruit");
      if (MythicDungeons.inst().getPartyPluginName().equalsIgnoreCase("Default")
         || MythicDungeons.inst().getPartyPluginName().equalsIgnoreCase("DungeonParties")) {
         new PartyChatCommand(this.plugin, "p");
         new MythicPartyCommand(this.plugin, "party");
      }
   }
}
