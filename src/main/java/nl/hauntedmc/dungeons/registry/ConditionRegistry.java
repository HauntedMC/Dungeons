package nl.hauntedmc.dungeons.registry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;

/**
 * Registry for discoverable {@link TriggerCondition} implementations.
 */
public final class ConditionRegistry {
    private final DungeonsPlugin plugin;
    private final Map<String, Class<? extends TriggerCondition>> conditions = new LinkedHashMap<>();
    private final Map<Class<? extends TriggerCondition>, String> conditionIds = new LinkedHashMap<>();
    private final Map<String, MenuButton> conditionButtons = new LinkedHashMap<>();

    /** Creates a condition registry bound to the plugin logger and editor metadata lookup. */
    public ConditionRegistry(DungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a condition class and its editor button metadata.
     */
    public <T extends TriggerCondition> void register(Class<T> conditionClass) {
        String conditionId = TypeMetadataSupport.requiredId(conditionClass);
        try {
            this.conditions.put(conditionId, conditionClass);
            this.conditionIds.put(conditionClass, conditionId);

            Method menuButtonMethod = conditionClass.getDeclaredMethod("buildMenuButton");
            menuButtonMethod.setAccessible(true);
            MenuButton button = (MenuButton) menuButtonMethod
                    .invoke(conditionClass.getDeclaredConstructor().newInstance());
            if (button == null) {
                this.plugin.getSLF4JLogger().warn(
                        "Condition '{}' does not expose a menu button and will not appear in the editor.",
                        conditionClass.getSimpleName());
                return;
            }

            this.conditionButtons.put(conditionId, button);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException
                | IllegalAccessException exception) {
            this.plugin.getSLF4JLogger().error("Condition '{}' has an invalid buildMenuButton declaration.",
                    conditionClass.getSimpleName(), exception);
        }
    }

    /** Returns the registered condition class for a serialized id. */
    public Class<? extends TriggerCondition> getConditionClass(String id) {
        return this.conditions.get(id);
    }

    /** Returns all registered condition implementation classes. */
    public Collection<Class<? extends TriggerCondition>> getRegisteredConditions() {
        return this.conditions.values();
    }

    /** Returns editor button metadata keyed by condition id. */
    public Map<String, MenuButton> getConditionButtons() {
        return this.conditionButtons;
    }

    /** Returns the editor button for a registered condition class. */
    public MenuButton getConditionButton(Class<? extends TriggerCondition> conditionClass) {
        String id = this.conditionIds.get(conditionClass);
        return id == null ? null : this.conditionButtons.get(id);
    }
}
