package nl.hauntedmc.dungeons.api.parents.elements;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.listeners.ElementListener;
import nl.hauntedmc.dungeons.gui.hotbar.DungeonPlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.util.version.ReflectionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public abstract class DungeonElement implements ConfigurationSerializable {
   protected boolean enabled;
   private ElementListener editListener;
   protected Map<String, Object> config;
   protected Location location;
   protected AbstractInstance instance;
   protected DungeonPlayerHotbarMenu menu;

   public DungeonElement(Map<String, Object> config) {
      this.config = config;
      this.editListener = new ElementListener(this);
      Bukkit.getPluginManager().registerEvents(this.editListener, Dungeons.inst());
   }

   public DungeonElement() {
      this.config = null;
      this.editListener = new ElementListener(this);
      Bukkit.getPluginManager().registerEvents(this.editListener, Dungeons.inst());
   }

   public abstract void init();

   public final void initFields() {
      if (this.config != null) {
         try {
            List<Field> fields = new ArrayList<>();
            ReflectionUtils.getAnnotatedFields(fields, this.getClass(), SavedField.class);

            for (Field field : fields) {
               field.setAccessible(true);
               String configVar = field.getName();
               if (this.config.get(configVar) != null) {
                  field.set(this, this.config.get(configVar));
               }
            }
         } catch (IllegalAccessException var5) {
            Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var5.getMessage());
         }

         this.initLegacyFields();
         this.initLegacyFields(this.config);
         this.initAdditionalFields();
      }
   }

   protected void initAdditionalFields() {
   }

   protected void initLegacyFields() {
   }

   protected void initLegacyFields(Map<String, Object> config) {
   }

   public void disable() {
      HandlerList.unregisterAll(this.editListener);
   }

   public abstract MenuButton buildMenuButton();

   public abstract void buildHotbarMenu();

   protected abstract void initMenu();

   @NotNull
   public Map<String, Object> serialize() {
      Map<String, Object> map = new HashMap<>();
      if (this.location != null) {
         this.location.setWorld(null);
      }

      try {
         List<Field> fields = new ArrayList<>();
         ReflectionUtils.getAllFields(fields, this.getClass());

         for (Field field : fields) {
            field.setAccessible(true);
            if (field.getAnnotation(SavedField.class) != null) {
               String configName = field.getName();
               map.put(configName, field.get(this));
            }
         }
      } catch (IllegalAccessException var6) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var6.getMessage());
      }

      return map;
   }

   public DungeonElement clone() throws CloneNotSupportedException {
      DungeonElement clone = (DungeonElement)super.clone();
      clone.editListener = new ElementListener(clone);
      Bukkit.getPluginManager().registerEvents(clone.editListener, Dungeons.inst());
      return clone;
   }

   public Location getLocation() {
      return this.location;
   }

   public void setLocation(Location location) {
      this.location = location;
   }

   public AbstractInstance getInstance() {
      return this.instance;
   }

   public void setInstance(AbstractInstance instance) {
      this.instance = instance;
   }

   public DungeonPlayerHotbarMenu getMenu() {
      return this.menu;
   }
}
