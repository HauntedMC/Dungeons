package nl.hauntedmc.dungeons.runtime.player;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Synchronous write-ahead journal. All operations run on the server thread. A transaction is
 * acknowledged only after its marker and resulting inventory are saved in vanilla player data.
 */
final class PlayerRecoveryJournal {
    private static final NamespacedKey APPLIED =
            new NamespacedKey("dungeons", "player_transaction");
    private final Path file;
    private YamlConfiguration state;

    PlayerRecoveryJournal(Path file) {
        this.file = file.toAbsolutePath().normalize();
        state = new YamlConfiguration();
        if (Files.exists(file)) {
            try {
                state.load(file.toFile());
                if (state.getInt("version") != 2)
                    throw new IllegalArgumentException("Unknown recovery journal version");
                rewards();
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
            } catch (Exception failure) {
                throw new IllegalStateException(
                        "Cannot load player recovery journal " + file, failure);
            }
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
        var refunds = items("refunds");
        if (refundOriginal)
            refunds.add(
                    PlayerRecoverySnapshot.decode(
                            Objects.requireNonNull(state.getList("key-reservation.item")))[0]);
        else if (replacement != null) refunds.add(replacement.clone());
        commit(
                next -> {
                    next.set("key-reservation", null);
                    next.set(
                            "refunds",
                            PlayerRecoverySnapshot.encode(refunds.toArray(ItemStack[]::new)));
                });
        return true;
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
            throw new IllegalStateException("Player has unfinished recovery or reward delivery");
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

    List<ItemStack> rewards() {
        var result = items("rewards");
        result.addAll(items("refunds"));
        return result;
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

    /** Automatically returns reserved keys; overflow remains durably claimable as a reward. */
    void deliverRefunds(Player player) {
        requireServerThread();
        if (hasPendingTransaction()) return;
        for (ItemStack refund : items("refunds")) claim(player, refund, "refunds");
    }

    void addRewards(ItemStack... items) {
        requireServerThread();
        List<ItemStack> rewards = items("rewards");
        for (ItemStack item : items)
            if (item != null && !item.getType().isAir() && item.getAmount() > 0)
                rewards.add(item.clone());
        commit(
                next ->
                        next.set(
                                "rewards",
                                PlayerRecoverySnapshot.encode(rewards.toArray(ItemStack[]::new))));
    }

    boolean addStaffRewards(UUID operation, ItemStack... incoming) {
        requireServerThread();
        String receipt = "staff-receipts." + operation;
        if (state.contains(receipt)) return false;
        var rewards = items("rewards");
        for (ItemStack item : incoming) {
            if (item == null || item.getType().isAir() || item.getAmount() < 1)
                throw new IllegalArgumentException("Invalid compensation item");
            rewards.add(item.clone());
        }
        commit(
                next -> {
                    next.set(
                            "rewards",
                            PlayerRecoverySnapshot.encode(rewards.toArray(ItemStack[]::new)));
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

    boolean claim(Player player, ItemStack requested) {
        if (claim(player, requested, "rewards")) return true;
        return claim(player, requested, "refunds");
    }

    private boolean claim(Player player, ItemStack requested, String source) {
        requireServerThread();
        // Offline saves may silently do nothing, and respawn can replace a dead player's inventory.
        // Keep both the reward and any unfinished delivery journal untouched until a live claim.
        if (!player.isOnline() || player.isDead() || player.getHealth() <= 0) return false;
        if (hasRecovery()) return false;
        finishDelivery(player);
        List<ItemStack> rewards = items(source);
        int index = -1;
        for (int i = 0; i < rewards.size(); i++) {
            ItemStack reward = rewards.get(i);
            if (reward.getAmount() == requested.getAmount() && reward.isSimilar(requested)) {
                index = i;
                break;
            }
        }
        if (index < 0) return false; // stale GUI click
        ItemStack item = rewards.get(index);
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
        if (remaining == 0) rewards.remove(index);
        else rewards.get(index).setAmount(remaining);
        commit(
                next -> {
                    next.set(
                            source,
                            PlayerRecoverySnapshot.encode(rewards.toArray(ItemStack[]::new)));
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
        Path temporary = null;
        try {
            var next = new YamlConfiguration();
            next.loadFromString(state.saveToString());
            next.set("version", 2);
            mutation.accept(next);
            byte[] bytes = next.saveToString().getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(file.toAbsolutePath().getParent());
            temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".player-", ".tmp");
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            state = next;
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "Cannot persist player recovery journal " + file, failure);
        } catch (org.bukkit.configuration.InvalidConfigurationException failure) {
            throw new IllegalStateException(failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    /* Preserve primary failure. */
                }
            }
        }
    }
}
