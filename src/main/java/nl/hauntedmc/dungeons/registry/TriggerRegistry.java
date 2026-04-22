package nl.hauntedmc.dungeons.registry;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.listener.EventRelayListener;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;

/**
 * Registry for discoverable {@link DungeonTrigger} implementations.
 *
 * <p>This registry builds editor metadata and wires trigger event handlers through a shared relay
 * listener so each active instance dispatches only to its own trigger copies.</p>
 */
public final class TriggerRegistry {
    private final DungeonsPlugin plugin;
    private final ActiveInstanceRegistry activeInstanceManager;
    private final EventRelayListener elementEventHandler;
    private final Map<String, Class<? extends DungeonTrigger>> triggers = new LinkedHashMap<>();
    private final Map<Class<? extends DungeonTrigger>, String> triggerIds = new LinkedHashMap<>();
    private final Map<String, MenuButton> triggerButtons = new LinkedHashMap<>();
    private final Map<TriggerCategory, Map<String, MenuButton>> buttonsByCategory = new LinkedHashMap<>();

    /** Creates a trigger registry bound to plugin/runtime event dispatch infrastructure. */
    public TriggerRegistry(DungeonsPlugin plugin, ActiveInstanceRegistry activeInstanceManager,
            EventRelayListener elementEventHandler) {
        this.plugin = plugin;
        this.activeInstanceManager = activeInstanceManager;
        this.elementEventHandler = elementEventHandler;
        for (TriggerCategory category : TriggerCategory.values()) {
            this.buttonsByCategory.put(category, new LinkedHashMap<>());
        }
    }

    /**
     * Registers a trigger class, editor metadata, and reflective event handlers.
     */
    public <T extends DungeonTrigger> void register(Class<T> triggerClass) {
        String triggerId = TypeMetadataSupport.requiredId(triggerClass);
        try {
            this.triggers.put(triggerId, triggerClass);
            this.triggerIds.put(triggerClass, triggerId);

            Method menuButtonMethod = triggerClass.getDeclaredMethod("buildMenuButton");
            menuButtonMethod.setAccessible(true);
            DungeonTrigger trigger = triggerClass.getDeclaredConstructor().newInstance();
            MenuButton button = (MenuButton) menuButtonMethod.invoke(trigger);
            if (button == null) {
                this.plugin.getSLF4JLogger().warn(
                        "Trigger '{}' does not expose a menu button and will not appear in the editor.",
                        triggerClass.getSimpleName());
                return;
            }

            this.triggerButtons.put(triggerId, button);
            Field categoryField = triggerClass.getField("category");
            categoryField.setAccessible(true);
            TriggerCategory category = (TriggerCategory) categoryField.get(trigger);
            if (category != null) {
                this.buttonsByCategory.get(category).put(triggerId, button);
            }

            List<Method> eventMethods = MethodUtils.getMethodsListWithAnnotation(triggerClass, EventHandler.class);
            for (Method method : eventMethods) {
                EventHandler handler = method.getAnnotation(EventHandler.class);
                Class<?> rawEvent = method.getParameterTypes()[0];
                if (Event.class.isAssignableFrom(rawEvent)) {
                    Class<? extends Event> eventClass = rawEvent.asSubclass(Event.class);
                    Bukkit.getPluginManager().registerEvent(eventClass, this.elementEventHandler, handler.priority(),
                            (listener, event) -> {
                                for (DungeonInstance instance : this.activeInstanceManager.getActiveInstances()) {
                                    List<DungeonTrigger> elements = instance.getTriggerListeners(triggerClass);
                                    if (elements != null) {
                                        for (DungeonTrigger element : new ArrayList<>(elements)) {
                                            if (eventClass.isAssignableFrom(event.getClass())) {
                                                try {
                                                    method.invoke(element, event);
                                                } catch (Exception exception) {
                                                    Throwable cause = exception.getCause() == null
                                                            ? exception
                                                            : exception.getCause();
                                                    String instanceName = instance.getInstanceWorld() == null
                                                            ? "<unloaded>"
                                                            : instance.getInstanceWorld().getName();
                                                    this.plugin.getSLF4JLogger().error(
                                                            "Trigger listener '{}#{}' failed for event '{}' in instance '{}'.",
                                                            triggerClass.getSimpleName(), method.getName(),
                                                            eventClass.getSimpleName(), instanceName, cause);
                                                }
                                            }
                                        }
                                    }
                                }
                            }, this.plugin);
                }
            }
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException
                | IllegalAccessException exception) {
            this.plugin.getSLF4JLogger().error("Trigger '{}' has an invalid buildMenuButton declaration.",
                    triggerClass.getSimpleName(), exception);
        } catch (NoSuchFieldException exception) {
            this.plugin.getSLF4JLogger().error("Trigger '{}' is missing the required 'category' field.",
                    triggerClass.getSimpleName(), exception);
        }
    }

    /** Returns the registered trigger class for a serialized id. */
    public Class<? extends DungeonTrigger> getTriggerClass(String id) {
        return this.triggers.get(id);
    }

    /** Returns all registered trigger implementation classes. */
    public Collection<Class<? extends DungeonTrigger>> getRegisteredTriggers() {
        return this.triggers.values();
    }

    /** Returns editor button metadata keyed by trigger id. */
    public Map<String, MenuButton> getTriggerButtons() {
        return this.triggerButtons;
    }

    /** Returns editor button metadata grouped by trigger category. */
    public Map<TriggerCategory, Map<String, MenuButton>> getButtonsByCategory() {
        return this.buttonsByCategory;
    }

    /** Returns the editor button for a registered trigger class. */
    public MenuButton getTriggerButton(Class<? extends DungeonTrigger> triggerClass) {
        String id = this.triggerIds.get(triggerClass);
        return id == null ? null : this.triggerButtons.get(id);
    }
}
