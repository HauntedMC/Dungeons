package nl.hauntedmc.dungeons.api.parents.elements;

import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.DungeonPlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class TriggerCondition extends DungeonElement implements Cloneable, ConfigurationSerializable {
   protected String namespace;
   private boolean initialized = false;
   @SavedField
   protected boolean inverted = false;
   protected DungeonTrigger trigger;

   public TriggerCondition(String namespace, Map<String, Object> config) {
      super(config);
      this.namespace = namespace;
   }

   public TriggerCondition(String namespace) {
      this.namespace = namespace;
   }

   @Override
   public final void init() {
      if (!this.initialized) {
         this.initFields();
         this.initMenu();
         this.initialized = true;
      }
   }

   public abstract boolean check(TriggerFireEvent var1);

   public void onEnable() {
   }

   public void onDisable() {
   }

   public final void enable(DungeonTrigger trigger) {
      if (!this.enabled) {
         this.enabled = true;
         this.trigger = trigger;
         this.instance = trigger.getInstance();
         this.location = trigger.getLocation();
         this.onEnable();
      }
   }

   @Override
   public final void disable() {
      super.disable();
      this.onDisable();
   }

   @Override
   public abstract MenuButton buildMenuButton();

   @Override
   public abstract void buildHotbarMenu();

   @Override
   protected final void initMenu() {
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
            aPlayer.setActiveCondition(null);
            aPlayer.previousHotbar();
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lToggle Condition Requirement");
            this.button.setEnchanted(!TriggerCondition.this.inverted);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerCondition.this.inverted) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Condition &cmust NOT &6be met&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Condition &bmust &6be met&a'"));
            }

            TriggerCondition.this.inverted = !TriggerCondition.this.inverted;
         }
      });
      this.buildHotbarMenu();
   }

   public TriggerCondition clone() {
      try {
         TriggerCondition newCondition = (TriggerCondition)super.clone();
         newCondition.initMenu();
         return newCondition;
      } catch (CloneNotSupportedException var2) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var2.getMessage());
         return null;
      }
   }

   public String getNamespace() {
      return this.namespace;
   }

   public boolean isInitialized() {
      return this.initialized;
   }

   public boolean isInverted() {
      return this.inverted;
   }

   public DungeonTrigger getTrigger() {
      return this.trigger;
   }

   public void setTrigger(DungeonTrigger trigger) {
      this.trigger = trigger;
   }
}
