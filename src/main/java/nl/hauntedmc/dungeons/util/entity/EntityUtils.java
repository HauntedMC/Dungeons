package nl.hauntedmc.dungeons.util.entity;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Entity attribute and teleport helper utilities. */
public final class EntityUtils {

    /** Returns effective max health for damageable entities. */
    public static double getMaxHealth(Damageable entity) {
        if (entity instanceof LivingEntity living) {
            AttributeInstance attribute = living.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null) {
                return attribute.getValue();
            }
        }

        return entity.getHealth();
    }

    /** Sets max-health base value when the attribute is available. */
    public static void setMaxHealth(LivingEntity entity, double maxHealth) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(maxHealth);
        }
    }

    /** Teleports an entity while dismounting it from any current vehicle first. */
    public static void forceTeleport(Entity ent, Location loc) {
        if (ent == null || loc == null) return;
        if (ent.getVehicle() != null) {
            ent.getVehicle().removePassenger(ent);
        }

        ent.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }
}
