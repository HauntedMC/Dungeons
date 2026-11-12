package nl.hauntedmc.dungeons.content.trigger;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.ExperienceEvent;
import nl.hauntedmc.dungeons.experience.ExperienceFields;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.event.EventHandler;

/** Graph receiver for scoped progression, wave and boss-phase events. */
@AutoRegister(id = "dungeons.trigger.experience")
@SerializableAs("dungeons.trigger.experience")
public final class ExperienceTrigger extends DungeonTrigger {
    @PersistedField private String kind = "objective-completed";
    @PersistedField private String id = "objective";

    public ExperienceTrigger() {
        super("Experience Event");
        setCategory(TriggerCategory.DUNGEON);
        setHasTarget(true);
    }

    public ExperienceTrigger(Map<String, Object> config) {
        super("Experience Event", config);
        setCategory(TriggerCategory.DUNGEON);
        setHasTarget(true);
    }

    @Override
    public void initialize() {
        super.initialize();
        setDisplayName(kind + ": " + id);
    }

    @EventHandler
    public void changed(ExperienceEvent event) {
        if (event.getInstance() == this.instance
                && kind.equals(event.kind())
                && (id.equals("*") || id.equals(event.id())))
            trigger(event.player() == null ? null : runtime().playerSessions().get(event.player()));
    }

    @Override
    public MenuButton buildMenuButton() {
        var button = new MenuButton(Material.COMPARATOR);
        button.setDisplayName("&aExperience Event");
        return button;
    }

    @Override
    public void buildHotbarMenu() {
        ExperienceFields.field(menu, "Event kind", () -> kind, value -> kind = value);
        ExperienceFields.field(
                menu,
                "Objective / stage / encounter ID (* for any)",
                () -> id,
                value -> id = value);
    }
}
