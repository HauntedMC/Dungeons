package nl.hauntedmc.dungeons.dungeons.functions.rooms;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.generation.rooms.Connector;
import nl.hauntedmc.dungeons.api.generation.rooms.DoorAction;
import nl.hauntedmc.dungeons.api.generation.rooms.DungeonRoomContainer;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceProcedural;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
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
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat is the name of the door you want to open/close?"));
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eEnter 'all' for all doors with rooms on the other side of them."));
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent door is: &6" + FunctionRoomDoor.this.doorName));
               }

               @Override
               public void onInput(Player player, String message) {
                  DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
                  FunctionRoomDoor.this.doorName = message;
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet the target door to '&6" + message + "&a'"));
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
                           HelperUtils.colorize(
                              Dungeons.logPrefix + "&cHeads up! There's no door by that name in this room. Are you sure you entered it correctly?"
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
            DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
            FunctionRoomDoor.this.action++;
            if (FunctionRoomDoor.this.action >= DoorAction.values().length) {
               FunctionRoomDoor.this.action = 0;
            }

            DoorAction action = DoorAction.values()[FunctionRoomDoor.this.action];
            this.menu.updateMenu(mPlayer);
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSwitched door action to '&6" + action + "&a'"));
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
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Keep entrance open&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&cDON'T &6keep entrance open&a'"));
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
