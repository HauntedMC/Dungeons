package nl.hauntedmc.dungeons.content.condition;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ValueMenuItem;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;

/** Player Health. */
@AutoRegister(id = "dungeons.condition.player_health")
@SerializableAs("dungeons.condition.player_health")
public class PlayerHealthCondition extends TriggerCondition {
    public PlayerHealthCondition() {
        super("Player Health");
    }

    public PlayerHealthCondition(Map<String, Object> config) {
        super("Player Health", config);
    }

    @PersistedField private double minimum = 0;
    @PersistedField private double maximum = 20;

    @Override
    public boolean check(TriggerFireEvent event) {
        if (event == null
                || event.getPlayerSession() == null
                || event.getInstance() != instance
                || event.getPlayerSession().getInstance() != instance
                || event.getPlayerSession().isObserving()
                || event.getPlayer() == null
                || !Double.isFinite(minimum)
                || !Double.isFinite(maximum)
                || minimum < 0
                || maximum < minimum) return false;
        double health = event.getPlayer().getHealth();
        return health >= minimum && health <= maximum;
    }

    @Override
    public void buildHotbarMenu() {
        menu.addMenuItem(
                new ValueMenuItem(
                        "Minimum health",
                        () -> minimum,
                        v -> minimum = ValueMenuItem.nonNegativeDouble(v)));
        menu.addMenuItem(
                new ValueMenuItem(
                        "Maximum health",
                        () -> maximum,
                        v -> maximum = ValueMenuItem.nonNegativeDouble(v)));
    }

    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.GOLDEN_APPLE);
        button.setDisplayName("&dPlayer Health");
        return button;
    }
}
