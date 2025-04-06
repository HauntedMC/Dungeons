package net.playavalon.mythicdungeons.gui;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.Hidden;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.api.parents.instances.InstanceEditable;
import net.playavalon.mythicdungeons.avngui.GUI.GUIInventory;
import net.playavalon.mythicdungeons.avngui.GUI.Window;
import net.playavalon.mythicdungeons.avngui.GUI.Buttons.Button;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionGiveItem;
import net.playavalon.mythicdungeons.dungeons.functions.meta.FunctionMulti;
import net.playavalon.mythicdungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerDungeonStart;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerKeyItem;
import net.playavalon.mythicdungeons.dungeons.triggers.gates.TriggerGate;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.ReflectionUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GUIHandler {
   public static void initFunctionMenu() {
      Window gui = new Window("functionmenu", 27, "&8Select a Function");
      Button catButton = new Button("category_dungeon", Material.MOSSY_COBBLESTONE, "&6Dungeon Functions");
      catButton.addLore(Util.colorize("&eFunctions relating to general"));
      catButton.addLore(Util.colorize("&edungeon behaviour."));
      catButton.addLore(Util.colorize(""));
      catButton.addLore(Util.colorize("&8Checkpoint, start, signals, etc."));
      catButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicDungeons.inst().getAvnAPI().openGUI(player, "functions_dungeon");
      });
      gui.addButton(11, catButton);
      catButton = new Button("category_player", Material.PLAYER_HEAD, "&aPlayer Functions");
      catButton.addLore(Util.colorize("&eFunctions involving or effecting"));
      catButton.addLore(Util.colorize("&eplayers and party members."));
      catButton.addLore(Util.colorize(""));
      catButton.addLore(Util.colorize("&8Messages, keys, commands, etc."));
      catButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicDungeons.inst().getAvnAPI().openGUI(player, "functions_player");
      });
      gui.addButton(12, catButton);
      catButton = new Button("category_location", Material.COMPASS, "&dLocation Functions");
      catButton.addLore(Util.colorize("&eFunctions involving or effecting"));
      catButton.addLore(Util.colorize("&etheir location."));
      catButton.addLore(Util.colorize(""));
      catButton.addLore(Util.colorize("&8Mob spawners, sounds, doors, etc."));
      catButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicDungeons.inst().getAvnAPI().openGUI(player, "functions_location");
      });
      gui.addButton(13, catButton);
      catButton = new Button("category_meta", Material.NETHER_STAR, "&bMeta Functions");
      catButton.addLore(Util.colorize("&eFunctions involving or effecting"));
      catButton.addLore(Util.colorize("&eother functions and triggers."));
      catButton.addLore(Util.colorize(""));
      catButton.addLore(Util.colorize("&8Multi-functions, randoms, skills, etc."));
      catButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicDungeons.inst().getAvnAPI().openGUI(player, "functions_meta");
      });
      gui.addButton(14, catButton);
      catButton = new Button("category_room", Material.JIGSAW, "&cRoom Functions");
      catButton.addLore(Util.colorize("&eFunctions involving or effecting"));
      catButton.addLore(Util.colorize("&erooms in procedural dungeons."));
      catButton.addLore(Util.colorize(""));
      catButton.addLore(Util.colorize("&8Connector doors, locking and unlocking, etc."));
      catButton.addLore(Util.colorize("&dOnly available in randomly generated dungeons."));
      catButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
         AbstractInstance inst = mPlayer.getInstance();
         if (inst != null) {
            InstanceEditableProcedural proc = inst.as(InstanceEditableProcedural.class);
            if (proc == null) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cThese functions are only available in randomly generated dungeons!"));
            } else {
               MythicDungeons.inst().getAvnAPI().openGUI(player, "functions_room");
            }
         }
      });
      gui.addButton(15, catButton);
      initFunctionCategories();
   }

   private static void initFunctionCategories() {
      Map<FunctionCategory, Map<String, MenuButton>> categoryButtons = MythicDungeons.inst().getFunctionManager().getButtonsByCategory();
      Button backButton = new Button("back", Material.RED_STAINED_GLASS_PANE, "&c&lBACK");
      backButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicDungeons.inst().getAvnAPI().openGUI(player, "functionmenu");
      });

      for (FunctionCategory category : FunctionCategory.values()) {
         String catName = category.name().toLowerCase();
         Window gui = new Window("functions_" + catName, 27, "&8" + WordUtils.capitalize(catName) + " Functions");
         gui.addButton(0, backButton);
         int i = 1;

         for (Entry<String, MenuButton> pair : categoryButtons.get(category).entrySet()) {
            String functionName = pair.getKey();
            MenuButton menuButton = pair.getValue();
            Button button = new Button("function_" + i, menuButton.getItem());
            button.addAction(
               "click",
               event -> {
                  Player player = (Player)event.getWhoClicked();
                  MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
                  InstanceEditable instance = aPlayer.getInstance().asEditInstance();
                  if (instance != null) {
                     try {
                        if (aPlayer.getActiveFunction() instanceof FunctionMulti function) {
                           DungeonFunction newFunction = MythicDungeons.inst()
                              .getFunctionManager()
                              .getFunction(functionName)
                              .getDeclaredConstructor()
                              .newInstance();
                           newFunction.setLocation(function.getLocation());
                           newFunction.init();
                           function.addFunction(newFunction);
                           newFunction.setParentFunction(function);
                           newFunction.setInstance(instance);
                           player.closeInventory();
                           aPlayer.setTargetLocation(null);
                           if (aPlayer.getSavedHotbar() == null) {
                              aPlayer.switchHotbar(newFunction.getMenu());
                           } else {
                              aPlayer.setHotbar(newFunction.getMenu());
                           }

                           return;
                        }

                        DungeonFunction function = MythicDungeons.inst().getFunctionManager().getFunction(functionName).getDeclaredConstructor().newInstance();
                        aPlayer.setActiveFunction(function);
                        AbstractDungeon dungeon = instance.getDungeon();
                        dungeon.addFunction(aPlayer.getTargetLocation(), function);
                        instance.addFunctionLabel(function);
                        function.setInstance(instance);
                        MythicDungeons.inst().getAvnAPI().openGUI(player, "triggermenu");
                     } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | InstantiationException var8x) {
                        var8x.printStackTrace();
                     }
                  }
               }
            );
            gui.addButton(i, button);
            i++;
         }
      }
   }

   public static void initTriggerMenu() {
      Window gui = new Window("triggermenu", 27, "&8Select a Trigger");
      Button button = new Button("trigger_none", Material.BARRIER, "&cNONE");
      button.addLore(Util.fullColor("&cCreate a function without a"));
      button.addLore(Util.fullColor("&ctrigger."));
      button.addAction("click", clickEvent -> {
         Player player = (Player)clickEvent.getWhoClicked();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         DungeonFunction function = aPlayer.getActiveFunction();
         DungeonFunction parentFunction = function.getParentFunction();
         if (parentFunction != null) {
            function.setTrigger(null);
         } else if (function.isRequiresTrigger()) {
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cThis function requires a trigger!"));
            player.playSound(player.getLocation(), "entity.enderman.teleport", 0.5F, 0.5F);
            return;
         }

         player.closeInventory();
         aPlayer.setTargetLocation(null);
         function.init();
         if (aPlayer.getSavedHotbar() == null) {
            aPlayer.switchHotbar(function.getMenu());
         } else {
            aPlayer.setHotbar(function.getMenu());
         }
      });
      gui.addButton(4, button);
      button = new Button("category_dungeon", Material.MOSSY_COBBLESTONE, "&6Dungeon Triggers");
      button.addLore(Util.colorize("&eTriggers caused by dungeon-related"));
      button.addLore(Util.colorize("&eevents."));
      button.addLore(Util.colorize(""));
      button.addLore(Util.colorize("&8Dungeon start, signals, etc."));
      button.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicDungeons.inst().getAvnAPI().openGUI(player, "triggers_dungeon");
      });
      gui.addButton(11, button);
      button = new Button("category_player", Material.PLAYER_HEAD, "&aPlayer Triggers");
      button.addLore(Util.colorize("&eTriggers caused by a direct player"));
      button.addLore(Util.colorize("&eor party action."));
      button.addLore(Util.colorize(""));
      button.addLore(Util.colorize("&8Right-click, player death, etc."));
      button.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicDungeons.inst().getAvnAPI().openGUI(player, "triggers_player");
      });
      gui.addButton(12, button);
      button = new Button("category_meta", Material.STRUCTURE_BLOCK, "&bMeta Triggers");
      button.addLore(Util.colorize("&eTriggers contain and interact with"));
      button.addLore(Util.colorize("&eother triggers directly."));
      button.addLore(Util.colorize(""));
      button.addLore(Util.colorize("&8AND gates, OR gates, etc."));
      button.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicDungeons.inst().getAvnAPI().openGUI(player, "triggers_meta");
      });
      gui.addButton(13, button);
      button = new Button("category_general", Material.COMPASS, "&5General Triggers");
      button.addLore(Util.colorize("&eTriggers caused by anything outside"));
      button.addLore(Util.colorize("&ethe other categories."));
      button.addLore(Util.colorize(""));
      button.addLore(Util.colorize("&8Mob deaths, redstone signal, etc."));
      button.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicDungeons.inst().getAvnAPI().openGUI(player, "triggers_general");
      });
      gui.addButton(14, button);
      button = new Button("category_room", Material.JIGSAW, "&cRoom Triggers");
      button.addLore(Util.colorize("&eTriggers caused by something changing"));
      button.addLore(Util.colorize("&ein the room."));
      button.addLore(Util.colorize(""));
      button.addLore(Util.colorize("&8Doors opening/closing, etc."));
      button.addLore(Util.colorize("&dOnly available in randomly generated dungeons."));
      button.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
         AbstractInstance inst = mPlayer.getInstance();
         if (inst != null) {
            InstanceEditableProcedural proc = inst.as(InstanceEditableProcedural.class);
            if (proc == null) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cThese triggers are only available in randomly generated dungeons!"));
            } else {
               MythicDungeons.inst().getAvnAPI().openGUI(player, "triggers_room");
            }
         }
      });
      gui.addButton(15, button);
      initTriggerCategories();
      gui.addCloseAction("checktrig", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         DungeonFunction function = aPlayer.getActiveFunction();
         if (function.getTrigger() == null) {
            DungeonFunction parentFunction = function.getParentFunction();
            if (function.isRequiresTrigger() && parentFunction == null) {
               function.setTrigger(new TriggerDungeonStart());
               function.init();
            }
         }
      });
   }

   private static void initTriggerCategories() {
      Map<TriggerCategory, Map<String, MenuButton>> categoryButtons = MythicDungeons.inst().getTriggerManager().getButtonsByCategory();
      Button backButton = new Button("back", Material.RED_STAINED_GLASS_PANE, "&c&lBACK");
      backButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         MythicDungeons.inst().getAvnAPI().openGUI(player, "triggermenu");
      });

      for (TriggerCategory category : TriggerCategory.values()) {
         String catName = category.name().toLowerCase();
         Window gui = new Window("triggers_" + catName, 27, "&8" + WordUtils.capitalize(catName) + " Triggers");
         gui.addButton(0, backButton);
         int i = 1;

         for (Entry<String, MenuButton> pair : categoryButtons.get(category).entrySet()) {
            String triggerName = pair.getKey();
            MenuButton menuButton = pair.getValue();
            Button button = new Button("trigger_" + i, menuButton.getItem());
            button.addAction("click", event -> {
               Player player = (Player)event.getWhoClicked();
               MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
               InstanceEditable instance = aPlayer.getInstance().asEditInstance();
               if (instance != null) {
                  try {
                     DungeonTrigger parentTrigger = aPlayer.getActiveTrigger();
                     DungeonFunction parentFunction = aPlayer.getActiveFunction().getParentFunction();
                     if (parentTrigger instanceof TriggerGate gate) {
                        DungeonTrigger trigger = MythicDungeons.inst().getTriggerManager().getTrigger(triggerName).getDeclaredConstructor().newInstance();
                        trigger.init();
                        gate.addTrigger(trigger);
                        player.closeInventory();
                        aPlayer.setTargetLocation(null);
                        if (aPlayer.getSavedHotbar() == null) {
                           aPlayer.switchHotbar(trigger.getMenu());
                        } else {
                           aPlayer.setHotbar(trigger.getMenu());
                        }

                        instance.updateLabel(gate.getFunction());
                        trigger.setInstance(gate.getInstance());
                        return;
                     }

                     DungeonTrigger trigger = MythicDungeons.inst().getTriggerManager().getTrigger(triggerName).getDeclaredConstructor().newInstance();
                     DungeonFunction function = aPlayer.getActiveFunction();
                     function.setTrigger(trigger);
                     if (!function.isInitialized()) {
                        function.init();
                     }

                     if (!trigger.isInitialized()) {
                        trigger.init();
                     }

                     player.closeInventory();
                     aPlayer.setTargetLocation(null);
                     if (aPlayer.getSavedHotbar() == null) {
                        aPlayer.switchHotbar(function.getMenu());
                     } else {
                        aPlayer.setHotbar(function.getMenu());
                     }

                     instance.updateLabel(function);
                     trigger.setInstance(function.getInstance());
                  } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | InstantiationException var9) {
                     var9.printStackTrace();
                  }
               }
            });
            gui.addButton(i, button);
            i++;
         }
      }
   }

   public static void initItemSelectTriggerMenu() {
      Window gui = new Window("selectitem_trigger", 9, "&8Put your item here");
      gui.setCancelClick(false);

      for (int i = 0; i < 9; i++) {
         if (i != 4) {
            Button button = new Button("blocked_" + i, ItemUtils.getBlockedMenuItem());
            button.addAction("denyclick", event -> event.setCancelled(true));
            gui.addButton(i, button);
         }
      }

      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.getActiveTrigger() instanceof TriggerKeyItem trigger) {
            Inventory inv = event.getInventory();
            inv.setItem(4, trigger.getItem());
         }
      });
      gui.addCloseAction("save", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.getActiveTrigger() instanceof TriggerKeyItem trigger) {
            Inventory inv = event.getInventory();
            ItemStack savedItem = inv.getItem(4);
            trigger.setItem(savedItem);
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSaved item for this trigger!"));
         }
      });
   }

   public static void initItemSelectFunctionMenu() {
      Window gui = new Window("selectitem_function", 9, "&8Put your item here");
      gui.setCancelClick(false);

      for (int i = 0; i < 9; i++) {
         if (i != 4) {
            Button button = new Button("blocked_" + i, ItemUtils.getBlockedMenuItem());
            button.addAction("denyclick", event -> event.setCancelled(true));
            gui.addButton(i, button);
         }
      }

      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.getActiveFunction() instanceof FunctionGiveItem function) {
            Inventory inv = event.getInventory();
            inv.setItem(4, function.getItem());
         }
      });
      gui.addCloseAction("save", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.getActiveFunction() instanceof FunctionGiveItem function) {
            Inventory inv = event.getInventory();
            ItemStack savedItem = inv.getItem(4);
            function.setItem(savedItem);
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSaved item for this function!"));
         }
      });
   }

   public static void initConditionsMenus() {
      initConditionsBrowser();
      initConditionsEditor();
      initConditionsRemover();
   }

   private static void initConditionsBrowser() {
      Window gui = new Window("conditionsmenu", 54, "&8Select a Condition");
      int i = 0;

      for (Entry<String, MenuButton> pair : MythicDungeons.inst().getConditionManager().getConditionButtons().entrySet()) {
         String conditionName = pair.getKey();
         MenuButton menuButton = pair.getValue();
         Button button = new Button("trigger_" + i, menuButton.getItem());
         button.addAction("click", event -> {
            Player player = (Player)event.getWhoClicked();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);

            try {
               DungeonTrigger trigger = aPlayer.getActiveTrigger();
               TriggerCondition condition = MythicDungeons.inst().getConditionManager().getCondition(conditionName).getDeclaredConstructor().newInstance();
               condition.setTrigger(trigger);
               condition.init();
               trigger.addCondition(condition);
               player.closeInventory();
               aPlayer.setTargetLocation(null);
               if (aPlayer.getSavedHotbar() == null) {
                  aPlayer.switchHotbar(condition.getMenu());
               } else {
                  aPlayer.setHotbar(condition.getMenu());
               }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | InstantiationException var6x) {
               var6x.printStackTrace();
            }
         });
         gui.addButton(i, button);
         i++;
      }
   }

   private static void initConditionsEditor() {
      Window gui = new Window("editcondition", 54, "&8Edit a Condition");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         DungeonTrigger trigger = aPlayer.getActiveTrigger();
         GUIInventory guiInv = gui.getPlayersGui(player);

         for (int x = 0; x < 27; x++) {
            guiInv.removeButton(x);
         }

         int slot = 0;

         for (TriggerCondition condition : trigger.getConditions()) {
            MenuButton menuButton = MythicDungeons.inst().getConditionManager().getConditionButtons().get(condition.getClass().getSimpleName());
            if (menuButton != null) {
               Button button = new Button("condition_" + slot, menuButton.getItem());
               button.addLore("");
               button.addLore(Util.colorize("&6Options:"));
               List<Field> fields = new ArrayList<>();
               ReflectionUtils.getAnnotatedFields(fields, condition.getClass(), SavedField.class);

               for (Field field : fields) {
                  if (field.getAnnotation(Hidden.class) == null) {
                     try {
                        field.setAccessible(true);
                        String configVar = field.getName();
                        Object value = field.get(condition);
                        if (value != null && !(value instanceof Collection)) {
                           button.addLore(Util.colorize(" &e" + configVar + ": &7" + value));
                        }
                     } catch (IllegalAccessException var16) {
                        var16.printStackTrace();
                     }
                  }
               }

               button.addAction("click", clickEvent -> {
                  Player clickPlayer = (Player)clickEvent.getWhoClicked();
                  MythicPlayer clickAPlayer = MythicDungeons.inst().getMythicPlayer(clickPlayer);
                  clickPlayer.closeInventory();
                  clickAPlayer.setHotbar(condition.getMenu());
               });
               guiInv.setButton(slot, button);
               slot++;
            }
         }
      });
   }

   private static void initConditionsRemover() {
      Window gui = new Window("removecondition", 54, "&8Remove a Condition");
      gui.addOpenAction("populate", event -> reloadConditionsRemovalGUI(gui, (Player)event.getPlayer()));
   }

   private static void reloadConditionsRemovalGUI(Window gui, Player player) {
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      DungeonTrigger trigger = aPlayer.getActiveTrigger();
      GUIInventory guiInv = gui.getPlayersGui(player);

      for (int x = 0; x < 27; x++) {
         guiInv.removeButton(x);
      }

      int slot = 0;

      for (TriggerCondition condition : trigger.getConditions()) {
         MenuButton menuButton = MythicDungeons.inst().getConditionManager().getConditionButtons().get(condition.getClass().getSimpleName());
         if (menuButton != null) {
            Button button = new Button("condition_" + slot, menuButton.getItem());
            button.addAction("click", clickEvent -> {
               Player clickPlayer = (Player)clickEvent.getWhoClicked();
               trigger.removeCondition(condition);
               clickPlayer.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cRemoved " + menuButton.getDisplayName() + " &ccondition from trigger."));
               reloadConditionsRemovalGUI(gui, clickPlayer);
            });
            guiInv.setButton(slot, button);
            slot++;
         }
      }

      gui.updateButtons(player);
   }

   public static void initGateTriggerMenus() {
      initGateTriggerBrowser();
      initGateTriggerEditor();
      initGateTriggerRemover();
   }

   private static void initGateTriggerBrowser() {
      Window gui = new Window("gatetriggersmenu", 54, "&8Select a Trigger");
      int i = 0;

      for (Entry<String, MenuButton> pair : MythicDungeons.inst().getTriggerManager().getTriggerButtons().entrySet()) {
         String triggerName = pair.getKey();
         MenuButton menuButton = pair.getValue();
         Button button = new Button("trigger_" + i, menuButton.getItem());
         button.addAction("click", event -> {
            Player player = (Player)event.getWhoClicked();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);

            try {
               if (!(aPlayer.getActiveTrigger() instanceof TriggerGate gate)) {
                  return;
               }

               DungeonTrigger trigger = MythicDungeons.inst().getTriggerManager().getTrigger(triggerName).getDeclaredConstructor().newInstance();
               trigger.init();
               gate.addTrigger(trigger);
               player.closeInventory();
               aPlayer.setTargetLocation(null);
               if (aPlayer.getSavedHotbar() == null) {
                  aPlayer.switchHotbar(trigger.getMenu());
               } else {
                  aPlayer.setHotbar(trigger.getMenu());
               }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | InstantiationException var7) {
               var7.printStackTrace();
            }
         });
         gui.addButton(i, button);
         i++;
      }
   }

   private static void initGateTriggerEditor() {
      Window gui = new Window("editgatetrigger", 54, "&8Edit a Trigger");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.getActiveTrigger() instanceof TriggerGate gate) {
            GUIInventory guiInv = gui.getPlayersGui(player);

            for (int x = 0; x < 54; x++) {
               guiInv.removeButton(x);
            }

            int slot = 0;

            for (DungeonTrigger trigger : gate.getTriggers()) {
               MenuButton menuButton = MythicDungeons.inst().getTriggerManager().getTriggerButtons().get(trigger.getClass().getSimpleName());
               if (menuButton != null) {
                  Button button = new Button("trigger_" + slot, menuButton.getItem());
                  button.addLore("");
                  button.addLore(Util.colorize("&6Options:"));
                  List<Field> fields = new ArrayList<>();
                  ReflectionUtils.getAnnotatedFields(fields, trigger.getClass(), SavedField.class);

                  for (Field field : fields) {
                     if (field.getAnnotation(Hidden.class) == null) {
                        try {
                           field.setAccessible(true);
                           String configVar = field.getName();
                           Object value = field.get(trigger);
                           if (value != null && !(value instanceof Collection)) {
                              button.addLore(Util.colorize(" &e" + configVar + ": &7" + value));
                           }
                        } catch (IllegalAccessException var17) {
                           var17.printStackTrace();
                        }
                     }
                  }

                  if (trigger instanceof TriggerGate subGate) {
                     button.addLore(Util.colorize(" &eTriggers:"));

                     for (DungeonTrigger subTrig : subGate.getTriggers()) {
                        button.addLore(Util.colorize(" &e- &7" + subTrig.getDisplayName()));
                     }
                  }

                  button.addAction("click", clickEvent -> {
                     Player clickPlayer = (Player)clickEvent.getWhoClicked();
                     MythicPlayer clickAPlayer = MythicDungeons.inst().getMythicPlayer(clickPlayer);
                     clickPlayer.closeInventory();
                     clickAPlayer.setHotbar(trigger.getMenu());
                  });
                  guiInv.setButton(slot, button);
                  slot++;
               }
            }
         }
      });
   }

   private static void initGateTriggerRemover() {
      Window gui = new Window("removegatetrigger", 54, "&8Remove a Trigger");
      gui.addOpenAction("populate", event -> reloadTriggerRemovalGUI(gui, (Player)event.getPlayer()));
   }

   private static void reloadTriggerRemovalGUI(Window gui, Player player) {
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer.getActiveTrigger() instanceof TriggerGate gate) {
         GUIInventory guiInv = gui.getPlayersGui(player);

         for (int x = 0; x < 54; x++) {
            guiInv.removeButton(x);
         }

         int slot = 0;

         for (DungeonTrigger trigger : gate.getTriggers()) {
            MenuButton menuButton = MythicDungeons.inst().getTriggerManager().getTriggerButtons().get(trigger.getClass().getSimpleName());
            if (menuButton != null) {
               Button button = new Button("trigger_" + slot, menuButton.getItem());
               button.addAction("click", clickEvent -> {
                  Player clickPlayer = (Player)clickEvent.getWhoClicked();
                  gate.removeTrigger(trigger);
                  clickPlayer.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cRemoved " + menuButton.getDisplayName() + " &ctrigger."));
                  reloadTriggerRemovalGUI(gui, clickPlayer);
               });
               guiInv.setButton(slot, button);
               slot++;
            }
         }

         gui.updateButtons(player);
      }
   }

   public static void initMultiFunctionMenus() {
      initMultiFunctionEditor();
      initMultiFunctionRemover();
      initMultiFunctionTriggerEditor();
   }

   private static void initMultiFunctionEditor() {
      Window gui = new Window("editmultifunction", 54, "&8Edit a Function");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.getActiveFunction() instanceof FunctionMulti function) {
            GUIInventory guiInv = gui.getPlayersGui(player);

            for (int x = 0; x < 54; x++) {
               guiInv.removeButton(x);
            }

            int slot = 0;

            for (DungeonFunction targetFunction : function.getFunctions()) {
               MenuButton menuButton = MythicDungeons.inst().getFunctionManager().getFunctionButtons().get(targetFunction.getClass().getSimpleName());
               if (menuButton != null) {
                  Button button = new Button("function_" + slot, menuButton.getItem());
                  button.addLore("");
                  button.addLore(Util.colorize("&6Options:"));
                  List<Field> fields = new ArrayList<>();
                  ReflectionUtils.getAnnotatedFields(fields, targetFunction.getClass(), SavedField.class);

                  for (Field field : fields) {
                     if (field.getAnnotation(Hidden.class) == null) {
                        try {
                           field.setAccessible(true);
                           String configVar = field.getName();
                           Object value = field.get(targetFunction);
                           if (value != null && !(value instanceof Collection)) {
                              if (value instanceof DungeonTrigger trig) {
                                 button.addLore(Util.colorize(" &eTrigger: &7" + trig.getDisplayName()));
                              } else {
                                 button.addLore(Util.colorize(" &e" + configVar + ": &7" + value));
                              }
                           }
                        } catch (IllegalAccessException var18) {
                           var18.printStackTrace();
                        }
                     }
                  }

                  if (targetFunction instanceof FunctionMulti subMulti) {
                     button.addLore(Util.colorize(" &eFunctions:"));

                     for (DungeonFunction subFunction : subMulti.getFunctions()) {
                        button.addLore(Util.colorize("  &e- &7" + subFunction.getDisplayName()));
                     }
                  }

                  button.addAction("click", clickEvent -> {
                     Player clickPlayer = (Player)clickEvent.getWhoClicked();
                     MythicPlayer clickAPlayer = MythicDungeons.inst().getMythicPlayer(clickPlayer);
                     clickPlayer.closeInventory();
                     clickAPlayer.setHotbar(targetFunction.getMenu());
                  });
                  guiInv.setButton(slot, button);
                  slot++;
               }
            }
         }
      });
   }

   private static void initMultiFunctionRemover() {
      Window gui = new Window("removemultifunction", 54, "&8Remove a Function");
      gui.addOpenAction("populate", event -> reloadMultiFunctionRemovalGUI(gui, (Player)event.getPlayer()));
   }

   private static void reloadMultiFunctionRemovalGUI(Window gui, Player player) {
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer.getActiveFunction() instanceof FunctionMulti function) {
         GUIInventory guiInv = gui.getPlayersGui(player);

         for (int x = 0; x < 54; x++) {
            guiInv.removeButton(x);
         }

         int slot = 0;

         for (DungeonFunction targetFunction : function.getFunctions()) {
            MenuButton menuButton = MythicDungeons.inst().getFunctionManager().getFunctionButtons().get(targetFunction.getClass().getSimpleName());
            if (menuButton != null) {
               Button button = new Button("function_" + slot, menuButton.getItem());
               button.addAction("click", clickEvent -> {
                  Player clickPlayer = (Player)clickEvent.getWhoClicked();
                  function.removeFunction(targetFunction);
                  clickPlayer.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cRemoved " + menuButton.getDisplayName() + " &cfunction."));
                  reloadMultiFunctionRemovalGUI(gui, clickPlayer);
               });
               guiInv.setButton(slot, button);
               slot++;
            }
         }

         gui.updateButtons(player);
      }
   }

   private static void initMultiFunctionTriggerEditor() {
      Window gui = new Window("editmultifunctiontriggers", 54, "&8Edit Function Triggers");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.getActiveFunction() instanceof FunctionMulti function) {
            GUIInventory guiInv = gui.getPlayersGui(player);

            for (int x = 0; x < 54; x++) {
               guiInv.removeButton(x);
            }

            int slot = 0;

            for (DungeonFunction targetFunction : function.getFunctions()) {
               MenuButton menuButton = MythicDungeons.inst().getFunctionManager().getFunctionButtons().get(targetFunction.getClass().getSimpleName());
               if (menuButton != null) {
                  Button button = new Button("function_" + slot, menuButton.getItem());
                  button.addLore("");
                  button.addLore(Util.colorize("&6Options:"));
                  List<Field> fields = new ArrayList<>();
                  ReflectionUtils.getAnnotatedFields(fields, targetFunction.getClass(), SavedField.class);

                  for (Field field : fields) {
                     if (field.getAnnotation(Hidden.class) == null) {
                        try {
                           field.setAccessible(true);
                           String configVar = field.getName();
                           Object value = field.get(targetFunction);
                           if (value != null && !(value instanceof Collection)) {
                              if (value instanceof DungeonTrigger trig) {
                                 button.addLore(Util.colorize(" &d&lTrigger: &7&l" + trig.getDisplayName()));
                              } else {
                                 button.addLore(Util.colorize(" &e" + configVar + ": &7" + value));
                              }
                           }
                        } catch (IllegalAccessException var18) {
                           var18.printStackTrace();
                        }
                     }
                  }

                  if (targetFunction instanceof FunctionMulti subMulti) {
                     button.addLore(Util.colorize(" &eFunctions:"));

                     for (DungeonFunction subFunction : subMulti.getFunctions()) {
                        button.addLore(Util.colorize("  &e- &7" + subFunction.getDisplayName()));
                     }
                  }

                  button.addAction("click", clickEvent -> {
                     Player clickPlayer = (Player)clickEvent.getWhoClicked();
                     MythicPlayer clickAPlayer = MythicDungeons.inst().getMythicPlayer(clickPlayer);
                     clickAPlayer.setActiveFunction(targetFunction);
                     MythicDungeons.inst().getAvnAPI().openGUI(clickPlayer, "triggermenu");
                  });
                  guiInv.setButton(slot, button);
                  slot++;
               }
            }
         }
      });
   }
}
