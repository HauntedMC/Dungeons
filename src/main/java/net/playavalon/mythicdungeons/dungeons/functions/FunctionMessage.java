package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
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
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

@DeclaredFunction
public class FunctionMessage extends DungeonFunction {
   @SavedField
   private String message = "DEFAULT";
   @SavedField
   private int messageType = 0;

   public FunctionMessage(Map<String, Object> config) {
      super("Message", config);
      this.targetType = FunctionTargetType.PARTY;
      this.setCategory(FunctionCategory.PLAYER);
   }

   public FunctionMessage() {
      super("Message");
      this.targetType = FunctionTargetType.PARTY;
      this.setCategory(FunctionCategory.PLAYER);
   }

   @Override
   public void initLegacyFields(Map<String, Object> config) {
      if (config.containsKey("Message")) {
         this.message = (String)config.get("Message");
      }
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         for (MythicPlayer dPlayer : targets) {
            Player player = dPlayer.getPlayer();
            String message = this.message;
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
               message = PlaceholderAPI.setPlaceholders(player, message);
            }

            message = Util.parseVars(instance, message);
            switch (this.messageType) {
               case 0:
                  player.sendMessage(Util.fullColor(message));
                  break;
               case 1:
                  player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(Util.fullColor(message)));
            }
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.PAPER);
      functionButton.setDisplayName("&aMessage Sender");
      functionButton.addLore("&eSends a chat or action bar");
      functionButton.addLore("&emessage to the target player(s).");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.KNOWLEDGE_BOOK);
            this.button.setDisplayName("&d&lMessage Type");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            FunctionMessage.this.messageType++;
            if (FunctionMessage.this.messageType >= FunctionMessage.MessageType.values().length) {
               FunctionMessage.this.messageType = 0;
            }

            FunctionMessage.MessageType type = FunctionMessage.MessageType.intToType(FunctionMessage.this.messageType);
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched message type to '&6" + type.toString() + "&a'"));
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PAPER);
            this.button.setDisplayName("&d&lEdit Message");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat should the message say?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent message: &6" + FunctionMessage.this.message));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionMessage.this.message = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet message to '&6" + message + "&a'"));
         }
      });
   }

   public void setMessage(String message) {
      this.message = message;
   }

   public void setMessageType(int messageType) {
      this.messageType = messageType;
   }

   private static enum MessageType {
      CHAT,
      ACTION_BAR;

      public static FunctionMessage.MessageType intToType(int index) {
         return switch (index) {
            default -> CHAT;
            case 1 -> ACTION_BAR;
         };
      }
   }
}
