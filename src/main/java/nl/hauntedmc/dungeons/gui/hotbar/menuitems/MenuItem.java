package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.gui.hotbar.DungeonPlayerHotbarMenu;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

public abstract class MenuItem {
   public static MenuItem BACK = new MenuItem() {
      @Override
      public void buildButton() {
         this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
         this.button.setDisplayName("&c&lBACK");
      }

      @Override
      public void onSelect(PlayerEvent event) {
         Player player = event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         aPlayer.setActiveTrigger(null);
         aPlayer.previousHotbar();
      }
   };

   protected DungeonPlayerHotbarMenu menu;
   protected MenuButton button;
   protected List<MenuAction<PlayerEvent>> selectActions = new ArrayList<>();
   protected List<MenuAction<PlayerEvent>> clickActions = new ArrayList<>();
   protected List<ChatInputAction> chatActions = new ArrayList<>();
   protected List<MenuAction<PlayerItemHeldEvent>> hoverActions = new ArrayList<>();

   public abstract void buildButton();

   public abstract void onSelect(PlayerEvent var1);

   public void onClick(PlayerEvent event) {
   }

   public void onChat(Player player, String message) {
   }

   public void onHover(PlayerItemHeldEvent event) {
   }

   public void onUnhover(PlayerItemHeldEvent event) {
   }

   public void runSelectActions(PlayerEvent event) {
      this.onSelect(event);

      for (MenuAction<PlayerEvent> action : this.selectActions) {
         action.run(event);
      }
   }

   public void runClickActions(PlayerEvent event) {
      this.onClick(event);

      for (MenuAction<PlayerEvent> action : this.clickActions) {
         action.run(event);
      }
   }

   public void runChatActions(Player player, String message) {
      this.onChat(player, message);

      for (ChatInputAction action : this.chatActions) {
         action.run(player, message);
      }
   }

   public void runHoverActions(PlayerItemHeldEvent event) {
      this.onHover(event);

      for (MenuAction<PlayerItemHeldEvent> action : this.hoverActions) {
         action.run(event);
      }
   }

   public void runUnhoverActions(PlayerItemHeldEvent event) {
      this.onUnhover(event);

      for (MenuAction<PlayerItemHeldEvent> action : this.hoverActions) {
         action.run(event);
      }
   }

   public void setMenu(DungeonPlayerHotbarMenu menu) {
      this.menu = menu;
   }

   public MenuButton getButton() {
      return this.button;
   }

   @FunctionalInterface
   public interface ChatInputAction {
      void run(Player player, String message);
   }
}
