package nl.hauntedmc.dungeons.dungeons.triggers.rooms;

import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredTrigger;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.rooms.RoomDoorChangeEvent;
import nl.hauntedmc.dungeons.api.generation.rooms.DoorAction;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.api.parents.TriggerCategory;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceProcedural;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
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
                     HelperUtils.colorize(Dungeons.logPrefix + "&eWhat is the name of the door that will trigger this? (Enter 'any' for any door.)")
                  );
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent door is: &6" + TriggerRoomDoorChange.this.doorName));
               }

               @Override
               public void onInput(Player player, String message) {
                  TriggerRoomDoorChange.this.doorName = message;
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet the triggering door to '&6" + message + "&a'"));
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
            DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
            TriggerRoomDoorChange.this.action++;
            if (TriggerRoomDoorChange.this.action >= DoorAction.values().length) {
               TriggerRoomDoorChange.this.action = 1;
            }

            DoorAction action = DoorAction.values()[TriggerRoomDoorChange.this.action];
            this.menu.updateMenu(mPlayer);
            if (action == DoorAction.OPEN) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSwitched to '&6trigger when this door &bOPENS&a'"));
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSwitched to '&6trigger when this door &cCLOSES&a'"));
            }
         }
      });
   }

   public String getDoorName() {
      return this.doorName;
   }
}
