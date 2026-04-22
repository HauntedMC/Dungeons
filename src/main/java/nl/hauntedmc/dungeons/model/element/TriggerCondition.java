package nl.hauntedmc.dungeons.model.element;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Base contract for conditional checks attached to a {@link DungeonTrigger}.
 */
public abstract class TriggerCondition extends DungeonElement
        implements Cloneable, ConfigurationSerializable {
    protected final String namespace;
    private boolean initialized = false;
    @PersistedField protected boolean inverted = false;
    protected DungeonTrigger trigger;

    /**
     * Creates a condition from persisted configuration data.
     */
    public TriggerCondition(String namespace, Map<String, Object> config) {
        super(config);
        this.namespace = namespace;
    }

    /**
     * Creates a new condition with no persisted state.
     */
    public TriggerCondition(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Performs one-time field hydration and menu initialization.
     */
    @Override
    public final void initialize() {
        if (!this.initialized) {
            this.initializeFields();
            this.initializeMenu();
            this.initialized = true;
        }
    }

    /**
     * Evaluates this condition against a trigger dispatch context.
     */
    public abstract boolean check(TriggerFireEvent triggerEvent);

    /**
     * Hook executed when the condition is enabled for a live trigger.
     */
    public void onEnable() {}

    /**
     * Hook executed when the condition is disabled.
     */
    public void onDisable() {}

    /**
     * Enables this condition for one concrete trigger instance.
     */
    public final void enable(DungeonTrigger trigger) {
        if (!this.enabled) {
            this.enabled = true;
            this.trigger = trigger;
            this.instance = trigger.getInstance();
            this.location = trigger.getLocation();
            this.onEnable();
        }
    }

    /**
     * Disables condition-specific resources and shared listeners.
     */
    @Override
    public final void disable() {
        super.disable();
        this.onDisable();
    }

    /**
     * Builds the menu button used in condition selection lists.
     */
    @Override
    public abstract MenuButton buildMenuButton();

    /**
     * Adds condition-specific controls to the hotbar menu.
     */
    @Override
    public abstract void buildHotbarMenu();

    /**
     * Builds the shared condition hotbar with back and invert toggles.
     */
    @Override
    protected final void initializeMenu() {
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
                        DungeonPlayerSession playerSession = TriggerCondition.this.playerSession(player);
                        playerSession.restorePreviousHotbar();
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lToggle Condition Requirement");
                        this.button.setEnchanted(!TriggerCondition.this.inverted);
                    }

                    @Override
                                        public void onSelect(Player player) {
                        if (!TriggerCondition.this.inverted) {
                            LangUtils.sendMessage(player, "editor.condition.negated");
                        } else {
                            LangUtils.sendMessage(player, "editor.condition.normal");
                        }

                        TriggerCondition.this.inverted = !TriggerCondition.this.inverted;
                    }
                });
        this.buildHotbarMenu();
    }

    /**
     * Clones the condition and rebuilds clone-owned menu state.
     */
    public TriggerCondition clone() {
        try {
            TriggerCondition newCondition = (TriggerCondition) super.clone();
            newCondition.initializeMenu();
            return newCondition;
        } catch (CloneNotSupportedException exception) {
            this.logger()
                    .error(
                            "Failed to clone trigger condition '{}'.",
                            this.getClass().getSimpleName(),
                            exception);
            return null;
        }
    }

    /**
     * Returns the serialization namespace of this condition.
     */
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Returns whether one-time initialization has completed.
     */
    public boolean isInitialized() {
        return this.initialized;
    }

    /**
     * Returns whether this condition result should be inverted.
     */
    public boolean isInverted() {
        return this.inverted;
    }

    /**
     * Returns the trigger currently owning this condition.
     */
    public DungeonTrigger getTrigger() {
        return this.trigger;
    }

    /**
     * Updates the trigger owning this condition.
     */
    public void setTrigger(DungeonTrigger trigger) {
        this.trigger = trigger;
    }
}
