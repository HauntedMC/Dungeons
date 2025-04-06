package net.playavalon.mythicdungeons.api.events.party;

import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;

public class MythicPartyCreateEvent extends MythicPartyEvent {
   private final MythicPlayer hostPlayer;

   public MythicPartyCreateEvent(MythicParty party, MythicPlayer host) {
      super(party);
      this.hostPlayer = host;
   }

   public MythicPlayer getHostPlayer() {
      return this.hostPlayer;
   }
}
