package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ValueMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;

/** Player Health. */
@AutoRegister(id = "dungeons.function.health")
@SerializableAs("dungeons.function.health")
public class HealthFunction extends DungeonFunction {
    public HealthFunction() {
        super("Player Health");
        configure();
    }

    public HealthFunction(Map<String, Object> config) {
        super("Player Health", config);
        configure();
    }

    @PersistedField private double amount = 20;
    @PersistedField private boolean relative = false;

    private void configure() {
        setTargetType(FunctionTargetType.PLAYER);
    }

    @Override
    public void runFunction(TriggerFireEvent event, List<DungeonPlayerSession> targets) {
        if (!Double.isFinite(amount)) return;
        for (DungeonPlayerSession target : targets) {
            if (target == null || target.isObserving() || target.getInstance() != instance)
                continue;
            var player = target.getPlayer();
            if (player == null || player.isDead()) continue;
            double maximum = EntityUtils.getMaxHealth(player);
            if (!Double.isFinite(maximum) || maximum <= 0) continue;
            player.setHealth(
                    Math.clamp(relative ? player.getHealth() + amount : amount, 0, maximum));
        }
    }

    @Override
    public void buildHotbarMenu() {
        menu.addMenuItem(
                new ValueMenuItem(
                        "Health amount",
                        () -> amount,
                        v -> {
                            double parsed = Double.parseDouble(v);
                            if (!Double.isFinite(parsed)) throw new IllegalArgumentException();
                            amount = parsed;
                        }));
        menu.addMenuItem(
                new ValueMenuItem(
                        "Relative (true/false)",
                        () -> relative,
                        v -> {
                            if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false"))
                                throw new IllegalArgumentException();
                            relative = Boolean.parseBoolean(v);
                        }));
    }

    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.GOLDEN_APPLE);
        button.setDisplayName("&dPlayer Health");
        return button;
    }
}
