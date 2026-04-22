package nl.hauntedmc.dungeons.model.element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.content.instance.play.OpenInstance;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for elements that observe gameplay state and dispatch functions when their
 * conditions pass.
 *
 * <p>The trigger owns condition evaluation, retrigger rules, and the bridge from raw gameplay
 * events to {@link TriggerFireEvent} dispatch.</p>
 */
public abstract class DungeonTrigger extends DungeonElement
        implements Cloneable, ConfigurationSerializable {
    protected final String namespace;
    private String displayName;
    private boolean initialized = false;
    public TriggerCategory category = TriggerCategory.PLAYER;
    @PersistedField protected boolean allowRetrigger;
    @PersistedField protected List<TriggerCondition> conditions;
    @PersistedField protected boolean limitToRoom;
    @Nullable protected DungeonFunction function;
    protected boolean waitForConditions;
    protected boolean hasTarget = false;
    protected List<UUID> playersFound;
    protected boolean triggered;
    private PlayerHotbarMenu conditionsMenu;
    private BukkitRunnable conditionWaiter;
    protected List<UUID> playersTriggered = new ArrayList<>();

    /**
     * Creates a trigger from persisted configuration data.
     */
    public DungeonTrigger(String displayName, Map<String, Object> config) {
        super(config);
        this.namespace = displayName;
        if (this.conditions == null) {
            this.conditions = new ArrayList<>();
        }

        this.playersFound = new ArrayList<>();
        this.displayName = this.namespace;
    }

    /**
     * Creates a new trigger with no persisted state.
     */
    public DungeonTrigger(String displayName) {
        this.namespace = displayName;
        this.conditions = new ArrayList<>();
        this.playersFound = new ArrayList<>();
        this.displayName = this.namespace;
    }

    /**
     * Performs one-time persisted-field and condition initialization.
     */
    @Override
    public void initialize() {
        if (!this.initialized) {
            if (this.function != null) {
                this.setAllowRetrigger(this.function.isAllowRetriggerByDefault());
            }

            this.initializeFields();
            this.initializeConditions();
            this.initializeMenu();
            this.initializeConditionsMenu();
            this.initialized = true;
        }
    }

    @Override
        protected final void initializeAdditionalFields() {
        for (TriggerCondition condition : this.conditions) {
            condition.initializeFields();
        }
    }

        public void onEnable() {}

        public void onDisable() {}

    /**
     * Enables the trigger using the bound function's instance.
     */
    public void enable(@NotNull DungeonFunction function) {
        this.enable(function, function.getInstance());
    }

    /**
     * Enables the trigger for the supplied function and instance and registers all conditions.
     */
    public void enable(@Nullable DungeonFunction function, DungeonInstance instance) {
        if (!this.enabled) {
            this.enabled = true;
            this.instance = instance;
            this.register();
            if (function != null) {
                this.function = function;
                this.location = function.location;
            }

            for (TriggerCondition condition : this.conditions) {
                condition.enable(this);
            }

            this.onEnable();
        }
    }

    /**
     * Disables the trigger, its condition listeners, and any pending condition wait task.
     */
    @Override
    public final void disable() {
        for (TriggerCondition condition : this.conditions) {
            condition.disable();
        }

        if (this.conditionWaiter != null && !this.conditionWaiter.isCancelled()) {
            this.conditionWaiter.cancel();
            this.conditionWaiter = null;
        }

        this.unregister();
        this.onDisable();
        super.disable();
    }

        public final void register() {
        this.instance.registerTriggerListener(this);
    }

        public final void unregister() {
        this.instance.unregisterTriggerListener(this);
    }

    /**
     * Initializes all configured conditions after persisted fields have been hydrated.
     */
    public final void initializeConditions() {
        for (TriggerCondition condition : this.conditions) {
            condition.setTrigger(this);
            condition.initialize();
        }
    }

    /**
     * Returns whether every configured condition currently passes for the given trigger event.
     */
    public final boolean checkConditions(TriggerFireEvent event) {
        for (TriggerCondition condition : this.conditions) {
            final boolean check;
            try {
                if (!condition.isInverted()) {
                    check = condition.check(event);
                } else {
                    check = !condition.check(event);
                }
            } catch (Exception exception) {
                this.logger()
                        .error(
                                "Condition '{}' failed on trigger '{}' in dungeon '{}' at {}.",
                                condition.getClass().getSimpleName(),
                                this.getClass().getSimpleName(),
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                exception);
                return false;
            }

            if (!check) {
                return false;
            }
        }

        return true;
    }

        public void addCondition(TriggerCondition condition) {
        this.conditions.add(condition);
    }

        public void removeCondition(TriggerCondition condition) {
        this.conditions.remove(condition);
    }

        public void onTrigger(TriggerFireEvent event) {}

        protected final boolean hasTrackedTarget(DungeonPlayerSession player) {
        if (player == null) {
            return false;
        }

        for (DungeonPlayerSession target : this.resolveTrackedTargets(player)) {
            if (this.playersFound.contains(target.getPlayer().getUniqueId())) {
                return true;
            }
        }

        return false;
    }

        protected final void rememberTriggeredTargets(DungeonPlayerSession player) {
        for (DungeonPlayerSession target : this.resolveTrackedTargets(player)) {
            this.playersFound.add(target.getPlayer().getUniqueId());
        }
    }

        protected final boolean hasOpenlyTriggeredTarget(DungeonPlayerSession player) {
        if (player == null) {
            return false;
        }

        for (DungeonPlayerSession target : this.resolveTrackedTargets(player)) {
            if (this.playersTriggered.contains(target.getPlayer().getUniqueId())) {
                return true;
            }
        }

        return false;
    }

        protected final void rememberOpenlyTriggeredTargets(DungeonPlayerSession player) {
        for (DungeonPlayerSession target : this.resolveTrackedTargets(player)) {
            this.playersTriggered.add(target.getPlayer().getUniqueId());
        }
    }

        private List<DungeonPlayerSession> resolveTrackedTargets(DungeonPlayerSession player) {
        if (player == null) {
            return new ArrayList<>();
        }

        if (this.function != null
                && this.function.getTargetType() == FunctionTargetType.TEAM
                && this.runtime() != null) {
            return this.runtime().teamService().getActiveMembers(player, this.instance);
        }

        return List.of(player);
    }

        public final void trigger() {
        this.trigger(null);
    }

    /**
     * Fires the trigger on behalf of a player session, respecting retrigger rules.
     */
    public void trigger(DungeonPlayerSession playerSession) {
        this.trigger(playerSession, !this.allowRetrigger);
    }

        public final void trigger(DungeonPlayerSession playerSession, boolean disable) {
        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance != null) {
            this.triggered = true;
            final TriggerFireEvent event;
            if (playerSession != null && playerSession.getInstance() != null) {
                event = new TriggerFireEvent(playerSession, this);
            } else {
                event = new TriggerFireEvent(instance, this);
            }

            if (instance instanceof OpenInstance) {
                if (playerSession != null) {
                    if (this.hasOpenlyTriggeredTarget(playerSession)) {
                        return;
                    }

                    if (disable) {
                        this.rememberOpenlyTriggeredTargets(playerSession);
                    }
                }

                disable = false;
            }

            final boolean finalDisable = disable;
            this.conditionWaiter =
                                        new BukkitRunnable() {
                                                public void run() {
                            try {
                                // Waiting triggers reuse the same event object so conditions and downstream
                                // listeners observe a consistent cause while the polling task retries.
                                if (DungeonTrigger.this.checkConditions(event)) {
                                    Bukkit.getPluginManager().callEvent(event);
                                    if (!event.isCancelled()) {
                                        if (DungeonTrigger.this.function != null) {
                                            DungeonTrigger.this.function.execute(event);
                                        }

                                        DungeonTrigger.this.onTrigger(event);
                                        if (finalDisable) {
                                            this.cancel();
                                            DungeonTrigger.this.disable();
                                        }
                                    }
                                }
                            } catch (Exception exception) {
                                this.cancel();
                                DungeonTrigger.this
                                        .logger()
                                        .error(
                                                "Trigger '{}' failed in dungeon '{}' at {}.",
                                                DungeonTrigger.this.getClass().getSimpleName(),
                                                DungeonTrigger.this.dungeonNameForLogs(),
                                                DungeonTrigger.this.locationForLogs(),
                                                exception);
                            }
                        }
                    };
            if (!this.allowRetrigger && this.waitForConditions) {
                this.conditionWaiter.runTaskTimer(this.plugin(), 0L, 20L);
            } else {
                this.conditionWaiter.runTask(this.plugin());
            }
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
                        DungeonPlayerSession playerSession = DungeonTrigger.this.playerSession(player);
                        playerSession.setActiveTrigger(null);
                        playerSession.restorePreviousHotbar();
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.COMPARATOR);
                        this.button.setDisplayName("&a&lTrigger Conditions");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = DungeonTrigger.this.playerSession(player);
                        playerSession.showHotbar(DungeonTrigger.this.conditionsMenu, true);
                    }
                });
        this.buildHotbarMenu();
    }

        public void addRoomLimitToggleButton() {
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    @Override
                                        public void buildButton() {
                        BranchingEditableInstance instance =
                                DungeonTrigger.this.instance.as(BranchingEditableInstance.class);
                        if (instance == null) {
                            this.button = null;
                        } else {
                            this.button = new MenuButton(Material.STRUCTURE_BLOCK);
                            this.button.setDisplayName("&d&lLimit to Room");
                            this.button.setEnchanted(DungeonTrigger.this.limitToRoom);
                        }
                    }

                    @Override
                                        public void onSelect(Player player) {
                        if (!DungeonTrigger.this.limitToRoom) {
                            LangUtils.sendMessage(player, "editor.trigger.same-room-required");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.same-room-not-required");
                        }

                        DungeonTrigger.this.limitToRoom = !DungeonTrigger.this.limitToRoom;
                    }
                });
    }

        public void initializeConditionsMenu() {
        this.conditionsMenu = PlayerHotbarMenu.createMenu();
        this.conditionsMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
                        this.button.setDisplayName("&c&lBACK");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = DungeonTrigger.this.playerSession(player);
                        playerSession.restorePreviousHotbar();
                    }
                });
        this.conditionsMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.COMMAND_BLOCK);
                        this.button.setDisplayName("&a&lAdd Condition");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        if (DungeonTrigger.this.conditions.size() >= 54) {
                            LangUtils.sendMessage(player, "editor.trigger.max-conditions-reached");
                        } else {
                            DungeonTrigger.this.guiService().openGui(player, "conditionsmenu");
                        }
                    }
                });
        this.conditionsMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
                        this.button.setDisplayName("&e&lEdit Condition");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonTrigger.this.guiService().openGui(player, "editcondition");
                    }
                });
        this.conditionsMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.BARRIER);
                        this.button.setDisplayName("&c&lRemove Condition");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonTrigger.this.guiService().openGui(player, "removecondition");
                    }
                });
    }

        public void setDisplayName(String displayName) {
        this.displayName = displayName;
        if (this.instance != null) {
            EditableInstance instance = this.instance.asEditInstance();
            if (instance != null) {
                instance.updateLabel(this.function);
            }
        }
    }

        public DungeonTrigger clone() {
        try {
            DungeonTrigger clone = (DungeonTrigger) super.clone();
            List<TriggerCondition> newConditions = new ArrayList<>();

            for (TriggerCondition oldCondition : this.conditions) {
                TriggerCondition clonedCondition = oldCondition.clone();
                clonedCondition.location = clone.location;
                clonedCondition.setTrigger(clone);
                newConditions.add(clonedCondition);
            }

            clone.conditions = newConditions;
            clone.playersFound = new ArrayList<>();
            clone.playersTriggered = new ArrayList<>();
            clone.initializeMenu();
            clone.initializeConditionsMenu();
            return clone;
        } catch (CloneNotSupportedException exception) {
            this.logger()
                    .error("Failed to clone trigger '{}'.", this.getClass().getSimpleName(), exception);
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

        public TriggerCategory getCategory() {
        return this.category;
    }

        public void setCategory(TriggerCategory category) {
        this.category = category;
    }

        public boolean isAllowRetrigger() {
        return this.allowRetrigger;
    }

        public void setAllowRetrigger(boolean allowRetrigger) {
        this.allowRetrigger = allowRetrigger;
    }

        public List<TriggerCondition> getConditions() {
        return this.conditions;
    }

        public boolean isLimitToRoom() {
        return this.limitToRoom;
    }

    @Nullable public DungeonFunction getFunction() {
        return this.function;
    }

        public void setFunction(@Nullable DungeonFunction function) {
        this.function = function;
    }

        public boolean isWaitForConditions() {
        return this.waitForConditions;
    }

        public boolean isHasTarget() {
        return this.hasTarget;
    }

        public void setHasTarget(boolean hasTarget) {
        this.hasTarget = hasTarget;
    }

        public List<UUID> getPlayersFound() {
        return this.playersFound;
    }

        public boolean isTriggered() {
        return this.triggered;
    }

        public PlayerHotbarMenu getConditionsMenu() {
        return this.conditionsMenu;
    }

        public List<UUID> getPlayersTriggered() {
        return this.playersTriggered;
    }
}
