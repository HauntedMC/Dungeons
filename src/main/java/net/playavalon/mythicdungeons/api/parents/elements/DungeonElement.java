package net.playavalon.mythicdungeons.api.parents.elements;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.listeners.ElementListener;
import net.playavalon.mythicdungeons.menu.HotbarMenu;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.utility.helpers.ReflectionUtils;
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
   protected HotbarMenu menu;

   public DungeonElement(Map<String, Object> config) {
      this.config = config;
      this.editListener = new ElementListener(this);
      Bukkit.getPluginManager().registerEvents(this.editListener, MythicDungeons.inst());
   }

   public DungeonElement() {
      this.config = null;
      this.editListener = new ElementListener(this);
      Bukkit.getPluginManager().registerEvents(this.editListener, MythicDungeons.inst());
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
            var5.printStackTrace();
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
         var6.printStackTrace();
      }

      return map;
   }

   public DungeonElement clone() throws CloneNotSupportedException {
      DungeonElement clone = (DungeonElement)super.clone();
      clone.editListener = new ElementListener(clone);
      Bukkit.getPluginManager().registerEvents(clone.editListener, MythicDungeons.inst());
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

   public HotbarMenu getMenu() {
      return this.menu;
   }
}
