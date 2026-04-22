package nl.hauntedmc.dungeons.model.element;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import nl.hauntedmc.dungeons.annotation.EditorHidden;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Base class for executable dungeon elements.
 *
 * <p>A function is responsible for resolving its targets, coordinating with its trigger, and
 * exposing editor controls. Concrete subclasses provide only the actual gameplay effect.</p>
 */
public abstract class DungeonFunction extends DungeonElement
        implements Cloneable, ConfigurationSerializable {
    protected final String namespace;
    private String displayName;
    private boolean initialized = false;
    protected String colour;
    private boolean requiresTrigger = true;
    private boolean allowRetriggerByDefault = false;
    public FunctionCategory category = FunctionCategory.PLAYER;
    public boolean requiresTarget = false;
    private boolean allowChangingTargetType = true;
    @PersistedField protected FunctionTargetType targetType = FunctionTargetType.NONE;
    private int targetIndex = 0;
    @PersistedField @Nullable protected DungeonTrigger trigger;
    @PersistedField @EditorHidden protected Location location;
    @Nullable protected DungeonFunction parentFunction;

    /**
     * Creates a function from persisted configuration.
     */
    public DungeonFunction(String namespace, Map<String, Object> config) {
        super(config);
        this.namespace = namespace;
        this.displayName = namespace;
    }

    /**
     * Creates a new function without persisted state.
     */
    public DungeonFunction(String namespace) {
        this.namespace = namespace;
        this.displayName = namespace;
    }

    /**
     * Performs one-time initialization after persisted fields have been loaded.
     */
    @Override
    public void initialize() {
        if (!this.initialized) {
            this.initializeFields();
            this.targetIndex = this.targetType.getIndex();
            this.initializeMenu();
            if (this.trigger != null) {
                this.trigger.setFunction(this);
                this.trigger.initialize();
            }

            this.verifyTargetType();
            this.initialized = true;
        }
    }

    @Override
        protected final void initializeAdditionalFields() {
        if (this.trigger != null) {
            this.trigger.setFunction(this);
            this.trigger.initializeFields();
        }
    }

    @Override
        public void setLocation(Location loc) {
        this.location = loc;
        super.location = loc;
    }

        public void onEnable() {}

        public void onDisable() {}

    /**
     * Enables the function for the supplied instance and location and registers its trigger
     * listeners.
     */
    public final void enable(DungeonInstance instance, Location loc) {
        if (!this.enabled) {
            this.enabled = true;
            this.instance = instance;
            this.location = loc;
            this.register();
            if (this.trigger != null) {
                this.trigger.enable(this);
            }

            this.onEnable();
        }
    }

    /**
     * Disables the function, its trigger, and any registered listeners.
     */
    @Override
    public final void disable() {
        super.disable();
        if (this.trigger != null) {
            this.trigger.disable();
        }

        this.unregister();
        this.onDisable();
    }

    /**
     * Binds a trigger to this function and eagerly initializes the trigger editor state.
     */
    public void setTrigger(DungeonTrigger trigger) {
        this.trigger = trigger;
        if (trigger != null) {
            this.trigger.setFunction(this);
            this.trigger.initialize();
        }
    }

        public void setCategory(FunctionCategory category) {
        this.category = category;
        this.setColour(category.getColor());
    }

        public final void register() {
        this.instance.registerFunctionListener(this);
    }

        public final void unregister() {
        this.instance.unregisterFunctionListener(this);
    }

        public abstract void runFunction(
            TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets);

    /**
     * Optional pre-execution hook for validation or state mutation before targets are resolved.
     */
    public void onExecute(TriggerFireEvent triggerEvent) {}

    /**
     * Validates the incoming trigger event and then runs the function against its resolved targets.
     */
    public final void execute(TriggerFireEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (this.trigger == null) {
            this.logger()
                    .warn(
                            "Skipping function '{}' in dungeon '{}' at {} because it has no bound trigger.",
                            this.getClass().getSimpleName(),
                            this.dungeonNameForLogs(),
                            this.locationForLogs());
            return;
        }

        DungeonTrigger eventTrigger = event.getTrigger();
        if (eventTrigger == null || !eventTrigger.getClass().equals(this.trigger.getClass())) {
            return;
        }

        try {
            this.onExecute(event);
        } catch (Exception exception) {
            this.logger()
                    .error(
                            "Function '{}' failed during pre-execution in dungeon '{}' at {}.",
                            this.getClass().getSimpleName(),
                            this.dungeonNameForLogs(),
                            this.locationForLogs(),
                            exception);
            return;
        }

        // Target resolution happens after the pre-execution hook so wrapper functions can still adjust
        // target mode or other state just before dispatch.
        this.runFunctionSafely(event, this.resolveTargets(event));
    }

    /**
     * Resolves the effective player targets for the current target mode.
     */
    protected final List<DungeonPlayerSession> resolveTargets(TriggerFireEvent event) {
        LinkedHashSet<DungeonPlayerSession> targets = new LinkedHashSet<>();
        DungeonPlayerSession source = event.getPlayerSession();

        switch (this.targetType) {
            case PLAYER -> this.addTarget(targets, source);
            case TEAM -> targets.addAll(this.resolveTeamTargets(source));
            case ROOM -> this.resolveRoomTargets(targets);
            case NONE -> {}
        }

        return new ArrayList<>(targets);
    }

    /**
     * Resolves the targets for a nested function call. If this wrapper already resolved a concrete
     * target list, that explicit targeting wins. Otherwise the nested function may resolve its own
     * target mode from the original trigger event.
     */
    protected final List<DungeonPlayerSession> resolveNestedFunctionTargets(
            DungeonFunction nestedFunction,
            TriggerFireEvent event,
            List<DungeonPlayerSession> currentTargets) {
        if (!currentTargets.isEmpty() || this.targetType != FunctionTargetType.NONE) {
            return new ArrayList<>(currentTargets);
        }

        if (nestedFunction == null) {
            return new ArrayList<>();
        }

        return nestedFunction.resolveTargets(event);
    }

        private void addTarget(
            LinkedHashSet<DungeonPlayerSession> targets, @Nullable DungeonPlayerSession target) {
        if (target != null) {
            targets.add(target);
        }
    }

        private List<DungeonPlayerSession> resolveTeamTargets(@Nullable DungeonPlayerSession source) {
        if (source == null || this.runtime() == null) {
            return new ArrayList<>();
        }

        return this.runtime().teamService().getActiveMembers(source, this.instance);
    }

        private void resolveRoomTargets(LinkedHashSet<DungeonPlayerSession> targets) {
        BranchingInstance instance = this.instance.as(BranchingInstance.class);
        if (instance == null) {
            return;
        }

        InstanceRoom targetRoom = instance.getRoom(this.location);
        if (targetRoom == null) {
            return;
        }

        for (DungeonPlayerSession targetPlayer : instance.getPlayers()) {
            Player player = targetPlayer.getPlayer();
            if (player
                    .getBoundingBox()
                    .overlaps(targetRoom.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))) {
                targets.add(targetPlayer);
            }
        }
    }

        protected final void executeNestedFunction(
            DungeonFunction nestedFunction, TriggerFireEvent event, List<DungeonPlayerSession> targets) {
        if (nestedFunction == null) {
            this.logger()
                    .warn(
                            "Skipping nested function execution in '{}' for dungeon '{}' at {} because no function was configured.",
                            this.getClass().getSimpleName(),
                            this.dungeonNameForLogs(),
                            this.locationForLogs());
            return;
        }

        nestedFunction.runFunctionSafely(event, targets);
    }

        private void runFunctionSafely(TriggerFireEvent event, List<DungeonPlayerSession> targets) {
        try {
            this.runFunction(event, targets);
        } catch (Exception exception) {
            this.logger()
                    .error(
                            "Function '{}' failed in dungeon '{}' at {}.",
                            this.getClass().getSimpleName(),
                            this.dungeonNameForLogs(),
                            this.locationForLogs(),
                            exception);
        }
    }

    @Override
        public abstract MenuButton buildMenuButton();

    @Override
        public abstract void buildHotbarMenu();

    @Override
        public final void initializeMenu() {
        this.menu = PlayerHotbarMenu.createMenu();
        this.menu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
                        this.button.setDisplayName("&c&lBACK");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = DungeonFunction.this.playerSession(player);
                        playerSession.restorePreviousHotbar();
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.STICKY_PISTON);
                        String triggerName = "NONE";
                        if (DungeonFunction.this.trigger != null) {
                            triggerName = DungeonFunction.this.trigger.getNamespace();
                        }

                        this.button.setDisplayName("&a&lTrigger: " + triggerName);
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = DungeonFunction.this.playerSession(player);
                        if (DungeonFunction.this.trigger == null) {
                            LangUtils.sendMessage(player, "editor.function.no-trigger");
                        } else {
                            playerSession.showHotbar(DungeonFunction.this.trigger.getMenu());
                        }
                    }
                });
        if (this.allowChangingTargetType) {
            this.menu.addMenuItem(
                                        new MenuItem() {
                        @Override
                                                public void buildButton() {
                            this.button = new MenuButton(Material.OBSERVER);
                            String targetTypeDisplay = DungeonFunction.this.targetType.toString();
                            this.button.setDisplayName("&d&lTarget Type: " + targetTypeDisplay);
                        }

                        @Override
                                                public void onSelect(PlayerEvent event) {
                            Player player = event.getPlayer();
                            DungeonPlayerSession playerSession = DungeonFunction.this.playerSession(player);
                            DungeonFunction.this.targetIndex++;
                            DungeonFunction.this.verifyTargetType(playerSession.getInstance());
                            String targetTypeDisplay = DungeonFunction.this.targetType.getDisplay();
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.target-type-set",
                                    LangUtils.placeholder("target_type", targetTypeDisplay));
                            playerSession.showHotbar(this.menu);
                        }
                    });
        }

        this.buildHotbarMenu();
    }

        private void verifyTargetType() {
        this.verifyTargetType(null);
    }

        private void verifyTargetType(DungeonInstance instance) {
        FunctionTargetType[] values = FunctionTargetType.values();
        for (int attempts = 0; attempts < values.length; attempts++) {
            if (this.targetIndex >= values.length) {
                this.targetIndex = 0;
            }

            FunctionTargetType candidate = FunctionTargetType.intToTargetType(this.targetIndex);
            if (this.isSupportedTargetType(candidate, instance)) {
                this.targetType = candidate;
                return;
            }

            this.targetIndex++;
        }

        this.targetIndex = FunctionTargetType.NONE.getIndex();
        this.targetType = FunctionTargetType.NONE;
    }

        private boolean isSupportedTargetType(FunctionTargetType candidate, DungeonInstance instance) {
        if (this.requiresTarget && candidate == FunctionTargetType.NONE) {
            return false;
        }

        if (this.trigger != null
                && !this.trigger.hasTarget
                && (candidate == FunctionTargetType.PLAYER || candidate == FunctionTargetType.TEAM)) {
            return false;
        }

        return !(instance != null
                && instance.as(BranchingEditableInstance.class) == null
                && candidate == FunctionTargetType.ROOM);
    }

    @Override
        public void setInstance(DungeonInstance instance) {
        this.instance = instance;
        if (this.trigger != null) {
            this.trigger.setInstance(this.instance);
        }
    }

        public void setDisplayName(String displayName) {
        this.displayName = displayName;
        if (this.instance != null) {
            if (this.instance instanceof EditableInstance) {
                ((EditableInstance) this.instance).updateLabel(this);
            }
        }
    }

        public DungeonFunction clone() {
        try {
            DungeonFunction clone = (DungeonFunction) super.clone();
            clone.location = this.location == null ? null : this.location.clone();
            if (this.trigger != null) {
                clone.trigger = this.trigger.clone();
                clone.trigger.location = clone.location;
            }

            clone.initializeMenu();
            return clone;
        } catch (CloneNotSupportedException exception) {
            this.logger()
                    .error("Failed to clone function '{}'.", this.getClass().getSimpleName(), exception);
            return null;
        }
    }

        public String getNamespace() {
        return this.namespace;
    }

        public String getDisplayName() {
        return this.displayName;
    }

        public boolean isInitialized() {
        return this.initialized;
    }

        public String getColour() {
        return this.colour;
    }

        protected void setColour(String colour) {
        this.colour = colour;
    }

        public boolean isRequiresTrigger() {
        return this.requiresTrigger;
    }

        public void setRequiresTrigger(boolean requiresTrigger) {
        this.requiresTrigger = requiresTrigger;
    }

        public boolean isAllowRetriggerByDefault() {
        return this.allowRetriggerByDefault;
    }

        public void setAllowRetriggerByDefault(boolean allowRetriggerByDefault) {
        this.allowRetriggerByDefault = allowRetriggerByDefault;
    }

        public FunctionCategory getCategory() {
        return this.category;
    }

        public boolean isRequiresTarget() {
        return this.requiresTarget;
    }

        public void setRequiresTarget(boolean requiresTarget) {
        this.requiresTarget = requiresTarget;
    }

        public boolean isAllowChangingTargetType() {
        return this.allowChangingTargetType;
    }

        public void setAllowChangingTargetType(boolean allowChangingTargetType) {
        this.allowChangingTargetType = allowChangingTargetType;
    }

        public FunctionTargetType getTargetType() {
        return this.targetType;
    }

        public void setTargetType(FunctionTargetType targetType) {
        this.targetType = targetType;
    }

    @Nullable public DungeonTrigger getTrigger() {
        return this.trigger;
    }

    @Override
        public Location getLocation() {
        return this.location;
    }

        public void setParentFunction(@Nullable DungeonFunction parentFunction) {
        this.parentFunction = parentFunction;
    }

    @Nullable public DungeonFunction getParentFunction() {
        return this.parentFunction;
    }
}
