package net.playavalon.mythicdungeons.api;

import java.util.Collection;
import javax.annotation.Nullable;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import org.bukkit.entity.Player;

public interface MythicDungeonsService {
   boolean isPlayerInDungeon(Player var1);

   AbstractInstance getDungeonInstance(Player var1);

   boolean initiateDungeonForPlayer(Player var1, String var2);

   AbstractInstance getDungeonInstance(String var1);

   Collection<AbstractDungeon> getAllDungeons();

   boolean createParty(Player var1);

   boolean removeFromParty(Player var1);

   boolean disbandParty(MythicParty var1);

   boolean disbandParty(Player var1);

   boolean inviteToParty(Player var1, Player var2);

   boolean acceptPartyInvite(Player var1);

   boolean declinePartyInvite(Player var1);

   boolean setPartyLeader(Player var1);

   MythicParty getParty(Player var1);

   @Nullable
   String isPartyQueuedForDungeon(Player var1);

   @Nullable
   String isPartyQueuedForDungeon(IDungeonParty var1);
}
