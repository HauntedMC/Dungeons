package net.playavalon.mythicdungeons.dungeons.triggers.gates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class TriggerGate extends DungeonTrigger {
   @SavedField
   protected List<DungeonTrigger> triggers = new ArrayList<>();
   protected Map<DungeonTrigger, Boolean> triggerTracker = new HashMap<>();

   public TriggerGate(String displayName, Map<String, Object> config) {
      super(displayName, config);
      this.setCategory(TriggerCategory.META);
   }

   public TriggerGate(String id) {
      super(id);
      this.setCategory(TriggerCategory.META);
   }

   @Override
   public void init() {
      super.init();

      for (DungeonTrigger trigger : this.triggers) {
         trigger.init();
      }
   }

   @Override
   public void onEnable() {
      for (DungeonTrigger trigger : this.triggers) {
         trigger.setInstance(this.instance);
         trigger.setLocation(this.location);
         trigger.enable(null, this.instance);
         this.triggerTracker.put(trigger, false);
      }
   }

   @Override
   public void onDisable() {
      for (DungeonTrigger trigger : this.triggers) {
         trigger.disable();
      }
   }

   public void addTrigger(DungeonTrigger trigger) {
      this.triggers.add(trigger);
   }

   public void removeTrigger(DungeonTrigger trigger) {
      this.triggers.remove(trigger);
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.COMMAND_BLOCK);
            this.button.setDisplayName("&a&lAdd Trigger");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            if (TriggerGate.this.triggers.size() >= 54) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cYou can't add any more triggers to this gate!"));
            } else {
               MythicDungeons.inst().getAvnAPI().openGUI(player, "triggermenu");
            }
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
            this.button.setDisplayName("&e&lEdit Trigger");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicDungeons.inst().getAvnAPI().openGUI(player, "editgatetrigger");
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BARRIER);
            this.button.setDisplayName("&c&lRemove Trigger");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicDungeons.inst().getAvnAPI().openGUI(player, "removegatetrigger");
         }
      });
   }

   public TriggerGate clone() {
      TriggerGate clone = (TriggerGate)super.clone();
      List<DungeonTrigger> newTriggers = new ArrayList<>();

      for (DungeonTrigger oldTrigger : this.triggers) {
         DungeonTrigger clonedTrigger = oldTrigger.clone();
         clonedTrigger.setLocation(clone.location);
         newTriggers.add(clonedTrigger);
      }

      clone.triggers = newTriggers;
      clone.triggerTracker = new HashMap<>();
      return clone;
   }

   public List<DungeonTrigger> getTriggers() {
      return this.triggers;
   }

   public Map<DungeonTrigger, Boolean> getTriggerTracker() {
      return this.triggerTracker;
   }
}
