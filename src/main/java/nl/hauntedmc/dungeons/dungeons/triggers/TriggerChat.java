package nl.hauntedmc.dungeons.dungeons.triggers;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredTrigger;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.api.parents.TriggerCategory;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceProcedural;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

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
   public void onChat(AsyncChatEvent event) {
      World world = event.getPlayer().getWorld();
      if (world == this.instance.getInstanceWorld()) {
         Player player = event.getPlayer();
         String message = HelperUtils.plainText(event.originalMessage());
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
            if (message.contains(this.text)) {
               matched = true;
            }
         } else if (message.toLowerCase(java.util.Locale.ROOT).contains(this.text.toLowerCase(java.util.Locale.ROOT))) {
            matched = true;
         }

         if (matched) {
            if (this.matchesRoom(player.getLocation())) {
               event.setCancelled(true);
               Bukkit.getScheduler().runTask(Dungeons.inst(), () -> {
                  player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                  this.trigger(Dungeons.inst().getDungeonPlayer(player));
               });
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
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
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
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat should the player type in chat?"));
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent message: &6" + TriggerChat.this.text));
         }

         @Override
         public void onInput(Player player, String message) {
            TriggerChat.this.text = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet required text to '&6" + message + "&a'"));
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
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet to '&6Message must match required text exactly&a'"));
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet to '&6Message must contain required text&a'"));
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
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet to '&6Capitals letters must match&a'"));
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet to '&6Capital letters don't matter&a'"));
            }

            TriggerChat.this.caseSensitive = !TriggerChat.this.caseSensitive;
         }
      });
      this.addRoomLimitToggleButton();
   }
}
