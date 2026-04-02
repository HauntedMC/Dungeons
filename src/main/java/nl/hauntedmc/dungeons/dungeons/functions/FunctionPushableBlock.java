package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.blocks.PushableBlock;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class FunctionPushableBlock extends DungeonFunction {
   @SavedField
   protected double weight;
   @SavedField
   protected int slideFactor;
   @SavedField
   protected double maxSpeed;
   private PushableBlock block;

   public FunctionPushableBlock(String namespace, Map<String, Object> config) {
      super(namespace, config);
      this.setCategory(FunctionCategory.LOCATION);
      this.targetType = FunctionTargetType.NONE;
      this.setAllowChangingTargetType(false);
   }

   public FunctionPushableBlock(Map<String, Object> config) {
      this("Pushable Block", config);
   }

   public FunctionPushableBlock(String namespace) {
      super(namespace);
      this.setCategory(FunctionCategory.LOCATION);
      this.targetType = FunctionTargetType.NONE;
      this.setAllowChangingTargetType(false);
   }

   public FunctionPushableBlock() {
      this("Pushable Block");
   }

   @Override
   public void onDisable() {
      if (this.block != null) {
         this.block.stop();
      }
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (this.block == null) {
         this.block = new PushableBlock(this.location);
         this.block.setWeight(this.weight);
         this.block.setSlideFactor(this.slideFactor);
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.SMOOTH_STONE);
      functionButton.setDisplayName("&aPushable Block");
      functionButton.addLore("&eTurns the block at this location");
      functionButton.addLore("&einto a block players can push.");
      functionButton.addLore("");
      functionButton.addLore("&7Includes options for slipperiness");
      functionButton.addLore("&7and weight.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.OBSIDIAN);
            this.button.setDisplayName("&d&lWeight");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow heavy is this block when the player tries to push it?"));
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&bPush speed is the pushing player's movement speed - weight."));
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent weight: &6" + FunctionPushableBlock.this.weight));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            FunctionPushableBlock.this.weight = value.orElse(FunctionPushableBlock.this.weight);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet weight to '&6" + FunctionPushableBlock.this.weight + "&a'."));
            }
         }
      });
      this.menu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.ICE);
                  this.button.setDisplayName("&d&lSlipperiness");
                  this.button.setAmount(FunctionPushableBlock.this.slideFactor);
               }

               @Override
               public void onSelect(Player player) {
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow many ticks will it take for this block to stop moving?"));
               }

               @Override
               public void onInput(Player player, String message) {
                  Optional<Integer> value = StringUtils.readIntegerInput(player, message);
                  FunctionPushableBlock.this.slideFactor = value.orElse(FunctionPushableBlock.this.slideFactor);
                  if (value.isPresent()) {
                     player.sendMessage(
                        HelperUtils.colorize(Dungeons.logPrefix + "&aSet slipperiness to '&6" + FunctionPushableBlock.this.slideFactor + "&a' ticks.")
                     );
                  }
               }
            }
         );
   }
}
