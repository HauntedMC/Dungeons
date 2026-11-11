package nl.hauntedmc.dungeons.content.trigger;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.config.PluginConfigView;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.event.EventHandler;
import org.bukkit.scheduler.BukkitRunnable;

/** Trigger that fires when the dungeon run starts. */
@AutoRegister(id = "dungeons.trigger.dungeon_start")
@SerializableAs("dungeons.trigger.dungeon_start")
public class DungeonStartTrigger extends DungeonTrigger {
    private Set<BukkitRunnable> tasks = new HashSet<>();

    /** Creates a new DungeonStartTrigger instance. */
    public DungeonStartTrigger(Map<String, Object> config) {
        super("Dungeon Start", config);
        this.setCategory(TriggerCategory.DUNGEON);
        this.setHasTarget(true);
    }

    /** Creates a new DungeonStartTrigger instance. */
    public DungeonStartTrigger() {
        super("Dungeon Start");
        this.setCategory(TriggerCategory.DUNGEON);
        this.setHasTarget(true);
    }

    /** Builds menu button. */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.MOSSY_COBBLESTONE);
        functionButton.setDisplayName("&6Dungeon Start");
        functionButton.addLore("&eTriggered when the dungeon");
        functionButton.addLore("&ebegins.");
        return functionButton;
    }

    /** Performs on dungeon start. */
    @EventHandler
    public void onDungeonStart(DungeonStartEvent event) {
        if (event.getInstance() == this.instance) {
            int delay =
                    PluginConfigView.getDungeonStartTriggerDelayTicks(
                            DungeonStartTrigger.this.runtime().config());
            if (this.instance.getDungeon().isLobbyEnabled()) {
                delay =
                        PluginConfigView.getDungeonStartTriggerLobbyDelayTicks(
                                DungeonStartTrigger.this.runtime().config());
            }

            var sources = List.copyOf(event.getPlayerSessions());
            BukkitRunnable task =
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                sources.stream()
                                        .filter(
                                                source ->
                                                        source.getInstance()
                                                                        == DungeonStartTrigger.this
                                                                                .instance
                                                                && !source.isObserving())
                                        .findFirst()
                                        .ifPresent(DungeonStartTrigger.this::trigger);
                            } finally {
                                tasks.remove(this);
                            }
                        }
                    };
            tasks.add(task);
            try {
                task.runTaskLater(this.plugin(), Math.max(0, delay));
            } catch (RuntimeException exception) {
                tasks.remove(task);
                throw exception;
            }
        }
    }

    @Override
    public void onDisable() {
        for (BukkitRunnable task : tasks) {
            this.cleanupOwnedResource(
                    "dungeon start callback",
                    () -> {
                        if (!task.isCancelled()) task.cancel();
                    });
        }
        tasks.clear();
    }

    @Override
    public DungeonStartTrigger clone() {
        DungeonStartTrigger copy = (DungeonStartTrigger) super.clone();
        copy.tasks = new HashSet<>();
        return copy;
    }

    /** Builds hotbar menu. */
    @Override
    public void buildHotbarMenu() {}
}
