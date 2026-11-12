package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.experience.ExperienceDefinition;
import nl.hauntedmc.dungeons.experience.ExperienceFields;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;

/** Graph bridge for optional progression and encounters. */
@AutoRegister(id = "dungeons.function.experience")
@SerializableAs("dungeons.function.experience")
public final class ExperienceFunction extends DungeonFunction {
    @PersistedField private String operation = "signal";
    @PersistedField private String id = "objective";
    @PersistedField private int amount = 1;

    public ExperienceFunction() {
        super("Experience");
        setCategory(FunctionCategory.DUNGEON);
    }

    public ExperienceFunction(Map<String, Object> config) {
        super("Experience", config);
        setCategory(FunctionCategory.DUNGEON);
    }

    @Override
    public void runFunction(TriggerFireEvent event, List<DungeonPlayerSession> targets) {
        var playable = this.instance.asPlayInstance();
        if (playable == null || runtime().experience() == null) return;
        var run = runtime().experience().mechanics(playable);
        if (run == null) return;
        switch (operation.toLowerCase(Locale.ROOT)) {
            case "start-encounter" -> run.start(id);
            case "reset-encounter" -> run.reset(id);
            case "activate-stage" -> {
                if (targets.isEmpty()) run.progress().activateStage(id, new java.util.UUID(0, 0));
                else
                    for (var target : targets)
                        run.progress().activateStage(id, target.getPlayer().getUniqueId());
            }
            case "signal", "advance", "reveal" -> {
                var selected = targets;
                if (selected.isEmpty() && event != null && event.getPlayerSession() != null)
                    selected = List.of(event.getPlayerSession());
                if (selected.isEmpty()) selected = List.copyOf(playable.getPlayers());
                // Shared goals receive one contribution for this function invocation.
                boolean shared = false;
                String invocation = java.util.UUID.randomUUID().toString();
                for (var session : selected) {
                    var goal = run.definition().objectives().get(id);
                    if (!operation.equals("signal")
                            && goal != null
                            && goal.scope() == ExperienceDefinition.Scope.RUN) {
                        if (shared) continue;
                        shared = true;
                    }
                    var player = session.getPlayer().getUniqueId();
                    if (operation.equals("signal"))
                        run.signal(id, player, ExperienceDefinition.Source.SIGNAL, invocation);
                    else if (operation.equals("reveal")) run.progress().reveal(id, player);
                    else run.progress().advance(id, player, amount, null);
                }
            }
            default ->
                    throw new IllegalArgumentException(
                            "Unknown experience operation: " + operation);
        }
    }

    @Override
    public MenuButton buildMenuButton() {
        var button = new MenuButton(Material.WRITABLE_BOOK);
        button.setDisplayName("&aExperience Controller");
        button.addLore("&7Signals, objectives and encounters");
        return button;
    }

    @Override
    public void buildHotbarMenu() {
        ExperienceFields.field(
                menu,
                "Operation: signal / advance / reveal / activate-stage / start-encounter / reset-encounter",
                () -> operation,
                value -> {
                    if (!List.of(
                                    "signal",
                                    "advance",
                                    "reveal",
                                    "activate-stage",
                                    "start-encounter",
                                    "reset-encounter")
                            .contains(value))
                        throw new IllegalArgumentException("Unknown experience operation");
                    operation = value;
                });
        ExperienceFields.field(
                menu, "Objective, signal or encounter ID", () -> id, value -> id = value);
        ExperienceFields.field(
                menu,
                "Progress amount",
                () -> String.valueOf(amount),
                value -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 1) throw new IllegalArgumentException("Positive amount required");
                    amount = parsed;
                });
    }
}
