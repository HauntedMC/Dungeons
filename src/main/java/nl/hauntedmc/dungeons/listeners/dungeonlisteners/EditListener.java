package nl.hauntedmc.dungeons.listeners.dungeonlisteners;

import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class EditListener extends InstanceListener {
   private final InstanceEditable instance;

   public EditListener(InstanceEditable instance) {
      super(instance);
      this.instance = instance;
   }

   @EventHandler
   public void onMobSpawn(CreatureSpawnEvent event) {
      if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
         if (event.getEntityType() != EntityType.ARMOR_STAND) {
            event.setCancelled(true);
         }
      }
   }
}
