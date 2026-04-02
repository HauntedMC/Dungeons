package nl.hauntedmc.dungeons.managers;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.parents.TriggerCategory;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.util.HelperUtils;
import nl.hauntedmc.dungeons.util.reflection.ClassReflectionUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;

public final class TriggerManager {
   private final HashMap<String, Class<? extends DungeonTrigger>> triggers = new HashMap<>();
   private final HashMap<String, MenuButton> triggerButtons = new HashMap<>();
   private final Map<TriggerCategory, Map<String, MenuButton>> buttonsByCategory = new HashMap<>();

   public TriggerManager() {
      for (TriggerCategory cat : TriggerCategory.values()) {
         this.buttonsByCategory.put(cat, new HashMap<>());
      }
   }

   public <T extends DungeonTrigger> void register(Class<T> triggerClass) {
      try {
         this.triggers.put(triggerClass.getSimpleName(), triggerClass);
         Method menuButtonMethod = triggerClass.getDeclaredMethod("buildMenuButton");
         menuButtonMethod.setAccessible(true);
         DungeonTrigger trigger = triggerClass.getDeclaredConstructor().newInstance();
         MenuButton button = (MenuButton)menuButtonMethod.invoke(trigger);
         if (button == null) {
            Dungeons.inst()
               .getLogger()
               .info(
                  HelperUtils.colorize(
                     "&cERROR :: The trigger &6"
                        + triggerClass.getSimpleName()
                        + " &cdoes not have a menu button! It will not appear in the trigger selection menu!"
                  )
               );
            return;
         }

         this.triggerButtons.put(triggerClass.getSimpleName(), button);
         Field catField = triggerClass.getField("category");
         catField.setAccessible(true);
         TriggerCategory cat = (TriggerCategory)catField.get(trigger);
         if (cat != null) {
            this.buttonsByCategory.get(cat).put(triggerClass.getSimpleName(), button);
         }

         List<Method> eventMethods = new ArrayList<>();
         ClassReflectionUtils.collectAnnotatedMethods(eventMethods, triggerClass, EventHandler.class);

         for (Method method : eventMethods) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            Class<?> rawEvent = method.getParameterTypes()[0];
            if (Event.class.isAssignableFrom(rawEvent)) {
               Class<? extends Event> eventClass = rawEvent.asSubclass(Event.class);
               Bukkit.getPluginManager().registerEvent(eventClass, Dungeons.inst().getElementEventHandler(), handler.priority(), (listener, event) -> {
                  for (AbstractInstance inst : Dungeons.inst().getActiveInstances()) {
                     List<DungeonTrigger> elements = inst.getTriggerListeners(triggerClass);
                     if (elements != null) {
                        for (DungeonTrigger element : new ArrayList<>(elements)) {
                           if (eventClass.isAssignableFrom(event.getClass())) {
                              try {
                                 method.invoke(element, event);
                              } catch (Exception var12x) {
                                 Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var12x.getMessage());

                              }
                           }
                        }
                     }
                  }
               }, Dungeons.inst());
            }
         }
      } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException var13) {
         Dungeons.inst()
            .getLogger()
            .info(HelperUtils.colorize("&cERROR :: The trigger &6" + triggerClass.getSimpleName() + " &chas a misconfigured menu button declaration!"));
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var13.getMessage());
      } catch (NoSuchFieldException var14) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var14.getMessage());
      }
   }

   public Class<? extends DungeonTrigger> getTrigger(String name) {
      return this.triggers.get(name);
   }

   public Collection<Class<? extends DungeonTrigger>> getAll() {
      return this.triggers.values();
   }

   public HashMap<String, MenuButton> getTriggerButtons() {
      return this.triggerButtons;
   }

   public Map<TriggerCategory, Map<String, MenuButton>> getButtonsByCategory() {
      return this.buttonsByCategory;
   }
}
