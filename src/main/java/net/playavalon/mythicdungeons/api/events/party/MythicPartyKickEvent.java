package net.playavalon.mythicdungeons.api.events.party;

import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import org.bukkit.event.Cancellable;

public class MythicPartyKickEvent extends MythicPartyEvent implements Cancellable {
   private boolean cancelled;
   private final MythicPlayer kickedPlayer;
   private final MythicPlayer whoKicked;

   public MythicPartyKickEvent(MythicParty party, MythicPlayer kickedPlayer, MythicPlayer whoKicked) {
      super(party);
      this.kickedPlayer = kickedPlayer;
      this.whoKicked = whoKicked;
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancel) {
      this.cancelled = cancel;
   }

   public MythicPlayer getKickedPlayer() {
      return this.kickedPlayer;
   }

   public MythicPlayer getWhoKicked() {
      return this.whoKicked;
   }
}
