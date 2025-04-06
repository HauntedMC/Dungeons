package net.playavalon.mythicdungeons.api.events.party;

import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import org.bukkit.event.Cancellable;

public class AsyncMythicPartyChatEvent extends MythicPartyEvent implements Cancellable {
   private boolean cancelled;
   private String message;

   public AsyncMythicPartyChatEvent(MythicParty party, String message) {
      super(party, true);
      this.message = message;
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancel) {
      this.cancelled = cancel;
   }

   public String getMessage() {
      return this.message;
   }

   public void setMessage(String message) {
      this.message = message;
   }
}
