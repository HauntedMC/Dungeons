package nl.hauntedmc.dungeons.model.dungeon;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persisted dungeon access-key definition with audit metadata.
 */
@TypeKey(id = "dungeons.access.key_definition")
@SerializableAs("dungeons.access.key_definition")
public final class AccessKeyDefinition implements ConfigurationSerializable {
    private final String keyId;
    private final ItemStack item;
    private final Date addedAt;
    private final String addedByName;
    private final String addedByUniqueId;

    /**
     * Restores an access-key definition from serialized config data.
     */
    public AccessKeyDefinition(Map<String, Object> config) {
        this.keyId = resolveKeyId(config.get("KeyId"), config.get("Item"));
        Object rawItem = config.get("Item");
        this.item = rawItem instanceof ItemStack stack ? this.applyStableIdentity(stack.clone()) : null;
        this.addedAt = parseDate(config.get("AddedAt"));
        this.addedByName = asNormalizedString(config.get("AddedByName"));
        this.addedByUniqueId = asNormalizedString(config.get("AddedByUniqueId"));
    }

    /**
     * Creates a new access-key definition.
     */
    public AccessKeyDefinition(
            @NotNull ItemStack item,
            @Nullable Date addedAt,
            @Nullable String addedByName,
            @Nullable UUID addedByUniqueId) {
        this.keyId = UUID.randomUUID().toString();
        this.item = this.applyStableIdentity(item.clone());
        this.addedAt = addedAt == null ? null : new Date(addedAt.getTime());
        this.addedByName = normalize(addedByName);
        this.addedByUniqueId = addedByUniqueId == null ? null : addedByUniqueId.toString();
    }

    /**
     * Converts a raw config list entry into an access-key definition.
     */
    public static @Nullable AccessKeyDefinition fromConfigValue(@Nullable Object value) {
        if (value instanceof AccessKeyDefinition keyDefinition) {
            return keyDefinition.isValid() ? keyDefinition : null;
        }

        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> serialized = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    serialized.put(key, entry.getValue());
                }
            }
            if (asNormalizedString(serialized.get("KeyId")) == null) {
                return null;
            }
            AccessKeyDefinition keyDefinition = new AccessKeyDefinition(serialized);
            return keyDefinition.isValid() ? keyDefinition : null;
        }

        return null;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> values = new HashMap<>();
        values.put("KeyId", this.keyId);
        if (this.item != null) {
            values.put("Item", this.createItemCopy());
        }
        if (this.addedAt != null) {
            values.put("AddedAt", this.addedAt.getTime());
        }
        if (this.addedByName != null) {
            values.put("AddedByName", this.addedByName);
        }
        if (this.addedByUniqueId != null) {
            values.put("AddedByUniqueId", this.addedByUniqueId);
        }
        return values;
    }

    /**
     * Returns whether this definition is usable.
     */
    public boolean isValid() {
        return this.item != null && this.item.getType() != Material.AIR;
    }

    /**
     * Returns whether the given item matches this configured key.
     */
    public boolean matches(@Nullable ItemStack candidate) {
        String taggedCandidateId = this.readTaggedKeyId(candidate);
        return this.isValid() && taggedCandidateId != null && taggedCandidateId.equals(this.keyId);
    }

    /**
     * Returns a clone of the configured key item.
     */
    public @Nullable ItemStack createItemCopy() {
        return this.item == null ? null : this.applyStableIdentity(this.item.clone());
    }

    /**
     * Returns the configured key item.
     */
    public @Nullable ItemStack getItem() {
        return this.createItemCopy();
    }

    /**
     * Returns the stable identity of this access key.
     */
    public @NotNull String getKeyId() {
        return this.keyId;
    }

    /**
     * Returns the timestamp at which this key was added.
     */
    public @Nullable Date getAddedAt() {
        return this.addedAt == null ? null : new Date(this.addedAt.getTime());
    }

    /**
     * Returns the recorded player name that added this key.
     */
    public @Nullable String getAddedByName() {
        return this.addedByName;
    }

    /**
     * Returns the recorded player UUID string that added this key.
     */
    public @Nullable String getAddedByUniqueId() {
        return this.addedByUniqueId;
    }

    private static @Nullable Date parseDate(@Nullable Object value) {
        if (value instanceof Number number) {
            long timestamp = number.longValue();
            if (timestamp > 0L) {
                return new Date(timestamp);
            }
        }
        return null;
    }

    private static @Nullable String normalize(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static @Nullable String asNormalizedString(@Nullable Object value) {
        return value instanceof String string ? normalize(string) : null;
    }

    private static @NotNull String resolveKeyId(@Nullable Object rawKeyId, @Nullable Object rawItem) {
        String storedKeyId = asNormalizedString(rawKeyId);
        if (storedKeyId != null) {
            return storedKeyId;
        }

        if (rawItem instanceof ItemStack item) {
            String taggedKeyId = readTaggedKeyIdStatic(item);
            if (taggedKeyId != null) {
                return taggedKeyId;
            }
        }

        return UUID.randomUUID().toString();
    }

    private ItemStack applyStableIdentity(ItemStack item) {
        try {
            ItemUtils.clearDungeonAccessKeyInstance(item);
            return ItemUtils.tagDungeonAccessKey(item, this.keyId);
        } catch (IllegalStateException ignored) {
            return item;
        }
    }

    private @Nullable String readTaggedKeyId(@Nullable ItemStack item) {
        return readTaggedKeyIdStatic(item);
    }

    private static @Nullable String readTaggedKeyIdStatic(@Nullable ItemStack item) {
        try {
            return ItemUtils.getDungeonAccessKeyId(item);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }
}
