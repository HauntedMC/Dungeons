package net.playavalon.mythicdungeons.dungeons.functions.rooms;

import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.generation.rooms.Connector;
import net.playavalon.mythicdungeons.api.generation.rooms.DoorAction;
import net.playavalon.mythicdungeons.api.generation.rooms.DungeonRoomContainer;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

@DeclaredFunction
public class FunctionRoomDoor extends DungeonFunction {
   @SavedField
   private String doorName = "all";
   @SavedField
   private int action = 0;
   @SavedField
   private boolean keepEntranceOpen = true;

   public FunctionRoomDoor(Map<String, Object> config) {
      super("Room Door Controller", config);
      this.setCategory(FunctionCategory.ROOM);
      this.setAllowChangingTargetType(false);
   }

   public FunctionRoomDoor() {
      super("Room Door Controller");
      this.setCategory(FunctionCategory.ROOM);
      this.setAllowChangingTargetType(false);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      InstanceProcedural proc = this.instance.as(InstanceProcedural.class);
      if (proc != null) {
         InstanceRoom room = proc.getRoom(this.location);
         if (room != null) {
            DoorAction action = DoorAction.values()[this.action];
            switch (action) {
               case TOGGLE:
                  if (this.doorName.equals("all")) {
                     room.toggleValidDoors(this.keepEntranceOpen);
                  } else {
                     room.toggleDoor(this.doorName);
                  }
                  break;
               case OPEN:
                  if (this.doorName.equals("all")) {
                     room.openValidDoors(this.keepEntranceOpen);
                  } else {
                     room.openDoor(this.doorName);
                  }
                  break;
               case CLOSE:
                  if (this.doorName.equals("all")) {
                     room.closeValidDoors(this.keepEntranceOpen);
                  } else {
                     room.closeDoor(this.doorName);
                  }
            }
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.OAK_DOOR);
      functionButton.setDisplayName("&dRoom Door Controller");
      functionButton.addLore("&eOpens or closes a specified room");
      functionButton.addLore("&edoors. Room doors can be created");
      functionButton.addLore("&ewhen editing a connector.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
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
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat is the name of the door you want to open/close?"));
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eEnter 'all' for all doors with rooms on the other side of them."));
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent door is: &6" + FunctionRoomDoor.this.doorName));
               }

               @Override
               public void onInput(Player player, String message) {
                  MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
                  FunctionRoomDoor.this.doorName = message;
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet the target door to '&6" + message + "&a'"));
                  InstanceEditableProcedural inst = mPlayer.getInstance().as(InstanceEditableProcedural.class);
                  if (inst != null) {
                     DungeonRoomContainer room = inst.getDungeon().getRoom(FunctionRoomDoor.this.location);
                     if (room != null && !FunctionRoomDoor.this.doorName.equals("all")) {
                        for (Connector conn : room.getConnectors()) {
                           if (conn.getDoor().getNamespace().equals(FunctionRoomDoor.this.doorName)) {
                              return;
                           }
                        }

                        player.sendMessage(
                           Util.colorize(
                              MythicDungeons.debugPrefix + "&cHeads up! There's no door by that name in this room. Are you sure you entered it correctly?"
                           )
                        );
                     }

                     this.menu.updateMenu(mPlayer);
                  }
               }
            }
         );
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.DIAMOND);
            this.button.setDisplayName("&d&lAction: " + DoorAction.values()[FunctionRoomDoor.this.action]);
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
            FunctionRoomDoor.this.action++;
            if (FunctionRoomDoor.this.action >= DoorAction.values().length) {
               FunctionRoomDoor.this.action = 0;
            }

            DoorAction action = DoorAction.values()[FunctionRoomDoor.this.action];
            this.menu.updateMenu(mPlayer);
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSwitched door action to '&6" + action + "&a'"));
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            if (!FunctionRoomDoor.this.doorName.equals("all")) {
               this.button = null;
            } else {
               this.button = new MenuButton(Material.REDSTONE_TORCH);
               this.button.setDisplayName("&d&lKeep Entrance Open");
               this.button.addLore("&7Whether to keep the door the player");
               this.button.addLore("&7entered from open.");
               this.button.setEnchanted(FunctionRoomDoor.this.keepEntranceOpen);
            }
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionRoomDoor.this.keepEntranceOpen) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Keep entrance open&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&cDON'T &6keep entrance open&a'"));
            }

            FunctionRoomDoor.this.keepEntranceOpen = !FunctionRoomDoor.this.keepEntranceOpen;
         }
      });
   }

   public String getDoorName() {
      return this.doorName;
   }

   public boolean isKeepEntranceOpen() {
      return this.keepEntranceOpen;
   }
}
