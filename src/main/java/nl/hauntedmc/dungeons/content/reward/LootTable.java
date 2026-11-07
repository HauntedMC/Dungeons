package nl.hauntedmc.dungeons.content.reward;

import nl.hauntedmc.dungeons.annotation.TypeKey;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.math.RandomCollection;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Validated, serializable weighted loot data. Editor ownership and rendering live in gui.loot. */
@TypeKey(id = "dungeons.reward.loot_table")
@SerializableAs("dungeons.reward.loot_table")
public class LootTable implements ConfigurationSerializable {
    private final String namespace;
    private int minItems = 1;
    private int maxItems = 3;
    private boolean allowDuplicates = true;
    private final Map<Integer, LootTableItem> lootItems;

    /**
     * Creates a new LootTable instance.
     */
    public LootTable(Map<String, Object> config) {
        this.namespace = (String) config.get("namespace");
        this.minItems = (Integer) config.getOrDefault("minItems", 1);
        this.maxItems = (Integer) config.getOrDefault("maxItems", 3);
        this.allowDuplicates = (Boolean) config.getOrDefault("allowDuplicates", true);
        this.lootItems = new HashMap<>();
        if (!(config.getOrDefault("lootItems", Map.of()) instanceof Map<?, ?> entries)) {
            throw new IllegalArgumentException("lootItems must be a mapping");
        }
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            this.lootItems.put(
                    Integer.class.cast(entry.getKey()), LootTableItem.class.cast(entry.getValue()));
        }
        this.validate();
    }

    /**
     * Creates a new LootTable instance.
     */
    public LootTable(String namespace) {
        this.namespace = namespace;
        this.lootItems = new HashMap<>();
        this.validate();
    }

    /**
     * Performs randomize items.
     */
    public List<ItemStack> randomizeItems(double... chances) {
        int bonusItems = 0;

        for (int i = 0; i < chances.length && i <= bonusItems; i++) {
            double chance = chances[i];
            if (MathUtils.getRandomBoolean(chance)) {
                bonusItems++;
            }
        }

        return this.randomizeItems(bonusItems);
    }

    /**
     * Performs randomize items.
     */
    public List<ItemStack> randomizeItems(int bonusItems) {
        List<ItemStack> items = new ArrayList<>();
        RandomCollection<LootTableItem> randomizer = this.createRandomizer();

        for (LootTableItem lootItem : this.lootItems.values()) {
            randomizer.add(lootItem.getWeight(), lootItem);
        }

        if (randomizer.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(LootTable.class)
                    .warn(
                            "Loot table '{}' has no valid weighted items to randomize.",
                            this.namespace);
            return items;
        }

        int newMin = Math.min(this.minItems, this.maxItems);
        int amount = MathUtils.getRandomNumberInRange(newMin, this.maxItems) + bonusItems;
        if (!this.allowDuplicates) {
            amount = Math.min(this.lootItems.size(), amount);
        }

        List<LootTableItem> foundItems = new ArrayList<>();
        while (foundItems.size() < amount) {
            LootTableItem lootItem = randomizer.next();
            if (lootItem == null) {
                break;
            }
            foundItems.add(lootItem);
            if (!this.allowDuplicates) {
                randomizer = this.createRandomizer();
                for (LootTableItem candidate : this.lootItems.values()) {
                    if (!foundItems.contains(candidate)) {
                        randomizer.add(candidate.getWeight(), candidate);
                    }
                }
            }
        }

        for (LootTableItem lootItem : foundItems) {
            items.add(lootItem.getRandomizedItem());
        }

        return items;
    }

    @NotNull
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("namespace", this.namespace);
        map.put("minItems", this.minItems);
        map.put("maxItems", this.maxItems);
        map.put("allowDuplicates", this.allowDuplicates);
        map.put("lootItems", this.lootItems);
        return map;
    }

    /**
     * Returns the namespace.
     */
    public String getNamespace() {
        return this.namespace;
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

    /**
     * Returns whether allow duplicates.
     */
    public boolean isAllowDuplicates() {
        return this.allowDuplicates;
    }

    public Map<Integer, LootTableItem> getItems() {
        return java.util.Collections.unmodifiableMap(this.lootItems);
    }

    public void putItem(int slot, LootTableItem item) {
        if (slot < 9 || slot >= 54) throw new IllegalArgumentException("Invalid loot slot");
        java.util.Objects.requireNonNull(item).validate();
        this.lootItems.put(slot, item);
    }

    public LootTableItem removeItem(int slot) {
        return this.lootItems.remove(slot);
    }

    public void setMinimumItems(int minimum) {
        if (minimum < 0 || minimum > this.maxItems)
            throw new IllegalArgumentException("Invalid minimum loot count");
        this.minItems = minimum;
    }

    public void setMaximumItems(int maximum) {
        if (maximum < this.minItems || maximum == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid maximum loot count");
        this.maxItems = maximum;
    }

    public void setAllowDuplicates(boolean allowDuplicates) {
        this.allowDuplicates = allowDuplicates;
    }

    /** Validates persisted model data without constructing or replacing editor windows. */
    public final void validate() {
        if (this.namespace == null || this.namespace.isBlank()) {
            throw new IllegalArgumentException("Loot table namespace must not be blank");
        }
        if (this.minItems < 0
                || this.maxItems < this.minItems
                || this.maxItems == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Invalid loot count range in table " + this.namespace);
        }
        for (var entry : this.lootItems.entrySet()) {
            if (entry.getKey() == null
                    || entry.getKey() < 9
                    || entry.getKey() >= 54
                    || entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Invalid loot item slot in table " + this.namespace);
            }
            entry.getValue().validate();
        }
    }

    RandomCollection<LootTableItem> createRandomizer() {
        return new RandomCollection<>();
    }
}
