package nl.hauntedmc.dungeons.managers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.parents.elements.TriggerCondition;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.util.HelperUtils;

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
            Dungeons.inst()
               .getLogger()
               .info(
                  HelperUtils.colorize(
                     "&cERROR :: The condition &6"
                        + conditionClass.getSimpleName()
                        + " &cdoes not have a menu button! It will not appear in the function selection menu!"
                  )
               );
            return;
         }

         this.conditionButtons.put(conditionClass.getSimpleName(), button);
      } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException var4) {
         Dungeons.inst()
            .getLogger()
            .info(HelperUtils.colorize("&cERROR :: The condition &6" + conditionClass.getSimpleName() + " &chas a misconfigured menu button declaration!"));
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var4.getMessage());
      }
   }

   public Class<? extends TriggerCondition> getCondition(String name) {
      return this.conditions.get(name);
   }

   public Collection<Class<? extends TriggerCondition>> getAll() {
      return this.conditions.values();
   }

   public HashMap<String, MenuButton> getConditionButtons() {
      return this.conditionButtons;
   }
}
