package nl.hauntedmc.dungeons.content.trigger;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ValueMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.scheduler.BukkitRunnable;

/** Timer. */
@AutoRegister(id = "dungeons.trigger.timer")
@SerializableAs("dungeons.trigger.timer")
public class TimerTrigger extends DungeonTrigger {
    public TimerTrigger() {
        super("Timer");
        configure();
    }

    public TimerTrigger(Map<String, Object> config) {
        super("Timer", config);
        configure();
    }

    @PersistedField private int delay = 20;
    @PersistedField private int interval = 20;
    private BukkitRunnable clock;

    private void configure() {
        setCategory(TriggerCategory.DUNGEON);
    }

    @Override
    public void onEnable() {
        clock =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        TimerTrigger.this.trigger();
                    }
                };
        clock.runTaskTimer(plugin(), Math.max(0, delay), Math.max(1, interval));
    }

    @Override
    public void onDisable() {
        if (clock != null && !clock.isCancelled()) clock.cancel();
        clock = null;
    }

    @Override
    public TimerTrigger clone() {
        TimerTrigger copy = (TimerTrigger) super.clone();
        copy.clock = null;
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
                        "Delay (ticks)",
                        () -> delay,
                        v -> delay = ValueMenuItem.nonNegativeInt(v)));
        menu.addMenuItem(
                new ValueMenuItem(
                        "Interval (ticks)",
                        () -> interval,
                        v -> interval = Math.max(1, ValueMenuItem.nonNegativeInt(v))));
    }

    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.CLOCK);
        button.setDisplayName("&dTimer");
        return button;
    }
}
