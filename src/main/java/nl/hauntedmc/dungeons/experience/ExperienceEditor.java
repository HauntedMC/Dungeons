package nl.hauntedmc.dungeons.experience;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.staff.StaffEditGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Draft-based inventory/YAML editor: nothing changes on disk until a validated save. */
public final class ExperienceEditor implements Listener, AutoCloseable {
    private record Draft(
            UUID owner, DungeonDefinition dungeon, YamlConfiguration yaml, String original) {}

    private record Input(Draft draft, String path, boolean newPath) {}

    private final DungeonsRuntime runtime;
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Map<UUID, Input> inputs = new ConcurrentHashMap<>();
    private boolean closed;

    public ExperienceEditor(DungeonsRuntime runtime) {
        this.runtime = runtime;
        try {
            Bukkit.getPluginManager().registerEvents(this, runtime.environment().plugin());
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private void guard(Player player, Draft draft) {
        var session = runtime.playerSessions().get(player);
        if (closed
                || !draft.owner.equals(player.getUniqueId())
                || !StaffEditGuard.editor(player, session)
                || session.getInstance().getDungeon() != draft.dungeon)
            throw new IllegalArgumentException(
                    "Reopen this dungeon editor before editing its experience");
    }

    private Draft draft(Player player) {
        var session = runtime.playerSessions().get(player);
        if (!StaffEditGuard.editor(player, session))
            throw new IllegalArgumentException("Open an authorized dungeon editor first");
        var dungeon = session.getInstance().getDungeon();
        var existing = drafts.get(player.getUniqueId());
        if (existing != null && existing.dungeon == dungeon) return existing;
        try {
            var file = dungeon.getFolder().toPath().resolve("experience.yml");
            String original =
                    Files.exists(file) ? ConfigurationFile.readSnapshot(file.toFile()) : "";
            var yaml = new YamlConfiguration();
            yaml.loadFromString(original);
            if (original.isBlank()) yaml.set("version", 1);
            var result = new Draft(player.getUniqueId(), dungeon, yaml, original);
            drafts.put(player.getUniqueId(), result);
            return result;
        } catch (IOException | InvalidConfigurationException failure) {
            throw new IllegalArgumentException(
                    "Cannot read experience.yml: " + failure.getMessage(), failure);
        }
    }

    public boolean handle(Player player, String[] args) {
        var draft = draft(player);
        guard(player, draft);
        String operation = args.length > 2 ? args[2] : "open";
        switch (operation) {
            case "open" -> show(player, draft, "", 0);
            case "get" ->
                    LangUtils.sendRaw(
                            player,
                            args.length > 3
                                    ? String.valueOf(draft.yaml.get(args[3]))
                                    : draft.yaml.saveToString());
            case "set" -> {
                if (args.length < 5)
                    throw new IllegalArgumentException(
                            "/dungeon edit experience set <path> <YAML value>");
                set(
                        draft,
                        args[3],
                        String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)));
                LangUtils.sendRaw(player, "Draft updated. Validate and save when ready.");
            }
            case "remove" -> {
                if (args.length != 4) throw new IllegalArgumentException("Specify a path");
                draft.yaml.set(args[3], null);
            }
            case "validate" -> {
                ExperienceDefinition.parse(draft.yaml);
                LangUtils.sendMessage(player, "experience.editor-valid");
            }
            case "save" -> save(player, draft);
            case "reload" -> {
                drafts.remove(player.getUniqueId());
                show(player, draft(player), "", 0);
            }
            case "recipe" -> {
                if (args.length != 4)
                    throw new IllegalArgumentException(
                            "Recipes: loot | dragon | waves | adventure");
                recipe(draft, args[3], player);
                show(player, draft, "", 0);
            }
            case "preview" -> runtime.experience().preview(player, draft.dungeon);
            default ->
                    throw new IllegalArgumentException(
                            "Experience editor: open|get|set|remove|validate|save|reload|recipe|preview");
        }
        return true;
    }

    private void set(Draft draft, String path, String value) {
        if (!path.matches(
                "(?:version|enabled|completion|objectives|tracks|groups|encounters)(?:\\.[A-Za-z0-9_-]+)*"))
            throw new IllegalArgumentException("Invalid experience path");
        if (value.equals("null")) {
            draft.yaml.set(path, null);
            return;
        }
        var input = new YamlConfiguration();
        try {
            input.loadFromString("value: " + value);
        } catch (InvalidConfigurationException failure) {
            throw new IllegalArgumentException("Use a YAML scalar, list or inline map", failure);
        }
        if (!input.getKeys(false).equals(java.util.Set.of("value")))
            throw new IllegalArgumentException("One YAML value required");
        if (input.isConfigurationSection("value")) {
            draft.yaml.set(path, null);
            draft.yaml.createSection(path, input.getConfigurationSection("value").getValues(false));
        } else draft.yaml.set(path, input.get("value"));
    }

    public void saveDraft(Player player) {
        var draft = drafts.get(player.getUniqueId());
        if (draft != null) save(player, draft);
    }

    private void save(Player player, Draft draft) {
        guard(player, draft);
        var definition = ExperienceDefinition.parse(draft.yaml);
        ExperienceReferences.dungeon(draft.dungeon, definition);
        var file = draft.dungeon.getFolder().toPath().resolve("experience.yml");
        try {
            String current =
                    Files.exists(file) ? ConfigurationFile.readSnapshot(file.toFile()) : "";
            if (!current.equals(draft.original))
                throw new IllegalArgumentException(
                        "experience.yml changed externally; reload before saving");
            String snapshot = draft.yaml.saveToString();
            ConfigurationFile.saveSnapshot(runtime.environment().plugin(), file.toFile(), snapshot);
            drafts.put(
                    player.getUniqueId(),
                    new Draft(draft.owner, draft.dungeon, draft.yaml, snapshot));
            LangUtils.sendMessage(player, "experience.editor-saved");
            var window =
                    runtime.guiService().getWindow("experience_editor_" + player.getUniqueId());
            var inventory = window == null ? null : window.getInventoryFor(player);
            if (inventory != null
                    && player.getOpenInventory().getTopInventory() == inventory.inventory())
                show(player, drafts.get(player.getUniqueId()), "", 0);
        } catch (IOException | UncheckedIOException failure) {
            throw new IllegalArgumentException(
                    "Could not save experience: " + failure.getMessage(), failure);
        }
    }

    private void prompt(Player player, Draft draft, String path, boolean newPath) {
        guard(player, draft);
        player.closeInventory();
        var session = runtime.playerSessions().get(player);
        session.setChatListening(false);
        inputs.put(player.getUniqueId(), new Input(draft, path, newPath));
        LangUtils.sendRaw(
                player,
                newPath
                        ? "Enter a child key, or cancel."
                        : path
                                + " = "
                                + draft.yaml.get(path)
                                + ". Enter a YAML value, null to remove, or cancel.");
    }

    private void show(Player player, Draft draft, String path, int page) {
        guard(player, draft);
        GuiWindow window =
                runtime.guiService()
                        .createWindow(
                                "experience_editor_" + player.getUniqueId(),
                                54,
                                "&8Experience / " + path);
        window.setAccessCheck(
                viewer -> {
                    if (!viewer.getUniqueId().equals(player.getUniqueId())) return false;
                    try {
                        guard(viewer, draft);
                        return true;
                    } catch (IllegalArgumentException failure) {
                        return false;
                    }
                });
        ConfigurationSection section =
                path.isBlank() ? draft.yaml : draft.yaml.getConfigurationSection(path);
        var keys =
                new ArrayList<>(
                        section == null ? java.util.Set.<String>of() : section.getKeys(false));
        for (int index = page * 36; index < Math.min(keys.size(), (page + 1) * 36); index++) {
            String key = keys.get(index), full = path.isBlank() ? key : path + "." + key;
            boolean branch = draft.yaml.isConfigurationSection(full);
            button(
                    window,
                    index - page * 36,
                    key,
                    branch ? Material.CHEST : Material.PAPER,
                    () -> {
                        if (branch) show(player, draft, full, 0);
                        else prompt(player, draft, full, false);
                    });
        }
        if (path.isBlank()) {
            int slot = 36;
            for (String name : List.of("objectives", "tracks", "groups", "encounters")) {
                String selected = name;
                button(
                        window,
                        slot++,
                        "Add / edit " + name,
                        Material.WRITABLE_BOOK,
                        () -> {
                            if (!draft.yaml.isConfigurationSection(selected))
                                draft.yaml.createSection(selected);
                            show(player, draft, selected, 0);
                        });
            }
            int recipeSlot = 40;
            for (String name : List.of("loot", "dragon", "waves", "adventure")) {
                button(
                        window,
                        recipeSlot++,
                        "Draft recipe: " + name,
                        Material.MAP,
                        () -> {
                            guard(player, draft);
                            recipe(draft, name, player);
                            show(player, draft, "", 0);
                        });
            }
        }
        button(
                window,
                45,
                "Back",
                Material.ARROW,
                () ->
                        show(
                                player,
                                draft,
                                path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "",
                                0));
        button(window, 46, "Add key", Material.ANVIL, () -> prompt(player, draft, path, true));
        button(
                window,
                47,
                "Validate",
                Material.COMPARATOR,
                () -> {
                    guard(player, draft);
                    ExperienceDefinition.parse(draft.yaml);
                    LangUtils.sendMessage(player, "experience.editor-valid");
                });
        button(
                window,
                48,
                "Preview HUD",
                Material.PAINTING,
                () -> runtime.experience().preview(player, draft.dungeon));
        button(window, 49, "Save draft", Material.EMERALD, () -> save(player, draft));
        if (page > 0)
            button(
                    window,
                    52,
                    "Previous page",
                    Material.ARROW,
                    () -> show(player, draft, path, page - 1));
        if (keys.size() > (page + 1) * 36)
            button(
                    window,
                    53,
                    "Next page",
                    Material.ARROW,
                    () -> show(player, draft, path, page + 1));
        window.open(player);
    }

    private void button(
            GuiWindow window, int slot, String label, Material material, Runnable action) {
        var button = new Button(label, material, "&e" + label);
        button.addAction(
                label,
                event -> {
                    try {
                        action.run();
                    } catch (IllegalArgumentException failure) {
                        LangUtils.sendRaw((Player) event.getWhoClicked(), failure.getMessage());
                    }
                });
        window.addButton(slot, button);
    }

    private void recipe(Draft draft, String recipe, Player player) {
        // Recipes add only missing sections. Reapplication cannot replace custom mechanics.
        if (!java.util.Set.of("loot", "dragon", "waves", "adventure").contains(recipe))
            throw new IllegalArgumentException("Unknown recipe");
        if (!recipe.equals("loot")
                && (draft.yaml.contains("objectives")
                        || draft.yaml.contains("encounters")
                        || draft.yaml.contains("groups")
                        || draft.yaml.contains("tracks")))
            throw new IllegalArgumentException(
                    "Recipe would overlap authored mechanics; add individual modules instead");
        if (recipe.equals("loot")) return;
        var location = player.getLocation();
        if (recipe.equals("dragon")) {
            goal(
                    draft.yaml,
                    "crystals",
                    "Destroy the End crystals",
                    "destroy",
                    "type:END_CRYSTAL",
                    1);
            draft.yaml.set("objectives.crystals.capture", true);
            point(draft.yaml, "objectives.crystals.location", location, 128);
            goal(draft.yaml, "dragon", "Defeat the dragon", "kill", "group:dragon", 1);
            group(draft.yaml, "dragon", "ENDER_DRAGON", 1, location, true);
            draft.yaml.set("groups.dragon.boss", "Ender Dragon");
            draft.yaml.set("completion", "objectives");
        } else {
            group(draft.yaml, "guards", "ZOMBIE", 5, location, false);
            draft.yaml.set("encounters.arena.title", "The arena");
            draft.yaml.set("encounters.arena.auto_start", true);
            draft.yaml.set("encounters.arena.waves.first.groups", List.of("guards"));
            draft.yaml.set("encounters.arena.waves.second.groups", List.of("guards"));
            if (recipe.equals("adventure")) {
                goal(draft.yaml, "seals", "Break three seals", "signal", "seal", 3);
                goal(draft.yaml, "guards", "Defeat the guards", "kill", "group:guards", 10);
                draft.yaml.set("tracks.story.scope", "run");
                draft.yaml.set("tracks.story.stages.seals.objectives", List.of("seals"));
                draft.yaml.set("tracks.story.stages.arena.objectives", List.of("guards"));
                draft.yaml.set("encounters.arena.auto_start", false);
            }
        }
        LangUtils.sendRaw(
                player,
                "Recipe added to draft. Review the values and arena location, connect graph events where needed, then save. Lives, time and key rules were not changed.");
    }

    private static void goal(
            YamlConfiguration yaml,
            String id,
            String title,
            String source,
            String selector,
            int count) {
        String root = "objectives." + id;
        yaml.set(root + ".title", title);
        yaml.set(root + ".source", source);
        yaml.set(root + ".selector", selector);
        yaml.set(root + ".target", count);
    }

    private static void group(
            YamlConfiguration yaml,
            String id,
            String entity,
            int count,
            org.bukkit.Location location,
            boolean existing) {
        String root = "groups." + id;
        yaml.set(root + ".entity", entity);
        yaml.set(root + ".count", count);
        yaml.set(root + ".existing", existing);
        point(yaml, root + ".location", location, 128);
    }

    private static void point(
            YamlConfiguration yaml, String root, org.bukkit.Location location, double radius) {
        yaml.set(root + ".x", location.getX());
        yaml.set(root + ".y", location.getY());
        yaml.set(root + ".z", location.getZ());
        yaml.set(root + ".radius", radius);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void input(AsyncChatEvent event) {
        Input input = inputs.remove(event.getPlayer().getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler()
                .runTask(
                        runtime.environment().plugin(),
                        () -> {
                            Player player = event.getPlayer();
                            try {
                                guard(player, input.draft);
                                if (!message.equalsIgnoreCase("cancel")) {
                                    if (input.newPath) {
                                        if (!message.matches("[A-Za-z0-9_-]+"))
                                            throw new IllegalArgumentException(
                                                    "Use letters, numbers, hyphens or underscores");
                                        String path =
                                                input.path.isBlank()
                                                        ? message
                                                        : input.path + "." + message;
                                        prompt(player, input.draft, path, false);
                                        return;
                                    }
                                    set(input.draft, input.path, message);
                                }
                                show(
                                        player,
                                        input.draft,
                                        input.path.contains(".")
                                                ? input.path.substring(
                                                        0, input.path.lastIndexOf('.'))
                                                : "",
                                        0);
                            } catch (IllegalArgumentException failure) {
                                LangUtils.sendRaw(player, failure.getMessage());
                            }
                        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        drafts.remove(event.getPlayer().getUniqueId());
        inputs.remove(event.getPlayer().getUniqueId());
        var window =
                runtime.guiService()
                        .getWindow("experience_editor_" + event.getPlayer().getUniqueId());
        if (window != null) window.unregister();
    }

    @Override
    public void close() {
        closed = true;
        drafts.clear();
        inputs.clear();
        HandlerList.unregisterAll(this);
    }
}
