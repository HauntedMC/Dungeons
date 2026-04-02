package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import me.clip.placeholderapi.PlaceholderAPI;
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
import nl.hauntedmc.dungeons.util.HelperUtils;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         for (DungeonPlayer dPlayer : targets) {
            Player player = dPlayer.getPlayer();
            String message = this.message;
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
               message = PlaceholderAPI.setPlaceholders(player, message);
            }

            message = HelperUtils.parseVars(instance, message);
            switch (this.messageType) {
               case 0:
                  player.sendMessage(HelperUtils.component(message));
                  break;
               case 1:
                  player.sendActionBar(HelperUtils.component(message));
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
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched message type to '&6" + type.toString() + "&a'"));
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
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat should the message say?"));
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent message: &6" + FunctionMessage.this.message));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionMessage.this.message = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet message to '&6" + message + "&a'"));
         }
      });
   }

   public void setMessage(String message) {
      this.message = message;
   }

   public void setMessageType(int messageType) {
      this.messageType = messageType;
   }

   private enum MessageType {
      CHAT,
      ACTION_BAR;

      public static FunctionMessage.MessageType intToType(int index) {
         return switch (index) {
            case 1 -> ACTION_BAR;
            default -> CHAT;
         };
      }
   }
}
