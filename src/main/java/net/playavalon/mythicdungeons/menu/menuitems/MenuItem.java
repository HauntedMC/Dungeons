package net.playavalon.mythicdungeons.menu.menuitems;

import java.util.ArrayList;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.menu.HotbarMenu;
import net.playavalon.mythicdungeons.menu.MenuAction;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         aPlayer.setActiveTrigger(null);
         aPlayer.previousHotbar();
      }
   };
   protected HotbarMenu menu;
   protected MenuButton button;
   protected List<MenuAction<PlayerEvent>> selectActions = new ArrayList<>();
   protected List<MenuAction<PlayerEvent>> clickActions = new ArrayList<>();
   protected List<MenuAction<AsyncPlayerChatEvent>> chatActions = new ArrayList<>();
   protected List<MenuAction<PlayerItemHeldEvent>> hoverActions = new ArrayList<>();
   protected List<MenuAction<PlayerItemHeldEvent>> unhoverActions = new ArrayList<>();

   public abstract void buildButton();

   public abstract void onSelect(PlayerEvent var1);

   public void onClick(PlayerEvent event) {
   }

   public void onChat(AsyncPlayerChatEvent event) {
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

   public void runChatActions(AsyncPlayerChatEvent event) {
      this.onChat(event);

      for (MenuAction<AsyncPlayerChatEvent> action : this.chatActions) {
         action.run(event);
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

   public void addSelectAction(MenuAction<PlayerEvent> action) {
      this.selectActions.add(action);
   }

   public void addClickAction(MenuAction<PlayerEvent> action) {
      this.clickActions.add(action);
   }

   public void addChatAction(MenuAction<AsyncPlayerChatEvent> action) {
      this.chatActions.add(action);
   }

   public void addHoverAction(MenuAction<PlayerItemHeldEvent> action) {
      this.hoverActions.add(action);
   }

   public void setMenu(HotbarMenu menu) {
      this.menu = menu;
   }

   public MenuButton getButton() {
      return this.button;
   }
}
