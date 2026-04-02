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
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.util.HelperUtils;
import nl.hauntedmc.dungeons.util.reflection.ClassReflectionUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;

public final class FunctionManager {
   private final HashMap<String, Class<? extends DungeonFunction>> functions = new HashMap<>();
   private final HashMap<String, MenuButton> functionButtons = new HashMap<>();
   private final HashMap<FunctionCategory, Map<String, MenuButton>> buttonsByCategory = new HashMap<>();

   public FunctionManager() {
      for (FunctionCategory cat : FunctionCategory.values()) {
         this.buttonsByCategory.put(cat, new HashMap<>());
      }
   }

   public <T extends DungeonFunction> void register(Class<T> functionClass) {
      try {
         this.functions.put(functionClass.getSimpleName(), functionClass);
         Method menuButtonMethod = functionClass.getDeclaredMethod("buildMenuButton");
         menuButtonMethod.setAccessible(true);
         DungeonFunction function = functionClass.getDeclaredConstructor().newInstance();
         MenuButton button = (MenuButton)menuButtonMethod.invoke(function);
         if (button == null) {
            Dungeons.inst()
               .getLogger()
               .info(
                  HelperUtils.colorize(
                     "&cERROR :: The function &6"
                        + functionClass.getSimpleName()
                        + " &cdoes not have a menu button! It will not appear in the function selection menu!"
                  )
               );
            return;
         }

         this.functionButtons.put(functionClass.getSimpleName(), button);
         Field catField = functionClass.getField("category");
         catField.setAccessible(true);
         FunctionCategory cat = (FunctionCategory)catField.get(function);
         if (cat != null) {
            this.buttonsByCategory.get(cat).put(functionClass.getSimpleName(), button);
         }

         List<Method> eventMethods = new ArrayList<>();
         ClassReflectionUtils.collectAnnotatedMethods(eventMethods, functionClass, EventHandler.class);

         for (Method method : eventMethods) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            Class<?> rawEvent = method.getParameterTypes()[0];
            if (Event.class.isAssignableFrom(rawEvent)) {
               Class<? extends Event> eventClass = rawEvent.asSubclass(Event.class);
               Bukkit.getPluginManager().registerEvent(eventClass, Dungeons.inst().getElementEventHandler(), handler.priority(), (listener, event) -> {
                  for (AbstractInstance inst : Dungeons.inst().getActiveInstances()) {
                     List<DungeonFunction> elements = inst.getFunctionListeners(functionClass);
                     if (elements != null) {
                        for (DungeonFunction element : new ArrayList<>(elements)) {
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
            .info(
               Dungeons.logPrefix
                  + HelperUtils.colorize("&cERROR :: The function &6" + functionClass.getSimpleName() + " &chas a misconfigured menu button declaration!")
            );
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var13.getMessage());
      } catch (NoSuchFieldException var14) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var14.getMessage());
      }
   }

   public Class<? extends DungeonFunction> getFunction(String name) {
      return this.functions.get(name);
   }

   public Collection<Class<? extends DungeonFunction>> getAll() {
      return this.functions.values();
   }

   public HashMap<String, MenuButton> getFunctionButtons() {
      return this.functionButtons;
   }

   public HashMap<FunctionCategory, Map<String, MenuButton>> getButtonsByCategory() {
      return this.buttonsByCategory;
   }
}
