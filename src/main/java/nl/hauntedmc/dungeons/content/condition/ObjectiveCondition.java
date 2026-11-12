package nl.hauntedmc.dungeons.content.condition;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.experience.ExperienceFields;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;

/** Checks shared or actor-specific objective state without depending on a visible HUD. */
@AutoRegister(id = "dungeons.condition.objective")
@SerializableAs("dungeons.condition.objective")
public final class ObjectiveCondition extends TriggerCondition {
    @PersistedField private String id = "objective";
    @PersistedField private String state = "completed";

    public ObjectiveCondition() {
        super("Objective");
    }

    public ObjectiveCondition(Map<String, Object> config) {
        super("Objective", config);
    }

    @Override
    public boolean check(TriggerFireEvent event) {
        var playable = this.instance.asPlayInstance();
        if (playable == null || runtime().experience() == null) return false;
        var run = runtime().experience().mechanics(playable);
        if (run == null || !run.definition().objectives().containsKey(id)) return false;
        var actor = event.getPlayerSession();
        if (actor == null
                && run.definition().objectives().get(id).scope()
                        == nl.hauntedmc.dungeons.experience.ExperienceDefinition.Scope.PLAYER)
            return false;
        var player = actor == null ? new java.util.UUID(0, 0) : actor.getPlayer().getUniqueId();
        return state.equals("active")
                ? run.progress().active(id, player)
                : run.progress().completed(id, player);
    }

    @Override
    public MenuButton buildMenuButton() {
        var button = new MenuButton(Material.WRITABLE_BOOK);
        button.setDisplayName("&aObjective State");
        return button;
    }

    @Override
    public void buildHotbarMenu() {
        ExperienceFields.field(menu, "Objective ID", () -> id, value -> id = value);
        ExperienceFields.field(
                menu,
                "State: active / completed",
                () -> state,
                value -> {
                    if (!java.util.Set.of("active", "completed").contains(value))
                        throw new IllegalArgumentException("Unknown objective state");
                    state = value;
                });
    }
}
