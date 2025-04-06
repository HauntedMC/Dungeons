package net.playavalon.mythicdungeons.api.events.party;

import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import org.bukkit.event.Cancellable;

public class MythicPartyJoinEvent extends MythicPartyEvent implements Cancellable {
   private boolean cancelled;
   private final MythicPlayer joiningPlayer;

   public MythicPartyJoinEvent(MythicParty party, MythicPlayer player) {
      super(party);
      this.joiningPlayer = player;
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancel) {
      this.cancelled = cancel;
   }

   public MythicPlayer getJoiningPlayer() {
      return this.joiningPlayer;
   }
}
