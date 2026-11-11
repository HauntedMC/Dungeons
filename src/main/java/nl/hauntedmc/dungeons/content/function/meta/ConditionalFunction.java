package nl.hauntedmc.dungeons.content.function.meta;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ValueMenuItem;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.instance.InstanceUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;

/** Evaluates a variable comparison at execution time before dispatching its child functions. */
@AutoRegister(id = "dungeons.function.conditional")
@SerializableAs("dungeons.function.conditional")
public class ConditionalFunction extends CompositeFunction {
    @PersistedField private String comparison = "";

    public ConditionalFunction() {
        super("Conditional Function");
    }

    public ConditionalFunction(Map<String, Object> config) {
        super("Conditional Function", config);
    }

    @Override
    public void runFunction(TriggerFireEvent event, List<DungeonPlayerSession> targets) {
        var run = instance == null ? null : instance.asPlayInstance();
        if (run != null
                && comparison != null
                && !comparison.isBlank()
                && InstanceUtils.compareVars(run, comparison)) {
            super.runFunction(event, targets);
        }
    }

    @Override
    public void buildHotbarMenu() {
        super.buildHotbarMenu();
        menu.addMenuItem(
                new ValueMenuItem("Comparison", () -> comparison, value -> comparison = value));
    }

    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.COMPARATOR);
        button.setDisplayName("&bConditional Function");
        button.addLore("&eRuns child functions when a variable comparison passes.");
        return button;
    }
}
