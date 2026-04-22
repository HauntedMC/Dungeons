package nl.hauntedmc.dungeons.listener.instance;

import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Instance listener specialization for editor worlds.
 *
 * <p>Edit worlds stay mostly empty so markers and tooling remain readable.</p>
 */
public class EditListener extends InstanceListener {
    private final EditableInstance instance;

    /**
     * Creates the editor listener for a single edit instance.
     */
    public EditListener(EditableInstance instance) {
        super(instance);
        this.instance = instance;
    }

    /**
     * Blocks natural mob spawning in edit instances, except armor stands used by tools.
     */
    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
            if (event.getEntityType() != EntityType.ARMOR_STAND) {
                event.setCancelled(true);
            }
        }
    }
}
