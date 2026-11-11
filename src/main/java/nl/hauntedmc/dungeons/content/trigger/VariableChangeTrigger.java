package nl.hauntedmc.dungeons.content.trigger;

import java.util.Map;
import java.util.Objects;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ValueMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.scheduler.BukkitRunnable;

/** Variable Change. */
@AutoRegister(id = "dungeons.trigger.variable_change")
@SerializableAs("dungeons.trigger.variable_change")
public class VariableChangeTrigger extends DungeonTrigger {
    public VariableChangeTrigger() {
        super("Variable Change");
        configure();
    }

    public VariableChangeTrigger(Map<String, Object> config) {
        super("Variable Change", config);
        configure();
    }

    @PersistedField private String variable = "score";
    @PersistedField private int interval = 1;
    private BukkitRunnable watcher;

    private void configure() {
        setCategory(TriggerCategory.DUNGEON);
    }

    @Override
    public void onEnable() {
        var run = instance.asPlayInstance();
        if (run == null || variable == null || variable.isBlank()) return;
        watcher =
                new BukkitRunnable() {
                    private String previous = run.getInstanceVariables().getString(variable);

                    @Override
                    public void run() {
                        String current = run.getInstanceVariables().getString(variable);
                        if (!Objects.equals(current, previous)) {
                            previous = current;
                            VariableChangeTrigger.this.trigger();
                        }
                    }
                };
        watcher.runTaskTimer(plugin(), 1L, Math.max(1, interval));
    }

    @Override
    public void onDisable() {
        if (watcher != null && !watcher.isCancelled()) watcher.cancel();
        watcher = null;
    }

    @Override
    public VariableChangeTrigger clone() {
        VariableChangeTrigger copy = (VariableChangeTrigger) super.clone();
        copy.watcher = null;
        return copy;
    }

    @Override
    public void buildHotbarMenu() {
        menu.addMenuItem(
                new ValueMenuItem(
                        "Allow retrigger (true/false)",
                        () -> allowRetrigger,
                        v -> {
                            if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false"))
                                throw new IllegalArgumentException();
                            allowRetrigger = Boolean.parseBoolean(v);
                        }));
        menu.addMenuItem(
                new ValueMenuItem(
                        "Variable",
                        () -> variable,
                        v -> {
                            if (v.isBlank()) throw new IllegalArgumentException();
                            variable = v;
                        }));
        menu.addMenuItem(
                new ValueMenuItem(
                        "Interval (ticks)",
                        () -> interval,
                        v -> interval = Math.max(1, ValueMenuItem.nonNegativeInt(v))));
    }

    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.OBSERVER);
        button.setDisplayName("&dVariable Change");
        return button;
    }
}
