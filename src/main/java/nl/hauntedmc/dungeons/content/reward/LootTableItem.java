package nl.hauntedmc.dungeons.content.reward;

import nl.hauntedmc.dungeons.annotation.TypeKey;
import nl.hauntedmc.dungeons.util.math.MathUtils;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * One weighted entry inside a {@link LootTable}.
 *
 * <p>Each entry stores only the base item, weight and randomized stack-size bounds.
 */
@TypeKey(id = "dungeons.reward.loot_table_item")
@SerializableAs("dungeons.reward.loot_table_item")
public class LootTableItem implements ConfigurationSerializable {
    private final ItemStack item;
    private int weight = 1;
    private int minItems = 1;
    private int maxItems = 1;

    /**
     * Creates a new LootTableItem instance.
     */
    public LootTableItem(Map<String, Object> config) {
        if (!(config.get("item") instanceof ItemStack stack)) {
            throw new IllegalArgumentException("Loot item requires an ItemStack");
        }
        this.item = stack;
        this.weight = (Integer) config.getOrDefault("weight", 1);
        this.minItems = (Integer) config.getOrDefault("minItems", 1);
        this.maxItems = (Integer) config.getOrDefault("maxItems", 1);
        this.validate();
    }

    /**
     * Creates a new LootTableItem instance.
     */
    public LootTableItem(ItemStack item) {
        this.item = item;
        this.validate();
    }

    /**
     * Returns the randomized item.
     */
    public ItemStack getRandomizedItem() {
        ItemStack item = this.item.clone();
        item.setAmount(
                item.getAmount() * MathUtils.getRandomNumberInRange(this.minItems, this.maxItems));
        return item;
    }

    public void setMinimumItems(int minimum) {
        if (minimum < 0 || minimum > this.maxItems)
            throw new IllegalArgumentException("Invalid minimum item count");
        this.minItems = minimum;
    }

    public void setMaximumItems(int maximum) {
        if (maximum < this.minItems
                || maximum == Integer.MAX_VALUE
                || (long) this.item.getAmount() * maximum > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid maximum item count");
        this.maxItems = maximum;
    }

    public void setWeight(int weight) {
        if (weight < 0) throw new IllegalArgumentException("Invalid loot weight");
        this.weight = weight;
    }

    /** Rejects malformed entries before a repository generation can become active. */
    public final void validate() {
        if (this.item == null || this.item.getType().isAir() || this.item.getAmount() <= 0) {
            throw new IllegalArgumentException("Loot item must contain a nonempty stack");
        }
        if (this.weight < 0
                || this.minItems < 0
                || this.maxItems < this.minItems
                || this.maxItems == Integer.MAX_VALUE
                || (long) this.item.getAmount() * this.maxItems > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid loot item weight or amount range");
        }
    }

    @NotNull
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("item", this.item);
        map.put("weight", this.weight);
        map.put("minItems", this.minItems);
        map.put("maxItems", this.maxItems);
        return map;
    }

    /**
     * Returns the item.
     */
    public ItemStack getItem() {
        return this.item;
    }

    /**
     * Returns the weight.
     */
    public int getWeight() {
        return this.weight;
    }

    /**
     * Returns the min items.
     */
    public int getMinItems() {
        return this.minItems;
    }

    /**
     * Returns the max items.
     */
    public int getMaxItems() {
        return this.maxItems;
    }
}
