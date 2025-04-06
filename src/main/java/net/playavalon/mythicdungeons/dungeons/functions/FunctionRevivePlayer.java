package net.playavalon.mythicdungeons.dungeons.functions;

import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.entity.TeleportFlag.EntityState;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@DeclaredFunction
public class FunctionRevivePlayer extends DungeonFunction {
   @SavedField
   private int maxRevives = 1;
   @SavedField
   private int livesAfterRevival = 1;
   private int currentRevives = 0;

   public FunctionRevivePlayer(Map<String, Object> config) {
      super("Reviver", config);
      this.setTargetType(FunctionTargetType.PLAYER);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.DUNGEON);
      this.setAllowRetriggerByDefault(true);
   }

   public FunctionRevivePlayer() {
      super("Reviver");
      this.setTargetType(FunctionTargetType.PLAYER);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.DUNGEON);
      this.setAllowRetriggerByDefault(true);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      if (!targets.isEmpty()) {
         MythicPlayer aPlayer = targets.get(0);
         Player player = aPlayer.getPlayer();
         if (this.maxRevives != 0 && this.currentRevives >= this.maxRevives) {
            LangUtils.sendMessage(player, "instance.functions.reviver.no-uses");
         } else {
            if (this.maxRevives == 0) {
               LangUtils.sendMessage(player, "instance.functions.reviver.infinite-uses");
            } else {
               LangUtils.sendMessage(player, "instance.functions.reviver.uses-remaining", String.valueOf(this.maxRevives - this.currentRevives));
            }

            aPlayer.setActiveFunction(this);
            MythicDungeons.inst().getAvnAPI().openGUI(player, "revivalmenu");
         }
      }
   }

   public void revivePlayer(MythicPlayer aPlayer, MythicPlayer reviver) {
      if (this.maxRevives != 0 && this.currentRevives >= this.maxRevives) {
         LangUtils.sendMessage(reviver.getPlayer(), "instance.functions.reviver.no-uses");
      } else {
         Player player = aPlayer.getPlayer();
         if (aPlayer.getInstance() != null && aPlayer.getInstance() == this.instance) {
            InstancePlayable instance = this.instance.asPlayInstance();
            if (instance != null) {
               if (instance.getLivingPlayers().contains(aPlayer)) {
                  LangUtils.sendMessage(reviver.getPlayer(), "instance.functions.reviver.not-dead");
               } else {
                  if (MythicDungeons.inst().isSupportsTeleportFlags()) {
                     player.teleport(reviver.getPlayer().getLocation(), new TeleportFlag[]{EntityState.RETAIN_PASSENGERS});
                  } else {
                     List<Entity> passengers = player.getPassengers();
                     player.eject();
                     player.teleport(reviver.getPlayer().getLocation());
                     if (!passengers.isEmpty()) {
                        player.addPassenger(passengers.get(0));
                     }
                  }

                  GameMode gamemode = GameMode.valueOf(instance.getDungeon().getConfig().getString("General.Gamemode", "ADVENTURE").toUpperCase(Locale.ROOT));
                  player.setGameMode(gamemode);
                  instance.getLivingPlayers().add(aPlayer);
                  instance.getPlayerLives().put(player.getUniqueId(), this.livesAfterRevival);
                  this.currentRevives++;

                  for (MythicPlayer target : instance.getPlayers()) {
                     Player targetPlayer = target.getPlayer();
                     LangUtils.sendMessage(reviver.getPlayer(), "instance.functions.reviver.revived", player.getName());
                     targetPlayer.playSound(this.location, "block.beacon.activate", 1.0F, 1.0F);
                     targetPlayer.playSound(this.location, "entity.player.levelup", 0.5F, 1.5F);
                  }
               }
            }
         } else {
            LangUtils.sendMessage(reviver.getPlayer(), "instance.functions.reviver.not-in-dungeon", player.getName());
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.TOTEM_OF_UNDYING);
      button.setDisplayName("&6Player Reviver");
      button.addLore("&eAllows players to revive other");
      button.addLore("&edead players still spectating.");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.TOTEM_OF_UNDYING);
            this.button.setDisplayName("&d&lMax Revives");
            this.button.setAmount(FunctionRevivePlayer.this.maxRevives);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eHow many times can players use this reviver? (0 for infinite)"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRevivePlayer.this.maxRevives = Math.max(value.orElse(1), 0);
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet max revives to '&6" + FunctionRevivePlayer.this.maxRevives + "&a'"));
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PLAYER_HEAD);
            this.button.setDisplayName("&d&lLives After Revival");
            this.button.setAmount(FunctionRevivePlayer.this.livesAfterRevival);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eHow many lives will the player have after being revived?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRevivePlayer.this.livesAfterRevival = Math.max(value.orElse(1), 1);
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet revival lives to '&6" + FunctionRevivePlayer.this.livesAfterRevival + "&a'"));
         }
      });
   }

   public int getMaxRevives() {
      return this.maxRevives;
   }

   public int getLivesAfterRevival() {
      return this.livesAfterRevival;
   }

   public int getCurrentRevives() {
      return this.currentRevives;
   }
}
