package net.playavalon.mythicdungeons.api.events.party;

import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public abstract class MythicPartyEvent extends Event {
   private static final HandlerList HANDLERS_LIST = new HandlerList();
   protected final MythicParty party;

   public MythicPartyEvent(MythicParty party) {
      this.party = party;
   }

   public MythicPartyEvent(MythicParty party, boolean isAsync) {
      super(isAsync);
      this.party = party;
   }

   public MythicParty getParty() {
      return this.party;
   }

   @NotNull
   public HandlerList getHandlers() {
      return HANDLERS_LIST;
   }

   public static HandlerList getHandlerList() {
      return HANDLERS_LIST;
   }
}
