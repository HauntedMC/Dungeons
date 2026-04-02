package nl.hauntedmc.dungeons.player.party.partyfinder;

import java.util.Optional;
import java.util.stream.IntStream;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

public class RecruitmentBuilder implements Listener {
   private final DungeonPlayer host;
   private String label;
   private String description;
   private int partySize;
   private String password;
   private RecruitmentBuilder.InputType inputType;

   public RecruitmentBuilder(DungeonPlayer aPlayer) {
      this.host = aPlayer;
      this.inputType = RecruitmentBuilder.InputType.LABEL;
      Bukkit.getPluginManager().registerEvents(this, Dungeons.inst());
   }

   public static RecruitmentBuilder newListing(DungeonPlayer aPlayer) {
      return new RecruitmentBuilder(aPlayer);
   }

   public RecruitmentBuilder setLabel(@NotNull String label) {
      this.label = label;
      return this;
   }

   public RecruitmentBuilder setDescription(@NotNull String description) {
      this.description = description;
      return this;
   }

   public RecruitmentBuilder setPartySize(int size) {
      this.partySize = size;
      return this;
   }

   public RecruitmentBuilder setPassword(@NotNull String password) {
      this.password = password;
      return this;
   }

   public RecruitmentListing build() {
      RecruitmentListing listing = new RecruitmentListing(this.host, this.label, this.description, this.partySize, this.password);
      listing.postListing();
      return listing;
   }

   public void dispose() {
      HandlerList.unregisterAll(this);
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onChat(AsyncPlayerChatEvent event) {
      Player player = this.host.getPlayer();
      if (event.getPlayer() == player) {
         String message = event.getMessage();
         event.setCancelled(true);
         event.setMessage("");
         if (message.equalsIgnoreCase("cancel")) {
            LangUtils.sendMessage(player, "commands.recruit.cancel.success");
            this.dispose();
         } else {
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.0F);
            switch (this.inputType) {
               case DESCRIPTION:
                  IntStream charsx_ = message.chars();
                  if (charsx_.count() > 128L) {
                     LangUtils.sendMessage(player, "commands.recruit.setup.too-many-characters", "128");
                     return;
                  }

                  this.setDescription(message);
                  this.inputType = RecruitmentBuilder.InputType.PARTY_SIZE;
                  LangUtils.sendMessage(player, "commands.recruit.setup.description-set", message);
                  LangUtils.sendMessage(player, "commands.recruit.setup.players");
                  break;
               case PARTY_SIZE:
                  Optional<Integer> value = StringUtils.readIntegerInput(player, message);
                  int count = value.orElse(0);
                  if (count <= 1) {
                     LangUtils.sendMessage(player, "commands.recruit.setup.party-too-small");
                     return;
                  }

                  if (count > 12) {
                     LangUtils.sendMessage(player, "commands.recruit.setup.party-too-big");
                     return;
                  }

                  this.setPartySize(count);
                  this.inputType = RecruitmentBuilder.InputType.PASSWORD;
                  LangUtils.sendMessage(player, "commands.recruit.setup.players-set", message);
                  LangUtils.sendMessage(player, "commands.recruit.setup.password");
                  break;
               case PASSWORD:
                  if (message.equalsIgnoreCase("none")) {
                     this.setPassword("");
                     LangUtils.sendMessage(player, "commands.recruit.setup.open-recruitment");
                     LangUtils.sendMessage(player, "commands.recruit.setup.complete");
                     player.playSound(player.getLocation(), "entity.player.levelup", 1.0F, 1.0F);
                     Dungeons.inst().getListingManager().putListing(player, this.build());
                     this.dispose();
                     return;
                  }

                  IntStream chars = message.chars();
                  if (chars.count() > 24L) {
                     LangUtils.sendMessage(player, "commands.recruit.setup.too-many-characters", "24");
                     return;
                  }

                  String[] words_ = message.split("\\s");
                  if (words_.length > 1) {
                     LangUtils.sendMessage(player, "commands.recruit.setup.no-spaces");
                     return;
                  }

                  this.setPassword(message);
                  LangUtils.sendMessage(player, "commands.recruit.setup.password-set", message);
                  LangUtils.sendMessage(player, "commands.recruit.setup.complete");
                  player.playSound(player.getLocation(), "entity.player.levelup", 1.0F, 1.0F);
                  Bukkit.getScheduler().runTask(Dungeons.inst(), () -> Dungeons.inst().getListingManager().putListing(player, this.build()));
                  this.dispose();
               case LABEL:
               default:
                  IntStream charsx = message.chars();
                  if (charsx.count() > 24L) {
                     LangUtils.sendMessage(player, "commands.recruit.setup.too-many-characters", "24");
                     return;
                  }

                  String[] words = message.split("\\s");
                  if (words.length > 3) {
                     LangUtils.sendMessage(player, "commands.recruit.setup.too-many-words", "3");
                     return;
                  }

                  this.setLabel(message);
                  this.inputType = RecruitmentBuilder.InputType.DESCRIPTION;
                  LangUtils.sendMessage(player, "commands.recruit.setup.label-set", message);
                  LangUtils.sendMessage(player, "commands.recruit.setup.description");
                  break;
            }
         }
      }
   }

   private enum InputType {
      LABEL,
      DESCRIPTION,
      PARTY_SIZE,
      PASSWORD
   }
}
