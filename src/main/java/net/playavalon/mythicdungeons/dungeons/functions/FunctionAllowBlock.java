package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerDungeonStart;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredFunction
public class FunctionAllowBlock extends DungeonFunction {
   @SavedField
   private boolean allowPlace = true;
   @SavedField
   private boolean allowBreak = true;
   @SavedField
   private boolean allowBucket = false;
   private boolean active = false;

   public FunctionAllowBlock(Map<String, Object> config) {
      super("Block Control", config);
      this.setRequiresTrigger(false);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.LOCATION);
   }

   public FunctionAllowBlock() {
      super("Block Control");
      this.setRequiresTrigger(false);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.LOCATION);
   }

   @Override
   public void onEnable() {
      if (this.trigger == null) {
         this.trigger = new TriggerDungeonStart();
         this.trigger.setLocation(this.location);
         this.trigger.setInstance(this.instance);
         this.trigger.init();
      }
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      this.active = true;
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.GRASS_BLOCK);
      functionButton.setDisplayName("&dAllow Block Place/Break");
      functionButton.addLore("&eAllows whitelisting or");
      functionButton.addLore("&eblacklisting the placing or");
      functionButton.addLore("&ebreaking of a block at this");
      functionButton.addLore("&elocation.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.GRASS_BLOCK);
            this.button.setDisplayName("&d&lToggle Place");
            this.button.setEnchanted(FunctionAllowBlock.this.allowPlace);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionAllowBlock.this.allowPlace) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Placing Blocks Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Deny Placing Blocks&a'"));
            }

            FunctionAllowBlock.this.allowPlace = !FunctionAllowBlock.this.allowPlace;
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BARRIER);
            this.button.setDisplayName("&d&lToggle Break");
            this.button.setEnchanted(FunctionAllowBlock.this.allowBreak);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionAllowBlock.this.allowBreak) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Breaking Blocks Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Deny Breaking Blocks&a'"));
            }

            FunctionAllowBlock.this.allowBreak = !FunctionAllowBlock.this.allowBreak;
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BUCKET);
            this.button.setDisplayName("&d&lToggle Bucket");
            this.button.setEnchanted(FunctionAllowBlock.this.allowBucket);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionAllowBlock.this.allowBucket) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Using Buckets Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Deny Using Buckets&a'"));
            }

            FunctionAllowBlock.this.allowBucket = !FunctionAllowBlock.this.allowBucket;
         }
      });
   }

   public boolean isAllowPlace() {
      return this.allowPlace;
   }

   public boolean isAllowBreak() {
      return this.allowBreak;
   }

   public boolean isAllowBucket() {
      return this.allowBucket;
   }

   public boolean isActive() {
      return this.active;
   }
}
