package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.content.reward.LootTableItem;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindowRegistry;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Verifies real Bukkit item decoding in a fully validated loot-table generation. */
final class NativeLoot {
    private NativeLoot() {}

    static CompletionStage<Void> run(AcceptancePlugin test) {
        var repository = test.runtime().lootTableRepository();
        String name = "acceptance.cache";
        var token = new NamespacedKey(test, "loot-token");
        var item = new ItemStack(Material.DIAMOND, 6);
        item.editMeta(
                meta -> {
                    meta.displayName(Component.text("Loot reload | !"));
                    meta.lore(List.of(Component.text("Native loot metadata")));
                    meta.getPersistentDataContainer()
                            .set(token, PersistentDataType.STRING, "loot-payload");
                });
        var table =
                new LootTable(
                        Map.of(
                                "namespace",
                                name,
                                "minItems",
                                2,
                                "maxItems",
                                2,
                                "lootItems",
                                Map.of(9, new LootTableItem(item))));
        require(repository.put(table), "Could not persist native loot table");
        require(repository.reload(), "Could not reload native loot table");
        var loaded = repository.get(name);
        require(loaded != null && loaded != table, "Loot table generation was not replaced");
        require(
                GuiWindowRegistry.getWindow("loottable_" + name) == null,
                "Loot reload unexpectedly registered an editor window");
        var rewards = loaded.randomizeItems(0);
        require(rewards.size() == 2, "Native loot count changed after reload");
        for (var reward : rewards) {
            require(
                    reward.getAmount() == 6 && reward.isSimilar(item),
                    "Native loot item type, quantity or metadata was lost");
            require(
                    "loot-payload"
                            .equals(
                                    reward.getItemMeta()
                                            .getPersistentDataContainer()
                                            .get(token, PersistentDataType.STRING)),
                    "Native loot persistent data was lost");
        }
        require(!repository.saveTablesConfig(table), "A stale editor saved another generation");
        require(
                repository.saveTablesConfig(loaded),
                "Current native loot generation could not save");
        require(repository.remove(name), "Could not remove native loot test table");
        return CompletableFuture.completedFuture(null);
    }
}
