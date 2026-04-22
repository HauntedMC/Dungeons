package nl.hauntedmc.dungeons.model.dungeon;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.math.RangedNumber;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

/**
 * Immutable difficulty preset loaded from a dungeon configuration section.
 */
public class DungeonDifficulty {
    private final String namespace;
    private ItemStack icon;
    private final double mobHealthScale;
    private final double mobSpawnScale;
    private final double mobDamageScale;
    private final RangedNumber bonusLoot;

    /**
     * Creates a difficulty definition from one named config section.
     */
    public DungeonDifficulty(ConfigurationSection config) {
        this.namespace = config.getName();
        this.mobHealthScale = config.getDouble("scaling.mob_health", 0.0);
        this.mobSpawnScale = config.getDouble("scaling.mob_count", 0.0);
        this.mobDamageScale = config.getDouble("scaling.mob_damage", 0.0);
        this.bonusLoot = new RangedNumber(config.getString("scaling.bonus_loot", "0"));

        try {
            Material mat = Material.valueOf(config.getString("icon.material", "STICK"));
            this.icon = new ItemStack(mat);
            ItemMeta meta = this.icon.getItemMeta();
            int model = config.getInt("icon.custom_model_data", -1);
            if (model > -1) {
                CustomModelDataComponent component = meta.getCustomModelDataComponent();
                component.setFloats(List.of((float) model));
                meta.setCustomModelDataComponent(component);
            }

            meta.displayName(
                    ComponentUtils.component(config.getString("icon.display_name", this.namespace)));
            List<String> lore = new ArrayList<>(config.getStringList("icon.lore"));
            meta.lore(ComponentUtils.components(lore));
            this.icon.setItemMeta(meta);
        } catch (IllegalArgumentException exception) {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .warn(
                            "Difficulty level '{}' has an invalid icon material '{}'.",
                            this.namespace,
                            config.getString("icon.material", "STICK"),
                            exception);
        }
    }

    /**
     * Returns a cloned icon item for menu rendering.
     */
    public ItemStack getIcon() {
        return this.icon.clone();
    }

    /**
     * Returns the config namespace identifier of this difficulty.
     */
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Returns the additive mob health scaling value.
     */
    public double getMobHealthScale() {
        return this.mobHealthScale;
    }

    /**
     * Returns the additive mob spawn amount scaling value.
     */
    public double getMobSpawnScale() {
        return this.mobSpawnScale;
    }

    /**
     * Returns the additive mob damage scaling value.
     */
    public double getMobDamageScale() {
        return this.mobDamageScale;
    }

    /**
     * Returns the bonus loot roll range for this difficulty.
     */
    public RangedNumber getBonusLoot() {
        return this.bonusLoot;
    }
}
