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
                Bukkit.dispatchCommand(console, "dungeon server status"),
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
        // Staff routes must render chat output for players without opening inventories.
        var opened = new java.util.concurrent.atomic.AtomicBoolean();
        var playerOutput = new java.util.ArrayList<String>();
        java.util.UUID syntheticId = java.util.UUID.randomUUID();
        test.seedPlayer(syntheticId);
        var player =
                (org.bukkit.entity.Player)
                        java.lang.reflect.Proxy.newProxyInstance(
                                org.bukkit.entity.Player.class.getClassLoader(),
                                new Class<?>[] {org.bukkit.entity.Player.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getUniqueId" -> syntheticId;
                                            case "getName" -> "StaffCommandAcceptance";
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            case "hasPermission", "isOnline" -> true;
                                            case "isPermissionSet" -> false;
                                            case "openInventory" -> {
                                                opened.set(true);
                                                yield null;
                                            }
                                            case "sendMessage" -> {
                                                for (Object arg : args) {
                                                    if (arg instanceof String text) playerOutput.add(text);
                                                    if (arg instanceof net.kyori.adventure.text.Component component)
                                                        playerOutput.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component));
                                                }
                                                yield null;
                                            }
                                            default -> null;
                                        });
        var command = java.util.Objects.requireNonNull(Bukkit.getPluginCommand("dungeon"));
        command.execute(player, "dungeon", new String[] {"server"});
        require(!opened.get(), "Staff status opened an inventory");
        require(playerOutput.stream().anyMatch(line -> line.contains("Dungeons:")),
                "Staff status did not render chat output");
        playerOutput.clear();
        command.execute(player, "dungeon", new String[] {"map", "info", dungeon.getWorldName()});
        require(!opened.get(), "Map info opened an inventory");
        require(playerOutput.stream().anyMatch(line -> line.contains(dungeon.getWorldName())),
                "Map detail did not render chat output");

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
            "player", syntheticId.toString(), "compensate", "DIAMOND", "3", "Native compensation"
        };
        command.execute(sender, "dungeon", compensation);
        String confirmation =
                output.stream()
                        .filter(
                                line ->
                                        line.contains("/dungeon player ")
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

        verifyExistingMapIdentityAndMetadataKeys(test, dungeon);
        return CompletableFuture.completedFuture(null);
    }
    private static void verifyExistingMapIdentityAndMetadataKeys(AcceptancePlugin test,
            nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition dungeon) throws Exception {
        var settings = test.runtime().config();
        Object unique = settings.get("access.keys.unique_validation_enabled");
        Object metadata = settings.get("access.keys.metadata_validation_enabled");
        try {
            settings.set("access.keys.unique_validation_enabled", false);
            settings.set("access.keys.metadata_validation_enabled", true);
            var definition = dungeon.addAccessKey(new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER), null);
            var existing = nl.hauntedmc.dungeons.util.item.ItemUtils.tagDungeonAccessKey(
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER), definition.getKeyId());
            var file = new java.io.File(dungeon.getFolder(), "config.yml");
            var before = new org.bukkit.configuration.file.YamlConfiguration();
            before.load(file);
            before.set("dungeon.id", null);
            before.save(file);
            java.nio.file.Files.writeString(dungeon.getFolder().toPath().resolve("access_keys.yml"), "unused: [broken");
            var upgraded = test.runtime().dungeonCatalog().loadDungeon(dungeon.getFolder());
            require(upgraded != null, "Missing map ID or unused ledger broke upgrade");
            java.util.UUID assigned = upgraded.getDungeonId();
            require(assigned != null, "Map UUID was not assigned");
            require(upgraded.isValidKey(existing) != null, "Existing metadata key rejected after ID assignment");
            var reloaded = test.runtime().dungeonCatalog().loadDungeon(upgraded.getFolder());
            require(reloaded != null && assigned.equals(reloaded.getDungeonId()), "Reload changed the assigned UUID");
            require(reloaded.getAccessKeys().getFirst().getKeyId().equals(definition.getKeyId()), "Upgrade changed key-definition identity");
            require(reloaded.isValidKey(existing) != null, "Reload rejected an existing metadata-only key");
        } finally {
            settings.set("access.keys.unique_validation_enabled", unique);
            settings.set("access.keys.metadata_validation_enabled", metadata);
        }
    }

}
