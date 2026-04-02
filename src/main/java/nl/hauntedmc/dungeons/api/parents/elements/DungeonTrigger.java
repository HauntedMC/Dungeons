package nl.hauntedmc.dungeons.api.parents.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.TriggerCategory;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceContinuous;
import nl.hauntedmc.dungeons.gui.hotbar.DungeonPlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

public abstract class DungeonTrigger extends DungeonElement implements Cloneable, ConfigurationSerializable {
   protected String namespace;
   private String displayName;
   private boolean initialized = false;
   public TriggerCategory category = TriggerCategory.PLAYER;
   @SavedField
   protected boolean allowRetrigger;
   @SavedField
   protected List<TriggerCondition> conditions;
   @SavedField
   protected boolean limitToRoom;
   @Nullable
   protected DungeonFunction function;
   protected boolean waitForConditions;
   protected boolean hasTarget = false;
   protected List<UUID> playersFound;
   protected boolean triggered;
   private DungeonPlayerHotbarMenu conditionsMenu;
   private BukkitRunnable conditionWaiter;
   protected List<UUID> playersTriggered = new ArrayList<>();

   public DungeonTrigger(String displayName, Map<String, Object> config) {
      super(config);
      this.namespace = displayName;
      if (this.conditions == null) {
         this.conditions = new ArrayList<>();
      }

      this.playersFound = new ArrayList<>();
      this.displayName = this.namespace;
   }

   public DungeonTrigger(String displayName) {
      this.namespace = displayName;
      this.conditions = new ArrayList<>();
      this.playersFound = new ArrayList<>();
      this.displayName = this.namespace;
   }

   @Override
   public void init() {
      if (!this.initialized) {
         if (this.function != null) {
            this.setAllowRetrigger(this.function.isAllowRetriggerByDefault());
         }

         this.initFields();
         this.initConditions();
         this.initMenu();
         this.initConditionsMenu();
         this.initialized = true;
      }
   }

   @Override
   protected final void initLegacyFields() {
      if (this.config != null) {
         if (this.config.containsKey("AllowRetrigger")) {
            this.allowRetrigger = (Boolean)this.config.get("AllowRetrigger");
         }

         if (this.config.containsKey("Individual")) {
            this.function.setTargetType(FunctionTargetType.PLAYER);
         }

         if (this.config.containsKey("individual")) {
            this.function.setTargetType(FunctionTargetType.PLAYER);
         }
      }
   }

   @Override
   protected final void initAdditionalFields() {
      for (TriggerCondition condition : this.conditions) {
         condition.initFields();
      }
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void enable(@NotNull DungeonFunction function) {
      this.enable(function, function.getInstance());
   }

   public void enable(@Nullable DungeonFunction function, AbstractInstance instance) {
      if (!this.enabled) {
         this.enabled = true;
         this.instance = instance;
         this.register();
         if (function != null) {
            this.function = function;
            this.location = function.location;
         }

         for (TriggerCondition condition : this.conditions) {
            condition.enable(this);
         }

         this.onEnable();
      }
   }

   @Override
   public final void disable() {
      for (TriggerCondition condition : this.conditions) {
         condition.disable();
      }

      if (this.conditionWaiter != null && !this.conditionWaiter.isCancelled()) {
         this.conditionWaiter.cancel();
         this.conditionWaiter = null;
      }

      this.unregister();
      this.onDisable();
      super.disable();
   }

   public final void register() {
      this.instance.registerTriggerListener(this);
   }

   public final void unregister() {
      this.instance.unregisterTriggerListener(this);
   }

   public final void initConditions() {
      for (TriggerCondition condition : this.conditions) {
         condition.setTrigger(this);
         condition.init();
      }
   }

   public final boolean checkConditions(TriggerFireEvent event) {
      for (TriggerCondition condition : this.conditions) {
         boolean check;
         if (!condition.isInverted()) {
            check = condition.check(event);
         } else {
            check = !condition.check(event);
         }

         if (!check) {
            return false;
         }
      }

      return true;
   }

   public void addCondition(TriggerCondition condition) {
      this.conditions.add(condition);
   }

   public void removeCondition(TriggerCondition condition) {
      this.conditions.remove(condition);
   }

   public void onTrigger(TriggerFireEvent event) {
   }

   public final void trigger() {
      this.trigger(null);
   }

   public void trigger(DungeonPlayer aPlayer) {
      this.trigger(aPlayer, !this.allowRetrigger);
   }

   public final void trigger(DungeonPlayer aPlayer, boolean disable) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         this.triggered = true;
         final TriggerFireEvent event;
         if (aPlayer != null && aPlayer.getInstance() != null) {
            event = new TriggerFireEvent(aPlayer, this);
         } else {
            event = new TriggerFireEvent(instance, this);
         }

         if (instance instanceof InstanceContinuous) {
            if (aPlayer != null) {
               UUID tPlayerId = aPlayer.getPlayer().getUniqueId();
               if (this.playersTriggered.contains(tPlayerId)) {
                  return;
               }

               if (disable) {
                  this.playersTriggered.add(tPlayerId);
               }
            }

            disable = false;
         }

         final boolean finalDisable = disable;
         this.conditionWaiter = new BukkitRunnable() {
            public void run() {
               if (DungeonTrigger.this.checkConditions(event)) {
                  Bukkit.getPluginManager().callEvent(event);
                  if (!event.isCancelled()) {
                     if (DungeonTrigger.this.function != null) {
                        DungeonTrigger.this.function.execute(event);
                     }

                     DungeonTrigger.this.onTrigger(event);
                     if (finalDisable) {
                        this.cancel();
                        DungeonTrigger.this.disable();
                     }
                  }
               }
            }
         };
         if (!this.allowRetrigger && this.waitForConditions) {
            this.conditionWaiter.runTaskTimer(Dungeons.inst(), 0L, 20L);
         } else {
            this.conditionWaiter.runTask(Dungeons.inst());
         }
      }
   }

   @Override
   public abstract MenuButton buildMenuButton();

   @Override
   public abstract void buildHotbarMenu();

   @Override
   public final void initMenu() {
      this.menu = DungeonPlayerHotbarMenu.create();
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
            this.button.setDisplayName("&c&lBACK");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            aPlayer.setActiveTrigger(null);
            aPlayer.previousHotbar();
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.COMPARATOR);
            this.button.setDisplayName("&a&lTrigger Conditions");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            aPlayer.setHotbar(DungeonTrigger.this.conditionsMenu, true);
         }
      });
      this.buildHotbarMenu();
   }

   public void addRoomLimitToggleButton() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            InstanceEditableProcedural inst = DungeonTrigger.this.instance.as(InstanceEditableProcedural.class);
            if (inst == null) {
               this.button = null;
            } else {
               this.button = new MenuButton(Material.STRUCTURE_BLOCK);
               this.button.setDisplayName("&d&lLimit to Room");
               this.button.setEnchanted(DungeonTrigger.this.limitToRoom);
            }
         }

         @Override
         public void onSelect(Player player) {
            if (!DungeonTrigger.this.limitToRoom) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6trigger must be from the same room&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6trigger can come from anywhere&a'"));
            }

            DungeonTrigger.this.limitToRoom = !DungeonTrigger.this.limitToRoom;
         }
      });
   }

   public void initConditionsMenu() {
      this.conditionsMenu = DungeonPlayerHotbarMenu.create();
      this.conditionsMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
            this.button.setDisplayName("&c&lBACK");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            aPlayer.setActiveCondition(null);
            aPlayer.previousHotbar();
         }
      });
      this.conditionsMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.COMMAND_BLOCK);
            this.button.setDisplayName("&a&lAdd Condition");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            if (DungeonTrigger.this.conditions.size() >= 54) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cYou can't add any more conditions to this trigger!"));
            } else {
               Dungeons.inst().getAvnAPI().openGUI(player, "conditionsmenu");
            }
         }
      });
      this.conditionsMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
            this.button.setDisplayName("&e&lEdit Condition");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            Dungeons.inst().getAvnAPI().openGUI(player, "editcondition");
         }
      });
      this.conditionsMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BARRIER);
            this.button.setDisplayName("&c&lRemove Condition");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            Dungeons.inst().getAvnAPI().openGUI(player, "removecondition");
         }
      });
   }

   public void setDisplayName(String displayName) {
      this.displayName = displayName;
      if (this.instance != null) {
         InstanceEditable instance = this.instance.asEditInstance();
         if (instance != null) {
            instance.updateLabel(this.function);
         }
      }
   }

   public DungeonTrigger clone() {
      try {
         DungeonTrigger clone = (DungeonTrigger)super.clone();
         List<TriggerCondition> newConditions = new ArrayList<>();

         for (TriggerCondition oldCondition : this.conditions) {
            TriggerCondition clonedCondition = oldCondition.clone();
            clonedCondition.location = clone.location;
            clonedCondition.setTrigger(clone);
            newConditions.add(clonedCondition);
         }

         clone.conditions = newConditions;
         clone.playersFound = new ArrayList<>();
         clone.playersTriggered = new ArrayList<>();
         clone.initMenu();
         clone.initConditionsMenu();
         return clone;
      } catch (CloneNotSupportedException var6) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var6.getMessage());
         return null;
      }
   }

   public String getNamespace() {
      return this.namespace;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public boolean isInitialized() {
      return this.initialized;
   }

   public TriggerCategory getCategory() {
      return this.category;
   }

   public void setCategory(TriggerCategory category) {
      this.category = category;
   }

   public boolean isAllowRetrigger() {
      return this.allowRetrigger;
   }

   public void setAllowRetrigger(boolean allowRetrigger) {
      this.allowRetrigger = allowRetrigger;
   }

   public List<TriggerCondition> getConditions() {
      return this.conditions;
   }

   public boolean isLimitToRoom() {
      return this.limitToRoom;
   }

   @Nullable
   public DungeonFunction getFunction() {
      return this.function;
   }

   public void setFunction(@Nullable DungeonFunction function) {
      this.function = function;
   }

   public boolean isWaitForConditions() {
      return this.waitForConditions;
   }

   public boolean isHasTarget() {
      return this.hasTarget;
   }

   public void setHasTarget(boolean hasTarget) {
      this.hasTarget = hasTarget;
   }

   public List<UUID> getPlayersFound() {
      return this.playersFound;
   }

   public boolean isTriggered() {
      return this.triggered;
   }

   public DungeonPlayerHotbarMenu getConditionsMenu() {
      return this.conditionsMenu;
   }

   public List<UUID> getPlayersTriggered() {
      return this.playersTriggered;
   }
}
