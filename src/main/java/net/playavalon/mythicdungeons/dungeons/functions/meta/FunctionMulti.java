package net.playavalon.mythicdungeons.dungeons.functions.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

@DeclaredFunction
public class FunctionMulti extends DungeonFunction {
   @SavedField
   protected List<DungeonFunction> functions;

   public FunctionMulti(String namespace, Map<String, Object> config) {
      super(namespace, config);
      this.setTargetType(FunctionTargetType.PARTY);
      this.functions = new ArrayList<>();
      this.setCategory(FunctionCategory.META);
   }

   public FunctionMulti(Map<String, Object> config) {
      super("Multi-Function", config);
      this.setTargetType(FunctionTargetType.PARTY);
      this.functions = new ArrayList<>();
      this.setCategory(FunctionCategory.META);
   }

   public FunctionMulti(String namespace) {
      super(namespace);
      this.setTargetType(FunctionTargetType.PARTY);
      this.functions = new ArrayList<>();
      this.setCategory(FunctionCategory.META);
   }

   public FunctionMulti() {
      super("Multi-Function");
      this.setTargetType(FunctionTargetType.PARTY);
      this.functions = new ArrayList<>();
      this.setCategory(FunctionCategory.META);
   }

   @Override
   public void init() {
      super.init();

      for (DungeonFunction function : this.functions) {
         function.setLocation(this.location);
         function.init();
         function.setParentFunction(this);
      }
   }

   @Override
   public void onEnable() {
      for (DungeonFunction function : this.functions) {
         function.setInstance(this.instance);
         function.setLocation(this.location);
         function.enable(this.instance, this.location);
      }
   }

   @Override
   public void onDisable() {
      for (DungeonFunction functions : this.functions) {
         functions.disable();
      }
   }

   public void addFunction(DungeonFunction function) {
      this.functions.add(function);
   }

   public void removeFunction(DungeonFunction function) {
      this.functions.remove(function);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      for (DungeonFunction function : this.functions) {
         List<MythicPlayer> functionTargets = new ArrayList<>();
         switch (this.targetType) {
            case PLAYER:
               if (triggerEvent.getDPlayer() != null) {
                  functionTargets.add(triggerEvent.getDPlayer());
               }
               break;
            case PARTY:
               functionTargets.addAll(this.instance.getPlayers());
         }

         if (function.getTrigger() == null) {
            function.runFunction(triggerEvent, functionTargets);
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.JIGSAW);
      button.setDisplayName("&bMulti-Function");
      button.addLore("&eRuns a set of multiple functions");
      button.addLore("&ewhen triggered.");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.COMMAND_BLOCK);
            this.button.setDisplayName("&a&lAdd Function");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            if (FunctionMulti.this.functions.size() >= 54) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cYou can't add any more functions!"));
            } else {
               MythicDungeons.inst().getAvnAPI().openGUI(player, "functionmenu");
            }
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
            this.button.setDisplayName("&e&lEdit Function");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicDungeons.inst().getAvnAPI().openGUI(player, "editmultifunction");
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BARRIER);
            this.button.setDisplayName("&c&lRemove Function");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicDungeons.inst().getAvnAPI().openGUI(player, "removemultifunction");
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PISTON);
            this.button.setDisplayName("&e&lChange Triggers");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicDungeons.inst().getAvnAPI().openGUI(player, "editmultifunctiontriggers");
         }
      });
   }

   @Override
   public void setLocation(Location loc) {
      super.setLocation(loc);

      for (DungeonFunction function : this.functions) {
         function.setLocation(loc);
      }
   }

   @Override
   public void setInstance(AbstractInstance instance) {
      super.setInstance(instance);

      for (DungeonFunction function : this.functions) {
         function.setInstance(instance);
      }
   }

   public FunctionMulti clone() {
      FunctionMulti clone = (FunctionMulti)super.clone();
      List<DungeonFunction> newFunctions = new ArrayList<>();

      for (DungeonFunction oldFunction : this.functions) {
         DungeonFunction clonedFunction = oldFunction.clone();
         clonedFunction.setLocation(clone.location);
         newFunctions.add(clonedFunction);
      }

      clone.functions = newFunctions;
      return clone;
   }

   public List<DungeonFunction> getFunctions() {
      return this.functions;
   }
}
