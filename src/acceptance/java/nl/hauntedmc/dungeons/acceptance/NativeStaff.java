package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import nl.hauntedmc.dungeons.runtime.staff.StaffOperations;

import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;

/**
 * Verifies packaged permissions, command binding and persisted admission policy on native Paper.
 */
final class NativeStaff {
    private NativeStaff() {}

    static java.util.concurrent.CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        java.nio.file.Files.createDirectories(test.getDataFolder().toPath());
        var ops = test.runtime().staffOperations();
        require(ops != null, "Staff operations not composed");
        require(
                Bukkit.getPluginManager().getPermission("dungeons.staff.rescue") != null,
                "Staff permissions missing from packaged plugin.yml");
        var console = Bukkit.getConsoleSender();
        require(
                console.hasPermission("dungeons.role.operator"),
                "Console cannot use operator preset");
        require(
                Bukkit.dispatchCommand(console, "dungeon admin overview"),
                "Staff command not bound");
        var permissible =
                new org.bukkit.permissions.PermissibleBase(
                        new org.bukkit.permissions.ServerOperator() {
                            public boolean isOp() {
                                return false;
                            }

                            public void setOp(boolean value) {
                                throw new UnsupportedOperationException();
                            }
                        });
        var attachment = permissible.addAttachment(test);
        var scoped =
                (org.bukkit.command.CommandSender)
                        java.lang.reflect.Proxy.newProxyInstance(
                                org.bukkit.command.CommandSender.class.getClassLoader(),
                                new Class<?>[] {org.bukkit.command.CommandSender.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "hasPermission" ->
                                                    permissible.hasPermission((String) args[0]);
                                            case "isPermissionSet" ->
                                                    permissible.isPermissionSet((String) args[0]);
                                            default -> null;
                                        });
        try {
            attachment.setPermission("dungeons.staff.edit.crypt", true);
            require(
                    nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.allows(
                            scoped, "edit", "crypt"),
                    "Scoped grant is hidden by Bukkit defaults");
            attachment.setPermission("dungeons.role.builder", true);
            require(
                    permissible.hasPermission("dungeons.staff.edit"),
                    "Builder preset did not grant editing");
            require(
                    !permissible.hasPermission("dungeons.staff.scripts"),
                    "Builder preset granted console scripts");
            require(
                    !permissible.hasPermission("dungeons.bypass.commands"),
                    "Builder preset bypassed gameplay commands");
            attachment.setPermission("dungeons.staff.edit.crypt", false);
            require(
                    !nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.allows(
                            scoped, "edit", "crypt"),
                    "Scoped denial did not override preset");
        } finally {
            attachment.remove();
            permissible.clearPermissions();
        }
        var dungeon = WorldFixtures.definition(test, "acceptance_staff", "static");
        ops.maintenance(console, dungeon.getWorldName(), true, "Native staff acceptance");
        require(!dungeon.isEnabled(), "Maintenance did not stop admission");
        require(
                new StaffOperations(test.runtime()).maintenance(dungeon.getWorldName()),
                "Maintenance not persisted");
        ops.maintenance(console, dungeon.getWorldName(), false, "Acceptance complete");
        // Use real Paper inventories/item metadata with only the network-facing player substituted.
        var opened =
                new java.util.concurrent.atomic.AtomicReference<org.bukkit.inventory.Inventory>();
        java.util.UUID syntheticId = java.util.UUID.randomUUID();
        var player =
                (org.bukkit.entity.Player)
                        java.lang.reflect.Proxy.newProxyInstance(
                                org.bukkit.entity.Player.class.getClassLoader(),
                                new Class<?>[] {org.bukkit.entity.Player.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getUniqueId" -> syntheticId;
                                            case "getName" -> "StaffMenuAcceptance";
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            case "hasPermission", "isOnline" -> true;
                                            case "isPermissionSet" -> false;
                                            case "openInventory" -> {
                                                opened.set(
                                                        (org.bukkit.inventory.Inventory) args[0]);
                                                yield null;
                                            }
                                            default -> null;
                                        });
        var command = java.util.Objects.requireNonNull(Bukkit.getPluginCommand("dungeon"));
        command.execute(player, "dungeon", new String[] {"admin"});
        require(
                opened.get() != null && opened.get().getSize() == 54,
                "Native staff dashboard did not render");
        require(
                opened.get().getItem(0) != null && opened.get().getItem(0).hasItemMeta(),
                "Dashboard metadata missing");
        var dashboard = opened.get();
        command.execute(
                player, "dungeon", new String[] {"admin", "dungeon", dungeon.getWorldName()});
        require(opened.get() != dashboard, "Dungeon detail screen did not open");

        var output = new java.util.ArrayList<String>();
        var sender =
                (org.bukkit.command.CommandSender)
                        java.lang.reflect.Proxy.newProxyInstance(
                                org.bukkit.command.CommandSender.class.getClassLoader(),
                                new Class<?>[] {org.bukkit.command.CommandSender.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getName" -> "NativeStaffCorrections";
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            case "hasPermission", "isOp" -> true;
                                            case "isPermissionSet" -> false;
                                            case "sendMessage" -> {
                                                for (Object arg : args) {
                                                    if (arg instanceof String text)
                                                        output.add(text);
                                                    else if (arg
                                                            instanceof
                                                            net.kyori.adventure.text.Component
                                                                    component)
                                                        output.add(
                                                                net.kyori.adventure.text.serializer
                                                                        .plain
                                                                        .PlainTextComponentSerializer
                                                                        .plainText()
                                                                        .serialize(component));
                                                }
                                                yield null;
                                            }
                                            default -> null;
                                        });
        String[] compensation = {
            "admin", "compensate", syntheticId.toString(), "DIAMOND", "3", "Native compensation"
        };
        command.execute(sender, "dungeon", compensation);
        String confirmation =
                output.stream()
                        .filter(
                                line ->
                                        line.contains("/dungeon admin ")
                                                && line.contains("--confirm "))
                        .reduce((a, b) -> b)
                        .orElseThrow();
        String[] confirmed =
                confirmation.substring(confirmation.indexOf("/dungeon ") + 9).split(" ");
        command.execute(sender, "dungeon", confirmed);
        require(
                nl.hauntedmc.dungeons.runtime.player.StaffPlayerRecords.inspect(
                                test.runtime(), syntheticId)
                        .contains("claimable stacks=1"),
                "Native compensation was not persisted");
        command.execute(sender, "dungeon", confirmed);
        require(
                nl.hauntedmc.dungeons.runtime.player.StaffPlayerRecords.inspect(
                                test.runtime(), syntheticId)
                        .contains("claimable stacks=1"),
                "Confirmation replay duplicated native compensation");

        return CompletableFuture.completedFuture(null);
    }
}
