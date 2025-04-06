package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.blocks.PushableBlock;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
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
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow heavy is this block when the player tries to push it?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&bPush speed is the pushing player's movement speed - weight."));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent weight: &6" + FunctionPushableBlock.this.weight));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            FunctionPushableBlock.this.weight = value.orElse(FunctionPushableBlock.this.weight);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet weight to '&6" + FunctionPushableBlock.this.weight + "&a'."));
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
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many ticks will it take for this block to stop moving?"));
               }

               @Override
               public void onInput(Player player, String message) {
                  Optional<Integer> value = StringUtils.readIntegerInput(player, message);
                  FunctionPushableBlock.this.slideFactor = value.orElse(FunctionPushableBlock.this.slideFactor);
                  if (value.isPresent()) {
                     player.sendMessage(
                        Util.colorize(MythicDungeons.debugPrefix + "&aSet slipperiness to '&6" + FunctionPushableBlock.this.slideFactor + "&a' ticks.")
                     );
                  }
               }
            }
         );
   }
}
