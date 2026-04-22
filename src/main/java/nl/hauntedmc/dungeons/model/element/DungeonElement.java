package nl.hauntedmc.dungeons.model.element;

import java.util.HashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.listener.ElementHotbarListener;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.util.metadata.PersistedFieldSupport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base type for trigger, function, and condition-style dungeon elements that can be serialized
 * into dungeon data and attached to an instance at runtime.
 *
 * <p>The class owns the shared editor listener lifecycle and persisted-field hydration so the
 * concrete subclasses can focus on their domain-specific behavior.</p>
 */
public abstract class DungeonElement implements ConfigurationSerializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DungeonElement.class);
    protected boolean enabled;
    private ElementHotbarListener editListener;
    protected final Map<String, Object> config;
    protected Location location;
    protected DungeonInstance instance;
    protected PlayerHotbarMenu menu;

    /**
     * Creates an element from persisted configuration data.
     */
    public DungeonElement(Map<String, Object> config) {
        this.config = config;
        this.editListener = new ElementHotbarListener(this);
        Bukkit.getPluginManager().registerEvents(this.editListener, this.plugin());
    }

    /**
     * Creates a new element that does not yet have serialized state.
     */
    public DungeonElement() {
        this.config = null;
        this.editListener = new ElementHotbarListener(this);
        Bukkit.getPluginManager().registerEvents(this.editListener, this.plugin());
    }

        protected final DungeonsRuntime runtime() {
        return this.instance == null ? null : this.instance.getRuntime();
    }

        protected final DungeonsPlugin plugin() {
        return this.instance == null
                ? JavaPlugin.getPlugin(DungeonsPlugin.class)
                : this.instance.getEnvironment().plugin();
    }

        protected final Logger logger() {
        return this.instance == null ? LOGGER : this.instance.getEnvironment().logger();
    }

        protected final GuiService guiService() {
        return this.runtime() == null ? null : this.runtime().guiService();
    }

        protected final nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession playerSession(
            Player player) {
        return this.runtime() == null ? null : this.runtime().playerSessions().get(player);
    }

        protected final String dungeonNameForLogs() {
        if (this.instance != null && this.instance.getDungeon() != null) {
            return this.instance.getDungeon().getWorldName();
        }

        return "<unknown>";
    }

        protected final String locationForLogs() {
        if (this.location == null) {
            return "<unknown>";
        }

        return this.location.getBlockX()
                + ","
                + this.location.getBlockY()
                + ","
                + this.location.getBlockZ();
    }

        public abstract void initialize();

    /**
     * Populates fields annotated with persisted metadata and then gives subclasses a second pass for
     * any manual initialization that depends on those values.
     */
    public final void initializeFields() {
        if (this.config != null) {
            try {
                PersistedFieldSupport.populate(
                        this,
                        this.config,
                        (metadata, rawValue) -> {
                            Object coerced = PersistedFieldSupport.coerceSimpleValue(metadata, rawValue);
                            if (coerced == PersistedFieldSupport.SKIP) {
                                this.logger()
                                        .warn(
                                                "Ignoring invalid value '{}' for field '{}' on element '{}'.",
                                                rawValue,
                                                metadata.name(),
                                                this.getClass().getSimpleName());
                            }
                            return coerced;
                        });
            } catch (IllegalAccessException | IllegalArgumentException exception) {
                this.logger()
                        .error(
                                "Failed to initialize saved fields on element '{}'.",
                                this.getClass().getSimpleName(),
                                exception);
            }

            this.initializeAdditionalFields();
        }
    }

        protected void initializeAdditionalFields() {}

    /**
     * Releases shared listeners owned by the base element implementation.
     */
    public void disable() {
        HandlerList.unregisterAll(this.editListener);
    }

    /**
     * Builds the menu button used when listing the element in editor UIs.
     */
    public abstract MenuButton buildMenuButton();

    /**
     * Populates the element-specific hotbar editor controls.
     */
    public abstract void buildHotbarMenu();

        protected abstract void initializeMenu();

    /**
     * Serializes persisted element fields back into configuration data.
     *
     * <p>Locations are cloned with a null world reference so saved data remains portable across
     * copied instance worlds.</p>
     */
    @NotNull
    public Map<String, Object> serialize() {
        try {
            return PersistedFieldSupport.serialize(
                    this,
                    (metadata, fieldValue) -> {
                        if (fieldValue instanceof Location loc) {
                            Location serializedLocation = loc.clone();
                            serializedLocation.setWorld(null);
                            return serializedLocation;
                        }

                        return fieldValue;
                    });
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            this.logger()
                    .error("Failed to serialize element '{}'.", this.getClass().getSimpleName(), exception);
        }

        return new HashMap<>();
    }

    /**
     * Clones the element and gives the clone its own editor listener registration.
     */
    public DungeonElement clone() throws CloneNotSupportedException {
        DungeonElement clone = (DungeonElement) super.clone();
        clone.editListener = new ElementHotbarListener(clone);
        Bukkit.getPluginManager().registerEvents(clone.editListener, clone.plugin());
        return clone;
    }

    /**
     * Returns the block location that owns this element.
     */
    public Location getLocation() {
        return this.location;
    }

    /**
     * Updates the block location that owns this element.
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    /**
     * Returns the instance this element currently belongs to.
     */
    public DungeonInstance getInstance() {
        return this.instance;
    }

    /**
     * Binds the element to an instance before enable-time registration.
     */
    public void setInstance(DungeonInstance instance) {
        this.instance = instance;
    }

    /**
     * Returns the active editor hotbar menu for this element.
     */
    public PlayerHotbarMenu getMenu() {
        return this.menu;
    }
}
