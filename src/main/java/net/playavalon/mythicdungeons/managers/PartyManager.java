package net.playavalon.mythicdungeons.managers;

import java.util.ArrayList;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import org.bukkit.entity.Player;

public final class PartyManager {
   private List<MythicPlayer> spies = new ArrayList<>();

   public void addSpy(MythicPlayer player) {
      if (!this.spies.contains(player)) {
         this.spies.add(player);
      }
   }

   public void removeSpy(MythicPlayer player) {
      this.spies.remove(player);
   }

   public MythicParty getParty(Player player) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
      return mythicPlayer.getMythicParty();
   }

   public List<MythicPlayer> getSpies() {
      return this.spies;
   }

   public void setSpies(List<MythicPlayer> spies) {
      this.spies = spies;
   }
}
