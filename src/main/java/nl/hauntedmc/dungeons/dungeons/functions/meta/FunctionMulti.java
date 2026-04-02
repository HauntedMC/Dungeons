package nl.hauntedmc.dungeons.dungeons.functions.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      for (DungeonFunction function : this.functions) {
         List<DungeonPlayer> functionTargets = new ArrayList<>();
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
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cYou can't add any more functions!"));
            } else {
               Dungeons.inst().getGuiApi().openGUI(player, "functionmenu");
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
            Dungeons.inst().getGuiApi().openGUI(player, "editmultifunction");
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
            Dungeons.inst().getGuiApi().openGUI(player, "removemultifunction");
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
            Dungeons.inst().getGuiApi().openGUI(player, "editmultifunctiontriggers");
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
