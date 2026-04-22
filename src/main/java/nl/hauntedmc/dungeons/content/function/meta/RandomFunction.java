package nl.hauntedmc.dungeons.content.function.meta;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;

/**
 * Composite function that chooses child functions randomly when triggered.
 */
@AutoRegister(id = "dungeons.function.random")
@SerializableAs("dungeons.function.random")
public class RandomFunction extends CompositeFunction {
    /**
     * Creates a new RandomFunction instance.
     */
    public RandomFunction(Map<String, Object> config) {
        super("Random Function", config);
    }

    /**
     * Creates a new RandomFunction instance.
     */
    public RandomFunction() {
        super("Random Function");
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (!this.functions.isEmpty()) {
            DungeonFunction function =
                    this.functions.get(MathUtils.getRandomNumberInRange(0, this.functions.size() - 1));
            this.executeNestedFunction(
                    function,
                    triggerEvent,
                    this.resolveNestedFunctionTargets(function, triggerEvent, targets));
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.STRUCTURE_BLOCK);
        button.setDisplayName("&bFunction Randomizer");
        button.addLore("&eRuns a random function from a");
        button.addLore("&econfigured list.");
        return button;
    }
}
