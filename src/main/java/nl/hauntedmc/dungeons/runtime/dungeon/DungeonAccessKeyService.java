package nl.hauntedmc.dungeons.runtime.dungeon;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import nl.hauntedmc.dungeons.config.PluginConfigView;
import nl.hauntedmc.dungeons.model.dungeon.AccessKeyDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonLoadException;
import nl.hauntedmc.dungeons.persistence.StateDocument;
import nl.hauntedmc.dungeons.persistence.StateStore;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Owns issued access-key validation, reservations, revocation and durable ledger rollback.
 * Configuration definitions are supplied by the dungeon and remain owned by its server thread.
 */
public final class DungeonAccessKeyService {
    private final DungeonsRuntime runtime;
    private final String worldName;
    private final Supplier<List<AccessKeyDefinition>> definitions;
    private final StateDocument document;
    private final YamlConfiguration accessKeyStateConfig;
    private Map<String, String> issuedAccessKeyInstances;
    private Map<String, String> durableIssuedKeys = Map.of();
    private Set<String> durableReservedKeys = Set.of();
    private Set<String> durableInvalidatedKeys = Set.of();
    private Set<String> reservedAccessKeyInstances;
    private Set<String> invalidatedAccessKeyInstances;
    private boolean accessKeyStateLoaded;
    private boolean planningSettlement;
    private @Nullable Boolean uniqueAccessKeyValidationEnabled;

    public DungeonAccessKeyService(
            DungeonsRuntime runtime,
            File folder,
            UUID dungeonId,
            Supplier<List<AccessKeyDefinition>> definitions)
            throws DungeonLoadException {
        this.runtime = runtime;
        this.worldName = folder.getName();
        this.definitions = definitions;
        this.document =
                runtime.stateStore()
                        .document(new StateStore.Key("key-ledger", dungeonId.toString(), null));
        this.accessKeyStateConfig = new YamlConfiguration();
        this.issuedAccessKeyInstances = new LinkedHashMap<>();
        this.reservedAccessKeyInstances = new LinkedHashSet<>();
        this.invalidatedAccessKeyInstances = new LinkedHashSet<>();
        this.accessKeyStateLoaded = false;
        this.uniqueAccessKeyValidationEnabled = null;
        if (this.usesUniqueAccessKeyValidation()) {
            this.loadAccessKeyState();
            this.pruneAccessKeyState();
        }
    }

    private void loadAccessKeyState() throws DungeonLoadException {
        this.issuedAccessKeyInstances.clear();
        this.reservedAccessKeyInstances.clear();
        this.invalidatedAccessKeyInstances.clear();
        if (!this.usesUniqueAccessKeyValidation()) {
            this.accessKeyStateLoaded = false;
            return;
        }

        try {
            this.document.refresh();
            this.document.loadInto(this.accessKeyStateConfig);
            boolean hadPersistedReservations =
                    !this.accessKeyStateConfig.getStringList("reserved_instance_ids").isEmpty();
            Object rawIssuedInstances = this.accessKeyStateConfig.get("issued_instances");
            if (rawIssuedInstances instanceof ConfigurationSection section) {
                for (String instanceId : section.getKeys(false)) {
                    String keyId = section.getString(instanceId);
                    if (keyId != null && !keyId.isBlank()) {
                        this.issuedAccessKeyInstances.put(instanceId, keyId);
                    }
                }
            } else if (rawIssuedInstances instanceof Map<?, ?> rawMap) {
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    if (!(entry.getKey() instanceof String instanceId)
                            || !(entry.getValue() instanceof String keyId)
                            || instanceId.isBlank()
                            || keyId.isBlank()) {
                        continue;
                    }

                    this.issuedAccessKeyInstances.put(instanceId, keyId);
                }
            }

            this.invalidatedAccessKeyInstances.addAll(
                    this.accessKeyStateConfig.getStringList("invalidated_instance_ids"));
            this.accessKeyStateLoaded = true;
            this.rememberKeyLedger();
            if (hadPersistedReservations) {
                this.saveAccessKeyState();
            }
        } catch (InvalidConfigurationException
                | IllegalArgumentException
                | YAMLException exception) {
            throw new DungeonLoadException(
                    "Database dungeon access key state is invalid! See error below...", true);
        }
    }

    private void rememberKeyLedger() {
        this.durableIssuedKeys = new LinkedHashMap<>(this.issuedAccessKeyInstances);
        this.durableReservedKeys = new LinkedHashSet<>(this.reservedAccessKeyInstances);
        this.durableInvalidatedKeys = new LinkedHashSet<>(this.invalidatedAccessKeyInstances);
    }

    private void saveAccessKeyState() {
        if (!this.usesUniqueAccessKeyValidation() || this.planningSettlement) return;
        var candidate = new YamlConfiguration();
        try {
            candidate.loadFromString(this.accessKeyStateConfig.saveToString());
            candidate.set("issued_instances", new LinkedHashMap<>(this.issuedAccessKeyInstances));
            candidate.set(
                    "reserved_instance_ids", new ArrayList<>(this.reservedAccessKeyInstances));
            candidate.set(
                    "invalidated_instance_ids",
                    new ArrayList<>(this.invalidatedAccessKeyInstances));
            this.document.save(candidate.saveToString());
            this.rememberKeyLedger();
        } catch (InvalidConfigurationException | RuntimeException failure) {
            this.issuedAccessKeyInstances.clear();
            this.issuedAccessKeyInstances.putAll(this.durableIssuedKeys);
            this.reservedAccessKeyInstances.clear();
            this.reservedAccessKeyInstances.addAll(this.durableReservedKeys);
            this.invalidatedAccessKeyInstances.clear();
            this.invalidatedAccessKeyInstances.addAll(this.durableInvalidatedKeys);
            throw new IllegalStateException(
                    "Cannot durably update access key ledger for " + this.worldName, failure);
        }
    }

    private void pruneAccessKeyState() {
        if (!this.usesUniqueAccessKeyValidation()) {
            this.issuedAccessKeyInstances.clear();
            this.reservedAccessKeyInstances.clear();
            this.invalidatedAccessKeyInstances.clear();
            this.accessKeyStateLoaded = false;
            return;
        }

        Set<String> configuredKeyIds = new LinkedHashSet<>();
        for (AccessKeyDefinition accessKey : this.definitions.get()) {
            configuredKeyIds.add(accessKey.getKeyId());
        }

        boolean changed =
                this.issuedAccessKeyInstances
                        .entrySet()
                        .removeIf(
                                entry ->
                                        entry.getKey() == null
                                                || entry.getKey().isBlank()
                                                || entry.getValue() == null
                                                || entry.getValue().isBlank()
                                                || this.invalidatedAccessKeyInstances.contains(
                                                        entry.getKey())
                                                || !configuredKeyIds.contains(entry.getValue()));
        changed |=
                this.reservedAccessKeyInstances.removeIf(
                        instanceId ->
                                instanceId == null
                                        || instanceId.isBlank()
                                        || !this.issuedAccessKeyInstances.containsKey(instanceId));
        changed |=
                this.invalidatedAccessKeyInstances.removeIf(
                        instanceId -> instanceId == null || instanceId.isBlank());
        if (changed) {
            this.saveAccessKeyState();
        }
    }

    private @Nullable AccessKeyDefinition findAccessKeyByStableId(@Nullable String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return null;
        }

        for (AccessKeyDefinition accessKey : this.definitions.get()) {
            if (keyId.equals(accessKey.getKeyId())) {
                return accessKey;
            }
        }

        return null;
    }

    private @Nullable AccessKeyDefinition findMatchingAccessKey(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        for (AccessKeyDefinition accessKey : this.definitions.get()) {
            if (accessKey.matches(item)) {
                return accessKey;
            }
        }

        return null;
    }

    private @Nullable String readAccessKeyId(@Nullable ItemStack item) {
        try {
            return ItemUtils.getDungeonAccessKeyId(item);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private @Nullable String readAccessKeyInstanceId(@Nullable ItemStack item) {
        try {
            return ItemUtils.getDungeonAccessKeyInstanceId(item);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private @Nullable String getTrackedIssuedAccessKeyId(
            @Nullable ItemStack item, boolean allowReserved) {
        String keyId = this.readAccessKeyId(item);
        String instanceId = this.readAccessKeyInstanceId(item);
        if (keyId == null
                || instanceId == null
                || this.invalidatedAccessKeyInstances.contains(instanceId)
                || (!allowReserved && this.reservedAccessKeyInstances.contains(instanceId))) {
            return null;
        }

        String issuedKeyId = this.issuedAccessKeyInstances.get(instanceId);
        return keyId.equals(issuedKeyId) ? keyId : null;
    }

    private boolean usesUniqueAccessKeyValidation() {
        boolean enabled =
                PluginConfigView.isUniqueAccessKeyValidationEnabled(this.runtime.config());
        if (this.uniqueAccessKeyValidationEnabled == null
                || this.uniqueAccessKeyValidationEnabled.booleanValue() != enabled) {
            this.uniqueAccessKeyValidationEnabled = enabled;
            this.accessKeyStateLoaded = false;
            this.issuedAccessKeyInstances.clear();
            this.reservedAccessKeyInstances.clear();
            this.invalidatedAccessKeyInstances.clear();
        }
        return enabled;
    }

    private void ensureUniqueAccessKeyStateLoaded() {
        if (!this.usesUniqueAccessKeyValidation() || this.accessKeyStateLoaded) {
            return;
        }

        try {
            this.loadAccessKeyState();
            this.pruneAccessKeyState();
        } catch (DungeonLoadException exception) {
            this.runtime
                    .environment()
                    .logger()
                    .error(
                            "Failed to load access key state for dungeon '{}' while unique"
                                    + " validation was enabled.",
                            this.worldName,
                            exception);
        }
    }

    private boolean invalidateIssuedAccessKeyInstance(@Nullable String instanceId) {
        this.ensureUniqueAccessKeyStateLoaded();
        if (instanceId == null || instanceId.isBlank()) {
            return false;
        }

        String removed = this.issuedAccessKeyInstances.remove(instanceId);
        if (removed == null && !this.invalidatedAccessKeyInstances.contains(instanceId)) {
            return false;
        }

        this.reservedAccessKeyInstances.remove(instanceId);
        this.invalidatedAccessKeyInstances.add(instanceId);
        this.saveAccessKeyState();
        return true;
    }

    public void revokeIssuedAccessKeysByDefinition(@Nullable String keyId) {
        if (!this.usesUniqueAccessKeyValidation()) {
            return;
        }

        this.ensureUniqueAccessKeyStateLoaded();
        if (keyId == null || keyId.isBlank()) {
            return;
        }

        boolean changed = false;
        List<String> revokedInstanceIds = new ArrayList<>();
        for (Map.Entry<String, String> entry :
                new ArrayList<>(this.issuedAccessKeyInstances.entrySet())) {
            if (!keyId.equals(entry.getValue())) {
                continue;
            }

            revokedInstanceIds.add(entry.getKey());
        }

        for (String instanceId : revokedInstanceIds) {
            changed |= this.issuedAccessKeyInstances.remove(instanceId) != null;
            changed |= this.reservedAccessKeyInstances.remove(instanceId);
            changed |= this.invalidatedAccessKeyInstances.add(instanceId);
        }

        if (changed) {
            this.saveAccessKeyState();
        }
    }

    public ItemStack isValidKey(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        if (!this.usesUniqueAccessKeyValidation()) {
            AccessKeyDefinition accessKey = this.findMatchingAccessKey(item);
            return accessKey == null ? null : accessKey.getItem();
        }

        this.ensureUniqueAccessKeyStateLoaded();
        String keyId = this.getTrackedIssuedAccessKeyId(item, false);
        if (keyId == null) {
            return null;
        }

        AccessKeyDefinition accessKey = this.findAccessKeyByStableId(keyId);
        if (accessKey == null || !accessKey.matches(item)) {
            return null;
        }

        return accessKey.getItem();
    }

    public synchronized @Nullable ItemStack issueAccessKey(
            @Nullable AccessKeyDefinition accessKey) {
        if (accessKey == null) {
            return null;
        }

        AccessKeyDefinition configuredKey = this.findAccessKeyByStableId(accessKey.getKeyId());
        if (configuredKey == null) {
            return null;
        }

        ItemStack issuedItem = configuredKey.createItemCopy();
        if (issuedItem == null || issuedItem.getType() == Material.AIR) {
            return null;
        }

        if (!this.usesUniqueAccessKeyValidation()) {
            return issuedItem;
        }

        this.ensureUniqueAccessKeyStateLoaded();
        if (!this.accessKeyStateLoaded) {
            return null;
        }
        String instanceId = UUID.randomUUID().toString();
        try {
            ItemUtils.tagDungeonAccessKeyInstance(issuedItem, instanceId);
        } catch (IllegalStateException ignored) {
            return null;
        }
        this.issuedAccessKeyInstances.put(instanceId, configuredKey.getKeyId());
        this.reservedAccessKeyInstances.remove(instanceId);
        this.invalidatedAccessKeyInstances.remove(instanceId);
        this.saveAccessKeyState();
        return issuedItem;
    }

    public synchronized boolean reserveIssuedAccessKey(@Nullable ItemStack item) {
        if (!this.usesUniqueAccessKeyValidation()) {
            return this.findMatchingAccessKey(item) != null;
        }

        this.ensureUniqueAccessKeyStateLoaded();
        if (this.getTrackedIssuedAccessKeyId(item, false) == null) {
            return false;
        }

        String instanceId = this.readAccessKeyInstanceId(item);
        if (instanceId == null
                || instanceId.isBlank()
                || !this.reservedAccessKeyInstances.add(instanceId)) {
            return false;
        }

        this.saveAccessKeyState();
        return true;
    }

    public synchronized boolean releaseReservedAccessKey(@Nullable ItemStack item) {
        if (!this.usesUniqueAccessKeyValidation()) {
            return this.findMatchingAccessKey(item) != null;
        }

        this.ensureUniqueAccessKeyStateLoaded();
        String instanceId = this.readAccessKeyInstanceId(item);
        if (instanceId == null
                || instanceId.isBlank()
                || !this.reservedAccessKeyInstances.remove(instanceId)) {
            return false;
        }

        this.saveAccessKeyState();
        return true;
    }

    public synchronized boolean isReservedAccessKey(@Nullable ItemStack item) {
        if (!this.usesUniqueAccessKeyValidation()) {
            return this.findMatchingAccessKey(item) != null;
        }

        this.ensureUniqueAccessKeyStateLoaded();
        String keyId = this.getTrackedIssuedAccessKeyId(item, true);
        String instanceId = this.readAccessKeyInstanceId(item);
        if (keyId == null
                || instanceId == null
                || !this.reservedAccessKeyInstances.contains(instanceId)) {
            return false;
        }

        AccessKeyDefinition accessKey = this.findAccessKeyByStableId(keyId);
        return accessKey != null && accessKey.matches(item);
    }

    /** A candidate ledger change; publication is allowed only after the joint journal commit. */
    public record Settlement(
            boolean valid,
            @Nullable ItemStack replacement,
            StateDocument.Prepared write,
            Runnable publish) {}

    public synchronized Settlement prepareSettlement(
            ItemStack item, boolean refund, boolean consume) {
        if (this.usesUniqueAccessKeyValidation()) this.ensureUniqueAccessKeyStateLoaded();
        var issued = new LinkedHashMap<>(this.issuedAccessKeyInstances);
        var reserved = new LinkedHashSet<>(this.reservedAccessKeyInstances);
        var invalidated = new LinkedHashSet<>(this.invalidatedAccessKeyInstances);
        this.planningSettlement = true;
        try {
            ItemStack replacement = null;
            boolean valid;
            if (refund) {
                // A reservation may already have been released (for example by startup cleanup).
                // The journal still owns the escrowed item and must be able to return it.
                this.releaseReservedAccessKey(item);
                valid = true;
            } else
                valid =
                        consume
                                ? this.consumeAccessKey(item)
                                : (replacement = this.rotateAccessKey(item)) != null;
            if (!valid) return new Settlement(false, null, null, () -> {});
            var nextIssued = new LinkedHashMap<>(this.issuedAccessKeyInstances);
            var nextReserved = new LinkedHashSet<>(this.reservedAccessKeyInstances);
            var nextInvalidated = new LinkedHashSet<>(this.invalidatedAccessKeyInstances);
            StateDocument.Prepared write = null;
            if (this.usesUniqueAccessKeyValidation()) {
                var candidate = new YamlConfiguration();
                candidate.loadFromString(this.accessKeyStateConfig.saveToString());
                candidate.set("issued_instances", nextIssued);
                candidate.set("reserved_instance_ids", new ArrayList<>(nextReserved));
                candidate.set("invalidated_instance_ids", new ArrayList<>(nextInvalidated));
                write = this.document.prepare(candidate.saveToString());
            }
            return new Settlement(
                    true,
                    replacement,
                    write,
                    () -> {
                        synchronized (this) {
                            this.issuedAccessKeyInstances = nextIssued;
                            this.reservedAccessKeyInstances = nextReserved;
                            this.invalidatedAccessKeyInstances = nextInvalidated;
                            this.rememberKeyLedger();
                        }
                    });
        } catch (InvalidConfigurationException failure) {
            throw new IllegalStateException("Cannot stage access key settlement", failure);
        } finally {
            this.planningSettlement = false;
            this.issuedAccessKeyInstances = issued;
            this.reservedAccessKeyInstances = reserved;
            this.invalidatedAccessKeyInstances = invalidated;
        }
    }

    public synchronized boolean consumeAccessKey(@Nullable ItemStack item) {
        if (!this.usesUniqueAccessKeyValidation()) {
            return this.findMatchingAccessKey(item) != null;
        }

        this.ensureUniqueAccessKeyStateLoaded();
        if (this.getTrackedIssuedAccessKeyId(item, true) == null) {
            return false;
        }

        this.reservedAccessKeyInstances.remove(this.readAccessKeyInstanceId(item));
        return this.invalidateIssuedAccessKeyInstance(this.readAccessKeyInstanceId(item));
    }

    public synchronized @Nullable ItemStack rotateAccessKey(@Nullable ItemStack item) {
        if (!this.usesUniqueAccessKeyValidation()) {
            AccessKeyDefinition accessKey = this.findMatchingAccessKey(item);
            return accessKey == null ? null : accessKey.createItemCopy();
        }

        this.ensureUniqueAccessKeyStateLoaded();
        String keyId = this.getTrackedIssuedAccessKeyId(item, true);
        if (keyId == null) {
            return null;
        }

        AccessKeyDefinition accessKey = this.findAccessKeyByStableId(keyId);
        if (accessKey == null) {
            return null;
        }

        String previousInstanceId = this.readAccessKeyInstanceId(item);
        if (previousInstanceId == null || previousInstanceId.isBlank()) {
            return null;
        }

        ItemStack replacementItem = accessKey.createItemCopy();
        if (replacementItem == null || replacementItem.getType() == Material.AIR) {
            return null;
        }

        String replacementInstanceId = UUID.randomUUID().toString();
        try {
            ItemUtils.tagDungeonAccessKeyInstance(replacementItem, replacementInstanceId);
        } catch (IllegalStateException ignored) {
            return null;
        }
        this.issuedAccessKeyInstances.remove(previousInstanceId);
        this.reservedAccessKeyInstances.remove(previousInstanceId);
        this.invalidatedAccessKeyInstances.add(previousInstanceId);
        this.issuedAccessKeyInstances.put(replacementInstanceId, keyId);
        this.reservedAccessKeyInstances.remove(replacementInstanceId);
        this.invalidatedAccessKeyInstances.remove(replacementInstanceId);
        this.saveAccessKeyState();
        return replacementItem;
    }
}
