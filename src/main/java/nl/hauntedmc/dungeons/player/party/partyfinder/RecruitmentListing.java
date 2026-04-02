package nl.hauntedmc.dungeons.player.party.partyfinder;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.player.party.DungeonPartyWrapper;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

public class RecruitmentListing implements Listener {
   private DungeonPlayer host;
   private String label;
   private String description;
   private int partySize;
   private String password;
   private boolean recruiting;
   private IDungeonParty party;
   private final List<UUID> players;
   private BukkitRunnable broadcaster;
   private final List<UUID> passwordPromptedPlayers;
   private final ConcurrentMap<UUID, Boolean> pendingPasswordInputs = new ConcurrentHashMap<>();

   public RecruitmentListing(DungeonPlayer host, String label, String description, int partySize, String password) {
      this.host = host;
      this.label = label;
      this.description = description;
      this.partySize = partySize;
      this.password = password;
      if (host.hasParty()) {
         this.party = host.getiDungeonParty();
      } else if (!Dungeons.inst().getPartyPluginName().equalsIgnoreCase("Default")
         && !Dungeons.inst().getPartyPluginName().equalsIgnoreCase("DungeonParties")) {
         this.party = new DungeonPartyWrapper(host);
      }

      this.players = new ArrayList<>();

      if (this.party != null && this.party.getPlayers() != null) {
         for (Player player : this.party.getPlayers()) {
            this.players.add(player.getUniqueId());
         }
      }

      this.passwordPromptedPlayers = new ArrayList<>();
   }

   public void postListing() {
      Bukkit.getPluginManager().registerEvents(this, Dungeons.inst());
      this.recruiting = true;
      this.broadcaster = new BukkitRunnable() {
         public void run() {
            if (RecruitmentListing.this.password.isEmpty()) {
               if (RecruitmentListing.this.verifyActive()) {
                  for (Player player : Bukkit.getOnlinePlayers()) {
                     if (!RecruitmentListing.this.players.contains(player.getUniqueId())) {
                        LangUtils.sendMessage(
                           player, "party.recruit.listing", HelperUtils.playerDisplayName(RecruitmentListing.this.host.getPlayer()), RecruitmentListing.this.label
                        );
                        StringUtils.sendClickableCommand(
                           player, LangUtils.getMessage("party.recruit.join.button"), "recruit join " + RecruitmentListing.this.host.getPlayer().getName()
                        );
                     }
                  }
               }
            }
         }
      };
      this.broadcaster
         .runTaskTimer(
            Dungeons.inst(), 0L, Dungeons.inst().getConfig().getInt("General.PartyFinder.ListingBroadcastPeriod", 5) * 1200L
         );
   }

   public void removeListing() {
      HandlerList.unregisterAll(this);
      this.recruiting = false;
      this.broadcaster.cancel();
      Dungeons.inst().getListingManager().removeListing(this.host.getPlayer());
   }

   public boolean join(DungeonPlayer aPlayer) {
      Player player = aPlayer.getPlayer();
      if (this.players.contains(player.getUniqueId())) {
         LangUtils.sendMessage(player, "party.recruit.already-in-party");
         return false;
      } else if (!this.recruiting || !this.verifyActive()) {
         LangUtils.sendMessage(player, "party.recruit.listing-expired");
         return false;
      } else if (!this.password.isEmpty()) {
         this.passwordPromptedPlayers.add(player.getUniqueId());
         LangUtils.sendMessage(player, "party.recruit.password.request");
         return true;
      } else {
         this.addPlayer(aPlayer);
         return true;
      }
   }

   public void addPlayer(DungeonPlayer aPlayer) {
      Player player = aPlayer.getPlayer();
      this.players.add(player.getUniqueId());
      this.party.addPlayer(player);

      for (Player target : this.party.getPlayers()) {
         target.playSound(target.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.5F, 1.2F);
         target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5F, 1.2F);
      }

      if (this.players.size() >= this.partySize) {
         this.removeListing();
         this.messagePlayers(LangUtils.getMessage("party.recruit.complete"), "entity.player.levelup");
      }
   }

   public void removePlayer(DungeonPlayer aPlayer) {
      Player player = aPlayer.getPlayer();
      this.players.remove(player.getUniqueId());
      if (this.party.hasPlayer(player)) {
         this.party.removePlayer(player);
      }
   }

   public void messagePlayers(String message, @Nullable String sound) {
      for (UUID uuid : this.players) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null) {
            player.sendMessage(HelperUtils.colorize(message));
            if (sound != null) {
               player.playSound(player.getLocation(), sound, 0.5F, 1.0F);
            }
         }
      }
   }

   public boolean verifyActive() {
      if (!this.party.getPlayers().isEmpty() && this.party.getLeader() == this.host.getPlayer()) {
         return true;
      } else {
         this.removeListing();
         return false;
      }
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void listenForPassword(AsyncChatEvent event) {
      Player player = event.getPlayer();
      if (this.passwordPromptedPlayers.contains(player.getUniqueId())) {
         event.setCancelled(true);
         UUID playerId = player.getUniqueId();
         if (this.pendingPasswordInputs.putIfAbsent(playerId, Boolean.TRUE) != null) {
            return;
         }

         String message = HelperUtils.plainText(event.originalMessage());
         Bukkit.getScheduler().runTask(Dungeons.inst(), () -> {
            try {
               this.handlePasswordInput(player, message);
            } finally {
               this.pendingPasswordInputs.remove(playerId);
            }
         });
      }
   }

   private void handlePasswordInput(Player player, String message) {
      if (message.equals("cancel")) {
         LangUtils.sendMessage(player, "party.recruit.join.cancel");
         this.passwordPromptedPlayers.remove(player.getUniqueId());
      } else if (message.contains(" ")) {
         LangUtils.sendMessage(player, "party.recruit.password.no-spaces");
      } else if (!message.equals(this.password)) {
         LangUtils.sendMessage(player, "party.recruit.password.incorrect");
      } else {
         LangUtils.sendMessage(player, "party.recruit.password.correct");
         this.passwordPromptedPlayers.remove(player.getUniqueId());
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         this.addPlayer(aPlayer);
      }
   }

   public DungeonPlayer getHost() {
      return this.host;
   }

   public void setHost(DungeonPlayer host) {
      this.host = host;
   }

   public String getLabel() {
      return this.label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getDescription() {
      return this.description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public int getPartySize() {
      return this.partySize;
   }

   public void setPartySize(int partySize) {
      this.partySize = partySize;
   }

   public String getPassword() {
      return this.password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public boolean isRecruiting() {
      return this.recruiting;
   }

   public IDungeonParty getParty() {
      return this.party;
   }

   public List<UUID> getPasswordPromptedPlayers() {
      return this.passwordPromptedPlayers;
   }
}
