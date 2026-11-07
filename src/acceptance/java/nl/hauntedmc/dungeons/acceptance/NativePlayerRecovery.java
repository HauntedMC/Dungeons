package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;

import org.bukkit.*;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * Exercises the native Paper item codec and journal; player/network I/O uses a controlled boundary.
 */
final class NativePlayerRecovery {
    private NativePlayerRecovery() {}

    static CompletionStage<Void> run(AcceptancePlugin test) {
        boolean[] online = {true};
        ItemStack[][] contents = {new ItemStack[41]};
        ItemStack[][] armor = {new ItemStack[4]};
        var inventory =
                proxy(
                        PlayerInventory.class,
                        (method, args) ->
                                switch (method) {
                                    case "getContents" -> contents[0].clone();
                                    case "getArmorContents" -> armor[0].clone();
                                    case "getHeldItemSlot" -> 2;
                                    case "setContents" -> {
                                        contents[0] = ((ItemStack[]) args[0]).clone();
                                        yield null;
                                    }
                                    case "setArmorContents" -> {
                                        armor[0] = ((ItemStack[]) args[0]).clone();
                                        yield null;
                                    }
                                    default -> null;
                                });
        var pdc = new ItemStack(Material.STONE).getItemMeta().getPersistentDataContainer();
        var attribute =
                proxy(
                        AttributeInstance.class,
                        (method, args) -> method.equals("getValue") ? 20.0 : null);
        UUID id = UUID.randomUUID();
        Map<String, Object> values = new HashMap<>();
        values.put("GameMode", GameMode.SURVIVAL);
        values.put("Health", 17.0);
        values.put("FoodLevel", 13);
        values.put("Exp", .6f);
        values.put("Level", 23);
        values.put("TotalExperience", 1000);
        values.put("Saturation", 4f);
        values.put("Exhaustion", 1f);
        values.put("FlySpeed", .1f);
        values.put("WalkSpeed", .2f);
        values.put("FireTicks", 0);
        values.put("FallDistance", 0f);
        values.put("RemainingAir", 300);
        Location[] location = {new Location(Bukkit.getWorlds().getFirst(), 0, 70, 0)};
        var player =
                proxy(
                        Player.class,
                        (method, args) -> {
                            if (method.startsWith("set") && args.length == 1) {
                                values.put(method.substring(3), args[0]);
                                return null;
                            }
                            if (method.startsWith("get") && values.containsKey(method.substring(3)))
                                return values.get(method.substring(3));
                            return switch (method) {
                                case "getUniqueId" -> id;
                                case "getName" -> "RecoveryAcceptance";
                                case "getInventory" -> inventory;
                                case "getPersistentDataContainer" -> pdc;
                                case "getLocation" -> location[0].clone();
                                case "getAttribute" -> attribute;
                                case "getActivePotionEffects" -> List.of();
                                case "isOnline" -> online[0];
                                case "addPotionEffects" -> true;
                                case "isDead", "isFlying", "getAllowFlight" -> false;
                                case "teleport" -> {
                                    location[0] = ((Location) args[0]).clone();
                                    yield true;
                                }
                                case "saveData" -> {
                                    try {
                                        var file =
                                                Bukkit.getServer()
                                                        .getLevelDirectory()
                                                        .resolve("playerdata")
                                                        .resolve(id + ".dat");
                                        java.nio.file.Files.createDirectories(file.getParent());
                                        try (var out =
                                                new java.io.DataOutputStream(
                                                        new java.util.zip.GZIPOutputStream(
                                                                java.nio.file.Files.newOutputStream(
                                                                        file)))) {
                                            out.writeByte(10);
                                            out.writeUTF("");
                                            out.writeByte(10);
                                            out.writeUTF("BukkitValues");
                                            out.writeByte(8);
                                            out.writeUTF("dungeons:player_transaction");
                                            out.writeUTF(
                                                    pdc.get(
                                                            new NamespacedKey(
                                                                    "dungeons",
                                                                    "player_transaction"),
                                                            PersistentDataType.STRING));
                                            out.writeByte(0);
                                            out.writeByte(0);
                                        }
                                    } catch (java.io.IOException failure) {
                                        throw new java.io.UncheckedIOException(failure);
                                    }
                                    yield null;
                                }
                                case "kick" ->
                                        throw new AssertionError(
                                                "Native recovery unexpectedly rejected player");
                                default -> null;
                            };
                        });
        var token = new NamespacedKey("dungeonsacceptance", "recovery_item");
        var original = new ItemStack(Material.DIAMOND, 7);
        original.editMeta(
                meta -> {
                    meta.displayName(Component.text("Original | inventory !"));
                    meta.getPersistentDataContainer()
                            .set(token, PersistentDataType.STRING, "preserved");
                });
        contents[0][0] = original.clone();
        contents[0][40] = new ItemStack(Material.SHIELD);
        armor[0][0] = new ItemStack(Material.DIAMOND_BOOTS);
        var registry = test.runtime().playerSessions();
        var first = registry.put(player);
        require(first.prepareRecovery(), "Native recovery record could not be prepared");
        first.addReward(new ItemStack(Material.EMERALD, 5));
        contents[0] = new ItemStack[41];
        armor[0] = new ItemStack[4];
        values.put("GameMode", GameMode.CREATIVE);
        values.put("Exp", 0f);
        online[0] = false;
        require(
                first.reserveAccessKey("native-refund", new ItemStack(Material.TRIPWIRE_HOOK, 2)),
                "Could not reserve native refund fixture");
        require(first.refundReservedAccessKey(), "Could not persist native offline refund");
        Object grace = test.runtime().config().get("sessions.offline_retention_seconds");
        test.runtime().config().set("sessions.offline_retention_seconds", 1);
        registry.disconnected(player);
        long disconnectedAt = System.nanoTime();
        return test.afterTicks(25)
                .thenCompose(
                        ignored -> {
                            registry.expireOfflineSessions();
                            if (!first.isExpired()) {
                                test.getLogger()
                                        .info(
                                                "Session expiry pending after 25 ticks; elapsed="
                                                        + java.util.concurrent.TimeUnit.NANOSECONDS
                                                                .toMillis(
                                                                        System.nanoTime()
                                                                                - disconnectedAt)
                                                        + "ms");
                            }
                            // The retention policy uses monotonic time. Catch-up ticks during
                            // startup
                            // can execute faster than wall time, so a fixed tick delay is not a
                            // deadline.
                            return test.await(
                                    "offline session expiry",
                                    () -> {
                                        registry.expireOfflineSessions();
                                        return first.isExpired() && registry.get(id) == null;
                                    });
                        })
                .thenRun(
                        () -> {
                            try {
                                registry.expireOfflineSessions();
                                require(
                                        first.isExpired() && registry.get(id) == null,
                                        "Offline session did not expire");
                                online[0] = true;
                                var restarted = registry.put(player);
                                require(
                                        restarted.recoverPendingState(),
                                        "Native player recovery failed");
                                require(
                                        contents[0][0].equals(original),
                                        "Native recovery lost original item components");
                                require(
                                        contents[0][40].getType() == Material.SHIELD,
                                        "Native recovery lost offhand");
                                require(
                                        armor[0][0].getType() == Material.DIAMOND_BOOTS,
                                        "Native recovery lost armor");
                                require(
                                        player.getGameMode() == GameMode.SURVIVAL
                                                && player.getExp() == .6f,
                                        "Native recovery lost game mode or XP");
                                require(
                                        restarted.getRewardItems().size() == 1,
                                        "Native reward did not survive session recreation");
                                require(
                                        restarted.claimReward(
                                                restarted.getRewardItems().getFirst()),
                                        "Native reward delivery failed");
                                require(
                                        Arrays.stream(contents[0])
                                                        .filter(Objects::nonNull)
                                                        .filter(
                                                                stack ->
                                                                        stack.getType()
                                                                                == Material.EMERALD)
                                                        .mapToInt(ItemStack::getAmount)
                                                        .sum()
                                                == 5,
                                        "Native reward delivery lost items");
                                var again =
                                        new DungeonPlayerSession(
                                                test.runtime().environment().plugin(), player);
                                require(
                                        again.recoverPendingState()
                                                && again.getRewardItems().isEmpty(),
                                        "Native delivery was not acknowledged");
                                require(
                                        Arrays.stream(contents[0])
                                                        .filter(Objects::nonNull)
                                                        .filter(
                                                                stack ->
                                                                        stack.getType()
                                                                                == Material
                                                                                        .TRIPWIRE_HOOK)
                                                        .mapToInt(ItemStack::getAmount)
                                                        .sum()
                                                == 2,
                                        "Offline key refund was lost or duplicated during"
                                                + " expiry/recovery");
                            } finally {
                                test.runtime()
                                        .config()
                                        .set("sessions.offline_retention_seconds", grace);
                            }
                        });
    }

    private interface Calls {
        Object invoke(String method, Object[] arguments);
    }

    private static <T> T proxy(Class<T> type, Calls calls) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (instance, method, args) -> {
                            if (method.getName().equals("hashCode"))
                                return System.identityHashCode(instance);
                            if (method.getName().equals("equals")) return instance == args[0];
                            if (method.getName().equals("toString"))
                                return type.getSimpleName() + "Acceptance";
                            Object value =
                                    calls.invoke(
                                            method.getName(), args == null ? new Object[0] : args);
                            if (value == null && method.getReturnType() == boolean.class)
                                return false;
                            if (value == null && method.getReturnType() == int.class) return 0;
                            return value;
                        }));
    }
}
