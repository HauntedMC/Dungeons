package nl.hauntedmc.dungeons.content.function.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Base function for content that owns and runs child functions.
 */
@AutoRegister(id = "dungeons.function.multi")
@SerializableAs("dungeons.function.multi")
public class CompositeFunction extends DungeonFunction {
    @PersistedField protected List<DungeonFunction> functions;

    /**
     * Creates a new CompositeFunction instance.
     */
    public CompositeFunction(String namespace, Map<String, Object> config) {
        super(namespace, config);
        this.functions = new ArrayList<>();
        this.setCategory(FunctionCategory.META);
    }

    /**
     * Creates a new CompositeFunction instance.
     */
    public CompositeFunction(Map<String, Object> config) {
        super("Composite Function", config);
        this.functions = new ArrayList<>();
        this.setCategory(FunctionCategory.META);
    }

    /**
     * Creates a new CompositeFunction instance.
     */
    public CompositeFunction(String namespace) {
        super(namespace);
        this.functions = new ArrayList<>();
        this.setCategory(FunctionCategory.META);
    }

    /**
     * Creates a new CompositeFunction instance.
     */
    public CompositeFunction() {
        super("Composite Function");
        this.functions = new ArrayList<>();
        this.setCategory(FunctionCategory.META);
    }

    /**
     * Performs initialize.
     */
    @Override
    public void initialize() {
        super.initialize();

        for (DungeonFunction function : this.functions) {
            function.setLocation(this.location);
            function.initialize();
            function.setParentFunction(this);
        }
    }

    /**
     * Performs on enable.
     */
    @Override
    public void onEnable() {
        for (DungeonFunction function : this.functions) {
            function.setInstance(this.instance);
            function.setLocation(this.location);
            function.enable(this.instance, this.location);
        }
    }

    /**
     * Performs on disable.
     */
    @Override
    public void onDisable() {
        for (DungeonFunction functions : this.functions) {
            functions.disable();
        }
    }

    /**
     * Adds function.
     */
    public void addFunction(DungeonFunction function) {
        this.functions.add(function);
    }

    /**
     * Removes function.
     */
    public void removeFunction(DungeonFunction function) {
        this.functions.remove(function);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        for (DungeonFunction function : this.functions) {
            if (function.getTrigger() == null) {
                this.executeNestedFunction(
                        function,
                        triggerEvent,
                        this.resolveNestedFunctionTargets(function, triggerEvent, targets));
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.JIGSAW);
        button.setDisplayName("&bComposite Function");
        button.addLore("&eRuns a sequence of nested");
        button.addLore("&efunctions when triggered.");
        return button;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.COMMAND_BLOCK);
                        this.button.setDisplayName("&a&lAdd Function");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        if (CompositeFunction.this.functions.size() >= 54) {
                            LangUtils.sendMessage(player, "editor.function.multi.max-functions-reached");
                        } else {
                            RuntimeContext.guiService().openGui(player, "functionmenu");
                        }
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
                        this.button.setDisplayName("&e&lEdit Function");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        RuntimeContext.guiService().openGui(player, "editmultifunction");
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.BARRIER);
                        this.button.setDisplayName("&c&lRemove Function");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        RuntimeContext.guiService().openGui(player, "removemultifunction");
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PISTON);
                        this.button.setDisplayName("&e&lChange Triggers");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        RuntimeContext.guiService().openGui(player, "editmultifunctiontriggers");
                    }
                });
    }

    /**
     * Sets the location.
     */
    @Override
    public void setLocation(Location loc) {
        super.setLocation(loc);

        for (DungeonFunction function : this.functions) {
            function.setLocation(loc);
        }
    }

    /**
     * Sets the instance.
     */
    @Override
    public void setInstance(DungeonInstance instance) {
        super.setInstance(instance);

        for (DungeonFunction function : this.functions) {
            function.setInstance(instance);
        }
    }

    /**
     * Performs clone.
     */
    public CompositeFunction clone() {
        CompositeFunction clone = (CompositeFunction) super.clone();
        List<DungeonFunction> newFunctions = new ArrayList<>();

        for (DungeonFunction oldFunction : this.functions) {
            DungeonFunction clonedFunction = oldFunction.clone();
            if (clonedFunction != null) {
                clonedFunction.setLocation(clone.location);
                newFunctions.add(clonedFunction);
            }
        }

        clone.functions = newFunctions;
        return clone;
    }

    /**
     * Returns the functions.
     */
    public List<DungeonFunction> getFunctions() {
        return this.functions;
    }
}
