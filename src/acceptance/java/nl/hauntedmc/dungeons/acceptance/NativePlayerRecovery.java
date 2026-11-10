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

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Exercises native Paper player saves, loads, inventory codecs and the journal; network actions use a controlled boundary.
 */
final class NativePlayerRecovery {
    private NativePlayerRecovery() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        boolean[] online = {true};
        PlayerInventory inventory = nativeInventory();
        test.getLogger().info("Native player inventory slots: " + inventory.getSize());
        UUID id = UUID.randomUUID();
        // A standalone Paper backend creates canonical identities after join, not pre-login.
        // Prefetch must probe the database without rejecting or inventing a first-time identity.
        test.runtime().stateStore().prefetch(id).get(5, TimeUnit.SECONDS);
        require(test.runtime().database().database().players().findIdentity(id).toCompletableFuture()
                .get(5, TimeUnit.SECONDS).isEmpty(), "Dungeons invented a canonical identity");
        boolean waiting = false;
        try { test.runtime().stateStore().requirePlayerReady(id); }
        catch (IllegalArgumentException expected) { waiting = true; }
        require(waiting, "First-time gameplay was admitted before identity readiness");
        test.seedPlayer(id);
        Player nativePlayer = nativePlayer(id);
        var pdc = nativePlayer.getPersistentDataContainer();
        var attribute =
                proxy(
                        AttributeInstance.class,
                        (method, args) -> method.equals("getValue") ? 20.0 : null);
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
                                    saveNativePlayer(nativePlayer, inventory);
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
        var initial = inventory.getContents();
        initial[0] = original.clone();
        initial[40] = new ItemStack(Material.SHIELD);
        initial[41] = new ItemStack(Material.LEATHER_HORSE_ARMOR);
        initial[42] = new ItemStack(Material.SADDLE);
        inventory.setContents(initial);
        inventory.setArmorContents(new ItemStack[] {new ItemStack(Material.DIAMOND_BOOTS), null, null, null});
        var registry = test.runtime().playerSessions();
        var first = registry.put(player);
        require(
                first.reserveAccessKey("native-refund", new ItemStack(Material.TRIPWIRE_HOOK, 2)),
                "Could not reserve native refund fixture");
        require(first.prepareRecovery(), "Native recovery record could not be prepared");
        inventory.clear();
        values.put("GameMode", GameMode.CREATIVE);
        values.put("Exp", 0f);
        online[0] = false;
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
                                                        + TimeUnit.NANOSECONDS
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
                                        inventory.getContents()[0].equals(original),
                                        "Native recovery lost original item components");
                                require(
                                        inventory.getContents()[40].getType() == Material.SHIELD,
                                        "Native recovery lost offhand");
                                require(inventory.getContents()[41].getType() == Material.LEATHER_HORSE_ARMOR,
                                        "Native recovery lost body equipment");
                                require(inventory.getContents()[42].getType() == Material.SADDLE,
                                        "Native recovery lost saddle equipment");
                                require(
                                        inventory.getArmorContents()[0].getType() == Material.DIAMOND_BOOTS,
                                        "Native recovery lost armor");
                                require(
                                        player.getGameMode() == GameMode.SURVIVAL
                                                && player.getExp() == .6f,
                                        "Native recovery lost game mode or XP");
                                var again =
                                        new DungeonPlayerSession(
                                                test.runtime().environment().plugin(), player);
                                require(
                                        again.recoverPendingState(),
                                        "Native delivery was not acknowledged");
                                require(
                                        Arrays.stream(inventory.getContents())
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
                                for (int attempt = 0; attempt < 3; attempt++) {
                                    require(again.prepareRecovery(), "Repeat entry rejected after a completed departure");
                                    inventory.clear();
                                    values.put("GameMode", GameMode.ADVENTURE);
                                    again.completeDeparture(true, true);
                                    require(inventory.getContents()[0].equals(original),
                                            "Departure lost the original inventory");
                                    again = new DungeonPlayerSession(test.runtime().environment().plugin(), player);
                                    require(again.recoverPendingState(), "Reconnect failed after a completed departure");
                                }
                                try {
                                    Files.writeString(test.getDataFolder().toPath().resolve("database-player.txt"), id.toString());
                                } catch (IOException failure) { throw new CompletionException(failure); }
                                test.getLogger().info("Native save/load and three entry/departure/reconnect cycles passed");
                            } finally {
                                test.runtime()
                                        .config()
                                        .set("sessions.offline_retention_seconds", grace);
                            }
                        });
    }

    /** Uses the actual native player serializer, including its configured storage directory. */
    private static Player nativePlayer(UUID id) throws Exception {
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = Bukkit.getWorlds().getFirst().getClass().getMethod("getHandle")
                .invoke(Bukkit.getWorlds().getFirst());
        Class<?> serverType = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelType = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileType = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> informationType = Class.forName("net.minecraft.server.level.ClientInformation");
        Object profile = profileType.getConstructor(UUID.class, String.class).newInstance(id, "RecoveryProbe");
        Object information = informationType.getMethod("createDefault").invoke(null);
        Object handle = Class.forName("net.minecraft.server.level.ServerPlayer")
                .getConstructor(serverType, levelType, profileType, informationType)
                .newInstance(server, level, profile, information);
        return (Player) handle.getClass().getMethod("getBukkitEntity").invoke(handle);
    }

    private static void saveNativePlayer(Player player, PlayerInventory source) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object inventory = handle.getClass().getMethod("getInventory").invoke(handle);
            Class<?> itemType = Class.forName("net.minecraft.world.item.ItemStack");
            var set = inventory.getClass().getMethod("setItem", int.class, itemType);
            var convert = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
                    .getMethod("asNMSCopy", ItemStack.class);
            var contents = source.getContents();
            for (int slot = 0; slot < contents.length; slot++)
                set.invoke(inventory, slot, convert.invoke(null, contents[slot]));
            player.saveData();
            // A separate native player must read the marker and items through Paper's real loader.
            Player reloaded = nativePlayer(player.getUniqueId());
            reloaded.loadData();
            var transaction = new NamespacedKey("dungeons", "player_transaction");
            require(Objects.equals(
                    player.getPersistentDataContainer().get(transaction, PersistentDataType.STRING),
                    reloaded.getPersistentDataContainer().get(transaction, PersistentDataType.STRING)),
                    "Native reload did not retain the transaction marker");
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack expected = contents[slot];
                ItemStack actual = reloaded.getInventory().getItem(slot);
                require(expected == null || expected.getType().isAir()
                                ? actual == null || actual.getType().isAir()
                                : actual != null && expected.isSimilar(actual)
                                        && expected.getAmount() == actual.getAmount(),
                        "Native reload lost inventory slot " + slot);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Native player serialization failed", failure);
        }
    }

    /** Uses Paper's real inventory and item/equipment containers, excluding network notifications. */
    private static PlayerInventory nativeInventory() throws Exception {
        Class<?> playerType = Class.forName("net.minecraft.world.entity.player.Player");
        Class<?> equipmentType = Class.forName("net.minecraft.world.entity.EntityEquipment");
        Class<?> inventoryType = Class.forName("net.minecraft.world.entity.player.Inventory");
        Class<?> itemType = Class.forName("net.minecraft.world.item.ItemStack");
        Object handle = inventoryType.getConstructor(playerType, equipmentType)
                .newInstance(null, equipmentType.getConstructor().newInstance());
        PlayerInventory nativeView = (PlayerInventory) Class.forName(
                "org.bukkit.craftbukkit.inventory.CraftInventoryPlayer")
                .getConstructor(inventoryType).newInstance(handle);
        var set = inventoryType.getMethod("setItem", int.class, itemType);
        var convert = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
                .getMethod("asNMSCopy", ItemStack.class);
        return proxy(PlayerInventory.class, (method, args) -> {
            try {
                return switch (method) {
                    case "getSize" -> nativeView.getSize();
                    case "getContents" -> nativeView.getContents();
                    case "getArmorContents" -> nativeView.getArmorContents();
                    case "getHeldItemSlot" -> nativeView.getHeldItemSlot();
                    case "setHeldItemSlot" -> {
                        inventoryType.getMethod("setSelectedSlot", int.class).invoke(handle, args[0]);
                        yield null;
                    }
                    case "setContents", "setArmorContents", "clear" -> {
                        int offset = method.equals("setArmorContents") ? 36 : 0;
                        ItemStack[] items = method.equals("clear")
                                ? new ItemStack[nativeView.getSize()] : (ItemStack[]) args[0];
                        for (int slot = 0; slot < items.length; slot++)
                            set.invoke(handle, offset + slot, convert.invoke(null, items[slot]));
                        yield null;
                    }
                    default -> throw new IllegalArgumentException("Unexpected inventory call: " + method);
                };
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
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
