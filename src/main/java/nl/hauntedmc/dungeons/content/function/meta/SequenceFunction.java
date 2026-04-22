package nl.hauntedmc.dungeons.content.function.meta;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Composite function that runs its child functions in order.
 */
@AutoRegister(id = "dungeons.function.sequence")
@SerializableAs("dungeons.function.sequence")
public class SequenceFunction extends CompositeFunction {
    @PersistedField private boolean loop = true;
    private int index = 0;

    /**
     * Creates a new SequenceFunction instance.
     */
    public SequenceFunction(Map<String, Object> config) {
        super("Function Sequence", config);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Creates a new SequenceFunction instance.
     */
    public SequenceFunction() {
        super("Function Sequence");
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (!this.functions.isEmpty()) {
            DungeonFunction function = this.functions.get(this.index);
            this.executeNestedFunction(
                    function,
                    triggerEvent,
                    this.resolveNestedFunctionTargets(function, triggerEvent, targets));
            this.increment();
        }
    }

    /**
     * Performs increment.
     */
    private void increment() {
        this.index++;
        if (this.index >= this.functions.size()) {
            if (!this.loop) {
                this.disable();
            }

            this.index = 0;
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
        button.setDisplayName("&bFunction Sequencer");
        button.addLore("&eRuns a list of functions");
        button.addLore("&eone at a time in order");
        button.addLore("&eeach time it's triggered.");
        return button;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        super.buildHotbarMenu();
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.REPEATER);
                        this.button.setDisplayName("&d&lLoop Sequence");
                        this.button.setEnchanted(SequenceFunction.this.loop);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!SequenceFunction.this.loop) {
                            LangUtils.sendMessage(player, "editor.function.sequence.loops");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.sequence.no-loop");
                        }

                        SequenceFunction.this.loop = !SequenceFunction.this.loop;
                    }
                });
    }
}
