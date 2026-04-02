package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (!targets.isEmpty()) {
         DungeonPlayer aPlayer = targets.getFirst();
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
            Dungeons.inst().getGuiApi().openGUI(player, "revivalmenu");
         }
      }
   }

   public void revivePlayer(DungeonPlayer aPlayer, DungeonPlayer reviver) {
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
                  HelperUtils.forceTeleport(player, reviver.getPlayer().getLocation());

                  GameMode gamemode = GameMode.valueOf(instance.getDungeon().getConfig().getString("General.Gamemode", "ADVENTURE").toUpperCase(Locale.ROOT));
                  player.setGameMode(gamemode);
                  instance.getLivingPlayers().add(aPlayer);
                  instance.getPlayerLives().put(player.getUniqueId(), this.livesAfterRevival);
                  this.currentRevives++;

                  for (DungeonPlayer target : instance.getPlayers()) {
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
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eHow many times can players use this reviver? (0 for infinite)"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRevivePlayer.this.maxRevives = Math.max(value.orElse(1), 0);
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet max revives to '&6" + FunctionRevivePlayer.this.maxRevives + "&a'"));
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
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eHow many lives will the player have after being revived?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRevivePlayer.this.livesAfterRevival = Math.max(value.orElse(1), 1);
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet revival lives to '&6" + FunctionRevivePlayer.this.livesAfterRevival + "&a'"));
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
