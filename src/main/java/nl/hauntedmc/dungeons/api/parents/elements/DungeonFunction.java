package nl.hauntedmc.dungeons.api.parents.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.Hidden;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceProcedural;
import nl.hauntedmc.dungeons.gui.hotbar.DungeonPlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class DungeonFunction extends DungeonElement implements Cloneable, ConfigurationSerializable {
   protected String namespace;
   private String displayName;
   private boolean initialized = false;
   protected String colour;
   private boolean requiresTrigger = true;
   private boolean allowRetriggerByDefault = false;
   public FunctionCategory category = FunctionCategory.PLAYER;
   public boolean requiresTarget = false;
   private boolean allowChangingTargetType = true;
   @SavedField
   protected FunctionTargetType targetType = FunctionTargetType.NONE;
   private int targetIndex = 0;
   @SavedField
   @Nullable
   protected DungeonTrigger trigger;
   @SavedField
   @Hidden
   protected Location location;
   @Nullable
   protected DungeonFunction parentFunction;

   public DungeonFunction(String namespace, Map<String, Object> config) {
      super(config);
      this.namespace = namespace;
      this.displayName = namespace;
   }

   public DungeonFunction(String namespace) {
      this.namespace = namespace;
      this.displayName = namespace;
   }

   @Override
   public void init() {
      if (!this.initialized) {
         this.initFields();
         this.targetIndex = this.targetType.getIndex();
         this.initMenu();
         if (this.trigger != null) {
            this.trigger.setFunction(this);
            this.trigger.init();
         }

         this.verifyTargetType();
         this.initialized = true;
      }
   }

   @Override
   protected final void initLegacyFields() {
      if (this.config != null) {
         if (this.config.containsKey("Trigger")) {
            this.trigger = (DungeonTrigger)this.config.get("Trigger");
         }

         if (this.config.containsKey("Location")) {
            this.location = (Location)this.config.get("Location");
         }
      }
   }

   @Override
   protected final void initAdditionalFields() {
      if (this.trigger != null) {
         this.trigger.setFunction(this);
         this.trigger.initFields();
      }
   }

   @Override
   public void setLocation(Location loc) {
      this.location = loc;
      super.location = loc;
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public final void enable(AbstractInstance instance, Location loc) {
      if (!this.enabled) {
         this.enabled = true;
         this.instance = instance;
         this.location = loc;
         this.register();
         if (this.trigger != null) {
            this.trigger.enable(this);
         }

         this.onEnable();
      }
   }

   @Override
   public final void disable() {
      super.disable();
      if (this.trigger != null) {
         this.trigger.disable();
      }

      this.unregister();
      this.onDisable();
   }

   public void setTrigger(DungeonTrigger trigger) {
      this.trigger = trigger;
      if (trigger != null) {
         this.trigger.setFunction(this);
         this.trigger.init();
      }
   }

   public void setCategory(FunctionCategory category) {
      this.category = category;
      this.setColour(category.getColor());
   }

   public final void register() {
      this.instance.registerFunctionListener(this);
   }

   public final void unregister() {
      this.instance.unregisterFunctionListener(this);
   }

   public abstract void runFunction(TriggerFireEvent var1, List<DungeonPlayer> var2);

   public void onExecute(TriggerFireEvent triggerEvent) {
   }

   public final void execute(TriggerFireEvent event) {
      if (!event.isCancelled()) {
         DungeonTrigger eventTrigger = event.getTrigger();
         if (eventTrigger.getClass().equals(this.trigger.getClass())) {
            this.onExecute(event);
            DungeonPlayer mPlayer = event.getDPlayer();
            List<DungeonPlayer> functionTargets = new ArrayList<>();
            switch (this.targetType) {
               case PLAYER:
                  if (mPlayer != null) {
                     functionTargets.add(event.getDPlayer());
                  }
                  break;
               case PARTY:
                  if (mPlayer == null) {
                     functionTargets.addAll(this.instance.getPlayers());
                  } else {
                     IDungeonParty party = mPlayer.getiDungeonParty();
                     if (party == null) {
                        functionTargets.add(mPlayer);
                     } else {
                        for (Player pPlayer : party.getPlayers()) {
                           DungeonPlayer pMPlayer = Dungeons.inst().getDungeonPlayer(pPlayer);
                           functionTargets.add(pMPlayer);
                        }
                     }
                  }
                  break;
               case ROOM:
                  InstanceProcedural inst = this.instance.as(InstanceProcedural.class);
                  if (inst != null) {
                     InstanceRoom targetRoom = inst.getRoom(this.location);
                     if (targetRoom != null) {
                        for (DungeonPlayer targetPlayer : inst.getPlayers()) {
                           Player player = targetPlayer.getPlayer();
                           if (player.getBoundingBox().overlaps(targetRoom.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))) {
                              functionTargets.add(targetPlayer);
                           }
                        }
                     }
                  }
            }

            this.runFunction(event, functionTargets);
            functionTargets.clear();
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
            aPlayer.previousHotbar();
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.STICKY_PISTON);
            String triggerName = "NONE";
            if (DungeonFunction.this.trigger != null) {
               triggerName = DungeonFunction.this.trigger.getNamespace();
            }

            this.button.setDisplayName("&a&lTrigger: " + triggerName);
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            if (DungeonFunction.this.trigger == null) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eThis function doesn't have a trigger!"));
            } else {
               aPlayer.setHotbar(DungeonFunction.this.trigger.getMenu());
            }
         }
      });
      if (this.allowChangingTargetType) {
         this.menu
            .addMenuItem(
               new MenuItem() {
                  @Override
                  public void buildButton() {
                     this.button = new MenuButton(Material.OBSERVER);
                     String targetTypeDisplay = DungeonFunction.this.targetType.toString();
                     if (DungeonFunction.this.trigger != null
                        && !DungeonFunction.this.trigger.hasTarget
                        && DungeonFunction.this.targetType == FunctionTargetType.PARTY) {
                        targetTypeDisplay = "ALL PLAYERS";
                     }

                     this.button.setDisplayName("&d&lTarget Type: " + targetTypeDisplay);
                  }

                  @Override
                  public void onSelect(PlayerEvent event) {
                     Player player = event.getPlayer();
                     DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
                     DungeonFunction.this.targetIndex++;
                     DungeonFunction.this.verifyTargetType(aPlayer.getInstance());
                     String targetTypeDisplay = DungeonFunction.this.targetType.getDisplay();
                     if (DungeonFunction.this.trigger != null
                        && !DungeonFunction.this.trigger.hasTarget
                        && DungeonFunction.this.targetType == FunctionTargetType.PARTY) {
                        targetTypeDisplay = "All Players";
                     }

                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched target type to '&6" + targetTypeDisplay + "&a'"));
                     aPlayer.setHotbar(this.menu);
                  }
               }
            );
      }

      this.buildHotbarMenu();
   }

   private void verifyTargetType() {
      this.verifyTargetType(null);
   }

   private void verifyTargetType(AbstractInstance instance) {
      if (this.requiresTarget && this.targetIndex == 0) {
         this.targetIndex++;
      }

      if (this.trigger != null && !this.trigger.hasTarget && this.targetIndex == 1) {
         this.targetIndex++;
      }

      if (instance != null && instance.as(InstanceEditableProcedural.class) == null && this.targetIndex == 3) {
         this.targetIndex++;
      }

      if (this.targetIndex >= FunctionTargetType.values().length) {
         this.targetIndex = 0;
      }

      this.targetType = FunctionTargetType.intToTargetType(this.targetIndex);
   }

   @Override
   public void setInstance(AbstractInstance inst) {
      this.instance = inst;
      if (this.trigger != null) {
         this.trigger.setInstance(this.instance);
      }
   }

   public void setDisplayName(String displayName) {
      this.displayName = displayName;
      if (this.instance != null) {
         if (this.instance instanceof InstanceEditable) {
            ((InstanceEditable)this.instance).updateLabel(this);
         }
      }
   }

   public DungeonFunction clone() {
      try {
         DungeonFunction clone = (DungeonFunction)super.clone();
         clone.location = this.location.clone();
         if (this.trigger != null) {
            clone.trigger = this.trigger.clone();
            clone.trigger.location = clone.location;
         }

         clone.initMenu();
         return clone;
      } catch (CloneNotSupportedException var2) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var2.getMessage());
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

   public String getColour() {
      return this.colour;
   }

   protected void setColour(String colour) {
      this.colour = colour;
   }

   public boolean isRequiresTrigger() {
      return this.requiresTrigger;
   }

   public void setRequiresTrigger(boolean requiresTrigger) {
      this.requiresTrigger = requiresTrigger;
   }

   public boolean isAllowRetriggerByDefault() {
      return this.allowRetriggerByDefault;
   }

   public void setAllowRetriggerByDefault(boolean allowRetriggerByDefault) {
      this.allowRetriggerByDefault = allowRetriggerByDefault;
   }

   public FunctionCategory getCategory() {
      return this.category;
   }

   public boolean isRequiresTarget() {
      return this.requiresTarget;
   }

   public void setRequiresTarget(boolean requiresTarget) {
      this.requiresTarget = requiresTarget;
   }

   public boolean isAllowChangingTargetType() {
      return this.allowChangingTargetType;
   }

   public void setAllowChangingTargetType(boolean allowChangingTargetType) {
      this.allowChangingTargetType = allowChangingTargetType;
   }

   public FunctionTargetType getTargetType() {
      return this.targetType;
   }

   public void setTargetType(FunctionTargetType targetType) {
      this.targetType = targetType;
   }

   @Nullable
   public DungeonTrigger getTrigger() {
      return this.trigger;
   }

   @Override
   public Location getLocation() {
      return this.location;
   }

   public void setParentFunction(@Nullable DungeonFunction parentFunction) {
      this.parentFunction = parentFunction;
   }

   @Nullable
   public DungeonFunction getParentFunction() {
      return this.parentFunction;
   }
}
