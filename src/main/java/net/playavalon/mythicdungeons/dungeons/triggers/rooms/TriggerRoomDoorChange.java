package net.playavalon.mythicdungeons.dungeons.triggers.rooms;

import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.rooms.RoomDoorChangeEvent;
import net.playavalon.mythicdungeons.api.generation.rooms.DoorAction;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerEvent;

@DeclaredTrigger
public class TriggerRoomDoorChange extends DungeonTrigger {
   @SavedField
   private String doorName = "all";
   @SavedField
   private int action = 1;

   public TriggerRoomDoorChange(Map<String, Object> config) {
      super("Room Door", config);
      this.setCategory(TriggerCategory.ROOM);
      this.setHasTarget(false);
   }

   public TriggerRoomDoorChange() {
      super("Room Door");
      this.setCategory(TriggerCategory.ROOM);
      this.setHasTarget(false);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.OAK_DOOR);
      functionButton.setDisplayName("&aRoom Door Open/Close");
      functionButton.addLore("&eTriggered when a door in this");
      functionButton.addLore("&eroom is opened or closed.");
      return functionButton;
   }

   @EventHandler
   public void onDoorChange(RoomDoorChangeEvent event) {
      DoorAction action = DoorAction.values()[this.action];
      if (action.equals(event.getAction())) {
         InstanceProcedural proc = this.instance.as(InstanceProcedural.class);
         if (proc != null) {
            InstanceRoom room = proc.getRoom(this.location);
            if (room != null) {
               if (event.getRoom() == room) {
                  if (this.doorName.equals("any") || event.getDoor().getNamespace().equals(this.doorName)) {
                     this.trigger();
                  }
               }
            }
         }
      }
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAllow Retrigger");
            this.button.setEnchanted(TriggerRoomDoorChange.this.allowRetrigger);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerRoomDoorChange.this.allowRetrigger) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerRoomDoorChange.this.allowRetrigger = !TriggerRoomDoorChange.this.allowRetrigger;
         }
      });
      this.menu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.NAME_TAG);
                  this.button.setDisplayName("&d&lDoor Name");
               }

               @Override
               public void onSelect(Player player) {
                  player.sendMessage(
                     Util.colorize(MythicDungeons.debugPrefix + "&eWhat is the name of the door that will trigger this? (Enter 'any' for any door.)")
                  );
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent door is: &6" + TriggerRoomDoorChange.this.doorName));
               }

               @Override
               public void onInput(Player player, String message) {
                  MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
                  TriggerRoomDoorChange.this.doorName = message;
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet the triggering door to '&6" + message + "&a'"));
               }
            }
         );
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.DIAMOND);
            this.button.setDisplayName("&d&lAction: " + DoorAction.values()[TriggerRoomDoorChange.this.action]);
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
            TriggerRoomDoorChange.this.action++;
            if (TriggerRoomDoorChange.this.action >= DoorAction.values().length) {
               TriggerRoomDoorChange.this.action = 1;
            }

            DoorAction action = DoorAction.values()[TriggerRoomDoorChange.this.action];
            this.menu.updateMenu(mPlayer);
            if (action == DoorAction.OPEN) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSwitched to '&6trigger when this door &bOPENS&a'"));
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSwitched to '&6trigger when this door &cCLOSES&a'"));
            }
         }
      });
   }

   public String getDoorName() {
      return this.doorName;
   }
}
