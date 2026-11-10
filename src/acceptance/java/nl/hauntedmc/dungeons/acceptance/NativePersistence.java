package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.content.function.meta.CompositeFunction;
import nl.hauntedmc.dungeons.content.function.meta.DelayedFunction;
import nl.hauntedmc.dungeons.model.dungeon.AccessKeyDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.util.item.ItemUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.nio.file.Files;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

final class NativePersistence {
    private NativePersistence() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        Files.createDirectories(test.getDataFolder().toPath());
        var item = new ItemStack(Material.TRIPWIRE_HOOK, 3);
        item.editMeta(
                meta -> {
                    meta.displayName(Component.text("Acceptance key | !"));
                    meta.lore(List.of(Component.text("Native metadata survives YAML")));
                    meta.getPersistentDataContainer()
                            .set(
                                    token(test),
                                    PersistentDataType.STRING,
                                    "native|payload!with delimiters");
                });
        var key =
                new AccessKeyDefinition(
                        item,
                        new Date(1_700_000_000_000L),
                        "Acceptance",
                        UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var issued =
                ItemUtils.tagDungeonAccessKeyInstance(
                        ItemUtils.tagDungeonAccessKey(item.clone(), key.getKeyId()),
                        "acceptance-issued");
        var graph = new CompositeFunction();
        graph.setLocation(new Location(null, -17, 70, 31));
        graph.addFunction(
                new DelayedFunction(
                        Map.of(
                                "delay",
                                2,
                                "function",
                                new SetStatusFunction(Map.of("status", "native-restored")))));
        graph.initialize();
        var yaml = new ConfigurationFile(test.runtime().environment().plugin());
        yaml.set("item", item);
        yaml.set("issued", issued);
        yaml.set("key", key);
        yaml.set("expected-key-id", key.getKeyId());
        yaml.set("graph", graph);
        var saved =
                test.onMain(
                        yaml.saveAsync(
                                test.getDataFolder()
                                        .toPath()
                                        .resolve("native-state.yml")
                                        .toFile()));
        // Editing continues immediately after the call; native item/model serialization must
        // already be frozen even if the disk worker has not run yet.
        item.setAmount(19);
        item.editMeta(meta -> meta.displayName(Component.text("Edited after save request")));
        graph.getLocation().setX(200);
        return saved.thenRun(
                () -> {
                    try {
                        verify(test);
                    } catch (Exception failure) {
                        throw new CompletionException(failure);
                    } finally {
                        release(graph);
                    }
                });
    }

    static void verify(AcceptancePlugin test) throws Exception {
        var yaml = new ConfigurationFile(test.runtime().environment().plugin());
        yaml.load(test.getDataFolder().toPath().resolve("native-state.yml").toFile());
        var item = yaml.getItemStack("item");
        require(
                item != null && item.getType() == Material.TRIPWIRE_HOOK && item.getAmount() == 3,
                "Native item type or quantity was lost");
        require(
                Component.text("Acceptance key | !").equals(item.getItemMeta().displayName()),
                "Native item display name was lost");
        require(
                List.of(Component.text("Native metadata survives YAML"))
                        .equals(item.getItemMeta().lore()),
                "Native item lore was lost");
        require(
                "native|payload!with delimiters"
                        .equals(
                                item.getItemMeta()
                                        .getPersistentDataContainer()
                                        .get(token(test), PersistentDataType.STRING)),
                "Native persistent data did not survive YAML");
        require(
                yaml.get("key") instanceof AccessKeyDefinition,
                "Key alias did not restore its type");
        var key = (AccessKeyDefinition) yaml.get("key");
        require(key.getKeyId().equals(yaml.getString("expected-key-id")), "Key identity changed");
        var keyItem = key.createItemCopy();
        require(
                key.getKeyId().equals(ItemUtils.getDungeonAccessKeyId(keyItem)),
                "Configured metadata validation did not stamp the key identity");
        require(
                ItemUtils.clearDungeonAccessKeyTracking(keyItem).isSimilar(item),
                "Non-tracking key metadata changed");
        require(
                "acceptance-issued"
                        .equals(
                                ItemUtils.getDungeonAccessKeyInstanceId(
                                        yaml.getItemStack("issued"))),
                "Issued item identity was lost");
        require(
                yaml.get("graph") instanceof CompositeFunction,
                "Graph alias did not restore its type");
        var graph = (CompositeFunction) yaml.get("graph");
        try {
            graph.initialize();
            require(
                    graph.getLocation().equals(new Location(null, -17, 70, 31)),
                    "Graph coordinates changed");
            require(
                    graph.getFunctions().getFirst() instanceof DelayedFunction,
                    "Nested function type was lost");
            var delayed = graph.getFunctions().getFirst();
            require(delayed.getParentFunction() == graph, "Nested parent ownership changed");
            require(
                    Integer.valueOf(2).equals(delayed.serialize().get("delay")),
                    "Nested delay changed");
        } finally {
            release(graph);
        }
    }

    private static void release(DungeonFunction function) {
        function.releaseEditorListener();
        if (function instanceof CompositeFunction composite) {
            composite.getFunctions().forEach(NativePersistence::release);
        } else if (function.serialize().get("function")
                instanceof DungeonFunction child) {
            release(child);
        }
    }

    private static NamespacedKey token(AcceptancePlugin test) {
        return new NamespacedKey(test, "native-token");
    }
}
