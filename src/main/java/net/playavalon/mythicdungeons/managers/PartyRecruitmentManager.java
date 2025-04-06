package net.playavalon.mythicdungeons.managers;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partyfinder.RecruitmentBuilder;
import net.playavalon.mythicdungeons.player.party.partyfinder.RecruitmentListing;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class PartyRecruitmentManager {
   private final Map<UUID, RecruitmentBuilder> builders = new HashMap<>();
   private final Map<UUID, RecruitmentListing> listings = new HashMap<>();

   public void putListing(MythicPlayer aPlayer, RecruitmentListing listing) {
      this.putListing(aPlayer.getPlayer(), listing);
   }

   public void putListing(Player player, RecruitmentListing listing) {
      this.listings.put(player.getUniqueId(), listing);
      this.removeBuilder(player);
   }

   @Nullable
   public RecruitmentListing getListing(Player player) {
      return this.listings.get(player.getUniqueId());
   }

   public void removeListing(Player player) {
      this.listings.remove(player.getUniqueId());
   }

   public void clear() {
      this.builders.clear();
      this.listings.clear();
   }

   public Collection<RecruitmentListing> getListings() {
      return this.listings.values();
   }

   public void putBuilder(MythicPlayer aPlayer, RecruitmentBuilder builder) {
      this.putBuilder(aPlayer.getPlayer(), builder);
   }

   public void putBuilder(Player player, RecruitmentBuilder builder) {
      this.builders.put(player.getUniqueId(), builder);
   }

   @Nullable
   public RecruitmentBuilder getBuilder(Player player) {
      return this.builders.get(player.getUniqueId());
   }

   public void removeBuilder(Player player) {
      RecruitmentBuilder builder = this.builders.get(player.getUniqueId());
      if (builder != null) {
         builder.dispose();
      }

      this.builders.remove(player.getUniqueId());
   }
}
