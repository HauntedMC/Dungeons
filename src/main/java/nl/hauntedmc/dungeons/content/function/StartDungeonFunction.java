package nl.hauntedmc.dungeons.content.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;

/**
 * Function that starts a lobby-enabled dungeon run.
 */
@AutoRegister(id = "dungeons.function.start_dungeon")
@SerializableAs("dungeons.function.start_dungeon")
public class StartDungeonFunction extends DungeonFunction {
    private List<UUID> readyPlayers = new ArrayList<>();

    /**
     * Creates a new StartDungeonFunction instance.
     */
    public StartDungeonFunction(Map<String, Object> config) {
        super("Start Dungeon", config);
        this.setCategory(FunctionCategory.DUNGEON);
        this.setAllowRetriggerByDefault(true);
        this.setRequiresTarget(true);
    }

    /**
     * Creates a new StartDungeonFunction instance.
     */
    public StartDungeonFunction() {
        super("Start Dungeon");
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.DUNGEON);
        this.setAllowRetriggerByDefault(true);
        this.setRequiresTarget(true);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.GOLD_BLOCK);
        functionButton.setDisplayName("&6Start Dungeon");
        functionButton.addLore("&eStarts the dungeon when the");
        functionButton.addLore("&etrigger condition is met.");
        return functionButton;
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance == null || instance.isStarted() || targets.isEmpty()) {
            return;
        }

        boolean markedNewPlayer = false;
        for (DungeonPlayerSession target : targets) {
            UUID playerId = target.getPlayer().getUniqueId();
            if (this.readyPlayers.contains(playerId)) {
                continue;
            }

            this.readyPlayers.add(playerId);
            LangUtils.sendMessage(target.getPlayer(), "instance.play.functions.start-dungeon-ready");
            markedNewPlayer = true;
        }

        if (!markedNewPlayer) {
            return;
        }

        for (DungeonPlayerSession player : instance.getPlayers()) {
            if (!this.readyPlayers.contains(player.getPlayer().getUniqueId())) {
                return;
            }
        }

        instance.startGame();
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {}

    /**
     * Performs clone.
     */
    @Override
    public DungeonFunction clone() {
        StartDungeonFunction clone = (StartDungeonFunction) super.clone();
        clone.readyPlayers = new ArrayList<>();
        return clone;
    }
}
