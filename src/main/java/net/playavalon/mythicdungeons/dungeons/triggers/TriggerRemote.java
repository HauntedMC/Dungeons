package net.playavalon.mythicdungeons.dungeons.triggers;

import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.RemoteTriggerEvent;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

@DeclaredTrigger
public class TriggerRemote extends DungeonTrigger {
   @SavedField
   private String triggerName = "trigger";
   @SavedField
   private boolean awaitConditions = true;

   public TriggerRemote(Map<String, Object> config) {
      super("Signal Receiver", config);
      this.waitForConditions = true;
      this.setCategory(TriggerCategory.DUNGEON);
      this.setHasTarget(true);
   }

   public TriggerRemote() {
      super("Signal Receiver");
      this.waitForConditions = true;
      this.setCategory(TriggerCategory.DUNGEON);
      this.setHasTarget(true);
   }

   @Override
   public void init() {
      super.init();
      this.setDisplayName("Signal: " + this.triggerName);
      this.waitForConditions = this.awaitConditions;
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.REDSTONE_TORCH);
      functionButton.setDisplayName("&6Signal Receiver");
      functionButton.addLore("&eTriggered when a signal sender");
      functionButton.addLore("&esends a signal with a matching");
      functionButton.addLore("&econfigured name.");
      return functionButton;
   }

   @Override
   public void initLegacyFields(Map<String, Object> config) {
      if (config.containsKey("TriggerName")) {
         this.triggerName = (String)config.get("TriggerName");
      }
   }

   @EventHandler
   public void onTriggerReceive(RemoteTriggerEvent event) {
      if (this.instance == event.getInstance()) {
         if (event.getTriggerName().equals(this.triggerName)) {
            Location origin = event.getOrigin();
            if (origin == null || event.getRange() == 0.0 || !(this.location.distance(origin) > event.getRange())) {
               if (this.matchesRoom(origin)) {
                  this.trigger(event.getMythicPlayer());
               }
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
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PAPER);
            this.button.setDisplayName("&d&lSignal Name");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat signal is this trigger waiting for?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eSignal name is currently: &6" + TriggerRemote.this.triggerName));
         }

         @Override
         public void onInput(Player player, String message) {
            TriggerRemote.this.triggerName = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet Signal name to '&6" + message + "&a'"));
            TriggerRemote.this.setDisplayName("Signal: " + TriggerRemote.this.triggerName);
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAllow Retrigger");
            this.button.setEnchanted(TriggerRemote.this.allowRetrigger);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerRemote.this.allowRetrigger) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerRemote.this.allowRetrigger = !TriggerRemote.this.allowRetrigger;
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&d&lWait For Conditions");
            this.button.setEnchanted(TriggerRemote.this.awaitConditions);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerRemote.this.awaitConditions) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Wait for conditions after triggering&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Require conditions be met at the time of triggering&a'"));
            }

            TriggerRemote.this.awaitConditions = !TriggerRemote.this.awaitConditions;
            TriggerRemote.this.waitForConditions = !TriggerRemote.this.waitForConditions;
         }
      });
      this.addRoomLimitToggleButton();
   }

   public String getTriggerName() {
      return this.triggerName;
   }

   public void setTriggerName(String triggerName) {
      this.triggerName = triggerName;
   }

   public boolean isAwaitConditions() {
      return this.awaitConditions;
   }

   public void setAwaitConditions(boolean awaitConditions) {
      this.awaitConditions = awaitConditions;
   }
}
