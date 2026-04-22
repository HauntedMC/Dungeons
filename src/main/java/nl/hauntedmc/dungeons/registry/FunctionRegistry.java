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
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;

/**
 * Registry for discoverable {@link DungeonFunction} implementations.
 *
 * <p>Besides type lookup and editor button metadata, this registry wires annotated event handler
 * methods onto a shared relay listener and dispatches them to active instance-owned function
 * clones.</p>
 */
public final class FunctionRegistry {
    private final DungeonsPlugin plugin;
    private final ActiveInstanceRegistry activeInstanceManager;
    private final EventRelayListener elementEventHandler;
    private final Map<String, Class<? extends DungeonFunction>> functions = new LinkedHashMap<>();
    private final Map<Class<? extends DungeonFunction>, String> functionIds = new LinkedHashMap<>();
    private final Map<String, MenuButton> functionButtons = new LinkedHashMap<>();
    private final Map<FunctionCategory, Map<String, MenuButton>> buttonsByCategory = new LinkedHashMap<>();

    /** Creates a function registry bound to plugin/runtime event dispatch infrastructure. */
    public FunctionRegistry(DungeonsPlugin plugin, ActiveInstanceRegistry activeInstanceManager,
            EventRelayListener elementEventHandler) {
        this.plugin = plugin;
        this.activeInstanceManager = activeInstanceManager;
        this.elementEventHandler = elementEventHandler;
        for (FunctionCategory category : FunctionCategory.values()) {
            this.buttonsByCategory.put(category, new LinkedHashMap<>());
        }
    }

    /**
     * Registers a function class, editor metadata, and reflective event handlers.
     */
    public <T extends DungeonFunction> void register(Class<T> functionClass) {
        String functionId = TypeMetadataSupport.requiredId(functionClass);
        try {
            this.functions.put(functionId, functionClass);
            this.functionIds.put(functionClass, functionId);

            Method menuButtonMethod = functionClass.getDeclaredMethod("buildMenuButton");
            menuButtonMethod.setAccessible(true);
            DungeonFunction function = functionClass.getDeclaredConstructor().newInstance();
            MenuButton button = (MenuButton) menuButtonMethod.invoke(function);
            if (button == null) {
                this.plugin.getSLF4JLogger().warn(
                        "Function '{}' does not expose a menu button and will not appear in the editor.",
                        functionClass.getSimpleName());
                return;
            }

            this.functionButtons.put(functionId, button);
            Field categoryField = functionClass.getField("category");
            categoryField.setAccessible(true);
            FunctionCategory category = (FunctionCategory) categoryField.get(function);
            if (category != null) {
                this.buttonsByCategory.get(category).put(functionId, button);
            }

            List<Method> eventMethods = MethodUtils.getMethodsListWithAnnotation(functionClass, EventHandler.class);
            for (Method method : eventMethods) {
                EventHandler handler = method.getAnnotation(EventHandler.class);
                Class<?> rawEvent = method.getParameterTypes()[0];
                if (Event.class.isAssignableFrom(rawEvent)) {
                    Class<? extends Event> eventClass = rawEvent.asSubclass(Event.class);
                    Bukkit.getPluginManager().registerEvent(eventClass, this.elementEventHandler, handler.priority(),
                            (listener, event) -> {
                                for (DungeonInstance instance : this.activeInstanceManager.getActiveInstances()) {
                                    List<DungeonFunction> elements = instance.getFunctionListeners(functionClass);
                                    if (elements != null) {
                                        for (DungeonFunction element : new ArrayList<>(elements)) {
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
                                                            "Function listener '{}#{}' failed for event '{}' in instance '{}'.",
                                                            functionClass.getSimpleName(), method.getName(),
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
            this.plugin.getSLF4JLogger().error("Function '{}' has an invalid buildMenuButton declaration.",
                    functionClass.getSimpleName(), exception);
        } catch (NoSuchFieldException exception) {
            this.plugin.getSLF4JLogger().error("Function '{}' is missing the required 'category' field.",
                    functionClass.getSimpleName(), exception);
        }
    }

    /** Returns the registered function class for a serialized id. */
    public Class<? extends DungeonFunction> getFunctionClass(String id) {
        return this.functions.get(id);
    }

    /** Returns all registered function implementation classes. */
    public Collection<Class<? extends DungeonFunction>> getRegisteredFunctions() {
        return this.functions.values();
    }

    /** Returns editor button metadata keyed by function id. */
    public Map<String, MenuButton> getFunctionButtons() {
        return this.functionButtons;
    }

    /** Returns editor button metadata grouped by function category. */
    public Map<FunctionCategory, Map<String, MenuButton>> getButtonsByCategory() {
        return this.buttonsByCategory;
    }

    /** Returns the editor button for a registered function class. */
    public MenuButton getFunctionButton(Class<? extends DungeonFunction> functionClass) {
        String id = this.functionIds.get(functionClass);
        return id == null ? null : this.functionButtons.get(id);
    }
}
