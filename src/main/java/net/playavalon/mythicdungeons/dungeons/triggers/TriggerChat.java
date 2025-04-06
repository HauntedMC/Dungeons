package net.playavalon.mythicdungeons.dungeons.triggers;

import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@DeclaredTrigger
public class TriggerChat extends DungeonTrigger {
   @SavedField
   private String text = "DEFAULT";
   @SavedField
   private boolean caseSensitive = false;
   @SavedField
   private boolean exact = true;

   public TriggerChat(Map<String, Object> config) {
      super("Chat Message", config);
      this.setCategory(TriggerCategory.PLAYER);
      this.setHasTarget(true);
   }

   public TriggerChat() {
      super("Chat Message");
      this.setCategory(TriggerCategory.PLAYER);
      this.setHasTarget(true);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.NAME_TAG);
      functionButton.setDisplayName("&aChat Message");
      functionButton.addLore("&eTriggered when a player sends");
      functionButton.addLore("&ea message with matching or");
      functionButton.addLore("&easimilar text.");
      return functionButton;
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onChat(AsyncPlayerChatEvent event) {
      World world = event.getPlayer().getWorld();
      if (world == this.instance.getInstanceWorld()) {
         Player player = event.getPlayer();
         String message = event.getMessage();
         boolean matched = false;
         if (this.exact) {
            if (this.caseSensitive) {
               if (message.equals(this.text)) {
                  matched = true;
               }
            } else if (message.equalsIgnoreCase(this.text)) {
               matched = true;
            }
         } else if (this.caseSensitive) {
            if (StringUtils.contains(message, this.text)) {
               matched = true;
            }
         } else if (StringUtils.containsIgnoreCase(message, this.text)) {
            matched = true;
         }

         if (matched) {
            if (this.matchesRoom(player.getLocation())) {
               event.setMessage("");
               player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
               event.setCancelled(true);
               this.trigger(MythicDungeons.inst().getMythicPlayer(player));
            }
         }
      }
   }

   private boolean matchesRoom(Location origin) {
      InstanceProcedural inst = this.instance.as(InstanceProcedural.class);
      if (inst == null) {
         return true;
      } else if (!this.limitToRoom) {
         return true;
      } else {
         InstanceRoom originRoom = inst.getRoom(origin);
         InstanceRoom thisRoom = inst.getRoom(this.location);
         return thisRoom == originRoom;
      }
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAllow Retrigger");
            this.button.setEnchanted(TriggerChat.this.allowRetrigger);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerChat.this.allowRetrigger) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerChat.this.allowRetrigger = !TriggerChat.this.allowRetrigger;
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PAPER);
            this.button.setDisplayName("&d&lEdit Required Text");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat should the player type in chat?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent message: &6" + TriggerChat.this.text));
         }

         @Override
         public void onInput(Player player, String message) {
            TriggerChat.this.text = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet required text to '&6" + message + "&a'"));
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NAME_TAG);
            this.button.setDisplayName("&d&lExact Text");
            this.button.setEnchanted(TriggerChat.this.exact);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerChat.this.exact) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet to '&6Message must match required text exactly&a'"));
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet to '&6Message must contain required text&a'"));
            }

            TriggerChat.this.exact = !TriggerChat.this.exact;
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.KNOWLEDGE_BOOK);
            this.button.setDisplayName("&d&lCase Sensitive");
            this.button.setEnchanted(TriggerChat.this.caseSensitive);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerChat.this.caseSensitive) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet to '&6Capitals letters must match&a'"));
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet to '&6Capital letters don't matter&a'"));
            }

            TriggerChat.this.caseSensitive = !TriggerChat.this.caseSensitive;
         }
      });
      this.addRoomLimitToggleButton();
   }
}
