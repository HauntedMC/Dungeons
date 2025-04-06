package net.playavalon.mythicdungeons.managers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.utility.helpers.Util;

public final class ConditionManager {
   private final HashMap<String, Class<? extends TriggerCondition>> conditions = new HashMap<>();
   private final HashMap<String, MenuButton> conditionButtons = new HashMap<>();

   public <T extends TriggerCondition> void register(Class<T> conditionClass) {
      try {
         this.conditions.put(conditionClass.getSimpleName(), conditionClass);
         Method menuButtonMethod = conditionClass.getDeclaredMethod("buildMenuButton");
         menuButtonMethod.setAccessible(true);
         MenuButton button = (MenuButton)menuButtonMethod.invoke(conditionClass.getDeclaredConstructor().newInstance());
         if (button == null) {
            MythicDungeons.inst()
               .getLogger()
               .info(
                  Util.colorize(
                     "&cERROR :: The condition &6"
                        + conditionClass.getSimpleName()
                        + " &cdoes not have a menu button! It will not appear in the function selection menu!"
                  )
               );
            return;
         }

         this.conditionButtons.put(conditionClass.getSimpleName(), button);
      } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException var4) {
         MythicDungeons.inst()
            .getLogger()
            .info(Util.colorize("&cERROR :: The condition &6" + conditionClass.getSimpleName() + " &chas a misconfigured menu button declaration!"));
         var4.printStackTrace();
      }
   }

   public Class<? extends TriggerCondition> getCondition(String name) {
      return this.conditions.get(name);
   }

   public Collection<String> getConditionNames() {
      return this.conditions.keySet();
   }

   public Collection<Class<? extends TriggerCondition>> getAll() {
      return this.conditions.values();
   }

   public HashMap<String, MenuButton> getConditionButtons() {
      return this.conditionButtons;
   }
}
