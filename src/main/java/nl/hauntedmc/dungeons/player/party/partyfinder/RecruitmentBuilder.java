package nl.hauntedmc.dungeons.player.party.partyfinder;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class RecruitmentBuilder implements Listener {
   private final DungeonPlayer host;
   private String label;
   private String description;
   private int partySize;
   private String password;
   private RecruitmentBuilder.InputType inputType;
   private final AtomicBoolean handlingInput = new AtomicBoolean();

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
   public void onChat(AsyncChatEvent event) {
      Player player = this.host.getPlayer();
      if (event.getPlayer() == player) {
         event.setCancelled(true);
         if (!this.handlingInput.compareAndSet(false, true)) {
            return;
         }

         String message = HelperUtils.plainText(event.originalMessage());
         Bukkit.getScheduler().runTask(Dungeons.inst(), () -> {
            try {
               this.handleChatInput(player, message);
            } finally {
               this.handlingInput.set(false);
            }
         });
      }
   }

   private void handleChatInput(Player player, String message) {
      if (message.equalsIgnoreCase("cancel")) {
         LangUtils.sendMessage(player, "commands.recruit.cancel.success");
         this.dispose();
         return;
      }

      player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.0F);
      switch (this.inputType) {
         case DESCRIPTION:
            if (message.length() > 128) {
               LangUtils.sendMessage(player, "commands.recruit.setup.too-many-characters", "128");
               return;
            }

            this.setDescription(message);
            this.inputType = RecruitmentBuilder.InputType.PARTY_SIZE;
            LangUtils.sendMessage(player, "commands.recruit.setup.description-set", message);
            LangUtils.sendMessage(player, "commands.recruit.setup.players");
            return;
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
            return;
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

            if (message.length() > 24) {
               LangUtils.sendMessage(player, "commands.recruit.setup.too-many-characters", "24");
               return;
            }

            if (message.contains(" ")) {
               LangUtils.sendMessage(player, "commands.recruit.setup.no-spaces");
               return;
            }

            this.setPassword(message);
            LangUtils.sendMessage(player, "commands.recruit.setup.password-set", message);
            LangUtils.sendMessage(player, "commands.recruit.setup.complete");
            player.playSound(player.getLocation(), "entity.player.levelup", 1.0F, 1.0F);
            Dungeons.inst().getListingManager().putListing(player, this.build());
            this.dispose();
            return;
         case LABEL:
         default:
            if (message.length() > 24) {
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
      }
   }

   private enum InputType {
      LABEL,
      DESCRIPTION,
      PARTY_SIZE,
      PASSWORD
   }
}
