package nl.hauntedmc.dungeons.gui.inv;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.Hidden;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.parents.TriggerCategory;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.api.parents.elements.TriggerCondition;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.api.gui.window.GUIInventory;
import nl.hauntedmc.dungeons.api.gui.window.GUIWindow;
import nl.hauntedmc.dungeons.api.gui.buttons.Button;
import nl.hauntedmc.dungeons.dungeons.functions.FunctionGiveItem;
import nl.hauntedmc.dungeons.dungeons.functions.meta.FunctionMulti;
import nl.hauntedmc.dungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerDungeonStart;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerKeyItem;
import nl.hauntedmc.dungeons.dungeons.triggers.gates.TriggerGate;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.version.ReflectionUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GUIHandler {
   public static void initFunctionMenu() {
      GUIWindow gui = new GUIWindow("functionmenu", 27, "&8Select a Function");
      Button catButton = new Button("category_dungeon", Material.MOSSY_COBBLESTONE, "&6Dungeon Functions");
      catButton.addLore(HelperUtils.colorize("&eFunctions relating to general"));
      catButton.addLore(HelperUtils.colorize("&edungeon behaviour."));
      catButton.addLore(HelperUtils.colorize(""));
      catButton.addLore(HelperUtils.colorize("&8Checkpoint, start, signals, etc."));
      catButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getAvnAPI().openGUI(player, "functions_dungeon");
      });
      gui.addButton(11, catButton);
      catButton = new Button("category_player", Material.PLAYER_HEAD, "&aPlayer Functions");
      catButton.addLore(HelperUtils.colorize("&eFunctions involving or effecting"));
      catButton.addLore(HelperUtils.colorize("&eplayers and party members."));
      catButton.addLore(HelperUtils.colorize(""));
      catButton.addLore(HelperUtils.colorize("&8Messages, keys, commands, etc."));
      catButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getAvnAPI().openGUI(player, "functions_player");
      });
      gui.addButton(12, catButton);
      catButton = new Button("category_location", Material.COMPASS, "&dLocation Functions");
      catButton.addLore(HelperUtils.colorize("&eFunctions involving or effecting"));
      catButton.addLore(HelperUtils.colorize("&etheir location."));
      catButton.addLore(HelperUtils.colorize(""));
      catButton.addLore(HelperUtils.colorize("&8Mob spawners, sounds, doors, etc."));
      catButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getAvnAPI().openGUI(player, "functions_location");
      });
      gui.addButton(13, catButton);
      catButton = new Button("category_meta", Material.NETHER_STAR, "&bMeta Functions");
      catButton.addLore(HelperUtils.colorize("&eFunctions involving or effecting"));
      catButton.addLore(HelperUtils.colorize("&eother functions and triggers."));
      catButton.addLore(HelperUtils.colorize(""));
      catButton.addLore(HelperUtils.colorize("&8Multi-functions, randoms, skills, etc."));
      catButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getAvnAPI().openGUI(player, "functions_meta");
      });
      gui.addButton(14, catButton);
      catButton = new Button("category_room", Material.JIGSAW, "&cRoom Functions");
      catButton.addLore(HelperUtils.colorize("&eFunctions involving or effecting"));
      catButton.addLore(HelperUtils.colorize("&erooms in procedural dungeons."));
      catButton.addLore(HelperUtils.colorize(""));
      catButton.addLore(HelperUtils.colorize("&8Connector doors, locking and unlocking, etc."));
      catButton.addLore(HelperUtils.colorize("&dOnly available in randomly generated dungeons."));
      catButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         AbstractInstance inst = mPlayer.getInstance();
         if (inst != null) {
            InstanceEditableProcedural proc = inst.as(InstanceEditableProcedural.class);
            if (proc == null) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cThese functions are only available in randomly generated dungeons!"));
            } else {
               Dungeons.inst().getAvnAPI().openGUI(player, "functions_room");
            }
         }
      });
      gui.addButton(15, catButton);
      initFunctionCategories();
   }

   private static void initFunctionCategories() {
      Map<FunctionCategory, Map<String, MenuButton>> categoryButtons = Dungeons.inst().getFunctionManager().getButtonsByCategory();
      Button backButton = new Button("back", Material.RED_STAINED_GLASS_PANE, "&c&lBACK");
      backButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getAvnAPI().openGUI(player, "functionmenu");
      });

      for (FunctionCategory category : FunctionCategory.values()) {
         String catName = category.name().toLowerCase();
         GUIWindow gui = new GUIWindow("functions_" + catName, 27, "&8" + HelperUtils.humanize(catName) + " Functions");
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
                  DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
                  InstanceEditable instance = aPlayer.getInstance().asEditInstance();
                  if (instance != null) {
                     try {
                        if (aPlayer.getActiveFunction() instanceof FunctionMulti function) {
                           DungeonFunction newFunction = Dungeons.inst()
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

                        DungeonFunction function = Dungeons.inst().getFunctionManager().getFunction(functionName).getDeclaredConstructor().newInstance();
                        aPlayer.setActiveFunction(function);
                        AbstractDungeon dungeon = instance.getDungeon();
                        dungeon.addFunction(aPlayer.getTargetLocation(), function);
                        instance.addFunctionLabel(function);
                        function.setInstance(instance);
                        Dungeons.inst().getAvnAPI().openGUI(player, "triggermenu");
                     } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | InstantiationException var8x) {
                        Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var8x.getMessage());

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
      GUIWindow gui = new GUIWindow("triggermenu", 27, "&8Select a Trigger");
      Button button = new Button("trigger_none", Material.BARRIER, "&cNONE");
      button.addLore(HelperUtils.fullColor("&cCreate a function without a"));
      button.addLore(HelperUtils.fullColor("&ctrigger."));
      button.addAction("click", clickEvent -> {
         Player player = (Player)clickEvent.getWhoClicked();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         DungeonFunction function = aPlayer.getActiveFunction();
         DungeonFunction parentFunction = function.getParentFunction();
         if (parentFunction != null) {
            function.setTrigger(null);
         } else if (function.isRequiresTrigger()) {
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cThis function requires a trigger!"));
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
      button.addLore(HelperUtils.colorize("&eTriggers caused by dungeon-related"));
      button.addLore(HelperUtils.colorize("&eevents."));
      button.addLore(HelperUtils.colorize(""));
      button.addLore(HelperUtils.colorize("&8Dungeon start, signals, etc."));
      button.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getAvnAPI().openGUI(player, "triggers_dungeon");
      });
      gui.addButton(11, button);
      button = new Button("category_player", Material.PLAYER_HEAD, "&aPlayer Triggers");
      button.addLore(HelperUtils.colorize("&eTriggers caused by a direct player"));
      button.addLore(HelperUtils.colorize("&eor party action."));
      button.addLore(HelperUtils.colorize(""));
      button.addLore(HelperUtils.colorize("&8Right-click, player death, etc."));
      button.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getAvnAPI().openGUI(player, "triggers_player");
      });
      gui.addButton(12, button);
      button = new Button("category_meta", Material.STRUCTURE_BLOCK, "&bMeta Triggers");
      button.addLore(HelperUtils.colorize("&eTriggers contain and interact with"));
      button.addLore(HelperUtils.colorize("&eother triggers directly."));
      button.addLore(HelperUtils.colorize(""));
      button.addLore(HelperUtils.colorize("&8AND gates, OR gates, etc."));
      button.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getAvnAPI().openGUI(player, "triggers_meta");
      });
      gui.addButton(13, button);
      button = new Button("category_general", Material.COMPASS, "&5General Triggers");
      button.addLore(HelperUtils.colorize("&eTriggers caused by anything outside"));
      button.addLore(HelperUtils.colorize("&ethe other categories."));
      button.addLore(HelperUtils.colorize(""));
      button.addLore(HelperUtils.colorize("&8Mob deaths, redstone signal, etc."));
      button.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getAvnAPI().openGUI(player, "triggers_general");
      });
      gui.addButton(14, button);
      button = new Button("category_room", Material.JIGSAW, "&cRoom Triggers");
      button.addLore(HelperUtils.colorize("&eTriggers caused by something changing"));
      button.addLore(HelperUtils.colorize("&ein the room."));
      button.addLore(HelperUtils.colorize(""));
      button.addLore(HelperUtils.colorize("&8Doors opening/closing, etc."));
      button.addLore(HelperUtils.colorize("&dOnly available in randomly generated dungeons."));
      button.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         AbstractInstance inst = mPlayer.getInstance();
         if (inst != null) {
            InstanceEditableProcedural proc = inst.as(InstanceEditableProcedural.class);
            if (proc == null) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cThese triggers are only available in randomly generated dungeons!"));
            } else {
               Dungeons.inst().getAvnAPI().openGUI(player, "triggers_room");
            }
         }
      });
      gui.addButton(15, button);
      initTriggerCategories();
      gui.addCloseAction("checktrig", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
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
      Map<TriggerCategory, Map<String, MenuButton>> categoryButtons = Dungeons.inst().getTriggerManager().getButtonsByCategory();
      Button backButton = new Button("back", Material.RED_STAINED_GLASS_PANE, "&c&lBACK");
      backButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getAvnAPI().openGUI(player, "triggermenu");
      });

      for (TriggerCategory category : TriggerCategory.values()) {
         String catName = category.name().toLowerCase();
         GUIWindow gui = new GUIWindow("triggers_" + catName, 27, "&8" + HelperUtils.humanize(catName) + " Triggers");
         gui.addButton(0, backButton);
         int i = 1;

         for (Entry<String, MenuButton> pair : categoryButtons.get(category).entrySet()) {
            String triggerName = pair.getKey();
            MenuButton menuButton = pair.getValue();
            Button button = new Button("trigger_" + i, menuButton.getItem());
            button.addAction("click", event -> {
               Player player = (Player)event.getWhoClicked();
               DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
               InstanceEditable instance = aPlayer.getInstance().asEditInstance();
               if (instance != null) {
                  try {
                     DungeonTrigger parentTrigger = aPlayer.getActiveTrigger();
                      if (parentTrigger instanceof TriggerGate gate) {
                        DungeonTrigger trigger = Dungeons.inst().getTriggerManager().getTrigger(triggerName).getDeclaredConstructor().newInstance();
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

                     DungeonTrigger trigger = Dungeons.inst().getTriggerManager().getTrigger(triggerName).getDeclaredConstructor().newInstance();
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
                     Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var9.getMessage());
                  }
               }
            });
            gui.addButton(i, button);
            i++;
         }
      }
   }

   public static void initItemSelectTriggerMenu() {
      GUIWindow gui = new GUIWindow("selectitem_trigger", 9, "&8Put your item here");
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
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (aPlayer.getActiveTrigger() instanceof TriggerKeyItem trigger) {
            Inventory inv = event.getInventory();
            inv.setItem(4, trigger.getItem());
         }
      });
      gui.addCloseAction("save", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (aPlayer.getActiveTrigger() instanceof TriggerKeyItem trigger) {
            Inventory inv = event.getInventory();
            ItemStack savedItem = inv.getItem(4);
            trigger.setItem(savedItem);
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSaved item for this trigger!"));
         }
      });
   }

   public static void initItemSelectFunctionMenu() {
      GUIWindow gui = new GUIWindow("selectitem_function", 9, "&8Put your item here");
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
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (aPlayer.getActiveFunction() instanceof FunctionGiveItem function) {
            Inventory inv = event.getInventory();
            inv.setItem(4, function.getItem());
         }
      });
      gui.addCloseAction("save", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (aPlayer.getActiveFunction() instanceof FunctionGiveItem function) {
            Inventory inv = event.getInventory();
            ItemStack savedItem = inv.getItem(4);
            function.setItem(savedItem);
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSaved item for this function!"));
         }
      });
   }

   public static void initConditionsMenus() {
      initConditionsBrowser();
      initConditionsEditor();
      initConditionsRemover();
   }

   private static void initConditionsBrowser() {
      GUIWindow gui = new GUIWindow("conditionsmenu", 54, "&8Select a Condition");
      int i = 0;

      for (Entry<String, MenuButton> pair : Dungeons.inst().getConditionManager().getConditionButtons().entrySet()) {
         String conditionName = pair.getKey();
         MenuButton menuButton = pair.getValue();
         Button button = new Button("trigger_" + i, menuButton.getItem());
         button.addAction("click", event -> {
            Player player = (Player)event.getWhoClicked();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);

            try {
               DungeonTrigger trigger = aPlayer.getActiveTrigger();
               TriggerCondition condition = Dungeons.inst().getConditionManager().getCondition(conditionName).getDeclaredConstructor().newInstance();
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
               Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var6x.getMessage());

            }
         });
         gui.addButton(i, button);
         i++;
      }
   }

   private static void initConditionsEditor() {
      GUIWindow gui = new GUIWindow("editcondition", 54, "&8Edit a Condition");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         DungeonTrigger trigger = aPlayer.getActiveTrigger();
         GUIInventory guiInv = gui.getPlayersGui(player);

         for (int x = 0; x < 27; x++) {
            guiInv.removeButton(x);
         }

         int slot = 0;

         for (TriggerCondition condition : trigger.getConditions()) {
            MenuButton menuButton = Dungeons.inst().getConditionManager().getConditionButtons().get(condition.getClass().getSimpleName());
            if (menuButton != null) {
               Button button = new Button("condition_" + slot, menuButton.getItem());
               button.addLore("");
               button.addLore(HelperUtils.colorize("&6Options:"));
               List<Field> fields = new ArrayList<>();
               ReflectionUtils.getAnnotatedFields(fields, condition.getClass(), SavedField.class);

               for (Field field : fields) {
                  if (field.getAnnotation(Hidden.class) == null) {
                     try {
                        field.setAccessible(true);
                        String configVar = field.getName();
                        Object value = field.get(condition);
                        if (value != null && !(value instanceof Collection)) {
                           button.addLore(HelperUtils.colorize(" &e" + configVar + ": &7" + value));
                        }
                     } catch (IllegalAccessException var16) {
                        Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var16.getMessage());
                     }
                  }
               }

               button.addAction("click", clickEvent -> {
                  Player clickPlayer = (Player)clickEvent.getWhoClicked();
                  DungeonPlayer clickAPlayer = Dungeons.inst().getDungeonPlayer(clickPlayer);
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
      GUIWindow gui = new GUIWindow("removecondition", 54, "&8Remove a Condition");
      gui.addOpenAction("populate", event -> reloadConditionsRemovalGUI(gui, (Player)event.getPlayer()));
   }

   private static void reloadConditionsRemovalGUI(GUIWindow gui, Player player) {
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      DungeonTrigger trigger = aPlayer.getActiveTrigger();
      GUIInventory guiInv = gui.getPlayersGui(player);

      for (int x = 0; x < 27; x++) {
         guiInv.removeButton(x);
      }

      int slot = 0;

      for (TriggerCondition condition : trigger.getConditions()) {
         MenuButton menuButton = Dungeons.inst().getConditionManager().getConditionButtons().get(condition.getClass().getSimpleName());
         if (menuButton != null) {
            Button button = new Button("condition_" + slot, menuButton.getItem());
            button.addAction("click", clickEvent -> {
               Player clickPlayer = (Player)clickEvent.getWhoClicked();
               trigger.removeCondition(condition);
               clickPlayer.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cRemoved " + menuButton.getDisplayName() + " &ccondition from trigger."));
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
      GUIWindow gui = new GUIWindow("gatetriggersmenu", 54, "&8Select a Trigger");
      int i = 0;

      for (Entry<String, MenuButton> pair : Dungeons.inst().getTriggerManager().getTriggerButtons().entrySet()) {
         String triggerName = pair.getKey();
         MenuButton menuButton = pair.getValue();
         Button button = new Button("trigger_" + i, menuButton.getItem());
         button.addAction("click", event -> {
            Player player = (Player)event.getWhoClicked();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);

            try {
               if (!(aPlayer.getActiveTrigger() instanceof TriggerGate gate)) {
                  return;
               }

               DungeonTrigger trigger = Dungeons.inst().getTriggerManager().getTrigger(triggerName).getDeclaredConstructor().newInstance();
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
               Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var7.getMessage());
            }
         });
         gui.addButton(i, button);
         i++;
      }
   }

   private static void initGateTriggerEditor() {
      GUIWindow gui = new GUIWindow("editgatetrigger", 54, "&8Edit a Trigger");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (aPlayer.getActiveTrigger() instanceof TriggerGate gate) {
            GUIInventory guiInv = gui.getPlayersGui(player);

            for (int x = 0; x < 54; x++) {
               guiInv.removeButton(x);
            }

            int slot = 0;

            for (DungeonTrigger trigger : gate.getTriggers()) {
               MenuButton menuButton = Dungeons.inst().getTriggerManager().getTriggerButtons().get(trigger.getClass().getSimpleName());
               if (menuButton != null) {
                  Button button = new Button("trigger_" + slot, menuButton.getItem());
                  button.addLore("");
                  button.addLore(HelperUtils.colorize("&6Options:"));
                  List<Field> fields = new ArrayList<>();
                  ReflectionUtils.getAnnotatedFields(fields, trigger.getClass(), SavedField.class);

                  for (Field field : fields) {
                     if (field.getAnnotation(Hidden.class) == null) {
                        try {
                           field.setAccessible(true);
                           String configVar = field.getName();
                           Object value = field.get(trigger);
                           if (value != null && !(value instanceof Collection)) {
                              button.addLore(HelperUtils.colorize(" &e" + configVar + ": &7" + value));
                           }
                        } catch (IllegalAccessException var17) {
                           Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var17.getMessage());
                        }
                     }
                  }

                  if (trigger instanceof TriggerGate subGate) {
                     button.addLore(HelperUtils.colorize(" &eTriggers:"));

                     for (DungeonTrigger subTrig : subGate.getTriggers()) {
                        button.addLore(HelperUtils.colorize(" &e- &7" + subTrig.getDisplayName()));
                     }
                  }

                  button.addAction("click", clickEvent -> {
                     Player clickPlayer = (Player)clickEvent.getWhoClicked();
                     DungeonPlayer clickAPlayer = Dungeons.inst().getDungeonPlayer(clickPlayer);
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
      GUIWindow gui = new GUIWindow("removegatetrigger", 54, "&8Remove a Trigger");
      gui.addOpenAction("populate", event -> reloadTriggerRemovalGUI(gui, (Player)event.getPlayer()));
   }

   private static void reloadTriggerRemovalGUI(GUIWindow gui, Player player) {
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer.getActiveTrigger() instanceof TriggerGate gate) {
         GUIInventory guiInv = gui.getPlayersGui(player);

         for (int x = 0; x < 54; x++) {
            guiInv.removeButton(x);
         }

         int slot = 0;

         for (DungeonTrigger trigger : gate.getTriggers()) {
            MenuButton menuButton = Dungeons.inst().getTriggerManager().getTriggerButtons().get(trigger.getClass().getSimpleName());
            if (menuButton != null) {
               Button button = new Button("trigger_" + slot, menuButton.getItem());
               button.addAction("click", clickEvent -> {
                  Player clickPlayer = (Player)clickEvent.getWhoClicked();
                  gate.removeTrigger(trigger);
                  clickPlayer.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cRemoved " + menuButton.getDisplayName() + " &ctrigger."));
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
      GUIWindow gui = new GUIWindow("editmultifunction", 54, "&8Edit a Function");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (aPlayer.getActiveFunction() instanceof FunctionMulti function) {
            GUIInventory guiInv = gui.getPlayersGui(player);

            for (int x = 0; x < 54; x++) {
               guiInv.removeButton(x);
            }

            int slot = 0;

            for (DungeonFunction targetFunction : function.getFunctions()) {
               MenuButton menuButton = Dungeons.inst().getFunctionManager().getFunctionButtons().get(targetFunction.getClass().getSimpleName());
               if (menuButton != null) {
                  Button button = new Button("function_" + slot, menuButton.getItem());
                  button.addLore("");
                  button.addLore(HelperUtils.colorize("&6Options:"));
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
                                 button.addLore(HelperUtils.colorize(" &eTrigger: &7" + trig.getDisplayName()));
                              } else {
                                 button.addLore(HelperUtils.colorize(" &e" + configVar + ": &7" + value));
                              }
                           }
                        } catch (IllegalAccessException var18) {
                           Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var18.getMessage());
                        }
                     }
                  }

                  if (targetFunction instanceof FunctionMulti subMulti) {
                     button.addLore(HelperUtils.colorize(" &eFunctions:"));

                     for (DungeonFunction subFunction : subMulti.getFunctions()) {
                        button.addLore(HelperUtils.colorize("  &e- &7" + subFunction.getDisplayName()));
                     }
                  }

                  button.addAction("click", clickEvent -> {
                     Player clickPlayer = (Player)clickEvent.getWhoClicked();
                     DungeonPlayer clickAPlayer = Dungeons.inst().getDungeonPlayer(clickPlayer);
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
      GUIWindow gui = new GUIWindow("removemultifunction", 54, "&8Remove a Function");
      gui.addOpenAction("populate", event -> reloadMultiFunctionRemovalGUI(gui, (Player)event.getPlayer()));
   }

   private static void reloadMultiFunctionRemovalGUI(GUIWindow gui, Player player) {
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer.getActiveFunction() instanceof FunctionMulti function) {
         GUIInventory guiInv = gui.getPlayersGui(player);

         for (int x = 0; x < 54; x++) {
            guiInv.removeButton(x);
         }

         int slot = 0;

         for (DungeonFunction targetFunction : function.getFunctions()) {
            MenuButton menuButton = Dungeons.inst().getFunctionManager().getFunctionButtons().get(targetFunction.getClass().getSimpleName());
            if (menuButton != null) {
               Button button = new Button("function_" + slot, menuButton.getItem());
               button.addAction("click", clickEvent -> {
                  Player clickPlayer = (Player)clickEvent.getWhoClicked();
                  function.removeFunction(targetFunction);
                  clickPlayer.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cRemoved " + menuButton.getDisplayName() + " &cfunction."));
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
      GUIWindow gui = new GUIWindow("editmultifunctiontriggers", 54, "&8Edit Function Triggers");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (aPlayer.getActiveFunction() instanceof FunctionMulti function) {
            GUIInventory guiInv = gui.getPlayersGui(player);

            for (int x = 0; x < 54; x++) {
               guiInv.removeButton(x);
            }

            int slot = 0;

            for (DungeonFunction targetFunction : function.getFunctions()) {
               MenuButton menuButton = Dungeons.inst().getFunctionManager().getFunctionButtons().get(targetFunction.getClass().getSimpleName());
               if (menuButton != null) {
                  Button button = new Button("function_" + slot, menuButton.getItem());
                  button.addLore("");
                  button.addLore(HelperUtils.colorize("&6Options:"));
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
                                 button.addLore(HelperUtils.colorize(" &d&lTrigger: &7&l" + trig.getDisplayName()));
                              } else {
                                 button.addLore(HelperUtils.colorize(" &e" + configVar + ": &7" + value));
                              }
                           }
                        } catch (IllegalAccessException var18) {
                           Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var18.getMessage());
                        }
                     }
                  }

                  if (targetFunction instanceof FunctionMulti subMulti) {
                     button.addLore(HelperUtils.colorize(" &eFunctions:"));

                     for (DungeonFunction subFunction : subMulti.getFunctions()) {
                        button.addLore(HelperUtils.colorize("  &e- &7" + subFunction.getDisplayName()));
                     }
                  }

                  button.addAction("click", clickEvent -> {
                     Player clickPlayer = (Player)clickEvent.getWhoClicked();
                     DungeonPlayer clickAPlayer = Dungeons.inst().getDungeonPlayer(clickPlayer);
                     clickAPlayer.setActiveFunction(targetFunction);
                     Dungeons.inst().getAvnAPI().openGUI(clickPlayer, "triggermenu");
                  });
                  guiInv.setButton(slot, button);
                  slot++;
               }
            }
         }
      });
   }
}
