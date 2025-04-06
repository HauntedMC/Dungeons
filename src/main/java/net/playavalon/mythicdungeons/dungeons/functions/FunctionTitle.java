package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.clip.placeholderapi.PlaceholderAPI;
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
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredFunction
public class FunctionTitle extends DungeonFunction {
   @SavedField
   private String title = " ";
   @SavedField
   private String subtitle = " ";
   @SavedField
   private int fadeIn = 10;
   @SavedField
   private int stay = 80;
   @SavedField
   private int fadeOut = 10;

   public FunctionTitle(Map<String, Object> config) {
      super("Title", config);
      this.targetType = FunctionTargetType.PARTY;
      this.setCategory(FunctionCategory.PLAYER);
   }

   public FunctionTitle() {
      super("Title");
      this.targetType = FunctionTargetType.PARTY;
      this.setCategory(FunctionCategory.PLAYER);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         for (MythicPlayer aPlayer : targets) {
            Player player = aPlayer.getPlayer();
            String title = this.title;
            String subtitle = this.subtitle;
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
               title = PlaceholderAPI.setPlaceholders(player, title);
               subtitle = PlaceholderAPI.setPlaceholders(player, subtitle);
            }

            title = Util.parseVars(instance, title);
            subtitle = Util.parseVars(instance, subtitle);
            player.sendTitle(Util.fullColor(title), Util.fullColor(subtitle), this.fadeIn, this.stay, this.fadeOut);
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.FILLED_MAP);
      button.setDisplayName("&aTitle Sender");
      button.addLore("&eSends a title and subtitle");
      button.addLore("&eto the target player(s).");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.KNOWLEDGE_BOOK);
            this.button.setDisplayName("&d&lEdit Title");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat should the title say?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent title: &6" + FunctionTitle.this.title));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionTitle.this.title = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet title to '&6" + message + "&a'"));
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BOOK);
            this.button.setDisplayName("&d&lEdit Subtitle");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat should the subtitle say?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent subtitle: &6" + FunctionTitle.this.subtitle));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionTitle.this.subtitle = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet subtitle to '&6" + message + "&a'"));
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.LIGHT_BLUE_BANNER);
            this.button.setDisplayName("&d&lDuration");
            this.button.setAmount(FunctionTitle.this.stay);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many ticks should the title remain for?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionTitle.this.stay = value.orElse(FunctionTitle.this.stay);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet title duration to '&6" + FunctionTitle.this.stay + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.LIME_BANNER);
            this.button.setDisplayName("&d&lFade-In Ticks");
            this.button.setAmount(FunctionTitle.this.fadeIn);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many ticks should it take to fade in?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionTitle.this.fadeIn = value.orElse(FunctionTitle.this.fadeIn);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet fade-in time to '&6" + FunctionTitle.this.fadeIn + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.RED_BANNER);
            this.button.setDisplayName("&d&lFade-Out Ticks");
            this.button.setAmount(FunctionTitle.this.fadeOut);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many ticks should it take to fade out?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionTitle.this.fadeOut = value.orElse(FunctionTitle.this.fadeOut);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet fade-out time to '&6" + FunctionTitle.this.fadeOut + "&a'"));
            }
         }
      });
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public void setSubtitle(String subtitle) {
      this.subtitle = subtitle;
   }
}
