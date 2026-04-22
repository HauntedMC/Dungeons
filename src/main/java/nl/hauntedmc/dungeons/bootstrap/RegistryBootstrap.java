package nl.hauntedmc.dungeons.bootstrap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.config.ConfigSerializableModel;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.content.dungeon.OpenDungeon;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.reward.LootCooldown;
import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.content.reward.LootTableItem;
import nl.hauntedmc.dungeons.content.reward.PlayerLootData;
import nl.hauntedmc.dungeons.content.variable.VariableEditMode;
import nl.hauntedmc.dungeons.generation.layout.BranchingLayout;
import nl.hauntedmc.dungeons.generation.layout.ConnectorExpansionLayout;
import nl.hauntedmc.dungeons.generation.layout.Layout;
import nl.hauntedmc.dungeons.listener.instance.EditListener;
import nl.hauntedmc.dungeons.listener.instance.InstanceListener;
import nl.hauntedmc.dungeons.listener.instance.PlayListener;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.registry.DungeonTypeRegistry;
import nl.hauntedmc.dungeons.registry.LayoutRegistry;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;

/**
 * Registers runtime types and serialization metadata that must exist before dungeon content is
 * loaded.
 */
final class RegistryBootstrap {
    private final DungeonsRuntime runtime;

    /**
     * Creates the registry bootstrap stage.
     */
    RegistryBootstrap(DungeonsRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Registers Bukkit configuration serializers that are safe to register before managers exist.
     */
    void registerConfigurationSerializers() {
        TypeMetadataSupport.registerConfigurationSerializable(PlayerLootData.class);
        TypeMetadataSupport.registerConfigurationSerializable(LootCooldown.class);
        TypeMetadataSupport.registerConfigurationSerializable(FunctionTargetType.class);
        TypeMetadataSupport.registerConfigurationSerializable(LootTable.class);
        TypeMetadataSupport.registerConfigurationSerializable(LootTableItem.class);
        TypeMetadataSupport.registerConfigurationSerializable(VariableEditMode.class);
    }

    /**
     * Registers custom serialization support that depends on the plugin instance.
     */
    void registerElementSerialization() {
        ConfigSerializableModel.register(this.runtime.environment().plugin());
    }

    /**
     * Registers dungeon/runtime types that later dungeon loading depends on.
     */
    void registerRuntimeTypes() {
        this.registerInstanceListener(PlayListener.class);
        this.registerInstanceListener(EditListener.class);
        this.registerDungeonType(StaticDungeon.class, "static");
        this.registerDungeonType(OpenDungeon.class, "open");
        this.registerDungeonType(BranchingDungeon.class, "branching");
        this.registerLayout(
                ConnectorExpansionLayout.class, "minecrafty", "minecraft", "vanilla", "random");
        this.registerLayout(BranchingLayout.class, "branching", "branch", "linear");
        this.registerFunctions("nl.hauntedmc.dungeons.content.function");
        this.registerTriggers("nl.hauntedmc.dungeons.content.trigger");
        this.registerConditions("nl.hauntedmc.dungeons.content.condition");
    }

    /**
     * Registers a concrete dungeon type name and its aliases.
     */
    private <T extends DungeonDefinition> void registerDungeonType(
            Class<T> type, String name, String... aliases) {
        DungeonTypeRegistry.register(type, name, aliases);
    }

    /**
     * Registers a concrete layout type name and its aliases.
     */
    private <T extends Layout> void registerLayout(Class<T> type, String name, String... aliases) {
        LayoutRegistry.register(type, name, aliases);
    }

    /**
     * Reflectively multiplexes Bukkit events to the matching per-instance listener class.
     */
    private <T extends InstanceListener> void registerInstanceListener(Class<T> type) {
        List<Method> eventMethods = MethodUtils.getMethodsListWithAnnotation(type, EventHandler.class);

        for (Method method : eventMethods) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            Class<?> rawEvent = method.getParameterTypes()[0];
            if (Event.class.isAssignableFrom(rawEvent)) {
                Class<? extends Event> eventClass = rawEvent.asSubclass(Event.class);
                Bukkit.getPluginManager()
                        .registerEvent(
                                eventClass,
                                this.runtime.instanceEventHandler(),
                                handler.priority(),
                                (listener, event) -> {
                                    // Use a snapshot because instance listeners may dispose worlds
                                    // or mutate the active-instance collection while handling an event.
                                    for (var instance :
                                            new ArrayList<>(this.runtime.activeInstances().getActiveInstances())) {
                                        if (eventClass.isAssignableFrom(event.getClass())) {
                                            InstanceListener instanceListener = instance.getListener();
                                            if (instanceListener != null
                                                    && type.isAssignableFrom(instanceListener.getClass())) {
                                                try {
                                                    method.invoke(instanceListener, event);
                                                } catch (Exception exception) {
                                                    Throwable cause =
                                                            exception.getCause() == null ? exception : exception.getCause();
                                                    String instanceName =
                                                            instance.getInstanceWorld() == null
                                                                    ? "<unloaded>"
                                                                    : instance.getInstanceWorld().getName();
                                                    this.runtime
                                                            .environment()
                                                            .logger()
                                                            .error(
                                                                    "Failed to invoke instance listener method '{}#{}' for event '{}' in instance '{}'.",
                                                                    type.getSimpleName(),
                                                                    method.getName(),
                                                                    eventClass.getSimpleName(),
                                                                    instanceName,
                                                                    cause);
                                                }
                                            }
                                        }
                                    }
                                },
                                this.runtime.environment().plugin());
            }
        }
    }

    /**
     * Registers a discovered function type with both the runtime registry and config serialization.
     */
    private <T extends DungeonFunction> void registerFunction(Class<T> function) {
        this.runtime.functionRegistry().register(function);
        TypeMetadataSupport.registerConfigurationSerializable(function);
    }

    /**
     * Registers a discovered trigger type with both the runtime registry and config serialization.
     */
    private <T extends DungeonTrigger> void registerTrigger(Class<T> trigger) {
        this.runtime.triggerRegistry().register(trigger);
        TypeMetadataSupport.registerConfigurationSerializable(trigger);
    }

    /**
     * Registers a discovered condition type with both the runtime registry and config
     * serialization.
     */
    private <T extends TriggerCondition> void registerCondition(Class<T> condition) {
        this.runtime.conditionRegistry().register(condition);
        TypeMetadataSupport.registerConfigurationSerializable(condition);
    }

    /**
     * Discovers and registers all auto-registered function types in the supplied package.
     */
    private void registerFunctions(String functionsPackage) {
        for (Class<? extends DungeonFunction> function :
                TypeMetadataSupport.discoverAutoRegisteredTypes(functionsPackage, DungeonFunction.class)) {
            this.registerFunction(function);
        }
    }

    /**
     * Discovers and registers all auto-registered trigger types in the supplied package.
     */
    private void registerTriggers(String triggersPackage) {
        for (Class<? extends DungeonTrigger> trigger :
                TypeMetadataSupport.discoverAutoRegisteredTypes(triggersPackage, DungeonTrigger.class)) {
            this.registerTrigger(trigger);
        }
    }

    /**
     * Discovers and registers all auto-registered condition types in the supplied package.
     */
    private void registerConditions(String conditionsPackage) {
        for (Class<? extends TriggerCondition> condition :
                TypeMetadataSupport.discoverAutoRegisteredTypes(
                        conditionsPackage, TriggerCondition.class)) {
            this.registerCondition(condition);
        }
    }
}
