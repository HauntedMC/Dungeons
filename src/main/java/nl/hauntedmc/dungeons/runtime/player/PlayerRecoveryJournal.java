package nl.hauntedmc.dungeons.runtime.player;

import java.util.*;
import java.util.UUID;
import java.util.function.Consumer;
import nl.hauntedmc.dungeons.persistence.StateDocument;
import nl.hauntedmc.dungeons.persistence.StateStore;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Database write-ahead journal. All operations run on the server thread. A transaction is
 * acknowledged only after its marker and resulting inventory are saved in vanilla player data.
 */
final class PlayerRecoveryJournal {
    private static final NamespacedKey APPLIED =
            new NamespacedKey("dungeons", "player_transaction");
    private final StateDocument document;
    private YamlConfiguration state;

    PlayerRecoveryJournal(UUID playerId) {
        this.document =
                RuntimeContext.runtime()
                        .stateStore()
                        .document(
                                new StateStore.Key(
                                        "player-recovery", playerId.toString(), playerId));
        state = new YamlConfiguration();
        try {
            document.loadInto(state);
            if (!state.getKeys(false).isEmpty()) {
                if (state.getInt("version") != 2)
                    throw new IllegalArgumentException("Unknown recovery journal version");
                items("refunds");
                if (state.contains("key-reservation")) {
                    if (state.getString("key-reservation.dungeon", "").isBlank()
                            || items("key-reservation.item").size() != 1)
                        throw new IllegalArgumentException("Invalid key reservation");
                }
                if (state.contains("recovery"))
                    PlayerRecoverySnapshot.read(state.getConfigurationSection("recovery.snapshot"));
                if (state.contains("recovery")) UUID.fromString(state.getString("recovery.id"));
                if (state.contains("delivery")) {
                    UUID.fromString(state.getString("delivery.id"));
                    PlayerRecoverySnapshot.validateInventoryContents(
                            PlayerRecoverySnapshot.decode(
                                    Objects.requireNonNull(state.getList("delivery.contents"))));
                }
            }
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Cannot load database player recovery journal " + playerId, failure);
        }
    }

    /** Escrows the removed key and checkpoints the post-removal inventory in one journal. */
    void reserveKey(Player player, String dungeon, ItemStack key) {
        requireServerThread();
        PlayerDataCheckpoint.requireStorage();
        if (hasPendingTransaction() || state.contains("key-reservation"))
            throw new IllegalStateException("Player already has an unfinished state transaction");
        var contents = PlayerRecoverySnapshot.copy(player.getInventory().getContents());
        PlayerRecoverySnapshot.validateInventoryContents(contents);
        commit(
                next -> {
                    next.set("key-reservation.dungeon", dungeon);
                    next.set(
                            "key-reservation.item",
                            PlayerRecoverySnapshot.encode(new ItemStack[] {key}));
                    next.set("delivery.id", UUID.randomUUID().toString());
                    next.set("delivery.contents", PlayerRecoverySnapshot.encode(contents));
                });
        if (player.isOnline() && !player.isDead()) finishDelivery(player);
    }

    /** Atomically turns escrow into a refund or settles it after accepted admission. */
    boolean settleKey(boolean refundOriginal, ItemStack replacement) {
        requireServerThread();
        if (!state.contains("key-reservation")) return false;
        var prepared = prepareKeySettlement(refundOriginal, replacement);
        StateDocument.commitTogether(prepared.write());
        prepared.publish().run();
        return true;
    }

    record Settlement(StateDocument.Prepared write, Runnable publish) {}

    Settlement prepareKeySettlement(boolean refundOriginal, ItemStack replacement) {
        requireServerThread();
        if (!hasKeyReservation())
            throw new IllegalStateException("Missing durable key reservation");
        var refunds = items("refunds");
        if (refundOriginal)
            refunds.add(
                    PlayerRecoverySnapshot.decode(
                            Objects.requireNonNull(state.getList("key-reservation.item")))[0]);
        else if (replacement != null) refunds.add(replacement.clone());
        try {
            var next = new YamlConfiguration();
            next.loadFromString(state.saveToString());
            next.set("version", 2);
            next.set("key-reservation", null);
            next.set("refunds", PlayerRecoverySnapshot.encode(refunds.toArray(ItemStack[]::new)));
            return new Settlement(document.prepare(next.saveToString()), () -> state = next);
        } catch (InvalidConfigurationException failure) {
            throw new IllegalStateException("Cannot stage key recovery", failure);
        }
    }

    boolean hasKeyReservation() {
        return state.contains("key-reservation");
    }

    boolean hasRecovery() {
        return state.contains("recovery");
    }

    boolean hasPendingTransaction() {
        return hasRecovery() || state.contains("delivery");
    }

    void begin(Player player) {
        requireServerThread();
        PlayerDataCheckpoint.requireStorage();
        if (player.isDead() || player.getHealth() <= 0) {
            throw new IllegalStateException("Player must respawn before state replacement");
        }
        if (hasPendingTransaction())
            throw new IllegalStateException("Player has unfinished recovery or key delivery");
        PlayerRecoverySnapshot snapshot = PlayerRecoverySnapshot.capture(player);
        commit(
                next -> {
                    next.set("recovery.id", UUID.randomUUID().toString());
                    snapshot.write(next.createSection("recovery.snapshot"));
                    // Validate before publishing a record that could block every later admission.
                    PlayerRecoverySnapshot.read(next.getConfigurationSection("recovery.snapshot"));
                });
    }

    /** Supplies the persisted baseline even when a session is reconstructed before recovery. */
    PlayerRecoverySnapshot originalSnapshot(Player player) {
        var data = Objects.requireNonNull(state.getConfigurationSection("recovery.snapshot"));
        var snapshot = PlayerRecoverySnapshot.read(data);
        UUID worldId = UUID.fromString(data.getString("world"));
        var world = player.getLocation().getWorld();
        if (world == null || !worldId.equals(world.getUID())) world = Bukkit.getWorld(worldId);
        snapshot.location()
                .setWorld(Objects.requireNonNull(world, "Recovery world is unavailable"));
        return snapshot;
    }

    List<ItemStack> refunds() {
        return items("refunds");
    }

    private List<ItemStack> items(String path) {
        if (state.contains(path) && !state.isList(path)) {
            throw new IllegalArgumentException("Invalid pending item list: " + path);
        }
        var list = state.getList(path, List.of());
        var items = new ArrayList<>(Arrays.asList(PlayerRecoverySnapshot.decode(list)));
        if (items.stream()
                .anyMatch(
                        item -> item == null || item.getType().isAir() || item.getAmount() <= 0)) {
            throw new IllegalArgumentException("Invalid pending item in " + path);
        }
        return items;
    }

    /** Reconfirms durability before the owning session releases its in-memory journal. */
    void checkpoint() {
        requireServerThread();
        if (!state.getKeys(false).isEmpty()) commit(next -> {});
    }

    void release() {
        state = new YamlConfiguration();
    }

    void addRefunds(ItemStack... incoming) {
        requireServerThread();
        var refunds = items("refunds");
        for (ItemStack item : incoming) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0)
                refunds.add(item.clone());
        }
        commit(
                next ->
                        next.set(
                                "refunds",
                                PlayerRecoverySnapshot.encode(refunds.toArray(ItemStack[]::new))));
    }

    /** Automatically returns reserved keys; overflow remains queued for automatic delivery. */
    void deliverRefunds(Player player) {
        requireServerThread();
        if (hasPendingTransaction()) return;
        for (ItemStack refund : items("refunds")) deliverRefund(player, refund);
    }

    boolean addStaffRefunds(UUID operation, ItemStack... incoming) {
        requireServerThread();
        String receipt = "staff-receipts." + operation;
        if (state.contains(receipt)) return false;
        var refunds = items("refunds");
        for (ItemStack item : incoming) {
            if (item == null || item.getType().isAir() || item.getAmount() < 1)
                throw new IllegalArgumentException("Invalid key item");
            refunds.add(item.clone());
        }
        commit(
                next -> {
                    next.set(
                            "refunds",
                            PlayerRecoverySnapshot.encode(refunds.toArray(ItemStack[]::new)));
                    next.set(receipt, System.currentTimeMillis());
                });
        return true;
    }

    boolean recover(Player player) {
        requireServerThread();
        // A crash during reservation can precede the native inventory removal checkpoint.
        finishDelivery(player);
        if (hasRecovery()) {
            String id = Objects.requireNonNull(state.getString("recovery.id"));
            if (!applied(player, id)) {
                var snapshot =
                        PlayerRecoverySnapshot.read(
                                state.getConfigurationSection("recovery.snapshot"));
                var world =
                        Bukkit.getWorld(
                                UUID.fromString(state.getString("recovery.snapshot.world")));
                snapshot.apply(player, world);
                markAndSave(player, id);
            }
            commit(next -> next.set("recovery", null));
        }
        settleKey(true, null);
        return true;
    }

    /** A normal departure has already applied the configured keep/restore policies. */
    void acknowledgeDeparture(Player player) {
        requireServerThread();
        if (!hasRecovery()) return;
        PlayerRecoverySnapshot.read(state.getConfigurationSection("recovery.snapshot"))
                .restoreMovement(player);
        markAndSave(player, Objects.requireNonNull(state.getString("recovery.id")));
        commit(next -> next.set("recovery", null));
    }

    boolean deliverRefund(Player player, ItemStack requested) {
        requireServerThread();
        // Offline saves may silently do nothing, and respawn can replace a dead player's inventory.
        // Keep both the refund and any unfinished delivery journal untouched until the player is
        // alive.
        if (!player.isOnline() || player.isDead() || player.getHealth() <= 0) return false;
        if (hasRecovery()) return false;
        finishDelivery(player);
        List<ItemStack> refunds = items("refunds");
        int index = -1;
        for (int i = 0; i < refunds.size(); i++) {
            ItemStack refund = refunds.get(i);
            if (refund.getAmount() == requested.getAmount() && refund.isSimilar(requested)) {
                index = i;
                break;
            }
        }
        if (index < 0) return false; // already delivered
        ItemStack item = refunds.get(index);
        ItemStack[] contents = PlayerRecoverySnapshot.copy(player.getInventory().getContents());
        int remaining = item.getAmount();
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack existing = contents[slot];
            if (existing != null && existing.isSimilar(item)) {
                int amount =
                        Math.min(
                                remaining,
                                Math.max(0, existing.getMaxStackSize() - existing.getAmount()));
                existing.setAmount(existing.getAmount() + amount);
                remaining -= amount;
            }
        }
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            if (contents[slot] == null || contents[slot].getType().isAir()) {
                int amount = Math.min(remaining, item.getMaxStackSize());
                contents[slot] = item.clone();
                contents[slot].setAmount(amount);
                remaining -= amount;
            }
        }
        if (remaining == item.getAmount()) return false;
        if (remaining == 0) refunds.remove(index);
        else refunds.get(index).setAmount(remaining);
        commit(
                next -> {
                    next.set(
                            "refunds",
                            PlayerRecoverySnapshot.encode(refunds.toArray(ItemStack[]::new)));
                    next.set("delivery.id", UUID.randomUUID().toString());
                    next.set("delivery.contents", PlayerRecoverySnapshot.encode(contents));
                });
        finishDelivery(player);
        return true;
    }

    private void finishDelivery(Player player) {
        if (!state.contains("delivery")) return;
        String id = Objects.requireNonNull(state.getString("delivery.id"));
        if (!applied(player, id)) {
            PlayerRecoverySnapshot.restoreContents(
                    player.getInventory(),
                    PlayerRecoverySnapshot.decode(
                            Objects.requireNonNull(state.getList("delivery.contents"))));
            markAndSave(player, id);
        }
        commit(next -> next.set("delivery", null));
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Player state transactions require the server thread");
    }

    private static boolean applied(Player player, String id) {
        return id.equals(
                player.getPersistentDataContainer().get(APPLIED, PersistentDataType.STRING));
    }

    private static void markAndSave(Player player, String id) {
        var data = player.getPersistentDataContainer();
        String previous = data.get(APPLIED, PersistentDataType.STRING);
        data.set(APPLIED, PersistentDataType.STRING, id);
        try {
            player.saveData();
            PlayerDataCheckpoint.verify(player, id);
        } catch (RuntimeException failure) {
            if (previous == null) data.remove(APPLIED);
            else data.set(APPLIED, PersistentDataType.STRING, previous);
            throw failure;
        }
    }

    private void commit(Consumer<YamlConfiguration> mutation) {
        try {
            var next = new YamlConfiguration();
            next.loadFromString(state.saveToString());
            next.set("version", 2);
            mutation.accept(next);
            document.save(next.saveToString());
            state = next;
        } catch (InvalidConfigurationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
