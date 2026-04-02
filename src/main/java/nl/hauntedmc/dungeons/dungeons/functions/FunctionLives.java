package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

@DeclaredFunction
public class FunctionLives extends DungeonFunction {
   @SavedField
   private int amount = 1;
   @SavedField
   private int mode = 0;

   public FunctionLives(Map<String, Object> config) {
      super("Lives", config);
      this.setTargetType(FunctionTargetType.PARTY);
      this.setCategory(FunctionCategory.DUNGEON);
   }

   public FunctionLives() {
      super("Lives");
      this.setTargetType(FunctionTargetType.PARTY);
      this.setCategory(FunctionCategory.DUNGEON);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         if (!instance.isLivesEnabled()) {
            Dungeons.inst()
               .getLogger()
               .info(
                  Dungeons.logPrefix
                     + HelperUtils.colorize("&cUnable to edit player lives: Lives are disable in dungeon " + instance.getDungeon().getWorldName() + "!")
               );
            Dungeons.inst()
               .getLogger()
               .info(Dungeons.logPrefix + HelperUtils.colorize("&cMake sure '&ePlayerLives&c' are set to &61 or more &cin the dungeon's config!"));
         } else {
            for (DungeonPlayer aPlayer : targets) {
               Player player = aPlayer.getPlayer();
               UUID uuid = player.getUniqueId();
               int currentLives = instance.getPlayerLives().get(uuid);

               int newLives = switch (this.mode) {
                  default -> currentLives + this.amount;
                  case 1 -> this.amount;
                  case 2 -> currentLives * this.amount;
               };
               if (newLives > 0) {
                  String plusMinus;
                  if (newLives < currentLives) {
                     plusMinus = "&c" + (newLives - currentLives);
                  } else {
                     plusMinus = "&a+" + (newLives - currentLives);
                  }

                  LangUtils.sendMessage(player, "instance.functions.lives-editor", String.valueOf(newLives), plusMinus);
                  instance.getPlayerLives().put(uuid, newLives);
               }
            }
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.PLAYER_HEAD);
      button.setDisplayName("&6Lives Editor");
      button.addLore("&eChanges the number of lives the");
      button.addLore("&eplayer(s) have.");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&d&lSet Lives");
            this.button.setAmount(FunctionLives.this.amount);
         }

         @Override
         public void onSelect(Player player) {
            switch (FunctionLives.this.mode) {
               case 0:
               default:
                  player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eHow many lives should the player gain?"));
                  break;
               case 1:
                  player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eWhat should we set the player's lives to?"));
                  break;
               case 2:
                  player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eHow much should we multiply the player's lives by?"));
            }
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionLives.this.amount = value.orElse(1);
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet lives to: '&6" + FunctionLives.this.amount + "&a'"));
         }
      });
      this.menu
         .addMenuItem(
            new MenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.COMPARATOR);
                  this.button.setDisplayName("&d&lSet Lives Change Mode");
               }

               @Override
               public void onSelect(PlayerEvent event) {
                  Player player = event.getPlayer();
                  FunctionLives.this.mode++;
                  if (FunctionLives.this.mode >= FunctionLives.MathType.values().length) {
                     FunctionLives.this.mode = 0;
                  }

                  player.sendMessage(
                     Dungeons.logPrefix
                        + HelperUtils.colorize("&aSet lives change mode to '&6" + FunctionLives.MathType.intToMode(FunctionLives.this.mode) + "&a'!")
                  );
               }
            }
         );
   }

   public void setAmount(int amount) {
      this.amount = amount;
   }

   public void setMode(int mode) {
      this.mode = mode;
   }

   public enum MathType {
      ADD,
      SET,
      MULTIPLY;

      public static FunctionLives.MathType intToMode(int index) {
          return switch (index) {
              case 1 -> SET;
              case 2 -> MULTIPLY;
              default -> ADD;
          };
      }
   }
}
