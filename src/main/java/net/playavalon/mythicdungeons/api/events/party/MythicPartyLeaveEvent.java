package net.playavalon.mythicdungeons.api.events.party;

import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;

public class MythicPartyLeaveEvent extends MythicPartyEvent {
   private final MythicPlayer leavingPlayer;

   public MythicPartyLeaveEvent(MythicParty party, MythicPlayer leavingPlayer) {
      super(party);
      this.leavingPlayer = leavingPlayer;
   }

   public MythicPlayer getLeavingPlayer() {
      return this.leavingPlayer;
   }
}
