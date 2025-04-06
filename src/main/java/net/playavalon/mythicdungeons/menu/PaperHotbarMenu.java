package net.playavalon.mythicdungeons.menu;

import io.papermc.paper.event.player.AsyncChatDecorateEvent;
import java.util.HashSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public abstract class PaperHotbarMenu extends HotbarMenu {
   @EventHandler
   @Override
   public void onChat(AsyncPlayerChatEvent event) {
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncChatDecorateEvent event) {
      Player player = event.player();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getCurrentHotbar() == this) {
            if (aPlayer.isChatListening()) {
               MenuItem menuItem = this.menuItems.get(this.selected);
               if (menuItem != null) {
                  TextComponent textComp = (TextComponent)event.originalMessage();
                  AsyncPlayerChatEvent legacyEvent = new AsyncPlayerChatEvent(true, player, textComp.content(), new HashSet());
                  menuItem.runChatActions(legacyEvent);
                  event.result(Component.text(""));
                  player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                  event.setCancelled(true);
                  aPlayer.setChatListening(false);
                  this.buildMenu();
               }
            }
         }
      }
   }
}
