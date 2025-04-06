package net.playavalon.mythicdungeons.player.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PartyWrapper implements IDungeonParty {
   private static String partyPlugin;

   public PartyWrapper(MythicPlayer host) {
      Player player = host.getPlayer();
      String var3 = partyPlugin.toLowerCase();
      switch (var3) {
//         case "heroes":
//            Hero heroHost = MythicDungeons.inst().getHeroesApi().getCharacterManager().getHero(host.getPlayer());
//            this.heroesParty = heroHost.getParty();
//            if (this.heroesParty == null) {
//               this.heroesParty = new HeroParty(heroHost, MythicDungeons.inst().getHeroesApi());
//               MythicDungeons.inst().getHeroesApi().getPartyManager().addParty(this.heroesParty);
//            }
//
//            this.initDungeonParty(MythicDungeons.inst().getHeroesApi());
//            break;
//         case "parties":
//            this.partiesParty = MythicDungeons.inst().getPartiesAPI().getPartyOfPlayer(host.getPlayer().getUniqueId());
//            if (this.partiesParty == null) {
//               PartyPlayer partyPlayer = MythicDungeons.inst().getPartiesAPI().getPartyPlayer(host.getPlayer().getUniqueId());
//               MythicDungeons.inst().getPartiesAPI().createParty(player.getName() + "'s Party", partyPlayer);
//               this.partiesParty = MythicDungeons.inst().getPartiesAPI().getPartyOfPlayer(host.getPlayer().getUniqueId());
//            }
//            this.initDungeonParty(new String[]{"Parties"});
      }

      host.setDungeonParty(this);
   }

   @Override
   public List<Player> getPlayers() {
      List<Player> players = new ArrayList<>();
      String var2 = partyPlugin.toLowerCase();
      switch (var2) {
//         case "heroes":
//            if (this.heroesParty == null) {
//               return players;
//            }
//
//            for (Hero hero : this.heroesParty.getMembers()) {
//               players.add(hero.getPlayer());
//            }
//            break;
//         case "parties":
//            if (this.partiesParty == null) {
//               return players;
//            }
//
//            for (UUID pId : this.partiesParty.getMembers()) {
//               Player player = Bukkit.getPlayer(pId);
//               if (player != null) {
//                  players.add(player);
//               }
//            }
      }

      return players;
   }

   @Override
   public void addPlayer(Player player) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (mythicPlayer.hasParty()) {
         assert mythicPlayer.getDungeonParty() != null;

         mythicPlayer.getDungeonParty().removePlayer(player);
      }

      mythicPlayer.setDungeonParty(this);
      String var3 = partyPlugin.toLowerCase();
      switch (var3) {
//         case "heroes":
//            Hero hero = MythicDungeons.inst().getHeroesApi().getCharacterManager().getHero(player);
//            if (!this.heroesParty.getMembers().contains(hero)) {
//               this.heroesParty.addMember(hero);
//            }
//            break;
//         case "parties":
//            PartyPlayer partyPlayer = MythicDungeons.inst().getPartiesAPI().getPartyPlayer(player.getUniqueId());
//            if (partyPlayer != null && !this.partiesParty.getMembers().contains(player.getUniqueId())) {
//               this.partiesParty.addMember(partyPlayer);
//            }
      }
   }

   @Override
   public void removePlayer(Player player) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
      mythicPlayer.setDungeonParty(null);
      String var3 = partyPlugin.toLowerCase();
      switch (var3) {
//         case "heroes":
//            Hero hero = MythicDungeons.inst().getHeroesApi().getCharacterManager().getHero(player);
//            if (this.heroesParty.getMembers().contains(hero)) {
//               this.heroesParty.removeMember(hero);
//            }
//            break;
//         case "parties":
//            PartyPlayer partyPlayer = MythicDungeons.inst().getPartiesAPI().getPartyPlayer(player.getUniqueId());
//            if (partyPlayer != null && this.partiesParty.getMembers().contains(player.getUniqueId())) {
//               this.partiesParty.removeMember(partyPlayer);
//            }
      }
   }

   @Override
   public boolean hasPlayer(Player player) {
      String var2 = partyPlugin.toLowerCase();
      switch (var2) {
//         case "heroes":
//            if (this.heroesParty == null) {
//               return false;
//            }
//
//            Hero hero = MythicDungeons.inst().getHeroesApi().getCharacterManager().getHero(player);
//            return this.heroesParty.getMembers().contains(hero);
//         case "parties":
//            if (this.partiesParty == null) {
//               return false;
//            }
//
//            return this.partiesParty.getMembers().contains(player.getUniqueId());
         default:
            return false;
      }
   }

   @NotNull
   public Player getLeader() {
      String var1 = partyPlugin.toLowerCase();
      switch (var1) {
//         case "heroes":
//            Hero hero = this.heroesParty.getLeader();
//            if (hero == null) {
//               if (this.heroesParty.getMembers().isEmpty()) {
//                  return null;
//               }
//
//               hero = (Hero)this.heroesParty.getMembers().toArray()[0];
//            }
//
//            return hero.getPlayer();
//         case "parties":
//            UUID leaderUUID = this.partiesParty.getLeader();
//            if (leaderUUID == null) {
//               if (this.partiesParty.getMembers().isEmpty()) {
//                  return null;
//               }
//
//               leaderUUID = (UUID)this.partiesParty.getMembers().toArray()[0];
//            }
//
//            return Bukkit.getPlayer(leaderUUID);
         default:
            return null;
      }
   }

   @Override
   public void partyMessage(String msg) {
      String var2 = partyPlugin.toLowerCase();
      switch (var2) {
//         case "heroes":
//            this.heroesParty.messageParty(Util.fullColor(msg));
//            break;
//         case "parties":
//            for (UUID pid : this.partiesParty.getMembers()) {
//               Player player = Bukkit.getPlayer(pid);
//               if (player != null && player.isOnline()) {
//                  player.sendMessage(Util.fullColor(msg));
//               }
//            }
      }
   }

   public void disband() {
      Player leader = this.getLeader();
      String var2 = partyPlugin.toLowerCase();
      switch (var2) {
//         case "heroes":
//            MythicDungeons.inst().getHeroesApi().getPartyManager().removeParty(this.heroesParty);
//
//            for (Hero hero : this.heroesParty.getMembers()) {
//               this.heroesParty.removeMember(hero);
//            }
//            break;
//         case "parties":
//            this.partiesParty.delete();
      }
   }

   @Nullable
   public static PartyWrapper adapt(MythicPlayer mPlayer) {
      String var1 = MythicDungeons.inst().getPartyPluginName().toLowerCase();
      switch (var1) {
//         case "heroes":
//            HeroParty heroParty = MythicDungeons.inst().getHeroesApi().getCharacterManager().getHero(mPlayer.getPlayer()).getParty();
//            if (heroParty == null) {
//               return null;
//            }
//            break;
//         case "parties":
//            Party partiesParty = MythicDungeons.inst().getPartiesAPI().getPartyOfPlayer(mPlayer.getPlayer().getUniqueId());
//            if (partiesParty == null) {
//               return null;
//            }
      }

      return new PartyWrapper(mPlayer);
   }

   public static void setPartyPlugin(String partyPlugin) {
      PartyWrapper.partyPlugin = partyPlugin;
   }
}
